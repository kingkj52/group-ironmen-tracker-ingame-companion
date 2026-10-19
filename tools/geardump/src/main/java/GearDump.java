import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.ItemManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.KitDefinition;
import net.runelite.cache.definitions.loaders.KitLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

/**
 * Dumps what the game needs to draw a player wearing a given set of items.
 * <p>
 * The plugin knows which items a group member has equipped, but a running client will not say
 * what those items look like worn: {@code ItemComposition} exposes only the flat inventory
 * icon, and a {@code PlayerComposition} cannot be built for somebody who is not on your world.
 * Both facts are in the cache, which is what this reads.
 * <p>
 * Two tables come out, and the composition rules deliberately do not: this is extraction only,
 * so the reasoning about which mesh covers which body part lives in the plugin, where it can
 * be unit tested.
 * <p>
 * <b>Items.</b> Up to three worn meshes per item. The first is the item in its own equipment
 * slot. The second, when present, is the piece that replaces a body part underneath: a rune
 * platebody is {@code 306, 164}, the torso and its sleeves, while a rune chainbody is
 * {@code 301, -1} and so leaves the wearer's own arms showing. That distinction is the whole
 * of the occlusion problem, and it comes free in the data.
 * <p>
 * Note that {@code wearPos1..3} look like they should answer this and do not: the fields exist
 * on {@code ItemDefinition} but no OSRS item populates them, so they are ignored here.
 * <p>
 * <b>Body kits.</b> One identity kit per male body part, which shows wherever no item covers
 * it. Kits are grouped by {@code bodyPartId}, which runs 0-6 for male parts and 7-13 for
 * female; the plugin maps those onto appearance slots. A member's own kit - their hair, build
 * and skin - is published nowhere, so an arbitrary default body is unavoidable. Drawn as a
 * translucent silhouette that is no loss, and a group that all share one build is arguably
 * what you want anyway.
 * <p>
 * Colours are deliberately not dumped: everything is recoloured to a single ghost blue at
 * runtime, so an item's own palette would be overwritten the moment it was read.
 */
public class GearDump
{
	/**
	 * Format version, written first so a plugin reading a table it does not understand can say
	 * so rather than misparse it.
	 */
	private static final int FORMAT_VERSION = 1;

	/** Male identity kit body parts: hair, jaw, torso, arms, hands, legs, feet. */
	private static final int MALE_BODY_PARTS = 7;

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			System.err.println("usage: geardump <cache dir> <out file>");
			System.exit(2);
		}

		File cacheDir = new File(args[0]);
		File out = new File(args[1]);

		try (Store store = new Store(cacheDir))
		{
			store.load();

			Map<Integer, int[]> items = loadWornItems(store);
			int[] body = loadDefaultBody(store);

			write(out, items, body);

			System.out.println("worn items: " + items.size());
			System.out.print("default male body kit models:");
			for (int part = 0; part < MALE_BODY_PARTS; part++)
			{
				System.out.print(" " + part + "=" + body[part]);
			}
			System.out.println();
			System.out.println("wrote " + out + " (" + out.length() + " bytes)");
		}
	}

	/**
	 * Every item that has a worn model, as {@code {model0, model1, model2, offset}}.
	 * <p>
	 * Items live one archive per 256 ids rather than as files of a single archive, so the
	 * library's own manager does the walking.
	 */
	private static Map<Integer, int[]> loadWornItems(Store store) throws IOException
	{
		ItemManager manager = new ItemManager(store);
		manager.load();

		Map<Integer, int[]> items = new TreeMap<>();
		int templates = 0;

		for (ItemDefinition item : manager.getItems())
		{
			if (item == null)
			{
				continue;
			}

			// An item with no primary worn mesh is not something you can see on a player:
			// runes, food, coins and the like.
			if (item.maleModel0 <= 0)
			{
				continue;
			}

			// Noted and placeholder copies inherit the real item's models and would double the
			// table while never being worn by anyone.
			if (item.notedTemplate != -1 || item.placeholderTemplateId != -1)
			{
				templates++;
				continue;
			}

			items.put(item.getId(), new int[]{
				item.maleModel0, item.maleModel1, item.maleModel2, item.maleOffset
			});
		}

		System.out.println("skipped " + templates + " noted or placeholder copies");
		return items;
	}

	/**
	 * One model per male body part, taken as the first kit found for each.
	 * <p>
	 * Which one hardly matters: it is the shape under the armour, drawn in flat blue.
	 */
	private static int[] loadDefaultBody(Store store) throws IOException
	{
		Index configs = store.getIndex(IndexType.CONFIGS);
		Storage storage = store.getStorage();

		Archive archive = configs.getArchive(ConfigType.IDENTKIT.getId());
		ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));

		KitLoader loader = new KitLoader();
		Map<Integer, Integer> firstPerPart = new LinkedHashMap<>();

		for (FSFile file : files.getFiles())
		{
			KitDefinition kit = loader.load(file.getFileId(), file.getContents());
			if (kit == null || kit.models == null || kit.models.length == 0)
			{
				continue;
			}
			if (kit.bodyPartId < 0 || kit.bodyPartId >= MALE_BODY_PARTS)
			{
				// Female parts are 7-13 and go unused: the figure is one silhouette for the
				// whole group, because nothing tells us a member's build.
				continue;
			}
			firstPerPart.putIfAbsent(kit.bodyPartId, kit.models[0]);
		}

		int[] body = new int[MALE_BODY_PARTS];
		for (int part = 0; part < MALE_BODY_PARTS; part++)
		{
			body[part] = firstPerPart.getOrDefault(part, -1);
		}
		return body;
	}

	/**
	 * Writes the table.
	 * <pre>
	 * int    format version
	 * int    body part count
	 * int[]  default model per male body part, -1 for none
	 * int    item count
	 * per item, ascending by id:
	 *   int    item id
	 *   int    worn model 1, 2, 3   (-1 for none)
	 *   short  vertical offset
	 * </pre>
	 */
	private static void write(File out, Map<Integer, int[]> items, int[] body) throws IOException
	{
		File parent = out.getParentFile();
		if (parent != null)
		{
			parent.mkdirs();
		}

		try (DataOutputStream stream = new DataOutputStream(new FileOutputStream(out)))
		{
			stream.writeInt(FORMAT_VERSION);
			stream.writeInt(MALE_BODY_PARTS);
			for (int model : body)
			{
				stream.writeInt(model);
			}

			stream.writeInt(items.size());
			for (Map.Entry<Integer, int[]> entry : items.entrySet())
			{
				int[] worn = entry.getValue();
				stream.writeInt(entry.getKey());
				stream.writeInt(worn[0]);
				stream.writeInt(worn[1]);
				stream.writeInt(worn[2]);
				stream.writeShort(worn[3]);
			}
		}
	}
}
