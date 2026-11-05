package com.groupironmencompanion.scene;

import com.groupironmencompanion.GroupIronmenCompanionConfig;
import com.groupironmencompanion.activity.Activity;
import com.groupironmencompanion.activity.ActivityTracker;
import com.groupironmencompanion.data.GroupMember;
import com.groupironmencompanion.data.GroupState;
import com.groupironmencompanion.data.ItemStack;
import com.groupironmencompanion.scene.GhostAnimations.Movement;
import java.time.Instant;
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
 * Draws group members on other worlds, and whatever they're fighting, as translucent figures.
 * <p>
 * The figures are {@link net.runelite.api.RuneLiteObject}s: the client draws them, but they're
 * not in the player or NPC lists, can't be clicked, and the server never hears about them.
 * <p>
 * What each figure does comes from {@link ActivityTracker}, the same guess the status overlay
 * and side panel show, so the two never disagree. Only members inside the loaded scene get a
 * figure, so anyone further away costs one coordinate check.
 */
@Singleton
public class GroupScenePresence
{
	// A fight further away than this is one they've walked off from.
	private static final int INTERACTION_MAX_TILES = 12;

	// The name and weapon lookups are keyed on things the group's server sends, so they're
	// capped rather than trusted to stay small.
	private static final int LOOKUP_CACHE_LIMIT = 256;

	@Inject
	private Client client;

	@Inject
	private GroupState groupState;

	@Inject
	private ActivityTracker activityTracker;

	@Inject
	private GroupIronmenCompanionConfig config;

	@Inject
	private GhostModels models;

	@Inject
	private ScenePresenceGate gate;

	@Inject
	private ItemManager itemManager;

	// All client thread, keyed by member name.
	private final Map<String, Ghost> memberGhosts = new HashMap<>();
	private final Map<String, Ghost> npcGhosts = new HashMap<>();
	private final Map<String, MemberTrail> trails = new HashMap<>();

	// Finding an NPC's id by name means walking the scene's NPC list, so it's done once.
	private final Map<String, Integer> npcIdByName = new HashMap<>();
	private final Map<Integer, WeaponClass> weaponClasses = new HashMap<>();

	// Read by the hover label.
	private volatile List<Ghost> visible = Collections.emptyList();

	private int tickCounter;

	/** Rebuilds the figures. Game tick, client thread. */
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
		Instant now = Instant.now();
		Set<String> present = new HashSet<>();

		for (GroupMember member : groupState.getMembers())
		{
			if (groupState.isLocalPlayer(member) || !member.isOnline(config.offlineAfterMinutes()))
			{
				continue;
			}

			WorldPoint position = member.getWorldPoint();
			if (position == null || member.isOnBoat() || alreadyDrawnByTheGame(view, member))
			{
				continue;
			}

			LocalPoint local = LocalPoint.fromWorld(view, position);
			if (!drawable(view, local, position.getPlane()))
			{
				continue;
			}

			present.add(member.getName());
			updateMember(view, member, position, local, transparency, now);
		}

