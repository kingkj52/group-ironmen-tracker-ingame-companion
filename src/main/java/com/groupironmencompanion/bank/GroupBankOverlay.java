package com.groupironmencompanion.bank;

import com.groupironmencompanion.GroupIronmenCompanionConfig;
import com.groupironmencompanion.data.GroupMember;
import com.groupironmencompanion.data.GroupState;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Draws the read-only group bank window.
 * <p>
 * The window is entirely the plugin's own drawing. It is not a game interface, it never
 * modifies, hides, resizes or reparents any game widget, and it creates no menu entries. It
 * cannot move items: there is no withdraw, deposit, or any other action on anything shown,
 * because the plugin only ever has item ids and quantities read back from your group's
 * server.
 */
@Singleton
public class GroupBankOverlay extends Overlay
{
	static final int CELL_WIDTH = 40;
	static final int CELL_HEIGHT = 36;
	static final int VISIBLE_ROWS = 7;

	private static final int PADDING = 8;
	private static final int TITLE_HEIGHT = 22;
	private static final int TAB_HEIGHT = 20;
	private static final int SEARCH_HEIGHT = 20;
	private static final int FOOTER_HEIGHT = 18;
	private static final int SCROLLBAR_WIDTH = 6;

	private static final int HEIGHT =
		TITLE_HEIGHT + TAB_HEIGHT + SEARCH_HEIGHT + VISIBLE_ROWS * CELL_HEIGHT + FOOTER_HEIGHT + PADDING;

	static final int WIDTH =
		PADDING * 2 + GroupBankViewer.COLUMNS * CELL_WIDTH + SCROLLBAR_WIDTH;

	private static final Font TAB_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	private static final Font QUANTITY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

	@Inject
	private Client client;

	@Inject
	private GroupBankViewer viewer;

	@Inject
	private GroupState groupState;

	@Inject
	private ItemManager itemManager;

	@Inject
	private GroupIronmenCompanionConfig config;

	@Inject
	private TooltipManager tooltipManager;

	/**
	 * Hit boxes in canvas coordinates, rebuilt on the client thread every frame and read on
	 * the AWT thread by {@link GroupBankInput}. Published as one immutable snapshot so the
	 * input handler can never observe a half-built frame or iterate a list being rewritten.
	 */
	private volatile HitBoxes hits = HitBoxes.EMPTY;

	/** Accumulates this frame's hit boxes before they are published. */
	private List<TabHit> frameTabs = new ArrayList<>();
	private Rectangle frameClose = new Rectangle();
	private Rectangle frameSearch = new Rectangle();
	private Rectangle frameGrid = new Rectangle();

	@Inject
	public GroupBankOverlay()
	{
		// DYNAMIC positions the window purely from its preferred location; setMovable must
		// come after setPosition, which resets it.
		setPosition(OverlayPosition.DYNAMIC);
		setMovable(true);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public Dimension getWindowSize()
	{
		return new Dimension(WIDTH, HEIGHT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.bankViewer() || !viewer.isOpen())
		{
			hits = HitBoxes.EMPTY;
			return null;
		}

		viewer.rebuildIfNeeded();

		java.awt.Point origin = getBounds().getLocation();
		Point mouse = client.getMouseCanvasPosition();
		java.awt.Point local = mouse == null
			? new java.awt.Point(-1, -1)
			: new java.awt.Point(mouse.getX() - origin.x, mouse.getY() - origin.y);

		frameTabs = new ArrayList<>();

		drawFrame(graphics);
		int y = drawTitle(graphics, origin, local);
		y = drawTabs(graphics, origin, y, local);
		y = drawSearch(graphics, origin, y, local);
		drawGrid(graphics, origin, y, local);
		drawFooter(graphics);

		hits = new HitBoxes(
			new Rectangle(origin.x, origin.y, WIDTH, HEIGHT),
			frameClose, frameSearch, frameGrid, frameTabs);

		return new Dimension(WIDTH, HEIGHT);
	}

	// ------------------------------------------------------------------
	// Drawing
	// ------------------------------------------------------------------

	private void drawFrame(Graphics2D graphics)
	{
		graphics.setColor(BankStyle.BACKGROUND);
		graphics.fillRect(0, 0, WIDTH, HEIGHT);
		graphics.setColor(BankStyle.BORDER);
		graphics.setStroke(new BasicStroke(1f));
		graphics.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);
	}

