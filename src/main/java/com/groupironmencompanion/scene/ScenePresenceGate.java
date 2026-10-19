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
 * The single place that decides whether group ghosts may be drawn at all.
 * <p>
 * Everything the scene renderer does goes through {@link #allowed()}. Keeping the decision in
 * one object means the restrictions below cannot be forgotten by a new caller, and means a
 * reviewer has one short file to read rather than a condition repeated across the renderer.
 * <p>
 * <b>The PvP cutout is deliberately not configurable.</b> A translucent figure that cannot be
 * clicked is harmless while two people farm next to each other on separate worlds, and
 * actively dangerous in the Wilderness: it reads as a player, it can sit between you and a
 * real one, and the whole point of being there is reacting correctly to figures on your
 * screen. There is no setting to turn this cutout off, and there must never be one, so the
 * worst case for a careless user is the feature silently stopping rather than misleading them
 * somewhere it matters.
 * <p>
 * Instances are excluded for a duller reason: inside one, world coordinates are remapped, so
 * a member's reported position would place their ghost somewhere unrelated.
 */
@Singleton
public class ScenePresenceGate
{
	/**
	 * World types where no ghost is ever drawn.
	 * <p>
	 * PvP, Bounty Hunter, High Risk, Deadman and Last Man Standing are all worlds where other
	 * players can attack you anywhere. PvP Arena, Tournament and Beta worlds are excluded as
	 * well: they are combat contexts, and the group's reported coordinates come from whatever
	 * world each member is actually on, so a ghost there would be meaningless anyway.
	 */
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

	/**
	 * Whether ghosts may be drawn right now. Client thread only.
	 * <p>
	 * Checked every tick rather than cached, because every input to it can change between one
	 * tick and the next: hopping worlds, stepping over the Wilderness ditch, or entering an
	 * instance.
	 */
	public boolean allowed()
	{
		if (!config.scenePresence())
		{
			return false;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}
		if (isForbiddenWorld(client.getWorldType()))
		{
			return false;
		}
		if (inWilderness())
		{
			return false;
		}

		WorldView view = client.getTopLevelWorldView();
		return view != null && !view.isInstance();
	}

	/**
	 * True on any world where ghosts are suppressed. Separated out as a pure function of the
	 * world types so the rule can be tested without a running client.
	 */
	static boolean isForbiddenWorld(Set<WorldType> types)
	{
		if (types == null)
		{
			// The client has not told us what kind of world this is yet. Draw nothing rather
			// than assume the safe case.
			return true;
		}

		// Defer to RuneLite's own idea of a PvP world first, so a world type added upstream
		// after this was written is covered without anyone remembering to come back here. The
		// explicit list below is the belt to that pair of braces, and covers worlds that are
		// combat contexts without being PvP in RuneLite's sense.
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

	/**
	 * Reads the game's own Wilderness flag rather than testing coordinates against a bounding
	 * box. The varbit is what the client uses to decide whether to show the Wilderness level
	 * on screen, so it already accounts for the awkward edges: the Wilderness Agility course,
	 * the underground sections, and the areas that look like Wilderness on the map but are not.
	 */
	private boolean inWilderness()
	{
		return client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 0;
	}
}
