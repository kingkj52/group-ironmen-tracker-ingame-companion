# geardump

Regenerates `src/main/resources/com/groupironmencompanion/gear.dat`, the worn-model data behind
the scene presence feature's figures.

The plugin knows which items a group member has equipped, but a running client will not say what
those items look like worn. `ItemComposition` exposes only the flat inventory icon, and a
`PlayerComposition` cannot be constructed for somebody who is not on your world. The models are
in the cache, so they are read at build time and bundled, exactly as the world map's area data
is.

## Regenerating after a game update

> **Use RuneLite's own cache, not `~/jagexcache`.** The Jagex launcher's copy at
> `~/jagexcache/oldschool/LIVE` can be years out of date if you no longer launch the game that
> way, and a stale cache is silently wrong rather than obviously broken: model and area ids
> shift between revisions, so what comes out references meshes the running client no longer
> has. Check the modification date of `main_file_cache.dat2` before trusting either path.

```bash
./gradlew run --args="$HOME/.runelite/jagexcache/oldschool/LIVE ../../src/main/resources/com/groupironmencompanion/gear.dat"
```

A third argument filters items by name and prints what the table holds for each, which is the
quickest way to check whether a particular piece of gear made it in.

Then run the plugin's own `GhostAppearanceTest`, which loads the regenerated resource and
asserts that a spread of known items still resolve the way the composition rules expect. It is
the quickest check that an update has not moved something underneath us.

Regeneration is only needed to pick up newly released items. Gear the table predates renders as
the member's own body showing through that slot, which is a cosmetic shortfall rather than a
failure, so a stale table is safe to ship.

## What comes out

Two tables, and deliberately no composition rules — those live in `GhostAppearance` in the
plugin, where they can be unit tested.

**Items.** Up to three worn meshes per item. The first is the item in its own equipment slot.
The second, where present, is the piece that stands in for the body part underneath:

| item | models | meaning |
| --- | --- | --- |
| Rune platebody | `306, 164` | torso, plus its own sleeves |
| Rune chainbody | `301, -1` | torso only, so the wearer's arms show |
| Blue wizard robe | `170, 292` | torso, plus long sleeves |
| Monk's robe | `265, -1` | torso only |

That distinction is the whole of the occlusion problem, and it comes free in the data — no
hand-curated table of what-covers-what is needed, because the presence of a second mesh is the
answer.

Note that `wearPos1..3` look like they should answer this and do not. The fields exist on
`ItemDefinition`, but no OSRS item populates them; every one reads `-1`. They are ignored.

**Body kits.** One identity kit model per male body part, which shows wherever no item covers
it. Kits are grouped by `bodyPartId`, which runs 0-6 for male parts and 7-13 for female, with 8
absent because female kits have no jaw. A member's own kit — their hair, build and skin — is
published nowhere, so an arbitrary default body is unavoidable. Everything is recoloured to a
single ghost blue at runtime, so it never shows, and a group sharing one silhouette is arguably
preferable.

Colours and textures are not dumped for the same reason: the ghost tint would overwrite them.

## Output format

```
int    format version
int    body part count
int[]  default model per male body part, -1 for none
int    item count
per item, ascending by id:
  int    item id
  int    worn model 1, 2, 3   (-1 for none)
  short  vertical offset
```

Binary rather than JSON because it is a few thousand fixed-width records with no structure worth
expressing. The current table is about 4,500 items in 80 KB.

## Dependencies

This tool depends on `net.runelite:cache`. It is a build-time utility and is **not** part of the
plugin build — the plugin itself has no dependencies beyond `runelite-client`, and the generated
file is checked in so the plugin builds without ever running this.
