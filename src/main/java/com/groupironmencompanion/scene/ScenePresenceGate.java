package com.groupironmencompanion.scene;

import com.groupironmencompanion.GroupIronmenCompanionConfig;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarbitID;

/**
 * Decides whether ghosts can be drawn at all.
 * <p>
 * Never in the Wilderness or on a PvP world, and there's deliberately no setting for that. A
 * figure you can't click is fine next to a farming patch, but in the Wilderness it looks like
 * a player and can stand between you and a real one. Don't add a setting for it.
 * <p>
 * Not in instances either, since coordinates there are remapped and a member's position
 * would land somewhere unrelated.
 */
@Singleton
public class ScenePresenceGate
{
	// Worlds where players can attack you anywhere, plus other combat worlds where a ghost
	// would mean nothing.
	private static final Set<WorldType> FORBIDDEN_WORLDS = EnumSet.of(
		WorldType.PVP,
		WorldType.BOUNTY,
		WorldType.HIGH_RISK,
		WorldType.DEADMAN,
		WorldType.LAST_MAN_STANDING,
		WorldType.PVP_ARENA,
		WorldType.TOURNAMENT_WORLD,
		WorldType.BETA_WORLD);

	@Inject
	private Client client;

	@Inject
	private GroupIronmenCompanionConfig config;

	/** Checked every tick, never cached: hopping or crossing the ditch changes the answer. */
	public boolean allowed()
	{
		if (!config.scenePresence()
			|| client.getGameState() != GameState.LOGGED_IN
			|| isForbiddenWorld(client.getWorldType())
			|| client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 0)
		{
			return false;
		}
		WorldView view = client.getTopLevelWorldView();
		return view != null && !view.isInstance();
	}

	static boolean isForbiddenWorld(Set<WorldType> types)
	{
		if (types == null)
		{
			// Not told yet. Draw nothing until we are.
			return true;
		}
		// RuneLite's own check first, so a PvP world type added later is covered too.
		if (WorldType.isPvpWorld(types))
		{
			return true;
		}
		for (WorldType type : types)
		{
			if (FORBIDDEN_WORLDS.contains(type))
			{
				return true;
			}
		}
		return false;
	}
}
