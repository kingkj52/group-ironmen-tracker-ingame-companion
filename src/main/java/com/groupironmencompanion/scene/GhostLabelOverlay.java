package com.groupironmencompanion.scene;

import com.groupironmencompanion.GroupIronmenCompanionConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Names the figure under the cursor.
 * <p>
 * Deliberately not a menu, and deliberately nothing like one. It is drawn in the plugin's own
 * style — rounded, cold blue, thin bordered, in a sans-serif face the game never uses — so it
 * cannot be mistaken for the game's right-click menu, and so nobody expects to be able to
 * click it. There is nothing to click: no menu entry is created anywhere in this plugin, and
 * this overlay registers no mouse listener at all. It reads the cursor position and draws.
 * That is the whole of it.
 * <p>
 * The label follows the cursor rather than sitting above the figure, which keeps it readable
 * when several members stand together and makes the hovered one unambiguous.
 */
@Singleton
public class GhostLabelOverlay extends Overlay
{
	/** Cold, dark and translucent: the same palette as the figures themselves. */
	private static final Color BACKGROUND = new Color(14, 22, 38, 225);
	private static final Color BORDER = new Color(96, 148, 205, 190);
	private static final Color MEMBER_TEXT = new Color(176, 214, 255);
	private static final Color OPPONENT_TEXT = new Color(150, 170, 196);

	private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

	/** How close the cursor must be to a figure's anchor point, in pixels. */
	private static final int HOVER_RADIUS = 28;

	private static final int PADDING = 6;
	private static final int LINE_HEIGHT = 14;
	private static final int CURSOR_OFFSET = 14;
	private static final int CORNER = 6;

	@Inject
	private Client client;

	@Inject
	private GroupIronmenCompanionConfig config;

	@Inject
	private GroupScenePresence presence;

	@Inject
	public GhostLabelOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.scenePresence() || !config.scenePresenceLabels())
		{
			return null;
		}

		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return null;
		}

		Ghost hovered = nearest(mouse);
		if (hovered == null)
		{
			return null;
		}

		draw(graphics, mouse, hovered);
		return null;
	}

	/**
	 * The figure closest to the cursor within the hover radius.
	 * <p>
	 * Closest rather than first, so that overlapping figures resolve to the one actually being
	 * pointed at instead of whichever happened to be drawn earliest.
	 */
	@Nullable
	private Ghost nearest(Point mouse)
	{
		Ghost best = null;
		double bestDistance = HOVER_RADIUS;

		for (Ghost ghost : presence.getVisible())
		{
			Point canvas = ghost.canvasLocation();
			if (canvas == null)
			{
				continue;
			}
			double distance = Math.hypot(canvas.getX() - mouse.getX(), canvas.getY() - mouse.getY());
			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = ghost;
			}
		}
		return best;
	}

	private void draw(Graphics2D graphics, Point mouse, Ghost ghost)
	{
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setFont(FONT);
		FontMetrics metrics = graphics.getFontMetrics();

		String member = ghost.getMemberName();
		String opponent = ghost.getLabel().equals(member) ? null : ghost.getLabel();

		int lines = opponent == null ? 1 : 2;
		int width = metrics.stringWidth(member);
		if (opponent != null)
		{
			width = Math.max(width, metrics.stringWidth(opponent));
		}

		int boxWidth = width + PADDING * 2;
		int boxHeight = lines * LINE_HEIGHT + PADDING * 2 - 2;

		int x = mouse.getX() + CURSOR_OFFSET;
		int y = mouse.getY() + CURSOR_OFFSET;

		// Keep the label on screen when the cursor is near an edge.
		Rectangle canvas = new Rectangle(client.getCanvasWidth(), client.getCanvasHeight());
		if (x + boxWidth > canvas.width)
		{
			x = Math.max(0, mouse.getX() - boxWidth - 4);
		}
		if (y + boxHeight > canvas.height)
		{
			y = Math.max(0, mouse.getY() - boxHeight - 4);
		}

		graphics.setColor(BACKGROUND);
		graphics.fillRoundRect(x, y, boxWidth, boxHeight, CORNER, CORNER);
		graphics.setColor(BORDER);
		graphics.setStroke(new BasicStroke(1f));
		graphics.drawRoundRect(x, y, boxWidth - 1, boxHeight - 1, CORNER, CORNER);

		int textY = y + PADDING + metrics.getAscent() - 2;
		graphics.setColor(MEMBER_TEXT);
		graphics.drawString(member, x + PADDING, textY);

		if (opponent != null)
		{
			graphics.setColor(OPPONENT_TEXT);
			graphics.drawString(opponent, x + PADDING, textY + LINE_HEIGHT);
		}
	}
}
