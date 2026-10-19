package com.groupironmencompanion.scene;

import java.util.Collections;
import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The world-type half of the gate, which is the half that can be tested without a client.
 * <p>
 * These are not decoration. The feature is only defensible because it refuses to draw where a
 * figure could be mistaken for a threat, so the rule deserves a test that fails loudly if
 * somebody trims the list.
 */
public class ScenePresenceGateTest
{
	@Test
	public void drawsOnAnOrdinaryWorld()
	{
		assertFalse(ScenePresenceGate.isForbiddenWorld(EnumSet.of(WorldType.MEMBERS)));
		assertFalse(ScenePresenceGate.isForbiddenWorld(Collections.emptySet()));
	}

	@Test
	public void refusesEveryWorldWherePlayersCanAttackEachOther()
	{
		for (WorldType type : new WorldType[]{
			WorldType.PVP,
			WorldType.BOUNTY,
			WorldType.HIGH_RISK,
			WorldType.DEADMAN,
			WorldType.LAST_MAN_STANDING,
			WorldType.PVP_ARENA})
		{
			assertTrue(type + " must suppress ghosts",
				ScenePresenceGate.isForbiddenWorld(EnumSet.of(type)));
		}
	}

	@Test
	public void refusesAWorldThatIsOnlyPartlyForbidden()
	{
		// A members PvP world is still a PvP world.
		assertTrue(ScenePresenceGate.isForbiddenWorld(EnumSet.of(WorldType.MEMBERS, WorldType.PVP)));
	}

	@Test
	public void inheritsRuneLitesOwnIdeaOfAPvpWorld()
	{
		// The explicit list cannot know about a world type added upstream later, so whatever
		// RuneLite itself counts as PvP must be refused too.
		for (WorldType type : WorldType.values())
		{
			if (WorldType.isPvpWorld(EnumSet.of(type)))
			{
				assertTrue(type + " is a PvP world to RuneLite and must suppress ghosts",
					ScenePresenceGate.isForbiddenWorld(EnumSet.of(type)));
			}
		}
	}

	@Test
	public void refusesWhenTheWorldIsNotKnownYet()
	{
		// Logging in, hopping, or any state where the client has not said what this world is.
		// Drawing nothing is the only safe default.
		assertTrue(ScenePresenceGate.isForbiddenWorld(null));
	}
}
