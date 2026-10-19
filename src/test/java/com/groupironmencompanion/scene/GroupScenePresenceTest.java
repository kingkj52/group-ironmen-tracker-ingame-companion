package com.groupironmencompanion.scene;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The plane guard, which is where this feature stops trusting the server.
 * <p>
 * A member's coordinates are stored exactly as sent and never validated on the way in, so the
 * plane is whatever a server put in the third slot of an array. It ends up in the client's own
 * scene code, which indexes by it, and the four real planes are the only values that can
 * safely go there.
 */
public class GroupScenePresenceTest
{
	@Test
	public void acceptsTheFourRealPlanes()
	{
		for (int plane = 0; plane <= 3; plane++)
		{
			assertTrue("plane " + plane + " is real", GroupScenePresence.isRealPlane(plane));
		}
	}

	@Test
	public void refusesPlanesAboveTheTopFloor()
	{
		assertFalse(GroupScenePresence.isRealPlane(4));
		assertFalse(GroupScenePresence.isRealPlane(99_999));
		assertFalse(GroupScenePresence.isRealPlane(Integer.MAX_VALUE));
	}

	@Test
	public void refusesNegativePlanes()
	{
		assertFalse(GroupScenePresence.isRealPlane(-1));
		assertFalse(GroupScenePresence.isRealPlane(Integer.MIN_VALUE));
	}

	// ------------------------------------------------------------------
	// Whose job it is to draw a member
	// ------------------------------------------------------------------

	@Test
	public void aMemberOnOurOwnWorldIsTheGamesToDraw()
	{
		// The point of the feature is seeing someone who is not here. When they are, the real
		// player is standing there and a ghost would be a duplicate inside them.
		assertTrue(GroupScenePresence.isOurWorld(302, 302));
	}

	@Test
	public void aMemberOnAnotherWorldIsOursToDraw()
	{
		assertFalse(GroupScenePresence.isOurWorld(301, 302));
	}

	@Test
	public void anUnknownWorldIsNotTreatedAsOurs()
	{
		// getWorld returns -1 for a member who has reported a position but no stats yet.
		// Claiming that as our world would hide them; the caller falls back to looking for a
		// real player of that name instead.
		assertFalse(GroupScenePresence.isOurWorld(-1, 302));
		assertFalse(GroupScenePresence.isOurWorld(0, 0));
	}
}
