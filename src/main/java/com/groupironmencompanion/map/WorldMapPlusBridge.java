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
 * <h2>The reply path, as actually shipped</h2>
 *
 * Settled with the World Map Plus author and then read back off their source rather than taken
 * from the agreement, which is just as well: the map kinds were planned as surface,
 * underground, instance, minigame and other, and shipped as surface, submap, extra and area.
 * Nothing here depends on them, but a consumer that had switched on the planned words would
 * have matched none of them. The shipped words are the settled ones, and are the better set:
 * they say where a map came from, which that plugin knows, rather than what kind of place it
 * depicts, which it would be guessing at.
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
 * Map kinds are strings - surface, submap, extra, area - deliberately not ids, so that
 * plugin's internal numbering does not become permanent API. We do not read them; coverage
 * alone decides what we do.
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
	private static final String MESSAGE_COVERS = "covers";
	private static final String REPLY_COVERS = "covers_reply";

	/** Pixels per tile to look at a member from: close enough to see where they are standing. */
	static final double MEMBER_ZOOM = 6.0;

	/** The most tiles one question may carry, matching World Map Plus's own cap. */
	private static final int MAX_POINTS = 16;

	/**
	 * How long a cached answer is trusted. Coverage changes only when that plugin is updated,
	 * so this is short for the sake of a stale answer after a map is added, not because the
	 * truth moves.
	 */
	private static final long ANSWER_TTL_MILLIS = 60_000;

	/**
	 * Least time between questions. One a second settles a group's worth of positions within a
	 * few ticks of the map opening, which is long before anyone clicks.
	 * <p>
	 * Their limit of eight a second is <b>shared across every plugin asking</b>, not ours to
	 * spend, so this stays far below it rather than close to it. Over the limit a question is
	 * dropped and never answered, which reaches us as silence and so as "not covered" - safe,
	 * since the caller then places the member itself, and self-correcting, since a tile with no
	 * answer is asked about again. Worth knowing before anyone raises this: the headroom is not
	 * ours alone.
	 */
	private static final long ASK_INTERVAL_MILLIS = 1_000;

	/** How long to wait for an answer before assuming the plugin is not there. */
	private static final long REPLY_TIMEOUT_MILLIS = 2_000;

	private final EventBus eventBus;

	/**
	 * What is known about each tile asked about, keyed by the tile.
	 * <p>
	 * Keyed by tile rather than by map square because a square can straddle the edge of a map,
	 * and a wrong answer at an edge is the kind that only shows up for one member standing in
	 * one spot.
	 */
	private final Map<WorldPoint, Coverage> known = new HashMap<>();

	/** Questions waiting for an answer, by the id sent. */
	private final Map<String, Pending> pending = new HashMap<>();

	private long lastAskMillis;
	private int nextId;

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
		// Without this, a point no map has imagery of sends their view to whichever map is
		// nearest within 192 tiles, which for a detached area is black space off the edge of
		// something unrelated. Asking for exact means they decline and leave the view alone.
		data.put("exact", Boolean.TRUE);
		eventBus.post(new PluginMessage(NAMESPACE, MESSAGE_FOCUS, data));
	}

	/**
	 * Whether World Map Plus has imagery of this exact tile, as far as we have been told.
	 * <p>
	 * False whenever the answer is not in hand, which covers the plugin being absent, the
	 * question not having been asked yet, and the answer having expired. Callers therefore
	 * treat this as "known to be covered" rather than "not known to be uncovered", and fall
	 * back to their own placement on false. That makes the uninstalled case the default rather
	 * than a special case.
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
	 * Asks about any of these tiles not already answered for. Call from the game tick.
	 * <p>
	 * Rate-limited, and silent when there is nothing new to ask, so this is cheap to call
	 * whenever the map is open. The answers arrive a tick or more later, which is why it is
	 * worth asking before anybody clicks rather than at the moment they do.
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

		// Their id rules are strict: short, and letters, digits or _ - . : only.
		String id = "gim-" + (nextId++);
		pending.put(id, new Pending(asking, now));

		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		data.put("points", asking);
		eventBus.post(new PluginMessage(NAMESPACE, MESSAGE_COVERS, data));
	}

	/**
	 * Takes an answer. Called for every plugin message, so it must ignore everything that is
	 * not one of ours.
	 */
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

		Pending asked = pending.remove(asText(data.get("id")));
		if (asked == null)
		{
			// An answer to a question we have already given up on, or one we never asked.
			// Their queue outlives our timeout, so this is expected rather than odd.
			return;
		}

		List<?> covered = asList(data.get("covered"));
		List<?> exact = asList(data.get("exact"));
		List<?> names = asList(data.get("mapNames"));
		if (covered == null || exact == null)
		{
			return;
		}

		long now = System.currentTimeMillis();
		for (int i = 0; i < asked.points.size(); i++)
		{
			// The lists run parallel to the points we sent, but they come from another plugin,
			// so every index is checked rather than assumed.
			boolean isCovered = i < covered.size() && Boolean.TRUE.equals(covered.get(i));
			boolean isExact = i < exact.size() && Boolean.TRUE.equals(exact.get(i));
			String name = names != null && i < names.size() && names.get(i) instanceof String
				? (String) names.get(i)
				: "";
			known.put(asked.points.get(i), new Coverage(isCovered, isExact, name, now));
		}
	}

	/** Forgets stale answers and questions that were never answered. */
	private void expire()
	{
		long now = System.currentTimeMillis();
		known.values().removeIf(Coverage::isStale);
		pending.values().removeIf(waiting -> now - waiting.at > REPLY_TIMEOUT_MILLIS);
	}

	/** Drops everything learned, on shutdown or when the group changes. */
	public void reset()
	{
		known.clear();
		pending.clear();
		lastAskMillis = 0;
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

	/** What World Map Plus said about one tile, and when. */
	private static final class Coverage
	{
		private final boolean covered;
		private final boolean exact;
		private final String mapName;
		private final long at;

		Coverage(boolean covered, boolean exact, String mapName, long at)
		{
			this.covered = covered;
			this.exact = exact;
			this.mapName = mapName;
			this.at = at;
		}

		boolean isStale()
		{
			return System.currentTimeMillis() - at > ANSWER_TTL_MILLIS;
		}
	}

	/** A question sent, kept so its answer can be matched back to the tiles it was about. */
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
