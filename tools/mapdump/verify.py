"""Runs the plugin's runtime lookup algorithm against known dungeon interiors."""
import json
import sys

sys.stdout.reconfigure(encoding="utf-8")

LOOSE_LINK_RADIUS = 64
UNDERGROUND_Y = 6400
UNDERGROUND_OFFSET = 6400

data = json.load(open("map-areas.json", encoding="utf-8"))
areas = data["areas"]
loose = data["looseLinks"]

owner = {}
for area in areas:
    for square in area["squares"]:
        owner[square] = area


def distance(ax, ay, bx, by):
    return ((ax - bx) ** 2 + (ay - by) ** 2) ** 0.5


def lookup(x, y):
    """Returns (areaName, entrance, how)."""
    area = owner.get((x // 64) * 256 + (y // 64))
    name = area["name"] if area else None

    if area and area["exits"]:
        best = min(area["exits"], key=lambda e: distance(e["x"], e["y"], x, y))
        return name, (best["ex"], best["ey"]), best["name"] or "area exit"

    best, best_distance = None, 1e18
    for link in loose:
        d = distance(link["x"], link["y"], x, y)
        if d < best_distance:
            best_distance, best = d, link
    if best and best_distance <= LOOSE_LINK_RADIUS:
        return name, (best["ex"], best["ey"]), (best["name"] or "loose link")

    if y >= UNDERGROUND_Y:
        return name, (x, y - UNDERGROUND_OFFSET), "projected"

    return name, None, "none"


TESTS = [
    ("Taverley Dungeon", (2884, 9813), (2884, 3397)),
    ("Edgeville Dungeon", (3096, 9867), (3097, 3468)),
    ("Varrock Sewers", (3237, 9858), (3237, 3458)),
    ("Lumbridge Swamp Caves", (3169, 9572), (3169, 3172)),
    ("Dwarven Mine", (3018, 9800), (3019, 3337)),
    ("Brimhaven Dungeon", (2713, 9564), (2745, 3152)),
    ("Asgarnian Ice Dungeon", (3007, 9550), (3007, 3150)),
    ("Fremennik Slayer Dungeon", (2802, 9998), (2808, 3652)),
    ("God Wars Dungeon", (2882, 5310), (2916, 3746)),
    ("Catacombs of Kourend", (1663, 10047), (1615, 3673)),
    ("Kalphite Lair", (3483, 9510), (3228, 3108)),
    ("Zanaris", (2452, 4473), (3202, 3169)),
    ("TzHaar / Mor Ul Rek", (2480, 5175), (2857, 3168)),
    ("Barrows tunnels", (3565, 9695), (3565, 3306)),
    ("Stronghold of Security", (1859, 5243), None),
    ("Ancient Cavern", (1763, 5361), None),
    ("Waterbirth Dungeon", (2495, 10144), None),
    ("Keldagrim", (2879, 10176), None),
    ("Motherlode Mine", (3760, 5666), None),
    ("Abyss", (3040, 4832), None),
    ("Dorgesh-Kaan", (2720, 5344), None),
    ("Slayer Tower basement", (3420, 9620), None),
    ("Chasm of Fire", (1432, 10070), None),
    ("Revenant Caves", (3220, 10120), None),
    ("Wilderness Slayer Cave", (3300, 10200), None),
    ("Fossil Island Underground", (3744, 10272), None),
    ("Kebos Underground", (1266, 10206), None),
    ("Yanille Underground", (2580, 9522), None),
    ("Troll Stronghold", (2822, 10087), None),
    ("Ourania Altar", (3040, 5600), None),
    ("Ruins of Camdozaal", (2952, 5766), None),
]

print("%-28s %-24s %-16s %-8s %s" % ("test point", "area (from cache)", "entrance", "source", "vs old"))
for label, point, old in TESTS:
    name, entrance, how = lookup(*point)
    drift = ""
    if old and entrance:
        drift = "%.0f" % distance(old[0], old[1], entrance[0], entrance[1])
    print("%-28s %-24s %-16s %-8s %s"
          % (label, (name or "-")[:24], str(entrance) if entrance else "NONE", how[:8], drift))