		retireAllExcept(present);
		republish();
	}

	/** Eases the figures along. Every frame, client thread. */
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

	public Collection<Ghost> getVisible()
	{
		return visible;
	}

	/** Removes every figure. Client thread. */
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

	/** Also drops cached models and lookups, after a settings change. Client thread. */
	public void reset()
	{
		clear();
		models.clear();
		npcIdByName.clear();
		weaponClasses.clear();
	}

	private void updateMember(WorldView view, GroupMember member, WorldPoint position, LocalPoint local,
		int transparency, Instant now)
	{
		String name = member.getName();
		Activity activity = activityTracker.get(name);

		Ghost ghost = memberGhosts.computeIfAbsent(name, key -> new Ghost(client, local, position.getPlane()));
		ghost.setModel(models.member(equippedItemIds(member), transparency));
		ghost.moveTo(local, position.getPlane());
		ghost.setLabel(name, activity == null ? null : activity.describe(now, false));

		MemberTrail trail = trails.computeIfAbsent(name, key -> new MemberTrail());
		trail.observe(position, tickCounter);

		boolean moving = ghost.isMoving();
		int pose = GhostAnimations.poseFor(moving ? trail.getMovement() : Movement.STILL);
		ghost.setAnimation(pose, GhostAnimations.actionFor(activity, weaponOf(member), moving));
		ghost.onGameTick();

		updateTarget(view, member, position, local, transparency, ghost, activity, moving);
	}

	/**
	 * Turns the member to face whoever they're interacting with, and draws the NPC if it's a
	 * fight. The opponent stays while they move, since running from an NPC is still a fight.
	 * <p>
	 * We get the NPC's name and position but not its id, which a model needs. Members are in
	 * the same place on different worlds, so the same NPC is almost always in our scene too and
	 * we borrow its id. If it isn't, there's just no opponent drawn.
	 */
	private void updateTarget(WorldView view, GroupMember member, WorldPoint position, LocalPoint local,
		int transparency, Ghost ghost, @Nullable Activity activity, boolean moving)
	{
		String name = member.getName();
		String target = activity == null ? null : activity.getTarget();
		GroupMember.Interaction interaction = member.getInteraction();

		// The activity is worked out on the polling thread, so the interaction may already have
		// moved on to someone else. Only trust it when both agree on who.
		WorldPoint at = target != null && interaction != null && target.equals(interaction.getName())
			? interaction.getLocation()
			: null;
		LocalPoint atLocal = at != null && at.getPlane() == position.getPlane()
			&& at.distanceTo(position) <= INTERACTION_MAX_TILES
			? LocalPoint.fromWorld(view, at)
			: null;

		if (atLocal == null || !drawable(view, atLocal, at.getPlane()))
		{
			retireNpc(name);
			return;
		}

		if (!moving)
		{
			ghost.faceTowards(atLocal);
		}

		Integer npcId = GhostAnimations.isFighting(activity) && config.scenePresenceOpponents()
			? resolveNpcId(view, target)
			: null;
		if (npcId == null)
		{
			retireNpc(name);
			return;
		}

		Ghost opponent = npcGhosts.computeIfAbsent(name, key -> new Ghost(client, atLocal, at.getPlane()));
		opponent.setModel(models.forNpc(npcId, transparency));
		opponent.moveTo(atLocal, at.getPlane());
		opponent.faceTowards(local);
		opponent.setLabel(target, "Fighting " + name);
	}

	/**
	 * Whether we're willing to draw at a position. It has to be in the loaded scene, and on a
	 * real plane: the plane comes straight off the group's server and the client indexes arrays
	 * by it, so nothing outside 0-3 gets through.
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
	 * Whether the game is drawing this member already because they're on our world. Uses the
	 * reported world when there is one. Before their stats arrive there isn't, so check the
	 * scene for a player with their name instead.
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

	static boolean isOurWorld(int memberWorld, int localWorld)
	{
		return memberWorld > 0 && memberWorld == localWorld;
	}

	static boolean isRealPlane(int plane)
	{
		return plane >= 0 && plane < Constants.MAX_Z;
	}

	// Any NPC with the name has the right model. Misses aren't remembered, so it tries again
	// as the scene changes.
	@Nullable
	private Integer resolveNpcId(WorldView view, @Nullable String name)
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

	// Equipment arrives positionally, so a slot's index is the equipment slot and empty is 0.
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

	private WeaponClass weaponOf(GroupMember member)
	{
		List<ItemStack> equipment = member.getEquipment();
		int slot = EquipmentInventorySlot.WEAPON.getSlotIdx();
		ItemStack weapon = slot < equipment.size() ? equipment.get(slot) : null;
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
			// An id this client doesn't know yet.
			resolved = WeaponClass.UNARMED;
		}
		remember(weaponClasses, weapon.getId(), resolved);
		return resolved;
	}

	private static <K, V> void remember(Map<K, V> cache, K key, V value)
	{
		if (cache.size() >= LOOKUP_CACHE_LIMIT)
		{
			cache.clear();
		}
		cache.put(key, value);
	}

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
