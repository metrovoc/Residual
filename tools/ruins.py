"""Builds the ruins: structure templates (NBT) and the worldgen, tags and loot tables that place and fill them.

Called by data.py; run that instead.
"""
import gzip
import random
import struct

DATA_VERSION = 4790  # Minecraft 26.1.2

# ---------------------------------------------------------------- NBT

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)


def _tag(v):
    if isinstance(v, bool) or isinstance(v, int) and not isinstance(v, Long):
        return INT
    if isinstance(v, Long):
        return LONG
    if isinstance(v, str):
        return STRING
    if isinstance(v, dict):
        return COMPOUND
    if isinstance(v, list):
        return LIST
    raise TypeError(v)


class Long(int):
    pass


def _payload(v):
    t = _tag(v)
    if t == INT:
        return struct.pack(">i", int(v))
    if t == LONG:
        return struct.pack(">q", int(v))
    if t == STRING:
        b = v.encode()
        return struct.pack(">H", len(b)) + b
    if t == LIST:
        et = _tag(v[0]) if v else END
        return bytes([et]) + struct.pack(">i", len(v)) + b"".join(_payload(e) for e in v)
    out = b""
    for k, e in v.items():
        out += bytes([_tag(e)]) + _payload(k) + _payload(e)
    return out + bytes([END])


def nbt(root):
    return gzip.compress(bytes([COMPOUND]) + _payload("") + _payload(root), mtime=0)


# ---------------------------------------------------------------- templates

class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}

    def set(self, x, y, z, name, props=None, data=None):
        self.blocks[(x, y, z)] = (name if ":" in name else "minecraft:" + name, props or {}, data)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, name, props)

    def chest(self, x, y, z, facing, loot):
        self.set(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"},
                 {"id": "minecraft:chest", "LootTable": loot})

    def suspicious(self, x, y, z, block, loot):
        self.set(x, y, z, "suspicious_" + block, {"dusted": "0"}, {"id": "minecraft:brushable_block", "LootTable": loot})

    def build(self):
        palette, index, blocks = [], {}, []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            name, props, data = self.blocks[pos]
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                palette.append({"Name": name} | ({"Properties": dict(props)} if props else {}))
            block = {"pos": list(pos), "state": index[key]}
            if data:
                block["nbt"] = data
            blocks.append(block)
        return nbt({"size": list(self.size), "entities": [], "blocks": blocks, "palette": palette,
                    "DataVersion": DATA_VERSION})


