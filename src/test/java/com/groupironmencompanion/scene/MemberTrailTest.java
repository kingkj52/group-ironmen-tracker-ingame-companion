package com.groupironmencompanion.scene;

import com.groupironmencompanion.scene.GhostAnimations.Activity;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The history that lets a slow feed drive a fast animation.
 * <p>
 * The case worth protecting is the mismatch between the two rates: positions land about once
 * a second and this is consulted once a game tick, so most ticks carry no news at all.
 */
public class MemberTrailTest
{
	private static WorldPoint at(int x, int y)
	{
		return new WorldPoint(x, y, 0);
	}

	@Test
	public void holdsTheClassificationOnTicksThatBringNoNewData()
	{
		// Three ticks pass; the position only changes on the first and the last, which is what
		// a one-second poll against a 0.6-second tick actually looks like.
		MemberTrail trail = new MemberTrail();
		trail.observe(at(3200, 3200), 1);
		trail.observe(at(3203, 3200), 2);

		Activity afterMove = trail.getMovement();
		assertEquals(Activity.RUNNING, afterMove);

		// A tick with the same position must not reclassify them as standing still.
		trail.observe(at(3203, 3200), 3);
		assertEquals("an unchanged position is no news, not a stop", afterMove, trail.getMovement());
	}

	@Test
	public void measuresSpeedBetweenRealChangesRatherThanPerTick()
	{
		// Two tiles covered across four ticks is a walk, even though the single jump that
		// delivered it was two tiles wide.
		MemberTrail trail = new MemberTrail();
		trail.observe(at(3200, 3200), 1);
		trail.observe(at(3202, 3200), 5);

		assertEquals(Activity.WALKING, trail.getMovement());
	}

	@Test
	public void twoTilesInOneTickIsARun()
	{
		MemberTrail trail = new MemberTrail();
		trail.observe(at(3200, 3200), 1);
		trail.observe(at(3202, 3200), 2);

		assertEquals(Activity.RUNNING, trail.getMovement());
	}

	@Test
	public void aDiagonalStepIsAWalk()
	{
		MemberTrail trail = new MemberTrail();
		trail.observe(at(3200, 3200), 1);
		trail.observe(at(3201, 3201), 2);

		assertEquals(Activity.WALKING, trail.getMovement());
	}

	@Test
	public void changingPlaneIsNotMovement()
	{
		// A staircase or a teleport, which should not read as sprinting up through a ceiling.
		MemberTrail trail = new MemberTrail();
		trail.observe(new WorldPoint(3200, 3200, 0), 1);
		trail.observe(new WorldPoint(3200, 3200, 1), 2);

		// IDLE is mapped to WALKING on read, since the caller only asks while the figure is
		// still closing a gap; what matters is that it is not reported as a run.
		assertEquals(Activity.WALKING, trail.getMovement());
	}

	// ------------------------------------------------------------------
	// Experience
	// ------------------------------------------------------------------

	@Test
	public void theFirstExperienceReadingIsNotAGain()
	{
		// Nothing to compare against yet, so a member who logs in mid-session must not look
		// like they just trained something.
		MemberTrail trail = new MemberTrail();
		assertFalse(trail.gainedXp(1_000_000));
	}

	@Test
	public void risingExperienceCountsOnceAndThenStops()
	{
		MemberTrail trail = new MemberTrail();
		trail.gainedXp(1_000_000);

		assertTrue(trail.gainedXp(1_000_050));
		assertFalse("the same total is not a fresh gain", trail.gainedXp(1_000_050));
	}

	@Test
	public void experienceGoingBackwardsIsNotAGain()
	{
		// Not expected, but the totals come off the wire and a nonsensical one should be inert
		// rather than being read as training.
		MemberTrail trail = new MemberTrail();
		trail.gainedXp(1_000_000);

		assertFalse(trail.gainedXp(5));
	}
}
