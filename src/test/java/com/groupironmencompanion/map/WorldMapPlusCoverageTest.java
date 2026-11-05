package com.groupironmencompanion.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Taking coverage answers from another plugin, which may be late, duplicated or malformed. */
public class WorldMapPlusCoverageTest
{
	// Captures posts so a reply can echo the id the bridge actually sent.
	private static final class Capture extends EventBus
	{
		private final List<PluginMessage> posted = new ArrayList<>();

		@Override
		public void post(Object event)
		{
			if (event instanceof PluginMessage)
			{
				posted.add((PluginMessage) event);
			}
		}

		PluginMessage last()
		{
			return posted.get(posted.size() - 1);
		}
	}

	private static final WorldPoint RIFT = new WorldPoint(3615, 9469, 0);
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

	private Capture bus;
	private WorldMapPlusBridge bridge;

	@Before
	public void setUp()
	{
		bus = new Capture();
		bridge = new WorldMapPlusBridge(bus);
	}

	private String askAbout(WorldPoint... points)
	{
		bridge.requestCoverage(Arrays.asList(points));
		PluginMessage sent = bus.last();
		assertEquals("worldmapplus", sent.getNamespace());
		assertEquals("covers", sent.getName());
		return (String) sent.getData().get("id");
	}

	private void reply(String id, List<Boolean> covered, List<Boolean> exact, List<String> names)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		data.put("covered", covered);
		data.put("exact", exact);
		data.put("mapNames", names);
		bridge.onPluginMessage(new PluginMessage("worldmapplus", "covers_reply", data));
	}

	// The default is "not covered"

	@Test
	public void nothingIsCoveredBeforeAnybodyAnswers()
	{
		// Same as World Map Plus not being installed.
		askAbout(RIFT);
		assertFalse(bridge.isExactlyCovered(RIFT));
	}

	@Test
	public void aNullPointIsNotCovered()
	{
		assertFalse(bridge.isExactlyCovered(null));
	}

	// Taking an answer

	@Test
	public void recordsAnExactAnswerAgainstTheTileItWasAbout()
	{
		String id = askAbout(RIFT);
		reply(id, Collections.singletonList(true), Collections.singletonList(true),
			Collections.singletonList("Guardians of the Rift"));

		assertTrue(bridge.isExactlyCovered(RIFT));
	}

	@Test
	public void coveredButNotExactDoesNotCount()
	{
		// "Covered" can mean their nearest map 192 tiles away. We only step aside for exact.
		String id = askAbout(RIFT);
		reply(id, Collections.singletonList(true), Collections.singletonList(false),
			Collections.singletonList("Morytania Underground"));

		assertFalse(bridge.isExactlyCovered(RIFT));
	}

	@Test
	public void answersAreMatchedToPointsByPosition()
	{
		String id = askAbout(RIFT, LUMBRIDGE);
		reply(id, Arrays.asList(true, true), Arrays.asList(true, false),
			Arrays.asList("Guardians of the Rift", "Gielinor Surface"));

		assertTrue(bridge.isExactlyCovered(RIFT));
		assertFalse(bridge.isExactlyCovered(LUMBRIDGE));
	}

	// Answers we must not act on

	@Test
	public void ignoresAnAnswerToAnIdWeNeverSent()
	{
		reply("gim-not-ours", Collections.singletonList(true), Collections.singletonList(true),
			Collections.singletonList("Somewhere"));

		assertFalse(bridge.isExactlyCovered(RIFT));
	}

	@Test
	public void anIdIsAnsweredOnlyOnce()
	{
		// Their queue outlives our timeout, so late duplicates happen.
		String id = askAbout(RIFT);
		reply(id, Collections.singletonList(true), Collections.singletonList(true),
			Collections.singletonList("Guardians of the Rift"));
		assertTrue(bridge.isExactlyCovered(RIFT));

		reply(id, Collections.singletonList(false), Collections.singletonList(false),
			Collections.singletonList(""));
		assertTrue("a second answer to a spent id must not overwrite the first",
			bridge.isExactlyCovered(RIFT));
	}

	@Test
	public void ignoresMessagesFromOtherNamespacesAndNames()
	{
		String id = askAbout(RIFT);

		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		data.put("covered", Collections.singletonList(true));
		data.put("exact", Collections.singletonList(true));

		bridge.onPluginMessage(new PluginMessage("shortestpath", "covers_reply", data));
		bridge.onPluginMessage(new PluginMessage("worldmapplus", "focus_result", data));
		bridge.onPluginMessage(new PluginMessage("worldmapplus", "covers", data));

		assertFalse(bridge.isExactlyCovered(RIFT));
	}

	// Malformed payloads

	@Test
	public void survivesAnAnswerMissingItsLists()
	{
		String id = askAbout(RIFT);

		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		bridge.onPluginMessage(new PluginMessage("worldmapplus", "covers_reply", data));

		assertFalse(bridge.isExactlyCovered(RIFT));
	}

	@Test
	public void survivesAnAnswerShorterThanTheQuestion()
	{
		// The tile with no answer stays unknown rather than borrowing its neighbour's.
		String id = askAbout(RIFT, LUMBRIDGE);
		reply(id, Collections.singletonList(true), Collections.singletonList(true),
			Collections.singletonList("Guardians of the Rift"));

		assertTrue(bridge.isExactlyCovered(RIFT));
		assertFalse(bridge.isExactlyCovered(LUMBRIDGE));
	}

	@Test
	public void survivesWrongTypesInTheLists()
	{
		String id = askAbout(RIFT);

		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		data.put("covered", Collections.singletonList("yes"));
		data.put("exact", Collections.singletonList(42));
		data.put("mapNames", "not a list");
		bridge.onPluginMessage(new PluginMessage("worldmapplus", "covers_reply", data));

		assertFalse(bridge.isExactlyCovered(RIFT));
	}

	// Asking

	@Test
	public void sendsAnIdTheirRulesAccept()
	{
		// They ignore ids over 64 characters or with anything but letters, digits and _ - . :
		String id = askAbout(RIFT);
		assertTrue(id.length() <= 64);
		assertFalse(id.isEmpty());
		for (char c : id.toCharArray())
		{
			boolean plain = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
				|| c == '_' || c == '-' || c == '.' || c == ':';
			assertTrue("id character '" + c + "' would be rejected", plain);
		}
	}

	@Test
	public void asksForEveryFocusToBeExact()
	{
		// Otherwise their view drifts to black space off the nearest map.
		bridge.focus(RIFT, "Someone");
		assertEquals("focus", bus.last().getName());
		assertEquals(Boolean.TRUE, bus.last().getData().get("exact"));
	}

	@Test
	public void doesNotRepeatAQuestionAlreadyAnswered()
	{
		String id = askAbout(RIFT);
		int before = bus.posted.size();
		reply(id, Collections.singletonList(true), Collections.singletonList(true),
			Collections.singletonList("Guardians of the Rift"));

		bridge.requestCoverage(Collections.singletonList(RIFT));
		assertEquals("a tile with an answer should not be asked about again",
			before, bus.posted.size());
	}

	@Test
	public void asksAtMostSixteenTilesAtOnce()
	{
		// Their cap; more gets the whole question refused.
		List<WorldPoint> many = new ArrayList<>();
		for (int i = 0; i < 40; i++)
		{
			many.add(new WorldPoint(3000 + i, 3000, 0));
		}
		bridge.requestCoverage(many);

		List<?> sent = (List<?>) bus.last().getData().get("points");
		assertTrue("sent " + sent.size() + " tiles", sent.size() <= 16);
	}

	@Test
	public void resetForgetsEverything()
	{
		String id = askAbout(RIFT);
		reply(id, Collections.singletonList(true), Collections.singletonList(true),
			Collections.singletonList("Guardians of the Rift"));
		assertTrue(bridge.isExactlyCovered(RIFT));

		bridge.reset();
		assertFalse(bridge.isExactlyCovered(RIFT));
	}
}
