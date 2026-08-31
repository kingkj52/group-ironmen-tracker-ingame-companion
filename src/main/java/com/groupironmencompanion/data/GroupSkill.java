package com.groupironmencompanion.data;

import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;

/**
 * The 24 skills in the exact order the groupiron.men server stores them in a member's
 * {@code skills} array, so {@link #ordinal()} doubles as the wire index.
 * <p>
 * The order is alphabetical with Sailing appended last, matching
 * {@code men.groupiron.SkillState#get()} in the official tracker plugin. Older servers
 * and older tracker builds send only the first 23 entries, so readers must bounds-check.
 */
public enum GroupSkill
{
	AGILITY("Agility", Skill.AGILITY, InterfaceID.Stats.AGILITY),
	ATTACK("Attack", Skill.ATTACK, InterfaceID.Stats.ATTACK),
	CONSTRUCTION("Construction", Skill.CONSTRUCTION, InterfaceID.Stats.CONSTRUCTION),
	COOKING("Cooking", Skill.COOKING, InterfaceID.Stats.COOKING),
	CRAFTING("Crafting", Skill.CRAFTING, InterfaceID.Stats.CRAFTING),
	DEFENCE("Defence", Skill.DEFENCE, InterfaceID.Stats.DEFENCE),
	FARMING("Farming", Skill.FARMING, InterfaceID.Stats.FARMING),
	FIREMAKING("Firemaking", Skill.FIREMAKING, InterfaceID.Stats.FIREMAKING),
	FISHING("Fishing", Skill.FISHING, InterfaceID.Stats.FISHING),
	FLETCHING("Fletching", Skill.FLETCHING, InterfaceID.Stats.FLETCHING),
	HERBLORE("Herblore", Skill.HERBLORE, InterfaceID.Stats.HERBLORE),
	HITPOINTS("Hitpoints", Skill.HITPOINTS, InterfaceID.Stats.HITPOINTS),
	HUNTER("Hunter", Skill.HUNTER, InterfaceID.Stats.HUNTER),
	MAGIC("Magic", Skill.MAGIC, InterfaceID.Stats.MAGIC),
	MINING("Mining", Skill.MINING, InterfaceID.Stats.MINING),
	PRAYER("Prayer", Skill.PRAYER, InterfaceID.Stats.PRAYER),
	RANGED("Ranged", Skill.RANGED, InterfaceID.Stats.RANGED),
	RUNECRAFT("Runecraft", Skill.RUNECRAFT, InterfaceID.Stats.RUNECRAFT),
	SLAYER("Slayer", Skill.SLAYER, InterfaceID.Stats.SLAYER),
	SMITHING("Smithing", Skill.SMITHING, InterfaceID.Stats.SMITHING),
	STRENGTH("Strength", Skill.STRENGTH, InterfaceID.Stats.STRENGTH),
	THIEVING("Thieving", Skill.THIEVING, InterfaceID.Stats.THIEVING),
	WOODCUTTING("Woodcutting", Skill.WOODCUTTING, InterfaceID.Stats.WOODCUTTING),
	SAILING("Sailing", Skill.SAILING, InterfaceID.Stats.SAILING);

	public static final GroupSkill[] VALUES = values();

	private final String displayName;
	private final Skill skill;
	private final int componentId;

	GroupSkill(String displayName, Skill skill, int componentId)
	{
		this.displayName = displayName;
		this.skill = skill;
		this.componentId = componentId;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public Skill getSkill()
	{
		return skill;
	}

	/**
	 * The packed component id of this skill's tile in the Skills tab. Used only to work out
	 * which skill the mouse is over; the widget itself is never modified.
	 */
	public int getComponentId()
	{
		return componentId;
	}

	public static GroupSkill forComponentId(int componentId)
	{
		for (GroupSkill s : VALUES)
		{
			if (s.componentId == componentId)
			{
				return s;
			}
		}
		return null;
	}
}
