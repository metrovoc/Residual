"""Draws every texture of the mod, pixel by pixel.

    uv run --with pillow python tools/art.py               # write the textures
    uv run --with pillow python tools/art.py preview.png   # and a preview sheet
"""
import json
import sys
from pathlib import Path

from PIL import Image

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
# graphite for the Model Core
G0, G1, G2, G3 = map(rgb, ["1d2229", "252b33", "2e353f", "3a434f"])
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


# ---------------------------------------------------------------- cables

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
    outline(c, rgb("161b21"))
    return c


PAPER = [
    "..oooooooooo....",
    "..oPPPPPPPPpo...",
    "..oPPPPPPPPpPo..",
    "..oPPPPPPPPppPo.",
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

INSIGHTS = {
    "insight_counting": ("3cd6c6", [
        "......#..",
        "......#..",
        "..#...#..",
        "..#.#.#..",
        "#.#.#.#.#",
        "#.#.#.#.#",
    ]),
    "insight_context": ("ffb547", [
        "##.##.##.",
        "##.##.##.",
        ".........",
        "......#..",
        "########.",
        "......#..",
    ]),
    "insight_attention": ("b48cff", [
        "..#####..",
        ".#.....#.",
        "#...#...#",
        "#..###..#",
        ".#.....#.",
        "..#####..",
    ]),
    "insight_position": ("84e08a", [
        ".##......",
        "#..#....#",
        "....#..#.",
        ".....##..",
        ".........",
        "#.#.#.#.#",
    ]),
    "insight_circuits": ("ff7a6b", [
        "#.#....#.",
        "#.#....#.",
        ".........",
        ".#######.",
        "#.......#",
        "#.......#",
    ]),
    "insight_language": ("6cb8ff", [
        "#######..",
        ".........",
        "#####.##.",
        ".........",
        "##.######",
        ".........",
    ]),
}


def insight(accent, rows):
    c = Canvas()
    paper, shade, fold = rgb("eef1f4"), rgb("cfd5dc"), rgb("b8c0ca")
    c.stamp(0, 0, PAPER, {"o": rgb("2a313b"), "P": paper, "p": fold, "S": shade})
    a = rgb(accent)
    # title rule
    for x in range(4, 10):
        c.px(x, 3, rgb("8f97a4"))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                c.px(4 + x, 6 + y, mix(a, (20, 24, 30, 255), 0.15))
    # seal
    c.rect(11, 11, 12, 12, a)
    c.px(11, 11, mix(a, (255, 255, 255, 255), 0.5))
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


def logo():
    """The Residual Junction's face beside the name, both outlined, the letters lit from above."""
    cells, x = set(), 0
    for ch in "RESIDUAL":
        rows = LETTERS[ch]
        cells |= {(x + dx, y) for y, row in enumerate(rows) for dx, c in enumerate(row) if c == "#"}
        x += len(rows[0]) + 2
    c = Canvas(21 + x, 18)
    face = operator_faces("add")[0]
    c.rect(0, 0, 17, 17, NEUTRAL[0])
    c.im.paste(face.im, (1, 1))
    x0, y0 = 22, 2
    ramp = [K0] * 3 + [K1] * 4 + [K2] * 3 + [K3] * 2
    for (x, y) in cells:
        for dx, dy in AROUND:
            if (x + dx, y + dy) not in cells:
                c.px(x0 + x + dx, y0 + y + dy, NEUTRAL[0])
    for (x, y) in cells:
        if (x, y + 1) not in cells:
            c.px(x0 + x, y0 + y + 1, K4)
        c.px(x0 + x, y0 + y, ramp[y])
    return c


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
    casing().save("block/tensor_casing.png")

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
    for name, (accent, rows) in INSIGHTS.items():
        insight(accent, rows).save(f"item/{name}.png")
    guide().save("item/guide.png")
    # the mod list scales it down to 50 px high
    mark = logo().im
    mark.resize((mark.width * 8, mark.height * 8), Image.NEAREST).save(ROOT / "src/main/resources/residual_logo.png")


def preview(path):
    scale, pad = 6, 6
    names = list(SAVED)
    cols = 9
    cell = 16 * scale + pad
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell + pad, rows * cell + pad), (58, 64, 74, 255))
    for i, name in enumerate(names):
        im = SAVED[name]
        if im.height > 16:
            im = im.crop((0, 0, 16, 16))
        sheet.paste(im.resize((16 * scale, 16 * scale), Image.NEAREST), (pad + (i % cols) * cell, pad + (i // cols) * cell),
                    im.resize((16 * scale, 16 * scale), Image.NEAREST))
    sheet.save(path)


if __name__ == "__main__":
    build()
    if len(sys.argv) > 1:
        preview(sys.argv[1])
