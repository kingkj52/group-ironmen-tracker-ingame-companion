package com.groupironmencompanion.bank;

import com.groupironmencompanion.GroupIronmenCompanionConfig;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;

/**
 * Mouse and keyboard handling for the group bank window.
 * <p>
 * Input is only ever consumed while the window is open and the pointer is inside it, or on
 * the small Group tab. Every action it can take is client-side: opening and closing the
 * plugin's own overlay, switching tab, scrolling, and typing into the plugin's own search
 * box. No input is translated into anything the game sends to the server.
 */
@Singleton
public class GroupBankInput extends MouseAdapter implements MouseWheelListener, KeyListener
{
	private static final int SCROLL_ROWS = 3;

	@Inject
	private GroupBankViewer viewer;

	@Inject
	private GroupBankOverlay overlay;

	@Inject
	private GroupBankTabOverlay tabOverlay;

	@Inject
	private GroupIronmenCompanionConfig config;

	@Inject
	private ClientThread clientThread;

	// ------------------------------------------------------------------
	// Mouse
	// ------------------------------------------------------------------

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (!config.bankViewer())
		{
			return event;
		}

		// Alt is RuneLite's overlay drag modifier; leave those clicks alone so the window
		// stays repositionable like any other overlay.
		if (event.isAltDown())
		{
			return event;
		}

		java.awt.Point point = event.getPoint();

		Rectangle tab = tabOverlay.getHit();
		if (tab != null && tab.contains(point))
		{
			if (!viewer.isOpen())
			{
				openBesideBank();
			}
			else
			{
				viewer.setOpen(false);
			}
			event.consume();
			return event;
		}

		if (!viewer.isOpen())
		{
			return event;
		}

		// One snapshot for the whole decision, so every test below sees the same frame.
		GroupBankOverlay.HitBoxes hits = overlay.getHits();

		if (!hits.window.contains(point))
		{
			// A click elsewhere just drops search focus; it still reaches the game.
			viewer.setSearchFocused(false);
			return event;
		}

		if (hits.close.contains(point))
		{
			viewer.setOpen(false);
			event.consume();
			return event;
		}

		for (GroupBankOverlay.TabHit hit : hits.tabs)
		{
			if (hit.bounds.contains(point))
			{
				viewer.setActiveTab(hit.tab);
				viewer.setSearchFocused(false);
				event.consume();
				return event;
			}
		}

		viewer.setSearchFocused(hits.search.contains(point));

		// Swallow anything else inside the window so clicks do not fall through to the game.
		event.consume();
		return event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		return swallowInsideWindow(event);
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		return swallowInsideWindow(event);
	}

	private MouseEvent swallowInsideWindow(MouseEvent event)
	{
		if (config.bankViewer() && viewer.isOpen() && !event.isAltDown()
			&& overlay.getHits().window.contains(event.getPoint()))
		{
			event.consume();
		}
		return event;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
	{
		if (!config.bankViewer() || !viewer.isOpen())
		{
			return event;
		}
		if (!overlay.getHits().grid.contains(event.getPoint()))
		{
			return event;
		}

		int rows = Math.max(1, viewer.getRows().size());
		int maxScroll = Math.max(0, rows - GroupBankOverlay.VISIBLE_ROWS);
		int next = viewer.getScrollRow() + (event.getWheelRotation() > 0 ? SCROLL_ROWS : -SCROLL_ROWS);
		viewer.setScrollRow(Math.max(0, Math.min(maxScroll, next)));

		event.consume();
		return event;
	}

	// ------------------------------------------------------------------
	// Keyboard
	// ------------------------------------------------------------------

	@Override
	public void keyTyped(KeyEvent event)
	{
		if (!viewer.isOpen() || !viewer.isSearchFocused())
		{
			return;
		}

		char typed = event.getKeyChar();
		if (typed >= ' ' && typed != KeyEvent.CHAR_UNDEFINED && viewer.getFilter().length() < 40)
		{
			viewer.setFilter(viewer.getFilter() + typed);
			event.consume();
		}
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (!viewer.isOpen())
		{
			return;
		}

		switch (event.getKeyCode())
		{
			case KeyEvent.VK_ESCAPE:
				if (viewer.isSearchFocused() && !viewer.getFilter().isEmpty())
				{
					viewer.setFilter("");
				}
				else if (viewer.isSearchFocused())
				{
					viewer.setSearchFocused(false);
				}
				else
				{
					viewer.setOpen(false);
				}
				event.consume();
				break;

			case KeyEvent.VK_BACK_SPACE:
				if (viewer.isSearchFocused())
				{
					String filter = viewer.getFilter();
					if (!filter.isEmpty())
					{
						viewer.setFilter(filter.substring(0, filter.length() - 1));
					}
					event.consume();
				}
				break;

			case KeyEvent.VK_ENTER:
				if (viewer.isSearchFocused())
				{
					viewer.setSearchFocused(false);
					event.consume();
				}
				break;

			default:
				// Everything else passes straight through to the game.
				break;
		}
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
		if (viewer.isOpen() && viewer.isSearchFocused() && isTextKey(event))
		{
			event.consume();
		}
	}

	private static boolean isTextKey(KeyEvent event)
	{
		int code = event.getKeyCode();
		return code == KeyEvent.VK_BACK_SPACE
			|| code == KeyEvent.VK_ENTER
			|| code == KeyEvent.VK_ESCAPE
			|| (event.getKeyChar() >= ' ' && event.getKeyChar() != KeyEvent.CHAR_UNDEFINED);
	}

	@Override
	public void focusLost()
	{
		viewer.setSearchFocused(false);
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	/**
	 * Opens the window, placing it only if the user has never moved it.
	 * <p>
	 * The window is opened first and unconditionally, because this is called from the AWT
	 * thread (the hotkey and the Group tab) as well as from game events. Working out where to
	 * put it means reading the bank widget, which is only legal on the client thread, so that
	 * part hops threads. Doing it inline was why the hotkey and the tab appeared to do
	 * nothing: the client call threw before the window was ever opened.
	 * <p>
	 * A remembered position always wins. RuneLite persists it when you alt-drag the overlay
	 * and restores it on startup, so re-docking on every open would throw that away.
	 */
	public void openBesideBank()
	{
		viewer.setOpen(true);

		if (overlay.getPreferredLocation() == null)
		{
			clientThread.invoke(() ->
			{
				if (overlay.getPreferredLocation() == null)
				{
					overlay.setPreferredLocation(tabOverlay.defaultLocation(
						GroupBankOverlay.WIDTH, overlay.getWindowSize().height));
				}
			});
		}
	}
}
