package com.groupironmencompanion.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

/**
 * Decodes a member's {@code quests} array.
 * <p>
 * The server stores one entry per quest, ordered by quest id ascending, holding the ordinal
 * of {@link QuestState}. That ordinal order is {@code IN_PROGRESS, NOT_STARTED, FINISHED},
 * which is not the order anyone would guess, so the enum is indexed rather than the values
 * being hard-coded.
 * <p>
 * The list of quests comes from the running client, so a member who last reported before a
 * new quest was released simply has a shorter array; every read is bounds-checked.
 */
public final class Quests
{
	/** Quest ids ascending, which is the order the server stores states in. */
	private static volatile Quest[] ordered;

	/** Each quest's index into that order, so a lookup is not a scan of every quest. */
	private static volatile Map<Quest, Integer> indices;

	private Quests()
	{
	}

	private static Quest[] ordered()
	{
		Quest[] local = ordered;
		if (local == null)
		{
			local = Quest.values().clone();
			Arrays.sort(local, Comparator.comparingInt(Quest::getId));
			ordered = local;
		}
		return local;
	}

	/** Every quest, in the order the wire format uses. */
	public static Quest[] all()
	{
		return ordered();
	}

	/** The state a member reported for a quest, or null when they have not reported it. */
	@Nullable
	public static QuestState stateOf(GroupMember member, Quest quest)
	{
		int[] states = member.getQuests();
		if (states == null)
		{
			return null;
		}

		Integer index = indices().get(quest);
		if (index == null || index >= states.length)
		{
			return null;
		}

		int ordinal = states[index];
		QuestState[] values = QuestState.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
	}

	private static Map<Quest, Integer> indices()
	{
		Map<Quest, Integer> local = indices;
		if (local == null)
		{
			Quest[] quests = ordered();
			local = new EnumMap<>(Quest.class);
			for (int i = 0; i < quests.length; i++)
			{
				local.put(quests[i], i);
			}
			indices = local;
		}
		return local;
	}

	/** How many quests a member has finished, and how many they have started but not finished. */
	public static Progress progressOf(GroupMember member)
	{
		int[] states = member.getQuests();
		if (states == null)
		{
			return new Progress(-1, 0, ordered().length);
		}

		QuestState[] values = QuestState.values();
		int finished = 0;
		int inProgress = 0;
		for (int ordinal : states)
		{
			if (ordinal < 0 || ordinal >= values.length)
			{
				continue;
			}
			if (values[ordinal] == QuestState.FINISHED)
			{
				finished++;
			}
			else if (values[ordinal] == QuestState.IN_PROGRESS)
			{
				inProgress++;
			}
		}
		return new Progress(finished, inProgress, Math.min(states.length, ordered().length));
	}

	/** Quests matching a name fragment, in alphabetical order. */
	public static List<Quest> search(String query)
	{
		List<Quest> matches = new ArrayList<>();
		String needle = query.toLowerCase(java.util.Locale.ROOT);
		for (Quest quest : ordered())
		{
			if (quest.getName().toLowerCase(java.util.Locale.ROOT).contains(needle))
			{
				matches.add(quest);
			}
		}
		matches.sort(Comparator.comparing(Quest::getName, String.CASE_INSENSITIVE_ORDER));
		return matches;
	}

	/** A member's overall quest progress. */
	public static final class Progress
	{
		private final int finished;
		private final int inProgress;
		private final int total;

		Progress(int finished, int inProgress, int total)
		{
			this.finished = finished;
			this.inProgress = inProgress;
			this.total = total;
		}

		/** Quests completed, or -1 when this member has never reported quest data. */
		public int getFinished()
		{
			return finished;
		}

		public int getInProgress()
		{
			return inProgress;
		}

		public int getTotal()
		{
			return total;
		}

		public boolean isKnown()
		{
			return finished >= 0;
		}
	}
}
