import net.runelite.cache.definitions.MapDefinition;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.fs.Store;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Prints which map regions have terrain data in a given square range.
 * <p>
 * For reading a detached area's real extent off the cache instead of inferring it from one
 * observed position. Guardians of the Rift was first handled from a single reported coordinate
 * and the box was a square too small, so members standing elsewhere in the arena were still
 * misplaced. This is how to size the next one properly: find the square the known point falls
 * in, then see how far the contiguous terrain actually runs.
 * <p>
 * No XTEA keys are needed. Neither terrain nor, as locProbe shows, the object placements are
 * encrypted in this cache, so the null key provider below is not a limitation.
 */
public class RegionProbe
{
	public static void main(String[] args) throws Exception
	{
		File cacheDir = new File(args[0]);
		int x0 = Integer.parseInt(args[1]);
		int x1 = Integer.parseInt(args[2]);
		int y0 = Integer.parseInt(args[3]);
		int y1 = Integer.parseInt(args[4]);

		try (Store store = new Store(cacheDir))
		{
			store.load();
			RegionLoader loader = new RegionLoader(store, region -> null);

			Set<Integer> present = new HashSet<>();
			for (int x = x0; x <= x1; x++)
			{
				for (int y = y0; y <= y1; y++)
				{
					int regionId = (x << 8) | y;
					try
					{
						MapDefinition def = loader.loadMapDef(regionId);
						if (def != null)
						{
							present.add(regionId);
						}
					}
					catch (Exception ignored)
					{
						// No terrain for this region.
					}
				}
			}

			System.out.print("      ");
			for (int x = x0; x <= x1; x++)
			{
				System.out.printf("%4d", x);
			}
			System.out.println("   <- region X");

			for (int y = y1; y >= y0; y--)
			{
				System.out.printf("y%4d ", y);
				for (int x = x0; x <= x1; x++)
				{
					System.out.print(present.contains((x << 8) | y) ? "   #" : "   .");
				}
				System.out.println();
			}
			System.out.println();
			System.out.println("# = terrain exists, . = nothing. " + present.size() + " regions found.");
		}
	}
}
