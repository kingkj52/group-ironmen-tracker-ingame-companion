package com.groupironmencompanion.scene;

import com.groupironmencompanion.scene.GhostAnimations.Movement;
import net.runelite.api.coords.WorldPoint;

/**
 * Tracks how fast a member is moving.
 * <p>
 * Positions arrive less often than this is asked, so most ticks bring the same position as
 * last time. Calling that "stopped" made a running member flicker between running and
 * standing, so speed is only measured when the position actually changes, and held until it
 * does again. Whether the figure is still moving is up to the {@link Ghost}, which knows how
 * far it has left to go.
 */
class MemberTrail
{
	private WorldPoint lastPosition;
	private int lastChangeTick = -1;
	private Movement movement = Movement.STILL;

	/** @param tick a game tick counter that only ever goes up */
	void observe(WorldPoint position, int tick)
	{
		if (lastPosition == null)
		{
			lastPosition = position;
			lastChangeTick = tick;
			return;
		}
		if (lastPosition.equals(position))
		{
			return;
		}

		if (lastPosition.getPlane() != position.getPlane())
		{
			// Stairs or a teleport, not a walk.
			movement = Movement.STILL;
		}
		else
		{
			int elapsed = Math.max(1, tick - lastChangeTick);
			movement = GhostAnimations.movementFor(lastPosition.distanceTo2D(position), elapsed);
		}

		lastPosition = position;
		lastChangeTick = tick;
	}

	/** How they were last seen moving. Only asked while the figure is moving, so never STILL. */
	Movement getMovement()
	{
		return movement == Movement.STILL ? Movement.WALKING : movement;
	}
}
