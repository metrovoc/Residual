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
    if isinstance(v, Byte):
        return BYTE
    if isinstance(v, bool) or isinstance(v, int) and not isinstance(v, Long):
        return INT
    if isinstance(v, Long):
        return LONG
    if isinstance(v, Float):
        return FLOAT
    if isinstance(v, float):
        return DOUBLE
    if isinstance(v, str):
        return STRING
    if isinstance(v, dict):
        return COMPOUND
    if isinstance(v, list):
        return LIST
    raise TypeError(v)


class Long(int):
    pass


class Byte(int):
    pass


class Float(float):
    pass


def _payload(v):
    t = _tag(v)
    if t == BYTE:
        return struct.pack(">b", int(v))
    if t == FLOAT:
        return struct.pack(">f", float(v))
    if t == DOUBLE:
        return struct.pack(">d", float(v))
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

SIDES = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
# what joins to its neighbours, by the end of its name; glass panes and iron bars join as panes do
JOINING = ("_fence", "_pane", "iron_bars", "_wall")
# blocks that do not fill their space, which fences, panes and walls do not join to
OPEN = ("air", "_slab", "_stairs", "torch", "lantern", "_door", "_trapdoor", "chest", "_bed", "_carpet", "ladder",
        "_sign", "rail", "_button", "lever", "flower_pot", "candle", "_rod", "cobweb", "chain", "_bars", "_pane",
        "_fence", "_wall", "_gate", "lectern", "campfire", "anvil", "grindstone", "bell", "hopper", "composter",
        "_leaves", "snow", "water", "lava", "_plate", "scaffolding", "_head", "_skull", "pot", "_cake", "fire",
        "sugar_cane", "wheat", "carrots", "potatoes", "beetroots", "_sapling", "grass", "fern", "bush", "dead_bush")


def solid(name):
    return not any(name.endswith(o) or o in name for o in OPEN)


