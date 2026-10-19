package com.groupironmencompanion.scene;

import com.groupironmencompanion.GroupIronmenCompanionConfig;
import com.groupironmencompanion.data.GroupMember;
import com.groupironmencompanion.data.GroupState;
import com.groupironmencompanion.data.ItemStack;
import com.groupironmencompanion.scene.GhostAnimations.Activity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

/**
 * Draws online group members, and whatever they are fighting, as translucent figures in the
 * local scene.
 * <p>
 * Nothing here is a game entity. Each figure is a {@link net.runelite.api.RuneLiteObject},
 * which the client draws and nothing else: it is absent from the player and NPC lists, it
 * creates no menu entry, it cannot be targeted, and the server is never told it exists. The
 * plugin is still read-only with respect to the game.
 * <p>
 * Figures are only built for members whose reported position falls inside the scene the client
 * has loaded, which is both what makes the feature coherent and what keeps it cheap: a member
 * across the map costs a coordinate comparison and nothing else.
 * <p>
 * Whether any of this may run at all is {@link ScenePresenceGate}'s decision, consulted every
 * tick and never cached.
 */
@Singleton
public class GroupScenePresence
{
	/**
	 * An interaction is only drawn while the NPC's reported position is this close to the
	 * member's own. A member who walked away from a fight keeps reporting the last thing they
	 * interacted with for a while, and drawing that at a distance looks like a second member.
	 */
	private static final int INTERACTION_MAX_TILES = 12;

	/**
	 * Upper bound on the remembered name-to-id and weapon lookups.
	 * <p>
	 * Both are keyed by values that ultimately come from the group's server, so neither may
	 * grow without limit on the strength of what a server chooses to send. The real number of
	 * distinct NPCs and weapons a group meets in a session is far below this; the cap exists
	 * so that a server sending nonsense costs a bounded amount of memory rather than an
	 * unbounded one.
	 */
	private static final int LOOKUP_CACHE_LIMIT = 256;

	@Inject
	private Client client;

	@Inject
	private GroupState groupState;

	@Inject
	private GroupIronmenCompanionConfig config;

	@Inject
	private GhostModels models;

	@Inject
	private ScenePresenceGate gate;

	@Inject
	private ItemManager itemManager;

	/** Live figures, keyed by member name. Client thread only. */
	private final Map<String, Ghost> memberGhosts = new HashMap<>();

	/** The figure for whatever each member is fighting, keyed by member name. */
	private final Map<String, Ghost> npcGhosts = new HashMap<>();

	/** Per-member motion and activity history, used to infer what they are doing. */
	private final Map<String, MemberTrail> trails = new HashMap<>();

	/**
	 * NPC name to id, remembered across ticks.
	 * <p>
	 * Resolving a name means walking the scene's NPC list, which with several members fighting
	 * would be that walk per member per tick. The mapping does not change once learned, so in
	 * the steady state this costs a hash lookup and the scan never runs again.
	 */
	private final Map<String, Integer> npcIdByName = new HashMap<>();

	/** Weapon category by item id, so a name is classified once rather than every tick. */
	private final Map<Integer, WeaponClass> weaponClasses = new HashMap<>();

	/** Published for the hover label overlay, which runs on the client thread too. */
	private volatile List<Ghost> visible = Collections.emptyList();

	/** Monotonic tick counter, so speed can be measured between real position changes. */
	private int tickCounter;

