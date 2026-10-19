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
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPC;
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
	private final Map<String, Trail> trails = new HashMap<>();

	/** Published for the hover label overlay, which runs on the client thread too. */
	private volatile List<Ghost> visible = Collections.emptyList();

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

			LocalPoint local = LocalPoint.fromWorld(view, position);
			if (!inScene(view, local))
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

	/** Drops cached models as well, after a settings change. Client thread only. */
	public void reset()
	{
		clear();
		models.clear();
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
			ghost = new Ghost(client, view, name, local, position.getPlane());
			memberGhosts.put(name, ghost);
		}

		ghost.setModel(models.member(transparency));
		ghost.moveTo(local, position.getPlane());

		Trail trail = trails.computeIfAbsent(name, key -> new Trail());
		Activity activity = resolveActivity(member, position, trail);
		trail.record(position, member.getTotalXp());

		WeaponClass weapon = weaponOf(member);
		ghost.setAnimation(GhostAnimations.poseFor(activity), GhostAnimations.actionFor(activity, weapon));
		ghost.setLabel(name);

		updateInteraction(view, member, position, local, transparency, ghost, activity);
	}

	/**
	 * Decides what a member appears to be doing, in priority order.
	 * <p>
	 * Movement first, because a member fighting while moving is kiting and the walk reads
	 * better than a swing. Then a live interaction, which is a direct observation. Then
	 * experience gained since the last tick, which is only a hint, and only used when they are
	 * standing still and not fighting anything.
	 */
	private Activity resolveActivity(GroupMember member, WorldPoint position, Trail trail)
	{
		Activity movement = trail.movementSince(position);
		if (movement != Activity.IDLE)
		{
			return movement;
		}

		GroupMember.Interaction interaction = member.getInteraction();
		if (interaction != null && !interaction.isStale())
		{
			return Activity.FIGHTING;
		}

		if (trail.gainedXpSince(member.getTotalXp()))
		{
			return Activity.GATHERING;
		}

		return Activity.IDLE;
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
		if (!inScene(view, targetLocal))
		{
			retireNpc(name);
			return;
		}

		// Face the member at whatever they are fighting, which is why the interaction's
		// position is worth carrying even when the opponent itself is not drawn.
		ghost.faceTowards(targetLocal);

		Integer npcId = findNpcIdByName(view, interaction.getName());
		if (npcId == null)
		{
			retireNpc(name);
			return;
		}

		Ghost opponent = npcGhosts.get(name);
		if (opponent == null)
		{
			opponent = new Ghost(client, view, name, targetLocal, target.getPlane());
			npcGhosts.put(name, opponent);
		}

		opponent.setModel(models.forNpc(npcId, transparency));
		opponent.moveTo(targetLocal, target.getPlane());
		opponent.faceTowards(local);
		opponent.setLabel(interaction.getName());
	}

	/**
	 * Whether a converted position actually falls inside the scene the client has loaded.
	 * <p>
	 * This is the test that keeps the feature to members who would be on screen anyway, and it
	 * is why a member on the other side of the map costs nothing but a comparison.
	 */
	private static boolean inScene(WorldView view, @Nullable LocalPoint point)
	{
		if (point == null)
		{
			return false;
		}
		int x = point.getSceneX();
		int y = point.getSceneY();
		return x >= 0 && y >= 0 && x < view.getSizeX() && y < view.getSizeY();
	}

	/**
	 * The id of an NPC in the local scene with this name, or null when there is none.
	 * <p>
	 * Names are not unique to an id, but any NPC sharing the name shares the model, which is
	 * all this is for.
	 */
	@Nullable
	private Integer findNpcIdByName(WorldView view, String name)
	{
		if (name == null || name.isEmpty())
		{
			return null;
		}
		for (NPC npc : view.npcs())
		{
			if (name.equals(npc.getName()))
			{
				return npc.getId();
			}
		}
		return null;
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

		try
		{
			return WeaponClass.classify(itemManager.getItemComposition(weapon.getId()).getName());
		}
		catch (RuntimeException e)
		{
			// An id the running client does not know, such as one from a newer revision.
			return WeaponClass.UNARMED;
		}
	}

	// ------------------------------------------------------------------
	// Bookkeeping
	// ------------------------------------------------------------------

	private void retireAllExcept(Set<String> present)
	{
		memberGhosts.keySet().removeIf(name ->
		{
			if (present.contains(name))
			{
				return false;
			}
			memberGhosts.get(name).destroy();
			return true;
		});

		npcGhosts.keySet().removeIf(name ->
		{
			if (present.contains(name))
			{
				return false;
			}
			npcGhosts.get(name).destroy();
			return true;
		});

		trails.keySet().retainAll(present);
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

	/** One member's recent history, enough to tell movement and training apart. */
	private static final class Trail
	{
		private WorldPoint lastPosition;
		private long lastTotalXp = -1;

		/** How the move from the last recorded position to this one should be animated. */
		Activity movementSince(WorldPoint position)
		{
			if (lastPosition == null || lastPosition.getPlane() != position.getPlane())
			{
				return Activity.IDLE;
			}
			double tiles = lastPosition.distanceTo2D(position);
			// One game tick per call, since this runs on the game tick.
			return GhostAnimations.movementFor(tiles, 1);
		}

		boolean gainedXpSince(long totalXp)
		{
			return lastTotalXp >= 0 && totalXp > lastTotalXp;
		}

		void record(WorldPoint position, long totalXp)
		{
			this.lastPosition = position;
			this.lastTotalXp = totalXp;
		}
	}
}
