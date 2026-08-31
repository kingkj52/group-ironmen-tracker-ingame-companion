"""
Builds map-areas.json, the resource shipped with the Group Ironmen Tracker In-Game Companion plugin.

Inputs (both generated, nothing hand-written):
  areas.json           - every world map the game defines, with the real world squares it
                         draws and the map's intermap links, dumped from the game cache
                         by AreaDump.java
  DungeonLocation.java - RuneLite's curated entrance names (BSD-2-Clause), used only to
                         label an entrance the cache gives no name for

Output:
  map-areas.json
    {"areas":[{"name","squares":[packed],"exits":[{x,y,ex,ey,name}]}],
     "looseLinks":[{x,y,ex,ey,name}]}

A square is packed as (x / 64) * 256 + (y / 64). The surface map is excluded: a member
standing on it is already drawn where they are.

Exits are resolved through the map graph, not just direct links, because some maps only
connect to another dungeon. Mor Ul Rek, for example, links into Karamja Underground, whose
own exit reaches the volcano on the surface.
"""
import json
import re
import sys
from collections import deque

SURFACE_MAP = "RuneScape Surface"
NAME_SNAP = 30  # tiles: how close a DungeonLocation must be to lend its name

sys.stdout.reconfigure(encoding="utf-8")


def packed(square_x, square_z):
    return square_x * 256 + square_z


def square_of(x, y):
    return packed(x // 64, y // 64)


def distance(a, b):
    return ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** 0.5


def load_names(path):
    source = open(path, encoding="utf-8").read()
    pattern = re.compile(
        r'^\s*[A-Z0-9_]+\("([^"]*)",\s*new WorldPoint\((\d+),\s*(\d+),\s*(\d+)\)\)',
        re.M,
    )
    return [
        {"name": m.group(1), "x": int(m.group(2)), "y": int(m.group(3))}
        for m in pattern.finditer(source)
    ]


def nearest_name(point, names):
    best, best_distance = None, 1e18
    for entry in names:
        d = distance((entry["x"], entry["y"]), point)
        if d < best_distance:
            best_distance, best = d, entry
    return best["name"] if best and best_distance <= NAME_SNAP else ""


def main():
    dump = json.load(open("areas.json", encoding="utf-8"))
    names = load_names("DungeonLocation.java")

    surface_squares = set()
    areas = {}
    square_owner = {}
    for entry in dump["areas"]:
        squares = {packed(s[0], s[1]) for s in entry["squares"]}
        if entry["name"] == SURFACE_MAP:
            surface_squares = squares
            continue
        areas[entry["name"]] = {"name": entry["name"], "squares": squares, "exits": []}
        for square in squares:
            square_owner[square] = entry["name"]

    def on_surface(point):
        return square_of(point[0], point[1]) in surface_squares

    def area_of(point):
        return square_owner.get(square_of(point[0], point[1]))

    # Every link, as an ordered pair in both directions.
    edges = []
    for link in dump["links"]:
        a = (link["fromX"], link["fromY"])
        b = (link["toX"], link["toY"])
        edges.append((a, b))
        edges.append((b, a))

    # Pass 1: direct exits, where a link from inside a map lands on the surface.
    loose = []
    pending = []  # (source point, target point) where the target is another dungeon
    for source, target in edges:
        if on_surface(source):
            continue
        owner = area_of(source)
        if on_surface(target):
            exit_entry = {
                "x": source[0], "y": source[1],
                "ex": target[0], "ey": target[1],
                "name": nearest_name(target, names),
            }
            if owner is None:
                loose.append(exit_entry)
            else:
                areas[owner]["exits"].append(exit_entry)
        elif owner is not None:
            pending.append((owner, source, target))

    # Pass 2: maps that only reach another dungeon inherit that dungeon's nearest exit.
    # Repeated until nothing new resolves, so chains of any length settle.
    for _ in range(len(areas)):
        progressed = False
        for owner, source, target in pending:
            if areas[owner]["exits"]:
                continue
            neighbour = area_of(target)
            if neighbour is None or not areas[neighbour]["exits"]:
                continue
            best = min(areas[neighbour]["exits"], key=lambda e: distance((e["x"], e["y"]), target))
            areas[owner]["exits"].append({
                "x": source[0], "y": source[1],
                "ex": best["ex"], "ey": best["ey"],
                "name": best["name"],
            })
            progressed = True
        if not progressed:
            break

    out_areas = [
        {"name": a["name"], "squares": sorted(a["squares"]), "exits": a["exits"]}
        for a in areas.values()
    ]

    with_exits = sum(1 for a in out_areas if a["exits"])
    print("areas: %d (%d with a resolved surface entrance)" % (len(out_areas), with_exits))
    print("surface squares: %d, dungeon squares: %d" % (len(surface_squares), len(square_owner)))
    print("loose links (in no known map): %d" % len(loose))
    print()
    for area in out_areas:
        if not area["exits"]:
            print("  no entrance: %s" % area["name"])

    with open("map-areas.json", "w", encoding="utf-8") as out:
        json.dump({"areas": out_areas, "looseLinks": loose}, out, separators=(",", ":"))
    print()
    print("wrote map-areas.json")


if __name__ == "__main__":
    main()