	private int drawTitle(Graphics2D graphics, java.awt.Point origin, java.awt.Point local)
	{
		graphics.setColor(BankStyle.TITLE_BACKGROUND);
		graphics.fillRect(1, 1, WIDTH - 2, TITLE_HEIGHT - 1);

		graphics.setColor(BankStyle.TEXT);
		graphics.setFont(TAB_FONT.deriveFont(Font.BOLD, 12f));
		graphics.drawString("Group Bank  (view only)", PADDING, 15);

		Rectangle close = new Rectangle(WIDTH - 20, 4, 14, 14);
		boolean hovered = close.contains(local);
		graphics.setColor(hovered ? Color.WHITE : BankStyle.TEXT_DIM);
		graphics.setFont(TAB_FONT.deriveFont(Font.BOLD, 12f));
		graphics.drawString("x", close.x + 5, close.y + 11);

		frameClose = toCanvas(close, origin);
		return TITLE_HEIGHT;
	}

	private int drawTabs(Graphics2D graphics, java.awt.Point origin, int top, java.awt.Point local)
	{
		List<String> tabs = viewer.getTabs();
		if (tabs.isEmpty())
		{
			return top;
		}

		graphics.setFont(TAB_FONT);
		FontMetrics metrics = graphics.getFontMetrics();

		int x = PADDING;
		int available = WIDTH - PADDING * 2;
		for (String tab : tabs)
		{
			String label = abbreviate(tab, metrics, 64);
			int width = metrics.stringWidth(label) + 12;

			if (x + width > available && x > PADDING)
			{
				// Out of room on this strip; the remaining tabs are unreachable, so stop
				// rather than drawing them off the edge.
				break;
			}

			Rectangle rect = new Rectangle(x, top + 2, width, TAB_HEIGHT - 4);
			boolean active = tab.equals(viewer.getActiveTab());

			graphics.setColor(active ? BankStyle.TAB_ACTIVE : BankStyle.TAB_IDLE);
			graphics.fillRect(rect.x, rect.y, rect.width, rect.height);

			if (rect.contains(local) && !active)
			{
				graphics.setColor(BankStyle.HOVER);
				graphics.fillRect(rect.x, rect.y, rect.width, rect.height);
			}

			graphics.setColor(active ? Color.WHITE : colourForTab(tab));
			graphics.drawString(label, rect.x + 6, rect.y + rect.height - 5);

			frameTabs.add(new TabHit(tab, toCanvas(rect, origin)));
			x += width + 2;
		}

		return top + TAB_HEIGHT;
	}

	private int drawSearch(Graphics2D graphics, java.awt.Point origin, int top, java.awt.Point local)
	{
		Rectangle rect = new Rectangle(PADDING, top + 1, WIDTH - PADDING * 2, SEARCH_HEIGHT - 4);

		graphics.setColor(BankStyle.SEARCH_BACKGROUND);
		graphics.fillRect(rect.x, rect.y, rect.width, rect.height);
		graphics.setColor(viewer.isSearchFocused() ? BankStyle.TEXT : BankStyle.BORDER);
		graphics.drawRect(rect.x, rect.y, rect.width, rect.height);

		graphics.setFont(TAB_FONT);
		String filter = viewer.getFilter();
		String text;
		Color colour;
		if (!filter.isEmpty())
		{
			text = filter + (viewer.isSearchFocused() && blink() ? "|" : "");
			colour = Color.WHITE;
		}
		else if (viewer.isSearchFocused())
		{
			text = blink() ? "|" : "";
			colour = Color.WHITE;
		}
		else
		{
			text = "Click here to search";
			colour = BankStyle.TEXT_DIM;
		}

		graphics.setColor(colour);
		graphics.drawString(text, rect.x + 5, rect.y + rect.height - 4);

		frameSearch = toCanvas(rect, origin);
		return top + SEARCH_HEIGHT;
	}

