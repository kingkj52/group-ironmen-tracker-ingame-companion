package com.groupironmencompanion.ui;

import com.groupironmencompanion.data.Diaries;
import com.groupironmencompanion.data.GroupMember;
import com.groupironmencompanion.data.GroupState;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ThinProgressBar;

/**
 * Achievement diary progress for the group: overall completion per member, and a per-region
 * breakdown of each tier.
 */
class DiariesView extends JPanel
{
	private static final String ALL_REGIONS = "All regions";

	private static final Color COMPLETE = new Color(0x55, 0xC5, 0x5A);
	private static final Color PARTIAL = new Color(0xE6, 0x96, 0x1E);
	private static final Color NONE = new Color(0x9E, 0x50, 0x50);
	private static final Color UNKNOWN = new Color(0x77, 0x77, 0x77);

	private final GroupState groupState;

	private final JComboBox<String> regionPicker = new JComboBox<>();
	private final JPanel rows = new JPanel();

	DiariesView(GroupState groupState)
	{
		this.groupState = groupState;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		model.addElement(ALL_REGIONS);
		for (Diaries.Region region : Diaries.regions())
		{
			model.addElement(region.getName());
		}
		regionPicker.setModel(model);
		regionPicker.setPreferredSize(new Dimension(0, 24));
		regionPicker.setFocusable(false);
		regionPicker.setForeground(ColorScheme.TEXT_COLOR);
		regionPicker.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		regionPicker.addActionListener(e -> refresh());

		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(regionPicker, BorderLayout.NORTH);
		add(rows, BorderLayout.CENTER);
	}

	void refresh()
	{
		rows.removeAll();

		List<GroupMember> members = groupState.getMembers();
		if (members.isEmpty())
		{
			JLabel empty = new JLabel("No group data yet");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setHorizontalAlignment(SwingConstants.CENTER);
			rows.add(empty);
		}
		else
		{
			Object selected = regionPicker.getSelectedItem();
			Diaries.Region region = null;
			if (selected != null && !ALL_REGIONS.equals(selected))
			{
				for (Diaries.Region candidate : Diaries.regions())
				{
					if (candidate.getName().equals(selected))
					{
						region = candidate;
						break;
					}
				}
			}

			for (GroupMember member : members)
			{
				rows.add(region == null ? overallRow(member) : regionRow(member, region));
			}
		}

		revalidate();
		repaint();
	}

	private JPanel overallRow(GroupMember member)
	{
		Diaries.Progress progress = Diaries.totalProgressOf(member);

		JPanel panel = card(member);
		JLabel count = new JLabel(progress.isKnown()
			? progress.getCompleted() + " / " + progress.getTotal() + " tasks"
			: "not reported");
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(progress.isKnown() ? ColorScheme.LIGHT_GRAY_COLOR : UNKNOWN);
		count.setHorizontalAlignment(SwingConstants.RIGHT);
		((JPanel) panel.getComponent(0)).add(count, BorderLayout.EAST);

		if (progress.isKnown() && progress.getTotal() > 0)
		{
			ThinProgressBar bar = new ThinProgressBar();
			bar.setForeground(COMPLETE);
			bar.setMaximumValue(progress.getTotal());
			bar.setValue(progress.getCompleted());
			bar.setPreferredSize(new Dimension(0, 5));
			panel.add(bar);
		}

		return panel;
	}

	private JPanel regionRow(GroupMember member, Diaries.Region region)
	{
		JPanel panel = card(member);

		for (Diaries.Tier tier : Diaries.Tier.values())
		{
			Diaries.Progress progress = Diaries.progressOf(member, region, tier);

			JLabel name = new JLabel(tier.getLabel());
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

			JLabel value = new JLabel(progress.isKnown()
				? progress.getCompleted() + " / " + progress.getTotal()
				: "-");
			value.setFont(FontManager.getRunescapeSmallFont());
			value.setForeground(colourOf(progress));
			value.setHorizontalAlignment(SwingConstants.RIGHT);

			JPanel line = new JPanel(new GridLayout(1, 2));
			line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			line.add(name);
			line.add(value);
			panel.add(line);
		}

		return panel;
	}

	/** A member card whose first component is a header ready for an EAST label. */
	private JPanel card(GroupMember member)
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

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.add(name, BorderLayout.WEST);
		panel.add(header);

		return panel;
	}

	private static Color colourOf(Diaries.Progress progress)
	{
		if (!progress.isKnown())
		{
			return UNKNOWN;
		}
		if (progress.isComplete())
		{
			return COMPLETE;
		}
		return progress.getCompleted() > 0 ? PARTIAL : NONE;
	}
}
