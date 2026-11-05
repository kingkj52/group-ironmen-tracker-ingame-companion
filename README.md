# Group Ironmen Tracker In-Game Companion

Brings the [groupiron.men](https://groupiron.men) group ironman dashboard into the client, so
you can see what your team has and where they are without alt-tabbing to a website.

It reads the data your group already publishes, so it works as a companion to
[group-ironmen-tracker](https://github.com/christoabrown/group-ironmen-tracker), and falls
back to collecting that data itself when that plugin is not running.

> **Unofficial.** This is an independent plugin. It is not affiliated with, endorsed by, or
> maintained by the authors of the Group Ironmen Tracker plugin or the groupiron.men website.
> It talks to their API the same way their own website does, using credentials you supply.

## Features

Every feature below can be switched off on its own in the plugin settings.

**Group bank** - a read-only, searchable window showing everything the group owns.
`Ctrl+G` by default, or click the **Group** tab (a bank tab with the group ironman helmet)
that appears beside your bank.

**New** It's drawn with the bank's own sprites and fonts, so it matches the real bank beside it.

The first time you open it, it places itself to the right of the bank, just below the Group
tab. If it does not fit there it goes to the left of the bank, or against the left edge of
the screen when the bank is too wide to sit beside (which it always is in fixed mode). After
that, alt-drag it wherever you like: the position is remembered.

It only swallows clicks inside its own frame, so your own bank stays fully usable beside it.

- An **All** tab totalling every item across the group, sorted by value, with a hover
  breakdown of who is holding it.
- A tab per member, split into Bank / Inventory / Equipment / Rune pouch / Seed vault /
  Quiver, plus a **Group Storage** tab.
- **New** Member tabs show the group ironman helmet in that member's map colour, with their name
  underneath.
- Click the search box and type to filter. `Esc` clears, then closes.
- Alt-drag to move it, like any RuneLite overlay.
- **New** Drag an edge or corner to resize it (no Alt needed). The size is remembered with the
  position.
- **New** Follows Resource Packs and Interface Styles reskins. The map list, skills panel and
  tooltips use RuneLite's overlay colour.

**Group levels on the Skills tab** - hover any skill and a panel lists every member's level,
experience and experience to the next level, each in that member's colour. Its placement is configurable.

**World map markers** - a coloured pin per member on the world map. When a member is off the
overworld, a second hollow marker with a downward chevron is drawn at the surface entrance
that leads to them, so you can find them without knowing where they went. Their real marker
still appears when you pan the map to that dungeon's own view, because RuneLite only draws a
world map point on the map that actually covers it.

While a member is somewhere the map cannot draw, their surface marker stays parked on one
entrance rather than sliding around as they walk about underground.

Opening the world map also shows a small list of online members: click one to jump the map to
them, or right-click to route to them with the Shortest Path plugin. The route follows them as
they move and clears once you reach them.

Entrances cover every area the game defines a map for - around 40, including the ones that
are not simply "the surface plus 6400", such as Zanaris, God Wars Dungeon, Mor Ul Rek and the
Stronghold of Security. The data is generated from the game cache rather than measured by
hand or pulled from online sources; see [tools/mapdump](tools/mapdump/README.md).

**Side panel** - five tabs:

- **Group** - each member's hitpoints, prayer, run energy, world, last-seen time and what
  they seem to be doing (see below).
- **Items** - search everything the group owns and see who is holding it.
- **Skills** - total level and experience, or one skill compared across the group.
- **Quests** - completion per member, plus a search showing where everyone stands on a quest.
- **Diaries** - achievement diary completion per member, overall or broken down by region
  and tier.

**Status overlay** ***Newly Updated*** (off by default) - the group at a glance in the game's own fonts: each
member's name and world, hitpoints, prayer and run energy bars, and a line saying what they
seem to be doing. Offline members get one grey line with when they were last seen. Alt-drag
to move it, alt-drag its edge to change the width.

**What members are doing** **New** - a guess, shown in the status overlay and side panel, like
"Fishing at Catherby", "Fighting Goblin (43%)" or "Idle at Lumbridge (3m)".

It can be wrong. The tracker doesn't share an activity, so it's worked out from what
changes between updates: XP, position, quests, bank and inventory, and what they're
interacting with. Some limits:

- Woodcutting, mining and similar only show once the XP arrives, since trees and rocks
  aren't an interaction.
- A quest only shows as in progress if it started while you were watching.
- Standing still may look the same as logged out once the offline threshold passes.
- An unusual instance may show a nearby place name instead of its own.

Places come from region names and the world map's own labels (see Credits), so new areas should be
named without a plugin update.

**Members in the world** **New** - off by default, the only feature that draws in the game scene.
Group members standing close enough to be on screen but on different worlds are drawn as translucent
blue figures, wearing the gear they actually have equipped, even though they are on another world.
The point is farming or skilling the same spot on separate worlds and still enjoying company.

They are this plugin's own drawing and nothing more: absent from the player and NPC lists,
impossible to click or target, and unknown to the server. A member on *your* world is left to
the game to draw, and hovering one names it in a small label panel beside the cursor.

A figure acts out the same guess the status overlay shows (see below), so the two always
agree: fighting, chopping, mining, fishing, farming and so on. Someone who moves mid-fight
keeps their opponent beside them. Updates come about once a second, so figures lag a little
and short actions are approximate.

**Never drawn in the Wilderness or on any PvP world, and that cannot be switched off.**

## Setup

1. One member creates a group at [groupiron.men](https://groupiron.men) and shares the group
   name and token.
2. Put them in this plugin's **Connection** settings.

If you already run the official Group Ironmen Tracker plugin, you can leave all three fields
blank - this plugin reads the group name, token and self-hosted server URL from that plugin's
configuration.

This works whether your group uses the public groupiron.men site or self-hosts: the plugin
reads the same API the website reads, with the same credentials. A group where everyone runs
only the official tracker plugin needs no extra setup at all - install this alongside it and
the in-game views populate from what the tracker is already uploading.

### Sharing your own data

Everyone in the group needs *something* uploading their character data for any of this to
have anything to show.

- If the official **Group Ironmen Tracker** plugin is running with a group name and token set,
  it does the uploading and this plugin only reads. The two never both upload.
- If it is not, this plugin uploads the same fields in the same format, so the website keeps
  working either way. Collection log progress is the one thing it does not collect; install
  the tracker plugin if your group wants that.
- Set **Data sharing → Share my data** to `OFF` to never upload anything. With this `OFF` and the
  official plugin not running, nothing will work as it can't sync to the website.

## Settings

| Section | What it controls |
| --- | --- |
| Connection | Group name, token, self-hosted server URL, refresh interval, offline threshold |
| Side panel | Enable, show what members are doing, show offline members, include yourself |
| Group bank | Enable, hotkey, bank tab, open/close with the bank, item values |
| Skills tab | Enable hover, panel position, include yourself, include offline members |
| World map | Enable markers, dungeon entrance markers, include offline members, member list, routing |
| Status overlay | Enable, vitals bars, activity, offline members, include yourself, bar numbers, backdrop |
| Scene presence | Draw members on other worlds, draw what they are fighting, hover labels, transparency |
| Member colours | One colour per member, assigned alphabetically |
| Data sharing | Whether to upload your own data when the tracker plugin is absent |

## Rules compliance

Group ironman data is shared between accounts that have all opted in, which is exactly what
the official tracker plugin and website already do. Bringing that display in-game raises the
bar, so this plugin is built to stay clearly inside
[Jagex's Third-Party Client Guidelines](https://oldschool.runescape.wiki/w/Update:Third_Party_Client_Guidelines):

- **No menu entries.** The plugin never calls `createMenuEntry` or `Overlay.addMenuEntry`.
  Nothing it draws can cause an action to be sent to the server, which is the guidelines'
  actual prohibition on menus. Everything clickable belongs to the plugin's own overlays.
  While the mouse is over the group bank window or its Group tab, the game's own options for
  whatever is behind the window are removed, leaving only Cancel, so the window never shows
  or offers actions on the scene behind it. Nothing is ever added.
- **No interface modification.** No game widget is created, hidden, unhidden, reparented,
  moved, resized or recoloured, and no click zone is changed. Game widgets are only read -
  for their on-screen bounds, to know which skill the mouse is over and where the bank window
  sits so the Group tab can be drawn *outside* it, and for the colour of the bank's separator
  line, so the group bank can match a recoloured bank. Every view is the plugin's own drawing.
  The game's sprites, and any interface reskin's replacements for them, are only read, to
  draw those views in the bank's style; nothing is ever written to them.
- **Read-only by construction.** The plugin only ever holds item ids and quantities read back
  from your group's server. There is no code path that could withdraw, deposit or use
  anything.
- **Nothing on the minimap, ever.** Member positions go on the world map. One optional
  feature, off by default, draws translucent figures in the game scene where nearby members
  are standing; it is confined to its own package, cannot be clicked or targeted, and is
  hardwired off in the Wilderness and on PvP worlds with no setting to override that.
- **No PvP or combat assistance.** The only combat-adjacent data is what a group member is
  fighting or doing. It shows in the side panel and status overlay, and scene presence can
  draw it in the world. Scene presence only draws members on *other* worlds, so nothing it
  shows can be part of your own fight, and it's off in the Wilderness and on PvP worlds.
  Nothing predicts attacks or prayers, and nothing is shown about anyone outside your group,
  who each opted in by running a tracker. All of it can be switched off.
- **No reflection**, no JNI, no external processes, no filesystem access, no runtime code
  loading, and no dependencies beyond what runelite-client already provides.

Each of those is enforced by `ComplianceTest`, which greps the source tree on every build, so
none of them can regress quietly.

## Load on the server

groupiron.men is a free service run by one person, and this plugin is on the hub, so how many
clients are pointed at it is not something this code gets to assume.

- One host is contacted, ever: the one under Connection. There are no wiki lookups, no price
  APIs, no telemetry. `ComplianceTest` fails the build if a second URL appears in the source.
- Requests carry the delta cursor the API provides, so a poll that finds nothing new returns
  nothing new.
- The refresh interval is capped at one second and defaults to three.
- **While nobody is logged in, polling backs off to once every thirty seconds.** The side panel
  still updates, which is the reason not to stop altogether; logging in takes effect at once
  rather than waiting the backoff out. `PollRateTest` pins the rule.

If you self-host, none of this reaches groupiron.men at all.

## Security

- The only host the plugin contacts is the base URL you configured - `groupiron.men` by
  default, or your own server.
- Requests carry your group token, so redirects are disabled: a redirect could otherwise hand
  that token to a host you never configured.
- All requests have a hard call timeout and run on the plugin's own thread, so a slow or
  hung server cannot stall the client or another plugin's scheduled work.
- Responses are parsed into plain data classes and are size-capped. Response bodies are never
  logged, and neither is your token.
- No files are read or written. The bundled resources (map areas and place names) are read
  from the jar.

## Building

```bash
./gradlew build
```

`./gradlew deploy` copies the jar into `~/.runelite/sideloaded-plugins`, clearing out older
builds of this plugin first.

## Map area data

`map-areas.json` is generated from the game cache by [tools/mapdump](tools/mapdump/README.md)
and covers every world map the game defines. Regenerate it after a game update; `MapAreasTest`
asserts that a spread of known dungeons still resolve to the right entrances.

Where the cache defines no route to the surface - the Abyss, Tutorial Island, a couple of
Prifddinas interiors - a member still gets their true-position marker and the area's real
name in the tooltip, just no entrance marker.

## Worn model data

`gear.dat` is generated from the game cache by [tools/geardump](tools/geardump/README.md) and
holds the worn models for every equippable item, about 4,500 of them in 80 KB. It exists
because a running client will not say what an item looks like worn: `ItemComposition` offers
only the flat inventory icon, and a `PlayerComposition` cannot be built for somebody who is not
on your world.

Regenerate it after a game update to pick up new items. Gear the table predates renders as the
member's own body showing through that slot, so a stale table is cosmetically poorer rather
than broken. `GhostAppearanceTest` loads the generated file and asserts that a spread of known
items still resolve the way the composition rules expect, including one item recent enough to
catch a table built from the wrong cache.

## Credits

Place names combine the region list from
[Location Display](https://github.com/trinhc2/Location-Display) by trinhc2 (BSD 2-Clause)
with the world map's own labels from the game cache. Region ids for the instances the map
labels name badly come from RuneLite's Discord plugin.

## Not implemented

Collection log is neither collected nor shown; the official tracker plugin remains the way to
populate that.