def archive_house():
    """A small archive hall of calcite and tuff: shelves along the back, a lectern, a chest, a caved-in corner."""
    rng = random.Random(1)
    t = Template(11, 7, 9)
    t.fill(0, 0, 0, 10, 0, 8, "polished_tuff")
    t.fill(1, 1, 1, 9, 5, 7, "air")
    for x in range(11):
        for z in range(9):
            edge_x, edge_z = x in (0, 10), z in (0, 8)
            if not (edge_x or edge_z):
                if rng.random() < 0.25:
                    t.set(x, 0, z, "tuff_bricks")
                continue
            for y in range(1, 5):
                corner = edge_x and edge_z
                t.set(x, y, z, "tuff_bricks" if corner or y == 4 else "calcite")
    for x in (2, 4, 6, 8):
        t.set(x, 2, 0, "glass_pane", {"north": "false", "south": "false", "east": "true", "west": "true", "waterlogged": "false"})
        t.set(x, 2, 8, "glass_pane", {"north": "false", "south": "false", "east": "true", "west": "true", "waterlogged": "false"})
    for y in (1, 2):
        t.set(5, y, 0, "air")
    for x in range(11):
        for z in range(9):
            t.set(x, 5, z, "tuff_brick_slab", {"type": "bottom", "waterlogged": "false"})
    # the roof fell in over one corner, and the floor under it
    for x in range(6, 11):
        for z in range(5, 9):
            if (x - 6) + (z - 5) < 5:
                t.set(x, 5, z, "air")
                if 0 < x < 10 and 0 < z < 8:
                    t.set(x, 1, z, "gravel")
    t.suspicious(7, 1, 6, "gravel", "residual:archaeology/ruin")
    t.suspicious(8, 1, 5, "gravel", "residual:archaeology/ruin")
    t.suspicious(8, 0, 7, "gravel", "residual:archaeology/ruin")
    t.suspicious(6, 1, 5, "gravel", "residual:archaeology/ruin")
    for x in (1, 2, 3):
        t.set(x, 1, 7, "chiseled_bookshelf", {"facing": "south", **{f"slot_{i}_occupied": "false" for i in range(6)}})
        t.set(x, 2, 7, "chiseled_bookshelf", {"facing": "south", **{f"slot_{i}_occupied": "false" for i in range(6)}})
    t.set(5, 1, 6, "lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    t.chest(1, 1, 1, "east", "residual:chests/archive_house")
    for y in (1, 2):
        t.set(9, y, 1, "barrel", {"facing": "up", "open": "false"}, {"id": "minecraft:barrel", "LootTable": "residual:chests/archive_shelf"})
    t.set(5, 4, 4, "lantern", {"hanging": "true", "waterlogged": "false"})
    t.set(2, 3, 2, "cobweb")
    t.set(8, 4, 2, "cobweb")
    return t


def relay_mast():
    """One of the old relay towers: a copper mast on a tuff footing, with its relay still on top."""
    t = Template(5, 15, 5)
    t.fill(0, 0, 0, 4, 0, 4, "tuff_bricks")
    for x, z in ((0, 0), (4, 0), (0, 4), (4, 4)):
        t.set(x, 0, z, "chiseled_tuff")
    t.fill(0, 1, 0, 4, 14, 4, "air")
    t.fill(2, 1, 2, 2, 3, 2, "oxidized_cut_copper")
    t.fill(2, 4, 2, 2, 12, 2, "oxidized_copper_grate", {"waterlogged": "false"})
    t.set(2, 13, 2, "residual:relay")
    for y in range(1, 13):
        t.set(2, y, 1, "ladder", {"facing": "north", "waterlogged": "false"})
    for x, z, axis in ((1, 2, "x"), (3, 2, "x"), (2, 3, "z")):
        for y in (4, 8):
            t.set(x, y, z, "lightning_rod", {"facing": {"x": "east" if x > 2 else "west", "z": "south"}[axis],
                                              "powered": "false", "waterlogged": "false"})
    t.chest(0, 1, 2, "east", "residual:chests/relay_mast")
    t.set(4, 1, 1, "oxidized_cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(3, 1, 4, "oxidized_cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(1, 1, 3, "suspicious_gravel", {"dusted": "0"}, {"id": "minecraft:brushable_block", "LootTable": "residual:archaeology/ruin"})
    return t


def buried_vault():
    """A deepslate vault below ground: a dead bank of machines, two chests, and a shaft up to a hatch."""
    t = Template(11, 13, 11)
    t.fill(0, 0, 0, 10, 0, 10, "deepslate_tiles")
    t.fill(0, 1, 0, 10, 5, 10, "polished_deepslate")
    t.fill(1, 1, 1, 9, 5, 9, "air")
    t.fill(0, 6, 0, 10, 6, 10, "deepslate_tiles")
    for x, z in ((1, 1), (9, 1), (1, 9), (9, 9)):
        t.set(x, 5, z, "oxidized_copper_bulb", {"lit": "true", "powered": "false"})
    # the machine bank along the back wall
    for x in range(2, 9):
        t.set(x, 1, 9, "residual:tensor_casing")
        t.set(x, 2, 9, "residual:accelerator" if x in (4, 6) else "residual:tensor_casing")
    for x in range(2, 9):
        t.set(x, 1, 8, "residual:cable", {d: str(d in ("east", "west", "south")).lower()
                                          for d in ("north", "south", "east", "west", "up", "down")})
    t.chest(1, 1, 7, "east", "residual:chests/buried_vault")
    t.chest(9, 1, 7, "west", "residual:chests/buried_vault")
    t.set(5, 1, 5, "residual:tensor_casing")
    t.set(5, 2, 5, "lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    t.suspicious(3, 0, 3, "gravel", "residual:archaeology/ruin")
    t.suspicious(7, 0, 4, "gravel", "residual:archaeology/ruin")
    # the shaft: a ladder up through the ceiling to a hatch at the surface
    for y in range(6, 11):
        for x, z in ((4, 1), (6, 1), (5, 2), (5, 0)):
            t.set(x, y, z, "tuff_bricks")
        t.set(5, y, 1, "air")
    for y in range(1, 11):
        t.set(5, y, 1, "ladder", {"facing": "south", "waterlogged": "false"})
    t.set(5, 11, 1, "oxidized_copper_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false",
                                                  "waterlogged": "false"})
    t.set(7, 11, 1, "tuff_brick_wall", {"east": "none", "west": "none", "north": "none", "south": "none", "up": "true",
                                         "waterlogged": "false"})
    t.set(7, 12, 1, "lantern", {"hanging": "false", "waterlogged": "false"})
    return t


RUINS = {
    # name: (template, step, start height, terrain adaptation, spacing, separation, salt)
    "archive_house": (archive_house, "surface_structures", 0, "beard_thin", 18, 6, 72930141),
    "relay_mast": (relay_mast, "surface_structures", 0, "beard_thin", 22, 7, 61820453),
    "buried_vault": (buried_vault, "underground_structures", -10, "none", 28, 9, 50417729),
}
BIOMES = ["plains", "sunflower_plains", "meadow", "forest", "birch_forest", "flower_forest", "old_growth_birch_forest",
          "dark_forest", "taiga", "snowy_plains", "snowy_taiga", "savanna", "savanna_plateau", "desert",
          "windswept_hills", "cherry_grove", "badlands"]


# ---------------------------------------------------------------- loot

def item(name, weight=1, count=None, components=None):
    entry = {"type": "minecraft:item", "name": name if ":" in name else f"residual:{name}", "weight": weight}
    functions = []
    if count:
        functions.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
    if components:
        functions.append({"function": "minecraft:set_components", "components": components})
    if functions:
        entry["functions"] = functions
    return entry


def pool(rolls, entries):
    return {"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "bonus_rolls": 0.0, "entries": entries}


WRECKS = ["shape", "diverge", "leak", "order", "depth"]

LOOT = {
    "chests/archive_house": [pool((2, 4), [
        item("archive_shard", 10, (1, 3)), item("weight_crystal", 4, (1, 3)), item("schematic_context", 2),
        item("schematic_attention", 1), item("schematic_positions", 1), item("minecraft:paper", 4, (2, 6)),
        item("minecraft:book", 2, (1, 2))])],
    "chests/archive_shelf": [pool((1, 3), [item("archive_shard", 8), item("minecraft:paper", 5, (1, 4)),
                                           item("minecraft:ink_sac", 2, (1, 2)), item("minecraft:candle", 2, (1, 2))])],
    "chests/relay_mast": [pool((2, 3), [
        item("archive_shard", 8, (1, 2)), item("relay_card", 4), item("schematic_relays", 2),
        item("minecraft:copper_ingot", 6, (2, 6)), item("minecraft:redstone", 5, (2, 5))])],
    "chests/buried_vault": [
        pool((1, 1), [item("salvaged_mind", 1, components={"residual:wreck": w}) for w in WRECKS]),
        pool((2, 4), [
            item("schematic_attention", 2), item("schematic_positions", 2), item("schematic_blocks", 1),
            item("schematic_relays", 1), item("checkpoint", 3), item("weight_crystal", 5, (2, 4)),
            item("archive_shard", 6, (1, 3)), item("minecraft:diamond", 1), item("minecraft:gold_ingot", 3, (1, 3))])],
    "archaeology/ruin": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
        item("archive_shard", 9), item("schematic_context", 1), item("weight_crystal", 2)]}],
}