	/**
	 * Rebuilds the set of figures and decides what each is doing. Called once per game tick on
	 * the client thread, which is the same order of magnitude as the poll that feeds it.
	 */
	public void onGameTick()
	{
		if (!gate.allowed())
		{
			clear();
			return;
		}

		WorldView view = client.getTopLevelWorldView();
		if (view == null)
		{
			clear();
			return;
		}

		tickCounter++;

		int transparency = config.scenePresenceTransparency();
		Set<String> present = new HashSet<>();

		for (GroupMember member : groupState.getMembers())
		{
			if (groupState.isLocalPlayer(member) || !member.isOnline(config.offlineAfterMinutes()))
			{
				continue;
			}

			WorldPoint position = member.getWorldPoint();
			if (position == null || member.isOnBoat())
			{
				continue;
			}

			if (alreadyDrawnByTheGame(view, member))
			{
				// They are really here, so the game is drawing them. A ghost on top would be
				// a translucent copy standing inside the actual player.
				continue;
			}

			LocalPoint local = LocalPoint.fromWorld(view, position);
			if (!drawable(view, local, position.getPlane()))
			{
				// Outside the loaded scene, so there is nowhere on screen to draw them. This
				// is the test that keeps the feature to "people who would be visible anyway".
				continue;
			}

			present.add(member.getName());
			updateMember(view, member, position, local, transparency);
		}

		retireAllExcept(present);
		republish();
	}

	/** Eases every figure toward its target. Called every client tick on the client thread. */
	public void onClientTick()
	{
		for (Ghost ghost : memberGhosts.values())
		{
			ghost.advance();
		}
		for (Ghost ghost : npcGhosts.values())
		{
			ghost.advance();
		}
	}

	/** The figures currently drawn, for the hover label. */
	public Collection<Ghost> getVisible()
	{
		return visible;
	}

	/** Removes every figure from the scene. Client thread only. */
	public void clear()
	{
		for (Ghost ghost : memberGhosts.values())
		{
			ghost.destroy();
		}
		for (Ghost ghost : npcGhosts.values())
		{
			ghost.destroy();
		}
		memberGhosts.clear();
		npcGhosts.clear();
		trails.clear();
		visible = Collections.emptyList();
	}

	/** Drops cached models and lookups as well, after a settings change. Client thread only. */
	public void reset()
	{
		clear();
		models.clear();
		npcIdByName.clear();
		weaponClasses.clear();
	}

	// ------------------------------------------------------------------
	// Per-member update
	// ------------------------------------------------------------------

	private void updateMember(WorldView view, GroupMember member, WorldPoint position,
		LocalPoint local, int transparency)
	{
		String name = member.getName();

		Ghost ghost = memberGhosts.get(name);
		if (ghost == null)
		{
			ghost = new Ghost(client, name, local, position.getPlane());
			ghost.setLabel(name);
			memberGhosts.put(name, ghost);
		}

		ghost.setModel(models.member(equippedItemIds(member), transparency));
		ghost.moveTo(local, position.getPlane());

		MemberTrail trail = trails.computeIfAbsent(name, key -> new MemberTrail());
		trail.observe(position, tickCounter);

		Activity activity = resolveActivity(member, trail, ghost);
		WeaponClass weapon = weaponOf(member);
		ghost.setAnimation(GhostAnimations.poseFor(activity), GhostAnimations.actionFor(activity, weapon));
		ghost.onGameTick();

		updateInteraction(view, member, position, local, transparency, ghost, activity);
	}

	/**
	 * Decides what a member appears to be doing, in priority order.
	 * <p>
	 * Movement is judged by whether the figure still has ground to cover rather than by
	 * whether new data arrived this tick, because data arrives more slowly than this runs.
	 * Judging it from the data directly makes a running member flicker between running and
	 * standing on alternate ticks. Then a live interaction, which is a direct observation.
	 * Then experience gained, which is only a hint, and only consulted when they are standing
	 * still and fighting nothing.
	 */
	private Activity resolveActivity(GroupMember member, MemberTrail trail, Ghost ghost)
	{
		// Sampled every tick regardless of the outcome, because it is a delta: skipping the
		// read when the member is moving would make the next one compare against a stale total
		// and report a gain they did not just make.
		boolean gainedXp = trail.gainedXp(member.getTotalXp());

		if (ghost.isMoving())
		{
			return trail.getMovement();
		}

		GroupMember.Interaction interaction = member.getInteraction();
		if (interaction != null && !interaction.isStale())
		{
			return Activity.FIGHTING;
		}

		return gainedXp ? Activity.GATHERING : Activity.IDLE;
	}

