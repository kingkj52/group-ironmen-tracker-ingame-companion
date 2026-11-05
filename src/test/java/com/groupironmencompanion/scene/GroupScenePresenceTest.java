package com.groupironmencompanion.scene;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupScenePresenceTest
{
	// The plane comes straight from the group's server and the client indexes arrays by it.

	@Test
	public void acceptsTheFourRealPlanes()
	{
		for (int plane = 0; plane <= 3; plane++)
		{
			assertTrue(GroupScenePresence.isRealPlane(plane));
		}
	}

	@Test
	public void refusesAnythingElse()
	{
		assertFalse(GroupScenePresence.isRealPlane(4));
		assertFalse(GroupScenePresence.isRealPlane(99_999));
		assertFalse(GroupScenePresence.isRealPlane(Integer.MAX_VALUE));
		assertFalse(GroupScenePresence.isRealPlane(-1));
		assertFalse(GroupScenePresence.isRealPlane(Integer.MIN_VALUE));
	}

	// Members on our own world are drawn by the game, not by us.

	@Test
	public void ourWorldIsTheGamesToDraw()
	{
		assertTrue(GroupScenePresence.isOurWorld(302, 302));
		assertFalse(GroupScenePresence.isOurWorld(301, 302));
	}

	@Test
	public void anUnknownWorldIsNotOurs()
	{
		// -1 before their stats arrive. The caller then checks the scene for them instead.
		assertFalse(GroupScenePresence.isOurWorld(-1, 302));
		assertFalse(GroupScenePresence.isOurWorld(0, 0));
	}
}
