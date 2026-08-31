package com.groupironmencompanion.overlay;

import com.groupironmencompanion.GroupIronmenCompanionConfig;
import com.groupironmencompanion.data.GroupMember;
import com.groupironmencompanion.data.GroupState;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * A compact, always-on summary of the group's vitals: hitpoints, prayer, run energy, world
 * and, optionally, what each member is currently interacting with.
 * <p>
 * This is a plain RuneLite overlay the user can drag anywhere or switch off. It reads only
 * data your group members' own clients published to your shared server, and draws nothing in
 * the game scene or on the minimap.
 */
@Singleton
public class GroupStatusOverlay extends OverlayPanel
{
	private static final Color DIM = new Color(0xB0, 0xB0, 0xB0);
	private static final Color OFFLINE = new Color(0x80, 0x80, 0x80);

	@Inject
	private GroupState groupState;

	@Inject
	private GroupIronmenCompanionConfig config;

	@Inject
	public GroupStatusOverlay()
	{
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.statusOverlay())
		{
			return null;
		}

		List<GroupMember> members = groupState.getMembers();
		if (members.isEmpty())
		{
			return null;
		}

		boolean drewAnyone = false;
		for (GroupMember member : members)
		{
			boolean online = member.isOnline(config.offlineAfterMinutes());

			if (!online && !config.statusShowOffline())
			{
				continue;
			}
			if (groupState.isLocalPlayer(member) && !config.statusShowSelf())
			{
				continue;
			}

			if (!drewAnyone)
			{
				panelComponent.setPreferredSize(new Dimension(180, 0));
				panelComponent.getChildren().add(TitleComponent.builder()
					.text("Group")
					.color(Color.WHITE)
					.build());
				drewAnyone = true;
			}

			addMember(member, online);
		}

		return drewAnyone ? super.render(graphics) : null;
	}

	private void addMember(GroupMember member, boolean online)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(member.getName())
			.leftColor(online ? member.getColour() : OFFLINE)
			.right(online && member.hasStats() ? "w" + member.getWorld() : member.getLastSeenText())
			.rightColor(online ? DIM : OFFLINE)
			.build());

		if (!online)
		{
			return;
		}

		if (config.statusShowVitals() && member.hasStats())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("  hp")
				.leftColor(DIM)
				.right(member.getHitpoints() + "/" + member.getMaxHitpoints())
				.rightColor(healthColour(member))
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("  pray")
				.leftColor(DIM)
				.right(member.getPrayer() + "/" + member.getMaxPrayer())
				.rightColor(DIM)
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("  run")
				.leftColor(DIM)
				.right(member.getRunEnergyPercent() + "%")
				.rightColor(DIM)
				.build());
		}

		GroupMember.Interaction interaction = member.getInteraction();
		if (config.statusShowActivity() && interaction != null && !interaction.isStale())
		{
			int health = interaction.getHealthPercent();
			panelComponent.getChildren().add(LineComponent.builder()
				.left("  vs " + interaction.getName())
				.leftColor(DIM)
				.right(health >= 0 ? health + "%" : "")
				.rightColor(DIM)
				.build());
		}
	}

	private Color healthColour(GroupMember member)
	{
		int max = member.getMaxHitpoints();
		if (max <= 0)
		{
			return DIM;
		}
		double fraction = (double) member.getHitpoints() / max;
		if (fraction <= 0.25)
		{
			return new Color(0xFF, 0x5C, 0x5C);
		}
		if (fraction <= 0.5)
		{
			return new Color(0xFF, 0xC1, 0x07);
		}
		return DIM;
	}
}
