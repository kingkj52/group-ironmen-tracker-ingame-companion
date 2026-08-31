package com.groupironmencompanion.upload;

/**
 * The achievement diary vars, in the exact order the groupiron.men server expects in a
 * member's {@code diary_vars} array: every varp first, then every varbit.
 * <p>
 * The order is part of the wire format and is shared with the official tracker plugin, so
 * these arrays must not be reordered or trimmed even if entries look redundant.
 */
final class DiaryVars
{
	static final int[] VARPS = {
		/* Ardougne */
		1196, 1197,
		/* Desert */
		1198, 1199,
		/* Falador */
		1186, 1187,
		/* Fremennik */
		1184, 1185,
		/* Kandarin */
		1178, 1179,
		/* Karamja Elite */
		1200,
		/* Kourend & Kebos */
		2085, 2086,
		/* Lumbridge & Draynor */
		1194, 1195,
		/* Morytania */
		1180, 1181,
		/* Varrock */
		1176, 1177,
		/* Western Provinces */
		1182, 1183,
		/* Wilderness */
		1192, 1193
	};

	static final int[] VARBITS = {
		/* Karamja Easy */
		3566, 3567, 3568, 3569, 3570, 3571, 3572, 3573, 3574, 3575,
		/* Karamja Medium */
		3579, 3580, 3581, 3582, 3583, 3584, 3596, 3586, 3587, 3588, 3589, 3590, 3591, 3592, 3593, 3594, 3595, 3597, 3585,
		/* Karamja Hard */
		3600, 3601, 3602, 3603, 3604, 3605, 3606, 3607, 3608, 3609
	};

	private DiaryVars()
	{
	}
}
