package com.groupironmencompanion.scene;

import java.util.Arrays;

/**
 * Works out which mesh belongs in each part of a figure, from what a member has equipped.
 * <p>
 * This is how the game builds a player: twelve appearance slots, each holding either a piece
 * of the wearer's own body or a piece of something they are wearing. Start with the body, then
 * let each equipped item replace the slots it covers.
 * <p>
 * The interesting case is sleeves, and it resolves itself. An item's second mesh is the piece
 * that stands in for the body part beneath it, so a platebody ({@code 306, 164}) puts its own
 * sleeves in the arms slot while a chainbody ({@code 301, -1}) supplies nothing and leaves the
 * wearer's arms showing. No table of what-covers-what is needed; the presence of a second mesh
 * is the answer.
 * <p>
 * Headgear is the one place this is deliberately coarser than the game. A full helm hides hair
 * and beard while a medium helm hides only hair, and nothing in the item data distinguishes
 * them - both are a single mesh with a chathead. Rather than guess per item, any headgear
 * hides both. On a translucent silhouette the missing hair under a med helm is invisible,
 * whereas hair erupting through a great helm would not be.
 * <p>
 * Pure, so the rules can be tested without a client or a cache.
 */
final class GhostAppearance
{
	// Appearance slots, in the order the game indexes them.
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

	// Male identity kit body parts, as grouped by bodyPartId in the cache.
	private static final int KIT_HAIR = 0;
	private static final int KIT_JAW = 1;
	private static final int KIT_TORSO = 2;
	private static final int KIT_ARMS = 3;
	private static final int KIT_HANDS = 4;
	private static final int KIT_LEGS = 5;
	private static final int KIT_FEET = 6;

	/**
	 * Which appearance slot each equipment slot feeds, indexed by
	 * {@code EquipmentInventorySlot.getSlotIdx()}. Rings and ammunition are worn but never
	 * drawn on a player, so they map to nothing.
	 */
	private static final int[] EQUIPMENT_TO_SLOT = {
		HEAD,   // 0  head
		CAPE,   // 1  cape
		AMULET, // 2  amulet
		WEAPON, // 3  weapon
		TORSO,  // 4  body
		SHIELD, // 5  shield
		-1,     // 6  unused
		LEGS,   // 7  legs
		-1,     // 8  unused
		HANDS,  // 9  gloves
		BOOTS,  // 10 boots
		-1,     // 11 unused
		-1,     // 12 ring
		-1      // 13 ammunition
	};

	private GhostAppearance()
	{
	}

	/**
	 * The mesh for each appearance slot, or -1 where nothing is drawn.
	 *
	 * @param equippedItemIds item id per equipment slot, 0 or -1 for an empty slot; a short
	 *                        array is fine and the missing slots count as empty
	 * @param table           the bundled worn-model data
	 */
	static int[] resolve(int[] equippedItemIds, GearTable table)
	{
		int[] slots = new int[SLOT_COUNT];
		Arrays.fill(slots, -1);

		// The wearer's own body first, which anything equipped then covers.
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

		for (int equipSlot = 0; equipSlot < equippedItemIds.length; equipSlot++)
		{
			if (equipSlot >= EQUIPMENT_TO_SLOT.length)
			{
				break;
			}

			int slot = EQUIPMENT_TO_SLOT[equipSlot];
			if (slot < 0)
			{
				continue;
			}

			int itemId = equippedItemIds[equipSlot];
			if (itemId <= 0)
			{
				continue;
			}

			int[] models = table.wornModels(itemId);
			if (models == null)
			{
				// An item the bundled table predates. Leaving the body part showing is a
				// better outcome than drawing nothing at all for that slot.
				continue;
			}

			slots[slot] = models[0];
			applySecondary(slots, slot, models[1]);
		}

		return slots;
	}

	/**
	 * Places an item's second mesh in the body part it stands in for, and handles the one slot
	 * where the data does not say enough.
	 */
	private static void applySecondary(int[] slots, int slot, int secondary)
	{
		switch (slot)
		{
			case TORSO:
				// Sleeves, when the item has them. Without them the wearer's arms show, which
				// is exactly right for a chainbody or a monk's robe.
				if (secondary > 0)
				{
					slots[ARMS] = secondary;
				}
				break;

			case HEAD:
				// Any headgear hides hair and beard. A second mesh, where one exists, is the
				// piece that replaces the jaw.
				slots[HAIR] = -1;
				slots[JAW] = secondary > 0 ? secondary : -1;
				break;

			default:
				// Everything else covers nothing underneath. Legwear is included here on
				// purpose: a second mesh on a leg item is rare and there is nothing in the
				// data to say which part it replaces, so guessing at the feet would be how
				// you get a boot-shaped piece floating at the knee.
				break;
		}
	}
}
