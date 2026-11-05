package com.groupironmencompanion.scene;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Runs against the real bundled table, so it also catches a bad regeneration. */
public class GhostAppearanceTest
{
	// A platebody has sleeves and a chainbody doesn't, which is what the sleeve rule hangs on.
	private static final int RUNE_PLATEBODY = 1127;
	private static final int RUNE_CHAINBODY = 1113;
	private static final int RUNE_FULL_HELM = 1163;
	private static final int RUNE_PLATELEGS = 1079;
	private static final int ABYSSAL_WHIP = 4151;

	// Equipment slot indices.
	private static final int SLOT_HEAD = 0;
	private static final int SLOT_WEAPON = 3;
	private static final int SLOT_BODY = 4;
	private static final int SLOT_LEGS = 7;
	private static final int SLOT_RING = 12;

	private static GearTable table()
	{
		GearTable table = GearTable.load();
		assertNotNull("the bundled worn-model table must load", table);
		return table;
	}

	private static int[] wearing(int slot, int itemId)
	{
		int[] equipment = new int[14];
		equipment[slot] = itemId;
		return equipment;
	}

	@Test
	public void theBundledTableIsPresentAndPopulated()
	{
		GearTable table = table();
		assertTrue("the table should cover thousands of items", table.itemCount() > 5000);

		for (int part = 0; part <= 6; part++)
		{
			assertTrue("body part " + part + " needs a default model", table.bodyModel(part) > 0);
		}
	}

	/**
	 * A table built from an old cache looks fine but draws members as floating heads (it's
	 * happened). The only way to tell is to check for something only a recent cache has.
	 */
	@Test
	public void theTableWasBuiltFromARecentCache()
	{
		GearTable table = table();

		int[] sunlightCrossbow = table.wornModels(28869);
		assertNotNull("Hunters' sunlight crossbow is missing: the cache used to build the "
			+ "worn-model table predates Varlamore. Regenerate with tools/geardump against "
			+ "~/.runelite/jagexcache, not ~/jagexcache.", sunlightCrossbow);
		assertTrue(sunlightCrossbow[0] > 0);

		assertNotNull("Mixed hide top is missing from the worn-model table", table.wornModels(29280));
	}

	@Test
	public void anUnarmedMemberIsTheirOwnBody()
	{
		GearTable gear = table();
		int[] slots = GhostAppearance.resolve(new int[14], gear);

		assertEquals(gear.bodyModel(2), slots[GhostAppearance.TORSO]);
		assertEquals(gear.bodyModel(3), slots[GhostAppearance.ARMS]);
		assertEquals(gear.bodyModel(5), slots[GhostAppearance.LEGS]);
		assertEquals(gear.bodyModel(0), slots[GhostAppearance.HAIR]);

		assertEquals(-1, slots[GhostAppearance.WEAPON]);
		assertEquals(-1, slots[GhostAppearance.CAPE]);
		assertEquals(-1, slots[GhostAppearance.SHIELD]);
	}

	// Sleeves

	@Test
	public void aPlatebodyBringsItsOwnSleeves()
	{
		GearTable gear = table();
		int[] slots = GhostAppearance.resolve(wearing(SLOT_BODY, RUNE_PLATEBODY), gear);

		int[] models = gear.wornModels(RUNE_PLATEBODY);
		assertNotNull("the table must know a rune platebody", models);

		assertEquals("torso is the item", models[0], slots[GhostAppearance.TORSO]);
		assertEquals("arms are the item's sleeves", models[1], slots[GhostAppearance.ARMS]);
		assertTrue("a platebody has a second mesh", models[1] > 0);
	}

	@Test
	public void aChainbodyLeavesTheWearersArmsShowing()
	{
		GearTable gear = table();
		int[] slots = GhostAppearance.resolve(wearing(SLOT_BODY, RUNE_CHAINBODY), gear);

		int[] models = gear.wornModels(RUNE_CHAINBODY);
		assertNotNull("the table must know a rune chainbody", models);
		assertEquals("a chainbody has no second mesh", -1, models[1]);

		assertEquals("torso is the item", models[0], slots[GhostAppearance.TORSO]);
		assertEquals("arms fall back to the body", gear.bodyModel(3), slots[GhostAppearance.ARMS]);
	}

	// Headgear

	@Test
	public void headgearHidesHairAndBeard()
	{
		GearTable gear = table();
		int[] slots = GhostAppearance.resolve(wearing(SLOT_HEAD, RUNE_FULL_HELM), gear);

		int[] models = gear.wornModels(RUNE_FULL_HELM);
		assertNotNull(models);

		assertEquals("head is the helm", models[0], slots[GhostAppearance.HEAD]);
		assertEquals("hair is hidden under it", -1, slots[GhostAppearance.HAIR]);
		assertEquals("so is the beard", -1, slots[GhostAppearance.JAW]);
	}

	// Slots that cover nothing

	@Test
	public void legwearReplacesLegsAndNothingElse()
	{
		GearTable gear = table();
		int[] slots = GhostAppearance.resolve(wearing(SLOT_LEGS, RUNE_PLATELEGS), gear);

		assertEquals(gear.wornModels(RUNE_PLATELEGS)[0], slots[GhostAppearance.LEGS]);
		assertEquals("feet are untouched", gear.bodyModel(6), slots[GhostAppearance.BOOTS]);
	}

	@Test
	public void aWeaponIsHeldAndCoversNothing()
	{
		GearTable gear = table();
		int[] slots = GhostAppearance.resolve(wearing(SLOT_WEAPON, ABYSSAL_WHIP), gear);

		assertEquals(gear.wornModels(ABYSSAL_WHIP)[0], slots[GhostAppearance.WEAPON]);
		assertEquals(gear.bodyModel(3), slots[GhostAppearance.ARMS]);
		assertEquals(gear.bodyModel(2), slots[GhostAppearance.TORSO]);
	}

	@Test
	public void ringsAndAmmunitionAreNeverDrawn()
	{
		GearTable gear = table();
		int[] bare = GhostAppearance.resolve(new int[14], gear);
		int[] ringed = GhostAppearance.resolve(wearing(SLOT_RING, 1635), gear);

		org.junit.Assert.assertArrayEquals(bare, ringed);
	}

	// Robustness

	@Test
	public void anItemTheTableDoesNotKnowLeavesTheBodyShowing()
	{
		// Gear newer than the table.
		GearTable gear = table();
		int[] slots = GhostAppearance.resolve(wearing(SLOT_BODY, 99_999_999), gear);

		assertEquals(gear.bodyModel(2), slots[GhostAppearance.TORSO]);
	}

	@Test
	public void aShortOrAbsentEquipmentArrayIsHandled()
	{
		// No equipment reported yet, or a short container.
		GearTable gear = table();

		assertEquals(GhostAppearance.SLOT_COUNT,
			GhostAppearance.resolve(null, gear).length);
		assertEquals(GhostAppearance.SLOT_COUNT,
			GhostAppearance.resolve(new int[]{0, 0}, gear).length);
	}

	@Test
	public void everySlotHoldsEitherAMeshOrNothing()
	{
		GearTable gear = table();
		int[] equipment = new int[14];
		equipment[SLOT_HEAD] = RUNE_FULL_HELM;
		equipment[SLOT_BODY] = RUNE_PLATEBODY;
		equipment[SLOT_LEGS] = RUNE_PLATELEGS;
		equipment[SLOT_WEAPON] = ABYSSAL_WHIP;

		for (int model : GhostAppearance.resolve(equipment, gear))
		{
			assertTrue("a slot is either a real model id or -1", model == -1 || model > 0);
		}
	}
}
