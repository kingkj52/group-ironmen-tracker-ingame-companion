package com.groupironmencompanion.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Talks to the World Map Plus plugin, if it's installed, over {@link PluginMessage}s.
 * <p>
 * Member markers need nothing from here; World Map Plus draws world map points on its own. This
 * is for jumping its view to a member, and asking which tiles it has a map for.
 * <pre>
 *   namespace "worldmapplus"
 *   "focus"  { point, zoom?, name?, exact?, id? }
 *            exact: if no map has imagery of the tile, leave the view alone
 *   "covers" { points: List&lt;WorldPoint&gt; (max 16), id }
 *   -&gt; "covers_reply" { id, covered[], exact[], mapNames[], mapKinds[] }, parallel to points
 * </pre>
 * Replies come a tick or more later (they're queued on that plugin's game tick, never sent from
 * inside its handler), and not at all if it isn't installed. So: wait about two seconds, then
 * assume no. They hold replies for five seconds, longer than we wait, so ignore ids we've
 * already given up on. The ask limit of eight a second is shared with every other plugin.
 * <p>
 * Client thread only.
 */
@Singleton
public class WorldMapPlusBridge
{
	private static final String NAMESPACE = "worldmapplus";
	private static final String MESSAGE_FOCUS = "focus";
	private static final String MESSAGE_COVERS = "covers";
	private static final String REPLY_COVERS = "covers_reply";

	// Close enough to see where they're standing.
	static final double MEMBER_ZOOM = 6.0;

	private static final int MAX_POINTS = 16;

	// Only changes when that plugin updates; short so a newly added map shows up.
	private static final long ANSWER_TTL_MILLIS = 60_000;

	// Well under their shared limit, and still answered long before anyone clicks.
	private static final long ASK_INTERVAL_MILLIS = 1_000;

	private static final long REPLY_TIMEOUT_MILLIS = 2_000;

	private final EventBus eventBus;

	// By tile rather than map square, since a square can straddle a map's edge.
	private final Map<WorldPoint, Coverage> known = new HashMap<>();
	private final Map<String, Pending> pending = new HashMap<>();

	private long lastAskMillis;
	private int nextId;

	@Inject
	public WorldMapPlusBridge(EventBus eventBus)
	{
		this.eventBus = eventBus;
	}

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
		// Otherwise a tile with no map sends their view to black space off the nearest one.
		data.put("exact", Boolean.TRUE);
		eventBus.post(new PluginMessage(NAMESPACE, MESSAGE_FOCUS, data));
	}

	/**
	 * Whether World Map Plus has imagery of this tile, as far as we know. False if we haven't
	 * heard, so not having the plugin is just the normal "no".
	 */
	public boolean isExactlyCovered(WorldPoint point)
	{
		if (point == null)
		{
			return false;
		}
		Coverage coverage = known.get(point);
		return coverage != null && !coverage.isStale() && coverage.exact;
	}

	/**
	 * Asks about any tiles we don't have a fresh answer for. Cheap to call often: it's rate
	 * limited and does nothing when there's nothing new. Ask ahead of time, since a click
	 * can't wait for the reply.
	 */
	public void requestCoverage(Collection<WorldPoint> points)
	{
		expire();
		if (points == null || points.isEmpty())
		{
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastAskMillis < ASK_INTERVAL_MILLIS)
		{
			return;
		}

		List<WorldPoint> asking = new ArrayList<>();
		for (WorldPoint point : points)
		{
			if (point == null || asking.size() >= MAX_POINTS)
			{
				continue;
			}
			Coverage coverage = known.get(point);
			if (coverage == null || coverage.isStale())
			{
				asking.add(point);
			}
		}
		if (asking.isEmpty())
		{
			return;
		}

		lastAskMillis = now;
		// Their ids must be short and only letters, digits and _ - . :
		String id = "gim-" + (nextId++);
		pending.put(id, new Pending(asking, now));

		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		data.put("points", asking);
		eventBus.post(new PluginMessage(NAMESPACE, MESSAGE_COVERS, data));
	}

	/** Sees every plugin message, so ignores anything that isn't a reply to us. */
	public void onPluginMessage(PluginMessage message)
	{
		if (message == null || !NAMESPACE.equals(message.getNamespace())
			|| !REPLY_COVERS.equals(message.getName()))
		{
			return;
		}

		Map<String, Object> data = message.getData();
		if (data == null)
		{
			return;
		}

		// Unknown or already given up on.
		Pending asked = pending.remove(asText(data.get("id")));
		if (asked == null)
		{
			return;
		}

		// Only "exact" matters to us: we stand aside only when they have the actual tile.
		List<?> exact = asList(data.get("exact"));
		if (exact == null)
		{
			return;
		}

		// It's another plugin's data, so don't assume the list is as long as what we asked.
		long now = System.currentTimeMillis();
		for (int i = 0; i < asked.points.size(); i++)
		{
			boolean isExact = i < exact.size() && Boolean.TRUE.equals(exact.get(i));
			known.put(asked.points.get(i), new Coverage(isExact, now));
		}
	}

	/** Forgets everything, when the group changes or the plugin stops. */
	public void reset()
	{
		known.clear();
		pending.clear();
		lastAskMillis = 0;
	}

	private void expire()
	{
		long now = System.currentTimeMillis();
		known.values().removeIf(Coverage::isStale);
		pending.values().removeIf(waiting -> now - waiting.at > REPLY_TIMEOUT_MILLIS);
	}

	@Nullable
	private static String asText(Object value)
	{
		return value instanceof String ? (String) value : null;
	}

	@Nullable
	private static List<?> asList(Object value)
	{
		return value instanceof List ? (List<?>) value : null;
	}

	private static final class Coverage
	{
		private final boolean exact;
		private final long at;

		Coverage(boolean exact, long at)
		{
			this.exact = exact;
			this.at = at;
		}

		boolean isStale()
		{
			return System.currentTimeMillis() - at > ANSWER_TTL_MILLIS;
		}
	}

	private static final class Pending
	{
		private final List<WorldPoint> points;
		private final long at;

		Pending(List<WorldPoint> points, long at)
		{
			this.points = points;
			this.at = at;
		}
	}
}
