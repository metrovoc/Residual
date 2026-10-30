"""Draws every texture of the mod, pixel by pixel.

    uv run --with pillow python tools/art.py               # write the textures
    uv run --with pillow python tools/art.py preview.png   # and a preview sheet
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/residual"


def rgb(h, a=255):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# light casing
K0, K1, K2, K3, K4 = map(rgb, ["f4f6f8", "e7eaee", "d4d9e0", "b7bec8", "8f97a4"])
# inset lines: dark ends, lighter middle
NEUTRAL = (rgb("363d4a"), rgb("474f5f"))
TEAL_LINE = (rgb("1f9a8c"), rgb("36c9b9"))
AMBER_LINE = (rgb("c47d18"), rgb("eea436"))
# displays
W0, W1, W2, W3 = map(rgb, ["0b1014", "121920", "19222a", "28333e"])
# ramps, dark to white-hot
TEAL = [rgb(h) for h in ["0d3835", "166b62", "21a092", "3cd6c6", "9cf1e6", "e6fffb"]]
AMBER = [rgb(h) for h in ["472c09", "8a5613", "cf8a22", "ffb547", "ffd690", "fff3da"]]
DIM = [rgb(h) for h in ["161e25", "222c35", "2d3a46", "3b4b5a"]]
# graphite for the Model Core and the Model Server
G0, G1, G2, G3 = map(rgb, ["1d2229", "252b33", "2e353f", "3a434f"])
BRASS = [rgb(h) for h in ["3b2810", "6a4819", "9a6c2a", "c8973f", "e6c06e", "faeab8"]]
AMETHYST = [rgb(h) for h in ["2c1c47", "4c2f78", "7650ad", "a57fdf", "d0b6ff", "f2e9ff"]]
STONE = [rgb(h) for h in ["4b4842", "6f6b63", "9b968b", "beb9ae", "dcd8cf", "f1efe9"]]
PRINT = [rgb(h) for h in ["10253d", "17324f", "1f4166", "2c5784", "a9d2ff", "e6f3ff"]]
# item outlines
INK = rgb("161b21")
CLEAR = (0, 0, 0, 0)


class Canvas:
    def __init__(self, w=16, h=16, fill=CLEAR):
        self.im = Image.new("RGBA", (w, h), fill)

    def px(self, x, y, c):
        if 0 <= x < self.im.width and 0 <= y < self.im.height:
            self.im.putpixel((x, y), c)

    def get(self, x, y):
        return self.im.getpixel((x, y))

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def stamp(self, x0, y0, rows, pal):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal:
                    self.px(x0 + x, y0 + y, pal[ch])

    def copy(self):
        c = Canvas(self.im.width, self.im.height)
        c.im = self.im.copy()
        return c

    def save(self, rel):
        path = ASSETS / "textures" / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        self.im.save(path)
        SAVED[rel] = self.im


SAVED = {}


def speckle(x, y, salt=0):
    """A fixed, even scatter of slightly darker pixels, so plates don't look flat."""
    return ((x * 7 + y * 13 + salt * 5) ^ (x * y + salt)) % 9 == 0


def glyph_cells(rows):
    return {(x, y): ch for y, row in enumerate(rows) for x, ch in enumerate(row) if ch not in ". "}


def centered(rows, x0=2, size=12):
    h = len(rows)
    w = max(len(r) for r in rows)
    return x0 + (size - w) // 2, x0 + (size - h) // 2


# ---------------------------------------------------------------- faces

def plate(line, salt=0, base=None):
    """The shared frame: a light rim, the port-colored inset line, and a light plate inside."""
    c = Canvas()
    hi, lo = (K0, K1) if base is None else base
    for y in range(16):
        for x in range(16):
            c.px(x, y, lo if speckle(x, y, salt) else hi)
    for i in range(16):
        c.px(i, 0, K0)
        c.px(0, i, K0)
        c.px(i, 15, K2)
        c.px(15, i, K2)
    for i in range(1, 15):
        tone = line[0] if i in (1, 2, 13, 14) else line[1]
        for x, y in ((i, 1), (1, i), (i, 14), (14, i)):
            c.px(x, y, tone)
    if base is None:
        for i in range(3, 14):
            c.px(i, 13, K2)
            c.px(13, i, K2)
    return c


def window(c, x0=2, y0=2, x1=13, y1=13):
    """A recessed display: shadow on the upper left lip, a faint dot grid inside."""
    c.rect(x0, y0, x1, y1, W1)
    for y in range(y0 + 2, y1, 3):
        for x in range(x0 + 2, x1, 3):
            c.px(x, y, W2)
    for i in range(x0, x1 + 1):
        c.px(i, y0, W0)
        c.px(i, y1, W3)
    for i in range(y0, y1 + 1):
        c.px(x0, i, W0)
        c.px(x1, i, W3)
    c.px(x1, y0, W2)
    c.px(x0, y1, W2)


def glow_glyph(c, rows, ramp, halo=True):
    x0, y0 = centered(rows)
    cells = glyph_cells(rows)
    if halo:
        for (x, y) in cells:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in cells:
                    c.px(x0 + x + dx, y0 + y + dy, ramp[0])
    tone = {"-": ramp[1], "#": ramp[3], "+": ramp[4], "*": ramp[5]}
    for (x, y), ch in cells.items():
        c.px(x0 + x, y0 + y, tone[ch])


def dim_glyph(c, rows):
    x0, y0 = centered(rows)
    tone = {"-": DIM[1], "#": DIM[2], "+": DIM[3], "*": DIM[3]}
    for (x, y), ch in glyph_cells(rows).items():
        c.px(x0 + x, y0 + y, tone[ch])


def engraved_glyph(c, rows):
    """Stamped into the plate: a darker groove with a lit lower lip."""
    x0, y0 = centered(rows)
    cells = glyph_cells(rows)
    for (x, y) in cells:
        if (x, y + 1) not in cells:
            c.px(x0 + x, y0 + y + 1, K0)
    for (x, y), ch in cells.items():
        c.px(x0 + x, y0 + y, NEUTRAL[1] if ch in "+*" else K4)


SOCKET_MAIN = [
    ".dddddd.",
    "dhBBBBBd",
    "dB0000ad",
    "dB0pp0ad",
    "dB0pq0ad",
    "dB0000ad",
    "dBaaaaad",
    ".dddddd.",
]
SOCKET_SIDE = [
    "...dd...",
    "..dhBd..",
    ".dB00ad.",
    "dB0pq0ad",
    "dB0qq0ad",
    ".dB00ad.",
    "..dBad..",
    "...dd...",
]


def socket(c, rows, ramp):
    pal = {"d": NEUTRAL[0], "h": ramp[4], "B": ramp[3], "a": ramp[2], "0": W0, "p": ramp[1], "q": ramp[2]}
    c.stamp(4, 4, rows, pal)


# ---------------------------------------------------------------- operator glyphs, 8 px or less

GLYPHS = {
    "embedding": [
        "##.#####",
        "........",
        "++.+++++",
        "........",
        "##.#####",
        "........",
        "##.#####",
    ],
    "position": [
        ".##.....",
        "#..#...#",
        "....#.#.",
        ".....#..",
        "........",
        "+.+.+.+.",
        ".+.+.+.+",
    ],
    "linear": [
        "##.##.##",
        "##.##.##",
        "........",
        "##.++.##",
        "##.++.##",
        "........",
        "##.##.##",
        "##.##.##",
    ],
    "activation": [
        ".......+",
        "......+.",
        ".....+..",
        "....+...",
        "####....",
        "--------",
    ],
    "add": [
        "..#++#..",
        ".#.++.#.",
        "#..++..#",
        "++++++++",
        "++++++++",
        "#..++..#",
        ".#.++.#.",
        "..#++#..",
    ],
    "multiply": [
        "..####..",
        ".++..++.",
        "#.++++.#",
        "#..++..#",
        "#.++++.#",
        ".++..++.",
        "..####..",
    ],
    "norm": [
        "....++....",
        "...#..#...",
        "...#..#...",
        "..#....#..",
        "##......##",
        "----------",
    ],
    "matmul": [
        ".####..",
        "#....#.",
        "#.++.#.",
        "#+..+#.",
        "#.+++#.",
        "#......",
        ".#####.",
    ],
    "scale": [
        "....++++",
        "....#...",
        "...#....",
        "#..#....",
        ".#.#....",
        "..#.....",
    ],
    "causal_mask": [
        "+-------",
        "#+------",
        "##+-----",
        "###+----",
        "####+---",
        "#####+--",
        "######+-",
        "#######+",
    ],
    "softmax": [
        "....+...",
        "....+...",
        "....+...",
        "..#.+...",
        "..#.+.#.",
        "#.#.+.#.",
        "--------",
    ],
    "split_heads": [
        ".....+++",
        "....#...",
        "...#....",
        "########",
        "...#....",
        "....#...",
        ".....+++",
    ],
    "merge_heads": [
        "+++.....",
        "...#....",
        "....#...",
        "########",
        "....#...",
        "...#....",
        "+++.....",
    ],
    "shift": [
        "##.##.##",
        "##.##.##",
        "........",
        "-.......",
        "-++.++.+",
        "-++.++.+",
    ],
    "concat": [
        "##.+++++",
        "##.+++++",
        "##.+++++",
        "........",
        "#......#",
        "########",
    ],
    "attention": [
        "..####..",
        ".#....#.",
        "#..++..#",
        "#.+**+.#",
        ".#.++.#.",
        "..####..",
    ],
    "mlp": [
        "...++...",
        "........",
        "##.++.##",
        "........",
        "##.++.##",
        "........",
        "...++...",
    ],
    "transformer": [
        "#####.+",
        "#+++#.+",
        "#####.+",
        "..#...+",
        "#####.+",
        "#+++#.+",
        "#####.+",
    ],
}

