package com.groupironmencompanion.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decodes a member's {@code diary_vars} array into per-region, per-tier task completion.
 * <p>
 * The array is the achievement diary varps followed by the varbits, in the order
 * {@code com.groupironmencompanion.upload.DiaryVars} defines and the official tracker plugin
 * sends. Most tasks are a single bit of a varp. Karamja is the odd one out: its easy, medium
 * and hard tasks each live in their own varbit that has to equal a particular value, not just
 * be non-zero.
 * <p>
 * The bit and value tables are ported from {@code diaries.js} in the groupiron.men site, so
 * this plugin reports exactly what the website reports.
 */
public final class Diaries
{
	/** Index of the first varbit in the array; everything before it is a varp. */
	private static final int VARBIT_BASE = 23;

	private static final List<Region> REGIONS = build();

	private Diaries()
	{
	}

	public static List<Region> regions()
	{
		return REGIONS;
	}

	public enum Tier
	{
		EASY("Easy"),
		MEDIUM("Medium"),
		HARD("Hard"),
		ELITE("Elite");

		private final String label;

		Tier(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	/** How many tasks of a tier a member has completed. */
	public static final class Progress
	{
		private final int completed;
		private final int total;
		private final boolean known;

		Progress(int completed, int total, boolean known)
		{
			this.completed = completed;
			this.total = total;
			this.known = known;
		}

		public int getCompleted()
		{
			return completed;
		}

		public int getTotal()
		{
			return total;
		}

		/** False when this member has never reported diary data. */
		public boolean isKnown()
		{
			return known;
		}

		public boolean isComplete()
		{
			return known && total > 0 && completed >= total;
		}
	}

	public static Progress progressOf(GroupMember member, Region region, Tier tier)
	{
		Task[] tasks = region.tasks(tier);
		int[] vars = member.getDiaryVars();
		if (vars == null)
		{
			return new Progress(0, tasks.length, false);
		}

		int done = 0;
		for (Task task : tasks)
		{
			if (task.done(vars))
			{
				done++;
			}
		}
		return new Progress(done, tasks.length, true);
	}

	/** Completion across every region and tier. */
	public static Progress totalProgressOf(GroupMember member)
	{
		int[] vars = member.getDiaryVars();
		int done = 0;
		int total = 0;
		for (Region region : REGIONS)
		{
			for (Tier tier : Tier.values())
			{
				Task[] tasks = region.tasks(tier);
				total += tasks.length;
				if (vars != null)
				{
					for (Task task : tasks)
					{
						if (task.done(vars))
						{
							done++;
						}
					}
				}
			}
		}
		return new Progress(done, total, vars != null);
	}

	/** One diary region, with its four tiers of tasks. */
	public static final class Region
	{
		private final String name;
		private final Task[] easy;
		private final Task[] medium;
		private final Task[] hard;
		private final Task[] elite;

		Region(String name, Task[] easy, Task[] medium, Task[] hard, Task[] elite)
		{
			this.name = name;
			this.easy = easy;
			this.medium = medium;
			this.hard = hard;
			this.elite = elite;
		}

		public String getName()
		{
			return name;
		}

		Task[] tasks(Tier tier)
		{
			switch (tier)
			{
				case EASY:
					return easy;
				case MEDIUM:
					return medium;
				case HARD:
					return hard;
				default:
					return elite;
			}
		}
	}

	// ------------------------------------------------------------------
	// Task predicates
	// ------------------------------------------------------------------

	private interface Task
	{
		boolean done(int[] vars);
	}

	private static int varAt(int[] vars, int index)
	{
		return index >= 0 && index < vars.length ? vars[index] : 0;
	}

	/** A task stored as one bit of a varp. */
	private static Task bit(int varIndex, int bitIndex)
	{
		return vars -> (varAt(vars, varIndex) >> bitIndex & 1) != 0;
	}

	/** Several bits of the same varp, in order. */
	private static Task[] bits(int varIndex, int... bitIndexes)
	{
		Task[] tasks = new Task[bitIndexes.length];
		for (int i = 0; i < bitIndexes.length; i++)
		{
			tasks[i] = bit(varIndex, bitIndexes[i]);
		}
		return tasks;
	}

	/** A Karamja task, stored as a varbit that must equal a particular value. */
	private static Task value(int varbitIndex, int expected)
	{
		int index = VARBIT_BASE + varbitIndex;
		return vars -> varAt(vars, index) == expected;
	}

	/** Karamja tasks are a run of consecutive varbits that must each equal 1. */
	private static Task[] values(int firstVarbit, int count)
	{
		Task[] tasks = new Task[count];
		for (int i = 0; i < count; i++)
		{
			tasks[i] = value(firstVarbit + i, 1);
		}
		return tasks;
	}

	/** A task that either of two bits can satisfy. */
	private static Task either(Task a, Task b)
	{
		return vars -> a.done(vars) || b.done(vars);
	}

	private static Task[] join(Object... parts)
	{
		List<Task> tasks = new ArrayList<>();
		for (Object part : parts)
		{
			if (part instanceof Task[])
			{
				Collections.addAll(tasks, (Task[]) part);
			}
			else
			{
				tasks.add((Task) part);
			}
		}
		return tasks.toArray(new Task[0]);
	}

	// ------------------------------------------------------------------
	// The table
	// ------------------------------------------------------------------

	private static List<Region> build()
	{
		List<Region> regions = new ArrayList<>();

		regions.add(new Region("Ardougne",
			bits(0, 0, 1, 2, 4, 5, 6, 7, 9, 11, 12),
			bits(0, 13, 14, 15, 16, 17, 18, 19, 20, 21, 23, 24, 25),
			join(bits(0, 26, 27, 28, 29, 30, 31), bits(1, 0, 1, 2, 3, 4, 5)),
			bits(1, 6, 7, 9, 8, 10, 11, 12, 13)));

		regions.add(new Region("Desert",
			bits(2, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
			join(bits(2, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21),
				either(bit(2, 22), bit(3, 9)),
				bits(2, 23)),
			join(bits(2, 24, 25, 26, 27, 28, 29, 30, 31), bits(3, 0, 1)),
			bits(3, 2, 4, 5, 6, 7, 8)));

		regions.add(new Region("Falador",
			bits(4, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
			bits(4, 11, 12, 13, 14, 15, 16, 17, 18, 20, 21, 22, 23, 24, 25),
			join(bits(4, 26, 27, 28, 29, 30, 31), bits(5, 0, 1, 2, 3, 4)),
			bits(5, 5, 6, 7, 8, 9, 10)));

		regions.add(new Region("Fremennik",
			bits(6, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
			bits(6, 11, 12, 13, 14, 15, 17, 18, 19, 20),
			bits(6, 21, 23, 24, 25, 26, 27, 28, 29, 30),
			join(bits(6, 31), bits(7, 0, 1, 2, 3, 4))));

		regions.add(new Region("Kandarin",
			bits(8, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
			bits(8, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25),
			join(bits(8, 26, 27, 28, 29, 30, 31), bits(9, 0, 1, 2, 3, 4)),
			bits(9, 5, 6, 7, 8, 9, 10, 11)));

		// Karamja's easy, medium and hard tasks are individual varbits rather than packed
		// bits, and two of the easy ones complete at 5 rather than 1.
		regions.add(new Region("Karamja",
			join(value(0, 5), values(1, 6), value(7, 5), values(8, 2)),
			values(10, 19),
			join(values(29, 7), value(36, 5), values(37, 2)),
			bits(10, 1, 2, 3, 4, 5)));

		regions.add(new Region("Kourend & Kebos",
			bits(11, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
			bits(11, 25, 13, 14, 15, 21, 16, 17, 18, 19, 22, 20, 23, 24),
			join(bits(11, 26, 27, 28, 29, 31, 30), bits(12, 0, 1, 2, 3)),
			bits(12, 4, 5, 6, 7, 8, 9, 10, 11)));

		regions.add(new Region("Lumbridge & Draynor",
			bits(13, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
			bits(13, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24),
			join(bits(13, 25, 26, 27, 28, 29, 30, 31), bits(14, 0, 1, 2, 3)),
			bits(14, 4, 5, 6, 7, 8, 9)));

		regions.add(new Region("Morytania",
			bits(15, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
			bits(15, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22),
			join(bits(15, 23, 24, 25, 26, 27, 28, 29, 30), bits(16, 1, 2)),
			bits(16, 3, 4, 5, 6, 7, 8)));

		regions.add(new Region("Varrock",
			bits(17, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14),
			bits(17, 15, 16, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28),
			join(bits(17, 29, 30, 31), bits(18, 0, 1, 2, 3, 4, 5, 6)),
			bits(18, 7, 8, 9, 10, 11)));

		regions.add(new Region("Western Provinces",
			bits(19, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
			bits(19, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24),
			join(bits(19, 25, 26, 27, 28, 29, 30, 31), bits(20, 0, 1, 2, 3, 4, 5)),
			bits(20, 6, 7, 8, 9, 12, 13, 14)));

		regions.add(new Region("Wilderness",
			bits(21, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
			bits(21, 13, 14, 15, 16, 18, 19, 20, 21, 22, 23, 24),
			join(bits(21, 25, 26, 27, 28, 29, 30, 31), bits(22, 0, 1, 2)),
			bits(22, 3, 5, 7, 8, 9, 10, 11)));

		return Collections.unmodifiableList(regions);
	}
}
