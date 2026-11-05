package com.groupironmencompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** How often we hit the group's server. It's someone else's free service, so this matters. */
public class PollRateTest
{
	private static final long IDLE_FLOOR = 30_000;

	@Test
	public void pollsAtTheConfiguredRateWhilePlaying()
	{
		assertEquals(3_000, GroupIronmenCompanionPlugin.pollIntervalMillis(3, true));
		assertEquals(1_000, GroupIronmenCompanionPlugin.pollIntervalMillis(1, true));
		assertEquals(60_000, GroupIronmenCompanionPlugin.pollIntervalMillis(60, true));
	}

	@Test
	public void backsOffWhileLoggedOut()
	{
		assertEquals(IDLE_FLOOR, GroupIronmenCompanionPlugin.pollIntervalMillis(3, false));
		assertEquals(IDLE_FLOOR, GroupIronmenCompanionPlugin.pollIntervalMillis(1, false));
	}

	@Test
	public void neverSpeedsUpSomeoneWhoChoseSlower()
	{
		assertEquals(60_000, GroupIronmenCompanionPlugin.pollIntervalMillis(60, false));
		assertEquals(45_000, GroupIronmenCompanionPlugin.pollIntervalMillis(45, false));
	}

	@Test
	public void loggedOutIsNeverFasterThanPlaying()
	{
		for (int seconds = 1; seconds <= 60; seconds++)
		{
			assertTrue(GroupIronmenCompanionPlugin.pollIntervalMillis(seconds, false)
				>= GroupIronmenCompanionPlugin.pollIntervalMillis(seconds, true));
		}
	}

	@Test
	public void aBrokenSettingStillHasAFloor()
	{
		// @Range stops this in the settings panel, not in a hand-edited config.
		assertTrue(GroupIronmenCompanionPlugin.pollIntervalMillis(0, true) >= 1_000);
		assertTrue(GroupIronmenCompanionPlugin.pollIntervalMillis(-5, true) >= 1_000);
		assertTrue(GroupIronmenCompanionPlugin.pollIntervalMillis(Integer.MIN_VALUE, true) >= 1_000);
	}
}
