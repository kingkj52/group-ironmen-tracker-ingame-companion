package com.groupironmencompanion.scene;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;
import net.runelite.api.gameval.NpcID;

/**
 * Builds the translucent blue models the scene renderer draws.
 * <p>
 * A member's real appearance cannot be reproduced. {@code PlayerComposition} is read-only with
 * no way to construct one, and nothing in the API turns a set of equipment ids into a model,
 * so every member is drawn as the same plain human figure. That turns out to be the right
 * answer rather than a limitation: an accurate copy of a player who is not on your world is
 * exactly the confusing thing to avoid, and a uniform washed-out figure reads as a marker.
 * <p>
 * Models are cached because building one merges several meshes and relights the result, which
 * is far too much work to repeat every frame for every member.
 */
@Slf4j
@Singleton
public class GhostModels
{
	/**
	 * The base figure. A plain human NPC, chosen because it is an ordinary unarmoured humanoid
	 * that animates with the standard human animation set the inference in
	 * {@link GhostAnimations} produces.
	 */
	private static final int BASE_FIGURE_NPC = NpcID.MAN;

	/**
	 * Jagex pack a colour into a short as {@code hue << 10 | saturation << 7 | luminance},
	 * with six bits of hue, three of saturation and seven of luminance.
	 */
	private static final int HUE_SHIFT = 10;
	private static final int SATURATION_SHIFT = 7;
	private static final int LUMINANCE_MASK = 0x7F;

	/** A cold blue, low saturation, so the figure reads as washed out rather than painted. */
	private static final int GHOST_HUE = 41;
	private static final int GHOST_SATURATION = 2;

	/** Most distinct figures kept built at once. */
	private static final int CACHE_LIMIT = 32;

	@Inject
	private Client client;

	/** Keyed by NPC id, with the base figure under its own id. Client thread only. */
	private final Map<Integer, Model> cache = new HashMap<>();

	/**
	 * The transparency every cached model was baked with. Transparency is part of the built
	 * mesh rather than something applied at draw time, so changing the setting invalidates
	 * everything already built.
	 */
	private int cachedTransparency = -1;

	/** The figure used for group members. Client thread only. */
	@Nullable
	Model member(int transparency)
	{
		return forNpc(BASE_FIGURE_NPC, transparency);
	}

	/**
	 * The ghost of a particular NPC, so the thing a member is fighting appears alongside them.
	 * Client thread only.
	 *
	 * @param npcId        the NPC to copy the shape of
	 * @param transparency 0 for opaque, 255 for invisible
	 */
	@Nullable
	Model forNpc(int npcId, int transparency)
	{
		if (transparency != cachedTransparency)
		{
			cache.clear();
			cachedTransparency = transparency;
		}

		Model cached = cache.get(npcId);
		if (cached != null)
		{
			return cached;
		}

		Model built = build(npcId, transparency);
		if (built != null)
		{
			// Each entry is a built mesh, so this is the expensive cache of the two. A group
			// meets nowhere near this many distinct opponents in a session; the cap is here so
			// that a long session, or a server naming things that resolve oddly, cannot grow
			// it without bound.
			if (cache.size() >= CACHE_LIMIT)
			{
				cache.clear();
			}
			cache.put(npcId, built);
		}
		return built;
	}

	/** Drops every built model, after a settings change or on shutdown. Client thread only. */
	void clear()
	{
		cache.clear();
		cachedTransparency = -1;
	}

	@Nullable
	private Model build(int npcId, int transparency)
	{
		NPCComposition composition = client.getNpcDefinition(npcId);
		if (composition == null)
		{
			return null;
		}

		int[] parts = composition.getModels();
		if (parts == null || parts.length == 0)
		{
			return null;
		}

		ModelData[] meshes = new ModelData[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			ModelData mesh = client.loadModelData(parts[i]);
			if (mesh == null)
			{
				// A part that has not reached the cache yet. Fail the whole build rather than
				// draw a figure with a limb missing; the next tick will try again.
				return null;
			}
			meshes[i] = mesh;
		}

		ModelData merged = client.mergeModels(meshes);
		if (merged == null)
		{
			return null;
		}

		return tint(merged, transparency).light();
	}

	/**
	 * Recolours every face toward blue and makes the whole figure translucent.
	 * <p>
	 * Luminance is preserved per face, so the figure keeps its own shading and still reads as
	 * a three-dimensional shape; only hue and saturation are replaced. Flattening luminance as
	 * well produces a silhouette, which is harder to tell apart from a real player at a
	 * glance, not easier.
	 */
	private ModelData tint(ModelData model, int transparency)
	{
		ModelData tinted = model.cloneColors();
		short[] colours = tinted.getFaceColors();
		if (colours != null)
		{
			for (int i = 0; i < colours.length; i++)
			{
				int luminance = colours[i] & LUMINANCE_MASK;
				colours[i] = (short) ((GHOST_HUE << HUE_SHIFT)
					| (GHOST_SATURATION << SATURATION_SHIFT)
					| luminance);
			}
		}

		// The single-argument clone only copies transparency data the model already has, and
		// an NPC model usually has none. The flag asks for the array to be allocated so there
		// is something to write into.
		ModelData translucent = tinted.cloneTransparencies(true);
		byte[] faces = translucent.getFaceTransparencies();
		if (faces == null)
		{
			// Nothing to write to. The figure is still tinted and still unclickable, it just
			// is not see-through, so it is worth drawing rather than suppressing.
			log.debug("Ghost model has no transparency data; drawing it opaque");
			return translucent;
		}

		byte value = (byte) Math.max(0, Math.min(255, transparency));
		Arrays.fill(faces, value);
		return translucent;
	}
}
