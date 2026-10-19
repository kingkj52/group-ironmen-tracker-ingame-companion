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
 * One translucent figure in the scene, and the motion state that drives it.
 * <p>
 * Backed by a {@link RuneLiteObject}, which is RuneLite's own facility for a client-side
 * object: it exists in the local scene graph for drawing purposes only, has no entry in the
 * player or NPC lists, and the server is never told about it. Nothing can be clicked, and
 * there is no menu entry to click, so a ghost cannot be interacted with even by accident.
 * <p>
 * Positions arrive about once a second while the client draws fifty times that, so the figure
 * is moved toward its latest reported position a little each frame rather than snapped to it.
 * The side effect is that a ghost lags its owner by up to a poll, and that it travels in a
 * straight line because only the endpoints are known, not the path between them. Both are
 * acceptable for two people standing in the same field; neither would be for anything where
 * exact position mattered, which is one more reason the gate keeps this out of the Wilderness.
 */
class Ghost
{
	/** Local units per tile, the scene's own coordinate scale. */
	private static final int UNITS_PER_TILE = 128;

	/**
	 * Fraction of the remaining distance covered each frame. Chosen by feel rather than
	 * derived: high enough that the figure keeps up with a running player, low enough that a
	 * new report does not look like a jump.
	 */
	private static final double EASE = 0.25;

	/** Beyond this, the figure is teleported rather than walked. */
	private static final int SNAP_DISTANCE_TILES = 12;

	/** Roughly head height on a human model, so the label sits above the figure. */
	private static final int LABEL_HEIGHT = 200;

	private final Client client;
	private final WorldView view;
	private final RuneLiteObject object;
	private final String memberName;

	/** Where the figure is drawn, in local units, as a double so easing does not stall. */
	private double x;
	private double y;
	private int plane;

	/** Where it is heading, in local units. */
	private int targetX;
	private int targetY;

	/**
	 * The animations currently installed.
	 * <p>
	 * Tracked so that an unchanged animation is left running. Reinstalling one every tick
	 * would reset it to its first frame each time, which shows as a figure twitching in place
	 * instead of a figure swinging a sword.
	 */
	private int poseId = -1;
	private int actionId = -1;

	private String label = "";

	Ghost(Client client, WorldView view, String memberName, LocalPoint start, int plane)
	{
		this.client = client;
		this.view = view;
		this.memberName = memberName;
		this.x = start.getX();
		this.y = start.getY();
		this.targetX = start.getX();
		this.targetY = start.getY();
		this.plane = plane;

		this.object = client.createRuneLiteObject();
		apply();
	}

	String getMemberName()
	{
		return memberName;
	}

	String getLabel()
	{
		return label;
	}

	void setLabel(String label)
	{
		this.label = label == null ? "" : label;
	}

	void setModel(@Nullable Model model)
	{
		object.setModel(model);
		object.setActive(model != null);
	}

	/** Points the figure at its newest reported position. */
	void moveTo(LocalPoint point, int plane)
	{
		this.targetX = point.getX();
		this.targetY = point.getY();

		boolean changedPlane = plane != this.plane;
		this.plane = plane;

		double tiles = Math.hypot(targetX - x, targetY - y) / UNITS_PER_TILE;
		if (changedPlane || tiles > SNAP_DISTANCE_TILES)
		{
			// A long jump is a world hop, a teleport, or the scene reloading around us. Easing
			// across it would draw the figure sliding through everything in between.
			this.x = targetX;
			this.y = targetY;
		}
	}

	/**
	 * Eases the figure toward its target and republishes its position. Called every client
	 * tick, on the client thread.
	 */
	void advance()
	{
		double dx = targetX - x;
		double dy = targetY - y;

		// Below a single unit the easing would never converge, so close the gap outright.
		if (Math.abs(dx) < 1 && Math.abs(dy) < 1)
		{
			x = targetX;
			y = targetY;
		}
		else
		{
			x += dx * EASE;
			y += dy * EASE;
			face(dx, dy);
		}

		apply();
	}

	/** Turns the figure toward a point, used to face whatever it is fighting. */
	void faceTowards(LocalPoint point)
	{
		face(point.getX() - x, point.getY() - y);
	}

	/**
	 * Installs the pose the figure stands or moves in, and the action it is performing.
	 * <p>
	 * {@link RuneLiteObject} keeps the two separate, which maps exactly onto the inference:
	 * locomotion is a pose, swinging or gathering is an action, and a member can be doing one
	 * while in the other.
	 *
	 * @param poseId   locomotion or idle animation
	 * @param actionId the action to play, or -1 for none
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
			object.setAnimationController(actionId < 0 ? null : looping(actionId));
		}
	}

	/**
	 * Where this figure is on screen, for the hover label, or null when it is off screen or
	 * behind the camera. Client thread only.
	 */
	@Nullable
	Point canvasLocation()
	{
		return Perspective.localToCanvas(client, localPoint(), plane, LABEL_HEIGHT);
	}

	/** Removes the figure from the scene. */
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

	private LocalPoint localPoint()
	{
		return new LocalPoint((int) x, (int) y, view);
	}

	private void apply()
	{
		object.setLocation(localPoint(), plane);
	}

	/**
	 * Sets the figure's facing from a direction vector.
	 * <p>
	 * Jagex angles run from 0 to 2047 counter-clockwise starting at south, so south is 0,
	 * west 512, north 1024 and east 1536.
	 */
	private void face(double dx, double dy)
	{
		if (Math.abs(dx) < 1 && Math.abs(dy) < 1)
		{
			return;
		}
		int orientation = (int) (Math.atan2(-dx, -dy) * (1024.0 / Math.PI));
		object.setOrientation(orientation & 0x7FF);
	}
}
