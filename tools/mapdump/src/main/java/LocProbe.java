import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.MapDefinition;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.fs.Store;
import java.io.File;

/** Probe: can object placements (the locations archive) be read without XTEA keys? */
public class LocProbe
{
	public static void main(String[] args) throws Exception
	{
		try (Store store = new Store(new File(args[0])))
		{
			store.load();
			RegionLoader loader = new RegionLoader(store, region -> null);

			int[] regions = {14483, 14484, 12850, 12853, 13622};
			String[] labels = {"GOTR (56,147)", "GOTR north (56,148)", "Lumbridge", "Varrock", "Wizards Tower area"};

			for (int i = 0; i < regions.length; i++)
			{
				int id = regions[i];
				String terrain = "?";
				String locs = "?";
				try
				{
					MapDefinition m = loader.loadMapDef(id);
					terrain = m == null ? "null" : "OK";
				}
				catch (Exception e)
				{
					terrain = "FAIL " + e.getClass().getSimpleName();
				}
				try
				{
					LocationsDefinition l = loader.loadLocDef(id);
					locs = l == null ? "null"
						: "OK (" + (l.getLocations() == null ? -1 : l.getLocations().size()) + " objects)";
				}
				catch (Exception e)
				{
					locs = "FAIL " + e.getClass().getSimpleName();
				}
				System.out.printf("  %-22s region %-6d terrain=%-18s locations=%s%n",
					labels[i], id, terrain, locs);
			}
		}
	}
}
