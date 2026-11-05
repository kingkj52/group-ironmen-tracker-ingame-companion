package com.groupironmencompanion.scene;

import java.util.Locale;

/**
 * Rough weapon types, enough to pick an attack animation.
 * <p>
 * Goes by item name. The real weapon type is in the cache but not in the API, and getting a
 * name wrong only means a ghost swings the wrong way.
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

	/** Most specific match first, since names like "dragon crossbow" contain several. */
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
			|| name.contains("trident"))
		{
			return STAFF;
		}
		if (name.contains("knife") || name.contains("dart") || name.contains("javelin")
			|| name.contains("thrownaxe") || name.contains("chinchompa"))
		{
			return THROWN;
		}
		if (name.contains("dagger") || name.contains("rapier") || name.contains("spear")
			|| name.contains("hasta") || name.contains("partisan"))
		{
			return MELEE_STAB;
		}
		if (name.contains("maul") || name.contains("hammer") || name.contains("mace")
			|| name.contains("club") || name.contains("bludgeon"))
		{
			return MELEE_CRUSH;
		}
		if (name.contains("sword") || name.contains("scimitar") || name.contains("axe")
			|| name.contains("halberd") || name.contains("whip") || name.contains("claws")
			|| name.contains("blade") || name.contains("cleaver") || name.contains("machete"))
		{
			return MELEE_SLASH;
		}

		// A cape, a bucket, a skilling tool. Punching beats nothing.
		return UNARMED;
	}
}
