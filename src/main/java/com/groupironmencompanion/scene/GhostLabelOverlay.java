package com.groupironmencompanion.scene;

import com.groupironmencompanion.GroupIronmenCompanionConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collection;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Names the ghost under the cursor, with what it's doing underneath.
 * <p>
 * Styled to look nothing like the game's right-click menu, because it isn't one. Nothing here
 * can be clicked: it registers no mouse listener and the plugin creates no menu entries. It
 * just reads where the cursor is.
 */
@Singleton
public class GhostLabelOverlay extends Overlay
{
	private static final Color BACKGROUND = new Color(14, 22, 38, 225);
	private static final Color BORDER = new Color(96, 148, 205, 190);
	private static final Color TITLE_TEXT = new Color(176, 214, 255);
	private static final Color DETAIL_TEXT = new Color(150, 170, 196);

	private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

	// How close the cursor has to be to a figure, in pixels.
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
		// This runs every frame, so check the empty list before touching config.
		Collection<Ghost> ghosts = presence.getVisible();
		if (ghosts.isEmpty() || !config.scenePresence() || !config.scenePresenceLabels())
		{
			return null;
		}

		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return null;
		}

		Ghost hovered = nearest(ghosts, mouse);
		if (hovered != null)
		{
			draw(graphics, mouse, hovered);
		}
		return null;
	}

	// The closest one, so overlapping figures resolve to the one actually pointed at.
	@Nullable
	private static Ghost nearest(Collection<Ghost> ghosts, Point mouse)
	{
		Ghost best = null;
		double bestDistance = HOVER_RADIUS;
		for (Ghost ghost : ghosts)
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

		String title = ghost.getTitle();
		String detail = ghost.getDetail();

		int width = metrics.stringWidth(title);
		if (detail != null)
		{
			width = Math.max(width, metrics.stringWidth(detail));
		}
		int boxWidth = width + PADDING * 2;
		int boxHeight = (detail == null ? 1 : 2) * LINE_HEIGHT + PADDING * 2 - 2;

		// Beside the cursor, flipped to the other side near the canvas edge.
		int x = mouse.getX() + CURSOR_OFFSET;
		int y = mouse.getY() + CURSOR_OFFSET;
		if (x + boxWidth > client.getCanvasWidth())
		{
			x = Math.max(0, mouse.getX() - boxWidth - 4);
		}
		if (y + boxHeight > client.getCanvasHeight())
		{
			y = Math.max(0, mouse.getY() - boxHeight - 4);
		}

		graphics.setColor(BACKGROUND);
		graphics.fillRoundRect(x, y, boxWidth, boxHeight, CORNER, CORNER);
		graphics.setColor(BORDER);
		graphics.setStroke(new BasicStroke(1f));
		graphics.drawRoundRect(x, y, boxWidth - 1, boxHeight - 1, CORNER, CORNER);

		int textY = y + PADDING + metrics.getAscent() - 2;
		graphics.setColor(TITLE_TEXT);
		graphics.drawString(title, x + PADDING, textY);
		if (detail != null)
		{
			graphics.setColor(DETAIL_TEXT);
			graphics.drawString(detail, x + PADDING, textY + LINE_HEIGHT);
		}
	}
}
