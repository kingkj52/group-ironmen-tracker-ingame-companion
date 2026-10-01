package com.groupironmencompanion.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;

public class WorldMapPlusBridgeTest
{
	/** An event bus only calls public methods of public classes. */
	public static final class Listener
	{
		private final List<PluginMessage> into;

		Listener(List<PluginMessage> into)
		{
			this.into = into;
		}

		@Subscribe
		public void onPluginMessage(PluginMessage message)
		{
			into.add(message);
		}
	}

	@Test
	public void focusAsksWorldMapPlusToCentreOnThePlace()
	{
		List<PluginMessage> sent = new ArrayList<>();
		EventBus bus = new EventBus();
		bus.register(new Listener(sent));
		WorldPoint where = new WorldPoint(3040, 9820, 0);

		new WorldMapPlusBridge(bus).focus(where, "Alice");

		assertEquals(1, sent.size());
		assertEquals("worldmapplus", sent.get(0).getNamespace());
		assertEquals("focus", sent.get(0).getName());
		assertSame(where, sent.get(0).getData().get("point"));
		assertEquals("Alice", sent.get(0).getData().get("name"));
		assertTrue(((Number) sent.get(0).getData().get("zoom")).doubleValue() > 0);
	}

	@Test
	public void noPlaceSendsNothing()
	{
		List<PluginMessage> sent = new ArrayList<>();
		EventBus bus = new EventBus();
		bus.register(new Listener(sent));
		new WorldMapPlusBridge(bus).focus(null, "Alice");
		assertTrue(sent.isEmpty());
	}
}
