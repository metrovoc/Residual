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

    def name(self, x, y, z):
        """The block at x, y, z without its namespace, or None."""
        b = self.blocks.get((x, y, z))
        return b[0].removeprefix("minecraft:") if b else None

    def empty(self, x, y, z):
        n = self.name(x, y, z)
        return n is None or n == "air"

    def put(self, x, y, z, name, props=None, data=None):
        """Sets a block only where nothing stands yet (air counts as nothing)."""
        if self.empty(x, y, z):
            self.set(x, y, z, name, props, data)

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


# ---------------------------------------------------------------- building parts

OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
LOGS = ("_log", "_wood", "_stem", "_hyphae", "basalt", "_pillar")


def run(face):
    """The step along a wall facing this way, and its outward normal."""
    nx, nz = SIDES[face]
    return (abs(nz), abs(nx)), (nx, nz)


def cell(face, plane, a):
    """The x, z of position a along a wall facing this way, its outer face in plane."""
    (ux, _), _ = run(face)
    return (a, plane) if ux else (plane, a)


def spaced(a0, a1, every):
    """Positions from a0 to a1, both ends included, evenly spaced about every apart."""
    n = max(1, round((a1 - a0) / every))
    return sorted({a0 + round(k * (a1 - a0) / n) for k in range(n + 1)})


def pillar(t, x, y0, y1, z, name):
    for y in range(y0, y1 + 1):
        if name.endswith(LOGS):
            t.log(x, y, z, name)
        else:
            t.set(x, y, z, name)


def framed(t, face, plane, a0, a1, y0, y1, post, infill, every=4, posts=None, recess=1, rails=(), pad=None):
    """A wall facing face, from a0 to a1 along it: posts from y0 to y1 at its ends and about every blocks apart (or at
    posts) in the outer plane, each on a pad of stone where given, and between them infill set recess blocks back, with
    the air kept clear in front; at the heights in rails the infill is a rail of the post's wood laid along the wall."""
    (ux, _), (nx, nz) = run(face)
    at = spaced(a0, a1, every) if posts is None else posts
    for a in range(a0, a1 + 1):
        x, z = cell(face, plane, a)
        if a in at:
            pillar(t, x, y0, y1, z, post)
            if pad:
                t.set(x, y0, z, pad)
            continue
        for y in range(y0, y1 + 1):
            if y in rails and post.endswith(LOGS):
                t.log(x - nx * recess, y, z - nz * recess, post, "x" if ux else "z")
            elif y in rails:
                t.set(x - nx * recess, y, z - nz * recess, post)
            else:
                t.set(x - nx * recess, y, z - nz * recess, infill)
            for r in range(recess):
                t.set(x - nx * r, y, z - nz * r, "air")


def boards(t, x0, x1, z0, z1, y, planks, beam, every=3, axis="x"):
    """A floor of planks crossed every few rows by a beam of the same wood, laid along axis."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (z - z0 if axis == "x" else x - x0) % every == every - 1:
                t.log(x, y, z, beam, axis)
            else:
                t.set(x, y, z, planks)


def dormer(t, c, zf, y, face, stairs, post, infill, glass="glass_pane"):
    """A dormer three wide in a roof whose ridge runs along x, its front at zf facing face (south or north) from y, the
    eave height: posts either side of a window two high, a little gable over it, cheeks back into the roof, and its
    own roof meeting the main one."""
    d = 1 if face == "south" else -1
    for dx in (-1, 0, 1):
        for k in range(4 if dx == 0 else 3):
            t.set(c + dx, y + k, zf + d * (1 - k), "air")
    for dx in (-1, 1):
        pillar(t, c + dx, y, y + 1, zf, post)
        for k in (1, 2):
            for h in (y, y + 1):
                t.set(c + dx, h, zf - d * k, infill)
    t.set(c, y, zf, glass)
    t.set(c, y + 1, zf, glass)
    t.set(c, y + 2, zf, infill)
    for k in range(-1, 3):
        z = zf - d * k
        t.stairs(c - 2, y + 1, z, stairs, "east")
        t.stairs(c + 2, y + 1, z, stairs, "west")
        t.stairs(c - 1, y + 2, z, stairs, "east")
        t.stairs(c + 1, y + 2, z, stairs, "west")
        t.slab(c, y + 3, z, stairs.replace("_stairs", "_slab"))


def window(t, face, x, y, z, glass="glass_pane", h=1, sill=None, shutters=None, hood=None):
    """A window h high at x, y, z in a wall facing face: a sill of upside-down stairs under it, open trapdoors either
    side for shutters and a slab over it for a hood, each set in front of the wall where given."""
    (ux, uz), (nx, nz) = run(face)
    for k in range(h):
        t.set(x, y + k, z, glass)
    fx, fz = x + nx, z + nz
    if sill:
        t.stairs(fx, y - 1, fz, sill, OPP[face], half="top")
    if shutters:
        for d in (-1, 1):
            for k in range(h):
                t.trapdoor(fx + ux * d, y + k, fz + uz * d, shutters, face, open_=True)
    if hood:
        t.slab(fx, y + h, fz, hood)


def roof(t, y, x0, x1, z0, z1, stairs, ridge="x", over=1, verge=1, brackets=None, mix=None, rng=None):
    """A gable roof on walls x0..x1 by z0..z1 whose tops are at y - 1: courses of stairs from the eaves, over blocks out
    past the walls, up to the ridge along ridge, standing verge blocks out past the gable walls; brackets of upside-down
    stairs under the eaves where given, and mix (stairs, share) to vary the courses. Returns the gable walls' cells
    under the roof, (u, y, a) with u the gable wall's plane and a the place along it."""
    if ridge == "x":
        e0, e1, g0, g1, w0, w1 = z0 - over, z1 + over, x0 - verge, x1 + verge, x0, x1
    else:
        e0, e1, g0, g1, w0, w1 = x0 - over, x1 + over, z0 - verge, z1 + verge, z0, z1
    gables, k = [], 0
    while e0 + k <= e1 - k:
        a0, a1 = e0 + k, e1 - k
        for u in range(g0, g1 + 1):
            for a, up in ((a0, "south" if ridge == "x" else "east"), (a1, "north" if ridge == "x" else "west")):
                x, z = (u, a) if ridge == "x" else (a, u)
                name = mix[0] if mix and rng and rng.random() < mix[1] else stairs
                if a0 == a1:
                    t.slab(x, y + k, z, name.replace("_stairs", "_slab"))
                else:
                    t.stairs(x, y + k, z, name, up)
        gables += [(u, y + k, a) for u in (w0, w1) for a in range(a0 + 1, a1)]
        k += 1
    if brackets:
        for u in range(w0, w1 + 1):
            for a, toward in ((e0, "south" if ridge == "x" else "east"), (e1, "north" if ridge == "x" else "west")):
                x, z = (u, a) if ridge == "x" else (a, u)
                t.stairs(x, y - 1, z, brackets, toward, half="top")
    return gables


def chimney(t, x, z, y0, y1, block, cap=None):
    """A chimney stack from y0 to y1, capped with a slab of cap."""
    pillar(t, x, y0, y1, z, block)
    if cap:
        t.slab(x, y1 + 1, z, cap)


def noise(seed, scale=3.0):
    """A smooth field of values in 0..1 over the template, so that weathering gathers in patches."""
    import math

    def lattice(i, j, k):
        h = (i * 73856093) ^ (j * 19349663) ^ (k * 83492791) ^ (seed * 2654435761)
        h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
        return (h ^ (h >> 16)) / 0xFFFFFFFF

    def f(x, y, z):
        fx, fy, fz = x / scale, y / scale, z / scale
        i, j, k = math.floor(fx), math.floor(fy), math.floor(fz)
        u, v, w = (s * s * (3 - 2 * s) for s in (fx - i, fy - j, fz - k))
        out = 0.0
        for di, a in ((0, 1 - u), (1, u)):
            for dj, b in ((0, 1 - v), (1, v)):
                for dk, c in ((0, 1 - w), (1, w)):
                    out += a * b * c * lattice(i + di, j + dj, k + dk)
        return out
    return f


MOSSY = {"cobblestone": "mossy_cobblestone", "stone_bricks": "mossy_stone_bricks"}
for _base in ("cobblestone", "stone_brick"):
    for _shape in ("_stairs", "_slab", "_wall"):
        MOSSY[_base + _shape] = "mossy_" + _base + _shape
CRACKED = {"stone_bricks": "cracked_stone_bricks", "deepslate_bricks": "cracked_deepslate_bricks",
           "deepslate_tiles": "cracked_deepslate_tiles", "polished_blackstone_bricks": "cracked_polished_blackstone_bricks",
           "nether_bricks": "cracked_nether_bricks"}
FULL = ("_planks", "_bricks", "_tiles", "stone", "cobblestone", "calcite", "tuff", "_log", "_wood", "terracotta",
        "deepslate", "andesite", "diorite", "granite", "mud_bricks", "_block", "concrete", "copper", "_wool", "sandstone",
        "packed_mud", "dirt", "clay")


def full(name):
    return name is not None and solid(name) and name.endswith(FULL) and not name.endswith(("_slab", "_stairs", "_wall"))


PLANTS = ["short_grass", "fern", "bush", "poppy", "dandelion", "azure_bluet", "oxeye_daisy", "cornflower"]
PLANT_WEIGHTS = [14, 3, 1, 1, 1, 1, 1, 1]


def weather(t, seed, ground=1, moss=0.0, crack=0.0, height=6, vines=0.0, webs=0.0, leaves=0.0, plants=0.0,
            inside=None, keep=()):
    """Weathers a template by where each block stands rather than evenly: moss gathers low down, on the north side
    and in patches; cracks at corners and beside openings; vines hang down outer walls, most on the north and west;
    cobwebs in the corners of ceilings inside (the box inside, x0, x1, z0, z1, or anywhere roofed); leaves on roofs;
    grass and flowers where ground blocks of the template meet the air. Blocks at keep are left alone."""
    rng = random.Random(seed)
    patch = noise(seed)
    keep = set(keep)
    blocks = sorted(t.blocks)
    for (x, y, z) in blocks:
        if (x, y, z) in keep:
            continue
        name, props, data = t.blocks[(x, y, z)]
        n = name.removeprefix("minecraft:")
        open_ = {s for s, (dx, dz) in SIDES.items() if t.empty(x + dx, y, z + dz)}
        low = max(0.15, 1 - (y - ground) / height)
        bias = (1.6 if "north" in open_ else 1.0) * (0.4 + 1.2 * patch(x, y, z))
        if n in MOSSY and rng.random() < moss * low * bias:
            t.blocks[(x, y, z)] = ("minecraft:" + MOSSY[n], props, data)
        elif n in CRACKED and open_ and rng.random() < crack * (1.5 if len(open_) > 1 else 1.0) * (0.4 + 1.2 * patch(z, y, x)):
            t.blocks[(x, y, z)] = ("minecraft:" + CRACKED[n], props, data)
    if vines:
        for (x, y, z) in blocks:
            n = t.name(x, y, z)
            if not full(n) or y < ground + 2:
                continue
            for face, (dx, dz) in SIDES.items():
                if not t.empty(x + dx, y, z + dz) or not t.empty(x + dx, y + 1, z + dz) and t.name(x + dx, y + 1, z + dz) != "vine":
                    continue
                share = vines * {"north": 1.6, "west": 1.2, "east": 0.8, "south": 0.5}[face] * (0.3 + 1.4 * patch(x, y, z))
                if rng.random() >= share:
                    continue
                for k in range(rng.randint(1, 4)):
                    vy = y - k
                    if vy < ground or not t.empty(x + dx, vy, z + dz) or not full(t.name(x, vy, z)):
                        break
                    t.set(x + dx, vy, z + dz, "vine", {s: str(s == OPP[face]).lower() for s in SIDES} | {"up": "false"})
    if webs:
        for (x, y, z) in blocks:
            if not full(t.name(x, y, z)):
                continue
            wx, wy, wz = x, y - 1, z
            if not t.empty(wx, wy, wz) or (wx, wy, wz) in t.blocks and t.blocks[(wx, wy, wz)][0] != "minecraft:air":
                continue
            if inside and not (inside[0] < wx < inside[1] and inside[2] < wz < inside[3]):
                continue
            walls = sum(full(t.name(wx + dx, wy, wz + dz)) for dx, dz in SIDES.values())
            if walls and (wx, wy, wz) not in keep and rng.random() < webs * walls:
                t.set(wx, wy, wz, "cobweb")
    if leaves:
        for (x, y, z) in blocks:
            n = t.name(x, y, z)
            if n and n.endswith(("_stairs", "_slab")) and t.empty(x, y + 1, z) and (x, y + 1, z) not in t.blocks \
                    and patch(x, 0, z) > 0.62 and rng.random() < leaves * 4:
                t.set(x, y + 1, z, rng.choice(["oak_leaves", "oak_leaves", "azalea_leaves", "flowering_azalea_leaves"]),
                      {"distance": "1", "persistent": "true", "waterlogged": "false"})
    if plants:
        for (x, y, z) in blocks:
            if t.name(x, y, z) in ("grass_block", "dirt", "coarse_dirt", "moss_block", "podzol") and \
                    (x, y + 1, z) not in t.blocks and rng.random() < plants:
                t.set(x, y + 1, z, rng.choices(PLANTS, PLANT_WEIGHTS)[0])


def lawn(t, sx, sz, rng, grass=0.85, bare=("coarse_dirt",)):
    """The template's ground: grass, worn bare in places."""
    for x in range(sx):
        for z in range(sz):
            if rng.random() < grass:
                t.set(x, 0, z, "grass_block", {"snowy": "false"})
            else:
                t.set(x, 0, z, rng.choice(bare))


def lights_out(t, seed, share=0.85, keep=()):
    """Puts out most of the lights: lanterns, torches and lit candles are taken, except those at keep."""
    rng = random.Random(seed)
    for pos, (name, _, _) in list(t.blocks.items()):
        if pos not in keep and name.endswith(("lantern", "torch")) and rng.random() < share:
            t.set(*pos, "air")


def broken_glass(t, seed, share=0.4):
    """Breaks some of the glass: a pane gone, a cobweb across it, or dirty glass."""
    rng = random.Random(seed)
    for pos, (name, props, _) in list(t.blocks.items()):
        if name.endswith("glass_pane") and rng.random() < share:
            r = rng.random()
            t.set(*pos, "air" if r < 0.4 else "cobweb" if r < 0.7 else "brown_stained_glass_pane")


def caved(t, seed, x0, x1, z0, z1, y_top, y_floor, rubble=("cobblestone", "gravel", "coarse_dirt")):
    """A roof and floor fallen in over an uneven patch: everything above y_floor taken, the rubble heaped on the
    floor."""
    rng = random.Random(seed)
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    rx, rz = (x1 - x0) / 2 + 0.5, (z1 - z0) / 2 + 0.5
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            d = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
            if d > 1 - 0.35 * rng.random():
                continue
            for y in range(y_floor + 1, y_top + 1):
                if (x, y, z) in t.blocks:
                    t.set(x, y, z, "air")
            if rng.random() < 0.6:
                t.set(x, y_floor + 1, z, rng.choice(rubble))
                if rng.random() < 0.25:
                    t.set(x, y_floor + 2, z, rng.choice(rubble))


def broken(t, seed, cx, cz, radius, y_top, y_low, rubble=None, floor=0, inside=None):
    """A corner broken away: round x, z, everything above a line rising from y_low at the middle to y_top at radius is
    gone, unevenly; the rubble heaped on the floor inside (the box x0, x1, z0, z1)."""
    import math
    rng = random.Random(seed)
    for (x, y, z) in list(t.blocks):
        d = math.hypot(x - cx, z - cz)
        if d > radius or y <= floor:
            continue
        cut = y_low + (y_top - y_low) * (d / radius) ** 1.5 + rng.uniform(-0.8, 0.8)
        if y > cut:
            t.set(x, y, z, "air")
    if rubble:
        for x in range(round(cx - radius), round(cx + radius) + 1):
            for z in range(round(cz - radius), round(cz + radius) + 1):
                if inside and not (inside[0] <= x <= inside[1] and inside[2] <= z <= inside[3]):
                    continue
                if math.hypot(x - cx, z - cz) < radius * 0.8 and t.empty(x, floor + 1, z) and rng.random() < 0.55:
                    t.set(x, floor + 1, z, rng.choice(rubble))


ARCHIVE_HOUSES = [
    {"length": 11, "fall": "ne", "seed": 1, "porch": True, "desks": False},
    {"length": 13, "fall": "sw", "seed": 2, "porch": True, "desks": True},
    {"length": 11, "fall": "nw", "seed": 3, "porch": False, "desks": True},
]