class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.entities = []

    def set(self, x, y, z, name, props=None, data=None):
        self.blocks[(x, y, z)] = (name if ":" in name else "minecraft:" + name, props or {}, data)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, name, props)

    def walls(self, x0, y0, z0, x1, y1, z1, name, props=None):
        """The four walls of a box, its floor and ceiling left alone."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, name, props)
                self.set(x, y, z1, name, props)
            for z in range(z0, z1 + 1):
                self.set(x0, y, z, name, props)
                self.set(x1, y, z, name, props)

    def stairs(self, x, y, z, name, facing, half="bottom", shape="straight"):
        self.set(x, y, z, name, {"facing": facing, "half": half, "shape": shape, "waterlogged": "false"})

    def slab(self, x, y, z, name, half="bottom"):
        self.set(x, y, z, name, {"type": half, "waterlogged": "false"})

    def log(self, x, y, z, name, axis="y"):
        self.set(x, y, z, name, {"axis": axis})

    def door(self, x, y, z, name, facing, hinge="left", open_=False):
        for half, dy in (("lower", 0), ("upper", 1)):
            self.set(x, y + dy, z, name, {"facing": facing, "half": half, "hinge": hinge, "open": str(open_).lower(),
                                          "powered": "false"})

    def trapdoor(self, x, y, z, name, facing, half="bottom", open_=False):
        self.set(x, y, z, name, {"facing": facing, "half": half, "open": str(open_).lower(), "powered": "false",
                                 "waterlogged": "false"})

    def bed(self, x, y, z, color, facing):
        """A bed whose foot is at x, y, z and whose head lies toward facing."""
        dx, dz = SIDES[facing]
        self.set(x, y, z, f"{color}_bed", {"facing": facing, "part": "foot", "occupied": "false"})
        self.set(x + dx, y, z + dz, f"{color}_bed", {"facing": facing, "part": "head", "occupied": "false"})

    def barrel(self, x, y, z, facing, loot=None):
        self.set(x, y, z, "barrel", {"facing": facing, "open": "false"},
                 {"id": "minecraft:barrel", "LootTable": loot} if loot else None)

    def lantern(self, x, y, z, hanging=False, name="lantern"):
        self.set(x, y, z, name, {"hanging": str(hanging).lower(), "waterlogged": "false"})

    def wall_torch(self, x, y, z, facing, name="wall_torch"):
        self.set(x, y, z, name, {"facing": facing})

    def paste(self, other, ox, oy, oz):
        """Copies another template's blocks in, its corner at ox, oy, oz."""
        for (x, y, z), block in other.blocks.items():
            self.blocks[(x + ox, y + oy, z + oz)] = block

    def apprentice(self, x, y, z, yaw):
        """An apprentice standing still where its work stopped, facing yaw degrees (0 south, 90 west)."""
        self.entities.append({"pos": [x + 0.5, float(y), z + 0.5], "blockPos": [x, y, z], "nbt": {
            "id": "residual:agent", "NoAI": Byte(1), "PersistenceRequired": Byte(1), "program": "",
            "stopped": Byte(1), "Rotation": [Float(yaw), Float(0)]}})

    def connect(self):
        """Joins fences, panes, bars and walls to what stands beside them, as placing them by hand would."""
        for (x, y, z), (name, props, data) in list(self.blocks.items()):
            family = next((f for f in JOINING if name.endswith(f)), None)
            if family is None:
                continue
            new = dict(props)
            for side, (dx, dz) in SIDES.items():
                other = self.blocks.get((x + dx, y, z + dz))
                joins = other is not None and (other[0].endswith(family) or other[0].endswith("_gate")
                                               and family == "_fence" or solid(other[0]))
                if family == "_wall":
                    new[side] = "low" if joins else "none"
                else:
                    new[side] = str(joins).lower()
            if family == "_wall":
                above = self.blocks.get((x, y + 1, z))
                straight = new["north"] == new["south"] != new["east"] == new["west"]
                new["up"] = str(not straight or above is not None and above[0] != "minecraft:air").lower()
            new.setdefault("waterlogged", "false")
            self.blocks[(x, y, z)] = (name, new, data)

    def sign(self, x, y, z, facing, lines, wood="spruce"):
        """A sign on the wall behind it, facing out, with up to four lines of text."""
        lines = (list(lines) + ["", "", "", ""])[:4]
        self.set(x, y, z, f"{wood}_wall_sign", {"facing": facing, "waterlogged": "false"},
                 {"id": "minecraft:sign", "is_waxed": 1,
                  "front_text": {"messages": lines, "color": "black", "has_glowing_text": 0},
                  "back_text": {"messages": ["", "", "", ""], "color": "black", "has_glowing_text": 0}})

    def post_sign(self, x, y, z, rotation, lines, wood="oak"):
        """A sign standing on its own post, turned rotation sixteenths of a turn from south, with up to four lines."""
        lines = (list(lines) + ["", "", "", ""])[:4]
        self.set(x, y, z, f"{wood}_sign", {"rotation": str(rotation), "waterlogged": "false"},
                 {"id": "minecraft:sign", "is_waxed": 1,
                  "front_text": {"messages": lines, "color": "black", "has_glowing_text": 0},
                  "back_text": {"messages": lines, "color": "black", "has_glowing_text": 0}})

    def chest(self, x, y, z, facing, loot):
        self.set(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"},
                 {"id": "minecraft:chest", "LootTable": loot})

    def suspicious(self, x, y, z, block, loot):
        self.set(x, y, z, "suspicious_" + block, {"dusted": "0"}, {"id": "minecraft:brushable_block", "LootTable": loot})

    def build(self):
        self.connect()
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
        return nbt({"size": list(self.size), "entities": self.entities, "blocks": blocks, "palette": palette,
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


def ring_stairs(t, y, lo, hi, name, half="bottom", z0=None, z1=None):
    """Stairs round the ring of the square lo to hi (or of x lo to hi by z z0 to z1), each facing in, turning outward
    at the corners: a roof course, a stepped plinth, or upside down a row of corbels."""
    z0, z1 = (lo, hi) if z0 is None else (z0, z1)
    for x in range(lo, hi + 1):
        for z in range(z0, z1 + 1):
            if lo < x < hi and z0 < z < z1:
                continue
            if x in (lo, hi) and z in (z0, z1):
                facing = "south" if z == z0 else "north"
                shape = "outer_left" if (x == lo) == (z == z0) else "outer_right"
            else:
                facing = "south" if z == z0 else "north" if z == z1 else "east" if x == lo else "west"
                shape = "straight"
            t.stairs(x, y, z, name, facing, half=half, shape=shape)


def octagon(t, y0, y1, x0, z0, size, name, props=None, cut=2, fill=None):
    """The walls of an octagon: the square of side size from x0, z0 with its corners cut back by cut, from y0 to y1;
    with fill, its inside filled too."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x0 + size):
            for z in range(z0, z0 + size):
                dx, dz = min(x - x0, x0 + size - 1 - x), min(z - z0, z0 + size - 1 - z)
                if dx + dz < cut:
                    continue
                if dx == 0 or dz == 0 or dx + dz == cut:
                    t.set(x, y, z, name, props)
                elif fill:
                    t.set(x, y, z, fill)


def windmill():
    """A windmill of Kettle Ridge: a stone tower and a body of birch boards under a dark cap, four cloth sails on the
    north side turning an axle and a shaft down to the millstone, and before its door a field of wheat and beetroot by a
    water channel. One sail has lost its cloth."""
    t = Template(15, 24, 22)
    octagon(t, 0, 0, 3, 5, 9, "cobblestone", fill="spruce_planks")
    octagon(t, 1, 7, 3, 5, 9, "stone_bricks")
    octagon(t, 8, 8, 3, 5, 9, "polished_andesite", fill="spruce_planks")
    octagon(t, 9, 9, 3, 5, 9, "stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
    octagon(t, 9, 14, 4, 6, 7, "birch_planks")
    octagon(t, 15, 15, 4, 6, 7, "spruce_planks", fill="spruce_planks")
    ring_stairs(t, 15, 3, 11, "dark_oak_stairs", z0=5, z1=13)
    ring_stairs(t, 16, 4, 10, "dark_oak_stairs", z0=6, z1=12)
    ring_stairs(t, 17, 5, 9, "dark_oak_stairs", z0=7, z1=11)
    ring_stairs(t, 18, 6, 8, "dark_oak_stairs", z0=8, z1=10)
    t.set(7, 19, 9, "dark_oak_planks")
    t.set(7, 20, 9, "dark_oak_fence")
    # the shaft from the millstone up to the axle, and the axle out through the cap to the hub
    t.set(7, 1, 9, "grindstone", {"face": "floor", "facing": "north"})
    for y in range(2, 17):
        t.log(7, y, 9, "stripped_spruce_log")
    for z in range(3, 9):
        t.log(7, 16, z, "spruce_log", "z")
    # four sails turning round the hub, cloth trailing each arm; the west one bare
    for i in range(1, 7):
        for x, y in ((7, 16 + i), (7, 16 - i), (7 - i, 16), (7 + i, 16)):
            t.set(x, y, 3, "spruce_fence")
    for i in range(2, 7):
        for dx in (1, 2):
            t.set(7 + dx, 16 + i, 3, "white_wool")
            t.set(7 - dx, 16 - i, 3, "white_wool")
            t.set(7 + i, 16 - dx, 3, "white_wool")
    # the door to the south and its path, windows on the other sides
    t.door(7, 1, 13, "spruce_door", "south")
    for y in (3, 4):
        t.set(3, y, 9, "glass_pane")
        t.set(11, y, 9, "glass_pane")
    for y in (11, 12):
        t.set(4, y, 9, "glass_pane")
        t.set(10, y, 9, "glass_pane")
        t.set(7, y, 12, "glass_pane")
    # inside: sacks and the miller's barrels below, a ladder up to the gears
    t.barrel(5, 1, 7, "up", "residual:chests/windmill")
    t.barrel(6, 1, 7, "up", "residual:chests/windmill")
    t.barrel(5, 2, 7, "up")
    t.set(9, 1, 7, "hay_block", {"axis": "y"})
    t.set(9, 1, 8, "hay_block", {"axis": "y"})
    t.set(9, 2, 7, "hay_block", {"axis": "x"})
    t.chest(9, 1, 11, "west", "residual:chests/windmill_miller")
    # the ladder up the shaft's south side, through the floor to the gears
    for y in range(2, 10):
        t.set(7, y, 10, "ladder", {"facing": "south", "waterlogged": "false"})
    t.lantern(7, 7, 7, hanging=True)
    t.lantern(7, 14, 11, hanging=True)
    # a railed stage round the body where the stone ends, and the tally board by the door
    for x in range(3, 12):
        for z in range(5, 14):
            dx, dz = min(x - 3, 11 - x), min(z - 5, 13 - z)
            if dx + dz >= 2 and (dx == 0 or dz == 0 or dx + dz == 2):
                t.set(x, 10, z, "spruce_fence")
    t.sign(6, 3, 14, "south", ["Tally board", "Top farm 410", "Brin's farm 380", "East farm 290"])
    t.sign(8, 3, 14, "south", ["North farm 300", "Low farm 220", "Windy farm 260", "The mill 3400"])
    t.set(6, 3, 8, "cobweb")
    return t


def field(t, x0, x1, z0, z1, crops, rng, ditch=None):
    """A field of farmland, watered by a ditch down its middle, its crops at every age."""
    ditch = (z0 + z1) // 2 if ditch is None else ditch
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if z == ditch:
                t.set(x, 0, z, "water", {"level": "0"})
                continue
            t.set(x, 0, z, "farmland", {"moisture": "7"})
            name, most = rng.choice(crops)
            t.set(x, 1, z, name, {"age": str(rng.randint(most // 2, most))})


def windmill_farm():
    """A farm on Kettle Ridge, as its farmers wrote of theirs: the windmill that squeaks on every turn, its tally board
    by the door; the top field of wheat, the long field of beetroot and the bottom field of potatoes and carrots, each
    with its ditch down the middle, a hedge on its north side and torches on posts along its edge; the scarecrow with a
    pumpkin for a head and an old hat; three hives by the flowers; the well in the yard; the barn and the pig pen."""
    import random
    rng = random.Random(48215537)
    t = Template(35, 24, 31)
    t.paste(windmill(), 0, 0, 0)
    # the yard and its paths
    for x in range(7, 34):
        for z in (14, 15):
            if rng.random() < 0.85:
                t.set(x, 0, z, rng.choice(["dirt_path", "dirt_path", "coarse_dirt"]))
    for z in range(16, 31):
        if rng.random() < 0.85:
            t.set(14, 0, z, rng.choice(["dirt_path", "dirt_path", "coarse_dirt"]))
    # the well: a ring of cobblestone round the water, a little roof on posts
    for x in range(17, 20):
        for z in range(6, 9):
            if (x, z) == (18, 7):
                t.set(x, 0, z, "water", {"level": "0"})
            else:
                t.set(x, 0, z, "cobblestone")
                t.set(x, 1, z, "cobblestone_wall")
    for x, z in ((17, 6), (19, 8)):
        t.set(x, 2, z, "oak_fence")
        t.set(x, 3, z, "oak_fence")
    for x in range(16, 21):
        t.slab(x, 4, 7, "oak_slab")
    t.slab(17, 4, 6, "oak_slab")
    t.slab(19, 4, 8, "oak_slab")
    t.set(18, 3, 7, "chain", {"axis": "y", "waterlogged": "false"})
    t.set(19, 1, 9, "cauldron")
    # the barn: a cobblestone base, an oak frame and spruce boards, a wide door, a dark roof; hay in its loft
    bx0, bx1, bz0, bz1 = 22, 32, 2, 10
    t.fill(bx0, 0, bz0, bx1, 0, bz1, "cobblestone")
    t.fill(bx0 + 1, 1, bz0 + 1, bx1 - 1, 5, bz1 - 1, "air")
    t.walls(bx0, 1, bz0, bx1, 1, bz1, "cobblestone")
    t.walls(bx0, 2, bz0, bx1, 5, bz1, "spruce_planks")
    for x in (bx0, 27, bx1):
        for z in (bz0, bz1):
            t.fill(x, 1, z, x, 5, z, "stripped_oak_log")
    for z in (bz0, 6, bz1):
        t.fill(bx0, 1, z, bx0, 5, z, "stripped_oak_log")
        t.fill(bx1, 1, z, bx1, 5, z, "stripped_oak_log")
    t.fill(26, 1, bz1, 28, 4, bz1, "air")
    t.fill(bx0 + 1, 4, bz0 + 1, bx1 - 1, 4, bz0 + 4, "spruce_planks")
    gable(t, 6, bx0 - 1, bx1 + 1, bz0 - 1, bz1 + 1, "dark_oak_stairs", "x", end="spruce_planks")
    for x in range(bx0 + 1, bx1):
        if x % 3:
            t.set(x, 5, bz0 + 1, "hay_block", {"axis": "x"})
    for x in range(bx0 + 1, bx0 + 5):
        t.set(x, 1, bz0 + 1, "hay_block", {"axis": "y"})
        if x < bx0 + 3:
            t.set(x, 2, bz0 + 1, "hay_block", {"axis": "y"})
    t.chest(bx1 - 1, 1, bz0 + 1, "west", "residual:chests/windmill_miller")
    t.barrel(bx1 - 1, 1, bz0 + 2, "up", "residual:chests/windmill")
    t.set(bx0 + 1, 1, bz1 - 1, "composter", {"level": "5"})
    # part of the roof's north slope has fallen in
    for x, y, z in ((26, 9, 4), (27, 9, 4), (26, 10, 5), (27, 10, 5), (28, 10, 5), (27, 8, 3)):
        t.set(x, y, z, "air")
    # the pig pen beside the barn
    for x in range(23, 32):
        t.set(x, 1, 12, "oak_fence")
        t.set(x, 1, 17, "oak_fence")
    for z in range(12, 18):
        t.set(23, 1, z, "oak_fence")
        t.set(31, 1, z, "oak_fence")
    t.set(27, 1, 12, "oak_fence_gate", {"facing": "north", "in_wall": "false", "open": "false", "powered": "false"})
    for x in range(24, 31):
        for z in range(13, 17):
            t.set(x, 0, z, "coarse_dirt" if rng.random() < 0.5 else "mud")
    t.set(29, 1, 15, "water_cauldron", {"level": "2"})
    # the top field (wheat), the long field (beetroot), the bottom field (potatoes and carrots)
    field(t, 1, 12, 17, 22, [("wheat", 7)], rng)
    field(t, 15, 33, 19, 23, [("beetroots", 3)], rng, ditch=21)
    field(t, 1, 12, 25, 29, [("potatoes", 7), ("carrots", 7)], rng)
    for x0, x1, z in ((1, 12, 16), (15, 33, 18), (1, 12, 24)):
        for x in range(x0, x1 + 1):
            t.set(x, 1, z, "oak_leaves", {"distance": "1", "persistent": "true", "waterlogged": "false"})
        for x in (x0, x1):
            t.set(x, 1, z + 1, "air")
    for x, z in ((0, 18), (0, 22), (0, 26), (13, 26), (34, 21), (13, 18)):
        t.set(x, 1, z, "oak_fence")
        t.set(x, 2, z, "torch")
    # the scarecrow in the long field, in Tam's oldest hat
    t.set(24, 1, 20, "oak_fence")
    t.set(24, 2, 20, "brown_wool")
    t.set(23, 2, 20, "oak_fence")
    t.set(25, 2, 20, "oak_fence")
    t.set(24, 3, 20, "carved_pumpkin", {"facing": "south"})
    t.set(24, 4, 20, "black_carpet")
    # three hives by the flowers at the edge of the bottom field
    for x, flower in ((16, "poppy"), (18, "dandelion"), (20, "cornflower"), (22, "poppy"), (17, "cornflower")):
        t.set(x, 0, 27 if x % 2 == 0 else 28, "grass_block", {"snowy": "false"})
        t.set(x, 1, 27 if x % 2 == 0 else 28, flower)
    for x in (16, 18, 20):
        t.set(x, 0, 26, "grass_block", {"snowy": "false"})
        t.set(x, 1, 26, "beehive", {"facing": "south", "honey_level": "3"})
    return t




def relay_tower():
    """A relay tower as the keepers wrote of theirs, and the relay house at its foot. The tower: a cobblestone plinth,
    stone walls banded at each floor, four rooms one on top of another (the store, the kitchen with the stove, the
    keeper's room with a shelf of books, and the lamp room), stairs up the inside walls with a lamp at every turn, the
    great lamp of glass and redstone ringed with copper, a railed walk round it with the bell for the coal, and a roof of
    copper gone green carrying the rod. The relay house: the keeper's room with the desk the redstone line runs down to
    and the tower book on its lectern, broken off mid-word; and the big mind's stand, its crystal gone, ringed by
    quickeners in their cooling water. A path, the woodpile, torches round the foot."""
    import random
    rng = random.Random(30817231)
    t = Template(17, 31, 24)
    lo, hi, mid, z0, z1, zm = 4, 12, 8, 2, 10, 6
    # the plinth, stepped, and the tower's walls: cobblestone below, stone bricks above, banded at each floor
    t.fill(3, 0, 1, 13, 0, 11, "cobblestone")
    ring_stairs(t, 1, 3, 13, "cobblestone_stairs", z0=1, z1=11)
    t.walls(lo, 1, z0, hi, 20, z1, "stone_bricks")
    t.walls(lo, 1, z0, hi, 2, z1, "cobblestone")
    for y in (5, 10, 15):
        t.walls(lo, y, z0, hi, y, z1, "polished_andesite")
    for x, z in ((lo, z0), (hi, z0), (lo, z1), (hi, z1)):
        t.fill(x, 1, z, x, 20, z, "polished_andesite")
    ring_stairs(t, 10, lo - 1, hi + 1, "stone_brick_stairs", half="top", z0=z0 - 1, z1=z1 + 1)
    for y in range(3, 20):
        for x in range(lo + 1, hi):
            for z in (z0, z1):
                if t.blocks.get((x, y, z), ("",))[0] == "minecraft:stone_bricks" and rng.random() < 0.12:
                    t.set(x, y, z, rng.choice(["andesite", "cobblestone", "stone"]))
    t.fill(lo + 1, 0, z0 + 1, hi - 1, 0, z1 - 1, "spruce_planks")
    for floor in (5, 10, 15, 20):
        t.fill(lo + 1, floor, z0 + 1, hi - 1, floor, z1 - 1, "spruce_planks")
    # stairs up the west or east wall of each lower room, a lantern where each run turns
    for base, x in ((0, lo + 1), (5, hi - 1), (10, lo + 1)):
        for k in range(5):
            t.stairs(x, base + 1 + k, z1 - 2 - k, "spruce_stairs", "north")
        for z in range(z0 + 3, z0 + 6):
            t.set(x, base + 5, z, "air")
        t.lantern(x, base + 4, z0 + 1, hanging=True)
    # windows two high, framed by sills, on the three sides away from the house
    for y0 in (2, 7, 12):
        for x, z, face in ((mid, z0, "north"), (lo, zm, "west"), (hi, zm, "east")):
            t.fill(x, y0, z, x, y0 + 1, z, "glass_pane")
            dx, dz = SIDES[face]
            t.stairs(x + dx, y0 - 1, z + dz, "stone_brick_stairs", {"north": "south", "south": "north", "west": "east",
                                                                     "east": "west"}[face], half="top")
    # the store
    t.barrel(9, 1, z0 + 1, "up", "residual:chests/relay_tower_store")
    t.barrel(10, 1, z0 + 1, "up", "residual:chests/relay_tower_store")
    t.barrel(10, 2, z0 + 1, "up")
    t.fill(10, 1, 8, 11, 1, 9, "coal_block")
    t.set(11, 2, 9, "coal_block")
    t.set(11, 1, z0 + 1, "hay_block", {"axis": "y"})
    # the kitchen: the stove under its brick hood, a table with two seats, water, the food barrel
    t.set(mid, 6, z0 + 1, "smoker", {"facing": "south", "lit": "false"})
    t.fill(mid, 7, z0 + 1, mid, 9, z0 + 1, "bricks")
    t.barrel(mid - 1, 6, z0 + 1, "up", "residual:chests/relay_tower_kitchen")
    t.set(mid + 1, 6, z0 + 1, "water_cauldron", {"level": "2"})
    t.set(7, 6, 7, "spruce_fence")
    t.set(7, 7, 7, "spruce_pressure_plate", {"powered": "false"})
    t.stairs(6, 6, 7, "spruce_stairs", "east")
    t.stairs(8, 6, 7, "spruce_stairs", "west")
    # the keeper's room: the bed, a shelf of books from Vell, the chest, a rug
    t.bed(8, 11, 9, "red", "east")
    t.fill(9, 11, z0 + 1, 11, 11, z0 + 1, "bookshelf")
    t.set(10, 12, z0 + 1, "bookshelf")
    t.chest(11, 11, zm, "west", "residual:chests/relay_tower_keeper")
    for x in (7, 8):
        for z in (5, 6):
            t.set(x, 11, z, "red_carpet")
    # the lamp room: glass in a copper frame all round, the great lamp of glass and redstone in the middle
    for y in range(16, 20):
        for k in range(lo + 1, hi):
            t.set(k, y, z0, "glass_pane")
            t.set(k, y, z1, "glass_pane")
        for k in range(z0 + 1, z1):
            t.set(lo, y, k, "glass_pane")
            t.set(hi, y, k, "glass_pane")
    for x, z in ((lo, z0), (hi, z0), (lo, z1), (hi, z1)):
        t.fill(x, 16, z, x, 19, z, "weathered_cut_copper")
    t.walls(lo, 20, z0, hi, 20, z1, "weathered_cut_copper")
    t.door(mid, 16, z1, "spruce_door", "south")
    t.fill(mid - 1, 16, zm - 1, mid + 1, 16, zm + 1, "polished_andesite")
    for x in (mid - 1, mid, mid + 1):
        for z in (zm - 1, zm, zm + 1):
            corner = x != mid and z != zm
            for y in (17, 18):
                t.set(x, y, z, "glass" if corner else "redstone_lamp", None if corner else {"lit": "false"})
    t.fill(mid - 1, 19, zm - 1, mid + 1, 19, zm + 1, "cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    # the walk round the lamp room on its corbels, railed, and the bell that calls for coal
    ring_stairs(t, 14, lo - 1, hi + 1, "stone_brick_stairs", half="top", z0=z0 - 1, z1=z1 + 1)
    for x in range(lo - 1, hi + 2):
        for z in range(z0 - 1, z1 + 2):
            if x in (lo - 1, hi + 1) or z in (z0 - 1, z1 + 1):
                t.slab(x, 15, z, "stone_brick_slab", "top")
                t.set(x, 16, z, "spruce_fence")
    t.set(lo - 1, 17, z0 - 1, "lantern", {"hanging": "false", "waterlogged": "false"})
    t.set(hi + 1, 17, z1 + 1, "lantern", {"hanging": "false", "waterlogged": "false"})
    t.set(lo - 1, 16, zm, "bell", {"attachment": "floor", "facing": "north", "powered": "false"})
    for y in range(3, 15):
        t.set(lo - 2, y, zm, "chain", {"axis": "y", "waterlogged": "false"})
    t.set(lo - 2, 2, zm, "barrel", {"facing": "up", "open": "false"})
    t.set(lo - 2, 1, zm, "coal_block")
    # the roof: copper gone green, in courses over the walk, the rod at the point
    for k in range(5):
        ring_stairs(t, 21 + k, lo - 1 + k, hi + 1 - k, "oxidized_cut_copper_stairs", z0=z0 - 1 + k, z1=z1 + 1 - k)
    t.set(mid, 26, zm, "oxidized_cut_copper")
    t.set(mid, 27, zm, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    t.fill(lo + 1, 20, z0 + 1, hi - 1, 20, z1 - 1, "spruce_planks")
    # the relay house at the tower's foot: stone below, a spruce frame and boards above, a dark roof, a chimney
    hx0, hx1, hz0, hz1 = 3, 13, 11, 18
    t.fill(hx0, 0, hz0, hx1, 0, hz1, "cobblestone")
    t.fill(hx0 + 1, 0, hz0 + 1, hx1 - 1, 0, hz1 - 1, "spruce_planks")
    t.fill(hx0 + 1, 1, hz0 + 1, hx1 - 1, 4, hz1 - 1, "air")
    t.walls(hx0, 1, hz0, hx1, 2, hz1, "cobblestone")
    t.walls(hx0, 3, hz0, hx1, 4, hz1, "spruce_planks")
    for x, z in ((hx0, hz0), (hx1, hz0), (hx0, hz1), (hx1, hz1), (8, hz1), (hx0, 14), (hx1, 14)):
        t.fill(x, 1, z, x, 4, z, "stripped_spruce_log")
    t.fill(hx0, 5, hz0, hx1, 5, hz1, "spruce_planks")
    for x in range(hx0, hx1 + 1):
        t.log(x, 5, hz0, "stripped_spruce_log", "x")
        t.log(x, 5, hz1, "stripped_spruce_log", "x")
    gable(t, 6, hx0 - 1, hx1 + 1, hz0, hz1 + 1, "deepslate_tile_stairs", "x", end="spruce_planks")
    # the chimney rises from the stove's hood through the roof
    t.fill(6, 2, hz1 - 1, 6, 10, hz1 - 1, "bricks")
    t.set(6, 11, hz1 - 1, "campfire", {"facing": "north", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    t.door(mid, 1, z1, "spruce_door", "south")
    t.set(mid, 1, hz0, "air")
    t.set(mid, 2, hz0, "air")
    t.door(mid, 1, hz1, "spruce_door", "north")
    for x in (5, 11):
        t.set(x, 3, hz1, "glass_pane")
        t.trapdoor(x - 1, 3, hz1 + 1, "spruce_trapdoor", "south", open_=True)
        t.trapdoor(x + 1, 3, hz1 + 1, "spruce_trapdoor", "south", open_=True)
    for z in (13, 16):
        t.set(hx0, 3, z, "glass_pane")
        t.set(hx1, 3, z, "glass_pane")
    # the keeper's side: the desk where the redstone line from the lamp comes down, the tower book on its lectern
    t.set(5, 1, 12, "lectern", {"facing": "south", "has_book": "true", "powered": "false"},
          {"id": "minecraft:lectern", "Page": 0, "Book": {"id": "minecraft:written_book", "count": 1, "components": {
              "minecraft:written_book_content": {"title": {"raw": "Tower book"}, "author": "the keeper", "resolved": 1,
                                                 "pages": [{"raw": p} for p in TOWER_BOOK]}}}})
    for x in (6, 7):
        t.set(x, 1, 12, "redstone_wire", {"east": "side", "west": "side", "north": "none", "south": "none", "power": "0"})
    t.set(4, 1, 13, "barrel", {"facing": "up", "open": "false"})
    t.bed(4, 1, 15, "blue", "south")
    t.set(6, 1, 17, "furnace", {"facing": "north", "lit": "false"})
    t.lantern(6, 4, 15, hanging=True)
    # the big mind's side: its stand, the crystal gone, ringed by quickeners sunk in the floor with their cooling water
    t.set(11, 0, 14, "polished_andesite")
    t.set(11, 1, 14, "residual:model_server", {"facing": "west", "loaded": "false", "serving": "false"})
    for x, z in ((10, 13), (12, 13), (10, 15), (12, 15)):
        t.set(x, 0, z, "residual:accelerator")
    for x, z in ((11, 13), (11, 15), (10, 14), (12, 14)):
        t.set(x, 0, z, "water", {"level": "0"})
    t.lantern(11, 4, 14, hanging=True)
    # outside: the path from the door, the woodpile under its slab roof, torches round the foot
    for z in range(hz1 + 1, 24):
        for x in (7, 8, 9):
            if rng.random() < 0.8:
                t.set(x, 0, z, rng.choice(["dirt_path", "dirt_path", "gravel", "coarse_dirt"]))
    for z in range(12, 18):
        for y in (1, 2):
            if z != 17 or y == 1:
                t.log(1, y, z, "spruce_log", "z")
        t.slab(1, 3, z, "spruce_slab")
        t.slab(2, 3, z, "spruce_slab")
    for x, z in ((2, 0), (14, 0), (14, 12), (2, 19), (14, 19)):
        t.set(x, 0, z, "cobblestone")
        t.set(x, 1, z, "spruce_fence")
        t.set(x, 2, z, "torch")
    t.suspicious(6, 0, 20, "gravel", "residual:archaeology/ruin")
    t.set(10, 18, 8, "cobweb")
    t.set(6, 14, 8, "cobweb")
    return t


# the tower book, as the last keeper left it
TOWER_BOOK = [
    "Night 11. Lamp lit at dusk. Blinks from the south all night. Fair. Coal: 31 sacks.",
    "Night 12. Wind from the north. Blinks from the south till the second lamp change, then quiet. Sent again. "
    "Nothing.",
    "Night 13. Quiet from the south. Sent our call every hour. Lamp glass cleaned twice.",
    "Night 14. Lamp",
]


def gable(t, y, x0, x1, z0, z1, name, ridge_axis="x", end=None):
    """A gable roof over x0..x1 by z0..z1 from y, its ridge along ridge_axis, each course a row of stairs a step in from
    the last; with end, the gable ends filled with that block."""
    k = 0
    while True:
        a0, a1 = (z0 + k, z1 - k) if ridge_axis == "x" else (x0 + k, x1 - k)
        if a0 > a1:
            break
        for u in range(x0, x1 + 1) if ridge_axis == "x" else range(z0, z1 + 1):
            for a, facing in ((a0, "south" if ridge_axis == "x" else "east"), (a1, "north" if ridge_axis == "x" else "west")):
                x, z = (u, a) if ridge_axis == "x" else (a, u)
                if a0 == a1:
                    t.slab(x, y + k, z, name.replace("_stairs", "_slab"))
                else:
                    t.stairs(x, y + k, z, name, facing)
        if end:
            for a in range(a0 + 1, a1):
                for u in ((x0 + 1, x1 - 1) if ridge_axis == "x" else (z0 + 1, z1 - 1)):
                    x, z = (u, a) if ridge_axis == "x" else (a, u)
                    t.set(x, y + k, z, end)
        k += 1


def stilt_house():
    """Wren's house at Lowmarsh: a healer's cottage of mud brick between mangrove posts, on a railed spruce deck on
    mangrove stilts over the marsh, under a mangrove roof gone green with moss, a boardwalk to the bank with a lamp on its landing, and inside her remedies:
    the brewing stand and the cauldron on the bench, herbs in pots, the stove, the bed, her chest and lectern. It stands
    on the marsh floor, and the water round its stilts stays."""
    t = Template(13, 13, 20)
    deck = 4
    for x, z in ((1, 1), (6, 1), (11, 1), (1, 5), (11, 5), (1, 9), (6, 9), (11, 9)):
        for y in range(deck):
            t.log(x, y, z, "mangrove_log")
    t.fill(1, deck, 1, 11, deck, 9, "spruce_planks")
    for k in range(1, 12):
        t.set(k, deck + 1, 1, "spruce_fence")
        t.set(k, deck + 1, 9, "spruce_fence")
    for k in range(2, 9):
        t.set(1, deck + 1, k, "spruce_fence")
        t.set(11, deck + 1, k, "spruce_fence")
    # the cottage: mud brick walls between posts of stripped mangrove, a ceiling, a gable roof with moss on the eaves
    x0, x1, z0, z1 = 3, 9, 2, 7
    t.fill(x0 + 1, deck + 1, z0 + 1, x1 - 1, deck + 3, z1 - 1, "air")
    t.walls(x0, deck + 1, z0, x1, deck + 3, z1, "mud_bricks")
    for x, z in ((6, z0), (6, z1)):
        for y in range(deck + 1, deck + 4):
            t.log(x, y, z, "stripped_mangrove_log")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(deck + 1, deck + 4):
            t.log(x, y, z, "stripped_mangrove_log")
    t.fill(x0 + 1, deck + 4, z0 + 1, x1 - 1, deck + 4, z1 - 1, "mangrove_planks")
    gable(t, deck + 4, x0 - 1, x1 + 1, z0 - 1, z1 + 1, "mangrove_stairs", "x", end="mangrove_planks")
    for x in range(x0 - 1, x1 + 2, 2):
        t.set(x, deck + 5, z0 - 1, "moss_carpet")
        t.set(x, deck + 5, z1 + 1, "moss_carpet")
    t.door(6, deck + 1, z1, "mangrove_door", "south")
    for x in (4, 8):
        t.set(x, deck + 2, z0, "glass_pane")
        t.set(x, deck + 2, z1, "glass_pane")
    t.set(x0, deck + 2, 4, "glass_pane")
    t.set(x1, deck + 2, 4, "glass_pane")
    # inside: the bench along the north wall, herbs in pots, the bed, her chest and lectern
    t.set(4, deck + 1, 3, "barrel", {"facing": "up", "open": "false"},
          {"id": "minecraft:barrel", "LootTable": "residual:chests/stilt_house_remedies"})
    t.set(5, deck + 1, 3, "brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
    t.set(6, deck + 1, 3, "cauldron")
    t.set(7, deck + 1, 3, "smoker", {"facing": "south", "lit": "false"})
    t.set(8, deck + 1, 3, "barrel", {"facing": "up", "open": "false"})
    t.set(4, deck + 2, 3, "potted_blue_orchid")
    t.set(7, deck + 2, 3, "potted_red_mushroom")
    t.set(8, deck + 2, 3, "potted_fern")
    t.bed(5, deck + 1, 6, "green", "west")
    t.chest(8, deck + 1, 6, "west", "residual:chests/stilt_house_wren")
    t.set(8, deck + 1, 5, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    t.lantern(6, deck + 3, 5, hanging=True)
    # the deck: a bench by the wall, the barrel of the day's fish, a lamp
    t.barrel(2, deck + 1, 8, "up", "residual:chests/stilt_house_fish")
    t.stairs(10, deck + 1, 3, "spruce_stairs", "west")
    t.stairs(10, deck + 1, 4, "spruce_stairs", "west")
    t.lantern(2, deck + 1, 2)
    # the boardwalk to the bank on log posts, a landing with a lamp on its post halfway
    t.set(6, deck + 1, 9, "air")
    for z in range(10, 20):
        t.set(6, deck, z, "spruce_planks")
    for z in (13, 17):
        for y in range(deck):
            t.log(6, y, z, "mangrove_log")
    t.set(7, deck, 13, "spruce_planks")
    for y in range(deck):
        t.log(7, y, 13, "mangrove_log")
    t.set(7, deck + 1, 13, "spruce_fence")
    t.set(7, deck + 2, 13, "spruce_fence")
    t.lantern(7, deck + 3, 13)
    return t


def slipway():
    """A slip at Saltreach, where the ships were built: a stone quay at the water's edge with a shipwright's shed and
    stacks of timber, and running down from it into the sea the slip, on it the frame of a ship never finished: a keel
    and ribs of spruce, half planked. A crane leans over it. Its sister slip beside it is empty."""
    t = Template(23, 16, 28)
    q = 4  # the quay's top, four above the template's floor, which sits below the sea
    # the quay: stone bricks to the water, its face down to the sea floor
    t.fill(0, 0, 0, 22, q, 9, "stone_bricks")
    t.fill(0, q, 0, 22, q, 9, "stone_bricks")
    for x in range(0, 23, 4):
        t.set(x, q + 1, 9, "stone_brick_wall")
    # the shipwright's shed on the quay: stone below, spruce above, a door to the slip
    x0, x1, z0, z1 = 1, 7, 1, 6
    t.fill(x0, q + 1, z0, x1, q + 4, z1, "air")
    t.walls(x0, q + 1, z0, x1, q + 2, z1, "stone_bricks")
    t.walls(x0, q + 3, z0, x1, q + 4, z1, "spruce_planks")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(q + 1, q + 5):
            t.log(x, y, z, "spruce_log")
    gable(t, q + 5, x0 - 1, x1 + 1, z0 - 1, z1 + 1, "spruce_stairs", "z", end="spruce_planks")
    t.door(4, q + 1, z1, "spruce_door", "south")
    t.set(x0, q + 3, 3, "glass_pane")
    t.set(x1, q + 3, 3, "glass_pane")
    t.chest(2, q + 1, 2, "east", "residual:chests/slipway_shipwright")
    t.set(3, q + 1, 2, "crafting_table")
    t.set(6, q + 1, 2, "barrel", {"facing": "up", "open": "false"}, {"id": "minecraft:barrel",
                                                                     "LootTable": "residual:chests/slipway_stores"})
    t.set(6, q + 1, 5, "cobweb")
    t.set(2, q + 4, 5, "cobweb")
    t.lantern(4, q + 4, 3, hanging=True)
    # timber stacked on the quay
    for z in (2, 3, 4):
        t.fill(9, q + 1, z, 12, q + 1, z, "stripped_spruce_log", {"axis": "x"})
    t.fill(9, q + 2, 3, 12, q + 2, 3, "stripped_spruce_log", {"axis": "x"})
    # the slip: a level stage of spruce on posts out over the water, and on it the frame of a ship: a keel, ribs that
    # flare toward the gunwale, a stem at the bow and a transom at the stern, its lowest strakes planked
    k, bow, stern = 15, 23, 11
    for z in range(10, 26):
        for x in range(k - 4, k + 5):
            t.slab(x, q, z, "spruce_slab", "top")
    for z in range(13, 26, 4):
        for x in (k - 4, k + 4):
            for y in range(q):
                t.log(x, y, z, "spruce_log")
    for z in range(stern, bow + 1):
        t.log(k, q + 1, z, "spruce_log", "z")
    for z in range(stern + 1, bow, 2):
        narrow = z in (stern + 1, bow - 1)
        for d in (-1, 1):
            rib = ((1, 2), (2, 3), (2, 4), (2, 5)) if narrow else ((1, 2), (2, 3), (2, 4), (3, 5), (3, 6))
            for dx, dy in rib:
                t.log(k + d * dx, q + dy, z, "stripped_spruce_log")
    for z in range(stern + 2, bow - 1):
        for d in (-1, 1):
            t.log(k + 3 * d, q + 6, z, "spruce_log", "z")
            if (k + d, q + 2, z) not in t.blocks:
                t.set(k + d, q + 2, z, "spruce_planks")
            if (k + 2 * d, q + 3, z) not in t.blocks:
                t.set(k + 2 * d, q + 3, z, "spruce_planks")
    for d in (-1, 1):
        t.log(k + 2 * d, q + 6, stern + 1, "spruce_log", "z")
        t.log(k + 2 * d, q + 6, bow - 1, "spruce_log", "z")
        t.log(k + d, q + 6, bow, "spruce_log", "z")
    for y in range(q + 2, q + 8):
        t.log(k, y, bow + 1 if y > q + 3 else bow, "spruce_log")
    t.fill(k - 2, q + 3, stern, k + 2, q + 6, stern, "spruce_planks")
    # the crane: a post at the quay's edge, its jib out over the slip's side, a chain hanging with a lamp on it
    for y in range(q + 1, q + 11):
        t.log(20, y, 9, "spruce_log")
    for z in range(9, 16):
        t.log(20, q + 10, z, "spruce_log", "z")
    for y in range(q + 6, q + 10):
        t.set(20, y, 15, "chain", {"axis": "y", "waterlogged": "false"})
    t.lantern(20, q + 5, 15, hanging=True)
    # the empty slip to the west: its ramp, and nothing on it
    for z in range(10, 22):
        y = max(0, q - (z - 10) // 3)
        for x in range(3, 8):
            if (x + z) % 5:
                t.slab(x, y, z, "spruce_slab", "top")
    return t

def old_road(t, x0, x1, z0, z1, rng, along="z"):
    """A stretch of the Long Road as Dace laid it on the open road, running along z or x: packed earth between
    cobblestone edges, worn and broken, grass coming through in places."""
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) if along == "z" else z in (z0, z1)
            r = rng.random()
            if edge:
                if r < 0.75:
                    t.set(x, 0, z, "mossy_cobblestone" if r < 0.25 else "cobblestone")
            elif r < 0.85:
                t.set(x, 0, z, "dirt_path" if r < 0.45 else "coarse_dirt" if r < 0.7 else "gravel")


def waymark(t, x, z, face="west"):
    """Dunn's waymark: a pillar of stone three blocks high, its figure cut in the middle stone, a torch on top, little
    stones left at its foot."""
    t.set(x, 0, z, "cobblestone")
    t.set(x, 1, z, "stone_bricks")
    t.set(x, 2, z, "chiseled_stone_bricks")
    t.set(x, 3, z, "stone_bricks")
    t.set(x, 4, z, "torch")
    dx, dz = SIDES[face]
    for ox, oz in ((dx, dz + 1), (dx, dz - 1), (-dz, dx)):
        t.set(x + ox, 1, z + oz, "stone_button", {"face": "floor", "facing": face, "powered": "false"})
    t.suspicious(x + dx, 0, z + dz, "gravel", "residual:archaeology/waymark")


def waymark_road():
    """A waymark by a stretch of the old road, a torch post along it."""
    import random
    t = Template(9, 6, 13)
    old_road(t, 1, 5, 0, 12, random.Random(7))
    waymark(t, 7, 6)
    t.set(7, 0, 0, "cobblestone")
    t.set(7, 1, 0, "oak_fence")
    t.set(7, 2, 0, "oak_fence")
    t.set(7, 3, 0, "torch")
    return t


def waymark_north():
    """The waymark on the north road between tower two and Northpoint, under its little roof, with the two notes left on
    it: nobody knows which came first."""
    import random
    t = Template(9, 6, 13)
    old_road(t, 1, 5, 0, 12, random.Random(11))
    waymark(t, 7, 6)
    for z in (5, 7):
        t.set(8, 1, z, "spruce_fence")
        t.set(8, 2, z, "spruce_fence")
        t.set(8, 3, z, "spruce_fence")
    for z in (4, 5, 6, 7, 8):
        for x in (6, 7, 8):
            if (x, z) != (7, 6):
                t.slab(x, 4, z, "spruce_slab")
    t.set(7, 4, 6, "spruce_planks")
    t.set(7, 5, 6, "torch")
    t.sign(6, 3, 6, "west", ["Gone south", "to tower two", "", "M."])
    t.sign(6, 1, 6, "west", ["Gone north", "to Northpoint", "", "T."])
    t.set(8, 1, 6, "barrel", {"facing": "up", "open": "false"},
          {"id": "minecraft:barrel", "LootTable": "residual:chests/waymark_north"})
    return t

def tutor_house():
    """A tutor house, where minds were drilled and tested: a hall of tuff with a cornice of copper and a relay on its
    roof, the apprentices still standing in their row before the tutor's bench, the drill box beside the crystal; the
    teaching room with its sunny window and the tray of crystals, each on its card; the apprentice hall, its apprentices
    stopped at their stations, the dry box and the jar; and the drill yard ringed with torches, its boards drawn as
    small fields of green and gold."""
    t = Template(25, 11, 22)
    # the drill hall and the teaching room: tuff bricks between pilasters, high windows, a flat roof behind a parapet
    x0, x1, z0, z1 = 1, 23, 1, 9
    t.fill(x0, 0, z0, x1, 0, z1, "polished_tuff")
    t.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, "spruce_planks")
    t.fill(x0 + 1, 1, z0 + 1, x1 - 1, 6, z1 - 1, "air")
    t.walls(x0, 1, z0, x1, 6, z1, "tuff_bricks")
    t.walls(x0, 1, z0, x1, 1, z1, "polished_tuff")
    t.walls(15, 1, z0, 15, 6, z1, "tuff_bricks")
    for x in range(x0, x1 + 1, 4):
        for z in (z0, z1):
            t.fill(x, 1, z, x, 6, z, "chiseled_tuff_bricks" if x in (x0, x1) else "polished_tuff")
    for x in range(x0 + 1, x1):
        for z in (z0, z1):
            if (x - x0) % 4 and x != 15:
                t.fill(x, 4, z, x, 5, z, "glass_pane")
    t.fill(x0, 7, z0, x1, 7, z1, "polished_tuff")
    ring_stairs(t, 7, x0 - 1, x1 + 1, "weathered_cut_copper_stairs", half="top", z0=z0 - 1, z1=z1 + 1)
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            t.set(x, 8, z, "tuff_brick_wall")
    for z in range(z0, z1 + 1):
        t.set(x0, 8, z, "tuff_brick_wall")
        t.set(x1, 8, z, "tuff_brick_wall")
    t.set(8, 8, 5, "residual:relay")
    # the drill hall: the apprentices in their row, the tutor's bench with the drill box and the crystal
    for k, x in enumerate(range(3, 14, 2)):
        t.apprentice(x, 1, 2, 0)
    t.set(14, 1, 5, "residual:bench")
    t.barrel(14, 1, 4, "up", "residual:chests/tutor_house")
    t.set(14, 1, 6, "polished_tuff")
    t.set(14, 2, 6, "amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    for x in (4, 8, 12):
        t.lantern(x, 6, 5, hanging=True)
    t.door(8, 1, z1, "spruce_door", "south")
    t.door(15, 1, 5, "spruce_door", "east")
    # the teaching room: the sunny window, the tray of crystals each on its card, a cushion for the cat
    t.fill(x1, 2, 4, x1, 4, 6, "glass_pane")
    t.fill(18, 1, 3, 20, 1, 3, "spruce_planks")
    for x, card in ((18, "Farm."), (19, "Build."), (20, "Store.")):
        t.set(x, 2, 3, "amethyst_cluster", {"facing": "up", "waterlogged": "false"})
        t.sign(x, 1, 4, "south", [card])
    t.fill(16, 1, 8, 22, 1, 8, "bookshelf")
    t.set(22, 1, 5, "red_carpet")
    t.chest(16, 1, 2, "east", "residual:chests/tutor_house")
    t.lantern(19, 6, 5, hanging=True)
    # the apprentice hall: its apprentices stopped at their stations, the dry box, the blue jar and the note
    a0, a1, b0, b1 = 1, 9, 10, 18
    t.fill(a0, 0, b0, a1, 0, b1, "polished_tuff")
    t.fill(a0 + 1, 0, b0 + 1, a1 - 1, 0, b1 - 1, "spruce_planks")
    t.fill(a0 + 1, 1, b0 + 1, a1 - 1, 4, b1 - 1, "air")
    t.walls(a0, 1, b0, a1, 4, b1, "tuff_bricks")
    t.walls(a0, 1, b0, a1, 1, b1, "polished_tuff")
    for x, z in ((a0, b0), (a1, b0), (a0, b1), (a1, b1)):
        t.fill(x, 1, z, x, 4, z, "polished_tuff")
    t.fill(a0, 5, b0, a1, 5, b1, "polished_tuff")
    ring_stairs(t, 5, a0 - 1, a1 + 1, "weathered_cut_copper_stairs", half="top", z0=b0, z1=b1 + 1)
    for z in (13, 15):
        t.fill(a0, 2, z, a0, 3, z, "glass_pane")
    t.door(a1, 1, 14, "spruce_door", "east")
    t.apprentice(3, 1, 12, 0)
    t.apprentice(3, 1, 16, 180)
    t.apprentice(6, 1, 17, 180)
    t.set(2, 1, b0 + 1, "anvil", {"facing": "east"})
    t.set(4, 1, b0 + 1, "grindstone", {"face": "floor", "facing": "south"})
    t.chest(a1 - 1, 1, b1 - 1, "west", "residual:chests/tutor_dry_box")
    t.set(a1 - 1, 1, b1 - 2, "decorated_pot", {"facing": "west", "cracked": "false", "waterlogged": "false"})
    t.sign(5, 2, b1 - 1, "north", ["Oil is in the", "jar. Spare", "crystals are in", "the dry box."])
    t.lantern(5, 4, 14, hanging=True)
    # the drill yard: fenced, torches round it, three boards drawn as small fields
    for x in range(11, 24):
        t.set(x, 1, 21, "spruce_fence")
    for z in range(10, 22):
        t.set(11, 1, z, "spruce_fence")
        t.set(23, 1, z, "spruce_fence")
    for x, z in ((11, 10), (11, 21), (23, 10), (23, 21), (17, 21)):
        t.set(x, 2, z, "torch")
    for bx, bz in ((13, 12), (18, 12), (13, 16)):
        t.fill(bx - 1, 0, bz - 1, bx + 4, 0, bz + 3, "spruce_planks")
        for x in range(bx, bx + 4):
            t.set(x, 1, bz, "lime_carpet")
            t.set(x, 1, bz + 1, "yellow_carpet")
            t.set(x, 1, bz + 2, "lime_carpet")
    t.stairs(19, 1, 17, "spruce_stairs", "north")
    t.stairs(20, 1, 17, "spruce_stairs", "north")
    t.set(8, 1, 10, "air")
    t.set(6, 4, 9, "cobweb")
    t.set(21, 5, 2, "cobweb")
    return t

def fallen_relay_mast():
    """A relay mast that fell, long ago: the bowl it broke in the ground, grassed over at the rim; the mast lying
    across it in three pieces, its copper gone green, its relay half buried at the far end; and its drive housing split
    open, the drives of writing it carried still inside."""
    import math
    import random
    rng = random.Random(40211973)
    t = Template(19, 10, 19)
    cx, cz, ground, r0 = 9, 9, 5, 8.5

    def floor(x, z):
        r = math.hypot(x - cx, z - cz)
        return ground - round(4 * (1 - (r / r0) ** 2)) if r <= r0 else None

    for x in range(19):
        for z in range(19):
            f = floor(x, z)
            if f is None:
                continue
            for y in range(f + 1, 10):
                t.set(x, y, z, "air")
            for y in range(max(0, f - 2), f):
                t.set(x, y, z, "stone" if rng.random() < 0.6 else "tuff")
            p = rng.random()
            rim = math.hypot(x - cx, z - cz) > r0 - 2
            if rim and p < 0.55:
                t.set(x, f, z, "grass_block", {"snowy": "false"})
                if rng.random() < 0.35:
                    t.set(x, f + 1, z, "short_grass")
            else:
                t.set(x, f, z, "coarse_dirt" if p < 0.3 else "gravel" if p < 0.5 else "cobblestone" if p < 0.65
                      else "tuff" if p < 0.8 else "dirt")
                if rng.random() < 0.08:
                    t.set(x, f + 1, z, "moss_carpet")
    # the mast, lying across the bowl in three pieces, rods still standing off it
    for x in range(1, 18):
        if x in (6, 7, 12):
            continue
        f = floor(x, 9)
        if f is None:
            continue
        t.set(x, f + 1, 9, "oxidized_copper_grate", {"waterlogged": "false"})
        if x % 3 == 0:
            t.set(x, f + 2, 9, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
        if x % 4 == 1:
            t.set(x, f + 1, 10, "oxidized_cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(16, floor(16, 9), 9, "residual:relay")
    for x, z in ((4, 6), (11, 13), (14, 5), (7, 12)):
        t.set(x, floor(x, z) + 1, z, "oxidized_cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    # the drive housing, split open, its chest of drives inside
    f = floor(9, 7)
    t.fill(8, f + 1, 6, 10, f + 2, 7, "weathered_cut_copper")
    t.set(9, f + 1, 7, "air")
    t.set(9, f + 2, 7, "air")
    t.set(10, f + 2, 6, "air")
    t.chest(9, f + 1, 7, "south", "residual:chests/fallen_relay_mast")
    return t


SEALING_BOOK = [
    "The sealing book. Each stack is closed when it is full, or when the council says.",
    "East stacks: shelves 1 to 40, boxed in waxed cloth in number order. Door closed with clay. O.",
    "North stacks: shelves 41 to 77. Door closed with clay. The list of every number is just inside the door. O.",
    "The great doors: not yet. The lamp is on the long table, by the reading room window, for whoever comes.",
]


def sealed_stacks():
    """Stacks of the Archive sealed in its last days, deep underground with no way in but its wall of clay: shelves
    behind doors, wax and the archive's seal, boxes of records in waxed cloth in number order, the sealing book on its
    lectern, and a lamp left on the long table for whoever comes."""
    t = Template(13, 7, 16)
    t.fill(0, 0, 0, 12, 6, 15, "deepslate_tiles")
    t.fill(1, 1, 1, 11, 5, 14, "air")
    t.fill(1, 0, 1, 11, 0, 14, "polished_tuff")
    for z in range(1, 15, 3):
        t.fill(0, 1, z, 0, 5, z, "polished_deepslate")
        t.fill(12, 1, z, 12, 5, z, "polished_deepslate")
    # shelf bays along both long walls: full shelves behind trapdoor doors, sealed with wax and the archive's seal
    shelf = {f"slot_{i}_occupied": "true" for i in range(6)}
    for z in range(2, 14):
        if (z - 1) % 3 == 0:
            continue
        for x, face in ((1, "east"), (11, "west")):
            for y in (1, 2, 3):
                t.set(x, y, z, "chiseled_bookshelf", {"facing": face, **shelf})
            t.trapdoor(x + (1 if face == "east" else -1), 2, z, "spruce_trapdoor", face, open_=True)
            t.set(x, 4, z, "honeycomb_block")
    for z in (3, 6, 9, 12):
        t.set(0, 4, z, "chiseled_tuff")
        t.set(12, 4, z, "chiseled_tuff")
    # boxes in waxed cloth down the middle, in number order
    for z in range(3, 11, 2):
        t.barrel(5, 1, z, "up", "residual:chests/sealed_stacks")
        t.set(5, 2, z, "white_carpet")
        t.set(7, 1, z, "barrel", {"facing": "up", "open": "false"})
        t.set(7, 2, z, "white_carpet")
    # the long table, the lamp left burning on it, and the sealing book
    for x in (4, 5, 6, 7, 8):
        t.slab(x, 1, 13, "spruce_slab", "top")
    t.lantern(6, 2, 13)
    t.set(8, 1, 12, "lectern", {"facing": "north", "has_book": "true", "powered": "false"},
          {"id": "minecraft:lectern", "Page": 0, "Book": {"id": "minecraft:written_book", "count": 1, "components": {
              "minecraft:written_book_content": {"title": {"raw": "The sealing book"}, "author": "O.", "resolved": 1,
                                                 "pages": [{"raw": p} for p in SEALING_BOOK]}}}})
    # the way in, closed with clay
    t.fill(5, 1, 15, 7, 3, 15, "clay")
    t.fill(5, 1, 14, 7, 3, 14, "clay")
    for x in (3, 9):
        t.lantern(x, 5, 7, hanging=True)
    t.set(2, 5, 13, "cobweb")
    return t

WAYMARK_BOOK = [
    "The waymark book, kept for the keepers of the Long Road behind the counter of the Halfway Inn.",
    "Dunn's stretch: from the bridge to this door. Figures 0 to 9, each cut once. Stones on top of every one.",
    "Spring: the 4 by the old mill track leans. Set it up again, and a new torch. Hobb paid for the torch in soup.",
    "Leaffall: all well. The 7 has a fox carved under its figure now. Nobody will say who did it.",
]


def halfway_inn():
    """The Halfway Inn on the Long Road, a day from anywhere: stone below and an oak frame above, its upper floor
    jutting over the road side, a steep roof and a chimney; the common room with its long tables and the hearth that
    has not gone out, the riddle board by the fire, the counter with the waymark book and a pie, the kitchen and its soup;
    rooms upstairs; the stable across the yard; its sign on a post by the road, and a waymark."""
    import random
    rng = random.Random(30091277)
    t = Template(25, 15, 23)
    x0, x1, z0, z1 = 2, 16, 2, 11
    # the ground floor: a stone base, an oak frame, plaster between
    t.fill(x0, 0, z0, x1, 0, z1, "cobblestone")
    t.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, "spruce_planks")
    t.fill(x0 + 1, 1, z0 + 1, x1 - 1, 4, z1 - 1, "air")
    t.walls(x0, 1, z0, x1, 1, z1, "stone_bricks")
    t.walls(x0, 2, z0, x1, 4, z1, "calcite")
    for x in range(x0, x1 + 1, 4):
        for z in (z0, z1):
            t.fill(x, 1, z, x, 4, z, "stripped_oak_log")
    for z in (z0, 6, z1):
        t.fill(x0, 1, z, x0, 4, z, "stripped_oak_log")
        t.fill(x1, 1, z, x1, 4, z, "stripped_oak_log")
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            t.log(x, 5, z, "stripped_oak_log", "x")
    for z in range(z0, z1 + 1):
        t.log(x0, 5, z, "stripped_oak_log", "z")
        t.log(x1, 5, z, "stripped_oak_log", "z")
    t.fill(x0 + 1, 5, z0 + 1, x1 - 1, 5, z1 - 1, "spruce_planks")
    for x in range(x0 + 1, x1):
        if (x - x0) % 4 and x not in (8, 9):
            t.set(x, 2, z1, "glass_pane")
            t.set(x, 3, z1, "glass_pane")
    t.door(8, 1, z1, "oak_door", "south", hinge="left")
    t.door(9, 1, z1, "oak_door", "south", hinge="right")
    # the upper floor juts a block out over the road side, on corbels; plaster between oak posts
    t.fill(x0, 6, z0, x1, 8, z1 + 1, "air")
    t.walls(x0, 6, z0, x1, 8, z1 + 1, "calcite")
    for x in range(x0, x1 + 1, 4):
        for z in (z0, z1 + 1):
            t.fill(x, 6, z, x, 8, z, "stripped_oak_log")
    for x in range(x0, x1 + 1):
        t.stairs(x, 5, z1 + 1, "oak_stairs", "north", half="top")
        if (x - x0) % 4 and x % 2:
            t.set(x, 7, z1 + 1, "glass_pane")
            t.set(x, 7, z0, "glass_pane")
    t.fill(x0 + 1, 6, z0 + 1, x1 - 1, 8, z1, "air")
    t.fill(x0 + 1, 9, z0 + 1, x1 - 1, 9, z1, "spruce_planks")
    gable(t, 9, x0 - 1, x1 + 1, z0 - 1, z1 + 2, "dark_oak_stairs", "x", end="calcite")
    # the hearth that has not gone out, its chimney, the riddle board by the fire
    t.fill(x0 + 1, 1, 5, x0 + 1, 4, 8, "bricks")
    t.set(x0 + 1, 1, 6, "campfire", {"facing": "east", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    t.set(x0 + 1, 1, 7, "campfire", {"facing": "east", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    t.fill(x0 + 1, 2, 6, x0 + 1, 2, 7, "air")
    t.fill(x0, 1, 6, x0, 13, 7, "bricks")
    t.sign(x0 + 1, 3, 4, "east", ["I come to every", "supper at the", "inn and I never", "pay. What am I?"])
    t.sign(x0 + 1, 3, 9, "east", ["What gets", "shorter every", "time you walk", "on it?"])
    # the common room: two long tables with their seats
    for z in (5, 8):
        for x in range(6, 11):
            t.slab(x, 1, z, "spruce_slab", "top")
            t.stairs(x, 1, z - 1 if z == 5 else z + 1, "spruce_stairs", "south" if z == 5 else "north")
    for x in (5, 8, 11):
        t.lantern(x, 4, 6, hanging=True)
    # the counter, the waymark book behind it next to the pie; cider barrels; the kitchen and its soup
    for z in range(z0 + 1, 8):
        t.set(12, 1, z, "spruce_planks")
    t.set(12, 2, 4, "cake", {"bites": "2"})
    t.set(13, 1, 4, "lectern", {"facing": "west", "has_book": "true", "powered": "false"},
          {"id": "minecraft:lectern", "Page": 0, "Book": {"id": "minecraft:written_book", "count": 1, "components": {
              "minecraft:written_book_content": {"title": {"raw": "Waymark book"}, "author": "Dunn", "resolved": 1,
                                                 "pages": [{"raw": p} for p in WAYMARK_BOOK]}}}})
    for z in (3, 5):
        t.barrel(15, 1, z, "west", "residual:chests/inn_cellar")
        t.barrel(15, 2, z, "west")
    t.set(15, 1, 7, "smoker", {"facing": "west", "lit": "false"})
    t.set(15, 1, 8, "water_cauldron", {"level": "3"})
    t.set(14, 1, 9, "barrel", {"facing": "up", "open": "false"})
    # the stairs up, and the rooms: beds, chests, a lantern each
    for k in range(4):
        t.stairs(15 - k, 2 + k, 10, "spruce_stairs", "west")
    for x in (12, 13, 14, 15):
        t.set(x, 5, 10, "air")
    for x in (7, 11):
        t.fill(x, 6, z0 + 1, x, 8, 6, "spruce_planks")
    for bx in (4, 8):
        t.bed(bx, 6, z0 + 1, "red", "south")
        t.bed(bx + 1, 6, z0 + 1, "red", "south")
    t.bed(12, 6, z0 + 1, "blue", "south")
    t.chest(5, 6, 6, "north", "residual:chests/inn_rooms")
    t.chest(13, 6, 6, "north", "residual:chests/inn_rooms")
    for x in (5, 9, 13):
        t.lantern(x, 8, 5, hanging=True)
    # the stable across the yard: posts, a sloping roof, hay, a trough
    for x in (18, 21, 23):
        for z in (3, 10):
            t.fill(x, 1, z, x, 3, z, "oak_log")
    for x in range(18, 24):
        for z in range(3, 11):
            t.slab(x, 4, z, "spruce_slab")
    t.fill(23, 1, 4, 23, 2, 9, "spruce_planks")
    t.fill(19, 1, 4, 22, 1, 4, "hay_block", {"axis": "x"})
    t.set(21, 1, 8, "water_cauldron", {"level": "2"})
    for z in range(5, 10):
        t.set(20, 1, z, "oak_fence")
    # the road past the door, the inn's sign on its post, and a waymark
    old_road(t, 0, 24, 16, 20, rng, along="x")
    for z in (12, 13, 14, 15):
        for x in (8, 9):
            t.set(x, 0, z, "dirt_path")
    t.set(6, 0, 14, "cobblestone")
    t.set(6, 1, 14, "oak_fence")
    t.post_sign(6, 2, 14, 0, ["The Halfway", "Inn", "Beds, soup,", "hay"])
    waymark(t, 21, 14, face="south")
    t.set(10, 7, 4, "cobweb")
    t.set(4, 3, 3, "cobweb")
    return t

def weathered(*rules, integrity=0.82):
    """A ruin's processors: each (block, its weathered form, the share that weathered) as a rule, then the decay that
    takes out some of the blocks tagged ruin_rot."""
    rule = {"processor_type": "minecraft:rule", "rules": [
        {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": f"minecraft:{a}",
                             "probability": p},
         "location_predicate": {"predicate_type": "minecraft:always_true"},
         "output_state": {"Name": f"minecraft:{b}"}} for a, b, p in rules]}
    rot = {"processor_type": "minecraft:block_rot", "integrity": integrity, "rottable_blocks": "#residual:ruin_rot"}
    return ([rule] if rules else []) + [rot]


STONE_AGE = (("stone_bricks", "mossy_stone_bricks", 0.18), ("stone_bricks", "cracked_stone_bricks", 0.14),
             ("cobblestone", "mossy_cobblestone", 0.3), ("deepslate_tiles", "cracked_deepslate_tiles", 0.15))


# what each ruin's records tell of
RELAYS = ["relay", "tower", "lamp", "keeper"]
MILLS = ["Kettle Ridge", "windmill", "miller"]
MARSH = ["Lowmarsh", "Wren", "remedy", "marsh"]
SHIPS = ["Saltreach", "shipyard", "shipwright", "Corin"]
ARCHIVES = ["archive", "clerk"]
MASTS = ["mast", "relay house"]
VAULTS = ["vault", "sealed"]
ROADS = ["waymark", "Long Road", "north road", "inn"]
TUTORS = ["tutor", "drill"]
INNS = ["Halfway Inn", "Brown Hen", "innkeeper", "the inn"]
FALLEN = ["mast", "relay line"]
SEALED = ["sealed", "the stacks", "sealing"]


class Ruin:
    """A kind of ruin. build is a function that draws its template, or a list of (weight, function), its variants."""

    def __init__(self, build, spacing, separation, salt, step="surface_structures", start=0, terrain="beard_thin",
                 biomes=None, processors=None, heightmap="WORLD_SURFACE_WG", shore=False, words=()):
        self.build, self.spacing, self.separation, self.salt = build, spacing, separation, salt
        self.step, self.start, self.terrain, self.heightmap = step, start, terrain, heightmap
        # at the water's edge: its north end on land, its south end over water (the residual:shore structure type)
        self.shore = shore
        # what a record that tells of it says: its shards are mostly such records, and reading one lets a Wayfinder
        # look for it
        self.words = list(words)
        # its own biomes, or the ruins' shared list; its own weathering, or the shared decay
        self.biomes, self.processors = biomes, processors

    def variants(self, name):
        """Each variant's weight, function and template name."""
        if callable(self.build):
            return [(1, self.build, name)]
        return [(w, f, f"{name}_{i}") for i, (w, f) in enumerate(self.build)]


RUINS = {
    "archive_house": Ruin(archive_house, 56, 18, 72930141, words=ARCHIVES),
    "relay_mast": Ruin(relay_mast, 56, 18, 61820453, words=MASTS),
    "buried_vault": Ruin(buried_vault, 64, 20, 50417729, step="underground_structures", start=-10, terrain="none",
                         words=VAULTS),
    "waymark": Ruin([(3, waymark_road), (1, waymark_north)], 24, 8, 21094417, words=ROADS,
                    processors=weathered(*STONE_AGE, integrity=0.95)),
    "tutor_house": Ruin(tutor_house, 72, 22, 66190453, words=TUTORS,
                        processors=weathered(("tuff_bricks", "tuff", 0.1), integrity=0.92),
                        biomes=[f"minecraft:{b}" for b in ("plains", "meadow", "forest", "birch_forest", "flower_forest",
                                                            "taiga", "savanna", "cherry_grove")]),
    "fallen_relay_mast": Ruin(fallen_relay_mast, 40, 12, 18830291, start=-5, terrain="none", words=FALLEN,
                              processors=weathered(integrity=0.95)),
    "sealed_stacks": Ruin(sealed_stacks, 64, 20, 77015239, step="underground_structures", start=-16, terrain="none",
                          words=SEALED, processors=weathered(integrity=0.97)),
    "inn": Ruin(halfway_inn, 64, 20, 30091277, words=INNS, processors=weathered(*STONE_AGE, integrity=0.92)),
    "relay_tower": Ruin(relay_tower, 48, 16, 30817231, processors=weathered(*STONE_AGE, integrity=0.9), words=RELAYS),
    "windmill": Ruin(windmill_farm, 56, 18, 48215537, processors=weathered(*STONE_AGE, integrity=0.9), words=MILLS,
                     biomes=[f"minecraft:{b}" for b in ("plains", "sunflower_plains", "meadow", "windswept_hills",
                                                         "savanna", "flower_forest")]),
    "slipway": Ruin(slipway, 40, 12, 90442817, start=-5, terrain="none", processors=weathered(*STONE_AGE, integrity=0.9),
                    shore=True, words=SHIPS,
                    biomes=["minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore"]),
    "stilt_house": Ruin(stilt_house, 48, 16, 77120593, terrain="none", heightmap="OCEAN_FLOOR_WG",
                        processors=weathered(integrity=0.92),
                        biomes=["minecraft:swamp", "minecraft:mangrove_swamp"], words=MARSH),
}
BIOMES = ["plains", "sunflower_plains", "meadow", "forest", "birch_forest", "flower_forest", "old_growth_birch_forest",
          "dark_forest", "taiga", "snowy_plains", "snowy_taiga", "savanna", "savanna_plateau", "desert",
          "windswept_hills", "cherry_grove", "badlands"]


# ---------------------------------------------------------------- loot

def item(name, weight=1, count=None, components=None, about=None, drive=None):
    """A loot entry; with about, words a shard's record most often mentions (the shard_about function); with drive,
    (records, words) to fill an Archive Drive with (the fill_drive function)."""
    entry = {"type": "minecraft:item", "name": name if ":" in name else f"residual:{name}", "weight": weight}
    functions = []
    if count:
        functions.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
    if components:
        functions.append({"function": "minecraft:set_components", "components": components})
    if about:
        functions.append({"function": "residual:shard_about", "words": about})
    if drive:
        functions.append({"function": "residual:fill_drive", "records": drive[0], "words": drive[1]})
    if functions:
        entry["functions"] = functions
    return entry


def pool(rolls, entries):
    return {"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "bonus_rolls": 0.0, "entries": entries}


def mask(spec, weight=1):
    """A photomask carrying the old salvaged design for a spec: it fabricates working but unfused machines."""
    return item("photomask", weight, components={"residual:mask": {"spec": spec, "salvaged": True}})


WRECKS = ["shape", "diverge", "leak", "order", "depth"]


LOOT = {
    "chests/archive_house": [pool((2, 4), [
        item("archive_shard", 10, (1, 3)), item("weight_crystal", 4, (1, 3)), mask("linear", 2), mask("softmax", 1),
        mask("gelu", 1), item("wafer", 3, (2, 5)), item("minecraft:paper", 4, (2, 6)), item("minecraft:book", 2, (1, 2))])],
    "chests/archive_shelf": [pool((1, 3), [item("archive_shard", 8), item("minecraft:paper", 5, (1, 4)),
                                           item("minecraft:ink_sac", 2, (1, 2)), item("minecraft:candle", 2, (1, 2))])],
    "chests/relay_mast": [pool((2, 3), [
        item("archive_shard", 8, (1, 2)), item("relay_card", 4), item("schematic_relays", 2),
        item("minecraft:copper_ingot", 6, (2, 6)), item("minecraft:redstone", 5, (2, 5))])],
    "chests/buried_vault": [
        pool((1, 1), [item("salvaged_model", 1, components={"residual:wreck": w}) for w in WRECKS]),
        pool((2, 4), [
            mask("attention", 2), mask("layer_norm", 2), mask("mlp", 2), mask("transformer", 1),
            item("schematic_relays", 1), item("checkpoint", 3), item("weight_crystal", 5, (2, 4)),
            item("archive_shard", 6, (1, 3)), item("minecraft:diamond", 1), item("minecraft:gold_ingot", 3, (1, 3))])],
    "chests/relay_tower_store": [pool((2, 4), [
        item("minecraft:coal", 8, (3, 9)), item("minecraft:potato", 8, (2, 6)), item("minecraft:bread", 3, (1, 3)),
        item("minecraft:torch", 4, (2, 6)), item("minecraft:string", 2, (1, 3)), item("archive_shard", 3, about=RELAYS)])],
    "chests/relay_tower_kitchen": [pool((2, 3), [
        item("minecraft:baked_potato", 6, (1, 4)), item("minecraft:rabbit_stew", 2), item("minecraft:bowl", 3, (1, 2)),
        item("minecraft:cooked_rabbit", 3, (1, 2)), item("archive_shard", 2, about=RELAYS)])],
    "chests/relay_tower_keeper": [pool((2, 4), [
        item("archive_shard", 10, (1, 3), about=RELAYS), item("relay_card", 4), item("schematic_relays", 2),
        item("minecraft:paper", 4, (1, 4)), item("minecraft:book", 2), item("minecraft:candle", 2),
        item("minecraft:redstone", 3, (2, 4))])],
    "chests/windmill": [pool((2, 4), [
        item("minecraft:wheat", 8, (3, 9)), item("minecraft:bread", 5, (1, 4)), item("minecraft:wheat_seeds", 4, (2, 6)),
        item("minecraft:beetroot", 4, (2, 5)), item("minecraft:beetroot_seeds", 3, (1, 4)),
        item("archive_shard", 2, about=MILLS)])],
    "chests/windmill_miller": [pool((2, 4), [
        item("archive_shard", 8, (1, 2), about=MILLS), item("minecraft:bread", 5, (2, 4)),
        item("minecraft:emerald", 3, (1, 3)),
        item("minecraft:paper", 3, (1, 3)), item("minecraft:shears", 1), item("minecraft:string", 3, (2, 5))])],
    "chests/stilt_house_remedies": [pool((2, 4), [
        item("minecraft:potion", 3, components={"minecraft:potion_contents": {"potion": "minecraft:healing"}}),
        item("minecraft:potion", 2, components={"minecraft:potion_contents": {"potion": "minecraft:regeneration"}}),
        item("minecraft:honey_bottle", 4, (1, 2)), item("minecraft:glass_bottle", 4, (1, 4)),
        item("minecraft:blue_orchid", 3, (1, 3)), item("minecraft:sugar", 3, (1, 4)),
        item("minecraft:glistering_melon_slice", 1)])],
    "chests/stilt_house_wren": [pool((2, 4), [
        item("archive_shard", 10, (1, 3), about=MARSH), item("minecraft:paper", 4, (1, 4)),
        item("minecraft:candle", 3, (1, 2)), item("minecraft:glass_bottle", 3, (1, 3)),
        item("minecraft:emerald", 2, (1, 2))])],
    "chests/stilt_house_fish": [pool((2, 3), [
        item("minecraft:cod", 6, (1, 4)), item("minecraft:salmon", 4, (1, 3)), item("minecraft:dried_kelp", 4, (2, 6)),
        item("minecraft:string", 3, (1, 3)), item("minecraft:fishing_rod", 1), item("archive_shard", 2, about=MARSH)])],
    "chests/slipway_shipwright": [pool((2, 4), [
        item("archive_shard", 10, (1, 3), about=SHIPS), item("minecraft:paper", 3, (1, 3)), item("minecraft:map", 2),
        item("minecraft:compass", 1), item("minecraft:emerald", 3, (1, 3)), item("minecraft:iron_ingot", 3, (1, 3))])],
    "chests/slipway_stores": [pool((2, 4), [
        item("minecraft:string", 6, (2, 6)), item("minecraft:spruce_planks", 5, (4, 12)), item("minecraft:iron_nugget", 4, (3, 9)),
        item("minecraft:cod", 4, (1, 3)), item("minecraft:white_wool", 3, (1, 4)), item("archive_shard", 2, about=SHIPS)])],
    "chests/waymark_north": [pool((1, 3), [
        item("archive_shard", 8, (1, 2), about=["tower two", "Northpoint", "north road"]), item("minecraft:paper", 4, (1, 3)),
        item("minecraft:torch", 4, (2, 5)), item("minecraft:bread", 2, (1, 2)), item("minecraft:coal", 3, (1, 4))])],
    "archaeology/waymark": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
        item("archive_shard", 6, about=ROADS), item("minecraft:emerald", 2), item("minecraft:flint", 2)]}],
    "chests/tutor_house": [pool((2, 4), [
        item("archive_shard", 10, (1, 3), about=TUTORS), item("checkpoint", 4), item("weight_crystal", 4, (1, 3)),
        mask("linear", 2), mask("softmax", 1), item("minecraft:paper", 3, (1, 4)), item("minecraft:book", 2),
        item("minecraft:amethyst_shard", 3, (1, 3))])],
    "chests/tutor_dry_box": [
        pool((1, 1), [item("salvaged_model", 1, components={"residual:wreck": w}) for w in WRECKS] + [item("checkpoint", 5)]),
        pool((2, 3), [item("checkpoint", 4, (1, 2)), item("weight_crystal", 5, (2, 4)),
                      item("minecraft:honeycomb", 3, (1, 3)), item("archive_shard", 3, about=TUTORS)])],
    "chests/fallen_relay_mast": [
        pool((2, 3), [item("archive_drive", 1, drive=(10, ["relay", "message", "tower", "keeper"]))]),
        pool((1, 3), [item("relay_card", 4), item("archive_shard", 6, (1, 3), about=["relay", "message"]),
                      item("minecraft:copper_ingot", 4, (2, 6)), item("minecraft:redstone", 3, (2, 5))])],
    "chests/sealed_stacks": [
        pool((1, 2), [item("archive_drive", 1, drive=(14, ["archive", "clerk", "Vell", "record"]))]),
        pool((1, 3), [item("archive_shard", 8, (1, 3)), item("minecraft:paper", 4, (2, 6)),
                      item("minecraft:honeycomb", 3, (1, 3)), item("minecraft:book", 2)])],
    "chests/inn_cellar": [pool((2, 4), [
        item("minecraft:bread", 5, (2, 5)), item("minecraft:carrot", 5, (2, 6)), item("minecraft:baked_potato", 3, (1, 4)),
        item("minecraft:apple", 4, (1, 4)), item("minecraft:honey_bottle", 2), item("minecraft:emerald", 3, (1, 4)),
        item("archive_shard", 3, about=INNS)])],
    "chests/inn_rooms": [pool((2, 4), [
        item("archive_shard", 8, (1, 2), about=INNS + ROADS), item("minecraft:paper", 3, (1, 3)), item("minecraft:map", 2),
        item("minecraft:white_wool", 3, (1, 3)), item("minecraft:candle", 2), item("minecraft:emerald", 3, (1, 3)),
        item("wayfinder", 1)])],
    "archaeology/ruin": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
        item("archive_shard", 9), mask("matmul", 1), item("weight_crystal", 2)]}],
}


