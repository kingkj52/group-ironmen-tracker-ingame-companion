package com.groupironmencompanion.scene;

import net.runelite.api.gameval.AnimationID;

/**
 * Picks an animation from what a member's reported state implies they are doing.
 * <p>
 * No animation id is sent over the wire, and there is nowhere to put one: the server rejects
 * an update carrying a field it does not know about, so the format cannot be extended from
 * this side. Everything here is therefore inferred from data the group already publishes —
 * position, worn equipment, the NPC being fought, and experience gained since the last poll.
 * <p>
 * The inference is a pure function of that state so it can be tested on its own, and so the
 * renderer has no business deciding what a member is doing.
 * <p>
 * What this cannot do is time a short action. Updates arrive about once a second while attack
 * animations fire every few ticks, so a sustained activity looks right and a brief one does
 * not. Planting a seed is the clearest example, and the honest result is a ghost that loops a
 * gathering motion near the patch rather than one that plants in step with its owner.
 */
final class GhostAnimations
{
	/**
	 * Game ticks between repeats of an action animation.
	 * <p>
	 * An action is a single swing, cast or swipe, not a continuous motion, so it plays once
	 * and the figure returns to its pose until the next one. Looping it instead produces a
	 * member who never stops mid-swing, which is the one thing nobody in the game ever looks
	 * like.
	 * <p>
	 * Four ticks is the common weapon speed and close enough for most gathering actions too.
	 * The true interval varies per weapon and is in the cache, but deriving it would be effort
	 * spent on precision the feed cannot support: positions and targets arrive about once a
	 * second, so a ghost's swings were never going to land in step with its owner's. The point
	 * is a figure that reads as fighting, not a reconstruction of the fight.
	 */
	static final int ACTION_INTERVAL_TICKS = 4;

	/** What a member appears to be doing, in the order the renderer resolves it. */
	enum Activity
	{
		IDLE,
		WALKING,
		RUNNING,
		FIGHTING,
		GATHERING
	}

	private GhostAnimations()
	{
	}

	/**
	 * The animation to play for a member, or -1 to leave them in their pose animation.
	 * <p>
	 * Movement wins over everything: a member who is fighting while moving is kiting, and
	 * showing the walk reads better than a swing that never lands. Fighting wins over
	 * gathering because {@code interacting} is a direct observation where a skill's experience
	 * delta is a guess.
	 */
	static int actionFor(Activity activity, WeaponClass weapon)
	{
		switch (activity)
		{
			case FIGHTING:
				return attackFor(weapon);
			case GATHERING:
				// A single generic gathering motion, rather than a per-skill guess. The skill
				// is known, but the animation for it depends on the tool, and a wrong tool
				// animation looks worse than a neutral one.
				return AnimationID.HUMAN_PICKUPFLOOR;
			case IDLE:
			case WALKING:
			case RUNNING:
			default:
				// Locomotion and standing still are pose animations, not actions.
				return -1;
		}
	}

	/** The pose animation underlying whatever action is playing. */
	static int poseFor(Activity activity)
	{
		switch (activity)
		{
			case RUNNING:
				return AnimationID.HUMAN_RUNNING;
			case WALKING:
				return AnimationID.HUMAN_WALK_F;
			case IDLE:
			case FIGHTING:
			case GATHERING:
			default:
				return AnimationID.HUMAN_READY;
		}
	}

	/**
	 * A representative attack animation per weapon category.
	 * <p>
	 * These are the plain human stances, not the weapon's own: a dragon scimitar's real
	 * animation lives in its stance table in the cache, which the plugin API does not expose.
	 * The result is recognisably the right kind of attack without being the exact one.
	 */
	private static int attackFor(WeaponClass weapon)
	{
		switch (weapon)
		{
			case BOW:
				return AnimationID.HUMAN_BOW;
			case CROSSBOW:
				return AnimationID.HUMAN_CROSSBOW;
			case THROWN:
				return AnimationID.HUMAN_THROW;
			case STAFF:
				return AnimationID.HUMAN_CASTING;
			case MELEE_STAB:
				return AnimationID.HUMAN_SWORD_STAB;
			case MELEE_CRUSH:
				return AnimationID.HUMAN_AXE_SMASH;
			case MELEE_SLASH:
				return AnimationID.HUMAN_SWORD_SLASH;
			case UNARMED:
			default:
				return AnimationID.HUMAN_UNARMEDPUNCH;
		}
	}

	/**
	 * Classifies movement from the distance covered between two reports.
	 * <p>
	 * Walk and run are not distinguished per tick, because a poll covers between one and two
	 * ticks and the reported positions are endpoints rather than a path. Measuring over the
	 * whole gap instead is self-correcting: whatever speed the ghost has to move at to arrive
	 * on time is the speed its animation should show.
	 *
	 * @param tiles        distance between the previous and current reported positions, in
	 *                     tiles, under either the game's own metric where a diagonal step
	 *                     counts as one or a straight-line measure where it counts as
	 *                     {@code sqrt(2)}; the threshold is chosen to separate walking from
	 *                     running under both
	 * @param elapsedTicks game ticks the move is being spread across, at least one
	 */
	static Activity movementFor(double tiles, int elapsedTicks)
	{
		if (tiles <= 0 || elapsedTicks <= 0)
		{
			return Activity.IDLE;
		}
		double tilesPerTick = tiles / elapsedTicks;
		if (tilesPerTick > RUN_THRESHOLD_TILES_PER_TICK)
		{
			return Activity.RUNNING;
		}
		return Activity.WALKING;
	}

	/**
	 * Walking is one tile per tick and running is two, so the threshold belongs between them.
	 * <p>
	 * It sits above {@code sqrt(2)} rather than just above 1.0 so that a diagonal step is
	 * still a walk however the caller measured it: a diagonal covers one tile of movement but
	 * 1.41 of straight-line distance, and reading that as a run makes every member who walks
	 * around a corner break into a sprint for a tick. Running diagonally measures 2.83, so
	 * there is plenty of room on the other side.
	 */
	private static final double RUN_THRESHOLD_TILES_PER_TICK = 1.7;
}