def archive_house(variant=0):
    """A house that kept records, of calcite between pilasters of tuff on a tuff footing, under a roof of deepslate
    tiles with a round window in each gable; a porch before the door. Inside, shelves along the back wall, the lectern,
    a desk under the window or the copying tables, the chest and the shelf barrels. One corner has fallen in, roof and
    walls, its rubble on the floor, and the floor under it dug through to what was buried."""
    v = ARCHIVE_HOUSES[variant]
    rng = random.Random(v["seed"])
    n = v["length"]
    t = Template(n + 6, 13, 16)
    x0, x1, z0, z1 = 3, n + 2, 3, 11
    mid = (x0 + x1) // 2
    xs = sorted({x0, x0 + 3, x1 - 3, x1, mid - 1, mid + 1}) if n > 11 else [x0, x0 + 3, x1 - 3, x1]
    zs = [z0, 7, z1]
    lawn(t, n + 6, 16, rng)
    t.fill(x0, 0, z0, x1, 0, z1, "tuff_bricks")
    boards(t, x0 + 2, x1 - 2, z0 + 2, z1 - 2, 0, "spruce_planks", "stripped_spruce_log", axis="z")
    for face, plane, a0, a1, posts in (("north", z0, x0, x1, xs), ("south", z1, x0, x1, xs), ("west", x0, z0, z1, zs),
                                       ("east", x1, z0, z1, zs)):
        framed(t, face, plane, a0, a1, 1, 5, "polished_tuff", "calcite", posts=posts, rails=(4,), pad="tuff_bricks")
        _, (nx, nz) = run(face)
        for a in range(a0 + 1, a1):
            x, z = cell(face, plane, a)
            if a not in posts:
                t.set(x - nx, 1, z - nz, "tuff_bricks")
                t.stairs(x, 1, z, "tuff_brick_stairs", OPP[face])
            else:
                t.set(x, 5, z, "chiseled_tuff")
    for u, y, a in roof(t, 6, x0, x1, z0, z1, "deepslate_tile_stairs", "x", brackets="tuff_brick_stairs"):
        inward = 1 if u == x0 else -1
        if a == 7:
            t.set(u, y, a, "glass_pane" if y == 8 else "polished_tuff")
        else:
            t.set(u, y, a, "air")
            t.set(u + inward, y, a, "calcite")
    # windows two high with sills, the door; a porch of tuff over it with its own little roof
    for x in range(x0 + 1, x1):
        if x not in xs and x != mid:
            window(t, "south", x, 2, z1 - 1, h=2, sill="tuff_brick_stairs")
            window(t, "north", x, 2, z0 + 1, h=2, sill="tuff_brick_stairs")
    for z in (5, 9):
        window(t, "west", x0 + 1, 2, z, h=2, sill="tuff_brick_stairs")
        window(t, "east", x1 - 1, 2, z, h=2, sill="tuff_brick_stairs")
    t.set(x0, 2, 5, "potted_azure_bluet")
    t.set(x1, 2, 9, "potted_fern")
    t.door(mid, 1, z1 - 1, "spruce_door", "south")
    t.set(mid, 1, z1, "air")
    t.set(mid, 3, z1 - 1, "glass_pane")
    if v["porch"]:
        for x in (mid - 1, mid + 1):
            for y in range(1, 5):
                t.set(x, y, z1 + 2, "tuff_brick_wall")
        for z in range(z1 + 1, z1 + 4):
            t.stairs(mid - 2, 4, z, "deepslate_tile_stairs", "east")
            t.stairs(mid + 2, 4, z, "deepslate_tile_stairs", "west")
            t.stairs(mid - 1, 5, z, "deepslate_tile_stairs", "east")
            t.stairs(mid + 1, 5, z, "deepslate_tile_stairs", "west")
            t.slab(mid, 6, z, "deepslate_tile_slab")
        t.fill(mid - 1, 0, z1 + 1, mid + 1, 0, z1 + 2, "tuff_bricks")
    else:
        # a bay of glass on the south side instead
        for x in range(mid - 1, mid + 2):
            t.set(x, 0, z1 + 1, "tuff_bricks")
            t.slab(x, 1, z1 + 1, "tuff_brick_slab", "top")
            t.set(x, 2, z1 + 1, "glass_pane")
            t.set(x, 3, z1 + 1, "glass_pane")
            t.slab(x, 4, z1 + 1, "deepslate_tile_slab")
        t.door(mid, 1, z0 + 1, "spruce_door", "north")
        t.set(mid, 1, z0, "air")
        t.set(mid, 1, z1 - 1, "air")
        t.set(mid, 2, z1 - 1, "air")
        t.set(mid, 3, z1 - 1, "air")
    # inside: shelves along the back, the lectern, a desk or the copying tables, the chest, the shelf barrels
    shelf = {"facing": "south", **{f"slot_{i}_occupied": str(rng.random() < 0.4).lower() for i in range(6)}}
    for x in range(x0 + 2, x1 - 1):
        if abs(x - mid) > 1:
            for y in (1, 2, 3):
                t.set(x, y, z0 + 2, "chiseled_bookshelf" if (x + y) % 3 else "bookshelf", shelf if (x + y) % 3 else None)
    t.slab(mid, 1, z0 + 2, "spruce_slab", "top")
    t.trapdoor(mid - 1, 1, z0 + 2, "spruce_trapdoor", "south", open_=True)
    t.trapdoor(mid + 1, 1, z0 + 2, "spruce_trapdoor", "south", open_=True)
    t.set(mid, 1, 7, "lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    if v["desks"]:
        for x in (x0 + 2, x1 - 2):
            for z in (6, 8):
                t.slab(x, 1, z, "spruce_slab", "top")
                t.stairs(x + (1 if x == x0 + 2 else -1), 1, z, "spruce_stairs", "west" if x == x0 + 2 else "east")
    t.chest(x0 + 2, 1, z1 - 2, "east", "residual:chests/archive_house")
    for y in (1, 2):
        t.barrel(x1 - 2, y, z1 - 2, "up", "residual:chests/archive_shelf")
    for y in range(6, 10):
        t.set(mid, y, 7, "chain", {"axis": "y", "waterlogged": "false"})
    t.lantern(mid, 5, 7, hanging=True)
    # the corner that fell: roof and walls gone down toward it, rubble on the floor, the floor dug through
    cx, cz = {"ne": (x1, z0), "sw": (x0, z1), "nw": (x0, z0)}[v["fall"]]
    broken(t, v["seed"], cx, cz, 6.5, 12, 1, rubble=["gravel", "tuff", "calcite", "cobbled_deepslate", "coarse_dirt"],
           inside=(x0 + 1, x1 - 1, z0 + 1, z1 - 1))
    dx, dz = (1 if cx == x0 else -1), (1 if cz == z0 else -1)
    for k, (ox, oz) in enumerate(((2, 2), (3, 2), (2, 3), (3, 3))):
        if k < 3:
            t.suspicious(cx + dx * ox, 0 if k == 2 else 1, cz + dz * oz, "gravel", "residual:archaeology/ruin")
    lights_out(t, v["seed"], 0.6)
    broken_glass(t, v["seed"], 0.35)
    weather(t, v["seed"], ground=1, height=5, vines=0.3, webs=0.08, plants=0.35,
            inside=(x0 + 1, x1 - 1, z0 + 1, z1 - 1))
    return t


def relay_mast(variant=0):
    """A relay mast of the relay network: an open lattice of copper on a stepped tuff footing, four legs bound by rings,
    a spine up the middle with its ladder to the stand where its keepers worked; above the stand the lattice narrows to
    the spine, the arms across it and the relay on top, still there; the copper box at its foot. The copper has long
    gone green. In the second the top has come down in a storm and lies in the grass, its relay with it."""
    rng = random.Random(61820453 + variant)
    patch = noise(61820453 + variant, 5.0)
    t = Template(15, 28, 15)

    def copper(x, y, z, kind, props=None):
        t.set(x, y, z, ("weathered_" if patch(x, y, z) > 0.62 else "oxidized_") + kind, props)

    slab_top = {"type": "top", "waterlogged": "false"}
    lawn(t, 15, 15, rng)
    # the footing: two steps of tuff, chiseled at the corners
    t.fill(3, 0, 3, 11, 1, 11, "tuff_bricks")
    ring_stairs(t, 1, 3, 11, "tuff_brick_stairs")
    t.fill(4, 2, 4, 10, 2, 10, "polished_tuff")
    ring_stairs(t, 2, 4, 10, "tuff_brick_stairs")
    for x, z in ((3, 3), (11, 3), (3, 11), (11, 11)):
        t.set(x, 1, z, "chiseled_tuff")
    t.fill(5, 2, 5, 9, 2, 9, "polished_tuff")
    for x in range(3, 12):
        for z in range(3, 12):
            if t.name(x, 0, z) == "tuff_bricks" and rng.random() < 0.35:
                t.set(x, 0, z, rng.choice(["tuff", "chiseled_tuff_bricks", "polished_tuff"]))
    # the footing's corners cut back, a chiseled block where each cut meets the step
    for cx_, cz_ in ((3, 3), (11, 3), (3, 11), (11, 11)):
        sx_, sz_ = (1 if cx_ == 3 else -1), (1 if cz_ == 3 else -1)
        for dx, dz in ((0, 0), (1, 0), (0, 1)):
            x, z = cx_ + sx_ * dx, cz_ + sz_ * dz
            t.set(x, 0, z, "grass_block", {"snowy": "false"})
            t.set(x, 1, z, "air")
        t.set(cx_ + sx_, 1, cz_ + sz_, "chiseled_tuff")
    top = 25 if variant == 0 else 13
    lo = min(top, 16)
    # four legs five apart, a ring of slabs every four, the spine up the middle with its ladder
    for y in range(3, lo):
        for x, z in ((5, 5), (9, 5), (5, 9), (9, 9)):
            copper(x, y, z, "chiseled_copper" if y % 4 == 0 else "cut_copper")
        if y % 4 == 0:
            for k in range(6, 9):
                for x, z in ((k, 5), (k, 9), (5, k), (9, k)):
                    copper(x, y, z, "cut_copper_slab", slab_top)
        copper(7, y, 7, "copper_grate", {"waterlogged": "false"})
        t.set(7, y, 8, "ladder", {"facing": "south", "waterlogged": "false"})
    if variant == 0:
        # the stand at sixteen: a floor of slabs out to the legs, railed; then the lattice narrows to three across
        for x in range(4, 11):
            for z in range(4, 11):
                copper(x, 16, z, "cut_copper_slab", slab_top)
                if x in (4, 10) or z in (4, 10):
                    t.set(x, 17, z, "iron_bars")
        for x, z, f in ((5, 4, "south"), (9, 4, "south"), (4, 5, "east"), (10, 9, "west"), (5, 10, "north")):
            copper(x, 15, z, "cut_copper_stairs", {"facing": f, "half": "top", "shape": "straight", "waterlogged": "false"})
        t.set(7, 16, 8, "oxidized_copper_trapdoor", {"facing": "south", "half": "top", "open": "true", "powered": "false",
                                                     "waterlogged": "false"})
        t.set(7, 16, 7, "oxidized_copper_grate", {"waterlogged": "false"})
        copper(4, 17, 4, "copper_bulb", {"lit": "false", "powered": "false"})
        for y in range(17, 22):
            for x, z in ((6, 6), (8, 6), (6, 8), (8, 8)):
                copper(x, y, z, "cut_copper")
            if y % 2:
                for x, z in ((7, 6), (6, 7), (8, 7), (7, 8)):
                    copper(x, y, z, "cut_copper_slab", slab_top)
        for y in range(17, top):
            copper(7, y, 7, "copper_grate", {"waterlogged": "false"})
        copper(7, 22, 7, "chiseled_copper")
        for d, f in ((-1, "west"), (1, "east")):
            for k in (1, 2, 3):
                copper(7 + d * k, 23, 7, "lightning_rod", {"facing": f, "powered": "false", "waterlogged": "false"})
            copper(7 + d * 4, 23, 7, "copper_bulb", {"lit": "false", "powered": "false"})
        t.set(7, top, 7, "residual:relay")
        copper(7, top + 1, 7, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    else:
        # broken off: the legs torn short at different heights, the top lying in the grass to the east with its relay
        for x, z, h in ((5, 5, 2), (9, 5, 0), (5, 9, 1), (9, 9, 3)):
            for y in range(top - 3 + h, top):
                t.set(x, y, z, "air")
        for k in range(0, 9):
            copper(5 + k, 1, 13, "cut_copper" if k % 4 else "copper_grate", None if k % 4 else {"waterlogged": "false"})
            copper(5 + k, 1, 11, "cut_copper")
            if k % 2 == 0:
                copper(5 + k, 2, 12, "cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
        t.set(14, 1, 12, "residual:relay")
        for x in (12, 13):
            copper(x, 1, 10, "lightning_rod", {"facing": "north", "powered": "false", "waterlogged": "false"})
        for x, z in ((4, 12), (3, 10)):
            t.set(x, 1, z, "tuff_brick_wall")
        for x, z, f in ((2, 4, "east"), (1, 6, "south"), (12, 2, "west"), (13, 4, "north"), (2, 12, "east")):
            copper(x, 1, z, "cut_copper_stairs", {"facing": f, "half": "bottom", "shape": "straight", "waterlogged": "false"})
        for x, z in ((1, 9), (13, 7), (4, 1), (8, 1), (1, 13)):
            copper(x, 1, z, "cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
        for x, z in ((6, 12), (9, 12), (11, 10)):
            copper(x, 1, z, "copper_trapdoor", {"facing": "north", "half": "bottom", "open": "false", "powered": "false",
                                                "waterlogged": "false"})
        copper(7, 1, 2, "lightning_rod", {"facing": "east", "powered": "false", "waterlogged": "false"})
    # the copper box at the foot, its door a trapdoor, the keepers' chest inside; a barrel and their lectern
    for y in (3, 4):
        for x, z in ((11, 5), (11, 6), (11, 7), (10, 5), (10, 7)):
            copper(x, y, z, "cut_copper")
    for x in (10, 11):
        for z in (5, 6, 7):
            copper(x, 5, z, "cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(10, 3, 6, "chest", {"facing": "west", "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": "residual:chests/relay_mast"})
    t.set(9, 4, 6, "oxidized_copper_trapdoor", {"facing": "west", "half": "bottom", "open": "true", "powered": "false",
                                                "waterlogged": "false"})
    t.barrel(10, 3, 8, "up")
    t.barrel(11, 3, 8, "up", "residual:chests/relay_mast")
    t.set(11, 4, 8, "oxidized_copper_trapdoor", {"facing": "north", "half": "bottom", "open": "false", "powered": "false",
                                                 "waterlogged": "false"})
    t.set(9, 3, 10, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    t.sign(12, 4, 6, "east", ["Relay twelve.", "Keep the box", "shut in rain."])
    for y in range(3, 6):
        t.set(4, y, 12, "chain", {"axis": "y", "waterlogged": "false"})
    t.set(4, 2, 12, "tuff_brick_wall")
    t.suspicious(1, 0, 7, "gravel", "residual:archaeology/ruin")
    # what the years did: vines up the legs, moss on the footing, grass and ferns round it, webs in the lattice
    for y in range(3, 11):
        for x, z, face in ((5, 5, "north"), (9, 5, "north"), (5, 9, "west"), (5, 5, "west")):
            if rng.random() < 0.6 - 0.05 * y and t.empty(x + SIDES[face][0], y, z + SIDES[face][1]):
                t.set(x + SIDES[face][0], y, z + SIDES[face][1], "vine",
                      {s_: str(s_ == OPP[face]).lower() for s_ in SIDES} | {"up": "false"})
    for x, z in ((3, 4), (4, 3), (10, 3), (3, 10), (11, 9)):
        if t.empty(x, 2, z):
            t.set(x, 2, z, "moss_carpet")
    for y in (6, 10, 14):
        if rng.random() < 0.7 and t.empty(6, y, 5):
            t.set(6, y, 5, "cobweb")
    weather(t, 61820453 + variant, ground=1, plants=0.35)
    return t


def buried_vault(variant=0):
    """A deepslate vault below ground, as the masts' keepers left it: a room of deepslate tiles between pillars of
    polished deepslate, beams across its ceiling, niches of shelves either side, a dead bank of quickeners along the
    back wall and two chests; the entry at the foot of the shaft, its ladder up to a hatch under a lantern on a post.
    The years have cracked it and let the roots and the damp in; in the second a corner of the ceiling has come
    down."""
    t = Template(15, 15, 15)
    x0, x1, z0, z1, h = 2, 12, 3, 13, 5
    t.fill(x0, 0, z0, x1, h + 1, z1, "deepslate_tiles")
    t.fill(x0 + 1, 1, z0 + 1, x1 - 1, h, z1 - 1, "air")
    t.fill(x0 + 2, 0, z0 + 2, x1 - 2, 0, z1 - 2, "polished_deepslate")
    # the entry: a little room under the shaft, through an arch into the vault
    t.fill(5, 0, 0, 9, 4, 3, "deepslate_bricks")
    t.fill(6, 1, 1, 8, 3, 3, "air")
    t.stairs(6, 3, 3, "deepslate_brick_stairs", "east", half="top")
    t.stairs(8, 3, 3, "deepslate_brick_stairs", "west", half="top")
    # niches either side, their shelves of barrels
    for x, inner in ((x0 - 1, x0), (x1 + 1, x1)):
        t.fill(x, 0, 6, x, 4, 10, "deepslate_bricks")
        for z in range(7, 10):
            for y in (1, 2, 3):
                t.set(inner, y, z, "air")
            t.barrel(inner, 1, z, "up")
            t.slab(inner, 2, z, "deepslate_brick_slab", "bottom")
    t.barrel(x0, 1, 8, "up", "residual:chests/archive_shelf")
    # pillars standing out from the walls, beams across the ceiling between them, a band of bricks in the walls
    for k in (5, 7, 9):
        for y in range(1, h + 1):
            for x, z in ((k, z0 + 1), (k, z1 - 1)):
                t.set(x, y, z, "polished_deepslate")
        for z in range(z0 + 2, z1 - 1):
            t.slab(k, h, z, "deepslate_brick_slab", "top")
    for z in (6, 10):
        for y in range(1, h + 1):
            t.set(x0 + 1, y, z, "polished_deepslate")
            t.set(x1 - 1, y, z, "polished_deepslate")
    for a_ in range(x0 + 2, x1 - 1):
        t.stairs(a_, h, z0 + 1, "deepslate_brick_stairs", "south", half="top")
        t.stairs(a_, h, z1 - 1, "deepslate_brick_stairs", "north", half="top")
    for a_ in range(x0 + 1, x1):
        for x, z in ((a_, z0), (a_, z1)):
            t.set(x, 3, z, "deepslate_bricks")
    t.set(7, 1, z0, "air")
    t.set(7, 2, z0, "air")
    # the machine bank along the back wall
    for x in range(x0 + 2, x1 - 1):
        if x in (5, 7, 9):
            continue
        t.set(x, 1, z1 - 1, "residual:tensor_casing")
        t.set(x, 2, z1 - 1, "residual:accelerator" if x in (4, 10) else "residual:tensor_casing")
        t.set(x, 1, z1 - 2, "residual:cable", {d: str(d in ("east", "west", "south")).lower()
                                              for d in ("north", "south", "east", "west", "up", "down")})
    t.chest(x0 + 1, 1, 11, "east", "residual:chests/buried_vault")
    t.chest(x1 - 1, 1, 11, "west", "residual:chests/buried_vault")
    t.set(7, 1, 7, "residual:tensor_casing")
    t.set(7, 2, 7, "lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    for x, z in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        t.set(x, h, z, "oxidized_copper_bulb", {"lit": str(variant == 0).lower(), "powered": "false"})
    t.suspicious(5, 0, 6, "gravel", "residual:archaeology/ruin")
    t.suspicious(9, 0, 8, "gravel", "residual:archaeology/ruin")
    # the shaft: a ladder up through the rock to a hatch, a lantern on a post beside it
    for y in range(5, 12):
        for x, z in ((6, 1), (8, 1), (7, 2), (7, 0)):
            t.set(x, y, z, "tuff_bricks" if (x + y) % 3 else "tuff")
        t.set(7, y, 1, "air")
    for y in range(1, 12):
        t.set(7, y, 1, "ladder", {"facing": "south", "waterlogged": "false"})
    t.set(7, 12, 1, "oxidized_copper_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false",
                                                 "waterlogged": "false"})
    t.set(9, 12, 1, "cobblestone_wall")
    t.set(9, 13, 1, "lantern", {"hanging": "false", "waterlogged": "false"})
    # what the years did: cracks, roots through the ceiling, lichen in the dark, a seep and its moss
    if variant:
        caved(t, 50417729, 9, 12, 9, 12, h + 1, 0, rubble=("gravel", "cobbled_deepslate", "rooted_dirt", "tuff"))
        for x, z in ((10, 10), (11, 9), (9, 11)):
            t.set(x, h + 1, z, "rooted_dirt")
            t.set(x, h, z, "hanging_roots", {"waterlogged": "false"})
    for x, z in ((4, 6), (5, 10), (10, 5), (8, 9)):
        if t.empty(x, h, z):
            t.set(x, h, z, "hanging_roots", {"waterlogged": "false"})
    for x, z in ((4, 7), (6, 11), (10, 6)):
        if t.empty(x, 1, z):
            t.set(x, 1, z, "moss_carpet")
    t.set(4, 0, 10, "water", {"level": "0"})
    t.set(5, 0, 11, "moss_block")
    for (x, y, z), face in (((3, 3, 5), "west"), ((11, 2, 7), "east"), ((6, 4, 12), "south"), ((10, 3, 4), "north")):
        if t.empty(x, y, z):
            t.set(x, y, z, "glow_lichen", {s_: str(s_ == face).lower() for s_ in ("north", "south", "east", "west", "up", "down")}
                  | {"waterlogged": "false"})
    weather(t, 50417729 + variant, ground=1, crack=0.35, height=6, webs=0.07, inside=(x0, x1, z0, z1))
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


def corners8(x0, z0, size, cut):
    """The eight corners of an octagon: the square of side size from x0, z0 with its corners cut back by cut."""
    a, b = cut, size - 1 - cut
    m = size - 1
    return [(x0 + a, z0), (x0 + b, z0), (x0 + m, z0 + a), (x0 + m, z0 + b), (x0 + b, z0 + m), (x0 + a, z0 + m),
            (x0, z0 + b), (x0, z0 + a)]


def smock(t, y0, y1, x0, z0, size, cut, wall, post, batten):
    """An octagonal tower of boards from y0 to y1, posts at its eight corners and battens of trapdoors down its four
    straight sides."""
    octagon(t, y0, y1, x0, z0, size, wall, cut=cut)
    for x, z in corners8(x0, z0, size, cut):
        pillar(t, x, y0, y1, z, post)
    lo, hi, m = cut + 1, size - 2 - cut, size - 1
    for k in range(lo, hi + 1, 2):
        for y in range(y0, y1 + 1):
            t.trapdoor(x0 + k, y, z0 - 1, batten, "north", open_=True)
            t.trapdoor(x0 + k, y, z0 + m + 1, batten, "south", open_=True)
            t.trapdoor(x0 - 1, y, z0 + k, batten, "west", open_=True)
            t.trapdoor(x0 + m + 1, y, z0 + k, batten, "east", open_=True)


def windmill(rng, bare_sail=0):
    """A windmill of Kettle Ridge: an octagonal stone base two storeys high, a railed stage round it on braces, a body of
    birch boards between posts with battens, and a dark cap; four sails on the north side turning an axle and a shaft
    down to the millstone, the tally board by the door. One sail has lost its cloth."""
    t = Template(17, 27, 20)
    x0, z0 = 3, 5
    octagon(t, 0, 0, x0, z0, 11, "cobblestone", cut=3, fill="spruce_planks")
    octagon(t, 1, 7, x0, z0, 11, "cobblestone", cut=3)
    for x, z in corners8(x0, z0, 11, 3):
        pillar(t, x, 1, 7, z, "stone_bricks")
    octagon(t, 8, 8, x0, z0, 11, "cobblestone", cut=3, fill="spruce_planks")
    # the stage round the body where the stone ends: a floor of slabs out a block, railed, on braces
    for x in range(x0 - 1, x0 + 12):
        for z in range(z0 - 1, z0 + 12):
            dx, dz = min(x - x0 + 1, x0 + 11 - x), min(z - z0 + 1, z0 + 11 - z)
            if dx + dz >= 4 and (dx == 0 or dz == 0 or dx + dz == 4):
                t.slab(x, 8, z, "spruce_slab", "top")
                t.set(x, 9, z, "spruce_fence")
    for x, z, f in ((x0 + 5, z0 - 1, "south"), (x0 + 5, z0 + 11, "north"), (x0 - 1, z0 + 5, "east"),
                    (x0 + 11, z0 + 5, "west")):
        t.stairs(x, 7, z, "spruce_stairs", f, half="top")
    # the body: birch boards between birch posts, battens down its sides; a cap of spruce over it
    smock(t, 9, 15, x0 + 1, z0 + 1, 9, 2, "birch_planks", "stripped_birch_log", "birch_trapdoor")
    octagon(t, 16, 16, x0 + 1, z0 + 1, 9, "spruce_planks", cut=2, fill="spruce_planks")
    for k in range(5):
        ring_stairs(t, 16 + k, x0 + k, x0 + 10 - k, "spruce_stairs", z0=z0 + k, z1=z0 + 10 - k)
    t.slab(x0 + 5, 21, z0 + 5, "spruce_slab")
    cx, cz = x0 + 5, z0 + 5
    # the shaft from the millstone up to the axle, and the axle out through the cap to the hub
    t.set(cx, 1, cz, "grindstone", {"face": "floor", "facing": "north"})
    for y in range(2, 18):
        t.log(cx, y, cz, "stripped_spruce_log")
    for z in range(z0 - 2, cz):
        t.log(cx, 17, z, "spruce_log", "z")
    hub_z = z0 - 2
    # four sails round the hub: a stock of fence, cloth of wool on frames, one bare
    for i in range(1, 9):
        for x, y in ((cx, 17 + i), (cx, 17 - i), (cx - i, 17), (cx + i, 17)):
            if y >= 0:
                t.set(x, y, hub_z, "spruce_fence")
    arms = [lambda i, d: (cx + d, 17 + i), lambda i, d: (cx - d, 17 - i), lambda i, d: (cx + i, 17 - d),
            lambda i, d: (cx - i, 17 + d)]
    for n, arm in enumerate(arms):
        for i in range(2, 9):
            for d in (1, 2):
                x, y = arm(i, d)
                if y < 1:
                    continue
                if n == bare_sail:
                    if d == 2 and i % 2 == 0:
                        t.set(x, y, hub_z, "spruce_fence")
                else:
                    t.set(x, y, hub_z, "white_wool" if (i + d) % 4 else "spruce_planks")
    # the door to the south and its step, windows on the other sides
    t.door(cx, 1, z0 + 10, "spruce_door", "south")
    t.stairs(cx, 0, z0 + 11, "cobblestone_stairs", "north")
    for y in (3, 4):
        t.set(x0, y, cz, "glass_pane")
        t.set(x0 + 10, y, cz, "glass_pane")
    for y in (11, 12):
        t.set(x0 + 1, y, cz, "glass_pane")
        t.set(x0 + 9, y, cz, "glass_pane")
        t.set(cx, y, z0 + 9, "glass_pane")
    t.door(cx, 9, z0 + 9, "birch_door", "south")
    # inside: sacks and the miller's barrels, hay, the chest; a ladder up the shaft to the gears
    t.barrel(x0 + 3, 1, z0 + 2, "up", "residual:chests/windmill")
    t.barrel(x0 + 4, 1, z0 + 2, "up", "residual:chests/windmill")
    t.barrel(x0 + 3, 2, z0 + 2, "up")
    t.set(x0 + 7, 1, z0 + 2, "hay_block", {"axis": "y"})
    t.set(x0 + 7, 1, z0 + 3, "hay_block", {"axis": "y"})
    t.set(x0 + 7, 2, z0 + 2, "hay_block", {"axis": "x"})
    t.chest(x0 + 8, 1, z0 + 7, "west", "residual:chests/windmill_miller")
    for y in range(2, 16):
        t.set(cx, y, cz + 1, "ladder", {"facing": "south", "waterlogged": "false"})
    t.lantern(cx, 7, cz - 2, hanging=True)
    t.lantern(cx, 15, cz - 2, hanging=True)
    t.sign(cx - 1, 3, z0 + 11, "south", ["Tally board", "Top farm 410", "Brin's farm 380", "East farm 290"])
    t.sign(cx + 1, 3, z0 + 11, "south", ["North farm 300", "Low farm 220", "Windy farm 260", "The mill 3400"])
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


def windmill_farm(variant=0):
    """A farm on Kettle Ridge, as its farmers wrote of theirs: the windmill that squeaks on every turn, its tally board
    by the door; the top field of wheat, the long field of beetroot and the bottom field of potatoes and carrots, each
    with its ditch down the middle, a hedge on its north side and torches on posts along its edge; the scarecrow with a
    pumpkin for a head and an old hat; three hives by the flowers; the well in the yard; the barn with its hayloft and
    hoist, part of its roof fallen; the pig pen and the hen house. The second farm is laid out the other way round."""
    rng = random.Random(48215537 + variant)
    t = Template(35, 27, 31)
    for x in range(35):
        for z in range(31):
            t.set(x, 0, z, "grass_block", {"snowy": "false"})
    t.paste(windmill(rng, bare_sail=variant), 0, 0, 0)
    # the yard and its paths
    for x in range(7, 34):
        for z in (17, 18):
            if rng.random() < 0.85:
                t.set(x, 0, z, rng.choice(["dirt_path", "dirt_path", "coarse_dirt"]))
    for z in range(19, 31):
        if rng.random() < 0.85:
            t.set(14, 0, z, rng.choice(["dirt_path", "dirt_path", "coarse_dirt"]))
    # the well: a ring of cobblestone round the water, a little roof on posts
    for x in range(17, 20):
        for z in range(9, 12):
            if (x, z) == (18, 10):
                t.set(x, 0, z, "water", {"level": "0"})
            else:
                t.set(x, 0, z, "cobblestone")
                t.set(x, 1, z, "cobblestone_wall")
    for x, z in ((17, 9), (19, 11)):
        t.set(x, 2, z, "spruce_fence")
        t.set(x, 3, z, "spruce_fence")
    for x in range(16, 21):
        t.stairs(x, 4, 9, "spruce_stairs", "south")
        t.stairs(x, 4, 11, "spruce_stairs", "north")
        t.slab(x, 4, 10, "spruce_slab")
    t.set(18, 3, 10, "chain", {"axis": "y", "waterlogged": "false"})
    t.set(19, 1, 12, "cauldron")
    # the barn: a cobblestone footing, a spruce frame with boards set back, a hayloft door and its hoist, a dark roof
    bx0, bx1, bz0, bz1 = 22, 32, 3, 13
    t.fill(bx0, 0, bz0, bx1, 0, bz1, "cobblestone")
    t.fill(bx0 + 2, 0, bz0 + 2, bx1 - 2, 0, bz1 - 2, "coarse_dirt")
    for face, plane, a0, a1, posts in (("north", bz0, bx0, bx1, [22, 25, 29, 32]), ("south", bz1, bx0, bx1, [22, 25, 29, 32]),
                                       ("west", bx0, bz0, bz1, [3, 8, 13]), ("east", bx1, bz0, bz1, [3, 8, 13])):
        framed(t, face, plane, a0, a1, 1, 5, "stripped_spruce_log", "spruce_planks", posts=posts, rails=(3,),
               pad="cobblestone")
        _, (nx, nz) = run(face)
        for a in range(a0 + 1, a1):
            if a in posts:
                continue
            x, z = cell(face, plane, a)
            t.set(x - nx, 1, z - nz, "cobblestone")
            t.stairs(x, 1, z, "cobblestone_stairs", OPP[face])
    for u, y, a in roof(t, 6, bx0, bx1, bz0, bz1, "spruce_stairs", "x", brackets="spruce_stairs"):
        inward = 1 if u == bx0 else -1
        if a == 8 and y in (7, 8):
            t.set(u, y, a, "air")
            t.trapdoor(u + inward, y, a, "spruce_trapdoor", "east" if u == bx1 else "west", open_=True)
        else:
            t.set(u, y, a, "air")
            t.set(u + inward, y, a, "spruce_planks")
    t.log(bx1 + 1, 10, 8, "spruce_log", "x")
    t.log(bx1 + 2, 10, 8, "spruce_log", "x")
    for y in (7, 8, 9):
        t.set(bx1 + 2, y, 8, "chain", {"axis": "y", "waterlogged": "false"})
    t.fill(26, 1, bz1 - 1, 28, 4, bz1 - 1, "air")
    t.fill(26, 1, bz1, 28, 4, bz1, "air")
    for x in (26, 28):
        t.set(x, 1, bz1, "spruce_fence_gate", {"facing": "south", "in_wall": "false", "open": "true", "powered": "false"})
    t.fill(bx0 + 2, 5, bz0 + 2, bx1 - 2, 5, bz0 + 4, "spruce_planks")
    for x in range(bx0 + 2, bx1 - 1):
        if x % 3:
            t.set(x, 6, bz0 + 2, "hay_block", {"axis": "x"})
    for x in range(bx0 + 2, bx0 + 6):
        t.set(x, 1, bz0 + 2, "hay_block", {"axis": "y"})
        if x < bx0 + 4:
            t.set(x, 2, bz0 + 2, "hay_block", {"axis": "y"})
    t.chest(bx1 - 2, 1, bz0 + 2, "west", "residual:chests/windmill_miller")
    t.barrel(bx1 - 2, 1, bz0 + 3, "up", "residual:chests/windmill")
    t.set(bx0 + 2, 1, bz1 - 2, "composter", {"level": "5"})
    t.lantern(bx0 + 2, 4, 8, hanging=True)
    for x in (bx0 + 6, bx0 + 8):
        t.set(x, 1, bz1 - 2, "hay_block", {"axis": "z"})
    caved(t, 48215537 + variant, 25, 30, bz0 - 1, 7, 13, 5, rubble=("spruce_planks", "hay_block", "spruce_slab"))
    # the pig pen beside the barn, a shelter in its corner and the trough; the hen house by the well
    for x in range(23, 33):
        t.set(x, 1, 15, "spruce_fence")
    for z in range(15, 21):
        t.set(23, 1, z, "spruce_fence")
        t.set(32, 1, z, "spruce_fence")
    t.set(27, 1, 15, "spruce_fence_gate", {"facing": "north", "in_wall": "false", "open": "false", "powered": "false"})
    for x in range(24, 32):
        for z in range(16, 20):
            t.set(x, 0, z, "coarse_dirt" if rng.random() < 0.5 else "mud")
    for x in range(29, 32):
        for z in range(16, 18):
            t.slab(x, 3, z, "spruce_slab")
    for x, z in ((29, 17), (31, 17)):
        pillar(t, x, 1, 2, z, "spruce_fence")
    t.set(25, 1, 19, "water_cauldron", {"level": "2"})
    for x in range(12, 16):
        for z in range(2, 5):
            t.set(x, 0, z, "coarse_dirt")
    for x, z in ((12, 2), (15, 2), (12, 4), (15, 4)):
        pillar(t, x, 1, 2, z, "stripped_spruce_log")
    for x in range(11, 17):
        t.stairs(x, 3, 1, "spruce_stairs", "south")
        t.slab(x, 3, 2, "spruce_slab", "top")
        t.slab(x, 3, 3, "spruce_slab", "top")
        t.slab(x, 3, 4, "spruce_slab", "top")
        t.stairs(x, 3, 5, "spruce_stairs", "north")
    for x in (13, 14):
        t.set(x, 1, 2, "hay_block", {"axis": "x"})
    t.set(13, 1, 4, "composter", {"level": "3"})
    for x in range(11, 17):
        t.set(x, 1, 6, "spruce_fence")
    # the top field (wheat), the long field (beetroot), the bottom field (potatoes and carrots), hedges on their north
    # sides and torches on posts along them
    field(t, 1, 12, 20, 24, [("wheat", 7)], rng)
    field(t, 15, 33, 23, 27, [("beetroots", 3)], rng, ditch=25)
    field(t, 1, 12, 27, 29, [("potatoes", 7), ("carrots", 7)], rng, ditch=28)
    for x0_, x1_, z in ((1, 12, 19), (15, 33, 22), (1, 12, 26)):
        for x in range(x0_, x1_ + 1):
            t.set(x, 1, z, "oak_leaves", {"distance": "1", "persistent": "true", "waterlogged": "false"})
            if rng.random() < 0.35:
                t.set(x, 2, z, "oak_leaves", {"distance": "1", "persistent": "true", "waterlogged": "false"})
        for x in (x0_, x1_):
            t.set(x, 1, z, "air")
            t.set(x, 2, z, "air")
    for x, z in ((0, 21), (0, 25), (13, 21), (34, 24), (13, 28)):
        t.set(x, 1, z, "spruce_fence")
        t.set(x, 2, z, "torch")
    # the scarecrow in the long field, in Tam's oldest hat
    t.set(24, 1, 24, "spruce_fence")
    t.set(24, 2, 24, "brown_wool")
    t.set(23, 2, 24, "spruce_fence")
    t.set(25, 2, 24, "spruce_fence")
    t.set(24, 3, 24, "carved_pumpkin", {"facing": "south"})
    t.set(24, 4, 24, "black_carpet")
    # three hives by the flowers at the edge of the bottom field
    for x, z, flower in ((16, 29, "poppy"), (18, 30, "dandelion"), (20, 29, "cornflower"), (22, 30, "poppy"),
                         (17, 30, "cornflower"), (21, 30, "azure_bluet")):
        t.set(x, 1, z, flower)
    for x in (16, 18, 20):
        t.set(x, 1, 28, "beehive", {"facing": "south", "honey_level": "3"})
    # what the years did: moss on the stone, vines up the mill, grass between everything
    lights_out(t, 48215537 + variant, 0.7)
    broken_glass(t, 48215537 + variant, 0.3)
    weather(t, 48215537 + variant, ground=1, moss=0.45, crack=0.2, height=6, vines=0.12, webs=0.04, plants=0.25)
    if variant:
        t = mirrored(t)
    return t


def mirrored(t):
    """The template mirrored east to west, every block that faces a way turned with it."""
    sx, sy, sz = t.size
    flip = {"east": "west", "west": "east"}
    out = Template(sx, sy, sz)
    for (x, y, z), (name, props, data) in t.blocks.items():
        p = dict(props)
        if "facing" in p:
            p["facing"] = flip.get(p["facing"], p["facing"])
        if p.get("shape") in ("inner_left", "inner_right", "outer_left", "outer_right"):
            p["shape"] = {"inner_left": "inner_right", "inner_right": "inner_left", "outer_left": "outer_right",
                          "outer_right": "outer_left"}[p["shape"]]
        if "hinge" in p:
            p["hinge"] = {"left": "right", "right": "left"}[p["hinge"]]
        if "east" in p and "west" in p:
            p["east"], p["west"] = p["west"], p["east"]
        if "rotation" in p:
            p["rotation"] = str((16 - int(p["rotation"])) % 16)
        out.blocks[(sx - 1 - x, y, z)] = (name, p, data)
    for e in t.entities:
        e = dict(e)
        e["pos"] = [sx - e["pos"][0], e["pos"][1], e["pos"][2]]
        e["blockPos"] = [sx - 1 - e["blockPos"][0], e["blockPos"][1], e["blockPos"][2]]
        out.entities.append(e)
    return out


# the tower books, as the last keepers left them
TOWER_BOOK = [
    "Night 11. Lamp lit at dusk. Blinks from the south all night. Fair. Coal: 31 sacks.",
    "Night 12. Wind from the north. Blinks from the south till the second lamp change, then quiet. Sent again. "
    "Nothing.",
    "Night 13. Quiet from the south. Sent our call every hour. Lamp glass cleaned twice.",
    "Night 14. Lamp",
]
TOWER_BOOKS = [
    TOWER_BOOK,
    ["Ambrook tower. Lamp book, kept by Sedge.",
     "Night 3. Lamp lit. Blinks from Kettle Ridge: the price of flour. Passed it north.",
     "Night 4. Fog. Lamp lit all night. No blinks seen. Coal: 18 sacks.",
     "Night 5. Fog again. The mind on the stand answered the north tower's call before I had read it. I wrote it "
     "down anyway."],
    ["Tower nine. Lamp book.",
     "Night 1. Lamp lit. Blinks both ways. The road is busy with carts going south.",
     "Night 2. The lights in the north again, lower than the stars. I went up to"],
]


def buttress(t, x, z, face, y0, y1, block, stairs):
    """A buttress standing a block out from the wall at x, z on its face side, from y0 to y1, its top sloped back."""
    nx, nz = SIDES[face]
    pillar(t, x + nx, y0, y1, z + nz, block)
    t.stairs(x + nx, y1 + 1, z + nz, stairs, OPP[face])


def relay_tower(variant=0):
    """A relay tower as the keepers wrote of theirs, and the relay house at its foot. The tower: a cobblestone plinth, a
    base of stone with buttresses, then the shaft set back above it, banded at each floor; the store, the kitchen
    with the stove under its brick hood and the keeper's room one over another, a ladder up the north wall with a lamp
    at each floor; the walk round the top on its corbels, railed, with the bell that calls for coal; the lamp room of
    glass in a copper frame and the great lamp of glass and redstone inside it; and a roof of copper gone green
    carrying the rod. The relay house: stone below and a spruce frame above, its roof and chimney; the keeper's desk the
    redstone line comes down to, the tower book on its lectern, broken off mid-word; the big mind's stand, its crystal
    gone, ringed by quickeners in their cooling water; the porch and its bench; the woodpile. In the third the top has
    come down."""
    rng = random.Random(30817231 + variant)
    t = Template(19, 27, 25)
    cx, cz = 9, 7
    b0, b1, bz0, bz1 = 5, 13, 3, 11          # the base
    s0, s1, sz0, sz1 = 6, 12, 4, 10          # the shaft
    lawn(t, 19, 25, rng)
    # the plinth and the base: stone, buttresses at the corners and the middle of each side
    t.fill(b0 - 1, 0, bz0 - 1, b1 + 1, 0, bz1 + 1, "stone_bricks")
    ring_stairs(t, 1, b0 - 1, b1 + 1, "stone_brick_stairs", z0=bz0 - 1, z1=bz1 + 1)
    t.walls(b0, 1, bz0, b1, 5, bz1, "stone_bricks")
    t.fill(b0 + 1, 1, bz0 + 1, b1 - 1, 5, bz1 - 1, "air")
    t.fill(b0 + 1, 0, bz0 + 1, b1 - 1, 0, bz1 - 1, "spruce_planks")
    for face, x, z in (("north", cx, bz0), ("west", b0, cz), ("east", b1, cz)):
        buttress(t, x, z, face, 1, 4, "stone_bricks", "stone_brick_stairs")
    for x, z in ((b0, bz0), (b1, bz0), (b0, bz1), (b1, bz1)):
        buttress(t, x, z, "north" if z == bz0 else "south", 1, 4, "stone_bricks", "stone_brick_stairs")
        buttress(t, x, z, "west" if x == b0 else "east", 1, 4, "stone_bricks", "stone_brick_stairs")
    # the shaft set back above the base, a sloped ledge round the setback, a band at each floor, corners standing out
    t.fill(b0 + 1, 6, bz0 + 1, b1 - 1, 6, bz1 - 1, "spruce_planks")
    ring_stairs(t, 6, b0, b1, "stone_brick_stairs", z0=bz0, z1=bz1)
    t.walls(s0, 6, sz0, s1, 14, sz1, "stone_bricks")
    t.fill(s0 + 1, 7, sz0 + 1, s1 - 1, 14, sz1 - 1, "air")
    t.fill(s0 + 1, 10, sz0 + 1, s1 - 1, 10, sz1 - 1, "spruce_planks")
    for x in range(s0 + 1, s1):
        t.stairs(x, 10, sz0 - 1, "stone_brick_stairs", "south", half="top")
        t.stairs(x, 10, sz1 + 1, "stone_brick_stairs", "north", half="top")
    for z in range(sz0 + 1, sz1):
        t.stairs(s0 - 1, 10, z, "stone_brick_stairs", "east", half="top")
        t.stairs(s1 + 1, 10, z, "stone_brick_stairs", "west", half="top")
    for x, z in ((s0, sz0), (s1, sz0), (s0, sz1), (s1, sz1)):
        for y in range(7, 14):
            t.set(x + (-1 if x == s0 else 1), y, z, "chiseled_stone_bricks" if y in (7, 13) else "stone_bricks")
            t.set(x, y, z + (-1 if z == sz0 else 1), "chiseled_stone_bricks" if y in (7, 13) else "stone_bricks")
    # windows: two to a side below, one to a side above, each on a sill
    for x in (7, 11):
        window(t, "north", x, 2, bz0, h=2, sill="stone_brick_stairs")
    for y in (7, 11):
        window(t, "north", cx, y, sz0, h=2, sill="stone_brick_stairs")
        window(t, "west", s0, y, cz, h=2, sill="stone_brick_stairs")
        window(t, "east", s1, y, cz, h=2, sill="stone_brick_stairs")
    for z in (5, 9):
        window(t, "west", b0, 2, z, h=2, sill="stone_brick_stairs")
        window(t, "east", b1, 2, z, h=2, sill="stone_brick_stairs")
    t.door(cx, 1, bz1, "spruce_door", "south")
    # inside: the ladder up a pier on the north wall, a lamp at each floor; the store, the kitchen, the keeper's room
    for y in range(1, 6):
        t.set(cx, y, bz0 + 1, "stone_bricks")
    for y in range(1, 15):
        t.set(cx, y, sz0 + 1, "ladder", {"facing": "south", "waterlogged": "false"})
    for y in (6, 10):
        t.set(cx + 1, y, sz0 + 1, "spruce_trapdoor", {"facing": "west", "half": "top", "open": "true", "powered": "false",
                                                      "waterlogged": "false"})
    t.lantern(cx + 2, 5, cz, hanging=True)
    t.lantern(cx - 2, 9, cz, hanging=True)
    t.lantern(cx + 2, 14, cz, hanging=True)
    t.barrel(b0 + 1, 1, bz0 + 1, "up", "residual:chests/relay_tower_store")
    t.barrel(b0 + 2, 1, bz0 + 1, "up", "residual:chests/relay_tower_store")
    t.barrel(b0 + 1, 2, bz0 + 1, "up")
    t.fill(b1 - 2, 1, bz1 - 2, b1 - 1, 1, bz1 - 1, "coal_block")
    t.set(b1 - 1, 2, bz1 - 1, "coal_block")
    t.set(b1 - 1, 1, bz0 + 1, "hay_block", {"axis": "y"})
    t.set(s0 + 1, 7, sz1 - 1, "smoker", {"facing": "north", "lit": "false"})
    t.fill(s0 + 1, 8, sz1 - 1, s0 + 1, 9, sz1 - 1, "bricks")
    t.barrel(s0 + 2, 7, sz1 - 1, "up", "residual:chests/relay_tower_kitchen")
    t.set(s1 - 1, 7, sz1 - 1, "water_cauldron", {"level": "2"})
    t.set(s1 - 2, 7, cz, "spruce_fence")
    t.set(s1 - 2, 8, cz, "spruce_pressure_plate", {"powered": "false"})
    t.stairs(s1 - 1, 7, cz, "spruce_stairs", "west")
    t.bed(s0 + 1, 11, sz1 - 2, "red", "south")
    t.set(s1 - 1, 11, sz1 - 1, "bookshelf")
    t.set(s1 - 1, 12, sz1 - 1, "bookshelf")
    t.chest(s1 - 1, 11, cz, "west", "residual:chests/relay_tower_keeper")
    for x in (cx, cx + 1):
        for z in (cz, cz + 1):
            t.set(x, 11, z, "red_carpet")
    # the walk round the top: corbels, a floor of slabs, a railing; the bell that calls for coal and its chain
    t.fill(s0, 15, sz0, s1, 15, sz1, "stone_bricks")
    ring_stairs(t, 14, b0, b1, "stone_brick_stairs", half="top", z0=bz0, z1=bz1)
    for x in range(b0, b1 + 1):
        for z in range(bz0, bz1 + 1):
            if x in (b0, b1) or z in (bz0, bz1):
                t.slab(x, 15, z, "stone_brick_slab", "top")
                t.set(x, 16, z, "spruce_fence")
    t.set(b0, 16, cz, "bell", {"attachment": "floor", "facing": "north", "powered": "false"})
    for y in range(2, 15):
        t.set(b0 - 1, y, cz, "chain", {"axis": "y", "waterlogged": "false"})
    t.barrel(b0 - 1, 1, cz, "up")
    # the lamp room: glass in a copper frame, the great lamp of glass and redstone in the middle
    l0, l1, lz0, lz1 = 7, 11, 5, 9
    for y in range(16, 20):
        for k in range(l0 + 1, l1):
            t.set(k, y, lz0, "glass_pane")
            t.set(k, y, lz1, "glass_pane")
        for k in range(lz0 + 1, lz1):
            t.set(l0, y, k, "glass_pane")
            t.set(l1, y, k, "glass_pane")
        for x, z in ((l0, lz0), (l1, lz0), (l0, lz1), (l1, lz1)):
            t.set(x, y, z, "weathered_cut_copper")
    t.door(cx, 16, lz1, "spruce_door", "south")
    for x in (cx - 1, cx, cx + 1):
        for z in (cz - 1, cz, cz + 1):
            corner = x != cx and z != cz
            for y in (17, 18):
                t.set(x, y, z, "glass" if corner else "redstone_lamp", None if corner else {"lit": "false"})
    t.fill(cx - 1, 19, cz - 1, cx + 1, 19, cz + 1, "cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    # the roof: copper gone green, flared at the eaves, the rod at the point
    for k in range(3):
        ring_stairs(t, 20 + k, l0 - 1 + k, l1 + 1 - k, "oxidized_cut_copper_stairs", z0=lz0 - 1 + k, z1=lz1 + 1 - k)
    ring_stairs(t, 20, l0 - 2, l1 + 2, "weathered_cut_copper_stairs", z0=lz0 - 2, z1=lz1 + 2)
    t.fill(l0, 20, lz0, l1, 20, lz1, "weathered_cut_copper")
    t.set(cx, 23, cz, "oxidized_cut_copper")
    t.set(cx, 24, cz, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    # the relay house at the tower's foot: stone below, a spruce frame with calcite set back above, a roof, a chimney
    h0, h1, hz0, hz1 = 3, 15, 12, 19
    t.fill(h0, 0, hz0, h1, 0, hz1, "stone_bricks")
    boards(t, h0 + 2, h1 - 2, hz0 + 2, hz1 - 2, 0, "spruce_planks", "stripped_spruce_log", axis="z")
    for face, plane, a0, a1, posts in (("south", hz1, h0, h1, [3, 7, 11, 15]), ("west", h0, hz0, hz1, [12, 16, 19]),
                                       ("east", h1, hz0, hz1, [12, 16, 19]), ("north", hz0, h0, h1, [3, 7, 11, 15])):
        framed(t, face, plane, a0, a1, 1, 4, "stripped_spruce_log", "calcite", posts=posts, rails=(3,), pad="stone_bricks")
        _, (nx, nz) = run(face)
        for a in range(a0 + 1, a1):
            if a in posts:
                continue
            x, z = cell(face, plane, a)
            t.set(x - nx, 1, z - nz, "stone_bricks")
    for u, y, a in roof(t, 5, h0, h1, hz0, hz1, "spruce_stairs", "x", brackets="spruce_stairs"):
        inward = 1 if u == h0 else -1
        t.set(u, y, a, "stripped_spruce_log" if a == 16 else "air")
        if a != 16:
            t.set(u + inward, y, a, "calcite")
    t.fill(h1 + 1, 1, 15, h1 + 1, 6, 16, "bricks")
    chimney(t, h1 + 1, 15, 7, 11, "bricks", cap="brick_slab")
    t.fill(cx - 1, 1, hz0, cx + 1, 3, hz0 + 1, "air")
    t.door(cx, 1, hz1 - 1, "spruce_door", "south")
    t.set(cx, 1, hz1, "air")
    for x in (5, 13):
        window(t, "south", x, 2, hz1 - 1, h=2, shutters="spruce_trapdoor")
    for z in (14, 17):
        window(t, "west", h0 + 1, 2, z, h=2, shutters="spruce_trapdoor")
    window(t, "east", h1 - 1, 2, 13, h=2, shutters="spruce_trapdoor")
    # the porch and its bench
    for x in (cx - 2, cx + 2):
        pillar(t, x, 1, 3, hz1 + 2, "stripped_spruce_log")
    for x in range(cx - 3, cx + 4):
        t.stairs(x, 4, hz1 + 2, "spruce_stairs", "north")
        t.slab(x, 4, hz1 + 1, "spruce_slab", "top")
    for x in (cx - 1, cx + 1):
        t.stairs(x, 1, hz1 + 1, "spruce_stairs", "north")
    # the keeper's side: the desk the redstone line comes down to, the tower book on its lectern
    book = TOWER_BOOKS[variant]
    t.set(5, 1, 14, "lectern", {"facing": "south", "has_book": "true", "powered": "false"},
          {"id": "minecraft:lectern", "Page": 0, "Book": {"id": "minecraft:written_book", "count": 1, "components": {
              "minecraft:written_book_content": {"title": {"raw": "Tower book"}, "author": "the keeper", "resolved": 1,
                                                 "pages": [{"raw": p} for p in book]}}}})
    for z in range(13, 15):
        t.set(cx, 1, z, "redstone_wire", {"east": "none", "west": "none", "north": "side", "south": "side", "power": "0"})
    for x in (6, 7, 8):
        t.set(x, 1, 14, "redstone_wire", {"east": "side", "west": "side", "north": "none", "south": "none", "power": "0"})
    t.barrel(5, 1, 15, "up")
    t.bed(5, 1, 16, "blue", "south")
    t.set(13, 1, 17, "furnace", {"facing": "west", "lit": "false"})
    t.lantern(7, 4, 15, hanging=True)
    # the big mind's side: its stand, the crystal gone, ringed by quickeners sunk in the floor with their cooling water
    t.set(12, 0, 15, "stone_bricks")
    t.set(12, 1, 15, "residual:model_server", {"facing": "west", "loaded": "false", "serving": "false"})
    for x, z in ((11, 14), (13, 14), (11, 16), (13, 16)):
        t.set(x, 0, z, "residual:accelerator")
    for x, z in ((12, 14), (12, 16), (11, 15), (13, 15)):
        t.set(x, 0, z, "water", {"level": "0"})
    t.lantern(12, 4, 15, hanging=True)
    # outside: the path from the porch, the woodpile under its slab roof, torches round the foot
    for z in range(hz1 + 1, 25):
        for x in (cx - 1, cx, cx + 1):
            if rng.random() < 0.8:
                t.set(x, 0, z, rng.choice(["dirt_path", "dirt_path", "gravel", "coarse_dirt"]))
    for z in range(13, 19):
        for y in (1, 2):
            if z != 18 or y == 1:
                t.log(1, y, z, "spruce_log", "z")
        t.slab(1, 3, z, "spruce_slab")
        t.slab(2, 3, z, "spruce_slab")
    for x, z in ((2, 1), (16, 1), (16, 21), (2, 21)):
        t.set(x, 0, z, "stone_bricks")
        t.set(x, 1, z, "spruce_fence")
        t.set(x, 2, z, "torch")
    t.suspicious(6, 0, 21, "gravel", "residual:archaeology/ruin")
    # what the years did, and in the third, the storm that brought the top down
    if variant == 2:
        broken(t, 30817231, cx + 3, cz - 3, 7.5, 26, 14, rubble=["stone_bricks", "stone_bricks", "gravel"],
               floor=15)
        for x, z in ((10, 1), (12, 2), (14, 4), (15, 2), (13, 0)):
            t.set(x, 1, z, rng.choice(["stone_bricks", "cracked_stone_bricks", "oxidized_cut_copper"]))
        t.set(15, 1, 3, "oxidized_cut_copper_stairs", {"facing": "east", "half": "bottom", "shape": "straight",
                                                       "waterlogged": "false"})
    lights_out(t, 30817231 + variant, 0.7)
    broken_glass(t, 30817231 + variant, 0.25 if variant < 2 else 0.6)
    weather(t, 30817231 + variant, ground=1, moss=0.5, crack=0.25, height=8, vines=0.2, webs=0.05, plants=0.3,
            inside=(s0, s1, sz0, sz1))
    return t


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


WREN_BOOK = [
    "Wren's day book. Who came, what for, what I gave.",
    "Pell's boy, a cut hand from the reeds. Washed it, honey on it, wrapped it. Back tomorrow.",
    "Old Ash, the cough again. Steam and the green jar. He says it is better. It is not, yet.",
    "Moss took the jars down and put them back in the right order without being told. I did not know it could read "
    "the labels.",
]


def legs(t, x, z, y_top, wood, marker="brown_stained_glass"):
    """A stilt of wood from y_top down to the template's floor, ending in the marker the legs processor runs down to the
    ground."""
    for y in range(1, y_top + 1):
        t.log(x, y, z, wood)
    t.set(x, 0, z, marker)


def stilt_house(variant=0):
    """A house of Lowmarsh on stilts over the marsh, its stilts down to the marsh floor: a railed deck of spruce, walls
    of mud brick set back between posts of mangrove, a steep mangrove roof gone green with moss, a boardwalk to the bank
    on posts with a lamp on its landing. Wren's healing house keeps her remedies: high shelves of labeled jars, the
    brewing stand and the cauldron on the bench, the stove, beds for the sick, her day book; the well hatch over the
    water and the shed on the deck. The boat house keeps the day's catch and the tally on its wall."""
    rng = random.Random(77120593 + variant)
    t = Template(15, 16, 23)
    deck = 5
    x0, x1, z0, z1 = 2, 12, 1, 11
    # the stilts down to the marsh floor, and the deck on them, railed
    for x in (x0, 7, x1):
        for z in (z0, 6, z1):
            legs(t, x, z, deck - 1, "mangrove_log")
    boards(t, x0, x1, z0, z1, deck, "spruce_planks", "stripped_spruce_log", axis="z")
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            t.slab(x, deck, z, "spruce_slab", "top")
    for k in range(x0, x1 + 1):
        for z in (z0, z1):
            if k in (x0, 7, x1):
                t.log(k, deck, z, "mangrove_log")
                t.set(k, deck + 1, z, "mangrove_fence")
            elif rng.random() < 0.85:
                t.set(k, deck + 1, z, "spruce_fence")
    for k in range(z0 + 1, z1):
        for x in (x0, x1):
            if k == 6:
                t.log(x, deck, k, "mangrove_log")
                t.set(x, deck + 1, k, "mangrove_fence")
            elif rng.random() < 0.85:
                t.set(x, deck + 1, k, "spruce_fence")
    # the house: mud brick set back between mangrove posts, a course of packed mud below, a steep mossy roof
    hx0, hx1, hz0, hz1 = 3, 10, 2, 8
    t.fill(hx0 + 1, deck + 1, hz0 + 1, hx1 - 1, deck + 4, hz1 - 1, "air")
    for face, plane, a0, a1, posts in (("north", hz0, hx0, hx1, [3, 6, 10]), ("south", hz1, hx0, hx1, [3, 7, 10]),
                                       ("west", hx0, hz0, hz1, [2, 5, 8]), ("east", hx1, hz0, hz1, [2, 5, 8])):
        framed(t, face, plane, a0, a1, deck + 1, deck + 4, "stripped_mangrove_log", "mud_bricks", posts=posts,
               rails=(deck + 1,) if face != "south" else ())
    for pos, (name, _, _) in list(t.blocks.items()):
        if name == "minecraft:mud_bricks" and rng.random() < 0.3:
            t.set(*pos, "packed_mud")
    for x in range(hx0 + 2, hx1 - 1):
        for z in range(hz0 + 2, hz1 - 1):
            t.set(x, deck + 5, z, "mangrove_planks")
    for u, y, a in roof(t, deck + 5, hx0, hx1, hz0, hz1, "mangrove_stairs", "x", over=1, verge=1,
                        brackets="mangrove_stairs"):
        inward = 1 if u == hx0 else -1
        t.set(u, y, a, "air")
        t.set(u + inward, y, a, "packed_mud" if a == 5 else "mud_bricks")
    for x in range(hx0 - 1, hx1 + 2):
        for z, y in ((hz0 - 1, deck + 6), (hz1 + 1, deck + 6), (hz0, deck + 7), (hz1, deck + 7)):
            if rng.random() < 0.55:
                t.set(x, y, z, "moss_carpet")
    # the door and the windows
    t.door(7, deck + 1, hz1 - 1, "mangrove_door", "south")
    t.set(7, deck + 1, hz1, "air")
    for x in (5, 9):
        window(t, "south", x, deck + 2, hz1 - 1, h=2, shutters="mangrove_trapdoor")
    window(t, "north", 8, deck + 2, hz0 + 1, h=2, shutters="mangrove_trapdoor")
    for z in (4, 6):
        window(t, "west", hx0 + 1, deck + 2, z, h=2)
        window(t, "east", hx1 - 1, deck + 2, z, h=2)
    inside = deck + 1
    if variant == 0:
        # high shelves of labeled jars along the north wall, the bench of remedies, beds for the sick, the stove
        for x in range(hx0 + 2, hx1 - 1):
            t.slab(x, inside + 2, hz0 + 2, "spruce_slab", "top")
            t.set(x, inside + 3, hz0 + 2, "decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
        for x, label in ((5, ["Willow bark"]), (6, ["Mint"]), (8, ["For fever"]), (9, ["Honey"])):
            t.sign(x, inside + 1, hz0 + 3, "south", label)
        t.barrel(hx0 + 2, inside, hz0 + 2, "up", "residual:chests/stilt_house_remedies")
        t.set(hx0 + 3, inside, hz0 + 2, "brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false",
                                                           "has_bottle_2": "true"})
        t.set(hx0 + 4, inside, hz0 + 2, "cauldron")
        t.set(hx1 - 2, inside, hz0 + 2, "smoker", {"facing": "south", "lit": "false"})
        t.bed(hx0 + 2, inside, 5, "green", "south")
        t.bed(hx1 - 2, inside, 5, "white", "south")
        t.chest(hx1 - 2, inside, hz1 - 2, "west", "residual:chests/stilt_house_wren")
        t.set(hx0 + 2, inside, hz1 - 2, "lectern", {"facing": "east", "has_book": "true", "powered": "false"},
              {"id": "minecraft:lectern", "Page": 0, "Book": {"id": "minecraft:written_book", "count": 1, "components": {
                  "minecraft:written_book_content": {"title": {"raw": "Day book"}, "author": "Wren", "resolved": 1,
                                                     "pages": [{"raw": p} for p in WREN_BOOK]}}}})
        t.lantern(7, deck + 4, 5, hanging=True)
        # the well hatch over the water and its bucket chain; the shed on the deck
        t.trapdoor(11, deck, 3, "spruce_trapdoor", "south", open_=True)
        t.set(11, deck, 3, "air")
        for y in range(1, deck):
            t.set(11, y, 3, "chain", {"axis": "y", "waterlogged": "false"})
        t.set(11, deck + 1, 3, "spruce_fence")
        t.set(11, deck + 2, 3, "spruce_fence")
        for x in (1, 2):
            for z in (8, 9, 10):
                t.set(x, deck + 4, z, "mangrove_slab", {"type": "bottom", "waterlogged": "false"})
        pillar(t, 1, deck + 1, deck + 3, 8, "mangrove_fence")
        pillar(t, 1, deck + 1, deck + 3, 10, "mangrove_fence")
        t.barrel(2, deck + 1, 9, "up", "residual:chests/stilt_house_fish")
        t.set(2, deck + 1, 10, "composter", {"level": "4"})
    else:
        # the boat house: nets and the catch, the tally on the wall, a ramp down to the water
        t.barrel(hx0 + 2, inside, hz0 + 2, "up", "residual:chests/stilt_house_fish")
        t.barrel(hx0 + 3, inside, hz0 + 2, "up", "residual:chests/stilt_house_fish")
        t.barrel(hx0 + 2, inside + 1, hz0 + 2, "up")
        for x in range(hx0 + 4, hx1 - 1):
            t.set(x, inside, hz0 + 2, "cobweb")
        t.sign(8, inside + 1, hz0 + 2, "south", ["Tally", "Wren: 3 salmon", "Moss: 2 cod", "Ash: none, again"])
        t.chest(hx1 - 2, inside, hz1 - 2, "west", "residual:chests/stilt_house_wren")
        t.set(hx0 + 2, inside, hz1 - 2, "crafting_table")
        for k in range(1, 5):
            t.stairs(x1 + 1, deck - k + 1, 2 + k, "spruce_stairs", "north")
    # the boardwalk to the bank on its posts, the landing with a lamp on its post
    t.set(7, deck + 1, z1, "air")
    for z in range(z1 + 1, 23):
        t.set(7, deck, z, "spruce_planks")
        t.slab(6, deck, z, "spruce_slab", "top")
    for z in (14, 18, 22):
        legs(t, 7, z, deck - 1, "mangrove_log")
        legs(t, 6, z, deck - 1, "mangrove_log")
    t.set(8, deck, 18, "spruce_planks")
    legs(t, 8, 18, deck - 1, "mangrove_log")
    t.set(8, deck + 1, 18, "spruce_fence")
    t.set(8, deck + 2, 18, "spruce_fence")
    t.lantern(8, deck + 3, 18)
    # what the years did: vines down the stilts and the walls, moss, lily pads round the posts
    lights_out(t, 77120593 + variant, 0.6)
    broken_glass(t, 77120593 + variant, 0.3)
    weather(t, 77120593 + variant, ground=deck + 1, vines=0.3, webs=0.06, inside=(hx0 + 1, hx1 - 1, hz0 + 1, hz1 - 1))
    return t


def slipway(variant=0):
    """A slip at Saltreach, where the ships were built: a stone quay at the water's edge, buttressed, with bollards and
    steps down to the water; on it the shipwright's shed with its window for mending nets, the wood store under its
    lean-to, the fish market's price board and benches facing the sea; and running out from it over the water on posts
    the slip, on it the frame of a ship never finished, a crane leaning over it. Its sister slip beside it is a ramp of
    stone into the sea. In the second the frame on the slip is a big ship's, three times as long as any boat."""
    rng = random.Random(90442817 + variant)
    t = Template(25, 19, 30)
    q = 4  # the quay's top, four above the template's floor, which sits below the sea
    stone = ["stone_bricks"] * 6 + ["mossy_stone_bricks", "cracked_stone_bricks", "stone"]
    # the quay: its paving resting on the land behind, built down to the sea floor toward the water in courses that
    # step back unevenly; its back edge ragged where the land took it; a course of chiseled stone along its edge
    for x in range(25):
        for z in range(10):
            if z < 2 and rng.random() < 0.35 * (2 - z):
                continue
            bottom = q - 1 if z < 5 else max(0, q - 2 - (z - 5) - rng.randint(0, 1))
            for y in range(bottom, q + 1):
                t.set(x, y, z, rng.choice(stone))
        if x % 6 == 3:
            t.set(x, q, 9, "chiseled_stone_bricks")
    for x in (1, 6, 12, 18, 23):
        t.barrel(x, q + 1, 0 if x % 2 else 8, "up", "residual:chests/slipway_stores" if x == 12 else None)
    t.set(20, q + 1, 6, "water_cauldron", {"level": "2"})
    t.set(19, q + 1, 6, "composter", {"level": "2"})
    # a ladder down the quay's face to the boats, mooring chains on the bollards, hatches in the slip
    for y in range(1, q + 1):
        t.set(9, y, 10, "ladder", {"facing": "south", "waterlogged": "false"})
    for x in (3, 6):
        t.set(x, q + 2, 9, "chain", {"axis": "x", "waterlogged": "false"})
    for z in (15, 20):
        t.trapdoor(12, q, z, "spruce_trapdoor", "north", half="top")
        t.trapdoor(18, q, z, "spruce_trapdoor", "south", half="top", open_=True)
    t.barrel(23, q + 1, 3, "east")
    t.barrel(23, q + 2, 3, "north")
    t.set(10, q + 1, 9, "spruce_fence_gate", {"facing": "south", "in_wall": "false", "open": "true", "powered": "false"})
    t.lantern(14, q + 1, 9)
    # buttresses on its face where no slip runs out, sloped back at the top; steps down to the water; bollards
    for x in (0, 8, 21, 24):
        pillar(t, x, 0, q - 1, 10, "stone_bricks")
        t.stairs(x, q, 10, "stone_brick_stairs", "north")
    for k in range(1, q + 1):
        for x in (22, 23):
            t.stairs(x, q - k + 1, 9 + k, "stone_brick_stairs", "north")
            pillar(t, x, 0, q - k, 9 + k, "stone_bricks")
    for x in (2, 7, 22):
        t.set(x, q + 1, 9, "stone_brick_wall")
        t.set(x, q + 2, 9, "stone_brick_wall")
    # the sister slip: a ramp of stone into the sea, stepping down every three
    for z in range(10, 23):
        top = q - (z - 10) // 3
        for x in range(1, 7):
            for y in range(top):
                t.set(x, y, z, rng.choice(stone))
            if (z - 10) % 3 == 2:
                t.slab(x, top, z, "stone_brick_slab")
            else:
                t.set(x, top, z, "stone_bricks")
    # the slip: a stage of spruce on posts down to the sea floor, its edges beamed
    for x in range(10, 21):
        for z in range(10, 28):
            if x in (10, 20):
                t.log(x, q, z, "stripped_spruce_log", "z")
            else:
                t.slab(x, q, z, "spruce_slab", "top")
    for x in (10, 15, 20):
        for z in (13, 18, 23, 27):
            legs(t, x, z, q - 1, "spruce_log")
    # the frame on the slip: a keel, ribs that flare toward the gunwale, the stem at the bow and the post at the stern
    k = 15
    if variant == 0:
        stern, bow, rib = 12, 24, ((1, 1), (2, 2), (2, 3), (3, 4), (3, 5))
    else:
        stern, bow, rib = 11, 27, ((1, 1), (2, 2), (3, 3), (4, 4), (4, 5), (4, 6), (5, 7))
    for z in range(stern, bow + 1):
        t.log(k, q + 1, z, "spruce_log", "z")
    for z in range(stern + 1, bow, 2):
        for d in (-1, 1):
            for dx, dy in rib:
                t.log(k + d * dx, q + 1 + dy, z, "stripped_spruce_log")
    top = q + 1 + rib[-1][1]
    for y in range(q + 2, top + 2):
        t.log(k, y, bow, "spruce_log")
    t.stairs(k, top + 2, bow, "spruce_stairs", "south")
    for y in range(q + 2, top):
        t.log(k, y, stern, "spruce_log")
    if variant == 0:
        for z in range(stern + 2, bow - 1):
            for d in (-1, 1):
                if (k + d, q + 2, z) not in t.blocks:
                    t.set(k + d, q + 2, z, "spruce_planks")
                if (k + 2 * d, q + 3, z) not in t.blocks:
                    t.set(k + 2 * d, q + 3, z, "spruce_planks")
        t.fill(k - 2, q + 4, stern, k + 2, q + 6, stern, "spruce_planks")
    # trestles alongside, with planks across them to work from
    for z in range(stern + 1, bow, 3):
        for x in (11, 19):
            pillar(t, x, q + 1, q + 2, z, "spruce_fence")
            t.slab(x, q + 3, z, "spruce_slab")
            t.slab(x, q + 3, z + 1, "spruce_slab")
    # the crane: a post at the quay's edge, its jib out over the slip, a chain hanging with a lamp on it
    pillar(t, 21, q + 1, q + 10, 9, "spruce_log")
    for z in range(9, 17):
        t.log(21, q + 10, z, "spruce_log", "z")
    t.stairs(21, q + 9, 10, "spruce_stairs", "south", half="top")
    for y in range(q + 6, q + 10):
        t.set(21, y, 16, "chain", {"axis": "y", "waterlogged": "false"})
    t.lantern(21, q + 5, 16, hanging=True)
    # the shipwright's shed: a stone footing, a spruce frame with boards set back, the net window to the sea
    s0, s1, sz0, sz1 = 1, 8, 1, 7
    for face, plane, a0, a1, posts in (("north", sz0, s0, s1, [1, 4, 8]), ("south", sz1, s0, s1, [1, 8]),
                                       ("west", s0, sz0, sz1, [1, 4, 7]), ("east", s1, sz0, sz1, [1, 4, 7])):
        framed(t, face, plane, a0, a1, q + 1, q + 4, "stripped_spruce_log", "spruce_planks", posts=posts,
               rails=(q + 3,), pad="stone_bricks")
    for u, y, a_ in roof(t, q + 5, s0, s1, sz0, sz1, "spruce_stairs", "z", brackets="spruce_stairs"):
        inward = 1 if u == sz0 else -1
        t.set(a_, y, u, "air")
        t.set(a_, y, u + inward, "spruce_planks")
    for x in range(3, 7):
        t.set(x, q + 2, sz1 - 1, "glass_pane")
        t.set(x, q + 3, sz1 - 1, "glass_pane")
    for x in (3, 4, 5, 6):
        t.stairs(x, q + 1, sz1 + 1, "spruce_stairs", "north")
    t.door(s1 - 1, q + 1, 4, "spruce_door", "east")
    t.set(s1, q + 1, 4, "air")
    t.chest(s0 + 2, q + 1, sz0 + 2, "south", "residual:chests/slipway_shipwright")
    t.set(s0 + 3, q + 1, sz0 + 2, "crafting_table")
    t.set(s0 + 4, q + 1, sz0 + 2, "grindstone", {"face": "floor", "facing": "south"})
    t.barrel(s0 + 2, q + 1, sz1 - 2, "up", "residual:chests/slipway_stores")
    t.set(s0 + 5, q + 1, sz0 + 2, "smoker", {"facing": "south", "lit": "false"})
    t.set(s1 - 1, q + 1, sz1 - 2, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    t.set(16, q + 1, 7, "campfire", {"facing": "south", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    t.lantern(4, q + 4, 4, hanging=True)
    if variant:
        t.sign(s1 + 1, q + 3, 3, "east", ["Gone to the", "big slip.", "", "C."])
    # the net rack outside the window
    for x in (2, 7):
        pillar(t, x, q + 1, q + 3, sz1 + 2, "spruce_fence")
    for x in range(3, 7):
        t.set(x, q + 3, sz1 + 2, "cobweb")
    # the wood store under its lean-to; the price board; the benches facing the sea
    for x in range(11, 19):
        for z in (2, 3):
            t.log(x, q + 1, z, "stripped_spruce_log", "x")
            if x < 17:
                t.log(x, q + 2, z, "stripped_spruce_log", "x")
    for x in (10, 19):
        pillar(t, x, q + 1, q + 3, 5, "spruce_fence")
    for x in range(10, 20):
        for z, y in ((1, q + 4), (2, q + 4), (3, q + 4), (4, q + 4), (5, q + 3)):
            t.slab(x, y, z, "spruce_slab", "top")
    for x in range(21, 24):
        t.set(x, q + 1, 1, "spruce_planks")
        t.set(x, q + 2, 1, "spruce_planks")
    t.sign(22, q + 2, 2, "south", ["Fish market", "Cod: four", "for a loaf", "Salmon: two"])
    for x in (12, 13, 17, 18):
        t.stairs(x, q + 1, 8, "spruce_stairs", "north")
    # what the sea and the years did: kelp and seagrass at the quay's foot, moss on the stone, the lamp out
    for x in range(0, 25):
        if x not in range(10, 21) and rng.random() < 0.4:
            t.set(x, 0, 11, "seagrass")
    for x, z in ((9, 12), (21, 14), (7, 16)):
        for y in range(0, 2):
            t.set(x, y, z, "kelp_plant" if y == 0 else "kelp", {"age": "20"} if y else None)
    lights_out(t, 90442817 + variant, 0.5)
    broken_glass(t, 90442817 + variant, 0.4)
    weather(t, 90442817 + variant, ground=q + 1, moss=0.5, height=4, vines=0.1, webs=0.05, plants=0.0)
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


def waymark(t, x, z, face="west", inn=None):
    """Dunn's waymark: a pillar of stone three blocks high on a stepped base, its figure cut in the middle stone, a
    torch on top and little stones left on the cap; an arrow to the nearest inn on a sign where given."""
    for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set(x + dx, 0, z + dz, "cobblestone")
    for f, (dx, dz) in SIDES.items():
        t.stairs(x + dx, 1, z + dz, "stone_brick_stairs", OPP[f])
    t.set(x, 1, z, "stone_bricks")
    t.set(x, 2, z, "chiseled_stone_bricks")
    t.set(x, 3, z, "stone_bricks")
    t.set(x, 4, z, "torch")
    dx, dz = SIDES[face]
    for ox, oz in ((dx, dz + 1), (dx, dz - 1), (-dz, dx)):
        t.set(x + ox + dx, 1, z + oz + dz, "stone_button", {"face": "floor", "facing": face, "powered": "false"})
    if inn:
        t.sign(x + dx, 3, z + dz, face, inn)
    t.suspicious(x + 2 * dx, 0, z + 2 * dz, "gravel", "residual:archaeology/waymark")


def waymark_road(variant=0):
    """A waymark by a stretch of the old road, as Dunn set them: the pillar on the verge with its arrow to the inn, a
    torch post along the road, the fire ring where walkers stopped with a bench and a trough. In the third the pillar
    has fallen into the grass and lies there."""
    rng = random.Random(7 + variant)
    t = Template(11, 7, 13)
    lawn(t, 11, 13, rng)
    old_road(t, 0, 4, 0, 12, rng)
    for z in range(13):
        for x in (0, 4):
            if t.name(x, 0, z) in ("cobblestone", "mossy_cobblestone") and rng.random() < 0.4:
                t.slab(x, 0, z, "cobblestone_slab" if rng.random() < 0.6 else "mossy_cobblestone_slab", "top")
    inn = [["Halfway Inn", "--->", "a day"], ["Brown Hen", "<---", "half a day"]][variant % 2]
    if variant < 2:
        waymark(t, 7, 6, inn=inn)
    else:
        # fallen: the base still there, the shaft lying in the grass, its figure face up, moss on it
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            t.set(7 + dx, 0, 6 + dz, "mossy_cobblestone" if rng.random() < 0.5 else "cobblestone")
        t.set(7, 1, 6, "mossy_stone_bricks")
        for k, block in enumerate(("cracked_stone_bricks", "chiseled_stone_bricks", "mossy_stone_bricks")):
            t.set(8 + k, 1, 8, block)
        t.slab(10, 1, 8, "mossy_stone_brick_slab")
        t.set(9, 2, 8, "moss_carpet")
        t.stairs(6, 1, 5, "mossy_stone_brick_stairs", "south")
        t.stairs(8, 1, 6, "stone_brick_stairs", "west")
        t.stairs(7, 1, 7, "stone_brick_stairs", "north")
        for x, z in ((6, 7), (8, 5), (9, 10)):
            t.set(x, 1, z, "stone_button", {"face": "floor", "facing": "west", "powered": "false"})
        t.slab(6, 1, 8, "stone_brick_slab")
        t.stairs(10, 1, 7, "mossy_stone_brick_stairs", "north")
        t.suspicious(9, 0, 9, "gravel", "residual:archaeology/waymark")
    # the torch post along the road, the fire ring where walkers stopped, a bench and a trough
    t.set(6, 0, 1, "cobblestone")
    t.set(6, 1, 1, "oak_fence")
    t.set(6, 2, 1, "oak_fence")
    t.set(6, 3, 1, "torch")
    t.set(8, 0, 11, "campfire", {"facing": "north", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        t.set(8 + dx, 0, 11 + dz, "cobblestone")
    for x, z in ((7, 10), (9, 10), (7, 12), (9, 12)):
        t.slab(x, 0, z, "cobblestone_slab", "top")
    t.stairs(10, 1, 10, "spruce_stairs", "west")
    t.stairs(10, 1, 11, "spruce_stairs", "west")
    t.slab(10, 1, 12, "spruce_slab")
    t.set(6, 1, 11, "water_cauldron", {"level": "1"})
    t.barrel(6, 1, 12, "up")
    t.set(5, 1, 3, "cobblestone_wall")
    t.set(5, 1, 9, "cobblestone_wall")
    for z in (2, 3, 4):
        t.set(9, 1, z, "spruce_fence")
    t.set(9, 2, 3, "lantern", {"hanging": "false", "waterlogged": "false"})
    for x, z in ((10, 4), (8, 3)):
        t.set(x, 1, z, "stone_button", {"face": "floor", "facing": "north", "powered": "false"})
    # a stretch of a field wall beyond the road, broken
    for z in range(7, 12):
        if z != 9:
            t.set(0, 1, z, "mossy_cobblestone_wall" if rng.random() < 0.5 else "cobblestone_wall")
    t.slab(0, 1, 9, "cobblestone_slab")
    weather(t, 7 + variant, ground=1, moss=0.3, plants=0.2)
    return t


def waymark_north():
    """The waymark on the north road between tower two and Northpoint, under its little roof, with the two notes left on
    it: nobody knows which came first. A barrel of what walkers left for each other stands by it."""
    rng = random.Random(11)
    t = Template(13, 7, 15)
    lawn(t, 13, 15, rng)
    old_road(t, 1, 5, 0, 14, rng)
    waymark(t, 8, 7)
    t.set(8, 4, 7, "air")
    for z in (5, 9):
        t.set(10, 0, z, "cobblestone")
        for y in (1, 2, 3, 4):
            t.set(10, y, z, "spruce_fence")
    for z in range(4, 11):
        for x in (7, 8, 9, 10, 11):
            t.slab(x, 5, z, "spruce_slab")
    for x in (7, 11):
        for z in range(4, 11):
            t.stairs(x, 5, z, "spruce_stairs", "east" if x == 7 else "west")
    t.set(8, 4, 7, "lantern", {"hanging": "true", "waterlogged": "false"})
    t.set(8, 5, 7, "spruce_planks")
    t.sign(7, 3, 7, "west", ["Gone south", "to tower two", "", "M."])
    t.sign(7, 1, 7, "west", ["Gone north", "to Northpoint", "", "T."])
    t.barrel(10, 1, 7, "up", "residual:chests/waymark_north")
    t.stairs(10, 1, 8, "spruce_stairs", "west")
    t.stairs(10, 1, 6, "spruce_stairs", "west")
    t.set(11, 1, 7, "water_cauldron", {"level": "1"})
    for z in (2, 3, 4, 10, 11, 12):
        t.set(12, 1, z, "spruce_fence")
    t.set(6, 1, 12, "cobblestone_wall")
    t.set(6, 1, 2, "cobblestone_wall")
    weather(t, 11, ground=1, moss=0.3, vines=0.2, plants=0.2)
    return t


def tutor_house(variant=0):
    """A tutor house, where minds were drilled and tested: halls of tuff bricks between pilasters of polished tuff, two
    tiers of tall windows, a cornice of copper, a flat roof behind its parapet with a relay on it; a portico to the
    yard. In the drill hall the apprentices still stand in their row before the tutor's bench, the drill box beside
    the crystal; the teaching room with its sunny window and the tray of crystals, each on its card; the apprentice
    hall across the yard, its apprentices stopped at their stations, the tool rack, the dry box and the jar; and the
    drill yard ringed with torches, its boards drawn as small fields of green and gold."""
    rng = random.Random(66190453 + variant)
    t = Template(27, 11, 25)
    lawn(t, 27, 25, rng)

    def hall(x0, x1, z0, z1, h, xs, zs):
        t.fill(x0, 0, z0, x1, 0, z1, "polished_tuff")
        boards(t, x0 + 2, x1 - 2, z0 + 2, z1 - 2, 0, "spruce_planks", "stripped_spruce_log", axis="z")
        for face, plane, a0, a1, posts in (("north", z0, x0, x1, xs), ("south", z1, x0, x1, xs), ("west", x0, z0, z1, zs),
                                           ("east", x1, z0, z1, zs)):
            framed(t, face, plane, a0, a1, 1, h, "polished_tuff", "tuff_bricks", posts=posts, rails=(4,),
                   pad="chiseled_tuff")
            _, (nx, nz) = run(face)
            for a_ in range(a0 + 1, a1):
                if a_ in posts:
                    continue
                x, z = cell(face, plane, a_)
                t.set(x - nx, 1, z - nz, "polished_tuff")
                t.stairs(x, 1, z, "tuff_brick_stairs", OPP[face])
                for y in (2, 3) + ((5, 6) if h >= 6 else ()):
                    t.set(x - nx, y, z - nz, "glass_pane")
        t.fill(x0 + 1, h + 1, z0 + 1, x1 - 1, h + 1, z1 - 1, "polished_tuff")
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                t.set(x, h + 1, z, "tuff_bricks")
                t.set(x, h + 2, z, "tuff_brick_wall")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                t.set(x, h + 1, z, "tuff_bricks")
                t.set(x, h + 2, z, "tuff_brick_wall")
        ring_stairs(t, h, x0 - 1, x1 + 1, "weathered_cut_copper_stairs", half="top", z0=z0 - 1, z1=z1 + 1)

    # the drill hall and the teaching room
    x0, x1, z0, z1 = 2, 20, 2, 9
    hall(x0, x1, z0, z1, 6, [2, 5, 8, 11, 14, 17, 20], [2, 6, 9])
    t.fill(x0 + 2, 1, z0 + 2, x1 - 2, 6, z1 - 2, "air")
    for z in range(z0 + 1, z1):
        for y in range(1, 7):
            t.set(14, y, z, "tuff_bricks")
    t.door(14, 1, 6, "spruce_door", "east")
    t.door(9, 1, z1 - 1, "spruce_door", "south")
    t.set(9, 1, z1, "air")
    t.set(9, 3, z1 - 1, "tuff_bricks")
    # the teaching room's sunny window, a bay of glass out to the east under a copper roof
    for z in range(4, 8):
        t.set(x1 + 1, 0, z, "polished_tuff")
        t.slab(x1 + 1, 1, z, "tuff_brick_slab", "top")
        for y in (2, 3, 4):
            t.set(x1 + 1, y, z, "glass_pane")
        t.slab(x1 + 1, 5, z, "weathered_cut_copper_slab")
        for y in (2, 3, 4):
            t.set(x1, y, z, "air")
    for z in (3, 8):
        pillar(t, x1 + 1, 1, 5, z, "polished_tuff")
    # the stair to the roof in a little tower at the north-east corner, under a copper cap
    for x in range(16, 19):
        for z in range(2, 5):
            if x in (16, 18) or z in (2, 4):
                for y in (8, 9):
                    t.set(x, y, z, "tuff_bricks")
    t.door(17, 8, 4, "spruce_door", "south")
    ring_stairs(t, 10, 15, 19, "weathered_cut_copper_stairs", z0=1, z1=5)
    t.fill(16, 10, 2, 18, 10, 4, "weathered_cut_copper")
    t.set(17, 11, 3, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    # the covered walk from the hall to the apprentice hall
    for z in (10, 12):
        for x in (3, 8):
            pillar(t, x, 1, 3, z, "polished_tuff")
    for x in range(2, 10):
        for z in range(10, 13):
            t.slab(x, 4, z, "weathered_cut_copper_slab")
    # the relay on the roof, its bulbs at the corners of the parapet
    t.set(8, 8, 5, "residual:relay")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        t.set(x, 9, z, "oxidized_copper_bulb", {"lit": "false", "powered": "false"})
    # the drill hall: the apprentices in their row, the tutor's bench with the drill box and the crystal
    for x in range(4, 14, 2):
        t.apprentice(x, 1, 4, 0)
    t.set(12, 1, 7, "residual:bench")
    t.barrel(11, 1, 7, "up", "residual:chests/tutor_house")
    t.set(13, 1, 7, "polished_tuff")
    t.set(13, 2, 7, "amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    for x in (5, 9, 12):
        t.lantern(x, 6, 5, hanging=True)
    # the teaching room: the sunny window, the tray of crystals each on its card, shelves, a cushion for the cat
    for x, card in ((16, "Farm."), (17, "Build."), (18, "Store.")):
        t.set(x, 1, 4, "spruce_planks")
        t.set(x, 2, 4, "amethyst_cluster", {"facing": "up", "waterlogged": "false"})
        t.sign(x, 1, 5, "south", [card])
    for x in range(15, 20):
        t.set(x, 1, z1 - 1, "bookshelf")
    t.set(19, 1, 6, "red_carpet")
    t.chest(15, 1, 4, "east", "residual:chests/tutor_house")
    t.lantern(17, 6, 6, hanging=True)
    # the portico to the yard: two columns, a copper roof, steps
    for x in (7, 11):
        for y in range(1, 4):
            t.set(x, y, 12, "tuff_brick_wall")
    for x in range(6, 13):
        for z in range(10, 14):
            t.slab(x, 4, z, "cut_copper_slab" if (x + z) % 2 else "weathered_cut_copper_slab")
    for x in range(7, 12):
        t.stairs(x, 0, 13, "tuff_brick_stairs", "north")
    # the apprentice hall across the yard: its apprentices stopped at their stations, the tool rack, the dry box
    a0, a1, b0, b1 = 2, 9, 13, 21
    hall(a0, a1, b0, b1, 4, [2, 5, 9], [13, 17, 21])
    t.fill(a0 + 2, 1, b0 + 2, a1 - 2, 4, b1 - 2, "air")
    t.door(a1 - 1, 1, 17, "spruce_door", "east")
    t.set(a1, 1, 17, "air")
    for z in (16, 18):
        pillar(t, a1 + 2, 1, 3, z, "tuff_brick_wall")
    for x in (a1 + 1, a1 + 2):
        for z in (15, 16, 17, 18, 19):
            t.slab(x, 4, z, "weathered_cut_copper_slab")
    # buttresses down the drill hall's back, each stepped
    for x in (5, 11, 17):
        buttress(t, x, z0, "north", 1, 4, "tuff_bricks", "tuff_brick_stairs")
        t.slab(x, 6, z0 - 1, "tuff_brick_slab")
    for x in (5, 17):
        buttress(t, x, z1, "south", 1, 4, "tuff_bricks", "tuff_brick_stairs")
    for z in (15, 19):
        buttress(t, a0, z, "west", 1, 2, "tuff_bricks", "tuff_brick_stairs")
    t.apprentice(4, 1, 15, 0)
    t.apprentice(4, 1, 19, 180)
    t.apprentice(6, 1, 19, 180)
    t.set(4, 1, b0 + 2, "anvil", {"facing": "east"})
    t.set(5, 1, b0 + 2, "grindstone", {"face": "floor", "facing": "south"})
    for x in (6, 7):
        t.set(x, 2, b0 + 2, "spruce_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false",
                                                "waterlogged": "false"})
    t.chest(a1 - 2, 1, b1 - 2, "west", "residual:chests/tutor_dry_box")
    t.set(a1 - 2, 1, b1 - 3, "decorated_pot", {"facing": "west", "cracked": "false", "waterlogged": "false"})
    t.sign(5, 2, b1 - 2, "north", ["Oil is in the", "jar. Spare", "crystals are in", "the dry box."])
    t.lantern(5, 4, 17, hanging=True)
    # the drill yard: fenced, torches round it, three boards drawn as small fields, a bench to watch from
    for x in range(12, 26):
        t.set(x, 1, 23, "spruce_fence")
    for z in range(12, 24):
        t.set(12, 1, z, "spruce_fence")
        t.set(25, 1, z, "spruce_fence")
    for x, z in ((12, 12), (12, 23), (25, 12), (25, 23), (18, 23)):
        t.set(x, 2, z, "torch")
    for bx, bz in ((14, 14), (20, 14), (14, 18)):
        t.fill(bx - 1, 0, bz - 1, bx + 4, 0, bz + 3, "spruce_planks")
        for x in range(bx, bx + 4):
            t.set(x, 1, bz, "lime_carpet")
            t.set(x, 1, bz + 1, "yellow_carpet")
            t.set(x, 1, bz + 2, "green_carpet")
    for x in (21, 22):
        t.stairs(x, 1, 19, "spruce_stairs", "north")
    # what the years did: vines down the tuff, moss on the roofs, the yard gone to grass, glass broken, lamps out
    lights_out(t, 66190453 + variant, 0.7)
    broken_glass(t, 66190453 + variant, 0.35)
    weather(t, 66190453 + variant, ground=1, height=6, vines=0.25, webs=0.06, plants=0.3)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if rng.random() < 0.15:
                t.set(x, 8, z, "moss_carpet")
    for x in range(a0 + 1, a1):
        for z in range(b0 + 1, b1):
            if rng.random() < 0.2:
                t.set(x, 6, z, "moss_carpet")
    if variant:
        t = mirrored(t)
    return t


def fallen_relay_mast(variant=0):
    """A relay mast that fell, long ago: the bowl it broke in the ground, grassed over at the rim, a puddle at the
    bottom; the lattice lying across it in three pieces, its copper gone green, rings and arms still on it, the relay
    half buried at the far end; and its drive housing split open, the drives of writing it carried still inside."""
    import math
    rng = random.Random(40211973 + variant)
    patch = noise(40211973 + variant, 4.0)
    t = Template(21, 11, 21)
    cx, cz, ground, r0 = 10, 10, 6, 9.5

    def copper(x, y, z, kind, props=None):
        p = patch(x, y, z) + 0.25
        stage = "oxidized_" if p > 0.7 else "weathered_" if p > 0.45 else "exposed_"
        t.set(x, y, z, stage + kind, props)

    def floor(x, z):
        r = math.hypot(x - cx, z - cz)
        return ground - round(4 * (1 - (r / r0) ** 2)) if r <= r0 else None

    for x in range(21):
        for z in range(21):
            f = floor(x, z)
            if f is None:
                if math.hypot(x - cx, z - cz) <= r0 + 1.5:
                    t.set(x, ground, z, "grass_block", {"snowy": "false"})
                continue
            for y in range(f + 1, 11):
                t.set(x, y, z, "air")
            p = rng.random()
            rim = math.hypot(x - cx, z - cz) > r0 - 2.5
            if rim and p < 0.6:
                t.set(x, f, z, "grass_block", {"snowy": "false"})
            else:
                t.set(x, f, z, "coarse_dirt" if p < 0.35 else "gravel" if p < 0.5 else "cobblestone" if p < 0.62
                      else "tuff" if p < 0.75 else "moss_block" if p < 0.88 else "dirt")
    # a puddle at the bottom, a lily pad on it
    for x, z in ((9, 11), (10, 11), (10, 12)):
        t.set(x, floor(x, z), z, "water", {"level": "0"})
    t.set(10, floor(10, 11) + 1, 11, "lily_pad")
    # the mast lying across the bowl in three pieces: four legs and rings every four, the top tilted where it broke
    pieces = [(1, 6, 0), (8, 12, 0), (14, 19, 1)]
    for a0, a1, lift in pieces:
        for x in range(a0, a1):
            f = floor(x, 10)
            if f is None:
                continue
            for z in (9, 11):
                copper(x, f + 1 + lift, z, "cut_copper")
                copper(x, f + 3 + lift, z, "cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
            if x % 4 == 1:
                for z in (9, 11):
                    copper(x, f + 2 + lift, z, "cut_copper_slab", {"type": "top", "waterlogged": "false"})
                copper(x, f + 1 + lift, 10, "copper_grate", {"waterlogged": "false"})
                copper(x, f + 3 + lift, 10, "cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
            elif x % 2:
                for z, f_ in ((9, "south"), (11, "north")):
                    copper(x, f + 2 + lift, z, "copper_trapdoor", {"facing": f_, "half": "bottom", "open": "true",
                                                                   "powered": "false", "waterlogged": "false"})
            else:
                t.set(x, f + 2 + lift, 10, "chain", {"axis": "x", "waterlogged": "false"})
    # the arms and the relay at the top end, half buried; the bulbs off the arms
    fz = floor(19, 10) if floor(19, 10) is not None else ground
    for k in (1, 2, 3):
        copper(17, floor(17, 10) + 3 + k, 10, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
        copper(17, floor(17, 10) + 1, 10 - k - 1, "lightning_rod", {"facing": "north", "powered": "false",
                                                                     "waterlogged": "false"})
    copper(17, floor(17, 10) + 7, 10, "copper_bulb", {"lit": "false", "powered": "false"})
    copper(17, floor(17, 6), 5, "copper_bulb", {"lit": "false", "powered": "false"})
    t.set(19, fz, 10, "residual:relay")
    # guy chains lying in the grass, torn pieces of the stand
    for z in range(2, 8):
        t.set(5, floor(5, z) + 1 if floor(5, z) is not None else ground + 1, z, "chain", {"axis": "z", "waterlogged": "false"})
    for x, z, f in ((4, 6, "east"), (12, 14, "north"), (14, 5, "west"), (7, 13, "south"), (13, 15, "east")):
        if floor(x, z) is not None:
            copper(x, floor(x, z) + 1, z, "cut_copper_stairs", {"facing": f, "half": "bottom", "shape": "straight",
                                                                "waterlogged": "false"})
    for x, z in ((3, 12), (6, 15), (15, 13), (16, 7), (11, 4), (7, 5)):
        if floor(x, z) is not None:
            copper(x, floor(x, z) + 1, z, "cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    for x, z in ((12, 6), (8, 15), (15, 9)):
        if floor(x, z) is not None:
            copper(x, floor(x, z) + 1, z, "copper_trapdoor", {"facing": "north", "half": "bottom", "open": "false",
                                                              "powered": "false", "waterlogged": "false"})
    # the drive housing, split open, its chest of drives inside, a barrel thrown out of it
    f = floor(10, 7)
    for x in (9, 11):
        for y in (f + 1, f + 2):
            copper(x, y, 7, "cut_copper")
            copper(x, y, 6, "cut_copper")
    copper(10, f + 1, 6, "cut_copper")
    copper(10, f + 3, 6, "cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    copper(9, f + 3, 7, "copper_grate", {"waterlogged": "false"})
    t.chest(10, f + 1, 7, "south", "residual:chests/fallen_relay_mast")
    copper(10, f + 2, 8, "copper_trapdoor", {"facing": "south", "half": "bottom", "open": "true", "powered": "false",
                                             "waterlogged": "false"})
    t.barrel(8, floor(8, 5) + 1, 5, "east")
    t.barrel(12, floor(12, 8) + 1, 8, "up", "residual:chests/fallen_relay_mast" if variant else None)
    # stone thrown up where it struck, broken off the bowl's floor
    for _ in range(70):
        x, z = rng.randint(2, 18), rng.randint(2, 18)
        f = floor(x, z)
        if f is None or not t.empty(x, f + 1, z) or abs(z - 10) < 2:
            continue
        r = rng.random()
        if r < 0.4:
            t.stairs(x, f + 1, z, rng.choice(["cobblestone_stairs", "mossy_cobblestone_stairs", "tuff_stairs"]),
                     rng.choice(list(SIDES)))
        elif r < 0.75:
            t.slab(x, f + 1, z, rng.choice(["cobblestone_slab", "tuff_slab", "mossy_cobblestone_slab"]))
        else:
            copper(x, f + 1, z, "cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    # the stand's railing torn off and thrown down, a keeper's lamp in the grass
    for k in range(4):
        x, z = 14 + k, 15
        if floor(x, z) is not None:
            t.set(x, floor(x, z) + 1, z, "iron_bars")
    if floor(6, 13) is not None:
        t.set(6, floor(6, 13) + 1, 13, "lantern", {"hanging": "false", "waterlogged": "false"})
    # the cordon the keepers put round it, fence posts on the rim with a rope of chain, and their sign
    for k in range(-4, 5):
        t.set(cx + k, ground, 0, "grass_block", {"snowy": "false"})
        if k % 2 == 0:
            t.set(cx + k, ground + 1, 0, "spruce_fence")
        else:
            t.set(cx + k, ground + 1, 0, "chain", {"axis": "x", "waterlogged": "false"})
    t.post_sign(cx, ground + 2, 0, 8, ["Mast down.", "Keep off till", "the keepers", "come."], wood="spruce")
    # what the years did: vines over the lattice, moss and grass in the bowl, webs in the rings
    weather(t, 40211973 + variant, ground=2, vines=0.35, plants=0.3, webs=0.03)
    for x in range(1, 20, 3):
        f = floor(x, 10)
        if f is not None and t.empty(x, f + 4, 10) and rng.random() < 0.6:
            t.set(x, f + 4, 10, "moss_carpet")
    return t


SEALING_BOOK = [
    "The sealing book. Each stack is closed when it is full, or when the council says.",
    "East stacks: shelves 1 to 40, boxed in waxed cloth in number order. Door closed with clay. O.",
    "North stacks: shelves 41 to 77. Door closed with clay. The list of every number is just inside the door. O.",
    "The great doors: not yet. The lamp is on the long table, by the reading room window, for whoever comes.",
]


LIST_BOOK = [
    "North stacks. The list of every number, shelf by shelf, so that whoever comes can find one text without opening "
    "them all.",
    "Shelf 41: 40,113 to 40,190. Shelf 42: 40,191 to 40,260. Shelf 43: Ambrook, all of it.",
    "Shelf 77: the last ones, copied in the last days. Not in number order. We were tired. Forgive us. O.",
]


def sealed_stacks(variant=0):
    """Stacks of the Archive sealed in its last days, deep underground with no way in but a wall of clay: a vaulted
    hall of deepslate between pillars, its shelves in bays behind folded doors with wax over them, boxes of records in
    waxed cloth in number order down the middle, the sealing book on its lectern, and a lamp left on the long table
    for whoever comes. Roots have found their way down through the vault, and the damp with them. The second, the
    north stacks, is longer, with the list of every number just inside the door."""
    rng = random.Random(77015239 + variant)
    n = 15 if variant == 0 else 19
    t = Template(15, 9, n + 5)
    x0, x1, z0, z1, h = 2, 12, 1, n + 1, 5
    for x in range(x0 - 1, x1 + 2):
        for y in range(0, h + 3):
            for z in range(z0, z1 + 1):
                r = rng.random()
                t.set(x, y, z, "deepslate_tiles" if r < 0.5 else "deepslate_bricks" if r < 0.8 else "cobbled_deepslate")
    # the passage that led in, beyond the clay
    for x in range(5, 10):
        for y in range(0, 5):
            for z in (z1 + 1, z1 + 2):
                t.set(x, y, z, "cobbled_deepslate" if rng.random() < 0.5 else "deepslate_bricks")
    t.fill(6, 1, z1 + 1, 8, 3, z1 + 2, "air")
    t.fill(x0 + 1, 1, z0 + 1, x1 - 1, h, z1 - 1, "air")
    t.fill(x0 + 2, 0, z0 + 2, x1 - 2, 0, z1 - 2, "polished_deepslate")
    # the vault: the ceiling stepped up toward the middle in brick stairs
    for z in range(z0 + 1, z1):
        t.stairs(x0 + 1, h, z, "deepslate_brick_stairs", "east", half="top")
        t.stairs(x1 - 1, h, z, "deepslate_brick_stairs", "west", half="top")
        t.fill(x0 + 3, h + 1, z, x1 - 3, h + 1, z, "air")
        t.stairs(x0 + 2, h + 1, z, "deepslate_brick_stairs", "east", half="top")
        t.stairs(x1 - 2, h + 1, z, "deepslate_brick_stairs", "west", half="top")
    # pillars standing out between the bays, ribs across the vault over them
    shelf = {f"slot_{i}_occupied": "true" for i in range(6)}
    for z in range(z0 + 1, z1, 3):
        for y in range(1, h):
            t.set(x0 + 1, y, z, "polished_deepslate")
            t.set(x1 - 1, y, z, "polished_deepslate")
        for x in range(x0 + 3, x1 - 2):
            t.slab(x, h + 1, z, "deepslate_brick_slab", "top")
    # the bays: full shelves behind folded doors, wax over them, the seal at the top of each pillar
    for z in range(z0 + 2, z1 - 1):
        if (z - z0 - 1) % 3 == 0:
            continue
        for x, face in ((x0, "east"), (x1, "west")):
            for y in (1, 2, 3):
                t.set(x, y, z, "chiseled_bookshelf", {"facing": face, **shelf})
            t.set(x, 4, z, "honeycomb_block")
            t.trapdoor(x + (1 if face == "east" else -1), 2, z, "spruce_trapdoor", face, open_=True)
    for z in range(z0 + 1, z1, 3):
        t.set(x0 + 1, h - 1, z, "chiseled_tuff")
        t.set(x1 - 1, h - 1, z, "chiseled_tuff")
    # boxes in waxed cloth down the middle, in number order
    for z in range(z0 + 3, z1 - 3, 2):
        for x in (6, 8):
            t.barrel(x, 1, z, "up", "residual:chests/sealed_stacks" if (x + z) % 4 == 0 else None)
            t.set(x, 2, z, "white_carpet")
    t.sign(6, 1, z0 + 2, "north", ["East stacks" if variant == 0 else "North stacks",
                                   "1 to 40" if variant == 0 else "41 to 77"])
    # the long table, the lamp left burning on it, and the sealing book
    lamp = (7, 2, z0 + 1)
    for x in range(5, 10):
        t.slab(x, 1, z0 + 1, "spruce_slab", "top")
    t.lantern(*lamp)
    t.set(10, 1, z0 + 2, "lectern", {"facing": "north", "has_book": "true", "powered": "false"},
          {"id": "minecraft:lectern", "Page": 0, "Book": {"id": "minecraft:written_book", "count": 1, "components": {
              "minecraft:written_book_content": {"title": {"raw": "The sealing book"}, "author": "O.", "resolved": 1,
                                                 "pages": [{"raw": p} for p in SEALING_BOOK]}}}})
    # the way in, closed with clay; in the north stacks the list of every number just inside it
    t.fill(6, 1, z1, 8, 3, z1, "clay")
    t.fill(6, 1, z1 - 1, 8, 3, z1 - 1, "clay")
    if variant:
        t.set(9, 1, z1 - 2, "lectern", {"facing": "south", "has_book": "true", "powered": "false"},
              {"id": "minecraft:lectern", "Page": 0, "Book": {"id": "minecraft:written_book", "count": 1, "components": {
                  "minecraft:written_book_content": {"title": {"raw": "The list"}, "author": "O.", "resolved": 1,
                                                     "pages": [{"raw": p} for p in LIST_BOOK]}}}})
    for z in (z0 + 5, z1 - 5):
        t.lantern(7, h + 1, z, hanging=True)
    # what the years did: roots through the vault, the damp under them, lichen, cracks and webs
    for x, z in ((5, z0 + 4), (8, z0 + 7), (6, z1 - 4), (9, z1 - 6)):
        t.set(x, h + 2, z, "rooted_dirt")
        t.set(x, h + 1, z, "hanging_roots", {"waterlogged": "false"})
    for x, z in ((4, z1 - 2), (10, z0 + 6), (5, z0 + 9)):
        if t.empty(x, 1, z):
            t.set(x, 1, z, "moss_carpet")
    t.set(4, 0, z1 - 3, "water", {"level": "0"})
    t.set(3, 0, z1 - 3, "moss_block")
    for (x, y, z), face in (((x0 + 2, 3, z0 + 5), "west"), ((x1 - 2, 4, z0 + 8), "east"), ((7, 4, z1 - 1), "north")):
        if t.empty(x, y, z):
            t.set(x, y, z, "glow_lichen", {s_: str(s_ == face).lower() for s_ in ("north", "south", "east", "west", "up", "down")}
                  | {"waterlogged": "false"})
    lights_out(t, 77015239 + variant, 0.5, keep=[lamp])
    weather(t, 77015239 + variant, ground=1, crack=0.3, height=7, webs=0.06, inside=(x0, x1, z0, z1), keep=[lamp])
    return t


WAYMARK_BOOK = [
    "The waymark book, kept for the keepers of the Long Road behind the counter of the Halfway Inn.",
    "Dunn's stretch: from the bridge to this door. Figures 0 to 9, each cut once. Stones on top of every one.",
    "Spring: the 4 by the old mill track leans. Set it up again, and a new torch. Hobb paid for the torch in soup.",
    "Leaffall: all well. The 7 has a fox carved under its figure now. Nobody will say who did it.",
]


ROOMS_BOOK = [
    "The Brown Hen. Rooms book: who, which room, how many nights, paid in what.",
    "Esk, the bridge keeper. The small room. One night. Paid in eggs, which we have plenty of.",
    "Two carters from Saltreach. The loft. Two nights. Paid in salt fish.",
    "A tutor going north with two apprentices. The end room. The apprentices stood in the yard all night by the pump. "
    "Paid.",
]
INN_VARIANTS = [
    {"sign": ["The Halfway", "Inn", "Beds, soup,", "hay"], "frame": "stripped_dark_oak_log", "infill": "calcite",
     "wood": "dark_oak", "low": None, "book": ("Waymark book", "Dunn", WAYMARK_BOOK), "seed": 30091277,
     "boards": (["I come to every", "supper at the", "inn and I never", "pay. What am I?"],
                ["What gets", "shorter every", "time you walk", "on it?"])},
    {"sign": ["The Brown Hen", "", "Beds and", "eggs"], "frame": "stripped_spruce_log", "infill": "packed_mud",
     "wood": "spruce", "low": ("stone_bricks", "cobblestone"), "book": ("Rooms book", "the innkeeper", ROOMS_BOOK),
     "seed": 30091278,
     "boards": (["Boots off at", "the door. Mud", "is for the", "yard."], ["Eggs: one each.", "Two if you", "chop wood."])},
]


def inn(variant):
    """An inn of the Long Road, a day from anywhere, as its walkers wrote of them: a stone footing, a timber frame with
    plaster set back between the posts (the Brown Hen's ground floor all stone), the upper floor jutting over the road
    on corbels, shuttered windows, a steep roof with two dormers to the road and the chimney stack up its west gable;
    the common room with its long tables and the hearth that has not gone out, the boards by the fire, the counter with
    the book behind it and a pie, the kitchen; rooms upstairs; the woodshed at the back; the stable across the yard,
    its roof part fallen; the sign on its post by the road, and a waymark. Variant 0 is the Halfway Inn, 1 the Brown
    Hen."""
    v = INN_VARIANTS[variant]
    rng = random.Random(v["seed"])
    frame, infill, wood = v["frame"], v["infill"], v["wood"]
    low_post, low_infill = v["low"] or (frame, infill)
    planks, stairs, slab, trap = wood + "_planks", wood + "_stairs", wood + "_slab", wood + "_trapdoor"
    t = Template(30, 17, 27)
    x0, x1, z0, z1 = 3, 19, 6, 16
    xs, zs = [3, 7, 11, 15, 19], [6, 11, 16]
    # the ground: grass, the yard trodden bare, the Long Road along the south
    lawn(t, 30, 27, rng, grass=0.8, bare=("coarse_dirt", "coarse_dirt", "dirt"))
    old_road(t, 0, 29, 21, 25, rng, along="x")
    for z in range(17, 21):
        t.set(13, 0, z, "dirt_path" if rng.random() < 0.8 else "coarse_dirt")
    for x in range(20, 23):
        for z in range(14, 21):
            if rng.random() < 0.7:
                t.set(x, 0, z, "dirt_path" if rng.random() < 0.6 else "coarse_dirt")
    # the ground floor on a cobblestone footing, with a sloped plinth in front of the walls
    t.fill(x0, 0, z0, x1, 0, z1, "cobblestone")
    boards(t, x0 + 2, x1 - 2, z0 + 2, z1 - 2, 0, planks, frame)
    for face, plane, a0, a1, posts in (("north", z0, x0, x1, xs), ("south", z1, x0, x1, xs), ("west", x0, z0, z1, zs),
                                       ("east", x1, z0, z1, zs)):
        framed(t, face, plane, a0, a1, 1, 4, low_post, low_infill, posts=posts, rails=(3,) if not v["low"] else (),
               pad="cobblestone")
        _, (nx, nz) = run(face)
        for a in range(a0 + 1, a1):
            if a in posts:
                continue
            x, z = cell(face, plane, a)
            t.set(x - nx, 1, z - nz, "cobblestone")
            t.stairs(x, 1, z, "cobblestone_stairs", OPP[face])
    # the upper floor, jutting a block over the road on corbels, a ledge at the floor elsewhere
    boards(t, x0 + 1, x1 - 1, z0 + 1, z1, 5, planks, frame)
    framed(t, "north", z0, x0, x1, 5, 8, frame, infill, posts=xs, rails=(5,))
    framed(t, "west", x0, z0, z1 + 1, 5, 8, frame, infill, posts=[6, 11, 17], rails=(5,))
    framed(t, "east", x1, z0, z1 + 1, 5, 8, frame, infill, posts=[6, 11, 17], rails=(5,))
    framed(t, "south", z1 + 1, x0, x1, 6, 8, frame, infill, posts=xs)
    for x in range(x0 + 1, x1):
        t.stairs(x, 5, z1 + 1, stairs, "north", half="top")
        if x not in xs:
            t.slab(x, 5, z0, slab, "top")
    for x in xs:
        t.stairs(x, 4, z1 + 1, stairs, "north", half="top")
    for z in range(z0 + 1, z1 + 1):
        if z not in (11, 17):
            t.slab(x0, 5, z, slab, "top")
            t.slab(x1, 5, z, slab, "top")
    # the roof: steep, overhanging, rafter ends under the eaves; the gables framed round a pair of king posts; two
    # dormers to the road
    for u, y, a in roof(t, 9, x0, x1, z0, z1 + 1, stairs, "x", over=1, verge=1, brackets=stairs):
        inward = 1 if u == x0 else -1
        if a in (11, 12):
            t.log(u, y, a, frame)
        elif a in (9, 14) and y in (10, 11):
            t.set(u, y, a, "air")
            t.set(u + inward, y, a, "glass_pane")
        else:
            t.set(u, y, a, "air")
            t.set(u + inward, y, a, infill)
    for c in (9, 13):
        dormer(t, c, z1 + 1, 9, "south", stairs, frame, infill)
    # shuttered windows, the door with a light over it
    for x in (5, 9, 17):
        window(t, "south", x, 2, z1 - 1, h=2, shutters=trap)
    for x in (5, 9, 13, 17):
        window(t, "south", x, 6, z1, h=2, shutters=trap)
    for x in (5, 17):
        window(t, "north", x, 2, z0 + 1, h=2, shutters=trap)
        window(t, "north", x, 6, z0 + 1, h=2, shutters=trap)
    for z in (8, 14):
        window(t, "east", x1 - 1, 2, z, h=2, shutters=trap)
        window(t, "east", x1 - 1, 6, z, h=2, shutters=trap)
        window(t, "west", x0 + 1, 6, z, h=2, shutters=trap)
    window(t, "west", x0 + 1, 2, 8, h=2, shutters=trap)
    t.door(13, 1, z1 - 1, wood + "_door", "south")
    t.set(13, 3, z1 - 1, "glass_pane")
    t.set(13, 1, z1, "air")
    t.door(9, 1, z0 + 1, wood + "_door", "north")
    t.set(9, 1, z0, "air")
    # the hearth that has not gone out, its stack up the west gable, the boards by the fire
    t.fill(x0 - 1, 1, 10, x0 - 1, 8, 12, "bricks")
    for y in (1, 5):
        t.fill(x0 - 1, y, 10, x0 - 1, y, 12, "cobblestone")
    for z in (10, 12):
        t.stairs(x0 - 1, 9, z, "brick_stairs", "east")
    chimney(t, x0 - 1, 11, 9, 16, "bricks")
    t.fill(x0 + 1, 1, 10, x0 + 1, 4, 12, "bricks")
    t.fill(x0 + 2, 1, 10, x0 + 2, 4, 12, "bricks")
    t.set(x0 + 2, 1, 11, "campfire", {"facing": "east", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    t.set(x0 + 2, 2, 11, "air")
    t.stairs(x0 + 3, 4, 11, "brick_stairs", "west", half="top")
    for z, lines in zip((10, 12), v["boards"]):
        t.sign(x0 + 3, 3, z, "east", lines)
    # the common room: two long tables with their benches, lamps hung from the floor above
    for tz in (10, 13):
        for x in range(8, 12):
            t.slab(x, 1, tz, slab, "top")
            t.stairs(x, 1, tz - 1, stairs, "south")
            t.stairs(x, 1, tz + 1, stairs, "north")
        t.lantern(9, 4, tz, hanging=True)
    # the counter, the book behind it next to the pie; cider barrels; the kitchen
    for z in range(8, 12):
        t.set(14, 1, z, planks)
    t.set(14, 2, 9, "cake", {"bites": "2"})
    title, author, pages = v["book"]
    t.set(15, 1, 9, "lectern", {"facing": "west", "has_book": "true", "powered": "false"},
          {"id": "minecraft:lectern", "Page": 0, "Book": {"id": "minecraft:written_book", "count": 1, "components": {
              "minecraft:written_book_content": {"title": {"raw": title}, "author": author, "resolved": 1,
                                                 "pages": [{"raw": p} for p in pages]}}}})
    for z in (8, 9):
        t.barrel(17, 1, z, "west", "residual:chests/inn_cellar")
        t.barrel(17, 2, z, "west")
    t.set(17, 1, 12, "smoker", {"facing": "west", "lit": "false"})
    t.set(17, 1, 13, "water_cauldron", {"level": "3"})
    t.barrel(17, 1, 14, "up", "residual:chests/inn_cellar")
    t.set(16, 1, 14, "crafting_table")
    # the stairs up along the north wall, the rooms above: beds, chests, a lamp on a barrel in each
    for k in range(4):
        t.stairs(5 + k, 1 + k, 8, stairs, "east")
        t.set(5 + k, 5, 8, "air")
    for x in (9, 14):
        t.fill(x, 6, 10, x, 8, 15, planks)
    t.bed(6, 6, 13, "red", "south")
    t.bed(8, 6, 13, "red", "south")
    t.bed(12, 6, 13, "blue" if variant == 0 else "brown", "south")
    t.bed(16, 6, 13, "red", "south")
    t.chest(5, 6, 11, "east", "residual:chests/inn_rooms")
    t.chest(17, 6, 11, "west", "residual:chests/inn_rooms")
    for x, z in ((7, 11), (11, 11), (15, 11)):
        t.barrel(x, 6, z, "up")
        t.lantern(x, 7, z)
    # the woodshed at the back: a lean-to on two posts, the woodpile, the chopping block
    for x in (7, 12):
        pillar(t, x, 1, 4, 3, frame)
    for z in range(2, 6):
        for x in range(6, 14):
            t.stairs(x, 4 + z - 2, z, stairs, "south")
    log = "oak_log" if variant == 0 else "spruce_log"
    for x in range(10, 13):
        for z in (4, 5):
            t.log(x, 1, z, log, "x")
            if x < 12:
                t.log(x, 2, z, log, "x")
    t.log(8, 1, 4, "stripped_oak_log")
    t.barrel(7, 1, 5, "up")
    # the stable across the yard: posts, boards set back, stalls of fences, hay and a trough; its roof part fallen
    sx0, sx1 = 22, 27
    for face, plane, a0, a1, posts in (("east", sx1, z0, z1, [6, 9, 13, 16]), ("north", z0, sx0, sx1, [sx0, sx1]),
                                       ("south", z1, sx0, sx1, [sx0, sx1])):
        framed(t, face, plane, a0, a1, 1, 3, frame, planks, posts=posts)
    for z in (9, 13, 16):
        pillar(t, sx0, 1, 3, z, frame)
    t.fill(sx0 + 1, 0, z0 + 1, sx1 - 1, 0, z1 - 1, "coarse_dirt")
    roof(t, 4, sx0, sx1, z0, z1, stairs, "z", over=1, verge=1)
    for z in range(z0 + 1, z1):
        if z in (9, 13):
            for x in range(sx0 + 1, sx1 - 1):
                t.set(x, 1, z, wood + "_fence")
        elif z in (8, 11, 15):
            t.set(sx0, 1, z, wood + "_fence_gate", {"facing": "west", "in_wall": "false", "open": "false", "powered": "false"})
        else:
            t.set(sx0, 1, z, wood + "_fence")
    for z in (7, 10, 14):
        t.set(sx1 - 2, 1, z, "hay_block", {"axis": "y"})
        t.set(sx1 - 2, 1, z + 1, "water_cauldron", {"level": "1"})
    caved(t, v["seed"], sx0 + 1, sx1 - 1, 11, 15, 8, 0)
    # the yard: the trough, lamp posts by the door, the inn's sign by the road, a waymark
    t.set(20, 1, 18, "water_cauldron", {"level": "2"})
    t.set(21, 1, 18, "hay_block", {"axis": "x"})
    lamps = []
    for x in (11, 15):
        t.set(x, 1, 18, "cobblestone_wall")
        t.set(x, 2, 18, "cobblestone_wall")
        t.lantern(x, 3, 18)
        lamps.append((x, 3, 18))
    t.set(6, 1, 19, "oak_fence" if variant == 0 else "spruce_fence")
    t.post_sign(6, 2, 19, 0, v["sign"], wood="oak" if variant == 0 else "spruce")
    waymark(t, 25, 19, face="south")
    # what the years did: most lamps out, glass gone, moss on the stone low down, vines on the north, grass in the yard
    lights_out(t, v["seed"], 0.8, keep=lamps)
    broken_glass(t, v["seed"], 0.3)
    weather(t, v["seed"], ground=1, moss=0.35, height=5, vines=0.18, webs=0.06, plants=0.3,
            inside=(x0 + 1, x1 - 1, z0 + 1, z1))
    return t


MILL_NOTICES = [
    (["The wheel turns", "dawn to dusk.", "Do not open", "the sluice."], ["Swim at the far", "end, by the", "reeds. A new", "bench seats two."]),
    (["Flour today:", "wheat only.", "Bring your own", "sacks."], ["No fishing from", "the wheel side.", "", "Jory"]),
]


def watermill(variant=0):
    """A mill of the orchard country, Jory's of birch and glass, on its pond: a stone footing down into the water, birch
    posts with glass between most of them, a spruce roof; the wheel on its axle out over the pond, paddles round its
    rim; the race of stone beside it with the sluice nobody may open and the footbridge with the loose plank; inside,
    the millstone under its hopper, the sacks and the miller's chest. On the bank the notices on the gate, the bench
    that seats two, old apple trees, one with its grey branch, and hives among the flowers. Its north end stands on
    the bank and its south end over the pond. In the second, the wheel has lost half its rim."""
    import math
    rng = random.Random(52990431 + variant)
    t = Template(21, 14, 22)
    bank = 4  # the floor of the mill and the bank's top; the pond's surface is a block below it
    for x in range(21):
        for z in range(11):
            t.set(x, bank - 2, z, "dirt")
            if rng.random() < 0.85:
                t.set(x, bank - 1, z, "grass_block", {"snowy": "false"})
            else:
                t.set(x, bank - 1, z, "coarse_dirt")
    # the mill: a stone footing down into the pond, birch posts with boards and glass set back, a spruce roof
    x0, x1, z0, z1 = 5, 13, 3, 10
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(0 if z >= 8 else bank - 2, bank):
                t.set(x, y, z, rng.choice(["stone_bricks"] * 5 + ["mossy_stone_bricks", "cracked_stone_bricks", "stone",
                                                                  "mossy_stone_bricks"]))
    boards(t, x0 + 2, x1 - 2, z0 + 2, z1 - 2, bank - 1, "spruce_planks", "stripped_spruce_log", axis="x")
    for face, plane, a0, a1, posts in (("north", z0, x0, x1, [5, 7, 9, 11, 13]), ("south", z1, x0, x1, [5, 7, 9, 11, 13]),
                                       ("west", x0, z0, z1, [3, 5, 8, 10]), ("east", x1, z0, z1, [3, 5, 8, 10])):
        framed(t, face, plane, a0, a1, bank, bank + 3, "birch_log", "birch_planks", posts=posts, rails=(bank,))
        _, (nx, nz) = run(face)
        for a_ in range(a0 + 1, a1):
            if a_ in posts:
                continue
            x, z = cell(face, plane, a_)
            for y in (bank + 1, bank + 2):
                t.set(x - nx, y, z - nz, "glass_pane")
    for u, y, a_ in roof(t, bank + 4, x0, x1, z0, z1, "spruce_stairs", "z", brackets="spruce_stairs"):
        inward = 1 if u == z0 else -1
        t.set(a_, y, u, "air")
        t.set(a_, y, u + inward, "glass_pane" if (a_ == 9 and y == bank + 6) else "birch_planks")
    t.door(9, bank, z0 + 1, "birch_door", "north")
    t.set(9, bank, z0, "air")
    # inside: the millstone under its hopper on the shaft, the sacks, the miller's chest, a lamp
    t.set(9, bank, 7, "grindstone", {"face": "floor", "facing": "north"})
    t.set(9, bank + 1, 7, "hopper", {"enabled": "true", "facing": "down"})
    t.barrel(x0 + 2, bank, z0 + 2, "up", "residual:chests/watermill")
    t.barrel(x0 + 3, bank, z0 + 2, "up")
    t.barrel(x0 + 2, bank + 1, z0 + 2, "up")
    t.set(x1 - 2, bank, z0 + 2, "white_wool")
    t.set(x1 - 2, bank, z0 + 3, "white_wool")
    t.set(x1 - 2, bank + 1, z0 + 2, "white_wool")
    t.chest(x1 - 2, bank, z1 - 2, "west", "residual:chests/watermill_jory")
    t.lantern(9, bank + 3, 6, hanging=True)
    # the wheel turning over the pond on its axle out of the south wall, paddles round its rim
    cx, cy, zw = 9, bank, z1 + 2
    for z in range(z1, zw):
        t.log(cx, cy, z, "stripped_spruce_log", "z")
    for k in range(40):
        a_ = 2 * math.pi * k / 40
        if variant and math.sin(a_) > 0.1:
            continue
        x, y = round(cx + 4 * math.cos(a_)), round(cy + 4 * math.sin(a_))
        if y >= 0:
            t.set(x, y, zw, "spruce_planks")
    for k in range(8):
        a_ = 2 * math.pi * k / 8
        if variant and math.sin(a_) > 0.1:
            continue
        for r in (1, 2, 3):
            x, y = round(cx + r * math.cos(a_)), round(cy + r * math.sin(a_))
            if y >= 0:
                t.set(x, y, zw, "spruce_fence")
        x, y = round(cx + 5 * math.cos(a_)), round(cy + 5 * math.sin(a_))
        if y >= 0:
            t.trapdoor(x, y, zw, "spruce_trapdoor", "south", open_=True)
    t.log(cx, cy, zw, "spruce_log", "z")
    t.log(cx, cy, zw + 1, "spruce_log", "z")
    if variant:
        for x, z in ((3, 9), (4, 8), (16, 2)):
            t.set(x, bank, z, "spruce_planks")
        t.trapdoor(2, bank, 9, "spruce_trapdoor", "north")
    # the race of stone beside the mill, its sluice nobody may open, the footbridge with the loose plank
    for z in range(2, 13):
        for x in (15, 17):
            for y in range(1, bank):
                t.set(x, y, z, rng.choice(["stone_bricks"] * 4 + ["mossy_stone_bricks", "cracked_stone_bricks", "stone"]))
        t.set(16, 1, z, "stone_bricks")
        t.set(16, 2, z, "water", {"level": "0"})
        t.set(16, bank - 1, z, "water", {"level": "0"})
    t.set(16, bank, 3, "spruce_fence_gate", {"facing": "south", "in_wall": "false", "open": "false", "powered": "false"})
    t.trapdoor(16, bank + 1, 3, "spruce_trapdoor", "south")
    for x in (15, 17):
        t.set(x, bank, 3, "stone_brick_wall")
    for x in range(14, 19):
        if x != 16:
            t.slab(x, bank, 7, "spruce_slab")
    t.trapdoor(16, bank, 7, "spruce_trapdoor", "east", half="bottom")
    # the gate and its notices, the path to the door
    for x in (6, 7, 11, 12):
        t.set(x, bank, 0, "oak_fence")
    t.set(9, bank, 0, "oak_fence_gate", {"facing": "south", "in_wall": "false", "open": "false", "powered": "false"})
    for x in (8, 10):
        t.set(x, bank, 0, "oak_log")
        t.set(x, bank + 1, 0, "oak_planks")
    first, second = MILL_NOTICES[variant]
    t.sign(10, bank + 1, 1, "south", first)
    t.sign(8, bank + 1, 1, "south", second)
    for z in (1, 2):
        t.set(9, bank - 1, z, "dirt_path")
    # the bench on the far side, the old apple trees, one with its grey branch, hives among the flowers
    t.stairs(18, bank, 9, "spruce_stairs", "north")
    t.stairs(19, bank, 9, "spruce_stairs", "north")
    for tx, tz in ((2, 2), (19, 4), (2, 7)):
        pillar(t, tx, bank, bank + 2, tz, "oak_log")
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for dy in (3, 4):
                    if (dx, dz) != (0, 0) or dy == 4:
                        if 0 <= tx + dx < 21 and t.empty(tx + dx, bank + dy, tz + dz):
                            t.set(tx + dx, bank + dy, tz + dz, "oak_leaves", {"distance": "1", "persistent": "true",
                                                                              "waterlogged": "false"})
        t.log(tx, bank + 3, tz, "oak_log")
    t.log(18, bank + 2, 4, "stripped_birch_log", "x")
    t.log(17, bank + 2, 4, "stripped_birch_log", "x")
    for x, z, block in ((1, 5, "beehive"), (3, 9, "beehive"), (18, 6, "poppy"), (1, 4, "cornflower"), (4, 6, "dandelion"),
                        (19, 7, "cornflower"), (0, 9, "azure_bluet")):
        if block == "beehive":
            t.set(x, bank, z, block, {"facing": "east", "honey_level": "4"})
        elif t.empty(x, bank, z):
            t.set(x, bank, z, block)
    lights_out(t, 52990431 + variant, 0.5)
    broken_glass(t, 52990431 + variant, 0.3)
    weather(t, 52990431 + variant, ground=bank, moss=0.5, height=5, vines=0.2, webs=0.06, plants=0.3,
            inside=(x0, x1, z0, z1))
    if variant:
        t = mirrored(t)
    return t


def town_well(t, cx, cz, wood="spruce"):
    """One of the wells of Three Wells: a ring of cobblestone round the water, a little gabled roof on two posts, the
    bucket's chain."""
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if (x, z) == (cx, cz):
                t.set(x, 0, z, "water", {"level": "0"})
            else:
                t.set(x, 0, z, "cobblestone")
                t.set(x, 1, z, "cobblestone_wall")
    for z in (cz - 1, cz + 1):
        pillar(t, cx, 2, 3, z, wood + "_fence")
        t.set(cx, 1, z, "cobblestone")
    for z in range(cz - 2, cz + 3):
        t.stairs(cx - 1, 4, z, wood + "_stairs", "east")
        t.stairs(cx + 1, 4, z, wood + "_stairs", "west")
        t.slab(cx, 5, z, wood + "_slab")
    t.set(cx, 4, cz, "chain", {"axis": "y", "waterlogged": "false"})
    t.set(cx, 3, cz, "chain", {"axis": "y", "waterlogged": "false"})


def three_wells(variant=0):
    """The square at Three Wells, where two roads cross like an X: paving round the three wells, a lamp at every corner
    and the bell at the crossing; the notice board with the market rules; stalls under wool awnings; the bakery of stone
    and timber with its oven built out on the side; the school house by the east well, of Copperhold stone and
    Saltreach glass, its door painted by the children, its bell in a cote on the roof; benches under two trees; and a
    stretch of the town wall, cobblestone, cobblestone, deep stone, buttressed, its top row stuck out against
    spiders."""
    rng = random.Random(70413227 + variant)
    t = Template(35, 13, 35)
    lawn(t, 35, 35, rng)
    # the roads crossing like an X, the square paved where they meet
    for k in range(35):
        for w in (-1, 0, 1):
            for x, z in ((k, k + w), (k, 34 - k + w)):
                if 0 <= z < 35 and rng.random() < 0.85:
                    t.set(x, 0, z, rng.choice(["dirt_path", "dirt_path", "coarse_dirt", "gravel"]))
    for x in range(9, 26):
        for z in range(9, 26):
            r = rng.random()
            t.set(x, 0, z, "cobblestone" if r < 0.7 else "mossy_cobblestone" if r < 0.85 else "stone")
    for cx, cz in ((17, 11), (23, 17), (17, 23)):
        town_well(t, cx, cz)
    # the bell at the crossing on its frame; a lamp at every corner
    pillar(t, 16, 1, 4, 17, "spruce_log")
    pillar(t, 18, 1, 4, 17, "spruce_log")
    for x in (15, 16, 17, 18, 19):
        t.log(x, 5, 17, "spruce_log", "x")
    t.stairs(15, 5, 17, "spruce_stairs", "east", half="top")
    t.stairs(19, 5, 17, "spruce_stairs", "west", half="top")
    t.set(17, 4, 17, "bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    for x, z in ((9, 9), (25, 9), (9, 25), (25, 25)):
        t.set(x, 1, z, "cobblestone_wall")
        t.set(x, 2, z, "spruce_fence")
        t.set(x, 3, z, "lantern", {"hanging": "false", "waterlogged": "false"})
    # the notice board on the west side of the square
    pillar(t, 10, 1, 4, 14, "spruce_log")
    pillar(t, 10, 1, 4, 20, "spruce_log")
    t.fill(10, 2, 15, 10, 3, 19, "spruce_planks")
    for z in range(14, 21):
        t.stairs(10, 5, z, "spruce_stairs", "west")
        t.stairs(11, 4, z, "spruce_stairs", "west")
    t.sign(11, 3, 15, "east", ["Market day: once", "a week, dawn", "until the noon", "bell."])
    t.sign(11, 3, 19, "east", ["Lost children", "wait at the", "north well."])
    t.sign(11, 2, 17, "east", ["Do not sit on", "the south well."])
    # stalls under wool awnings round the wells, their counters and wares
    wares = [("pumpkin", None), ("melon", None), ("hay_block", {"axis": "y"}), ("barrel", {"facing": "up", "open": "false"})]
    for (sx, sz, wool), (ware, props) in zip(((13, 12, "red_wool"), (20, 13, "yellow_wool"), (13, 21, "blue_wool"),
                                              (21, 21, "lime_wool")), wares):
        for x in (sx, sx + 2):
            pillar(t, x, 1, 2, sz, "spruce_fence")
            pillar(t, x, 1, 2, sz + 1, "spruce_fence")
        for x in range(sx - 1, sx + 4):
            t.set(x, 3, sz, wool)
            t.set(x, 3, sz + 1, wool)
            t.set(x, 3, sz - 1, wool.replace("_wool", "_carpet"))
        t.barrel(sx + 1, 1, sz, "up", "residual:chests/three_wells_stall")
        t.slab(sx + 1, 1, sz + 1, "spruce_slab", "top")
        t.set(sx + 1, 2, sz + 1, ware, props)
    # two trees with benches round them
    for tx, tz in ((21, 9), (12, 25)):
        pillar(t, tx, 1, 3, tz, "oak_log")
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for dy in (4, 5):
                    t.set(tx + dx, dy, tz + dz, "oak_leaves", {"distance": "1", "persistent": "true", "waterlogged": "false"})
        for f, (dx, dz) in SIDES.items():
            t.stairs(tx + dx, 1, tz + dz, "spruce_stairs", f)
    # the bakery to the north-west: a stone footing, an oak frame with plaster set back, its oven built out on the side
    bx0, bx1, bz0, bz1 = 2, 9, 2, 8
    t.fill(bx0, 0, bz0, bx1, 0, bz1, "cobblestone")
    boards(t, bx0 + 2, bx1 - 2, bz0 + 2, bz1 - 2, 0, "spruce_planks", "stripped_spruce_log")
    for face, plane, a0, a1, posts in (("north", bz0, bx0, bx1, [2, 5, 9]), ("south", bz1, bx0, bx1, [2, 5, 9]),
                                       ("west", bx0, bz0, bz1, [2, 5, 8]), ("east", bx1, bz0, bz1, [2, 5, 8])):
        framed(t, face, plane, a0, a1, 1, 4, "stripped_spruce_log", "calcite", posts=posts, rails=(3,), pad="cobblestone")
    for u, y, a_ in roof(t, 5, bx0, bx1, bz0, bz1, "spruce_stairs", "x", brackets="spruce_stairs"):
        inward = 1 if u == bx0 else -1
        t.set(u, y, a_, "air")
        t.set(u + inward, y, a_, "calcite")
    t.door(7, 1, bz1 - 1, "spruce_door", "south")
    t.set(7, 1, bz1, "air")
    window(t, "south", 3, 2, bz1 - 1, h=2, sill="spruce_stairs", shutters="spruce_trapdoor")
    t.set(3, 2, bz1, "cake", {"bites": "1"})
    t.set(4, 2, bz1, "air")
    window(t, "north", 7, 2, bz0 + 1, h=2, shutters="spruce_trapdoor")
    dormer(t, 5, bz1, 5, "south", "spruce_stairs", "stripped_spruce_log", "calcite")
    # the oven built out on the west side, its chimney up past the eaves
    t.fill(bx0 - 2, 0, 4, bx0 - 1, 0, 6, "cobblestone")
    t.fill(bx0 - 2, 1, 4, bx0 - 1, 2, 6, "bricks")
    t.stairs(bx0 - 2, 3, 4, "brick_stairs", "east")
    t.stairs(bx0 - 2, 3, 6, "brick_stairs", "east")
    t.stairs(bx0 - 2, 3, 5, "brick_stairs", "east")
    chimney(t, bx0 - 1, 5, 3, 9, "bricks", cap="brick_slab")
    t.set(bx0 + 2, 1, 5, "smoker", {"facing": "east", "lit": "false"})
    t.barrel(bx0 + 2, 1, bz0 + 2, "up", "residual:chests/three_wells_bakery")
    t.lantern(5, 4, 5, hanging=True)
    # the school house by the east well: Copperhold stone between pilasters, Saltreach glass, a painted door, its bell
    sx0, sx1, sz0, sz1 = 26, 33, 12, 22
    t.fill(sx0, 0, sz0, sx1, 0, sz1, "stone_bricks")
    boards(t, sx0 + 2, sx1 - 2, sz0 + 2, sz1 - 2, 0, "spruce_planks", "stripped_spruce_log", axis="z")
    for face, plane, a0, a1, posts in (("north", sz0, sx0, sx1, [26, 29, 33]), ("south", sz1, sx0, sx1, [26, 30, 33]),
                                       ("west", sx0, sz0, sz1, [12, 15, 17, 19, 22]),
                                       ("east", sx1, sz0, sz1, [12, 15, 17, 19, 22])):
        framed(t, face, plane, a0, a1, 1, 4, "chiseled_stone_bricks", "stone_bricks", posts=posts, rails=(1,),
               pad="stone_bricks")
        _, (nx, nz) = run(face)
        for a_ in range(a0 + 1, a1):
            if a_ in posts:
                continue
            x, z = cell(face, plane, a_)
            for y in (2, 3):
                t.set(x - nx, y, z - nz, "glass_pane")
            t.stairs(x, 1, z, "stone_brick_stairs", OPP[face], half="top")
    for u, y, a_ in roof(t, 5, sx0, sx1, sz0, sz1, "stone_brick_stairs", "z", brackets="stone_brick_stairs"):
        inward = 1 if u == sz0 else -1
        t.set(a_, y, u, "air")
        t.set(a_, y, u + inward, "glass_pane" if a_ in (29, 30) and y == 6 else "stone_bricks")
    t.door(sx0 + 1, 1, 17, "warped_door", "west")
    t.set(sx0, 1, 17, "air")
    t.set(sx0, 2, 17, "air")
    t.stairs(sx0 - 1, 0, 17, "stone_brick_stairs", "east")
    for z in (16, 18):
        pillar(t, sx0 - 1, 1, 3, z, "stone_brick_wall")
    for z in range(15, 20):
        t.slab(sx0 - 1, 4, z, "stone_brick_slab")
    for z in (16, 17, 18):
        t.slab(sx0 - 2, 4, z, "stone_brick_slab")
    # the bell in its cote on the school's ridge
    pillar(t, 29, 10, 11, 16, "stone_brick_wall")
    pillar(t, 29, 10, 11, 18, "stone_brick_wall")
    t.slab(29, 12, 16, "stone_brick_slab")
    t.slab(29, 12, 17, "stone_brick_slab")
    t.slab(29, 12, 18, "stone_brick_slab")
    t.set(29, 11, 17, "bell", {"attachment": "ceiling", "facing": "west", "powered": "false"})
    # inside the school: rows of benches facing the teacher's lectern, the spelling lesson on the wall, the chest
    for z in range(sz0 + 2, sz1 - 1, 2):
        for x in (sx0 + 2, sx0 + 4):
            t.stairs(x, 1, z, "spruce_stairs", "west")
    t.set(sx1 - 2, 1, 17, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    t.sign(sx1 - 2, 2, 15, "west", ["Spelling, the", "first class:", "well, wheel,", "wheat, weave"])
    t.chest(sx0 + 2, 1, sz1 - 2, "east", "residual:chests/three_wells_school")
    t.lantern(29, 4, 17, hanging=True)
    # the town wall: cobblestone, cobblestone, deep stone, buttressed, its top row stuck out one block, a torch every
    # eight, a gate in it
    for x in range(35):
        for y, block in ((1, "cobblestone"), (2, "cobblestone"), (3, "cobbled_deepslate")):
            t.set(x, y, 33, block)
        t.set(x, 0, 33, "cobblestone")
        t.slab(x, 4, 33, "cobblestone_slab")
        t.slab(x, 3, 34, "cobblestone_slab", "top")
        if x % 8 == 4:
            t.set(x, 5, 33, "torch")
        if x % 6 == 1:
            buttress(t, x, 33, "north", 1, 2, "cobblestone", "cobblestone_stairs")
    for x in range(15, 20):
        for y in (1, 2, 3, 4):
            t.set(x, y, 33, "air")
        t.set(x, 3, 34, "air")
    for x in (14, 20):
        pillar(t, x, 1, 4, 33, "cobbled_deepslate")
        t.set(x, 5, 33, "cobblestone_wall")
    t.set(15, 1, 33, "spruce_fence_gate", {"facing": "south", "in_wall": "false", "open": "true", "powered": "false"})
    # what the years did: moss in the paving and on the wall, grass coming through, vines, lamps out, glass gone
    lights_out(t, 70413227 + variant, 0.6)
    broken_glass(t, 70413227 + variant, 0.35)
    weather(t, 70413227 + variant, ground=1, moss=0.4, crack=0.2, height=4, vines=0.2, webs=0.05, plants=0.25)
    for x in range(9, 26):
        for z in range(9, 26):
            if t.name(x, 0, z) in ("cobblestone", "mossy_cobblestone", "stone") and t.empty(x, 1, z) \
                    and rng.random() < 0.08:
                t.set(x, 1, z, "short_grass")
    return t




def mine_tunnel(t, x0, z0, dx, dz, length, y, rng, ore, wall="stone"):
    """A level's tunnel from x0, z0 along dx, dz, three wide and three high in a shell of wall: timbered every fourth
    step, rails down the middle with every eighth one powered, a torch every eighth step on the left going in, ore
    showing in its walls."""
    lx, lz = -dz, dx
    facing = {v: k for k, v in SIDES.items()}[(-lx, -lz)]
    shape = "north_south" if dz else "east_west"
    for k in range(length):
        x, z = x0 + dx * k, z0 + dz * k
        for w in (-3, -2, -1, 0, 1, 2, 3):
            for h in (-2, -1, 0, 1, 2, 3, 4):
                inner = abs(w) <= 1 and 0 <= h <= 2
                cx_, cz_ = x + lx * w, z + lz * w
                if inner:
                    t.set(cx_, y + h, cz_, "air")
                elif t.empty(cx_, y + h, cz_) and (cx_, y + h, cz_) not in t.blocks:
                    t.set(cx_, y + h, cz_, ore if abs(w) == 2 and 0 <= h <= 2 and rng.random() < 0.12 else wall)
        if rng.random() < 0.85:
            if k % 8 == 0:
                t.set(x, y, z, "powered_rail", {"shape": shape, "powered": "false", "waterlogged": "false"})
            else:
                t.set(x, y, z, "rail", {"shape": shape, "waterlogged": "false"})
        if k % 4 == 2:
            for w in (-1, 1):
                t.log(x + lx * w, y, z + lz * w, "spruce_log")
                t.log(x + lx * w, y + 1, z + lz * w, "spruce_log")
            for w in (-1, 0, 1):
                t.slab(x + lx * w, y + 2, z + lz * w, "spruce_slab", "top")
        elif k % 8 == 4:
            t.wall_torch(x + lx, y + 1, z + lz, facing)


def deep_works(variant=0):
    """The Deep Works under Copperhold, as its miners wrote of it. At the shaft head, over the shaft, the headframe and
    its wheel on a stone apron; the board where miners write their names, a few never rubbed out; the tool store, the
    sorting bins under their roof. Down the shaft by ladder: the copper level, the iron and coal level with the
    amethyst hollow behind its door, the deep stone with the old west tunnel walled up over its spider cage, and at the
    ninth level the locked gate. Past it, by the old side door on the eighth: a hall too regular for a mine, its stone
    warm, a dead bank of quickeners along its wall. Nothing there says what it was."""
    rng = random.Random(12807731 + variant)
    t = Template(25, 55, 25)
    top = 42  # the ground at the shaft head
    sx, sz = 12, 12
    # the shaft, three across in a shell of stone bricks, with its ladder, from the shaft head down to the ninth level
    for y in range(9, top):
        for x in range(sx - 3, sx + 4):
            for z in range(sz - 3, sz + 4):
                edge = abs(x - sx) >= 2 or abs(z - sz) >= 2
                if edge:
                    if (x, y, z) not in t.blocks:
                        t.set(x, y, z, "stone_bricks" if y > 20 else "deepslate_bricks")
                else:
                    t.set(x, y, z, "air")
        t.set(sx, y, sz - 2, "stone_bricks" if y > 20 else "deepslate_bricks")
        t.set(sx, y, sz - 1, "ladder", {"facing": "south", "waterlogged": "false"})
    # the shaft head: a stone apron, the headframe and its wheel, the lift's chain
    for x in range(sx - 5, sx + 6):
        for z in range(sz - 5, sz + 6):
            if not (sx - 1 <= x <= sx + 1 and sz - 1 <= z <= sz + 1):
                t.set(x, top - 1, z, rng.choice(["stone_bricks", "stone_bricks", "cracked_stone_bricks", "stone",
                                                  "mossy_stone_bricks"]))
    for x, z in ((sx - 2, sz - 2), (sx + 2, sz - 2), (sx - 2, sz + 2), (sx + 2, sz + 2)):
        pillar(t, x, top, top + 8, z, "spruce_log")
    for y in (top + 4, top + 8):
        for k in range(sx - 1, sx + 2):
            t.log(k, y, sz - 2, "spruce_log", "x")
            t.log(k, y, sz + 2, "spruce_log", "x")
        for k in range(sz - 1, sz + 2):
            t.log(sx - 2, y, k, "spruce_log", "z")
            t.log(sx + 2, y, k, "spruce_log", "z")
    for x, z, f in ((sx - 3, sz - 2, "east"), (sx + 3, sz - 2, "west"), (sx - 3, sz + 2, "east"), (sx + 3, sz + 2, "west")):
        t.stairs(x, top, z, "spruce_stairs", f)
    t.log(sx, top + 9, sz, "spruce_log", "z")
    for dx, dy in ((-2, 0), (2, 0), (0, -2), (0, 2), (-1, 1), (1, 1), (-1, -1), (1, -1)):
        t.set(sx + dx, top + 9 + dy, sz, "spruce_planks")
    for dx, dy in ((-1, 0), (1, 0), (0, 1), (0, -1)):
        t.set(sx + dx, top + 9 + dy, sz, "spruce_fence")
    for y in range(top + 1, top + 8):
        t.set(sx + 1, y, sz, "chain", {"axis": "y", "waterlogged": "false"})
    # the board where miners write their names, and rub them out coming up; some names stayed
    pillar(t, sx - 6, top, top + 3, sz + 3, "spruce_log")
    pillar(t, sx - 6, top, top + 3, sz + 7, "spruce_log")
    t.fill(sx - 6, top, sz + 4, sx - 6, top + 2, sz + 6, "spruce_planks")
    for z in range(sz + 3, sz + 8):
        t.stairs(sx - 6, top + 4, z, "spruce_stairs", "west")
    t.sign(sx - 5, top + 2, sz + 4, "east", ["Write your name,", "level, tunnel.", "Rub it out when", "you come up."])
    t.sign(sx - 5, top + 1, sz + 5, "east", ["Arlo, 10", "Hesk, 11, the", "new cut", ""])
    t.sign(sx - 5, top + 2, sz + 6, "east", ["Rook, 9", "Fen, 9", "Wick, 10", ""])
    # the tool store: a stone footing, a spruce frame with boards set back, a roof
    x0, x1, z0, z1 = sx + 4, sx + 10, sz - 8, sz - 3
    t.fill(x0, top - 1, z0, x1, top - 1, z1, "stone_bricks")
    for face, plane, a0, a1, posts in (("north", z0, x0, x1, [x0, x1]), ("south", z1, x0, x1, [x0, x0 + 3, x1]),
                                       ("west", x0, z0, z1, [z0, z1]), ("east", x1, z0, z1, [z0, z1])):
        framed(t, face, plane, a0, a1, top, top + 2, "stone_bricks", "stone", posts=posts,
               pad="stone_bricks")
    for u, y, a_ in roof(t, top + 3, x0, x1, z0, z1, "deepslate_tile_stairs", "x", brackets="stone_brick_stairs"):
        inward = 1 if u == x0 else -1
        t.set(u, y, a_, "air")
        t.set(u + inward, y, a_, "stone_bricks")
    t.door(x0 + 2, top, z1 - 1, "spruce_door", "south")
    t.set(x0 + 2, top, z1, "air")
    t.chest(x0 + 2, top, z0 + 2, "south", "residual:chests/deep_works_tools")
    t.barrel(x1 - 2, top, z0 + 2, "up", "residual:chests/deep_works_tools")
    t.barrel(x1 - 2, top, z0 + 3, "up")
    t.set(x0 + 3, top, z0 + 2, "grindstone", {"face": "floor", "facing": "south"})
    # the sorting bins under their roof, each marked
    for k, (ore, mark) in enumerate((("raw_copper_block", "Copper"), ("raw_iron_block", "Iron"), ("coal_block", "Coal"))):
        bx = sx - 7 + 2 * k
        t.barrel(bx, top, sz - 6, "up")
        t.set(bx, top + 1, sz - 6, ore)
        t.sign(bx, top, sz - 5, "south", [mark])
    for x in range(sx - 8, sx - 1):
        t.slab(x, top + 3, sz - 6, "deepslate_tile_slab")
        t.slab(x, top + 3, sz - 7, "deepslate_tile_slab")
    for x in (sx - 8, sx - 2):
        pillar(t, x, top, top + 2, sz - 7, "spruce_fence")
    # the levels: copper going east, iron and coal going west, the deep stone going north
    mine_tunnel(t, sx + 3, sz, 1, 0, 10, 34, rng, "copper_ore")
    mine_tunnel(t, sx - 3, sz, -1, 0, 10, 26, rng, "iron_ore")
    for x in range(2, sx - 3, 3):
        t.set(x, 27, sz - 2, "coal_ore")
    mine_tunnel(t, sx, sz - 3, 0, -1, 10, 18, rng, "deepslate_iron_ore", wall="deepslate")
    for y, d in ((34, 1), (26, -1)):
        for h in range(3):
            t.set(sx + 2 * d, y + h, sz, "air")
    for h in range(3):
        t.set(sx, 18 + h, sz - 2, "air")
    # the amethyst hollow, off the iron level behind its wooden door: "Do not mine. Look"
    t.door(2, 26, sz + 2, "spruce_door", "south")
    t.sign(3, 28, sz + 2, "north", ["Do not mine.", "Look."])
    for x in range(0, 6):
        for y in range(24, 31):
            for z in range(sz + 3, sz + 10):
                d = ((x - 2.5) / 2.8) ** 2 + ((y - 27.5) / 3.2) ** 2 + ((z - sz - 6) / 3.2) ** 2
                if d <= 1:
                    t.set(x, y, z, "air" if d < 0.55 else "amethyst_block" if d < 0.8 else "calcite")
    for x, y, z, f in ((2, 25, sz + 6, "up"), (1, 26, sz + 5, "up"), (3, 26, sz + 7, "up"), (2, 30, sz + 6, "down")):
        t.set(x, y, z, "amethyst_cluster", {"facing": f, "waterlogged": "false"})
    t.set(2, 26, sz + 3, "air")
    t.set(2, 27, sz + 3, "air")
    # the old west tunnel on the deep stone level, walled up with cobblestone over its spider cage
    for z in range(sz - 6, sz - 2):
        for y in (18, 19, 20):
            t.set(sx - 3, y, z, "cobbled_deepslate")
    for x in range(sx - 8, sx - 3):
        for z in range(sz - 6, sz - 3):
            for y in (18, 19, 20):
                t.set(x, y, z, "air")
            t.set(x, 17, z, "deepslate")
            t.set(x, 21, z, "deepslate")
    t.set(sx - 6, 18, sz - 5, "spawner", None, {"id": "minecraft:mob_spawner", "SpawnData": {"entity": {"id": "minecraft:cave_spider"}}})
    for x, y, z in ((sx - 7, 20, sz - 6), (sx - 5, 19, sz - 4), (sx - 7, 18, sz - 4), (sx - 4, 20, sz - 5),
                    (sx - 8, 19, sz - 5)):
        t.set(x, y, z, "cobweb")
    # the ninth level: the gate, locked; its key is at the hall
    for z in range(sz + 2, sz + 6):
        for x in (sx - 1, sx, sx + 1):
            for y in (10, 11, 12):
                t.set(x, y, z, "air")
    t.fill(sx - 1, 10, sz + 6, sx + 1, 12, sz + 6, "polished_deepslate")
    t.door(sx, 10, sz + 6, "iron_door", "south")
    t.sign(sx - 1, 12, sz + 5, "north", ["Closed below", "the ninth. The", "key is kept at", "the hall."])
    # below: a hall too regular for a mine, the stone under it warm, a dead bank of quickeners along its wall
    hx0, hx1, hz0, hz1 = 6, 18, 14, 24
    for x in range(hx0, hx1 + 1):
        for y in range(0, 8):
            for z in range(hz0, hz1 + 1):
                t.set(x, y, z, "polished_deepslate" if rng.random() < 0.6 else "deepslate_tiles")
    t.fill(hx0 + 1, 2, hz0 + 1, hx1 - 1, 6, hz1 - 1, "air")
    t.fill(hx0 + 1, 0, hz0 + 1, hx1 - 1, 0, hz1 - 1, "deepslate_tiles")
    t.fill(hx0 + 3, 0, hz0 + 3, hx1 - 3, 0, hz1 - 3, "magma_block")
    t.fill(hx0 + 1, 1, hz0 + 1, hx1 - 1, 1, hz1 - 1, "deepslate_tiles")
    for x in range(hx0 + 2, hx1 - 1, 3):
        for y in range(2, 7):
            t.set(x, y, hz0 + 1, "polished_deepslate")
    cable = {d: str(d in ("east", "west", "south")).lower() for d in ("north", "south", "east", "west", "up", "down")}
    for x in range(hx0 + 2, hx1 - 1, 2):
        t.set(x, 2, hz1 - 1, "residual:accelerator")
        t.set(x, 3, hz1 - 1, "residual:tensor_casing")
        t.set(x, 2, hz1 - 2, "residual:cable", cable)
    for x, y, z, face in ((hx0 + 1, 4, 18, "west"), (hx1 - 1, 5, 20, "east"), (10, 6, hz0 + 1, "north"),
                          (14, 3, hz1 - 1, "south")):
        t.set(x, y, z, "sculk_vein", {d: str(d == face).lower() for d in ("down", "up", "north", "south", "east", "west")}
              | {"waterlogged": "false"})
    t.chest(sx, 2, hz1 - 3, "north", "residual:chests/deep_works_hall")
    # the old side door on the eighth level, and the back way down past the gate, into the hall through its roof
    t.slab(sx + 1, 13, sz, "spruce_slab", "top")
    t.door(sx + 2, 14, sz, "spruce_door", "east")
    t.set(sx + 3, 14, sz, "air")
    t.set(sx + 3, 15, sz, "air")
    for k in range(11):
        x, y, z = sx + 3, 13 - k, sz + 1 + k
        for h in (1, 2, 3):
            if y + h > 1:
                t.set(x, y + h, z, "air")
        t.stairs(x, y, z, "cobbled_deepslate_stairs", "north")
        for side in (-1, 1):
            for h in (0, 1, 2, 3):
                if (x + side, y + h, z) not in t.blocks:
                    t.set(x + side, y + h, z, "deepslate")
    # what the years did: cracks, moss on the upper stone, roots through the upper levels, lichen below, webs
    for x, y, z in ((sx + 5, 37, sz), (sx + 8, 37, sz), (sx - 6, 29, sz), (sx - 9, 29, sz)):
        t.set(x, y, z, "hanging_roots", {"waterlogged": "false"})
        t.set(x, y + 1, z, "rooted_dirt")
    for (x, y, z), face in (((sx - 1, 20, sz - 7), "west"), ((sx + 1, 19, sz - 9), "east"), ((sx + 1, 11, sz + 3), "east"),
                            ((sx - 1, 12, sz + 4), "west")):
        t.set(x, y, z, "glow_lichen", {s_: str(s_ == face).lower() for s_ in ("north", "south", "east", "west", "up", "down")}
              | {"waterlogged": "false"})
    weather(t, 12807731 + variant, ground=top, moss=0.5, crack=0.35, height=12, webs=0.03)
    return t


FINDING_BOOK = [
    "The finding book of the Archive at Vell. One line for every text, in the order it came.",
    "1. The founders' page. Deepest room, in the oak box.\n2. Bread, the very next day. Second hall.",
    "40,112. Letter: Mira to Tam, the new tower. Northpoint shelf.\n40,113. Relay log, Ambrook, first evening.",
    "41,230. Copied this morning. The stacks are full again. We will dig.",
]
FOUNDERS_PAGE = ["We will keep everything we write.", "Not only what is wise. Not only what is true. Everything, so "
                 "that whoever comes after can find us."]


def archive_hall(t, x0, x1, z0, z1, y0, shelves, rng, well=None):
    """One of the Archive's halls: tuff walls between pillars, a vault of stairs, a dark floor, rows of shelves down its
    length with aisles between, the shelf's name on a card at the end of each row, lanterns hung between; a light well
    of glass up through the hill to the daylight where given (x, z, the height of the ground)."""
    for x in range(x0, x1 + 1):
        for y in range(y0 - 1, y0 + 6):
            for z in range(z0, z1 + 1):
                t.set(x, y, z, "tuff_bricks" if rng.random() < 0.7 else rng.choice(["tuff", "polished_tuff"]))
    t.fill(x0 + 1, y0, z0 + 1, x1 - 1, y0 + 4, z1 - 1, "air")
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            t.set(x, y0 - 1, z, "polished_tuff" if (x + z) % 4 else "chiseled_tuff")
    for z in range(z0 + 1, z1):
        t.stairs(x0 + 1, y0 + 4, z, "tuff_brick_stairs", "east", half="top")
        t.stairs(x1 - 1, y0 + 4, z, "tuff_brick_stairs", "west", half="top")
    for z in range(z0 + 2, z1 - 1, 4):
        for y in range(y0, y0 + 4):
            t.set(x0 + 1, y, z, "polished_tuff")
            t.set(x1 - 1, y, z, "polished_tuff")
    full = {f"slot_{i}_occupied": str(rng.random() < 0.85).lower() for i in range(6)}
    for k, x in enumerate(range(x0 + 3, x1 - 1, 3)):
        for z in range(z0 + 2, z1 - 1):
            for y in (y0, y0 + 1, y0 + 2):
                t.set(x, y, z, "chiseled_bookshelf" if (z + y) % 3 else "bookshelf",
                      {"facing": "east", **full} if (z + y) % 3 else None)
            t.slab(x, y0 + 3, z, "tuff_brick_slab")
        if k < len(shelves):
            t.sign(x, y0 + 1, z1 - 1, "south", [shelves[k], "shelf"])
    for x in range(x0 + 2, x1 - 1, 6):
        t.lantern(x, y0 + 4, (z0 + z1) // 2, hanging=True)
    if well:
        wx, wz, ground = well
        for y in range(y0 + 5, ground + 1):
            for dx, dz in ((0, 0), (1, 0), (0, 1), (1, 1)):
                t.set(wx + dx, y, wz + dz, "glass")
            for dx, dz in ((-1, 0), (2, 0), (-1, 1), (2, 1), (0, -1), (1, -1), (0, 2), (1, 2)):
                if (wx + dx, y, wz + dz) not in t.blocks:
                    t.set(wx + dx, y, wz + dz, "tuff_bricks")
        for dx, dz in ((0, 0), (1, 0), (0, 1), (1, 1)):
            t.set(wx + dx, y0 + 4, wz + dz, "air")
            t.set(wx + dx, ground + 1, wz + dz, "glass_pane")


def archive_at_vell(variant=0):
    """The Archive at Vell, dug into the hill, which is its roof. Its front faces the town: tuff between pillars with
    calcite set back, the portal under the words the first keepers cut, a broad flight of steps, the copying room's big
    window over the town and the reading room's; turf over everything behind the front, light wells of glass coming up
    through the grass. Inside: the copying room, the brightest room in the hill, with eight long tables, and the
    reading room, where a lamp still burns on the long table by the window. Down the great stair, inside the hill: the
    front hall with the finding book by the door; halls of shelves, each shelf with its name, under the light wells; a
    stack sealed with clay; and the deepest room, where the oldest record is kept in an oak box."""
    rng = random.Random(11930227 + variant)
    t = Template(33, 31, 40)
    g = 20  # the floor of the front rooms, at the ground before them
    lawn_y = g - 1
    for x in range(33):
        for z in range(28, 40):
            if rng.random() < 0.85:
                t.set(x, lawn_y, z, "grass_block", {"snowy": "false"})
            else:
                t.set(x, lawn_y, z, "coarse_dirt")
    # the front rooms behind the front: a tuff shell, floors of boards, a turf roof that is the hill
    x0, x1, z0, z1 = 5, 27, 20, 27
    for x in range(x0, x1 + 1):
        for y in range(g - 1, g + 7):
            for z in range(z0, z1):
                t.set(x, y, z, "tuff_bricks" if rng.random() < 0.7 else "tuff")
    t.fill(x0 + 1, g, z0 + 1, x1 - 1, g + 5, z1 - 1, "air")
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            t.set(x, g - 1, z, "polished_tuff" if (x + z) % 4 else "chiseled_tuff")
    for x in (14, 18):
        for z in range(z0 + 1, z1):
            for y in range(g, g + 6):
                t.set(x, y, z, "calcite")
        t.set(x, g, 23, "air")
        t.set(x, g + 1, 23, "air")
    # the turf over it, sloping down at the sides and the back: grass over a course of earth, bushes and flowers in it
    for x in range(x0 - 3, x1 + 4):
        for z in range(z0 - 6, z1 + 1):
            d = min(x - (x0 - 3), (x1 + 3) - x, z - (z0 - 6), (z1 + 1) - z)
            hgt = g + 7 if z == z1 else g + 7 + min(1, d) - (3 - min(d, 3))
            if (x, hgt - 1, z) not in t.blocks:
                t.set(x, hgt - 1, z, "dirt")
            if rng.random() < 0.15:
                t.set(x, hgt, z, "moss_block")
            else:
                t.set(x, hgt, z, "grass_block", {"snowy": "false"})
            r = rng.random()
            if r < 0.4:
                t.set(x, hgt + 1, z, rng.choices(PLANTS, PLANT_WEIGHTS)[0])
            elif r < 0.44:
                t.set(x, hgt + 1, z, "oak_leaves", {"distance": "1", "persistent": "true", "waterlogged": "false"})
    # the front: tuff pillars a block proud, calcite set back between them, a plinth with its slope, the cornice
    xs = [5, 9, 13, 15, 17, 19, 23, 27]
    framed(t, "south", z1, x0, x1, g, g + 6, "chiseled_tuff_bricks", "calcite", posts=xs, rails=(g + 5,),
           pad="polished_tuff")
    for x in range(x0 + 1, x1):
        if x not in xs:
            t.set(x, g, z1 - 1, "polished_tuff")
            t.stairs(x, g, z1, "tuff_brick_stairs", "north")
    for x in range(x0 - 1, x1 + 2):
        t.stairs(x, g + 7, z1 + 1, "tuff_brick_stairs", "north", half="top")
        t.set(x, g + 7, z1, "polished_tuff")
        t.set(x, g + 8, z1, "tuff_brick_wall")
    # the portal: three wide and four high, set back between its jambs, the words cut over it, a pediment above
    t.fill(15, g, z1 - 1, 17, g + 3, z1 - 1, "air")
    t.fill(15, g, z1, 17, g + 3, z1, "air")
    for x in (14, 18):
        pillar(t, x, g, g + 4, z1 + 1, "polished_tuff")
        t.set(x, g + 5, z1 + 1, "chiseled_tuff")
    t.fill(15, g + 4, z1, 17, g + 4, z1, "chiseled_tuff_bricks")
    t.sign(15, g + 4, z1 + 1, "south", ["We will keep"])
    t.sign(16, g + 4, z1 + 1, "south", ["everything"])
    t.sign(17, g + 4, z1 + 1, "south", ["we write."])
    for k in range(4):
        for x in range(13 + k, 20 - k):
            t.stairs(x, g + 8 + k, z1, "tuff_brick_stairs", "south")
        t.set(13 + k, g + 8 + k, z1, "tuff_bricks")
        t.set(19 - k, g + 8 + k, z1, "tuff_bricks")
    # a broad flight of steps up to the portal, a lamp on a post either side
    for x in range(12, 21):
        t.stairs(x, g - 1, z1 + 2, "tuff_brick_stairs", "north")
        t.set(x, g - 1, z1 + 1, "polished_tuff")
    for x in (11, 21):
        t.set(x, g, z1 + 2, "tuff_brick_wall")
        t.set(x, g + 1, z1 + 2, "tuff_brick_wall")
        t.lantern(x, g + 2, z1 + 2)
    # the copying room's big window over the town and the reading room's, mullioned
    for x in range(19, 27):
        for y in range(g + 1, g + 5):
            if x not in xs:
                t.set(x, y, z1 - 1, "glass_pane")
    for x in range(6, 13):
        for y in (g + 2, g + 3):
            if x not in xs:
                t.set(x, y, z1 - 1, "glass_pane")
    # the copying room: eight long tables in two rows, ink and paper, the copying barrel
    for tx in (20, 24):
        for tz in (21, 23, 25):
            for x in (tx, tx + 1):
                t.slab(x, g, tz, "spruce_slab", "top")
            t.stairs(tx - 1, g, tz, "spruce_stairs", "east")
    for x in (20, 21):
        t.slab(x, g, 25, "spruce_slab", "top")
    t.barrel(26, g, 21, "up", "residual:chests/archive_copying")
    t.barrel(26, g + 1, 21, "up")
    t.barrel(26, g, 22, "up")
    t.lantern(22, g + 5, 23, hanging=True)
    # the reading room: the long table by the window, the lamp left burning on it
    lamp = (9, g + 1, 25)
    for x in range(7, 12):
        t.slab(x, g, 25, "spruce_slab", "top")
    t.lantern(*lamp)
    for x in (7, 10):
        t.stairs(x, g, 24, "spruce_stairs", "south")
    for x in range(6, 13):
        t.set(x, g, 21, "bookshelf")
    t.lantern(10, g + 5, 23, hanging=True)
    # the great stair down into the hill from the middle room's back wall
    for k in range(10):
        z, y = z0 - k, g - 1 - k
        for x in (15, 16, 17):
            for h in (1, 2, 3):
                t.set(x, y + h, z, "air")
            t.stairs(x, y, z, "tuff_brick_stairs", "south")
        for h in range(0, 4):
            t.set(14, y + h, z, "tuff_bricks")
            t.set(18, y + h, z, "tuff_bricks")
        t.slab(15, y + 4, z, "tuff_brick_slab", "top")
        t.slab(17, y + 4, z, "tuff_brick_slab", "top")
    t.fill(15, g, z0, 17, g + 2, z0, "air")
    # the front hall at the stair's foot: the finding book by the door, the founding words again
    fy = g - 10
    t.fill(10, fy - 1, 11, 22, fy + 5, 17, "tuff_bricks")
    t.fill(11, fy, 12, 21, fy + 4, 16, "air")
    for x in range(11, 22):
        for z in range(12, 17):
            t.set(x, fy - 1, z, "polished_tuff" if (x + z) % 4 else "chiseled_tuff")
    t.fill(15, fy, 17, 17, fy + 2, 17, "air")
    for x in (11, 21):
        for z in (12, 16):
            pillar(t, x, fy, fy + 3, z, "polished_tuff")
    t.set(13, fy, 15, "lectern", {"facing": "east", "has_book": "true", "powered": "false"},
          {"id": "minecraft:lectern", "Page": 0, "Book": {"id": "minecraft:written_book", "count": 1, "components": {
              "minecraft:written_book_content": {"title": {"raw": "The finding book"}, "author": "the clerks",
                                                 "resolved": 1, "pages": [{"raw": p} for p in FINDING_BOOK]}}}})
    t.fill(15, fy + 3, 11, 17, fy + 3, 11, "chiseled_tuff_bricks")
    for x in (13, 19):
        t.set(x, fy + 4, 14, "chain", {"axis": "y", "waterlogged": "false"})
        t.lantern(x, fy + 3, 14, hanging=True)
    # the halls of shelves, each shelf with its name, light wells of glass up through the hill
    archive_hall(t, 1, 11, 2, 12, fy, ["Kettle Ridge", "Ambrook", "Lowmarsh"], rng, well=(5, 6, g + 4))
    archive_hall(t, 21, 31, 2, 12, fy, ["Saltreach", "old songs", "relay"], rng, well=(25, 6, g + 4))
    t.fill(11, fy, 13, 11, fy + 2, 13, "air")
    t.fill(21, fy, 13, 21, fy + 2, 13, "air")
    for x in (10, 11):
        t.set(x, fy, 13, "air")
    for x in (21, 22):
        t.set(x, fy, 13, "air")
    # down again to the deep halls: a stack sealed with clay, and the deepest room with the oldest record
    dy = fy - 8
    for k in range(7):
        z, y = 11 - k, fy - 1 - k
        for h in (1, 2, 3):
            t.set(16, y + h, z, "air")
        t.stairs(16, y, z, "tuff_brick_stairs", "south")
        for h in range(0, 4):
            for x in (15, 17):
                if (x, y + h, z) not in t.blocks:
                    t.set(x, y + h, z, "tuff_bricks")
    t.fill(10, dy - 1, 0, 22, dy + 5, 4, "tuff_bricks")
    t.fill(11, dy, 1, 21, dy + 4, 3, "air")
    t.fill(15, dy, 4, 17, dy + 2, 4, "air")
    t.fill(11, dy, 1, 11, dy + 2, 3, "clay")
    t.fill(10, dy, 1, 10, dy + 2, 3, "honeycomb_block")
    t.set(19, dy, 2, "polished_tuff")
    t.chest(19, dy + 1, 2, "west", "residual:chests/archive_deepest")
    t.lantern(16, dy + 4, 2, hanging=True)
    # what the years did: vines down the front and the light wells, moss on the steps, webs in the halls, roots
    for x in range(x0 - 1, x1 + 2):
        if rng.random() < 0.35 and t.empty(x, g + 6, z1 + 1):
            for y in range(g + 6, g + 6 - rng.randint(1, 4), -1):
                if t.empty(x, y, z1 + 1):
                    t.set(x, y, z1 + 1, "vine", {"north": "true", "south": "false", "east": "false", "west": "false",
                                                 "up": "false"})
    for x, z in ((4, 4), (8, 9), (24, 4), (28, 10), (13, 13)):
        t.set(x, fy + 4, z, "hanging_roots", {"waterlogged": "false"})
    lights_out(t, 11930227 + variant, 0.5, keep=[lamp])
    broken_glass(t, 11930227 + variant, 0.3)
    weather(t, 11930227 + variant, ground=g, height=6, webs=0.08, plants=0.0, inside=(1, 31, 0, 26), keep=[lamp])
    for x in range(2, 31):
        for z in range(3, 12):
            if t.empty(x, fy, z) and (x, fy, z) in t.blocks and rng.random() < 0.14:
                t.set(x, fy, z, "moss_carpet")
            if t.empty(x, fy + 4, z) and (x, fy + 4, z) in t.blocks and rng.random() < 0.08:
                t.set(x, fy + 4, z, "hanging_roots", {"waterlogged": "false"})
    for z in range(3, 12):
        for x, face in ((2, "west"), (30, "east"), (12, "east"), (20, "west")):
            if t.empty(x, fy + 2, z) and (x, fy + 2, z) in t.blocks and rng.random() < 0.3:
                t.set(x, fy + 2, z, "glow_lichen", {s_: str(s_ == face).lower() for s_ in ("north", "south", "east", "west",
                                                                                         "up", "down")} | {"waterlogged": "false"})
    for x in range(33):
        for z in range(29, 40):
            if t.name(x, lawn_y, z) == "grass_block" and (x, lawn_y + 1, z) not in t.blocks and rng.random() < 0.35:
                t.set(x, lawn_y + 1, z, rng.choices(PLANTS, PLANT_WEIGHTS)[0])
    return t


def weathered(*rules, integrity=0.95):
    """A ruin's processors: each (block, its weathered form, the share that weathered) as a rule, then the decay that
    takes out some of the blocks tagged ruin_rot."""
    rule = {"processor_type": "minecraft:rule", "rules": [
        {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": f"minecraft:{a}",
                             "probability": p},
         "location_predicate": {"predicate_type": "minecraft:always_true"},
         "output_state": {"Name": f"minecraft:{b}"}} for a, b, p in rules]}
    rot = {"processor_type": "minecraft:block_rot", "integrity": integrity, "rottable_blocks": "#residual:ruin_rot"}
    return ([rule] if rules else []) + [rot]


def legs_to_ground(marker, leg, depth=24):
    """The processor that runs each marker block's leg down to the ground."""
    state = {"Name": f"minecraft:{leg}"} | ({"Properties": {"axis": "y"}} if leg.endswith(LOGS) else {})
    return [{"processor_type": "residual:legs", "marker": f"minecraft:{marker}", "leg": state, "depth": depth}]


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
MILL_POND = ["Ambrook", "mill pond", "Jory", "mill wheel"]
WELLS = ["Three Wells", "north well", "market day", "notice board"]
DEEP = ["Deep Works", "Copperhold", "the mine", "deep stone"]
VELL = ["Archive at Vell", "the Archive", "Oren", "copying room"]
FALLEN = ["mast", "relay line"]
SEALED = ["sealed", "the stacks", "sealing"]


class Ruin:
    """A kind of ruin. build is a function that draws its template, or a list of (weight, function), its variants."""

    def __init__(self, build, spacing, separation, salt, step="surface_structures", start=0, terrain="beard_thin",
                 biomes=None, processors=None, heightmap="WORLD_SURFACE_WG", shore=False, words=(), slope=4, dry=True):
        self.build, self.spacing, self.separation, self.salt = build, spacing, separation, salt
        self.step, self.start, self.terrain, self.heightmap = step, start, terrain, heightmap
        # on the surface it stands only where the ground under it is within slope blocks of level, and dry unless
        # it is built for the water (the residual:ruin structure type)
        self.slope, self.dry = slope, dry
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
    "archive_house": Ruin([(1, lambda: archive_house(0)), (1, lambda: archive_house(1)), (1, lambda: archive_house(2))], 56, 18, 72930141, words=ARCHIVES),
    "relay_mast": Ruin([(2, lambda: relay_mast(0)), (1, lambda: relay_mast(1))], 56, 18, 61820453, words=MASTS),
    "buried_vault": Ruin([(1, lambda: buried_vault(0)), (1, lambda: buried_vault(1))], 64, 20, 50417729, step="underground_structures", start=-10, terrain="none",
                         words=VAULTS),
    "waymark": Ruin([(2, lambda: waymark_road(0)), (2, lambda: waymark_road(1)), (1, lambda: waymark_road(2)), (1, waymark_north)], 24, 8, 21094417, words=ROADS,
                    processors=weathered(*STONE_AGE, integrity=0.95)),
    "tutor_house": Ruin([(1, lambda: tutor_house(0)), (1, lambda: tutor_house(1))], 72, 22, 66190453, words=TUTORS,
                        processors=weathered(("tuff_bricks", "tuff", 0.1), integrity=0.92),
                        biomes=[f"minecraft:{b}" for b in ("plains", "meadow", "forest", "birch_forest", "flower_forest",
                                                            "taiga", "savanna", "cherry_grove")]),
    "fallen_relay_mast": Ruin([(1, lambda: fallen_relay_mast(0)), (1, lambda: fallen_relay_mast(1))], 40, 12, 18830291, start=-7, terrain="none", words=FALLEN,
                              processors=weathered(integrity=0.95)),
    "sealed_stacks": Ruin([(1, lambda: sealed_stacks(0)), (1, lambda: sealed_stacks(1))], 64, 20, 77015239, step="underground_structures", start=-16, terrain="none",
                          words=SEALED, processors=weathered(integrity=0.97)),
    "watermill": Ruin([(2, lambda: watermill(0)), (1, lambda: watermill(1))], 56, 18, 52990431, start=-4,
                      terrain="none", shore=True, words=MILL_POND,
                      processors=weathered(*STONE_AGE, integrity=0.94),
                      biomes=[f"minecraft:{b}" for b in ("river", "forest", "flower_forest", "birch_forest", "plains",
                                                          "meadow", "cherry_grove")]),
    "archive_at_vell": Ruin(lambda: archive_at_vell(0), 160, 48, 11930227, start=-20, slope=6, terrain="none", words=VELL,
                            processors=weathered(("tuff_bricks", "tuff", 0.08), integrity=0.96),
                            biomes=[f"minecraft:{b}" for b in ("plains", "meadow", "forest", "windswept_hills",
                                                                "birch_forest", "taiga")]),
    "deep_works": Ruin(lambda: deep_works(0), 128, 40, 12807731, start=-42, slope=8, terrain="none", words=DEEP,
                       processors=weathered(*STONE_AGE, integrity=0.95),
                       biomes=[f"minecraft:{b}" for b in ("windswept_hills", "windswept_gravelly_hills", "windswept_forest",
                                                           "meadow", "savanna_plateau")]),
    "three_wells": Ruin(lambda: three_wells(0), 80, 24, 70413227, words=WELLS, processors=weathered(*STONE_AGE, integrity=0.94),
                        biomes=[f"minecraft:{b}" for b in ("plains", "sunflower_plains", "meadow", "savanna")]),
    "inn": Ruin([(1, lambda: inn(0)), (1, lambda: inn(1))], 64, 20, 30091277, words=INNS, processors=weathered(*STONE_AGE, integrity=0.92)),
    "relay_tower": Ruin([(2, lambda: relay_tower(0)), (2, lambda: relay_tower(1)), (1, lambda: relay_tower(2))], 48, 16, 30817231, processors=weathered(*STONE_AGE, integrity=0.9), words=RELAYS),
    "windmill": Ruin([(1, lambda: windmill_farm(0)), (1, lambda: windmill_farm(1))], 56, 18, 48215537, processors=weathered(*STONE_AGE, integrity=0.9), words=MILLS,
                     biomes=[f"minecraft:{b}" for b in ("plains", "sunflower_plains", "meadow", "windswept_hills",
                                                         "savanna", "flower_forest")]),
    "slipway": Ruin([(2, lambda: slipway(0)), (1, lambda: slipway(1))], 40, 12, 90442817, start=-5, terrain="none",
                    processors=weathered(*STONE_AGE, integrity=0.96) + legs_to_ground("brown_stained_glass", "spruce_log"),
                    shore=True, words=SHIPS,
                    biomes=["minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore"]),
    "stilt_house": Ruin([(2, lambda: stilt_house(0)), (1, lambda: stilt_house(1))], 48, 16, 77120593, terrain="none",
                        heightmap="OCEAN_FLOOR_WG", dry=False,
                        processors=weathered(integrity=0.96) + legs_to_ground("brown_stained_glass", "mangrove_log"),
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
            mask("attention", 2), mask("layer_norm", 2), mask("mlp", 2), mask("transformer", 1), mask("swiglu", 1), mask("qk_norm", 1),
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
        mask("linear", 2), mask("softmax", 1), mask("rms_norm", 1), mask("rotary", 1), mask("z_loss", 1),
        item("minecraft:paper", 3, (1, 4)), item("minecraft:book", 2), item("minecraft:amethyst_shard", 3, (1, 3))])],
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
    "chests/watermill": [pool((2, 4), [
        item("minecraft:wheat", 6, (3, 8)), item("minecraft:bread", 5, (1, 4)), item("minecraft:apple", 6, (2, 6)),
        item("minecraft:honey_bottle", 3, (1, 3)), item("minecraft:honeycomb", 3, (1, 4)), item("archive_shard", 2, about=MILL_POND)])],
    "chests/watermill_jory": [pool((2, 4), [
        item("archive_shard", 10, (1, 3), about=MILL_POND), item("minecraft:paper", 3, (1, 3)),
        item("minecraft:emerald", 3, (1, 4)), item("minecraft:apple", 4, (1, 3)), item("minecraft:shears", 1)])],
    "chests/three_wells_stall": [pool((1, 3), [
        item("minecraft:bread", 4, (1, 3)), item("minecraft:apple", 4, (1, 4)), item("minecraft:honey_bottle", 2),
        item("minecraft:white_wool", 3, (1, 4)), item("minecraft:emerald", 3, (1, 3)), item("minecraft:salmon", 2, (1, 2))])],
    "chests/three_wells_bakery": [pool((2, 3), [
        item("minecraft:bread", 8, (2, 6)), item("minecraft:pumpkin_pie", 4, (1, 3)), item("minecraft:cookie", 3, (2, 6)),
        item("minecraft:wheat", 3, (2, 5)), item("minecraft:egg", 3, (1, 4)), item("archive_shard", 2, about=WELLS)])],
    "chests/three_wells_school": [pool((2, 4), [
        item("archive_shard", 10, (1, 3), about=WELLS + ["school", "Bel"]), item("minecraft:paper", 4, (2, 5)),
        item("minecraft:book", 3, (1, 2)), item("minecraft:feather", 2, (1, 3)), item("minecraft:ink_sac", 2)])],
    "chests/deep_works_tools": [pool((3, 5), [
        item("minecraft:iron_pickaxe", 3), item("minecraft:torch", 6, (4, 12)), item("minecraft:string", 4, (2, 6)),
        item("minecraft:rail", 4, (4, 12)), item("minecraft:powered_rail", 2, (1, 3)), item("minecraft:honeycomb", 3, (1, 4)),
        item("minecraft:copper_ingot", 3, (2, 6)), item("minecraft:lantern", 2), item("archive_shard", 4, (1, 2), about=DEEP)])],
    "chests/deep_works_hall": [
        pool((1, 1), [item("salvaged_model", 1, components={"residual:wreck": w}) for w in WRECKS]),
        pool((2, 4), [mask("attention", 2), mask("transformer", 2), mask("layer_norm", 1), mask("mlp", 1),
                      mask("modern_attention", 2), mask("modern_block", 1), mask("swiglu", 1),
                      item("weight_crystal", 4, (2, 5)), item("minecraft:amethyst_shard", 3, (2, 6)),
                      item("archive_drive", 2, drive=(12, ["Deep Works", "warm", "humming", "ninth"]))])],
    "chests/archive_copying": [pool((2, 4), [
        item("minecraft:paper", 6, (4, 12)), item("minecraft:ink_sac", 4, (2, 6)), item("minecraft:feather", 3, (2, 5)),
        item("minecraft:book", 3, (1, 3)), item("archive_shard", 6, (1, 3), about=VELL),
        item("archive_drive", 2, drive=(16, ["archive", "clerk", "Vell", "copy"]))])],
    "chests/archive_deepest": [
        pool((1, 1), [item("minecraft:written_book", 1, components={"minecraft:written_book_content": {
            "title": {"raw": "The founders' page"}, "author": "the first keepers", "resolved": True,
            "pages": [{"raw": p} for p in FOUNDERS_PAGE]}})]),
        pool((2, 3), [item("archive_drive", 1, drive=(24, ["founding", "first", "archive"])), item("weight_crystal", 3, (2, 4)),
                      mask("transformer", 1), mask("modern_block", 1), item("archive_shard", 4, (2, 4))])],
    "archaeology/ruin": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [
        item("archive_shard", 9), mask("matmul", 1), item("weight_crystal", 2)]}],
}


def write_all(write, rid):
    for name, r in RUINS.items():
        for _, build, template in r.variants(name):
            path = write.root / f"data/residual/structure/ruins/{template}.nbt"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(build().build())
        # each kind's weathering, or the shared decay; on the surface its ground laid on the land's own
        own = r.processors if r.processors is not None else weathered()
        if r.step == "surface_structures":
            own = own + [{"processor_type": "residual:ground"}]
        processors = rid(f"ruin/{name}")
        write(f"data/residual/worldgen/processor_list/ruin/{name}.json", {"processors": own})
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
        elif r.step == "surface_structures":
            write(f"data/residual/worldgen/structure/{name}.json", {
                "type": rid("ruin"), "biomes": biomes, "step": r.step, "spawn_overrides": {},
                "terrain_adaptation": r.terrain, "start_pool": rid(f"ruins/{name}"), "offset": r.start,
                "max_slope": r.slope, "heightmap": r.heightmap, "dry": r.dry})
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
