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
 * Builds the translucent blue models.
 * <p>
 * Members are built from the gear they're wearing, using {@link GearTable}. The body under it
 * is a fixed default since nobody publishes their build or skin, and it's all tinted blue
 * anyway. Built models are cached by outfit, so a group in matching gear shares one.
 */
@Slf4j
@Singleton
public class GhostModels
{
	// Used only if the gear table can't be read.
	private static final int BASE_FIGURE_NPC = NpcID.MAN;

	// Jagex colours are packed as hue << 10 | saturation << 7 | luminance.
	private static final int HUE_SHIFT = 10;
	private static final int SATURATION_SHIFT = 7;
	private static final int LUMINANCE_MASK = 0x7F;

	// A pale, washed-out blue.
	private static final int GHOST_HUE = 41;
	private static final int GHOST_SATURATION = 2;

	private static final int CACHE_LIMIT = 32;

	@Inject
	private Client client;

	// Client thread only.
	private final Map<Integer, Model> cache = new HashMap<>();
	private final Map<String, Model> outfits = new HashMap<>();

	private GearTable gear;
	private boolean gearLoadAttempted;

	// Transparency is baked into the mesh, so changing it means rebuilding.
	private int cachedTransparency = -1;

	/**
	 * A member wearing these items. Client thread.
	 *
	 * @param transparency 0 opaque to 255 invisible
	 */
	@Nullable
	Model member(int[] equippedItemIds, int transparency)
	{
		GearTable table = gear();
		if (table == null)
		{
			return forNpc(BASE_FIGURE_NPC, transparency);
		}
		if (transparency != cachedTransparency)
		{
			dropCaches(transparency);
		}

		int[] slots = GhostAppearance.resolve(equippedItemIds, table);
		String key = Arrays.toString(slots);
		Model cached = outfits.get(key);
		if (cached != null)
		{
			return cached;
		}

		Model built = assemble(slots, transparency);
		if (built != null)
		{
			if (outfits.size() >= CACHE_LIMIT)
			{
				outfits.clear();
			}
			outfits.put(key, built);
		}
		return built;
	}

	/** The NPC a member is fighting. Client thread. */
	@Nullable
	Model forNpc(int npcId, int transparency)
	{
		if (transparency != cachedTransparency)
		{
			dropCaches(transparency);
		}

		Model cached = cache.get(npcId);
		if (cached != null)
		{
			return cached;
		}

		Model built = build(npcId, transparency);
		if (built != null)
		{
			if (cache.size() >= CACHE_LIMIT)
			{
				cache.clear();
			}
			cache.put(npcId, built);
		}
		return built;
	}

	void clear()
	{
		cache.clear();
		outfits.clear();
		cachedTransparency = -1;
	}

	// If any mesh isn't loaded yet, give up and try next tick rather than draw a missing limb.
	@Nullable
	private Model assemble(int[] slots, int transparency)
	{
		ModelData[] parts = new ModelData[slots.length];
		int count = 0;
		for (int model : slots)
		{
			if (model <= 0)
			{
				continue;
			}
			ModelData mesh = client.loadModelData(model);
			if (mesh == null)
			{
				return null;
			}
			parts[count++] = mesh;
		}
		if (count == 0)
		{
			return null;
		}

		ModelData merged = client.mergeModels(Arrays.copyOf(parts, count));
		return merged == null ? null : tint(merged, transparency).light();
	}

	@Nullable
	private Model build(int npcId, int transparency)
	{
		NPCComposition composition = client.getNpcDefinition(npcId);
		int[] parts = composition == null ? null : composition.getModels();
		if (parts == null || parts.length == 0)
		{
			return null;
		}

		ModelData[] meshes = new ModelData[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			meshes[i] = client.loadModelData(parts[i]);
			if (meshes[i] == null)
			{
				return null;
			}
		}

		ModelData merged = client.mergeModels(meshes);
		return merged == null ? null : tint(merged, transparency).light();
	}

	@Nullable
	private GearTable gear()
	{
		if (!gearLoadAttempted)
		{
			gearLoadAttempted = true;
			gear = GearTable.load();
		}
		return gear;
	}

	private void dropCaches(int transparency)
	{
		cache.clear();
		outfits.clear();
		cachedTransparency = transparency;
	}

	// Recolours every face blue, keeping its brightness so the shape still has depth, and makes
	// it see-through.
	private ModelData tint(ModelData model, int transparency)
	{
		ModelData tinted = model.cloneColors();
		short[] colours = tinted.getFaceColors();
		if (colours != null)
		{
			for (int i = 0; i < colours.length; i++)
			{
				int luminance = colours[i] & LUMINANCE_MASK;
				colours[i] = (short) ((GHOST_HUE << HUE_SHIFT) | (GHOST_SATURATION << SATURATION_SHIFT) | luminance);
			}
		}

		// true allocates the array if the model had no transparency data, which most don't.
		ModelData translucent = tinted.cloneTransparencies(true);
		byte[] faces = translucent.getFaceTransparencies();
		if (faces == null)
		{
			log.debug("Ghost model has no transparency data; drawing it opaque");
			return translucent;
		}
		Arrays.fill(faces, (byte) Math.max(0, Math.min(255, transparency)));
		return translucent;
	}
}
