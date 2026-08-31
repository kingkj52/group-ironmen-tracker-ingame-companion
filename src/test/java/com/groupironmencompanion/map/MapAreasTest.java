package com.groupironmencompanion.map;

import com.google.gson.Gson;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards the generated map-areas.json resource. Regenerate it with tools/mapdump after a
 * game update; if a dungeon moves or the cache format shifts, these break rather than the
 * markers quietly pointing somewhere wrong.
 */
public class MapAreasTest
{
	private final MapAreas areas = MapAreas.get(new Gson());

	private static WorldPoint at(int x, int y)
	{
		return new WorldPoint(x, y, 0);
	}

	private void assertEntrance(String label, WorldPoint inside, int expectedX, int expectedY, int tolerance)
	{
		MapAreas.Located located = areas.locate(inside);
		assertNotNull(label + ": should be recognised as off-surface", located);

		WorldPoint entrance = located.getEntrance();
		assertNotNull(label + ": should resolve to a surface entrance", entrance);

		int dx = entrance.getX() - expectedX;
		int dy = entrance.getY() - expectedY;
		double distance = Math.sqrt(dx * dx + dy * dy);
		assertTrue(
			label + ": entrance " + entrance.getX() + "," + entrance.getY()
				+ " is " + Math.round(distance) + " tiles from the expected "
				+ expectedX + "," + expectedY,
			distance <= tolerance);
	}

	@Test
	public void resolvesStandardUndergroundDungeons()
	{
		assertEntrance("Taverley Dungeon", at(2884, 9813), 2884, 3397, 5);
		assertEntrance("Edgeville Dungeon", at(3096, 9867), 3097, 3468, 5);
		assertEntrance("Varrock Sewers", at(3237, 9858), 3237, 3458, 5);
		assertEntrance("Lumbridge Swamp Caves", at(3169, 9572), 3169, 3172, 5);
		assertEntrance("Asgarnian Ice Dungeon", at(3007, 9550), 3008, 3150, 5);
		assertEntrance("Brimhaven Dungeon", at(2713, 9564), 2744, 3154, 5);
		assertEntrance("Kalphite Lair", at(3483, 9510), 3227, 3108, 5);
	}

	@Test
	public void resolvesDetachedAreasThatAreNotInTheUndergroundBand()
	{
		// These sit at their own coordinates rather than surface y + 6400, so they only
		// resolve through the cache's intermap links.
		assertEntrance("God Wars Dungeon", at(2882, 5310), 2918, 3746, 5);
		assertEntrance("Zanaris", at(2452, 4473), 3203, 3169, 5);
		assertEntrance("Stronghold of Security", at(1859, 5243), 3081, 3420, 5);
		assertEntrance("Ancient Cavern", at(1763, 5361), 2531, 3446, 5);
		assertEntrance("Ourania Altar", at(3040, 5600), 2452, 3231, 5);
		assertEntrance("Ruins of Camdozaal", at(2952, 5766), 2999, 3493, 5);
	}

	@Test
	public void resolvesAreasWhoseOnlyLinkLeadsIntoAnotherDungeon()
	{
		// Mor Ul Rek links into Karamja Underground, whose own exit is the volcano.
		assertEntrance("Mor Ul Rek", at(2480, 5175), 2856, 3168, 8);
		// Dorgesh-Kaan is reached through the Lumbridge Swamp Caves.
		assertEntrance("Dorgesh-Kaan", at(2720, 5344), 3209, 3218, 30);
	}

	@Test
	public void fallsBackToProjectionWhenTheCacheHasNoLink()
	{
		// The Barrows tunnels have no intermap link, but like every area in the underground
		// band they sit exactly 6400 tiles below the surface they belong to.
		MapAreas.Located located = areas.locate(at(3565, 9695));
		assertNotNull(located);
		assertNotNull(located.getEntrance());
		assertEquals(3565, located.getEntrance().getX());
		assertEquals(3295, located.getEntrance().getY());
	}

	@Test
	public void snapsAnUnmappedDungeonToARealEntrance()
	{
		// Varlamore's Hunter Guild caverns are in no area the game cache describes, so this
		// goes down the projection path. It must land on the guild's own entrance, and stay
		// on it as the member walks about, rather than sliding across the overworld with them.
		MapAreas.Located inside = areas.locate(at(1560, 9460));
		assertNotNull(inside);
		assertNotNull(inside.getEntrance());
		assertEquals("Hunter Guild Caverns", inside.getEntranceName());

		MapAreas.Located twentyNorth = areas.locate(at(1560, 9480));
		assertNotNull(twentyNorth.getEntrance());
		assertEquals("the entrance must not move with the member",
			inside.getEntrance().getX(), twentyNorth.getEntrance().getX());
		assertEquals("the entrance must not move with the member",
			inside.getEntrance().getY(), twentyNorth.getEntrance().getY());
	}

	@Test
	public void doesNotSnapToAConfidentlyWrongEntrance()
	{
		// The Barrows tunnels project to within 85 tiles of the Shade Catacombs entrance,
		// which is a different dungeon. Better to fall back to the bare projection than to
		// point confidently at the wrong door.
		MapAreas.Located located = areas.locate(at(3565, 9695));
		assertNotNull(located);
		assertNotNull(located.getEntrance());
		assertNull("must not borrow a neighbouring dungeon's name", located.getEntranceName());
	}

	@Test
	public void namesTheAreaEvenWhenItIsNotADungeon()
	{
		MapAreas.Located located = areas.locate(at(2884, 9813));
		assertNotNull(located);
		assertEquals("Taverley Underground", located.getAreaName());
		assertEquals("Taverley Dungeon", located.describe());
	}

	@Test
	public void overworldPositionsAreNotTreatedAsUnderground()
	{
		// Somewhere in Varrock, and a first-floor building: both are drawn on the world map
		// where they stand and must not gain an entrance marker.
		assertNull(areas.locate(at(3222, 3218)));
		assertNull(areas.locate(new WorldPoint(3222, 3218, 1)));
		assertNull(areas.locate(at(1637, 3673)));
	}
}
