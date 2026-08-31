package com.groupironmencompanion.map;

import com.groupironmencompanion.GroupIronmenCompanionConfig;
import com.groupironmencompanion.data.GroupMember;
import com.groupironmencompanion.data.GroupState;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Draws each group member on the world map.
 * <p>
 * The world map only, never the minimap and never the game scene: this is for finding your
 * team on a map you have deliberately opened, not for anything happening around you. The
 * markers carry a tooltip and nothing else, so no menu entry is created anywhere.
 */
@Singleton
public class GroupMapMarkers
{
	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private GroupState groupState;

	@Inject
	private GroupIronmenCompanionConfig config;

	@Inject
	private com.google.gson.Gson gson;

	/** Marker for each member's true position, keyed by member name. */
	private final Map<String, WorldMapPoint> positionMarkers = new HashMap<>();

	/** Extra marker at the surface entrance when a member is underground. */
	private final Map<String, WorldMapPoint> entranceMarkers = new HashMap<>();

	/** Colour each marker image was drawn with, so images are only redrawn when needed. */
	private final Map<String, Color> markerColours = new HashMap<>();

	public void clear()
	{
		for (WorldMapPoint point : positionMarkers.values())
		{
			worldMapPointManager.remove(point);
		}
		for (WorldMapPoint point : entranceMarkers.values())
		{
			worldMapPointManager.remove(point);
		}
		positionMarkers.clear();
		entranceMarkers.clear();
		markerColours.clear();
	}

	/** Rebuilds the markers from the current group state. Call on the client thread. */
	public void refresh()
	{
		if (!config.worldMapMarkers())
		{
			clear();
			return;
		}

		List<GroupMember> members = groupState.getMembers();
		Map<String, WorldMapPoint> keptPositions = new HashMap<>();
		Map<String, WorldMapPoint> keptEntrances = new HashMap<>();

		for (GroupMember member : members)
		{
			// Your own position is already on the map as the local player.
			if (groupState.isLocalPlayer(member))
			{
				continue;
			}
			if (!config.worldMapOffline() && !member.isOnline(config.offlineAfterMinutes()))
			{
				continue;
			}

			WorldPoint position = member.getWorldPoint();
			if (position == null)
			{
				continue;
			}

			String name = member.getName();
			Color colour = member.getColour();
			boolean recolour = !colour.equals(markerColours.get(name));
			markerColours.put(name, colour);

			WorldMapPoint marker = positionMarkers.remove(name);
			if (marker == null)
			{
				marker = new WorldMapPoint(position, MarkerImages.pin(colour, name));
				marker.setJumpOnClick(false);
				marker.setSnapToEdge(false);
				worldMapPointManager.add(marker);
			}
			else if (recolour)
			{
				marker.setImage(MarkerImages.pin(colour, name));
			}
			marker.setWorldPoint(position);
			marker.setTooltip(describe(member, position));
			keptPositions.put(name, marker);

			MapAreas.Located located = config.worldMapEntrances() ? mapAreas().locate(position) : null;

			if (located != null && located.getEntrance() != null)
			{
				WorldMapPoint entranceMarker = entranceMarkers.remove(name);
				if (entranceMarker == null)
				{
					entranceMarker = new WorldMapPoint(located.getEntrance(), MarkerImages.entrance(colour, name));
					entranceMarker.setJumpOnClick(false);
					entranceMarker.setSnapToEdge(false);
					worldMapPointManager.add(entranceMarker);
				}
				else if (recolour)
				{
					entranceMarker.setImage(MarkerImages.entrance(colour, name));
				}
				entranceMarker.setWorldPoint(located.getEntrance());
				entranceMarker.setTooltip(name + " is below, in " + located.describe());
				keptEntrances.put(name, entranceMarker);
			}
		}

		// Anything still in the old maps belongs to a member who left, went offline or
		// surfaced, so it is no longer wanted.
		for (WorldMapPoint stale : positionMarkers.values())
		{
			worldMapPointManager.remove(stale);
		}
		for (WorldMapPoint stale : entranceMarkers.values())
		{
			worldMapPointManager.remove(stale);
		}

		positionMarkers.clear();
		positionMarkers.putAll(keptPositions);
		entranceMarkers.clear();
		entranceMarkers.putAll(keptEntrances);
	}

	private MapAreas mapAreas()
	{
		return MapAreas.get(gson);
	}

	private String describe(GroupMember member, WorldPoint position)
	{
		StringBuilder text = new StringBuilder(member.getName());

		if (member.hasStats())
		{
			text.append("  |  ").append(member.getHitpoints()).append('/').append(member.getMaxHitpoints())
				.append(" hp  |  world ").append(member.getWorld());
		}

		MapAreas.Located located = mapAreas().locate(position);
		if (located != null)
		{
			text.append("  |  ").append(located.describe());
		}
		else if (position.getPlane() > 0)
		{
			text.append("  |  floor ").append(position.getPlane());
		}

		if (!member.isOnline(config.offlineAfterMinutes()))
		{
			text.append("  |  last seen ").append(member.getLastSeenText());
		}

		return text.toString();
	}
}