	/**
	 * Draws the NPC a member is fighting, and turns the member to face it.
	 * <p>
	 * The wire format carries the NPC's name and position but not its id, and an id is what a
	 * model needs. Rather than bundling a name table, the local scene is searched for an NPC of
	 * the same name: the two members are in the same place on different worlds, so the thing
	 * one is fighting almost always has a counterpart in the other's scene. When it does not,
	 * the member is still drawn and still faces the right way, just without an opponent.
	 */
	private void updateInteraction(WorldView view, GroupMember member, WorldPoint position,
		LocalPoint local, int transparency, Ghost ghost, Activity activity)
	{
		String name = member.getName();
		GroupMember.Interaction interaction = member.getInteraction();

		WorldPoint target = interaction == null ? null : interaction.getLocation();
		boolean drawable = config.scenePresenceOpponents()
			&& activity == Activity.FIGHTING
			&& target != null
			&& target.getPlane() == position.getPlane()
			&& target.distanceTo(position) <= INTERACTION_MAX_TILES;

		if (!drawable)
		{
			retireNpc(name);
			return;
		}

		LocalPoint targetLocal = LocalPoint.fromWorld(view, target);
		if (!drawable(view, targetLocal, target.getPlane()))
		{
			retireNpc(name);
			return;
		}

		// Face the member at whatever they are fighting, which is why the interaction's
		// position is worth carrying even when the opponent itself is not drawn.
		ghost.faceTowards(targetLocal);

		Integer npcId = resolveNpcId(view, interaction.getName());
		if (npcId == null)
		{
			retireNpc(name);
			return;
		}

		Ghost opponent = npcGhosts.get(name);
		if (opponent == null)
		{
			opponent = new Ghost(client, name, targetLocal, target.getPlane());
			npcGhosts.put(name, opponent);
		}

		opponent.setModel(models.forNpc(npcId, transparency));
		opponent.moveTo(targetLocal, target.getPlane());
		opponent.faceTowards(local);
		opponent.setLabel(interaction.getName());
	}

	/**
	 * Whether a reported position is one this plugin is willing to draw at.
	 * <p>
	 * Two jobs. The scene bounds keep the feature to members who would be on screen anyway,
	 * which is why a member on the other side of the map costs nothing but a comparison.
	 * <p>
	 * The plane check is the more important one, because it is a trust boundary. Coordinates
	 * arrive from the group's server and are stored exactly as sent, so the plane is whatever
	 * a server chose to put in the third slot of an array. It then reaches the client's own
	 * scene code, which indexes by it. Anything outside the four real planes is refused here
	 * rather than handed down.
	 */
	private static boolean drawable(WorldView view, @Nullable LocalPoint point, int plane)
	{
		if (point == null || !isRealPlane(plane))
		{
			return false;
		}
		int x = point.getSceneX();
		int y = point.getSceneY();
		return x >= 0 && y >= 0 && x < view.getSizeX() && y < view.getSizeY();
	}

	/**
	 * Whether the game is already drawing this member itself, making a ghost redundant.
	 * <p>
	 * The whole point of the feature is seeing someone who is not on your world. When they
	 * are, the real player is standing there and a ghost is a translucent duplicate inside
	 * them.
	 * <p>
	 * The world number settles it outright and costs nothing, so it is asked first. A member
	 * who has reported a position but not yet any stats has no known world, and then the only
	 * honest test is whether a player of that name is actually in the scene. That walk is
	 * confined to the case where the cheap answer is unavailable.
	 */
	private boolean alreadyDrawnByTheGame(WorldView view, GroupMember member)
	{
		int world = member.getWorld();
		if (world > 0)
		{
			return isOurWorld(world, client.getWorld());
		}

		for (Player player : view.players())
		{
			if (GroupState.namesMatch(player.getName(), member.getName()))
			{
				return true;
			}
		}
		return false;
	}

	/** Whether a member's reported world is this one, and therefore the game's to draw. */
	static boolean isOurWorld(int memberWorld, int localWorld)
	{
		return memberWorld > 0 && memberWorld == localWorld;
	}