PARAMETRIC = {"embedding", "position", "linear", "norm", "attention", "mlp", "transformer"}
ARITY = {"position": 0, "add": 2, "multiply": 2, "matmul": 2, "concat": 2}


def operator_faces(op):
    front = plate(TEAL_LINE, salt=1)
    window(front)
    glow_glyph(front, GLYPHS[op], TEAL)

    arity = ARITY.get(op, 1)
    if arity == 2:
        side = plate(AMBER_LINE, salt=2)
        socket(side, SOCKET_SIDE, AMBER)
    elif op in PARAMETRIC:
        side = plate(NEUTRAL, salt=2)
        window(side)
        dim_glyph(side, GLYPHS[op])
    else:
        side = plate(NEUTRAL, salt=2)
        engraved_glyph(side, GLYPHS[op])
    return front, side


def machine_back():
    back = plate(AMBER_LINE, salt=3)
    socket(back, SOCKET_MAIN, AMBER)
    return back


def blank_back(op):
    back = plate(NEUTRAL, salt=3)
    engraved_glyph(back, GLYPHS[op])
    return back


# ---------------------------------------------------------------- Model Core

EMITTER = [
    "..oooo..",
    ".oRRRRo.",
    "oRr..rRo",
    "oR.cc.Ro",
    "oR.cc.Ro",
    "oRr..rRo",
    ".oRRRRo.",
    "..oooo..",
]


def core_base(line, salt):
    c = plate(line, salt=salt, base=(G1, G2))
    for i in range(2, 14):
        c.px(i, 2, G3)
        c.px(2, i, G3)
        c.px(i, 13, G0)
        c.px(13, i, G0)
    # corner brackets
    for x, y in ((3, 3), (4, 3), (3, 4), (12, 3), (11, 3), (12, 4), (3, 12), (4, 12), (3, 11), (12, 12), (11, 12), (12, 11)):
        c.px(x, y, K4)
    return c


def core_front(phase=None):
    """Tokens leave through the front. Lit, a pulse runs out along the four traces."""
    on = phase is not None
    c = core_base(TEAL_LINE, salt=4)
    ring, shade, mid, hot = (TEAL[3], TEAL[2], TEAL[4], TEAL[5]) if on else (TEAL[2], TEAL[1], TEAL[1], TEAL[2])
    c.stamp(4, 4, EMITTER, {"o": G0, "R": ring, "r": shade, ".": W0, "c": hot if on else mid})
    for k in (2, 3):
        for a in (7, 8):
            lit = on and k == (2 if phase % 2 else 3)
            color = TEAL[4] if lit else TEAL[2] if on else TEAL[1]
            c.px(a, k, color)
            c.px(a, 15 - k, color)
            c.px(k, a, color)
            c.px(15 - k, a, color)
    return c


def core_side(phase=None):
    """Scores arrive on every other face: a grid of amber cells, flickering while the model trains."""
    on = phase is not None
    c = core_base(AMBER_LINE, salt=5)
    levels = [[2, 4, 1], [3, 1, 4], [1, 4, 2]]
    for gy in range(3):
        for gx in range(3):
            x, y = 4 + gx * 3, 4 + gy * 3
            if on:
                v = (levels[gy][gx] + phase) % 5
                color = [AMBER[1], AMBER[2], AMBER[3], AMBER[4], AMBER[3]][v]
            else:
                color = AMBER[1]
            c.rect(x, y, x + 1, y + 1, color)
            c.px(x + 1, y + 1, mix(color, (0, 0, 0, 255), 0.25))
    return c


def animated(frames, rel, frametime):
    strip = Canvas(16, 16 * len(frames))
    for i, f in enumerate(frames):
        strip.im.paste(f.im, (0, 16 * i))
    strip.save(rel)
    meta = ASSETS / "textures" / (rel + ".mcmeta")
    meta.write_text(json.dumps({"animation": {"frametime": frametime}}, indent=2) + "\n")


# ---------------------------------------------------------------- other blocks

CHIP = [
    "....l..l....",
    "....l..l....",
    "..dddddddd..",
    "..dhhhhhhd..",
    "lldhGGGGGdll",
    "..dhGttGGd..",
    "..dhGttGGd..",
    "lldhGGGGGdll",
    "..dhGGGGGd..",
    "..dddddddd..",
    "....l..l....",
    "....l..l....",
]


def accelerator():
    """A bare chip on a plate, its leads running out to the frame."""
    c = plate(NEUTRAL, salt=6)
    c.stamp(2, 2, CHIP, {"l": K4, "d": NEUTRAL[0], "h": G3, "G": G2, "t": TEAL[3]})
    c.px(7, 7, TEAL[5])
    return c


def casing():
    """Plain housing: an inner panel with a rivet in each corner."""
    c = plate(NEUTRAL, salt=7)
    for i in range(4, 12):
        c.px(i, 4, K3)
        c.px(4, i, K3)
        c.px(i, 11, K0)
        c.px(11, i, K0)
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        c.px(x, y, K4)
    return c


def accelerator_hot():
    """The same chip running hot: leads and die glowing amber."""
    c = plate(NEUTRAL, salt=6)
    c.stamp(2, 2, CHIP, {"l": AMBER[2], "d": NEUTRAL[0], "h": rgb("7a3a16"), "G": rgb("4a2412"), "t": AMBER[4]})
    c.px(7, 7, AMBER[5])
    return c


# ---------------------------------------------------------------- Model Server

SERVER_CRYSTAL = [
    "..TA..",
    ".TtaA.",
    "TttaaA",
    ".TtaA.",
    "..TA..",
]


def server_base(line, salt):
    c = plate(line, salt=salt, base=(G1, G2))
    for i in range(2, 14):
        c.px(i, 2, G3)
        c.px(2, i, G3)
        c.px(i, 13, G0)
        c.px(13, i, G0)
    return c


def server_front(crystal=False, phase=None):
    """A rack face: the crystal's bay above, request lights below that run while it answers."""
    on = phase is not None
    c = server_base(TEAL_LINE, salt=8)
    window(c, 4, 3, 11, 9)
    # the cradle a crystal sits in
    for x in (6, 9):
        c.px(x, 4, G3)
        c.px(x, 8, G3)
    if crystal:
        tones = (TEAL[4], TEAL[3], AMBER[3], AMBER[4]) if on else (TEAL[2], TEAL[1], AMBER[1], AMBER[2])
        c.stamp(5, 4, SERVER_CRYSTAL, dict(zip("TtaA", tones)))
        if on:
            c.px(7, 6, TEAL[5])
            c.px(8, 6, AMBER[5])
    c.rect(4, 11, 11, 11, W0)
    for i, x in enumerate((4, 6, 9, 11)):
        if not crystal:
            color = DIM[2]
        elif not on:
            color = AMBER[3] if i == 0 else DIM[3]
        else:
            color = TEAL[4] if (i + phase) % 4 in (0, 1) else TEAL[1]
        c.px(x, 11, color)
    return c


