package com.groupironmencompanion.scene;

import com.groupironmencompanion.activity.Activity;
import com.groupironmencompanion.data.GroupSkill;
import javax.annotation.Nullable;
import net.runelite.api.gameval.AnimationID;

/**
 * Picks a ghost's animations.
 * <p>
 * What a member is doing comes from their {@link Activity}, the same guess the status overlay
 * and side panel show, so the figure and the text always agree. How they're moving comes from
 * their position between updates. No client calls in here, so it's all testable.
 */
final class GhostAnimations
{
	/** Ticks between swings, chops and so on. Four is the usual weapon speed. */
	static final int ACTION_INTERVAL_TICKS = 4;

	// Walking is a tile a tick and running is two. Sits above sqrt(2) so a diagonal step
	// measured as a straight line still counts as walking.
	private static final double RUN_THRESHOLD_TILES_PER_TICK = 1.7;

	enum Movement
	{
		STILL,
		WALKING,
		RUNNING
	}

	private GhostAnimations()
	{
	}

	/**
	 * How fast someone covered a distance.
	 *
	 * @param tiles        distance between two reported positions
	 * @param elapsedTicks ticks between them, at least one
	 */
	static Movement movementFor(double tiles, int elapsedTicks)
	{
		if (tiles <= 0 || elapsedTicks <= 0)
		{
			return Movement.STILL;
		}
		return tiles / elapsedTicks > RUN_THRESHOLD_TILES_PER_TICK ? Movement.RUNNING : Movement.WALKING;
	}

	static int poseFor(Movement movement)
	{
		switch (movement)
		{
			case RUNNING:
				return AnimationID.HUMAN_RUNNING;
			case WALKING:
				return AnimationID.HUMAN_WALK_F;
			case STILL:
			default:
				return AnimationID.HUMAN_READY;
		}
	}

	/**
	 * The action to repeat, or -1 for none. Nobody swings or chops while moving, so a moving
	 * member gets no action, though they're still fighting as far as {@link #isFighting} goes.
	 */
	static int actionFor(@Nullable Activity activity, WeaponClass weapon, boolean moving)
	{
		if (activity == null || moving)
		{
			return -1;
		}
		switch (activity.getKind())
		{
			case FIGHTING:
				return attackFor(activity.getSkill(), weapon);
			case FISHING:
			case PICKPOCKETING:
			case SKILLING:
				return skillingFor(activity.getSkill());
			default:
				return -1;
		}
	}

	/** Moving doesn't end a fight: someone kiting or running from an NPC is still fighting it. */
	static boolean isFighting(@Nullable Activity activity)
	{
		return activity != null && activity.getKind() == Activity.Kind.FIGHTING;
	}

	// The plain human attack animations, not each weapon's own (those are in a stance table
	// the API doesn't expose). Close enough to read as the right kind of attack.
	private static int attackFor(@Nullable GroupSkill skill, WeaponClass weapon)
	{
		// XP says more than a weapon name we may not recognise.
		if (skill == GroupSkill.MAGIC)
		{
			return AnimationID.HUMAN_CASTING;
		}
		if (skill == GroupSkill.RANGED && weapon != WeaponClass.CROSSBOW && weapon != WeaponClass.THROWN)
		{
			return AnimationID.HUMAN_BOW;
		}

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

	// One representative animation per skill. Agility and Sailing have none: the movement
	// already says it.
	private static int skillingFor(@Nullable GroupSkill skill)
	{
		if (skill == null)
		{
			return -1;
		}
		switch (skill)
		{
			case WOODCUTTING:
				return AnimationID.HUMAN_WOODCUTTING_RUNE_AXE;
			case MINING:
				return AnimationID.HUMAN_MINING_RUNE_PICKAXE;
			case FISHING:
				return AnimationID.HUMAN_FISH_ONSPOT;
			case FARMING:
				return AnimationID.PICKING_LOW;
			case COOKING:
				return AnimationID.HUMAN_COOKING;
			case FIREMAKING:
				return AnimationID.HUMAN_CREATEFIRE;
			case SMITHING:
				return AnimationID.HUMAN_SMITHING;
			case HERBLORE:
				return AnimationID.HUMAN_HERBING_VIAL;
			case FLETCHING:
				return AnimationID.HUMAN_FLETCHING;
			case CRAFTING:
				return AnimationID.HUMAN_LEATHER_CRAFTING;
			case THIEVING:
				return AnimationID.HUMAN_PICKPOCKET;
			case RUNECRAFT:
				return AnimationID.HUMAN_RUNECRAFT;
			case MAGIC:
				return AnimationID.HUMAN_CASTING;
			case CONSTRUCTION:
				return AnimationID.HUMAN_POH_BUILD;
			case PRAYER:
			case HUNTER:
				return AnimationID.HUMAN_PICKUPFLOOR;
			default:
				return -1;
		}
	}
}
