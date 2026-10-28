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
 *
 * <h2>Agreed but not yet built on either side</h2>
 *
 * A reply path, settled with the World Map Plus author in October 2026 and recorded here
 * because an agreement nobody wrote down is an agreement that gets rebuilt differently. None
 * of it is implemented; do not post these until that plugin ships them.
 * <pre>
 *   "covers"  data { "points": List&lt;WorldPoint&gt; (max 16), "id": String }
 *             also accepts the single-point form { "point": WorldPoint, "id": String }
 *   -&gt; "covers_reply" { "id", "covered", "exact", "mapNames", "mapKinds" }
 *      the four lists run parallel to the points asked about
 *
 *   "focus"   gains optional { "exact": Boolean, "id": String }
 *   -&gt; "focus_result" { "id", "ok": Boolean, "mapName", "mapKind", "reason" }
 *      reason "no_map" when exact was asked and no map has imagery of that tile, in which
 *      case the view is left alone rather than drifting to the nearest map
 * </pre>
 * Map kinds are strings - surface, underground, instance, minigame, other - deliberately not
 * ids, so that plugin's internal numbering does not become permanent API.
 * <p>
 * Three things shape the consumer. Replies are <b>deferred</b>: they are queued and posted
 * from that plugin's game tick, never from inside its subscriber, so a reply arrives a tick or
 * more after the request and never re-entrantly during our own post. A {@code focus_result}
 * of "ok" means the view has already moved. And silence is the normal answer when the plugin
 * is absent, so every call needs a timeout - about two seconds - after which we fall back to
 * our own placement.
 * <p>
 * Replies carry back the id we sent and are dropped at their end after five seconds, which is
 * longer than that timeout. So a reply can arrive for a request we have already given up on,
 * and the consumer has to ignore ids it is no longer waiting for rather than acting on a
 * late answer.
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
