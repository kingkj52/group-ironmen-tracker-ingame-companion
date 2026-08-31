package com.groupironmencompanion.ui;

import com.groupironmencompanion.data.GroupMember;
import com.groupironmencompanion.data.GroupSkill;
import com.groupironmencompanion.data.GroupState;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.api.Experience;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Compares the group's skills: total level and experience by default, or one chosen skill
 * with each member's level, experience and experience to the next level.
 */
class SkillsView extends JPanel
{
	private static final String TOTAL = "Total";
	private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.ENGLISH);

	private final GroupState groupState;

	private final JComboBox<String> skillPicker = new JComboBox<>();
	private final JPanel rows = new JPanel();

	SkillsView(GroupState groupState)
	{
		this.groupState = groupState;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		model.addElement(TOTAL);
		for (GroupSkill skill : GroupSkill.VALUES)
		{
			model.addElement(skill.getDisplayName());
		}
		skillPicker.setModel(model);
		skillPicker.setPreferredSize(new Dimension(0, 24));
		skillPicker.setFocusable(false);
		skillPicker.setForeground(ColorScheme.TEXT_COLOR);
		skillPicker.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		skillPicker.addActionListener(e -> refresh());

		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(skillPicker, BorderLayout.NORTH);
		add(rows, BorderLayout.CENTER);
	}

	void refresh()
	{
		rows.removeAll();

		String selected = (String) skillPicker.getSelectedItem();
		GroupSkill skill = null;
		if (selected != null && !TOTAL.equals(selected))
		{
			for (GroupSkill candidate : GroupSkill.VALUES)
			{
				if (candidate.getDisplayName().equals(selected))
				{
					skill = candidate;
					break;
				}
			}
		}

		List<GroupMember> members = groupState.getMembers();
		if (skill == null)
		{
			members.sort(Comparator.comparingInt(GroupMember::getTotalLevel).reversed());
		}
		else
		{
			final GroupSkill sortBy = skill;
			members.sort(Comparator.comparingInt((GroupMember m) -> m.getXp(sortBy)).reversed());
		}

		for (GroupMember member : members)
		{
			rows.add(skill == null ? totalRow(member) : skillRow(member, skill));
		}

		if (members.isEmpty())
		{
			JLabel empty = new JLabel("No group data yet");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setHorizontalAlignment(SwingConstants.CENTER);
			rows.add(empty);
		}

		revalidate();
		repaint();
	}

	private JPanel totalRow(GroupMember member)
	{
		JPanel panel = row(member);
		int totalLevel = member.getTotalLevel();
		long totalXp = member.getTotalXp();

		panel.add(line("Total level", totalLevel < 0 ? "-" : NUMBERS.format(totalLevel)));
		panel.add(line("Total xp", totalXp < 0 ? "-" : NUMBERS.format(totalXp)));
		return panel;
	}

	private JPanel skillRow(GroupMember member, GroupSkill skill)
	{
		JPanel panel = row(member);

		int level = member.getLevel(skill);
		int xp = member.getXp(skill);
		int toNext = member.getXpToNextLevel(skill);

		if (level < 0)
		{
			panel.add(line("Level", "not reported yet"));
			return panel;
		}

		panel.add(line("Level", Integer.toString(level)));
		panel.add(line("Experience", NUMBERS.format(xp)));
		panel.add(line("To level " + (level + 1),
			level >= Experience.MAX_VIRT_LEVEL ? "maxed" : NUMBERS.format(toNext)));
		return panel;
	}

	private JPanel row(GroupMember member)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(5, 6, 5, 6)));

		JLabel name = new JLabel(groupState.isLocalPlayer(member) ? member.getName() + " (you)" : member.getName());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(member.getColour());
		panel.add(name);

		return panel;
	}

	private JPanel line(String caption, String value)
	{
		JLabel captionLabel = new JLabel(caption);
		captionLabel.setFont(FontManager.getRunescapeSmallFont());
		captionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel valueLabel = new JLabel(value);
		valueLabel.setFont(FontManager.getRunescapeSmallFont());
		valueLabel.setForeground(ColorScheme.TEXT_COLOR);
		valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		JPanel panel = new JPanel(new GridLayout(1, 2));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.add(captionLabel);
		panel.add(valueLabel);
		return panel;
	}
}