	private void drawGrid(Graphics2D graphics, java.awt.Point origin, int top, java.awt.Point local)
	{
		Rectangle area = new Rectangle(PADDING, top, GroupBankViewer.COLUMNS * CELL_WIDTH,
			VISIBLE_ROWS * CELL_HEIGHT);
		frameGrid = toCanvas(area, origin);

		List<GroupBankViewer.Row> rows = viewer.getRows();
		if (rows.isEmpty())
		{
			graphics.setColor(BankStyle.TEXT_DIM);
			graphics.setFont(TAB_FONT);
			String message = viewer.getFilter().isEmpty()
				? "No data yet. Check your group name and token."
				: "Nothing matches \"" + viewer.getFilter() + "\"";
			graphics.drawString(message, area.x + 4, area.y + 18);
			return;
		}

		java.awt.Shape clip = graphics.getClip();
		graphics.clipRect(area.x, area.y, area.width, area.height);

		int first = Math.min(viewer.getScrollRow(), Math.max(0, rows.size() - 1));
		int y = area.y;

		for (int index = first; index < rows.size() && y < area.y + area.height; index++)
		{
			GroupBankViewer.Row row = rows.get(index);

			if (row.isHeader())
			{
				graphics.setColor(BankStyle.TEXT);
				graphics.setFont(TAB_FONT.deriveFont(Font.BOLD));
				graphics.drawString(row.getHeader(), area.x + 2, y + 15);
				graphics.setColor(BankStyle.BORDER);
				graphics.drawLine(area.x + 2, y + 19, area.x + area.width - 4, y + 19);
				y += CELL_HEIGHT;
				continue;
			}

			int x = area.x;
			for (GroupBankViewer.Entry entry : row.getItems())
			{
				drawCell(graphics, entry, x, y, local);
				x += CELL_WIDTH;
			}
			y += CELL_HEIGHT;
		}

		graphics.setClip(clip);
		drawScrollbar(graphics, area, rows.size(), first);
	}

	private void drawCell(Graphics2D graphics, GroupBankViewer.Entry entry, int x, int y, java.awt.Point local)
	{
		Rectangle cell = new Rectangle(x, y, CELL_WIDTH, CELL_HEIGHT);
		boolean hovered = cell.contains(local);

		if (hovered)
		{
			graphics.setColor(BankStyle.HOVER);
			graphics.fillRect(cell.x, cell.y, cell.width, cell.height);
		}

		long quantity = entry.getQuantity();

		// Always request the plain, quantity-1 sprite. Quantity is part of ItemManager's
		// image cache key and that cache holds only 128 entries, so asking for group-wide
		// totals would evict and re-render sprites every frame. The stack text is drawn
		// below instead, which also lets totals exceed what the game's own text renders.
		BufferedImage image = itemManager.getImage(entry.getItemId());
		if (image != null)
		{
			graphics.drawImage(image, x + (CELL_WIDTH - image.getWidth()) / 2,
				y + (CELL_HEIGHT - image.getHeight()) / 2, null);
		}

		if (quantity > 1)
		{
			graphics.setFont(QUANTITY_FONT);
			String label = BankStyle.shortQuantity(quantity);
			graphics.setColor(Color.BLACK);
			graphics.drawString(label, x + 3, y + 12);
			graphics.setColor(BankStyle.quantityColour(quantity));
			graphics.drawString(label, x + 2, y + 11);
		}

		if (hovered)
		{
			addTooltip(entry);
		}
	}

	private void drawScrollbar(Graphics2D graphics, Rectangle area, int totalRows, int firstRow)
	{
		if (totalRows <= VISIBLE_ROWS)
		{
			return;
		}

		int x = area.x + area.width + 1;
		graphics.setColor(BankStyle.SEARCH_BACKGROUND);
		graphics.fillRect(x, area.y, SCROLLBAR_WIDTH - 2, area.height);

		int thumbHeight = Math.max(12, area.height * VISIBLE_ROWS / totalRows);
		int travel = area.height - thumbHeight;
		int maxScroll = Math.max(1, totalRows - VISIBLE_ROWS);
		int thumbY = area.y + (int) ((long) travel * Math.min(firstRow, maxScroll) / maxScroll);

		graphics.setColor(BankStyle.BORDER);
		graphics.fillRect(x, thumbY, SCROLLBAR_WIDTH - 2, thumbHeight);
	}

	private void drawFooter(Graphics2D graphics)
	{
		graphics.setFont(TAB_FONT);
		graphics.setColor(BankStyle.TEXT_DIM);

		StringBuilder text = new StringBuilder();
		text.append(viewer.getVisibleItemCount()).append(" stacks");
		if (config.bankShowPrices())
		{
			text.append("  ·  ").append(BankStyle.gp(viewer.getVisibleValue())).append(" gp");
		}
		graphics.drawString(text.toString(), PADDING, HEIGHT - 6);
	}