def server_side():
    """Vents: a Model Server runs warm."""
    c = server_base(NEUTRAL, salt=9)
    for y in (4, 6, 8, 10):
        for x in range(4, 12):
            c.px(x, y, G0)
            c.px(x, y + 1, G3)
    return c


def server_top():
    c = server_base(NEUTRAL, salt=10)
    for gy in (4, 7, 10):
        for gx in (4, 7, 10):
            c.rect(gx, gy, gx + 1, gy + 1, G0)
            c.px(gx + 1, gy + 1, G1)
            c.px(gx, gy + 2, G3)
            c.px(gx + 1, gy + 2, G3)
    return c


# ---------------------------------------------------------------- Reader

READER_LINES = [
    "###.####",
    "#####.##",
    "##.#####",
    "####.##.",
]
PAGE = [
    "#######.",
    "#.....##",
    "#.###..#",
    "#......#",
    "#.####.#",
    "#......#",
    "#.###..#",
    "########",
]


def reader_front(step=None):
    """Lines of an old text behind glass: grey while idle, lighting amber as a model reads them."""
    c = plate(TEAL_LINE, salt=11)
    window(c)
    for row, line in enumerate(READER_LINES):
        y = 4 + row * 2
        for i, ch in enumerate(line):
            if ch != "#":
                continue
            x = 4 + i
            if step is None:
                color = DIM[2]
            elif row < 2:
                color = AMBER[3]
            elif row == 2 and i < step:
                color = AMBER[3]
            elif row == 2 and i == step:
                color = AMBER[5]
            else:
                color = DIM[2]
            c.px(x, y, color)
    if step is not None:
        c.px(4 + step, 9, AMBER[2])
    return c


def reader_top():
    """A shallow tray to lay a shard in."""
    c = plate(AMBER_LINE, salt=12)
    c.rect(4, 4, 11, 11, K3)
    for i in range(4, 12):
        c.px(i, 4, K4)
        c.px(4, i, K4)
        c.px(i, 11, K1)
        c.px(11, i, K1)
    for y in (6, 8):
        for x in range(6, 10):
            c.px(x, y, K2)
    return c


def reader_side():
    c = plate(NEUTRAL, salt=13)
    engraved_glyph(c, PAGE)
    return c


# ---------------------------------------------------------------- Bench and Fabricator

def bench_top():
    """A breadboard: a hole grid with a few wires and a chip already plugged in."""
    c = Canvas(fill=G2)
    for i in range(16):
        c.px(i, 0, K3)
        c.px(0, i, K3)
        c.px(i, 15, G0)
        c.px(15, i, G0)
    c.rect(2, 2, 13, 13, W1)
    for y in range(3, 13, 2):
        for x in range(3, 13, 2):
            c.px(x, y, W3)
    for x in range(3, 9):
        c.px(x, 5, TEAL[3])
    for y in range(5, 11):
        c.px(8, y, TEAL[3])
    for x in range(4, 13):
        c.px(x, 11, AMBER[3])
    c.px(3, 5, TEAL[5])
    c.px(12, 11, AMBER[5])
    c.rect(10, 3, 12, 7, NEUTRAL[1])
    for y in (4, 6):
        c.px(9, y, K4)
        c.px(13, y, K4)
    c.px(11, 4, TEAL[4])
    return c


def bench_side():
    """Top rows: the table's edge with an accent stripe. Below: the metal its legs and shelf are made of."""
    c = Canvas()
    for x in range(16):
        c.px(x, 0, K0)
        c.px(x, 1, K1)
        c.px(x, 2, TEAL[2])
        c.px(x, 3, K3)
    for y in range(4, 16):
        for x in range(16):
            c.px(x, y, K2 if speckle(x, y, 21) else K1)
    for y in range(4, 16):
        c.px(0, y, K0)
        c.px(1, y, K3)
    return c


WAFER_STAGE = [
    "...oooo...",
    ".ooddddoo.",
    ".oddddddo.",
    "oddddddddo",
    "oddddddddo",
    "oddddddddo",
    "oddddddddo",
    ".oddddddo.",
    ".oodddooo.",
    "...oooo...",
]


def fabricator_front(scan=None):
    """A wafer on its stage behind glass; while it works, a violet line sweeps across it, leaving the pattern."""
    c = plate(AMBER_LINE, salt=14)
    window(c)
    c.stamp(3, 3, WAFER_STAGE, {"o": DIM[2], "d": DIM[1]})
    for y in range(4, 12, 2):
        for x in range(4, 12, 2):
            if c.get(x, y) == DIM[1]:
                c.px(x, y, DIM[3])
    if scan is not None:
        for y in range(3, 13):
            for x in range(3, 13):
                here = c.get(x, y)
                if here not in (DIM[1], DIM[3]):
                    continue
                if y < 3 + scan:
                    c.px(x, y, AMETHYST[1] if here == DIM[1] else AMETHYST[3])
                elif y == 3 + scan:
                    c.px(x, y, AMETHYST[5])
    return c


def fabricator_side():
    """Vents for the lamp's heat."""
    c = plate(NEUTRAL, salt=15)
    for y in (4, 6, 8, 10):
        for x in range(4, 12):
            c.px(x, y, W1)
            c.px(x, y + 1, K0)
    return c


def fabricator_top():
    """The lamp's aperture, a ring of lenses looking down."""
    c = plate(AMBER_LINE, salt=16)
    ring = [
        "..oooo..",
        ".oLLLLo.",
        "oLlAAlLo",
        "oLAaaALo",
        "oLAaaALo",
        "oLlAAlLo",
        ".oLLLLo.",
        "..oooo..",
    ]
    c.stamp(4, 4, ring, {"o": G1, "L": K4, "l": AMETHYST[2], "A": AMETHYST[3], "a": AMETHYST[5]})
    return c


# the forge's display: four lines of text, a character on every other pixel
FORGE_ROWS = (4, 6, 8, 10)
FORGE_GAPS = [(0, 4), (2, 8), (1, 6), (3, 4), (0, 10), (2, 4), (1, 10), (3, 8), (0, 6), (2, 6), (1, 4)]
CHIP_TONES = [TEAL[2], AMBER[2], AMETHYST[2]]


