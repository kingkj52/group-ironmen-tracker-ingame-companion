package com.groupironmencompanion.scene;

import javax.annotation.Nullable;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * One translucent figure in the scene.
 * <p>
 * Backed by a {@link RuneLiteObject}: drawn by the client, but not in the player or NPC lists,
 * not clickable, and unknown to the server.
 * <p>
 * Positions arrive about once a second, so the figure eases toward each new one instead of
 * snapping. It lags its owner a little and walks in straight lines, since we only get
 * endpoints, not the path.
 */
class Ghost
{
	private static final int UNITS_PER_TILE = 128;

	// Fraction of the remaining distance covered each frame. Picked by eye: keeps up with a
	// running player without making a new position look like a jump.
	private static final double EASE = 0.25;

	// Further than this and it's a hop or teleport, so jump instead of walking through walls.
	private static final int SNAP_DISTANCE_TILES = 12;

	private static final double ARRIVED_UNITS = 1.0;

	// Roughly head height, so the hover point sits on the figure rather than its feet.
	private static final int LABEL_HEIGHT = 200;

	// Names come from the group's server, so they get clipped before they size a label.
	private static final int MAX_TEXT = 32;

	private final Client client;
	private final RuneLiteObject object;

	// Where it's drawn and where it's heading, in local units.
	private double x;
	private double y;
	private int plane;
	private int targetX;
	private int targetY;
	private boolean dirty = true;

	// Remembered so an unchanged animation isn't restarted every tick, which looks like twitching.
	private int poseId = -1;
	private int actionId = -1;
	private int ticksUntilAction;
	private Model model;

	private String title = "";
	private String detail;

	Ghost(Client client, LocalPoint start, int plane)
	{
		this.client = client;
		this.x = start.getX();
		this.y = start.getY();
		this.targetX = start.getX();
		this.targetY = start.getY();
		this.plane = plane;

		this.object = client.createRuneLiteObject();
		apply();
	}

	String getTitle()
	{
		return title;
	}

	/** A second line for the hover label, or null. */
	@Nullable
	String getDetail()
	{
		return detail;
	}

	void setLabel(String title, @Nullable String detail)
	{
		this.title = clip(title);
		this.detail = detail == null || detail.isEmpty() ? null : clip(detail);
	}

	void setModel(@Nullable Model model)
	{
		if (model == this.model)
		{
			return;
		}
		this.model = model;
		object.setModel(model);
		object.setActive(model != null);
	}

	boolean isMoving()
	{
		return Math.abs(targetX - x) >= ARRIVED_UNITS || Math.abs(targetY - y) >= ARRIVED_UNITS;
	}

	void moveTo(LocalPoint point, int plane)
	{
		targetX = point.getX();
		targetY = point.getY();

		boolean changedPlane = plane != this.plane;
		this.plane = plane;

		double tiles = Math.hypot(targetX - x, targetY - y) / UNITS_PER_TILE;
		if (changedPlane || tiles > SNAP_DISTANCE_TILES)
		{
			x = targetX;
			y = targetY;
			dirty = true;
		}
	}

	/** Eases toward the target. Runs every frame, so a figure that's arrived does nothing. */
	void advance()
	{
		if (!isMoving())
		{
			if (dirty)
			{
				x = targetX;
				y = targetY;
				apply();
			}
			return;
		}

		double dx = targetX - x;
		double dy = targetY - y;
		x += dx * EASE;
		y += dy * EASE;
		face(dx, dy);
		apply();
	}

	void faceTowards(LocalPoint point)
	{
		face(point.getX() - x, point.getY() - y);
	}

	/**
	 * Sets the pose (standing, walking, running), which loops, and an action (a swing, a chop)
	 * that {@link #onGameTick} plays every few ticks. Pass -1 for no action.
	 */
	void setAnimation(int poseId, int actionId)
	{
		if (poseId != this.poseId)
		{
			this.poseId = poseId;
			object.setPoseAnimationController(looping(poseId));
		}
		if (actionId != this.actionId)
		{
			this.actionId = actionId;
			object.setAnimationController(null);
			// Start straight away rather than waiting out the last action's interval.
			ticksUntilAction = 0;
		}
	}

	/** Plays the action every {@link GhostAnimations#ACTION_INTERVAL_TICKS}, once each time. */
	void onGameTick()
	{
		if (actionId < 0 || --ticksUntilAction > 0)
		{
			return;
		}
		ticksUntilAction = GhostAnimations.ACTION_INTERVAL_TICKS;

		// Not looped: it clears itself when done so the pose shows between swings.
		AnimationController controller = new AnimationController(client, actionId);
		controller.setOnFinished(finished -> object.setAnimationController(null));
		object.setAnimationController(controller);
	}

	/** Where the figure is on screen, or null if it isn't. */
	@Nullable
	Point canvasLocation()
	{
		LocalPoint point = localPoint();
		return point == null ? null : Perspective.localToCanvas(client, point, plane, LABEL_HEIGHT);
	}

	void destroy()
	{
		object.setActive(false);
	}

	private AnimationController looping(int animationId)
	{
		AnimationController controller = new AnimationController(client, animationId);
		controller.loop();
		return controller;
	}

	// The world view is read each time rather than kept, since a figure can outlive a scene reload.
	@Nullable
	private LocalPoint localPoint()
	{
		WorldView view = client.getTopLevelWorldView();
		return view == null ? null : new LocalPoint((int) x, (int) y, view);
	}

	private void apply()
	{
		LocalPoint point = localPoint();
		if (point == null)
		{
			return;
		}
		object.setLocation(point, plane);
		dirty = false;
	}

	// Jagex angles go 0-2047 anticlockwise from south: 0 south, 512 west, 1024 north, 1536 east.
	private void face(double dx, double dy)
	{
		if (Math.abs(dx) < 1 && Math.abs(dy) < 1)
		{
			return;
		}
		int orientation = (int) (Math.atan2(-dx, -dy) * (1024.0 / Math.PI));
		object.setOrientation(orientation & 0x7FF);
	}

	private static String clip(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT) + "…";
	}
}
