package com.groupironmencompanion.activity;

import com.groupironmencompanion.data.GroupSkill;
import java.time.Instant;

/** Ready-made activities for tests outside this package. */
public final class Activities
{
	private static final Instant AT = Instant.parse("2026-01-01T12:00:00Z");

	private Activities()
	{
	}

	public static Activity fighting(String target, GroupSkill skill)
	{
		return new Activity(Activity.Kind.FIGHTING, "Fighting " + target, "Fighting " + target, null, null, "", AT)
			.withSkill(skill).withTarget(target);
	}

	/** Combat XP coming in, with no live interaction to say who against. */
	public static Activity fightingByExperience(GroupSkill skill)
	{
		return new Activity(Activity.Kind.FIGHTING, "Fighting", "Fighting", null, null, "", AT).withSkill(skill);
	}

	public static Activity skilling(GroupSkill skill)
	{
		return new Activity(Activity.Kind.SKILLING, skill.getDisplayName(), skill.getDisplayName(), null, null, "", AT)
			.withSkill(skill);
	}

	public static Activity fishing(String target)
	{
		return new Activity(Activity.Kind.FISHING, "Fishing", "Fishing", null, null, "", AT)
			.withSkill(GroupSkill.FISHING).withTarget(target);
	}

	public static Activity talking(String target)
	{
		return new Activity(Activity.Kind.TALKING, "Talking to " + target, "Talking to " + target, null, null, "", AT)
			.withTarget(target);
	}

	public static Activity idle()
	{
		return new Activity(Activity.Kind.IDLE, "Idle", "Idle", null, null, "", AT);
	}
}
