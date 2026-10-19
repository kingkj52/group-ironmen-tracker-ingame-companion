package com.groupironmencompanion.scene;

import com.groupironmencompanion.scene.GhostAnimations.Activity;
import net.runelite.api.coords.WorldPoint;

/**
 * One member's recent history, enough to tell movement and training apart.
 * <p>
 * The subtlety this exists to handle is that positions arrive more slowly than they are
 * consumed. A poll lands about once a second and this is asked once a game tick, so on most
 * ticks a member's reported position is simply the same one as last time. Treating that as
 * "standing still" makes a running member flicker between running and standing every other
 * tick, which is the wrong answer for a reason that has nothing to do with what they are
 * actually doing.
 * <p>
 * So speed is measured between the ticks where the position genuinely changed, and the
 * resulting classification is held rather than recomputed. Whether the figure is still moving
 * is then the renderer's question, not this one: it knows how far its ghost has left to ease.
 */
class MemberTrail
{
	private WorldPoint lastPosition;
	private int lastChangeTick = -1;
	private long lastTotalXp = -1;

	/** The last classification made from a real position change. */
	private Activity movement = Activity.IDLE;

	/**
	 * Records a reported position, reclassifying speed only when it actually changed.
	 *
	 * @param position the member's newest reported position
	 * @param tick     a monotonically increasing game tick counter
	 */
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
			// No new data, or genuinely not moving. Either way there is nothing to measure,
			// and the previous classification stands.
			return;
		}

		if (lastPosition.getPlane() != position.getPlane())
		{
			// A plane change is a staircase or a teleport, not a walk.
			movement = Activity.IDLE;
		}
		else
		{
			int elapsed = Math.max(1, tick - lastChangeTick);
			movement = GhostAnimations.movementFor(lastPosition.distanceTo2D(position), elapsed);
		}

		lastPosition = position;
		lastChangeTick = tick;
	}

	/**
	 * How the member was last seen moving. Only meaningful while their figure still has
	 * ground to cover; once it arrives the renderer stops asking.
	 */
	Activity getMovement()
	{
		return movement == Activity.IDLE ? Activity.WALKING : movement;
	}

	/**
	 * Whether total experience rose since the last call, which stands in for "doing something
	 * that trains a skill" when a member is stationary and not fighting.
	 */
	boolean gainedXp(long totalXp)
	{
		boolean gained = lastTotalXp >= 0 && totalXp > lastTotalXp;
		lastTotalXp = totalXp;
		return gained;
	}
}
