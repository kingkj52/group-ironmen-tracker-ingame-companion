package com.groupironmencompanion.map;

import com.groupironmencompanion.GroupIronmenCompanionConfig;
import com.groupironmencompanion.data.GroupMember;
import com.groupironmencompanion.data.GroupState;
import com.groupironmencompanion.route.RouteTracker;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.MouseAdapter;

/**
 * Clicks on the world map's group list.
 * <p>
 * Left-click jumps the map to that member, right-click starts or stops routing to them.
 * Input is only consumed when the pointer is genuinely on one of the panel's rows, so the
 * rest of the world map keeps behaving normally.
 * <p>
 * Everything that touches the client is dispatched to the client thread. Handlers here run
 * on AWT, where calling into the client is illegal and fails silently.
 */
@Singleton
public class GroupMapInput extends MouseAdapter
{
	@Inject
	private GroupMapOverlay overlay;

	@Inject
	private GroupState groupState;

	@Inject
	private GroupIronmenCompanionConfig config;

	@Inject
	private RouteTracker routeTracker;

	@Inject
	private ClientThread clientThread;

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (!config.mapPanel() || event.isAltDown())
		{
			return event;
		}

		String name = overlay.memberAt(event.getPoint());
		if (name == null)
		{
			return event;
		}

		boolean rightClick = javax.swing.SwingUtilities.isRightMouseButton(event);
		if (rightClick && !config.mapPanelRouting())
		{
			return event;
		}

		clientThread.invoke(() ->
		{
			GroupMember member = groupState.getMember(name);
			if (member == null)
			{
				return;
			}

			if (rightClick)
			{
				routeTracker.toggle(member);
				return;
			}

			overlay.jumpTo(member.getWorldPoint());
		});

		event.consume();
		return event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		return swallowOnRow(event);
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		return swallowOnRow(event);
	}

	private MouseEvent swallowOnRow(MouseEvent event)
	{
		if (config.mapPanel() && !event.isAltDown() && overlay.memberAt(event.getPoint()) != null)
		{
			event.consume();
		}
		return event;
	}
}
