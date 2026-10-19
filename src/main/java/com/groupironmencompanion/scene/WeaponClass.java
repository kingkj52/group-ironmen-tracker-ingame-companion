package com.groupironmencompanion.scene;

import java.util.Locale;

/**
 * Broad weapon categories, enough to pick a plausible attack animation.
 * <p>
 * The real weapon category and its per-stance animation ids live in the game cache, and are
 * not reachable through the plugin API: {@code ItemComposition} exposes only the inventory
 * icon model, not the worn models or the stance table. Resolving them properly means
 * extracting a weapon table at build time, the same way the world map areas are generated.
 * <p>
 * Until that exists, classification is by item name. That is crude, and deliberately so: a
 * misread weapon costs nothing more than a ghost swinging the wrong way, and the alternative
 * is shipping a generated table before the feature has proven itself worth one.
 */
enum WeaponClass
{
	UNARMED,
	MELEE_STAB,
	MELEE_SLASH,
	MELEE_CRUSH,
	BOW,
	CROSSBOW,
	THROWN,
	STAFF;

	/**
	 * Categorises a worn weapon by its name.
	 * <p>
	 * Order matters: the checks run from most to least specific, because many names contain
	 * several of these words ("crystal bow", "dragon crossbow", "staff of the dead").
	 *
	 * @param itemName the weapon's name, or null when the slot is empty
	 */
	static WeaponClass classify(String itemName)
	{
		if (itemName == null || itemName.isEmpty())
		{
			return UNARMED;
		}

		String name = itemName.toLowerCase(Locale.ROOT);

		if (name.contains("crossbow") || name.contains("c'bow") || name.contains("ballista"))
		{
			return CROSSBOW;
		}
		if (name.contains("bow") || name.contains("blowpipe"))
		{
			return BOW;
		}
		if (name.contains("staff") || name.contains("wand") || name.contains("sceptre")
			|| name.contains("trident") || name.contains("partisan"))
		{
			return STAFF;
		}
		if (name.contains("knife") || name.contains("dart") || name.contains("javelin")
			|| name.contains("thrownaxe") || name.contains("chinchompa"))
		{
			return THROWN;
		}
		if (name.contains("dagger") || name.contains("rapier") || name.contains("spear")
			|| name.contains("hasta"))
		{
			return MELEE_STAB;
		}
		if (name.contains("maul") || name.contains("warhammer") || name.contains("hammer")
			|| name.contains("mace") || name.contains("club") || name.contains("bludgeon"))
		{
			return MELEE_CRUSH;
		}
		if (name.contains("sword") || name.contains("scimitar") || name.contains("axe")
			|| name.contains("halberd") || name.contains("whip") || name.contains("claws")
			|| name.contains("blade") || name.contains("cleaver") || name.contains("machete"))
		{
			return MELEE_SLASH;
		}

		// Something worn in the weapon slot that is none of the above: a cape, a bucket, a
		// skilling tool. Punching is a better guess than nothing at all.
		return UNARMED;
	}
}
