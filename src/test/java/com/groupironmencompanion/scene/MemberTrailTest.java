package com.groupironmencompanion.scene;

import com.groupironmencompanion.scene.GhostAnimations.Movement;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MemberTrailTest
{
	private static WorldPoint at(int x, int y)
	{
		return new WorldPoint(x, y, 0);
	}

	@Test
	public void ticksWithNoNewPositionDoNotMeanTheyStopped()
	{
		// Positions land about every other tick. The ticks in between mustn't reset a run.
		MemberTrail trail = new MemberTrail();
		trail.observe(at(3200, 3200), 1);
		trail.observe(at(3203, 3200), 2);
		assertEquals(Movement.RUNNING, trail.getMovement());

		trail.observe(at(3203, 3200), 3);
		assertEquals(Movement.RUNNING, trail.getMovement());
	}

	@Test
	public void speedIsMeasuredBetweenRealChanges()
	{
		MemberTrail trail = new MemberTrail();
		trail.observe(at(3200, 3200), 1);
		trail.observe(at(3202, 3200), 5);
		assertEquals(Movement.WALKING, trail.getMovement());
	}

	@Test
	public void twoTilesInATickIsARun()
	{
		MemberTrail trail = new MemberTrail();
		trail.observe(at(3200, 3200), 1);
		trail.observe(at(3202, 3200), 2);
		assertEquals(Movement.RUNNING, trail.getMovement());
	}

	@Test
	public void aDiagonalStepIsAWalk()
	{
		MemberTrail trail = new MemberTrail();
		trail.observe(at(3200, 3200), 1);
		trail.observe(at(3201, 3201), 2);
		assertEquals(Movement.WALKING, trail.getMovement());
	}

	@Test
	public void changingPlaneIsNotARun()
	{
		MemberTrail trail = new MemberTrail();
		trail.observe(new WorldPoint(3200, 3200, 0), 1);
		trail.observe(new WorldPoint(3200, 3200, 1), 2);
		assertEquals(Movement.WALKING, trail.getMovement());
	}
}
