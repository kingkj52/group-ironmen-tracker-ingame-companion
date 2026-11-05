package com.groupironmencompanion.scene;

import java.util.Collections;
import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The world type rules. If someone trims the PvP list, this should fail. */
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
			assertTrue(type.name(), ScenePresenceGate.isForbiddenWorld(EnumSet.of(type)));
		}
	}

	@Test
	public void aMembersPvpWorldIsStillPvp()
	{
		assertTrue(ScenePresenceGate.isForbiddenWorld(EnumSet.of(WorldType.MEMBERS, WorldType.PVP)));
	}

	@Test
	public void anythingRuneLiteCallsPvpIsRefused()
	{
		// So a PvP world type added after this was written is covered.
		for (WorldType type : WorldType.values())
		{
			if (WorldType.isPvpWorld(EnumSet.of(type)))
			{
				assertTrue(type.name(), ScenePresenceGate.isForbiddenWorld(EnumSet.of(type)));
			}
		}
	}

	@Test
	public void refusesWhileTheWorldIsUnknown()
	{
		assertTrue(ScenePresenceGate.isForbiddenWorld(null));
	}
}
