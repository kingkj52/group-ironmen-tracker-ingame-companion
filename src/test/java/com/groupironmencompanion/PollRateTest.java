package com.groupironmencompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How often the plugin is willing to ask the group server for anything.
 * <p>
 * This is the one number in the plugin that other people pay for. The server is a free
 * service run by one person, the plugin is on the hub, and how many clients are running it is
 * not something this code gets to assume. So the rate is a rule with tests rather than an
 * expression inline in the polling loop.
 */
public class PollRateTest
{
	private static final long IDLE_FLOOR = 30_000;

	@Test
	public void whilePlayingItPollsAtTheConfiguredRate()
	{
		assertEquals(3_000, GroupIronmenCompanionPlugin.pollIntervalMillis(3, true));
		assertEquals(1_000, GroupIronmenCompanionPlugin.pollIntervalMillis(1, true));
		assertEquals(60_000, GroupIronmenCompanionPlugin.pollIntervalMillis(60, true));
	}

	@Test
	public void whileLoggedOutItBacksOff()
	{
		// A client left on the login screen used to ask every three seconds forever. Nobody is
		// looking at it, and it is somebody else's bandwidth.
		assertEquals(IDLE_FLOOR, GroupIronmenCompanionPlugin.pollIntervalMillis(3, false));
		assertEquals(IDLE_FLOOR, GroupIronmenCompanionPlugin.pollIntervalMillis(1, false));
	}

	@Test
	public void backingOffNeverSpeedsUpAPollTheUserAskedToSlow()
	{
		// Someone who set a minute between refreshes wants a minute, not thirty seconds,
		// and the idle floor must not become an idle ceiling.
		assertEquals(60_000, GroupIronmenCompanionPlugin.pollIntervalMillis(60, false));
		assertEquals(45_000, GroupIronmenCompanionPlugin.pollIntervalMillis(45, false));
	}

	@Test
	public void idleIsAlwaysAtLeastAsSlowAsPlaying()
	{
		// The property that matters, across the whole configurable range: being logged out can
		// only ever reduce traffic.
		for (int seconds = 1; seconds <= 60; seconds++)
		{
			long playing = GroupIronmenCompanionPlugin.pollIntervalMillis(seconds, true);
			long idle = GroupIronmenCompanionPlugin.pollIntervalMillis(seconds, false);
			assertTrue("at " + seconds + "s, idle polling must not be faster than playing",
				idle >= playing);
		}
	}

	@Test
	public void aNonsensicalIntervalStillHasAFloor()
	{
		// The setting is bounded by @Range, but nothing stops a hand-edited config file, and a
		// zero or negative interval would mean a request every time the scheduler ticks.
		assertTrue(GroupIronmenCompanionPlugin.pollIntervalMillis(0, true) >= 1_000);
		assertTrue(GroupIronmenCompanionPlugin.pollIntervalMillis(-5, true) >= 1_000);
		assertTrue(GroupIronmenCompanionPlugin.pollIntervalMillis(Integer.MIN_VALUE, true) >= 1_000);
	}
}