def forge_front(step=None):
    """Lines of text behind glass; while it learns, neighbouring tokens join one pair at a time and the new one flashes."""
    c = plate(TEAL_LINE, salt=17)
    window(c)
    joined = set(FORGE_GAPS[:step or 0])
    last = FORGE_GAPS[step - 1] if step else None
    for row, y in enumerate(FORGE_ROWS):
        tokens, run = [], []
        for x in range(3, 13):
            if x % 2 == 1 or (row, x) in joined:
                run.append(x)
            elif run:
                tokens.append(run)
                run = []
        if run:
            tokens.append(run)
        for token in tokens:
            if step is None:
                tone = DIM[3]
            elif last and last[0] == row and last[1] in token:
                tone = TEAL[5]
            else:
                tone = CHIP_TONES[(token[0] // 2 + row) % len(CHIP_TONES)]
            for x in token:
                c.px(x, y, tone)
    return c


def forge_side():
    """The slot a tokenizer card goes into."""
    c = plate(NEUTRAL, salt=18)
    for x in range(4, 12):
        c.px(x, 7, W0)
        c.px(x, 8, K0)
    for x in range(5, 11):
        c.px(x, 6, AMETHYST[3] if x % 2 else AMETHYST[2])
    for y in (10, 11):
        for x in range(4, 12, 2):
            c.px(x, y, K3)
    return c


def forge_top():
    """The intake: a page of text going down."""
    c = plate(TEAL_LINE, salt=19)
    window(c, 4, 4, 11, 11)
    c.rect(5, 5, 10, 10, K1)
    for y in (6, 8, 10):
        for x in range(6, 10 if y != 10 else 8):
            c.px(x, y, K4)
    return c

# ---------------------------------------------------------------- Forecaster

# a crop by age, from a seedling to ripe wheat, as the Forecaster's screen draws it
CROP = [rgb(h) for h in ["2f5a2a", "3a6b2c", "467c2e", "568c30", "6e9a33", "8ca637", "aeaf3e", "d9b44a"]]
# the front's nine crops: the age each starts at, and the frame of every four it grows on
FIELD = [(0, 0), (3, 2), (5, 1), (2, 3), (6, 0), (1, 2), (4, 3), (7, 1), (2, 2)]
SPROUT = [
    "##....##",
    "###..###",
    ".##..##.",
    "..####..",
    "...##...",
    "...##...",
    "########",
]
HOURGLASS = [
    "########",
    ".#....#.",
    "..#++#..",
    "...++...",
    "...##...",
    "..#..#..",
    ".#++++#.",
    "########",
]


def forecaster_front(step=None):
    """A field from above: nine crops by age, still while it only watches. Running ahead, they grow a look at a time
    and each one lights teal as the model grows it."""
    c = plate(TEAL_LINE, salt=21)
    window(c)
    for i, (start, phase) in enumerate(FIELD):
        x, y = 4 + i % 3 * 3, 4 + i // 3 * 3
        if step is None:
            color = mix(CROP[start], W1, 0.3)
        else:
            grew = (step + phase) % 4 == 0
            color = TEAL[4] if grew else CROP[(start + (step + phase) // 4) % 8]
        c.rect(x, y, x + 1, y + 1, color)
    return c


def forecaster_side():
    c = plate(NEUTRAL, salt=22)
    engraved_glyph(c, SPROUT)
    return c


def forecaster_top():
    """An hourglass: it sees where the field is going."""
    c = plate(AMBER_LINE, salt=23)
    engraved_glyph(c, HOURGLASS)
    return c


WAFER = [
    "................",
    ".....oooooo.....",
    "...ooSsSsSsoo...",
    "..oSsSsSsSsSso..",
    "..osSsSsSsSsSo..",
    ".oSsSsSsSsSsSso.",
    ".osSsSsSsSsSsSo.",
    ".oSsSsSsSsSsSso.",
    ".osSsSsSsSsSsSo.",
    ".oSsSsSsSsSsSso.",
    ".osSsSsSsSsSsSo.",
    "..oSsSsSsSsSso..",
    "..osSsSsSsSsSo..",
    "...ooSsSsSsoo...",
    ".....oooooo.....",
    "................",
]


def wafer():
    """A polished disc ruled into dies, with a flat cut on one edge."""
    c = Canvas()
    c.stamp(0, 0, WAFER, {"o": rgb("1c2430"), "S": rgb("8fa3b8"), "s": rgb("a9bccf")})
    for x in range(5, 11):
        c.px(x, 14, CLEAR)
        c.px(x, 13, rgb("1c2430"))
    for i in range(3, 13, 3):
        for j in range(2, 14):
            if c.get(i, j)[3] and c.get(i, j) != rgb("1c2430"):
                c.px(i, j, rgb("6f8399"))
            if c.get(j, i)[3] and c.get(j, i) != rgb("1c2430"):
                c.px(j, i, rgb("6f8399"))
    for x, y in ((5, 3), (4, 4), (4, 5)):
        c.px(x, y, rgb("e6f3ff"))
    return c


def photomask(pattern):
    """A square of quartz in a frame; a recorded one carries its design etched in chrome."""
    c = Canvas()
    c.rect(2, 2, 13, 13, rgb("2b3540"))
    c.rect(3, 3, 12, 12, (186, 226, 236, 140))
    for i in range(3, 13):
        c.px(i, 3, (230, 248, 252, 190))
        c.px(3, i, (230, 248, 252, 170))
    if pattern:
        rows = [
            "##.###....",
            "#..#.#.##.",
            "####.###..",
            "...#...#.#",
            ".###.#.###",
            ".#...#....",
            ".#.#####..",
            "##.#...#.#",
            "...###.###",
            ".......#..",
        ]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "#":
                    c.px(3 + x, 3 + y, rgb("39424d"))
        c.rect(11, 11, 12, 12, TEAL[3])
    outline(c, INK)
    return c


# ---------------------------------------------------------------- Relay

def relay():
    """One sheet for the relay's three parts, laid out where its model samples them."""
    c = Canvas(fill=G2)
    # lamp sides, 6x5: a lit glass band between a cap and a collar
    for x in range(6):
        c.px(x, 0, G3)
        c.px(x, 4, G0)
        for y in (1, 2, 3):
            c.px(x, y, G1 if x in (0, 5) else TEAL[4] if y == 2 and x in (2, 3) else TEAL[3])
    # lamp top and bottom, 6x6
    c.rect(10, 0, 15, 5, G2)
    for i in range(10, 16):
        c.px(i, 0, G3)
        c.px(i, 5, G0)
    for i in range(6):
        c.px(10, i, G3)
        c.px(15, i, G0)
    c.rect(12, 2, 13, 3, TEAL[4])
    c.px(12, 2, TEAL[5])
    # post sides, 3x9, with a lit trace running up the middle
    for y in range(5, 14):
        c.px(0, y, G3)
        c.px(1, y, TEAL[2] if y % 3 == 1 else G2)
        c.px(2, y, G1)
    c.rect(3, 5, 5, 7, G2)
    c.px(3, 5, G3)
    # base sides, 8x2, and its top, 8x8: a small casing plate
    for x in range(8):
        c.px(x, 14, K1)
        c.px(x, 15, K3)
    c.rect(8, 8, 15, 15, K1)
    for i in range(8, 16):
        c.px(i, 8, K0)
        c.px(8, i, K0)
        c.px(i, 15, K2)
        c.px(15, i, K2)
    for i in range(9, 15):
        for x, y in ((i, 9), (9, i), (i, 14), (14, i)):
            c.px(x, y, NEUTRAL[1])
    return c


# ---------------------------------------------------------------- Agent

GLASS = (196, 238, 232, 72)
GLINT = (255, 255, 255, 150)


def box(c, u, v, w, h, d, paint):
    """Paints one model box's unwrapped faces; paint(c, face, x, y, w, h) draws each."""
    faces = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }
    for face, (x, y, fw, fh) in faces.items():
        paint(c, face, x, y, fw, fh)


def body_face(c, face, x, y, w, h):
    c.rect(x, y, x + w - 1, y + h - 1, BRASS[3])
    for j in range(h):
        for i in range(w):
            if speckle(i, j, 9):
                c.px(x + i, y + j, BRASS[2])
    if face in ("top", "bottom"):
        if face == "bottom":
            c.rect(x, y, x + w - 1, y + h - 1, BRASS[1])
        else:
            c.rect(x + w // 2 - 1, y + h // 2 - 1, x + w // 2, y + h // 2, G1)
        return
    for i in range(w):
        c.px(x + i, y, BRASS[4])
        c.px(x + i, y + h - 1, BRASS[1])
    if face == "front":
        # a gauge on the chest
        c.rect(x + 2, y + 2, x + 5, y + 4, G1)
        c.rect(x + 3, y + 3, x + 4, y + 3, TEAL[3])
        c.px(x + 3, y + 3, TEAL[4])
        for i in (0, w - 1):
            c.px(x + i, y + 1, BRASS[5])
    elif face == "back":
        c.rect(x + 3, y + 2, x + 4, y + 4, BRASS[2])
        c.px(x + 3, y + 3, G0)
        c.px(x + 4, y + 3, G0)
    else:
        c.px(x + w // 2, y + 2, BRASS[5])


def head_face(c, face, x, y, w, h):
    if face == "top":
        c.rect(x, y, x + w - 1, y + h - 1, BRASS[3])
        for i in range(w):
            c.px(x + i, y, BRASS[4])
            c.px(x, y + i, BRASS[4])
        c.px(x + w // 2, y + h // 2, G0)
        return
    if face == "bottom":
        c.rect(x, y, x + w - 1, y + h - 1, BRASS[2])
        c.rect(x + 2, y + 2, x + w - 3, y + h - 3, BRASS[1])
        return
    c.rect(x, y, x + w - 1, y + h - 1, GLASS)
    for i in range(w):
        c.px(x + i, y, BRASS[4])
        c.px(x + i, y + h - 1, BRASS[2])
    for j in range(h):
        c.px(x, y + j, BRASS[3])
        c.px(x + w - 1, y + j, BRASS[3])
    c.px(x + 1, y + 1, GLINT)
    c.px(x + 2, y + 1, GLINT)
    c.px(x + 1, y + 2, GLINT)


def antenna_face(c, face, x, y, w, h):
    if face == "top":
        c.px(x, y, TEAL[5])
    elif face == "bottom":
        c.px(x, y, BRASS[2])
    else:
        c.rect(x, y, x, y + h - 1, BRASS[3])
        c.px(x, y, TEAL[4])


def core_face(c, face, x, y, w, h):
    for j in range(h):
        for i in range(w):
            edge = (i in (0, w - 1)) + (j in (0, h - 1))
            c.px(x + i, y + j, [TEAL[5], TEAL[4], TEAL[3]][edge])


def limb(foot):
    def paint(c, face, x, y, w, h):
        if face == "top":
            c.rect(x, y, x + w - 1, y + h - 1, BRASS[3] if not foot else G1)
            return
        if face == "bottom":
            c.rect(x, y, x + w - 1, y + h - 1, G0 if foot else BRASS[2])
            return
        c.rect(x, y, x + w - 1, y + h - 1, G2)
        c.rect(x, y, x, y + h - 1, G3)
        if foot:
            c.rect(x, y + h - 1, x + w - 1, y + h - 1, BRASS[2])
            if face == "front":
                c.px(x + w // 2, y + 1, BRASS[3])
        else:
            c.rect(x, y, x + w - 1, y, BRASS[3])
            c.rect(x, y + h - 1, x + w - 1, y + h - 1, BRASS[3])
            c.px(x, y + h - 1, BRASS[4])
    return paint


def agent_skin():
    c = Canvas(64, 64)
    box(c, 0, 0, 8, 7, 5, body_face)
    box(c, 0, 14, 7, 7, 7, head_face)
    box(c, 30, 22, 1, 3, 1, antenna_face)
    box(c, 30, 14, 3, 3, 3, core_face)
    box(c, 0, 30, 3, 5, 3, limb(True))
    box(c, 14, 30, 3, 5, 3, limb(True))
    box(c, 28, 30, 2, 6, 2, limb(False))
    box(c, 38, 30, 2, 6, 2, limb(False))
    return c


# ---------------------------------------------------------------- cables

def stream_side(phase=0):
    """A channel in a graphite body, chevrons pointing up the texture, the way the stream flows. The top and bottom
    edges stay open, so a line of blocks reads as one conduit; each phase moves the chevrons up a pixel."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.px(x, y, G1 if speckle(x, y, 21) else G2)
        c.px(0, y, G3)
        c.px(15, y, G0)
        c.px(1, y, TEAL_LINE[0])
        c.px(14, y, TEAL_LINE[0])
        c.px(3, y, G0)
        c.px(12, y, G0)
        c.rect(4, y, 11, y, TEAL[0])
        c.rect(7, y, 8, y, TEAL[1])
    for k in range(2):
        top = k * 8 - phase
        for i, (a, b) in enumerate([(7, 8), (6, 9), (5, 10), (4, 11)]):
            color = TEAL[4] if i == 0 else TEAL[3] if i < 3 else TEAL[2]
            c.px(a, (top + i) % 16, color)
            c.px(b, (top + i) % 16, color)
    return c


def stream_end():
    """The channel seen end on: a ring of light in the graphite."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.px(x, y, G1 if speckle(x, y, 22) else G2)
    for i in range(16):
        c.px(i, 0, G3)
        c.px(0, i, G3)
        c.px(i, 15, G0)
        c.px(15, i, G0)
    ring = [
        "...oooooo...",
        "..oTTTTTTo..",
        ".oTtttttTTo.",
        "oTtLLLLLLtTo",
        "oTtLwwwwLtTo",
        "oTtLwWWwLtTo",
        "oTtLwWWwLtTo",
        "oTtLwwwwLtTo",
        "oTtLLLLLLtTo",
        ".oTttttttTo.",
        "..oTTTTTTo..",
        "...oooooo...",
    ]
    c.stamp(2, 2, ring, {"o": G0, "T": TEAL[1], "t": TEAL[2], "L": TEAL[3], "w": TEAL[4], "W": TEAL[5]})
    return c


DYES = {
    "white": "e9ecef", "orange": "f08a24", "magenta": "c94fc0", "light_blue": "4cb6e0",
    "yellow": "f5cf3a", "lime": "86cc2b", "pink": "f08fae", "gray": "5d666c",
    "light_gray": "a3a8ad", "cyan": "1aa3a3", "purple": "8c3dbd", "blue": "3f52b8",
    "brown": "8a5a34", "green": "5c8a1c", "red": "c03a30", "black": "2a2c33",
}


def cable(base, light, dark, signal):
    """A plus of two 4 px bands; the cable model samples the arms from the bands and its joint from the middle."""
    c = Canvas()
    for i in range(16):
        for k, color in ((6, dark), (7, light), (8, base), (9, dark)):
            c.px(i, k, color)
            c.px(k, i, color)
    c.rect(6, 6, 9, 9, dark)
    c.rect(7, 7, 8, 8, signal)
    c.px(7, 7, mix(signal, (255, 255, 255, 255), 0.45))
    return c


def cable_colors(hex_):
    base = rgb(hex_)
    return base, mix(base, (255, 255, 255, 255), 0.35), mix(base, (16, 18, 24, 255), 0.5)


# ---------------------------------------------------------------- items

WEIGHT_CRYSTAL = [
    "................",
    ".......oo.......",
    "......oTAo......",
    ".....oTtAAo.....",
    ".....oTtAaAo....",
    "....oTTtAaAo....",
    "....oTttAaAAo...",
    "...oTTttAaaAo...",
    "...oTtttAaaAo...",
    "...oTttAaaAAo...",
    "....oTtAaaAo....",
    "....oTtAaAAo....",
    ".....otAaAo.....",
    "......oAAo......",
    ".......oo.......",
    "................",
]


def weight_crystal():
    c = Canvas()
    c.stamp(0, 0, WEIGHT_CRYSTAL, {"o": rgb("1c2a33"), "T": TEAL[4], "t": TEAL[3], "A": AMBER[3], "a": AMBER[2]})
    c.px(6, 4, TEAL[5])
    c.px(5, 6, TEAL[5])
    c.px(9, 3, AMBER[5])
    return c


def outline(c, color):
    """Rings every drawn shape with a 1 px border, the way vanilla items are drawn."""
    filled = {(x, y) for y in range(16) for x in range(16) if c.get(x, y)[3]}
    for x, y in filled:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in filled:
                c.px(x + dx, y + dy, color)


def probe():
    """A pen-shaped probe laid along the diagonal: glowing tip, needle, light body between two teal rings, dark grip."""
    c = Canvas()
    needle = (None, K0, K3)
    ring = (TEAL[4], TEAL[3], TEAL[2])
    body = (K0, K1, K3)
    grip = (G3, G2, G1)
    rows = [(TEAL[4], TEAL[5], None), (None, TEAL[4], TEAL[3]), needle, needle, needle,
            ring, body, body, body, ring, grip, grip, grip, grip, (None, G1, None)]
    for y, row in enumerate(rows):
        for d, color in zip((-1, 0, 1), row):
            if color is not None:
                c.px(15 - y + d, y, color)
    outline(c, INK)
    return c


PAPER = [
    "..oooooooooo....",
    "..oPPPPPPPPpo...",
    "..oPPPPPPPPppo..",
    "..oPPPPPPPPpppo.",
    "..oPPPPPPPPPPPo.",
    "..oPPPPPPPPPPPo.",
    "..oPPPPPPPPPPPo.",
    "..oPPPPPPPPPPPo.",
    "..oPPPPPPPPPPPo.",
    "..oPPPPPPPPPPPo.",
    "..oPPPPPPPPPPPo.",
    "..oPPPPPPPPPPPo.",
    "..oPPPPPPPPPPPo.",
    "..oPPPPPPPPPPPo.",
    "..oSSSSSSSSSSSo.",
    "..ooooooooooooo.",
]

# a schematic is a machine's plan: a white line drawing on blueprint, sealed in the color of what it unlocks
SCHEMATICS = {
    "schematic_relays": ("3cd6c6", [
        "#.......#",
        ".#..#..#.",
        "...###...",
        "....#....",
        "...#.#...",
        "..#...#..",
    ]),
}


def schematic(accent, rows):
    c = Canvas()
    c.stamp(0, 0, PAPER, {"o": rgb("0c1a2a"), "P": PRINT[2], "p": PRINT[4], "S": PRINT[1]})
    for y in range(4, 14, 3):
        for x in range(4, 14, 3):
            c.px(x, y, PRINT[3])
    for x in range(4, 10):
        c.px(x, 3, PRINT[4])
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                c.px(4 + x, 6 + y, PRINT[5])
    a = rgb(accent)
    c.rect(11, 12, 12, 13, a)
    c.px(11, 12, mix(a, (255, 255, 255, 255), 0.5))
    c.px(12, 13, mix(a, (0, 0, 0, 255), 0.3))
    return c


# one color per benchmark, on its dataset's header
BENCHMARK_COLORS = {"field": "84e08a", "growth": "d9b44a", "storage": "ffb547", "errand": "6cb8ff", "pattern": "b48cff",
                    "orders": "3cd6c6"}
# what each dataset practices, punched into the card
DATASET_HOLES = {
    "field": [
        "#..#..#.",
        ".#.#.#..",
        "..###...",
        "...#....",
        "...#....",
        "........",
        "########",
    ],
    "growth": [
        "......##",
        ".....#..",
        "....#...",
        "..##....",
        ".#......",
        "#.......",
        "########",
    ],
    "storage": [
        "########",
        "#..#...#",
        "#..#...#",
        "########",
        "#...#..#",
        "#...#..#",
        "########",
    ],
    "errand": [
        ".....#..",
        "#######.",
        ".....#..",
        "........",
        "..#.....",
        ".#######",
        "..#.....",
    ],
    "pattern": [
        "##..##..",
        "##..##..",
        "........",
        "#.#.#.#.",
        "........",
        "##..##..",
        "##..##..",
    ],
    "orders": [
        "#######.",
        "#.....#.",
        "#.###.#.",
        "#.....#.",
        "###.###.",
        "..##....",
        "..#.....",
    ],
}
CARD = [
    "................",
    "..HHHHHHHHHh....",
    "..HHHHHHHHHHh...",
    "..hhhhhhhhhhhh..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..SSSSSSSSSSSS..",
    "................",
]


def dataset(kind):
    """A punch card: the header in its benchmark's color, the practice punched below."""
    c = Canvas()
    a = rgb(BENCHMARK_COLORS[kind])
    c.stamp(0, 0, CARD, {"H": a, "h": mix(a, (0, 0, 0, 255), 0.35), "P": K0, "S": K3})
    for y in range(4, 14):
        for x in range(2, 14):
            if speckle(x, y, 3):
                c.px(x, y, K1)
    holes = glyph_cells(DATASET_HOLES[kind])
    for (x, y) in holes:
        c.px(4 + x, 5 + y, rgb("2a313b"))
        if (x, y + 1) not in holes:
            c.px(4 + x, 6 + y, K2)
    outline(c, INK)
    return c


TOKEN_CARD = [
    "................",
    "..VVVVVVVVVVVV..",
    "..vvvvvvvvvvvv..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..SSSSSSSSSSSS..",
    "................",
]
# what each line of a written tokenizer shows: runs of the same letter are one token
TOKEN_LINES = ["aaab.cccc.", "bb.aaaa.cc", "cccc.ab.aa", "aa.bbb.c.."]


def tokenizer(written):
    """A violet-headed card. Blank, its lines are separate characters; written, they are tokens of every length."""
    c = Canvas()
    c.stamp(0, 0, TOKEN_CARD, {"V": AMETHYST[3], "v": AMETHYST[2], "P": K0, "S": K3})
    tones = {"a": TEAL[2], "b": AMBER[2], "c": AMETHYST[2]}
    for row, y in enumerate((5, 7, 9, 11)):
        for i, ch in enumerate(TOKEN_LINES[row]):
            x = 3 + i
            if written and ch in tones:
                c.px(x, y, tones[ch])
            elif not written and i % 2 == 0 and i < 9:
                c.px(x, y, K4)
    outline(c, INK)
    return c

FIELD_LOG = [
    "................",
    "......kKKk......",
    "...bbbkKKkbbb...",
    "...bPPPPPPPPb...",
    "...bPPPPPPPPb...",
    "...bPPPPPPPPb...",
    "...bPPPPPPPPb...",
    "...bPPPPPPPPb...",
    "...bPPPPPPPPb...",
    "...bPPPPPPPPb...",
    "...bPPPPPPPPb...",
    "...bPPPPPPPPb...",
    "...bPPPPPPPPb...",
    "...bbbbbbbbbb...",
    "...dddddddddd...",
    "................",
]


def field_log():
    """A clipboard of crops' lines: each a row of looks, growing from green to ripe gold."""
    c = Canvas()
    c.stamp(0, 0, FIELD_LOG, {"k": K3, "K": K1, "b": rgb("9b7449"), "d": rgb("6e5131"), "P": K0})
    for row, (y, ages) in enumerate(((4, [0, 1, 1, 3, 5, 7]), (6, [0, 0, 2, 4, 4]), (8, [1, 2, 5, 6, 7, 7]),
                                     (10, [0, 3, 3, 6]), (12, [2, 4, 7]))):
        for i, age in enumerate(ages):
            c.px(5 + i, y, CROP[age])
    outline(c, INK)
    return c


CHECKPOINT = [
    "................",
    "......kKKk......",
    ".....kKKKKk.....",
    "....gGGGGGGg....",
    "....gwwwwwhg....",
    "....gwwTAwwg....",
    "....gwTtaAwg....",
    "....gTttaaAg....",
    "....gwTtaAwg....",
    "....gwwTAwwg....",
    "....gwwwwwwg....",
    "....gGGGGGGg....",
    ".....kKKKKk.....",
    "......kKKk......",
    "................",
    "................",
]


def checkpoint():
    """A vial for a model: a weight crystal sealed in glass between two steel caps."""
    c = Canvas()
    c.stamp(0, 0, CHECKPOINT, {"k": K3, "K": K1, "g": G1, "G": G3, "w": rgb("cfe6ea"), "h": K0,
                               "T": TEAL[3], "t": TEAL[2], "A": AMBER[3], "a": AMBER[2]})
    c.px(8, 7, TEAL[5])
    outline(c, INK)
    return c


DRIVE = [
    "................",
    "................",
    "...kKKKKKKKKk...",
    "...GGGGGGGGGG...",
    "...GPPPPPPPPG...",
    "...GPllllllPG...",
    "...GPPPPPPPPG...",
    "...GPlllllPPG...",
    "...GPPPPPPPPG...",
    "...GPllllPPPG...",
    "...GPPPPPPPPG...",
    "...GGGGGGGGtG...",
    "...gGGGGGGGGg...",
    "....y.y.y.y.....",
    "................",
    "................",
]


def archive_drive():
    """A cartridge for recovered text: a paper label of lines, a light that says it is written."""
    c = Canvas()
    c.stamp(0, 0, DRIVE, {"k": K3, "K": K1, "G": G2, "g": G1, "P": K0, "l": K4, "t": TEAL[4], "y": AMBER[3]})
    outline(c, INK)
    return c


SHARD = [
    "................",
    "................",
    "..LLLLLLLLLL....",
    "..LSSSSSSSSSL...",
    "..LSggggSggSS...",
    "..LSSSSSSSSSSe..",
    "..LSgggSgggSSe..",
    "..LSSSSSSSSSce..",
    "..LSggSgggScSe..",
    "..LSSSSSSSScee..",
    "..LSgggSSSce....",
    "..LSSSSSSee.....",
    "..LSSSSSSe......",
    "..eeeeeeee......",
    "................",
    "................",
]


def archive_shard():
    """A broken tablet with a few lines of writing left on it."""
    c = Canvas()
    c.stamp(0, 0, SHARD, {"L": STONE[5], "S": STONE[4], "s": STONE[3], "e": STONE[2], "g": STONE[2], "c": STONE[1]})
    outline(c, INK)
    return c


def salvaged_model():
    """A weight crystal gone dark, cracked through, still warm inside."""
    c = Canvas()
    c.stamp(0, 0, WEIGHT_CRYSTAL, {"o": rgb("12171c"), "T": rgb("51606a"), "t": rgb("3a464f"),
                                   "A": rgb("5e4c3a"), "a": rgb("45382b")})
    for x, y in ((9, 2), (10, 3), (10, 4), (11, 5)):
        c.px(x, y, CLEAR)
    for x, y in ((8, 2), (9, 3), (9, 4), (10, 5), (11, 6)):
        c.px(x, y, rgb("12171c"))
    crack = [(8, 4), (7, 5), (7, 6), (8, 7), (8, 8), (7, 9), (6, 10), (7, 11), (7, 12)]
    for i, (x, y) in enumerate(crack):
        c.px(x, y, AMBER[4] if i in (3, 4) else AMBER[3])
    for x, y in ((9, 8), (6, 6), (8, 10)):
        c.px(x, y, AMBER[1])
    return c


AGENT = [
    "................",
    ".......tt.......",
    ".......bb.......",
    "....BBBBBBBB....",
    "....BhgggggB....",
    "....BgggcggB....",
    "....BggcCcgB....",
    "....BBBBBBBB....",
    "..aDDDDDDDDDDa..",
    "..aDdddddddDDa..",
    "..aDddDvvDdDDa..",
    "..sDddDvvDdDDs..",
    "....dddddddd....",
    ".....LL..LL.....",
    ".....LL..LL.....",
    ".....ff..ff.....",
]


def agent_item():
    """Brass body, graphite limbs, and a glass lantern for a head with a model glowing in it."""
    c = Canvas()
    c.stamp(0, 0, AGENT, {"t": TEAL[4], "b": BRASS[3], "B": BRASS[3], "h": rgb("eafaf7"), "g": rgb("a9d9d2"),
                               "c": TEAL[3], "C": TEAL[5], "D": BRASS[3], "d": BRASS[2], "v": G1, "a": G2,
                               "s": BRASS[4], "L": G2, "f": BRASS[2]})
    for x in range(4, 12):
        c.px(x, 3, BRASS[4])
        c.px(x, 8, BRASS[4])
    c.px(8, 10, TEAL[4])
    outline(c, INK)
    return c


def survey_rod():
    """A rod with an amethyst at its tip, to mark where things go."""
    c = Canvas()
    for t in range(10):
        x, y = 2 + t, 13 - t
        c.px(x, y, BRASS[2] if t in (0, 1) else rgb("9b7449"))
        c.px(x + 1, y, BRASS[1] if t in (0, 1) else rgb("6e5131"))
    c.px(11, 4, BRASS[4])
    c.px(12, 4, BRASS[3])
    c.px(11, 5, BRASS[3])
    for (x, y), tone in {(12, 1): 4, (13, 1): 5, (11, 2): 3, (12, 2): 4, (13, 2): 3, (14, 2): 2,
                         (12, 3): 3, (13, 3): 2, (11, 3): 2, (13, 0): 4}.items():
        c.px(x, y, AMETHYST[tone])
    outline(c, INK)
    return c


RELAY_CARD = [
    "................",
    "................",
    "................",
    "..GGGGGGGGGg....",
    "..GgggggggggG...",
    "..GgyyyygggtgG..",
    "..GgyYYyggtgtG..",
    "..GgyyyygLgtgt..",
    "..GgyYYyggtgtG..",
    "..GgyyyygggtgG..",
    "..GggggggggggG..",
    "..GgggggggggLG..",
    "..GGGGGGGGGGGG..",
    "................",
    "................",
    "................",
]


def relay_card():
    """A card that knows which Model Server to ask: a chip, and an antenna drawn on in teal."""
    c = Canvas()
    c.stamp(0, 0, RELAY_CARD, {"G": G3, "g": G1, "y": AMBER[3], "Y": AMBER[2], "t": TEAL[3], "L": TEAL[5]})
    outline(c, INK)
    return c


TERMINAL = [
    "................",
    "............b...",
    "...GGGGGGGGGb...",
    "...GWWWWWWWGG...",
    "...GWtttWtWGG...",
    "...GWWWWWWWGG...",
    "...GWttWtttGG...",
    "...GWWWWWWWGG...",
    "...GWtcWWWWGG...",
    "...GWWWWWWWGG...",
    "...GGGGGGGGGG...",
    "...GkGkGkGlGG...",
    "...GGGGGGGGGG...",
    "...GkGkGkGkGG...",
    "...gGGGGGGGGg...",
    "................",
]


def terminal():
    """A handheld screen for asking a served model from anywhere its relays reach."""
    c = Canvas()
    c.stamp(0, 0, TERMINAL, {"G": G2, "g": G1, "W": W1, "t": TEAL[3], "c": TEAL[5], "k": K2, "l": TEAL[4],
                             "b": BRASS[3]})
    outline(c, INK)
    return c


GUIDE = [
    "..oooooooooooo..",
    ".oCCCCCCCCCCCCo.",
    ".oCLLLLLLLLLLCo.",
    ".oCL........LCo.",
    ".oCL.TTTTTT.LCo.",
    ".oCL.T....T.LCo.",
    ".oCL.T.TT.T.LCo.",
    ".oCL.T.TT.T.LCo.",
    ".oCL.T....T.LCo.",
    ".oCL.TTTTTT.LCo.",
    ".oCL........LCo.",
    ".oCLLLLLLLLLLCo.",
    ".oCCCCCCCCCCCCo.",
    ".oPPPPPPPPPPPPo.",
    ".oSSSSSSSSSSSSo.",
    "..oooooooooooo..",
]


def guide():
    c = Canvas()
    c.stamp(0, 0, GUIDE, {"o": rgb("1f252d"), "C": G2, "L": TEAL[1], ".": G1, "T": TEAL[3], "P": K0, "S": K3})
    c.px(8, 6, TEAL[5])
    return c


# ---------------------------------------------------------------- logo

LETTERS = {
    "R": ["#######.", "########", "##....##", "##....##", "##....##", "########", "#######.", "##..##..", "##...##.",
          "##....##", "##....##", "##....##"],
    "E": ["#######", "#######", "##.....", "##.....", "##.....", "######.", "######.", "##.....", "##.....", "##.....",
          "#######", "#######"],
    "S": [".#######", "########", "##......", "##......", "##......", "#######.", ".#######", "......##", "......##",
          "......##", "########", "#######."],
    "I": ["##"] * 12,
    "D": ["#######.", "########"] + ["##....##"] * 8 + ["########", "#######."],
    "U": ["##....##"] * 10 + ["########", ".######."],
    "A": [".######.", "########", "##....##", "##....##", "##....##", "########", "########"] + ["##....##"] * 5,
    "L": ["##....."] * 10 + ["#######", "#######"],
}
AROUND = [(dx, dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1, 2)]

# blocks as the inventory draws them: turned 45 degrees and looked down on at 30
PITCH, YAW = math.radians(30), math.radians(45)
# each face: where its texture's corners (0, 0), (1, 0) and (0, 1) sit on the unit cube, and how brightly it is lit
FACES = {
    "top": (((-.5, .5, -.5), (.5, .5, -.5), (-.5, .5, .5)), 1.0),
    "left": (((-.5, .5, -.5), (-.5, .5, .5), (-.5, -.5, -.5)), 0.8),
    "right": (((-.5, .5, .5), (.5, .5, .5), (-.5, -.5, .5)), 0.6),
}
# the corners on the block's outline, clockwise
HULL = [(-.5, .5, -.5), (.5, .5, -.5), (.5, .5, .5), (.5, -.5, .5), (-.5, -.5, .5), (-.5, -.5, -.5)]


def project(x, y, z):
    across = x * math.cos(YAW) + z * math.sin(YAW)
    toward = z * math.cos(YAW) - x * math.sin(YAW)
    return across, toward * math.sin(PITCH) - y * math.cos(PITCH)


def block(faces, height, glow=(), ss=4):
    """A block `height` pixels high with top, left and right faces, outlined like the name; its glow stays unshaded."""
    hull = [project(*p) for p in HULL]
    span = max(y for _, y in hull) - min(y for _, y in hull)
    k = height * ss / (span + 1.8 / 16)
    r = k * 0.9 / 16
    x0, y0 = min(x for x, _ in hull) * k - r, min(y for _, y in hull) * k - r
    im = Image.new("RGBA", (round(-2 * x0), height * ss), CLEAR)
    outline = [(x * k - x0, y * k - y0) for x, y in hull]
    draw = ImageDraw.Draw(im)
    draw.polygon(outline, fill=NEUTRAL[0])
    draw.line(outline + outline[:2], fill=NEUTRAL[0], width=round(2 * r), joint="curve")
    for name, (corners, light) in FACES.items():
        tex = faces[name].copy()
        px = tex.load()
        for y in range(tex.height):
            for x in range(tex.width):
                if px[x, y] not in glow:
                    px[x, y] = tuple(round(v * light) for v in px[x, y][:3]) + (px[x, y][3],)
        (ox, oy), (ux, uy), (vx, vy) = ((x * k - x0, y * k - y0) for x, y in (project(*p) for p in corners))
        # texel (u, v) lands on o + u (a, c) + v (b, d); the transform wants the way back
        a, b, c, d = ((ux - ox) / tex.width, (vx - ox) / tex.width, (uy - oy) / tex.width, (vy - oy) / tex.width)
        det = a * d - b * c
        back = (d / det, -b / det, (b * oy - d * ox) / det, -c / det, a / det, (c * ox - a * oy) / det)
        face = tex.transform(im.size, Image.AFFINE, back, Image.NEAREST)
        inside = Image.new("L", tex.size, 255).transform(im.size, Image.AFFINE, back, Image.NEAREST)
        face.putalpha(ImageChops.multiply(face.getchannel("A"), inside))
        im.alpha_composite(face)
    return im.resize((round(im.width / ss), height), Image.LANCZOS)


def junction(height):
    """The mod's mark: the Residual Junction, its glyph lit."""
    front, side = operator_faces("add")
    return block({"top": side.im, "left": side.im, "right": front.im}, height, set(TEAL))


def logo(scale=8):
    """The Junction beside the name, the letters outlined and lit from above, drawn `scale` times over."""
    cells, x = set(), 0
    for ch in "RESIDUAL":
        rows = LETTERS[ch]
        cells |= {(x + dx, y) for y, row in enumerate(rows) for dx, c in enumerate(row) if c == "#"}
        x += len(rows[0]) + 2
    c = Canvas(x, 18)
    x0, y0 = 1, 2
    ramp = [K0] * 3 + [K1] * 4 + [K2] * 3 + [K3] * 2
    for (x, y) in cells:
        for dx, dy in AROUND:
            if (x + dx, y + dy) not in cells:
                c.px(x0 + x + dx, y0 + y + dy, NEUTRAL[0])
    for (x, y) in cells:
        if (x, y + 1) not in cells:
            c.px(x0 + x, y0 + y + 1, K4)
        c.px(x0 + x, y0 + y, ramp[y])
    name = c.im.resize((c.im.width * scale, c.im.height * scale), Image.NEAREST)
    mark = junction(name.height)
    im = Image.new("RGBA", (mark.width + 3 * scale + name.width, name.height), CLEAR)
    im.alpha_composite(mark)
    im.alpha_composite(name, (mark.width + 3 * scale, 0))
    return im


def icon(size):
    """The Junction on a square, clear of a round crop."""
    im = Image.new("RGBA", (size, size), CLEAR)
    mark = junction(round(size * 0.89))
    im.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2))
    return im


# ---------------------------------------------------------------- output

def build():
    for op in GLYPHS:
        front, side = operator_faces(op)
        front.save(f"block/{op}_front.png")
        side.save(f"block/{op}_side.png")
        if ARITY.get(op, 1) == 0:
            blank_back(op).save(f"block/{op}_back.png")
    machine_back().save("block/machine_back.png")

    core_front().save("block/model_core_front.png")
    core_side().save("block/model_core_side.png")
    animated([core_front(i) for i in range(2)], "block/model_core_front_on.png", 4)
    animated([core_side(i) for i in range(5)], "block/model_core_side_on.png", 3)
    SAVED["block/model_core_front_on.png"] = core_front(0).im
    SAVED["block/model_core_side_on.png"] = core_side(0).im

    accelerator().save("block/accelerator.png")
    accelerator_hot().save("block/accelerator_hot.png")
    casing().save("block/tensor_casing.png")

    server_front().save("block/model_server_front.png")
    server_front(crystal=True).save("block/model_server_front_loaded.png")
    animated([server_front(True, i) for i in range(4)], "block/model_server_front_on.png", 3)
    SAVED["block/model_server_front_on.png"] = server_front(True, 0).im
    server_side().save("block/model_server_side.png")
    server_top().save("block/model_server_top.png")
    reader_front().save("block/reader_front.png")
    animated([reader_front(i) for i in range(8)], "block/reader_front_on.png", 4)
    SAVED["block/reader_front_on.png"] = reader_front(5).im
    reader_top().save("block/reader_top.png")
    reader_side().save("block/reader_side.png")
    relay().save("block/relay.png")
    bench_top().save("block/bench_top.png")
    bench_side().save("block/bench_side.png")
    fabricator_front().save("block/fabricator_front.png")
    animated([fabricator_front(i) for i in range(11)], "block/fabricator_front_on.png", 2)
    SAVED["block/fabricator_front_on.png"] = fabricator_front(6).im
    fabricator_side().save("block/fabricator_side.png")
    fabricator_top().save("block/fabricator_top.png")
    forge_front().save("block/bpe_forge_front.png")
    animated([forge_front(i) for i in range(len(FORGE_GAPS) + 1)], "block/bpe_forge_front_on.png", 3)
    SAVED["block/bpe_forge_front_on.png"] = forge_front(5).im
    forge_side().save("block/bpe_forge_side.png")
    forge_top().save("block/bpe_forge_top.png")
    forecaster_front().save("block/forecaster_front.png")
    animated([forecaster_front(i) for i in range(32)], "block/forecaster_front_on.png", 3)
    SAVED["block/forecaster_front_on.png"] = forecaster_front(5).im
    forecaster_side().save("block/forecaster_side.png")
    forecaster_top().save("block/forecaster_top.png")
    animated([stream_side(i) for i in range(8)], "block/stream_side.png", 2)
    SAVED["block/stream_side.png"] = stream_side(0).im
    stream_end().save("block/stream_end.png")

    plain = cable(K2, K0, NEUTRAL[1], TEAL[4])
    for i in range(16):
        plain.px(i, 7, TEAL[3])
        plain.px(7, i, TEAL[3])
    plain.rect(7, 7, 8, 8, TEAL[4])
    plain.save("block/cable.png")
    for dye, hex_ in DYES.items():
        base, light, dark = cable_colors(hex_)
        cable(base, light, dark, mix(base, (255, 255, 255, 255), 0.6)).save(f"block/{dye}_cable.png")

    weight_crystal().save("item/weight_crystal.png")
    probe().save("item/tensor_probe.png")
    checkpoint().save("item/checkpoint.png")
    archive_drive().save("item/archive_drive.png")
    tokenizer(False).save("item/tokenizer.png")
    tokenizer(True).save("item/tokenizer_written.png")
    field_log().save("item/field_log.png")
    archive_shard().save("item/archive_shard.png")
    salvaged_model().save("item/salvaged_model.png")
    for kind in BENCHMARK_COLORS:
        dataset(kind).save(f"item/{kind}_dataset.png")
    for name, (accent, rows) in SCHEMATICS.items():
        schematic(accent, rows).save(f"item/{name}.png")
    agent_item().save("item/agent.png")
    survey_rod().save("item/survey_rod.png")
    relay_card().save("item/relay_card.png")
    terminal().save("item/terminal.png")
    wafer().save("item/wafer.png")
    photomask(False).save("item/photomask.png")
    photomask(True).save("item/photomask_recorded.png")
    guide().save("item/guide.png")
    agent_skin().save("entity/agent.png")
    # the mod list scales it down to 50 px high
    logo().save(ROOT / "src/main/resources/residual_logo.png")
    # beside the website's title, 32 px high
    junction(128).save(ROOT / "src/website/logo.png")
    # the stores and the Discord server
    icon(1024).save(ROOT / "docs/images/icon.png")


def preview(path):
    scale, pad = 6, 6
    names = [n for n in SAVED if SAVED[n].width == 16]
    large = [SAVED[n] for n in SAVED if SAVED[n].width > 16 and SAVED[n].width <= 64]
    cols = 9
    cell = 16 * scale + pad
    rows = (len(names) + cols - 1) // cols
    extra = max((im.height * 4 + pad for im in large), default=0)
    sheet = Image.new("RGBA", (cols * cell + pad, rows * cell + pad + extra), (58, 64, 74, 255))
    for i, name in enumerate(names):
        im = SAVED[name].crop((0, 0, 16, 16))
        big = im.resize((16 * scale, 16 * scale), Image.NEAREST)
        sheet.paste(big, (pad + (i % cols) * cell, pad + (i // cols) * cell), big)
    x = pad
    for im in large:
        big = im.resize((im.width * 4, im.height * 4), Image.NEAREST)
        sheet.paste(big, (x, rows * cell + pad), big)
        x += big.width + pad
    sheet.save(path)


if __name__ == "__main__":
    build()
    if len(sys.argv) > 1:
        preview(sys.argv[1])
