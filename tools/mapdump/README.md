# mapdump

Regenerates `src/main/resources/com/groupironmencompanion/map-areas.json`, the data behind the
world map's dungeon-entrance markers.

Nothing in that file is hand-measured. It comes from the game's own world map definitions
and the map's intermap links — the jumps the world map performs when you click through from
the surface into a dungeon view and back.

## Regenerating after a game update

```bash
./gradlew run --args="$HOME/jagexcache/oldschool/LIVE ../../areas.json"
```

Then, from this directory:

```bash
python build_resource.py && python verify.py
```

`build_resource.py` reads `areas.json` plus `DungeonLocation.java` and writes
`map-areas.json`; copy that over the resource in `src/main/resources/com/groupironmencompanion/`
and run the plugin's own `MapAreasTest`, which asserts a spread of known dungeons still
resolve to the right entrances.

`verify.py` prints the resolved entrance for a list of known dungeon interiors, which is the
quickest way to eyeball whether a game update moved something.

## How the data is derived

1. `AreaDump.java` reads the `WORLDMAP` cache index. The `details` archive names each of the
   ~40 world maps; the `compositemap` archive says which real world squares each map draws;
   `WorldMapManager` parses intermap links out of client script 1705.
2. `build_resource.py` classifies every square as surface or not, assigns each link to the
   map containing it, and walks the map graph so a dungeon that only connects to another
   dungeon still resolves to a surface entrance. Mor Ul Rek, for instance, links into Karamja
   Underground, whose own exit is the volcano.
3. Entrance names are borrowed by proximity from RuneLite's `DungeonLocation` enum
   (BSD-2-Clause, `runelite-client`), which is the only hand-curated input and affects
   labels only, never positions.

## Dependencies

This tool depends on `net.runelite:cache`. It is a build-time utility and is **not** part of
the plugin build — the plugin itself has no dependencies beyond `runelite-client`, and the
generated JSON is checked in so the plugin builds without ever running this.
