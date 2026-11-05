package com.groupironmencompanion.scene;

import java.util.Arrays;

/**
 * Works out which mesh goes in each of a figure's twelve appearance slots.
 * <p>
 * Starts with the body, then each equipped item replaces what it covers. An item's second mesh
 * is whatever it puts over the body underneath: a platebody's sleeves go in the arms slot,
 * while a chainbody has none and leaves the arms showing.
 * <p>
 * Headgear always hides hair and beard. Full helms and med helms look the same in the data,
 * and missing hair under a med helm is less noticeable than hair poking through a full helm.
 */
final class GhostAppearance
{
	// Appearance slots, in the game's order.
	static final int HEAD = 0;
	static final int CAPE = 1;
	static final int AMULET = 2;
	static final int WEAPON = 3;
	static final int TORSO = 4;
	static final int SHIELD = 5;
	static final int ARMS = 6;
	static final int LEGS = 7;
	static final int HAIR = 8;
	static final int HANDS = 9;
	static final int BOOTS = 10;
	static final int JAW = 11;

	static final int SLOT_COUNT = 12;

	// Male body kit parts, as numbered in the cache.
	private static final int KIT_HAIR = 0;
	private static final int KIT_JAW = 1;
	private static final int KIT_TORSO = 2;
	private static final int KIT_ARMS = 3;
	private static final int KIT_HANDS = 4;
	private static final int KIT_LEGS = 5;
	private static final int KIT_FEET = 6;

	// Equipment slot (EquipmentInventorySlot index) to appearance slot. Rings and ammo aren't drawn.
	private static final int[] EQUIPMENT_TO_SLOT = {
		HEAD,   // 0  head
		CAPE,   // 1  cape
		AMULET, // 2  amulet
		WEAPON, // 3  weapon
		TORSO,  // 4  body
		SHIELD, // 5  shield
		-1,     // 6
		LEGS,   // 7  legs
		-1,     // 8
		HANDS,  // 9  gloves
		BOOTS,  // 10 boots
		-1,     // 11
		-1,     // 12 ring
		-1      // 13 ammo
	};

	private GhostAppearance()
	{
	}

	/**
	 * The mesh for each slot, -1 where nothing is drawn.
	 *
	 * @param equippedItemIds item id per equipment slot, 0 for empty; may be short or null
	 */
	static int[] resolve(int[] equippedItemIds, GearTable table)
	{
		int[] slots = new int[SLOT_COUNT];
		Arrays.fill(slots, -1);

		slots[HAIR] = table.bodyModel(KIT_HAIR);
		slots[JAW] = table.bodyModel(KIT_JAW);
		slots[TORSO] = table.bodyModel(KIT_TORSO);
		slots[ARMS] = table.bodyModel(KIT_ARMS);
		slots[HANDS] = table.bodyModel(KIT_HANDS);
		slots[LEGS] = table.bodyModel(KIT_LEGS);
		slots[BOOTS] = table.bodyModel(KIT_FEET);

		if (equippedItemIds == null)
		{
			return slots;
		}

		int count = Math.min(equippedItemIds.length, EQUIPMENT_TO_SLOT.length);
		for (int equipSlot = 0; equipSlot < count; equipSlot++)
		{
			int slot = EQUIPMENT_TO_SLOT[equipSlot];
			int itemId = equippedItemIds[equipSlot];
			if (slot < 0 || itemId <= 0)
			{
				continue;
			}

			// Unknown to the table (newer than it): leave the body showing.
			int[] models = table.wornModels(itemId);
			if (models == null)
			{
				continue;
			}

			slots[slot] = models[0];
			applySecondary(slots, slot, models[1]);
		}
		return slots;
	}

	private static void applySecondary(int[] slots, int slot, int secondary)
	{
		switch (slot)
		{
			case TORSO:
				if (secondary > 0)
				{
					slots[ARMS] = secondary;
				}
				break;
			case HEAD:
				slots[HAIR] = -1;
				slots[JAW] = secondary > 0 ? secondary : -1;
				break;
			default:
				// A second mesh on anything else is rare and the data doesn't say where it goes,
				// so it's left off rather than guessed.
				break;
		}
	}
}
