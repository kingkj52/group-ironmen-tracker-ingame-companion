package com.groupironmencompanion.scene;

import com.groupironmencompanion.scene.GhostAnimations.Activity;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The inference that stands in for animation data the wire format does not carry. */
public class GhostAnimationsTest
{
	// ------------------------------------------------------------------
	// Movement
	// ------------------------------------------------------------------

	@Test
	public void standingStillIsIdle()
	{
		assertEquals(Activity.IDLE, GhostAnimations.movementFor(0, 1));
	}

	@Test
	public void oneTilePerTickWalks()
	{
		assertEquals(Activity.WALKING, GhostAnimations.movementFor(1, 1));
	}

	@Test
	public void twoTilesPerTickRuns()
	{
		assertEquals(Activity.RUNNING, GhostAnimations.movementFor(2, 1));
	}

	@Test
	public void aDiagonalWalkIsStillAWalk()
	{
		// A diagonal step covers one tile of game movement but measures as more than one by
		// straight-line distance, which is why the run threshold sits above 1.0.
		assertEquals(Activity.WALKING, GhostAnimations.movementFor(Math.sqrt(2), 1));
	}

	@Test
	public void speedIsMeasuredAcrossTheWholeGap()
	{
		// Four tiles spread over four ticks is a walk, not a run, even though the single
		// reported jump was four tiles wide.
		assertEquals(Activity.WALKING, GhostAnimations.movementFor(4, 4));
	}

	// ------------------------------------------------------------------
	// Pose and action
	// ------------------------------------------------------------------

	@Test
	public void locomotionDrivesThePoseNotTheAction()
	{
		assertEquals(AnimationID.HUMAN_RUNNING, GhostAnimations.poseFor(Activity.RUNNING));
		assertEquals(AnimationID.HUMAN_WALK_F, GhostAnimations.poseFor(Activity.WALKING));
		assertEquals(AnimationID.HUMAN_READY, GhostAnimations.poseFor(Activity.IDLE));

		assertTrue("a walking figure has no action animation",
			GhostAnimations.actionFor(Activity.WALKING, WeaponClass.MELEE_SLASH) < 0);
	}

	@Test
	public void fightingPicksAnAttackForTheWeaponCategory()
	{
		assertEquals(AnimationID.HUMAN_BOW,
			GhostAnimations.actionFor(Activity.FIGHTING, WeaponClass.BOW));
		assertEquals(AnimationID.HUMAN_CROSSBOW,
			GhostAnimations.actionFor(Activity.FIGHTING, WeaponClass.CROSSBOW));
		assertEquals(AnimationID.HUMAN_CASTING,
			GhostAnimations.actionFor(Activity.FIGHTING, WeaponClass.STAFF));
		assertEquals(AnimationID.HUMAN_UNARMEDPUNCH,
			GhostAnimations.actionFor(Activity.FIGHTING, WeaponClass.UNARMED));
	}

	@Test
	public void aFightingFigureStandsRatherThanWalks()
	{
		assertEquals(AnimationID.HUMAN_READY, GhostAnimations.poseFor(Activity.FIGHTING));
	}

	@Test
	public void anActionRepeatsOnAWeaponSpeedCadenceRatherThanLooping()
	{
		// Looping the swing was the first attempt and it reads as a member who never stops
		// mid-attack. Four ticks is the common weapon speed, and the gap between swings is
		// what makes the figure look like it is fighting rather than vibrating.
		assertTrue("an action must have a gap between repeats",
			GhostAnimations.ACTION_INTERVAL_TICKS > 1);
		assertEquals(4, GhostAnimations.ACTION_INTERVAL_TICKS);
	}

	// ------------------------------------------------------------------
	// Weapon classification
	// ------------------------------------------------------------------

	@Test
	public void classifiesTheObviousWeapons()
	{
		assertEquals(WeaponClass.MELEE_SLASH, WeaponClass.classify("Dragon scimitar"));
		assertEquals(WeaponClass.MELEE_STAB, WeaponClass.classify("Dragon dagger"));
		assertEquals(WeaponClass.MELEE_CRUSH, WeaponClass.classify("Granite maul"));
		assertEquals(WeaponClass.BOW, WeaponClass.classify("Magic shortbow"));
		assertEquals(WeaponClass.THROWN, WeaponClass.classify("Rune knife"));
	}

	@Test
	public void prefersTheMoreSpecificWordWhenANameContainsSeveral()
	{
		// "Dragon crossbow" contains "bow", and a staff of the dead contains neither sword nor
		// blade but must not fall through to unarmed.
		assertEquals(WeaponClass.CROSSBOW, WeaponClass.classify("Dragon crossbow"));
		assertEquals(WeaponClass.STAFF, WeaponClass.classify("Staff of the dead"));
		assertEquals(WeaponClass.STAFF, WeaponClass.classify("Trident of the swamp"));
	}

	@Test
	public void anEmptyWeaponSlotIsUnarmed()
	{
		assertEquals(WeaponClass.UNARMED, WeaponClass.classify(null));
		assertEquals(WeaponClass.UNARMED, WeaponClass.classify(""));
	}

	@Test
	public void somethingUnrecognisedFallsBackToUnarmed()
	{
		// A skilling tool or a novelty item in the weapon slot. Punching is a better guess
		// than picking an attack style at random.
		assertEquals(WeaponClass.UNARMED, WeaponClass.classify("Bucket"));
	}
}
