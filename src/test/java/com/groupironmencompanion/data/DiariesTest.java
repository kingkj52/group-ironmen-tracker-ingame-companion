package com.groupironmencompanion.data;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards the achievement diary table, which is ported by hand from the groupiron.men site's
 * {@code diaries.js}. A miscounted tier would silently show the wrong denominator forever, so
 * the expected task counts are asserted independently of the table that produces them.
 */
public class DiariesTest
{
	/** region -> {easy, medium, hard, elite} task counts, read off the site's own table. */
	private static Map<String, int[]> expectedCounts()
	{
		Map<String, int[]> expected = new HashMap<>();
		expected.put("Ardougne", new int[]{10, 12, 12, 8});
		expected.put("Desert", new int[]{11, 12, 10, 6});
		expected.put("Falador", new int[]{11, 14, 11, 6});
		expected.put("Fremennik", new int[]{10, 9, 9, 6});
		expected.put("Kandarin", new int[]{11, 14, 11, 7});
		expected.put("Karamja", new int[]{10, 19, 10, 5});
		expected.put("Kourend & Kebos", new int[]{12, 13, 10, 8});
		expected.put("Lumbridge & Draynor", new int[]{12, 12, 11, 6});
		expected.put("Morytania", new int[]{11, 11, 10, 6});
		expected.put("Varrock", new int[]{14, 13, 10, 5});
		expected.put("Western Provinces", new int[]{11, 13, 13, 7});
		expected.put("Wilderness", new int[]{12, 11, 10, 7});
		return expected;
	}

	private static GroupMember memberWith(int[] diaryVars)
	{
		GroupMember member = new GroupMember("Tester");
		member.setDiaryVars(diaryVars);
		return member;
	}

	@Test
	public void hasEveryRegion()
	{
		assertEquals(expectedCounts().size(), Diaries.regions().size());
		for (Diaries.Region region : Diaries.regions())
		{
			assertNotNull("unexpected region " + region.getName(),
				expectedCounts().get(region.getName()));
		}
	}

	@Test
	public void eachTierHasTheRightNumberOfTasks()
	{
		Map<String, int[]> expected = expectedCounts();
		// Every task is counted by asking a member with no progress at all: the denominator
		// is what matters here.
		GroupMember empty = memberWith(new int[72]);

		for (Diaries.Region region : Diaries.regions())
		{
			int[] counts = expected.get(region.getName());
			Diaries.Tier[] tiers = Diaries.Tier.values();
			for (int i = 0; i < tiers.length; i++)
			{
				Diaries.Progress progress = Diaries.progressOf(empty, region, tiers[i]);
				assertEquals(region.getName() + " " + tiers[i].getLabel() + " task count",
					counts[i], progress.getTotal());
			}
		}
	}

	@Test
	public void totalMatchesTheSumOfEveryTier()
	{
		int sum = 0;
		for (int[] counts : expectedCounts().values())
		{
			for (int count : counts)
			{
				sum += count;
			}
		}

		Diaries.Progress total = Diaries.totalProgressOf(memberWith(new int[72]));
		assertEquals(sum, total.getTotal());
		assertEquals(0, total.getCompleted());
	}

	@Test
	public void readsBitsOutOfTheVarpSection()
	{
		// Ardougne easy is bits 0,1,2,4,5,6,7,9,11,12 of diary var 0. Setting bits 0 and 1
		// should complete exactly two of its tasks, and bit 3 is not a task at all.
		int[] vars = new int[72];
		vars[0] = (1 << 0) | (1 << 1) | (1 << 3);

		Diaries.Region ardougne = region("Ardougne");
		Diaries.Progress easy = Diaries.progressOf(memberWith(vars), ardougne, Diaries.Tier.EASY);
		assertEquals(2, easy.getCompleted());
		assertTrue(easy.isKnown());
		assertFalse(easy.isComplete());
	}

	@Test
	public void readsKaramjaTasksAsWholeVarbitValues()
	{
		// Karamja is stored as one varbit per task rather than packed bits, and its first
		// easy task completes at 5 rather than 1. Varbits start at index 23.
		int[] vars = new int[72];
		vars[23] = 5;   // first easy task, completes at 5
		vars[24] = 1;   // second easy task, completes at 1

		Diaries.Progress easy = Diaries.progressOf(memberWith(vars), region("Karamja"), Diaries.Tier.EASY);
		assertEquals(2, easy.getCompleted());

		// A value of 1 on the first task must NOT count, since that one needs 5.
		vars[23] = 1;
		assertEquals(1, Diaries.progressOf(memberWith(vars), region("Karamja"), Diaries.Tier.EASY).getCompleted());
	}

	@Test
	public void treatsMissingDataAsUnknownRatherThanZero()
	{
		GroupMember never = new GroupMember("Tester");
		Diaries.Progress progress = Diaries.progressOf(never, region("Varrock"), Diaries.Tier.EASY);
		assertFalse(progress.isKnown());
		assertFalse(progress.isComplete());
		assertEquals(14, progress.getTotal());
	}

	@Test
	public void toleratesAShortArrayFromAnOlderClient()
	{
		// A member who last reported before a var was added sends a shorter array; reading
		// past the end must be treated as "not done", not throw.
		Diaries.Progress progress = Diaries.progressOf(memberWith(new int[4]),
			region("Wilderness"), Diaries.Tier.ELITE);
		assertEquals(0, progress.getCompleted());
		assertEquals(7, progress.getTotal());
	}

	private static Diaries.Region region(String name)
	{
		for (Diaries.Region region : Diaries.regions())
		{
			if (region.getName().equals(name))
			{
				return region;
			}
		}
		throw new AssertionError("no such region: " + name);
	}
}
