package com.groupironmencompanion.map;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Brings the World Map Plus plugin's map to a member, when that plugin is installed.
 * <p>
 * World Map Plus draws its own map over the game's, so jumping the game's map under it would show the
 * member's surface entrance at best. It listens for a {@link PluginMessage} to centre its map on a
 * place, which can be a place underground, so the member is shown where they really are.
 * <p>
 * The member markers themselves need nothing here: they are world map points, which World Map Plus
 * reads and draws on its own map. If the plugin is not installed nothing subscribes to the message and
 * the post does nothing.
 * <p>
 * Contract, from World Map Plus's README:
 * <pre>
 *   namespace "worldmapplus"
 *   name      "focus"  data { "point": WorldPoint, "zoom": Number (optional), "name": String (optional) }
 * </pre>
 */
@Singleton
public class WorldMapPlusBridge
{
	private static final String NAMESPACE = "worldmapplus";
	private static final String MESSAGE_FOCUS = "focus";

	/** Pixels per tile to look at a member from: close enough to see where they are standing. */
	static final double MEMBER_ZOOM = 6.0;

	private final EventBus eventBus;

	@Inject
	public WorldMapPlusBridge(EventBus eventBus)
	{
		this.eventBus = eventBus;
	}

	/** Asks World Map Plus to centre on a place, with a name for the mark it puts there. */
	public void focus(WorldPoint point, String name)
	{
		if (point == null)
		{
			return;
		}
		Map<String, Object> data = new HashMap<>();
		data.put("point", point);
		data.put("zoom", MEMBER_ZOOM);
		data.put("name", name == null ? "" : name);
		eventBus.post(new PluginMessage(NAMESPACE, MESSAGE_FOCUS, data));
	}
}