	/**
	 * Whether a plane is one the game actually has.
	 * <p>
	 * Separated out so the rule can be tested without a scene, because it is the one place
	 * this feature guards a value that came off the wire before passing it to the client.
	 */
	static boolean isRealPlane(int plane)
	{
		return plane >= 0 && plane < Constants.MAX_Z;
	}

	/**
	 * The id of an NPC with this name, from the remembered mapping or by scanning the scene.
	 * <p>
	 * Names are not unique to an id, but any NPC sharing the name shares the model, which is
	 * all this is for. A name that cannot be resolved is deliberately not remembered, so the
	 * scan is retried as the member's surroundings change rather than written off for the
	 * session.
	 */
	@Nullable
	private Integer resolveNpcId(WorldView view, String name)
	{
		if (name == null || name.isEmpty())
		{
			return null;
		}

		Integer known = npcIdByName.get(name);
		if (known != null)
		{
			return known;
		}

		for (NPC npc : view.npcs())
		{
			if (name.equals(npc.getName()))
			{
				remember(npcIdByName, name, npc.getId());
				return npc.getId();
			}
		}
		return null;
	}

	/**
	 * A member's worn item ids, one per equipment slot.
	 * <p>
	 * The container is sent positionally, so a slot's index in the list is the equipment slot
	 * itself and an empty slot is a zero id. That is exactly the shape the appearance rules
	 * want, so it is handed over as-is rather than being interpreted here.
	 */
	private static int[] equippedItemIds(GroupMember member)
	{
		List<ItemStack> equipment = member.getEquipment();
		int[] ids = new int[equipment.size()];
		for (int slot = 0; slot < ids.length; slot++)
		{
			ItemStack item = equipment.get(slot);
			ids[slot] = item == null || item.isEmpty() ? 0 : item.getId();
		}
		return ids;
	}

	/** The category of weapon a member has equipped, for choosing an attack animation. */
	private WeaponClass weaponOf(GroupMember member)
	{
		List<ItemStack> equipment = member.getEquipment();
		int slot = EquipmentInventorySlot.WEAPON.getSlotIdx();
		if (slot >= equipment.size())
		{
			return WeaponClass.UNARMED;
		}

		ItemStack weapon = equipment.get(slot);
		if (weapon == null || weapon.isEmpty())
		{
			return WeaponClass.UNARMED;
		}

		WeaponClass known = weaponClasses.get(weapon.getId());
		if (known != null)
		{
			return known;
		}

		WeaponClass resolved;
		try
		{
			resolved = WeaponClass.classify(itemManager.getItemComposition(weapon.getId()).getName());
		}
		catch (RuntimeException e)
		{
			// An id the running client does not know, such as one from a newer revision.
			resolved = WeaponClass.UNARMED;
		}

		remember(weaponClasses, weapon.getId(), resolved);
		return resolved;
	}

	/** Stores a lookup, clearing the whole map first if it has grown past its cap. */
	private static <K, V> void remember(Map<K, V> cache, K key, V value)
	{
		if (cache.size() >= LOOKUP_CACHE_LIMIT)
		{
			cache.clear();
		}
		cache.put(key, value);
	}

	// ------------------------------------------------------------------
	// Bookkeeping
	// ------------------------------------------------------------------

	private void retireAllExcept(Set<String> present)
	{
		retire(memberGhosts, present);
		retire(npcGhosts, present);
		trails.keySet().retainAll(present);
	}

	private static void retire(Map<String, Ghost> ghosts, Set<String> present)
	{
		for (Iterator<Map.Entry<String, Ghost>> it = ghosts.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<String, Ghost> entry = it.next();
			if (!present.contains(entry.getKey()))
			{
				entry.getValue().destroy();
				it.remove();
			}
		}
	}

	private void retireNpc(String memberName)
	{
		Ghost opponent = npcGhosts.remove(memberName);
		if (opponent != null)
		{
			opponent.destroy();
		}
	}

	private void republish()
	{
		List<Ghost> drawn = new ArrayList<>(memberGhosts.size() + npcGhosts.size());
		drawn.addAll(memberGhosts.values());
		drawn.addAll(npcGhosts.values());
		visible = Collections.unmodifiableList(drawn);
	}
}
