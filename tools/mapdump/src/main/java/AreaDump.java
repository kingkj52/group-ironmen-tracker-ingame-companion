import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.cache.IndexType;
import net.runelite.cache.WorldMapManager;
import net.runelite.cache.definitions.MapSquareDefinition;
import net.runelite.cache.definitions.WorldMapCompositeDefinition;
import net.runelite.cache.definitions.WorldMapDefinition;
import net.runelite.cache.definitions.ZoneDefinition;
import net.runelite.cache.definitions.loaders.WorldMapCompositeLoader;
import net.runelite.cache.definitions.loaders.WorldMapLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Position;

/**
 * Dumps, for every world map the game defines, the real world squares it covers and the
 * intermap links the map view offers. Squares come from the composite map data, where each
 * entry says which source square of the world is shown at which display square.
 */
public class AreaDump
{
	public static class Area
	{
		int fileId;
		String name;
		boolean isSurface;
		/** Real world squares (x*64,y*64 tile blocks) this map draws, as "sx,sz" strings. */
		List<int[]> squares = new ArrayList<>();
	}

	public static class Link
	{
		int fromX;
		int fromY;
		int fromZ;
		int toX;
		int toY;
		int toZ;
	}

	public static class Dump
	{
		List<Area> areas = new ArrayList<>();
		List<Link> links = new ArrayList<>();
	}

	public static void main(String[] args) throws Exception
	{
		File cacheDir = new File(args[0]);
		File out = new File(args[1]);

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Dump dump = new Dump();

		try (Store store = new Store(cacheDir))
		{
			store.load();
			Storage storage = store.getStorage();
			Index index = store.getIndex(IndexType.WORLDMAP);

			// Map names, keyed by file id.
			Map<Integer, WorldMapDefinition> details = new LinkedHashMap<>();
			Archive detailsArchive = index.isNamed()
				? index.findArchiveByName("details")
				: index.getArchive(WorldMapManager.DETAILS_ID);
			ArchiveFiles detailFiles = detailsArchive.getFiles(storage.loadArchive(detailsArchive));
			for (FSFile file : detailFiles.getFiles())
			{
				WorldMapDefinition def = new WorldMapLoader().load(file.getContents(), file.getFileId());
				details.put(file.getFileId(), def);
			}

			// Composite data carries the actual square coverage, one file per map.
			Archive compositeArchive = index.isNamed()
				? index.findArchiveByName("compositemap")
				: index.getArchive(WorldMapManager.COMPOSITEMAP_ID);
			WorldMapCompositeLoader loader = new WorldMapCompositeLoader();
			loader.configureForRevision(index.getRevision());

			ArchiveFiles compositeFiles = compositeArchive.getFiles(storage.loadArchive(compositeArchive));
			for (FSFile file : compositeFiles.getFiles())
			{
				WorldMapCompositeDefinition composite = loader.load(file.getContents());

				Area area = new Area();
				area.fileId = file.getFileId();
				WorldMapDefinition def = details.get(file.getFileId());
				area.name = def == null ? ("map " + file.getFileId()) : def.name;
				area.isSurface = def != null && def.isSurface;

				for (MapSquareDefinition square : composite.getMapSquareDefinitions())
				{
					area.squares.add(new int[]{
						square.sourceSquareX, square.sourceSquareZ, square.minLevel, square.levels
					});
				}
				// Zones are 8x8 sub-blocks; record the square they belong to, which is
				// enough resolution for "which map is this point on".
				for (ZoneDefinition zone : composite.getZoneDefinitions())
				{
					area.squares.add(new int[]{
						zone.sourceSquareX, zone.sourceSquareZ, zone.minLevel, zone.levels
					});
				}

				dump.areas.add(area);
			}

			WorldMapManager manager = new WorldMapManager(store);
			manager.load();
			for (Map.Entry<Position, Position> entry : manager.getIntermapLinks().entrySet())
			{
				Link link = new Link();
				link.fromX = entry.getKey().getX();
				link.fromY = entry.getKey().getY();
				link.fromZ = entry.getKey().getZ();
				link.toX = entry.getValue().getX();
				link.toY = entry.getValue().getY();
				link.toZ = entry.getValue().getZ();
				dump.links.add(link);
			}
		}

		try (FileWriter writer = new FileWriter(out))
		{
			gson.toJson(dump, writer);
		}
		System.out.println("Dumped " + dump.areas.size() + " areas and " + dump.links.size() + " links to " + out);
	}
}
