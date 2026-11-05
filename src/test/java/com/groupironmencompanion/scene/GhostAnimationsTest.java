package com.groupironmencompanion.scene;

import com.groupironmencompanion.activity.Activities;
import com.groupironmencompanion.data.GroupSkill;
import com.groupironmencompanion.scene.GhostAnimations.Movement;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GhostAnimationsTest
{
	// Movement

	@Test
	public void movementSpeeds()
	{
		assertEquals(Movement.STILL, GhostAnimations.movementFor(0, 1));
		assertEquals(Movement.WALKING, GhostAnimations.movementFor(1, 1));
		assertEquals(Movement.RUNNING, GhostAnimations.movementFor(2, 1));
	}

	@Test
	public void aDiagonalStepIsAWalk()
	{
		assertEquals(Movement.WALKING, GhostAnimations.movementFor(Math.sqrt(2), 1));
	}

	@Test
	public void speedIsAveragedOverTheGap()
	{
		assertEquals(Movement.WALKING, GhostAnimations.movementFor(4, 4));
	}

	@Test
	public void poses()
	{
		assertEquals(AnimationID.HUMAN_RUNNING, GhostAnimations.poseFor(Movement.RUNNING));
		assertEquals(AnimationID.HUMAN_WALK_F, GhostAnimations.poseFor(Movement.WALKING));
		assertEquals(AnimationID.HUMAN_READY, GhostAnimations.poseFor(Movement.STILL));
	}

	// Actions follow the same activity the status text shows

	@Test
	public void fightingSwingsTheWeaponTheyHold()
	{
		assertEquals(AnimationID.HUMAN_SWORD_SLASH,
			GhostAnimations.actionFor(Activities.fighting("Goblin", GroupSkill.STRENGTH), WeaponClass.MELEE_SLASH, false));
		assertEquals(AnimationID.HUMAN_CROSSBOW,
			GhostAnimations.actionFor(Activities.fighting("Goblin", GroupSkill.RANGED), WeaponClass.CROSSBOW, false));
	}

	@Test
	public void combatXpBeatsAWeaponNameWeDidNotRecognise()
	{
		// A mage with a staff whose name doesn't contain "staff" still casts.
		assertEquals(AnimationID.HUMAN_CASTING,
			GhostAnimations.actionFor(Activities.fighting("Goblin", GroupSkill.MAGIC), WeaponClass.UNARMED, false));
		assertEquals(AnimationID.HUMAN_BOW,
			GhostAnimations.actionFor(Activities.fightingByExperience(GroupSkill.RANGED), WeaponClass.UNARMED, false));
	}

	@Test
	public void skillingPlaysThatSkill()
	{
		assertEquals(AnimationID.HUMAN_WOODCUTTING_RUNE_AXE,
			GhostAnimations.actionFor(Activities.skilling(GroupSkill.WOODCUTTING), WeaponClass.UNARMED, false));
		assertEquals(AnimationID.PICKING_LOW,
			GhostAnimations.actionFor(Activities.skilling(GroupSkill.FARMING), WeaponClass.UNARMED, false));
		assertEquals(AnimationID.HUMAN_FISH_ONSPOT,
			GhostAnimations.actionFor(Activities.fishing("Fishing spot"), WeaponClass.UNARMED, false));
	}

	@Test
	public void magicOnItsOwnCastsButIsNotAFight()
	{
		// Alching: the status says "Magic", so no opponent, but the figure still casts.
		assertEquals(AnimationID.HUMAN_CASTING,
			GhostAnimations.actionFor(Activities.skilling(GroupSkill.MAGIC), WeaponClass.MELEE_SLASH, false));
		assertFalse(GhostAnimations.isFighting(Activities.skilling(GroupSkill.MAGIC)));
	}

	@Test
	public void talkingToSomeoneIsNotAnAttack()
	{
		// This used to swing at shopkeepers.
		assertEquals(-1, GhostAnimations.actionFor(Activities.talking("Hans"), WeaponClass.MELEE_SLASH, false));
		assertFalse(GhostAnimations.isFighting(Activities.talking("Hans")));
	}

	@Test
	public void nothingToShowForIdleOrUnknown()
	{
		assertEquals(-1, GhostAnimations.actionFor(Activities.idle(), WeaponClass.MELEE_SLASH, false));
		assertEquals(-1, GhostAnimations.actionFor(null, WeaponClass.MELEE_SLASH, false));
	}

	@Test
	public void nobodySwingsOnTheMoveButTheFightGoesOn()
	{
		assertEquals(-1, GhostAnimations.actionFor(Activities.fighting("Goblin", GroupSkill.ATTACK), WeaponClass.MELEE_STAB, true));
		assertTrue(GhostAnimations.isFighting(Activities.fighting("Goblin", GroupSkill.ATTACK)));
	}

	@Test
	public void actionsRepeatOnAWeaponSpeedNotEveryTick()
	{
		assertEquals(4, GhostAnimations.ACTION_INTERVAL_TICKS);
	}

	// Weapon names

	@Test
	public void classifiesCommonWeapons()
	{
		assertEquals(WeaponClass.MELEE_SLASH, WeaponClass.classify("Dragon scimitar"));
		assertEquals(WeaponClass.MELEE_STAB, WeaponClass.classify("Dragon dagger"));
		assertEquals(WeaponClass.MELEE_CRUSH, WeaponClass.classify("Granite maul"));
		assertEquals(WeaponClass.BOW, WeaponClass.classify("Magic shortbow"));
		assertEquals(WeaponClass.THROWN, WeaponClass.classify("Rune knife"));
	}

	@Test
	public void moreSpecificNamesWin()
	{
		assertEquals(WeaponClass.CROSSBOW, WeaponClass.classify("Dragon crossbow"));
		assertEquals(WeaponClass.STAFF, WeaponClass.classify("Staff of the dead"));
		assertEquals(WeaponClass.STAFF, WeaponClass.classify("Trident of the swamp"));
	}

	@Test
	public void partisansAreStabWeapons()
	{
		// Used to land under STAFF and cast spells.
		assertEquals(WeaponClass.MELEE_STAB, WeaponClass.classify("Keris partisan"));
		assertEquals(WeaponClass.MELEE_STAB, WeaponClass.classify("Keris partisan of the sun"));
	}

	@Test
	public void unknownOrEmptyIsUnarmed()
	{
		assertEquals(WeaponClass.UNARMED, WeaponClass.classify(null));
		assertEquals(WeaponClass.UNARMED, WeaponClass.classify(""));
		assertEquals(WeaponClass.UNARMED, WeaponClass.classify("Bucket"));
	}
}