def write_all(write, rid):
    for name, (build, step, start, terrain, spacing, separation, salt) in RUINS.items():
        path = write.root / f"data/residual/structure/ruins/{name}.nbt"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(build().build())
        write(f"data/residual/worldgen/template_pool/ruins/{name}.json", {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                                    "location": rid(f"ruins/{name}"), "processors": rid("ruin_decay"),
                                                    "projection": "rigid"}}]})
        write(f"data/residual/worldgen/structure/{name}.json", {
            "type": "minecraft:jigsaw", "biomes": "#residual:has_ruins", "step": step, "spawn_overrides": {},
            "terrain_adaptation": terrain, "start_pool": rid(f"ruins/{name}"), "size": 1,
            "start_height": {"absolute": start}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 16, "use_expansion_hack": False})
        write(f"data/residual/worldgen/structure_set/{name}.json", {
            "structures": [{"structure": rid(name), "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt}})
    write("data/residual/worldgen/processor_list/ruin_decay.json", {"processors": [
        {"processor_type": "minecraft:block_rot", "integrity": 0.82, "rottable_blocks": "#residual:ruin_rot"}]})
    write("data/residual/tags/block/ruin_rot.json", {"values": [
        "minecraft:calcite", "minecraft:tuff_bricks", "minecraft:polished_tuff", "minecraft:tuff_brick_slab",
        "minecraft:polished_deepslate", "minecraft:glass_pane", "minecraft:cobweb"]})
    write("data/residual/tags/worldgen/biome/has_ruins.json", {"values": [f"minecraft:{b}" for b in BIOMES]})
    write("data/residual/tags/worldgen/structure/ruins.json", {"values": [rid(n) for n in RUINS]})
    for name, pools in LOOT.items():
        kind = "minecraft:archaeology" if name.startswith("archaeology") else "minecraft:chest"
        write(f"data/residual/loot_table/{name}.json", {"type": kind, "pools": pools, "random_sequence": rid(name)})
