package com.groupironmencompanion.ui;

import com.groupironmencompanion.data.GroupMember;
import com.groupironmencompanion.data.GroupState;
import com.groupironmencompanion.data.Quests;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.ThinProgressBar;

/**
 * Quest progress for the group: how many each member has finished, and a search that shows
 * where everyone stands on a particular quest.
 */
class QuestsView extends JPanel
{
	private static final int MAX_RESULTS = 25;

	private static final Color FINISHED = new Color(0x55, 0xC5, 0x5A);
	private static final Color IN_PROGRESS = new Color(0xE6, 0x96, 0x1E);
	private static final Color NOT_STARTED = new Color(0x9E, 0x50, 0x50);
	private static final Color UNKNOWN = new Color(0x77, 0x77, 0x77);

	private final GroupState groupState;

	private final JPanel summary = new JPanel();
	private final IconTextField search = new IconTextField();
	private final JPanel results = new JPanel();
	private final JLabel hint = new JLabel();

	QuestsView(GroupState groupState)
	{
		this.groupState = groupState;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
		summary.setBackground(ColorScheme.DARK_GRAY_COLOR);

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(0, 26));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.addActionListener(e -> refreshResults());
		search.addKeyListener(new java.awt.event.KeyAdapter()
		{
			@Override
			public void keyReleased(java.awt.event.KeyEvent e)
			{
				refreshResults();
			}
		});

		results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
		results.setBackground(ColorScheme.DARK_GRAY_COLOR);

		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setHorizontalAlignment(SwingConstants.CENTER);
		hint.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

		JPanel lower = new JPanel(new BorderLayout(0, 4));
		lower.setBackground(ColorScheme.DARK_GRAY_COLOR);
		lower.add(search, BorderLayout.NORTH);
		lower.add(results, BorderLayout.CENTER);
		lower.add(hint, BorderLayout.SOUTH);

		add(summary, BorderLayout.NORTH);
		add(lower, BorderLayout.CENTER);
	}

	void refresh()
	{
		summary.removeAll();

		List<GroupMember> members = groupState.getMembers();
		if (members.isEmpty())
		{
			JLabel empty = new JLabel("No group data yet");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setHorizontalAlignment(SwingConstants.CENTER);
			summary.add(empty);
		}
		else
		{
			for (GroupMember member : members)
			{
				summary.add(summaryRow(member));
			}
		}

		refreshResults();
		revalidate();
		repaint();
	}

	private JPanel summaryRow(GroupMember member)
	{
		Quests.Progress progress = Quests.progressOf(member);

		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(5, 6, 5, 6)));

		JLabel name = new JLabel(groupState.isLocalPlayer(member) ? member.getName() + " (you)" : member.getName());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(member.getColour());

		JLabel count = new JLabel(progress.isKnown()
			? progress.getFinished() + " / " + progress.getTotal()
			: "not reported");
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		count.setHorizontalAlignment(SwingConstants.RIGHT);

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.add(name, BorderLayout.WEST);
		header.add(count, BorderLayout.EAST);
		panel.add(header, BorderLayout.NORTH);

		if (progress.isKnown() && progress.getTotal() > 0)
		{
			ThinProgressBar bar = new ThinProgressBar();
			bar.setForeground(FINISHED);
			bar.setMaximumValue(progress.getTotal());
			bar.setValue(progress.getFinished());
			bar.setPreferredSize(new Dimension(0, 5));
			panel.add(bar, BorderLayout.CENTER);

			if (progress.getInProgress() > 0)
			{
				JLabel started = new JLabel(progress.getInProgress() + " in progress");
				started.setFont(FontManager.getRunescapeSmallFont());
				started.setForeground(IN_PROGRESS);
				panel.add(started, BorderLayout.SOUTH);
			}
		}

		return panel;
	}

	private void refreshResults()
	{
		results.removeAll();

		String query = search.getText().trim();
		if (query.length() < 2)
		{
			hint.setText("Search a quest to compare the group");
			revalidate();
			repaint();
			return;
		}

		List<Quest> matches = Quests.search(query);
		List<GroupMember> members = groupState.getMembers();

		int shown = 0;
		for (Quest quest : matches)
		{
			if (shown >= MAX_RESULTS)
			{
				break;
			}
			results.add(questRow(quest, members));
			shown++;
		}

		if (matches.isEmpty())
		{
			hint.setText("No quest matches");
		}
		else if (matches.size() > shown)
		{
			hint.setText("Showing " + shown + " of " + matches.size() + " quests");
		}
		else
		{
			hint.setText(" ");
		}

		revalidate();
		repaint();
	}

	private JPanel questRow(Quest quest, List<GroupMember> members)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(5, 6, 5, 6)));

		JLabel title = new JLabel(quest.getName());
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(ColorScheme.TEXT_COLOR);
		panel.add(title);

		for (GroupMember member : members)
		{
			QuestState state = Quests.stateOf(member, quest);

			JLabel who = new JLabel(member.getName());
			who.setFont(FontManager.getRunescapeSmallFont());
			who.setForeground(member.getColour());

			JLabel status = new JLabel(describe(state));
			status.setFont(FontManager.getRunescapeSmallFont());
			status.setForeground(colourOf(state));
			status.setHorizontalAlignment(SwingConstants.RIGHT);

			JPanel line = new JPanel(new GridLayout(1, 2));
			line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			line.add(who);
			line.add(status);
			panel.add(line);
		}

		return panel;
	}

	private static String describe(QuestState state)
	{
		if (state == null)
		{
			return "unknown";
		}
		switch (state)
		{
			case FINISHED:
				return "done";
			case IN_PROGRESS:
				return "started";
			default:
				return "not started";
		}
	}

	private static Color colourOf(QuestState state)
	{
		if (state == null)
		{
			return UNKNOWN;
		}
		switch (state)
		{
			case FINISHED:
				return FINISHED;
			case IN_PROGRESS:
				return IN_PROGRESS;
			default:
				return NOT_STARTED;
		}
	}
}
