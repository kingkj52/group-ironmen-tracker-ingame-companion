package com.groupironmencompanion.api;

/**
 * One element of the {@code get-group-data} response.
 * <p>
 * Field names deliberately match the server's snake_case JSON keys so Gson binds them
 * without any custom naming policy or type tokens. Any field the server omitted because it
 * has not changed since {@code from_time} arrives as null and must be treated as
 * "no new data", not as "cleared".
 */
public class MemberDto
{
	public String name;

	/** [hitpoints, maxHitpoints, prayer, maxPrayer, runEnergy, maxRunEnergy, world] */
	public int[] stats;

	/** [x, y, plane, onBoat] */
	public int[] coordinates;

	/** Experience per skill, indexed by {@code GroupSkill.ordinal()}. */
	public int[] skills;

	public int[] quests;
	public int[] diary_vars;

	/** Flat [itemId, quantity, itemId, quantity, ...] pairs. */
	public int[] inventory;
	public int[] equipment;
	public int[] bank;
	public int[] shared_bank;
	public int[] rune_pouch;
	public int[] seed_vault;
	public int[] quiver;
	public int[] potion_storage;
	public int[] deposited;

	public InteractingDto interacting;

	/** RFC 3339 timestamp of this member's most recent change. */
	public String last_updated;

	public static class InteractingDto
	{
		public String name;
		public int scale;
		public int ratio;
		public CoordinatesDto location;
		public String last_updated;
	}

	public static class CoordinatesDto
	{
		public int x;
		public int y;
		public int plane;
	}
}