def write_all(write, rid):
    for name, r in RUINS.items():
        for _, build, template in r.variants(name):
            path = write.root / f"data/residual/structure/ruins/{template}.nbt"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(build().build())
        processors = rid("ruin_decay")
        if r.processors is not None:
            processors = rid(f"ruin/{name}")
            write(f"data/residual/worldgen/processor_list/ruin/{name}.json", {"processors": r.processors})
        biomes = "#residual:has_ruins"
        if r.biomes is not None:
            biomes = f"#residual:has_ruin/{name}"
            write(f"data/residual/tags/worldgen/biome/has_ruin/{name}.json", {"values": r.biomes})
        write(f"data/residual/worldgen/template_pool/ruins/{name}.json", {
            "fallback": "minecraft:empty",
            "elements": [{"weight": w, "element": {"element_type": "minecraft:single_pool_element",
                                                    "location": rid(f"ruins/{template}"), "processors": processors,
                                                    "projection": "rigid"}} for w, _, template in r.variants(name)]})
        if r.shore:
            write(f"data/residual/worldgen/structure/{name}.json", {
                "type": rid("shore"), "biomes": biomes, "step": r.step, "spawn_overrides": {},
                "terrain_adaptation": r.terrain, "start_pool": rid(f"ruins/{name}"), "offset": r.start})
        else:
            write(f"data/residual/worldgen/structure/{name}.json", {
                "type": "minecraft:jigsaw", "biomes": biomes, "step": r.step, "spawn_overrides": {},
                "terrain_adaptation": r.terrain, "start_pool": rid(f"ruins/{name}"), "size": 1,
                "start_height": {"absolute": r.start}, "project_start_to_heightmap": r.heightmap,
                "max_distance_from_center": 16, "use_expansion_hack": False})
        write(f"data/residual/worldgen/structure_set/{name}.json", {
            "structures": [{"structure": rid(name), "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": r.spacing, "separation": r.separation,
                          "salt": r.salt}})
    write("data/residual/ruin_kinds.json", [{"name": n, "words": r.words} for n, r in RUINS.items()])
    write("data/residual/worldgen/processor_list/ruin_decay.json", {"processors": [
        {"processor_type": "minecraft:block_rot", "integrity": 0.82, "rottable_blocks": "#residual:ruin_rot"}]})
    write("data/residual/tags/block/ruin_rot.json", {"values": [
        "minecraft:calcite", "minecraft:tuff_bricks", "minecraft:polished_tuff", "minecraft:tuff_brick_slab",
        "minecraft:polished_deepslate", "minecraft:glass_pane", "minecraft:cobweb", "minecraft:mossy_stone_bricks",
        "minecraft:cracked_stone_bricks", "minecraft:mossy_cobblestone", "minecraft:white_wool",
        "minecraft:spruce_fence"]})
    write("data/residual/tags/worldgen/biome/has_ruins.json", {"values": [f"minecraft:{b}" for b in BIOMES]})
    write("data/residual/tags/worldgen/structure/ruins.json", {"values": [rid(n) for n in RUINS]})
    for name, pools in LOOT.items():
        kind = "minecraft:archaeology" if name.startswith("archaeology") else "minecraft:chest"
        write(f"data/residual/loot_table/{name}.json", {"type": kind, "pools": pools, "random_sequence": rid(name)})