	// ------------------------------------------------------------------
	// Tooltip
	// ------------------------------------------------------------------

	private void addTooltip(GroupBankViewer.Entry entry)
	{
		String name = viewer.itemName(entry.getItemId());
		if (name == null)
		{
			return;
		}

		PanelComponent panel = new PanelComponent();
		panel.setPreferredSize(new Dimension(190, 0));
		panel.getChildren().add(TitleComponent.builder().text(name).color(Color.WHITE).build());

		panel.getChildren().add(LineComponent.builder()
			.left("Quantity")
			.leftColor(BankStyle.TEXT_DIM)
			.right(BankStyle.commas(entry.getQuantity()))
			.rightColor(BankStyle.quantityColour(entry.getQuantity()))
			.build());

		if (config.bankShowPrices())
		{
			long value = (long) itemManager.getItemPrice(entry.getItemId()) * entry.getQuantity();
			panel.getChildren().add(LineComponent.builder()
				.left("Value")
				.leftColor(BankStyle.TEXT_DIM)
				.right(BankStyle.gp(value) + " gp")
				.rightColor(BankStyle.TEXT_DIM)
				.build());
		}

		if (GroupBankViewer.TAB_ALL.equals(viewer.getActiveTab()))
		{
			Map<String, Long> owners = viewer.ownersOf(entry.getItemId());
			for (Map.Entry<String, Long> owner : owners.entrySet())
			{
				GroupMember member = groupState.getMember(owner.getKey());
				panel.getChildren().add(LineComponent.builder()
					.left(owner.getKey())
					.leftColor(member != null ? member.getColour() : BankStyle.TEXT_DIM)
					.right(BankStyle.commas(owner.getValue()))
					.rightColor(BankStyle.TEXT_DIM)
					.build());
			}
		}

		tooltipManager.add(new Tooltip(panel));
	}

	// ------------------------------------------------------------------
	// Hit testing, used by GroupBankInput
	// ------------------------------------------------------------------

	/** The most recently drawn frame's hit boxes. Never null. */
	HitBoxes getHits()
	{
		return hits;
	}

	/**
	 * One frame's clickable regions, in canvas coordinates. Immutable once constructed so it
	 * can be handed to the AWT thread without locking.
	 */
	static final class HitBoxes
	{
		static final HitBoxes EMPTY = new HitBoxes(
			new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle(),
			java.util.Collections.emptyList());

		final Rectangle window;
		final Rectangle close;
		final Rectangle search;
		final Rectangle grid;
		final List<TabHit> tabs;

		HitBoxes(Rectangle window, Rectangle close, Rectangle search, Rectangle grid, List<TabHit> tabs)
		{
			this.window = window;
			this.close = close;
			this.search = search;
			this.grid = grid;
			this.tabs = java.util.Collections.unmodifiableList(tabs);
		}
	}

	private static Rectangle toCanvas(Rectangle local, java.awt.Point origin)
	{
		return new Rectangle(local.x + origin.x, local.y + origin.y, local.width, local.height);
	}

	@Nullable
	private Color colourForTab(String tab)
	{
		if (GroupBankViewer.TAB_ALL.equals(tab) || GroupBankViewer.TAB_SHARED.equals(tab))
		{
			return BankStyle.TEXT;
		}
		GroupMember member = groupState.getMember(tab);
		return member != null ? member.getColour() : BankStyle.TEXT;
	}

	private static String abbreviate(String text, FontMetrics metrics, int maxWidth)
	{
		if (metrics.stringWidth(text) <= maxWidth)
		{
			return text;
		}
		String result = text;
		while (result.length() > 1 && metrics.stringWidth(result + "…") > maxWidth)
		{
			result = result.substring(0, result.length() - 1);
		}
		return result + "…";
	}

	private static boolean blink()
	{
		return (System.currentTimeMillis() / 500) % 2 == 0;
	}

	static final class TabHit
	{
		final String tab;
		final Rectangle bounds;

		TabHit(String tab, Rectangle bounds)
		{
			this.tab = tab;
			this.bounds = bounds;
		}
	}
}
