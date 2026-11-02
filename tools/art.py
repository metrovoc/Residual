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


def ok(L, C, H):
    """A color from OKLCH (lightness 0..1, chroma, hue in degrees), so ramps step evenly in lightness whatever the hue."""
    a, b = C * math.cos(math.radians(H)), C * math.sin(math.radians(H))
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
    linear = (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
              -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
              -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)

    def encode(v):
        v = min(1, max(0, v))
        return round(255 * (12.92 * v if v <= 0.0031308 else 1.055 * v ** (1 / 2.4) - 0.055))

    return tuple(encode(v) for v in linear) + (255,)


def toward(h, target, amount):
    d = (target - h + 180) % 360 - 180
    return (h + max(-amount, min(amount, d))) % 360


def hue_ramp(h, lights=(.33, .43, .54, .66, .80, .93), chromas=(.06, .09, .12, .135, .115, .065)):
    """Six shades of a hue, dark to light: the shadows turn toward blue-violet and the lights toward yellow."""
    out = []
    for i, (L, C) in enumerate(zip(lights, chromas)):
        t = 2 * i / (len(lights) - 1) - 1
        out.append(ok(L, C, toward(h, 275, -10 * t) if t < 0 else toward(h, 95, 10 * t)))
    return out


# What the measured machine faces of AE2 and Mekanism share (.metrovoc/research/textures.md): frames of light, faintly
# tinted steel; a slate groove, never black; the darkest tones only in holes; each surface in two close tones; color
# that says what a block does or that it is working.
STEEL = [ok(L, C, 262 + 10 * i / 7) for i, (L, C) in
         enumerate(zip((.965, .925, .885, .845, .79, .73, .665, .60), (.004, .007, .011, .016, .022, .028, .033, .036)))]
SLATE = [ok(L, .045, 278) for L in (.27, .33, .385, .435, .50)]
# a hue per kind of work: the stream and its arithmetic, learned weights, attention, the shape of data, functions,
# heat and making
TEAL, AZURE, AMETHYST, AMBER, GREEN, CORAL = (hue_ramp(h) for h in (190, 250, 302, 72, 148, 38))
# light casing
K0, K1, K2, K3, K4 = STEEL[0], STEEL[1], STEEL[2], STEEL[4], STEEL[6]
NEUTRAL = (SLATE[1], SLATE[3])
TEAL_LINE = (TEAL[2], TEAL[3])
AMBER_LINE = (AMBER[2], AMBER[3])
# screens and holes
W0, W1, W2, W3 = SLATE[0], SLATE[1], SLATE[2], SLATE[3]
DIM = [SLATE[1], SLATE[2], SLATE[3], SLATE[4]]
# graphite for the Model Core and the Model Server
G0, G1, G2, G3 = (ok(L, .03, 270) for L in (.30, .36, .42, .49))
BRASS = [rgb(h) for h in ["3b2810", "6a4819", "9a6c2a", "c8973f", "e6c06e", "faeab8"]]
STONE = [rgb(h) for h in ["4b4842", "6f6b63", "9b968b", "beb9ae", "dcd8cf", "f1efe9"]]
PRINT = [rgb(h) for h in ["10253d", "17324f", "1f4166", "2c5784", "a9d2ff", "e6f3ff"]]
NAVY = [SLATE[2], SLATE[3], SLATE[4]]
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

def hsh(*values):
    """A small integer hash: the same inputs always draw the same texture."""
    h = 2166136261
    for v in values:
        h = ((h ^ (v & 0xffffffff)) * 16777619) & 0xffffffff
    return h ^ (h >> 15)


def runs(n, seed):
    """A line of n pixels as runs: True on short runs of a second tone, two or three long, between runs of the first,
    two to four long, so about two fifths of a surface takes the second tone and no pixel stands alone."""
    out, k, on = [], 0, hsh(seed, 7) % 3 == 0
    while len(out) < n:
        out += [on] * ((2 + hsh(seed, k) % 2) if on else (2 + hsh(seed, k) % 3))
        on, k = not on, k + 1
    return out[:n]


def fill(c, x0, y0, x1, y1, a, b=None, salt=0):
    """A surface in tone a with runs of the close tone b along its rows, mirrored left to right about its centre."""
    half = (x1 - x0 + 2) // 2
    for y in range(y0, y1 + 1):
        line = runs(half, hsh(salt, y, x0, x1)) if b else [False] * half
        for i, on in enumerate(line):
            c.px(x0 + i, y, b if on else a)
            c.px(x1 - i, y, b if on else a)


def edge(c, x0, y0, x1, y1, a, b=None, salt=0):
    """A rectangle's outline in tone a with runs of b along it, mirrored left to right."""
    fill(c, x0, y0, x1, y0, a, b, hsh(salt, 1))
    fill(c, x0, y1, x1, y1, a, b, hsh(salt, 2))
    side = runs(y1 - y0 - 1, hsh(salt, 3)) if b else [False] * (y1 - y0 - 1)
    for j, on in enumerate(side):
        c.px(x0, y0 + 1 + j, b if on else a)
        c.px(x1, y0 + 1 + j, b if on else a)


def ring(c, k, a, b=None, salt=0):
    edge(c, k, k, 15 - k, 15 - k, a, b, hsh(salt, k))


def plate(salt=0, centre=None):
    """The frame every machine face shares: a light rim and a light frame, each in two close tones, with a slate groove
    between them; nothing lit from one side and every part mirrored left to right, so faces tile into walls. The 10x10
    centre (3..12) is the face's own: plain steel, or the two tones `centre` gives."""
    c = Canvas()
    fill(c, 3, 3, 12, 12, *(centre or (STEEL[2], STEEL[3])), salt=salt)
    ring(c, 0, STEEL[0], STEEL[1], salt)
    ring(c, 1, SLATE[3], SLATE[2], salt + 1)
    ring(c, 2, STEEL[0], STEEL[1], salt + 2)
    return c


def window(c, x0=3, y0=3, x1=12, y1=12, ramp=None, salt=0):
    """A display: a bezel around a screen. Lit, both are the hue's (mid tones round its darkest two); unlit, slate."""
    if ramp:
        # a warm hue darkened turns brown, so warm light sits on a slate screen
        dark = ramp[0] if ramp[3][2] >= ramp[3][0] else mix(SLATE[1], ramp[1], .2)
        edge(c, x0, y0, x1, y1, ramp[2], mix(ramp[2], ramp[1], .45), salt + 5)
        fill(c, x0 + 1, y0 + 1, x1 - 1, y1 - 1, dark, mix(dark, ramp[1], .4), salt + 6)
    else:
        edge(c, x0, y0, x1, y1, SLATE[4], mix(SLATE[4], SLATE[3], .5), salt + 5)
        fill(c, x0 + 1, y0 + 1, x1 - 1, y1 - 1, SLATE[1], mix(SLATE[1], SLATE[2], .45), salt + 6)


def glow_glyph(c, rows, ramp):
    """A glyph lit on a screen, in four shades of one hue."""
    x0, y0 = centered(rows)
    tone = {"-": ramp[2], "#": ramp[4], "+": ramp[5], "*": mix(ramp[5], (255, 255, 255, 255), .5)}
    for (x, y), ch in glyph_cells(rows).items():
        c.px(x0 + x, y0 + y, tone[ch])


def dim_glyph(c, rows):
    """The same glyph on a screen that is off."""
    x0, y0 = centered(rows)
    tone = {"-": SLATE[3], "#": SLATE[4], "+": STEEL[6], "*": STEEL[6]}
    for (x, y), ch in glyph_cells(rows).items():
        c.px(x0 + x, y0 + y, tone[ch])


def engraved_glyph(c, rows, ramp=None):
    """Cut into the plate in mid steel, its brightest part in the block's hue."""
    x0, y0 = centered(rows)
    tone = {"-": STEEL[4], "#": STEEL[6], "+": ramp[3] if ramp else STEEL[7], "*": ramp[3] if ramp else STEEL[7]}
    for (x, y), ch in glyph_cells(rows).items():
        c.px(x0 + x, y0 + y, tone[ch])


def main_input(c, ramp=None):
    """A main input: a square socket sunk in the plate, its pin lit in the face's hue, or dark without one."""
    edge(c, 4, 4, 11, 11, SLATE[3], SLATE[2], 41)
    fill(c, 5, 5, 10, 10, SLATE[1], mix(SLATE[1], SLATE[0], .5), 42)
    edge(c, 6, 6, 9, 9, SLATE[2])
    pin = (ramp[4], ramp[3]) if ramp else (SLATE[4], SLATE[3])
    c.rect(7, 7, 8, 7, pin[0])
    c.rect(7, 8, 8, 8, pin[1])


def side_input(c, ramp):
    """A side input: a square port, a ring and a pin inside it in the machine's hue; square, so it reads the same
    whichever way a face turns."""
    edge(c, 5, 5, 10, 10, SLATE[3], SLATE[2], 43)
    fill(c, 6, 6, 9, 9, SLATE[1])
    edge(c, 6, 6, 9, 9, ramp[2])
    c.rect(7, 7, 8, 7, ramp[4])
    c.rect(7, 8, 8, 8, ramp[3])


# ---------------------------------------------------------------- operator glyphs, 8 x 8 or less, even both ways

# The matrix machines share one grid of 2 px cells, 1 px apart, three across: which cells light says what each does.
# Each operator's sides show its glyph with the top toward the front (data.py's flow), so a glyph that pictures a change
# reads upward: what comes in at the bottom, what goes out at the top. Its front shows the same glyph.
GLYPHS = {
    # a table, one row of it looked up
    "embedding": [
        "--.--.--",
        "--.--.--",
        "........",
        "##.++.##",
        "##.++.##",
        "........",
        "--.--.--",
        "--.--.--",
    ],
    # a row of its own for every position
    "position": [
        "++.--.--",
        "++.--.--",
        "........",
        "--.++.--",
        "--.++.--",
        "........",
        "--.--.++",
        "--.--.++",
    ],
    # a learned matrix, every cell of it weight
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
    # a row of one grid meeting a column of the other
    "matmul": [
        "--.##.--",
        "--.##.--",
        "........",
        "##.++.##",
        "##.++.##",
        "........",
        "--.##.--",
        "--.##.--",
    ],
    # flat, then rising
    "activation": [
        "......##",
        ".....##.",
        "....##..",
        "....#...",
        "...##...",
        "####....",
        "........",
        "--------",
    ],
    # two channels crossing, lit where they meet
    "add": [
        "..#--#..",
        "..#--#..",
        "###--###",
        "---++---",
        "---++---",
        "###--###",
        "..#--#..",
        "..#--#..",
    ],
    "multiply": [
        "#......#",
        ".#....#.",
        "..#..#..",
        "...++...",
        "...++...",
        "..#..#..",
        ".#....#.",
        "#......#",
    ],
    # every vector brought to the same size
    "norm": [
        "--------",
        "........",
        "##.++.##",
        "##.++.##",
        "##.++.##",
        "##.++.##",
        "........",
        "--------",
    ],
    # every number made smaller by the same factor
    "scale": [
        "...++...",
        "........",
        "..####..",
        "........",
        ".######.",
        "........",
        "--------",
        "........",
    ],
    # the future hidden: each row sees only itself and what came before
    "causal_mask": [
        "+-------",
        "#+------",
        "##+-----",
        "###+----",
        "####+---",
        "#####+--",
        "######+-",
        "#..##..#",
    ],
    # scores become weights: the largest takes most
    "softmax": [
        "...++...",
        "...++...",
        "...++...",
        "##.++.##",
        "##.++.##",
        "##.++.##",
        "........",
        "--------",
    ],
    # one vector, cut into heads
    "split_heads": [
        "++.++.++",
        "++.++.++",
        "........",
        "........",
        "........",
        "........",
        "#..##..#",
        "#..##..#",
    ],
    # the heads, joined back into one vector
    "merge_heads": [
        "#..##..#",
        "#..##..#",
        "........",
        "........",
        "........",
        "........",
        "++.++.++",
        "++.++.++",
    ],
    # the same row, a step later: its first cell left empty
    "shift": [
        "--.++.++",
        "--.++.++",
        "........",
        "........",
        "##.##.##",
        "##.##.##",
    ],
    # two vectors, laid end to end
    "concat": [
        "###..+++",
        "###..+++",
        "........",
        "........",
        "###+++++",
        "###+++++",
    ],
    # queries on one side, keys on the other: how much each looks at each
    "attention": [
        "##....##",
        "##----##",
        "........",
        "##....##",
        "##++++##",
        "........",
        "##....##",
        "##----##",
    ],
    # widen, then project back
    "mlp": [
        ".##..##.",
        ".##..##.",
        "........",
        "##.++.##",
        "##.++.##",
        "........",
        ".##..##.",
        ".##..##.",
    ],
    # the stream up the middle, and two layers reading from it and writing back
    "transformer": [
        "##.++.##",
        "##.++.##",
        "...++...",
        "...++...",
        "##.++.##",
        "##.++.##",
        "...++...",
        "...++...",
    ],
    # every vector the same height from one floor: no mean taken off
    "rms_norm": [
        "........",
        "##.++.##",
        "##.++.##",
        "##.++.##",
        "##.++.##",
        "##.++.##",
        "........",
        "--------",
    ],
    # one vector in at the bottom, two branches up its sides, the gate where they meet
    "swiglu": [
        "...++...",
        "...++...",
        "##.++.##",
        "##....##",
        "##....##",
        "##....##",
        "........",
        ".------.",
    ],
    # a pair of numbers, each taking the other's place as they go along
    "rotary": [
        "##....++",
        "##....++",
        ".##..++.",
        "..#++#..",
        "..+##+..",
        ".++..##.",
        "++....##",
        "++....##",
    ],
    # queries and keys cut to one height under a lid, and the score they make below it
    "qk_norm": [
        "--------",
        "........",
        "###..###",
        "###..###",
        "###..###",
        "###..###",
        "........",
        "...++...",
    ],
    # one line in, two lines of scores out, the second dimmer: the token after next
    "two_token_head": [
        "++++++++",
        "++++++++",
        "........",
        "--------",
        "--------",
        "........",
        "...##...",
        "...##...",
    ],
    # scores held down under a lid
    "z_loss": [
        "--------",
        "........",
        "...++...",
        "...++...",
        "##.++.##",
        "##.++.##",
        "##.++.##",
        "##.++.##",
    ],
    # a grid of scores, one tile of it in hand
    "modern_attention": [
        "##.++.##",
        "##.++.##",
        "........",
        "--.--.--",
        "--.--.--",
        "........",
        "--.--.--",
        "--.--.--",
    ],
    # the stream up the middle, and two layers each resting on a thin line of its own
    "modern_block": [
        "##.++.##",
        "--.++.--",
        "...++...",
        "...++...",
        "##.++.##",
        "--.++.--",
        "...++...",
        "...++...",
    ],
}
# what each machine's color says: the stream and its arithmetic, learned weights, attention, the shape of data,
# functions applied to every number
HUE = {"add": TEAL, "multiply": TEAL, "scale": TEAL, "shift": TEAL,
       "embedding": AZURE, "position": AZURE, "linear": AZURE, "mlp": AZURE,
       "attention": AMETHYST, "matmul": AMETHYST, "softmax": AMETHYST, "causal_mask": AMETHYST, "transformer": AMETHYST,
       "concat": AMBER, "split_heads": AMBER, "merge_heads": AMBER,
       "activation": GREEN, "norm": GREEN, "rms_norm": GREEN, "swiglu": AZURE, "rotary": AMETHYST, "qk_norm": AMETHYST,
       "z_loss": GREEN, "modern_attention": AMETHYST, "modern_block": AMETHYST, "two_token_head": AZURE}
# glyphs that picture something one-sided: a rising curve, a triangle, a step in time, a diagonal, two different halves
ONE_SIDED = {"activation", "causal_mask", "shift", "position", "concat", "rotary"}

PARAMETRIC = {"embedding", "position", "linear", "norm", "attention", "mlp", "transformer", "rms_norm", "swiglu", "modern_attention",
              "modern_block", "two_token_head"}
ARITY = {"position": 0, "add": 2, "multiply": 2, "matmul": 2, "concat": 2, "qk_norm": 2}


def operator_faces(op):
    """Front: the glyph lit on a screen in the machine's hue. Sides: a slot where a side input goes, the glyph on a dark
    screen where weights are kept, or else engraved."""
    hue = HUE[op]
    front = plate(salt=1)
    window(front, ramp=hue, salt=1)
    glow_glyph(front, GLYPHS[op], hue)

    side = plate(salt=2, centre=(mix(STEEL[2], hue[4], .18), mix(STEEL[3], hue[4], .18)))
    if ARITY.get(op, 1) == 2:
        side_input(side, hue)
    elif op in PARAMETRIC:
        window(side, salt=2)
        dim_glyph(side, GLYPHS[op])
    else:
        engraved_glyph(side, GLYPHS[op], hue)
    return front, side


# A Model Core's recipe units. Each shows the same picture on every side, on a screen that lights while a run uses it.
UNIT_GLYPHS = {
    # the rate over a run: up, held, and down
    "scheduler": [
        "........",
        "..####..",
        ".######.",
        ".######.",
        "########",
        "########",
        "........",
        "--------",
    ],
    # a grid of numbers: the narrower the format, the fewer of them
    "bf16_unit": [
        "##.++.##",
        "##.++.##",
        "........",
        "++.##.++",
        "++.##.++",
        "........",
        "##.++.##",
        "##.++.##",
    ],
    "fp8_unit": [
        "++.##.++",
        "++.##.++",
        "........",
        "##....##",
        "##....##",
        "........",
        "++.##.++",
        "++.##.++",
    ],
    "fp4_unit": [
        "++....++",
        "++....++",
        "........",
        "........",
        "........",
        "........",
        "++....++",
        "++....++",
    ],
    # two directions at right angles, of equal length
    "orthogonalizer": [
        "...++...",
        "...++...",
        "...++...",
        "++++++++",
        "++++++++",
        "...++...",
        "...++...",
        "...++...",
    ],
    # rows filled with pieces of different lengths, nothing left over
    "packer": [
        "###..###",
        "###..###",
        "........",
        "#.++++.#",
        "#.++++.#",
        "........",
        "##.##.##",
        "##.##.##",
    ],
}
UNIT_HUE = {"scheduler": TEAL, "bf16_unit": GREEN, "fp8_unit": GREEN, "fp4_unit": GREEN, "orthogonalizer": AZURE, "packer": AMBER}


def unit_face(name, on):
    """Its picture on a screen in the plate, dark until a run uses it."""
    c = plate(salt=64)
    window(c, ramp=UNIT_HUE[name] if on else None, salt=64)
    if on:
        glow_glyph(c, UNIT_GLYPHS[name], UNIT_HUE[name])
    else:
        dim_glyph(c, UNIT_GLYPHS[name])
    return c


def machine_back():
    back = plate(salt=3)
    main_input(back, TEAL)
    return back


def blank_back(op):
    back = plate(salt=3)
    engraved_glyph(back, GLYPHS[op], HUE[op])
    return back


# ---------------------------------------------------------------- Model Core

EMITTER = [
    "oooooooo",
    "oRRRRRRo",
    "oR....Ro",
    "oR.cc.Ro",
    "oR.cc.Ro",
    "oR....Ro",
    "oRRRRRRo",
    "oooooooo",
]


def core_base(salt):
    """The Core's face: the shared frame round a graphite centre."""
    return plate(salt=salt, centre=(G1, mix(G1, G2, .5)))


def core_front(phase=None):
    """Tokens leave through the front: square rings round a core, amber; running, a pulse runs out along the four
    traces to the frame."""
    on = phase is not None
    c = core_base(salt=4)
    edge(c, 4, 4, 11, 11, G0)
    edge(c, 5, 5, 10, 10, AMBER[4] if on and phase else AMBER[3] if on else AMBER[1])
    edge(c, 6, 6, 9, 9, SLATE[0])
    c.rect(7, 7, 8, 8, AMBER[5] if on else AMBER[2])
    trace = AMBER[4] if on and not phase else AMBER[2] if on else AMBER[1]
    for a in (7, 8):
        for x, y in ((a, 3), (a, 12), (3, a), (12, a)):
            c.px(x, y, trace)
    return c


def core_side(phase=None):
    """Scores arrive on every other face: a grid of cells, flickering while the model trains."""
    on = phase is not None
    c = core_base(salt=5)
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
    return c


def animated(frames, rel, frametime):
    strip = Canvas(16, 16 * len(frames))
    for i, f in enumerate(frames):
        strip.im.paste(f.im, (0, 16 * i))
    strip.save(rel)
    meta = ASSETS / "textures" / (rel + ".mcmeta")
    meta.write_text(json.dumps({"animation": {"frametime": frametime}}, indent=2) + "\n")


def lit(rel, on, off, frametime=None):
    """A working face as two textures, the way AE2 and Mekanism light theirs: the face with holes where it differs from
    the idle face, and those pixels alone in `rel`_lights, which the model draws full-bright (tools/data.py), so a
    working machine glows in the dark."""
    faces, lights = [], []
    for i, frame in enumerate(on):
        idle = off[i % len(off)]
        face, light = frame.copy(), Canvas()
        for y in range(16):
            for x in range(16):
                if frame.get(x, y) != idle.get(x, y):
                    face.px(x, y, CLEAR)
                    light.px(x, y, frame.get(x, y))
        faces.append(face)
        lights.append(light)
    glow = rel.replace(".png", "_lights.png")
    if len(on) > 1:
        animated(faces, rel, frametime)
        animated(lights, glow, frametime)
    else:
        faces[0].save(rel)
        lights[0].save(glow)
    SAVED[rel] = on[0].im
    SAVED.pop(glow)


# ---------------------------------------------------------------- other blocks

CHIP = [
    "....l..l....",
    "....l..l....",
    "..dddddddd..",
    "..dhhhhhhd..",
    "lldhGGGGhdll",
    "..dhGttGhd..",
    "..dhGttGhd..",
    "lldhGGGGhdll",
    "..dhhhhhhd..",
    "..dddddddd..",
    "....l..l....",
    "....l..l....",
]


def accelerator():
    """A bare chip on a plate, its leads running out to the frame."""
    c = plate(salt=6)
    c.stamp(2, 2, CHIP, {"l": STEEL[6], "d": SLATE[2], "h": G3, "G": G2, "t": TEAL[3]})
    c.rect(7, 7, 8, 7, TEAL[5])
    c.rect(7, 8, 8, 8, TEAL[4])
    return c


def casing():
    """Plain housing: an inner panel with a rivet in each corner."""
    c = plate(salt=7)
    edge(c, 4, 4, 11, 11, STEEL[4], STEEL[5], 71)
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        c.px(x, y, STEEL[7])
    return c


def accelerator_hot():
    """The same chip running hot: leads and die glowing."""
    c = plate(salt=6)
    c.stamp(2, 2, CHIP, {"l": CORAL[3], "d": SLATE[2], "h": CORAL[1], "G": CORAL[0], "t": CORAL[4]})
    c.rect(7, 7, 8, 7, CORAL[5])
    c.rect(7, 8, 8, 8, CORAL[4])
    return c


# ---------------------------------------------------------------- Model Server

SERVER_CRYSTAL = [
    ".TTTT.",
    "TttttT",
    "TtaatT",
    "TtaatT",
    "TttttT",
    ".TTTT.",
]


def server_base(salt):
    return plate(salt=salt, centre=(G1, mix(G1, G2, .5)))


def server_front(crystal=False, phase=None):
    """A rack face: the crystal's bay above, request lights below that run while it answers."""
    on = phase is not None
    c = server_base(salt=8)
    window(c, 4, 3, 11, 10, salt=8)
    # the cradle a crystal sits in
    for x in (6, 9):
        c.px(x, 4, G3)
        c.px(x, 9, G3)
    if crystal:
        tones = (TEAL[4], TEAL[3], TEAL[5]) if on else (TEAL[2], TEAL[1], TEAL[2])
        c.stamp(5, 4, SERVER_CRYSTAL, dict(zip("Tta", tones)))
    c.rect(4, 12, 11, 12, W0)
    for i, x in enumerate((4, 6, 9, 11)):
        if not crystal:
            color = DIM[2]
        elif not on:
            color = TEAL[3] if x in (4, 11) else DIM[3]
        else:
            color = TEAL[4] if (i + phase) % 4 in (0, 1) else TEAL[1]
        c.px(x, 12, color)
    return c


def server_side():
    """Vents: a Model Server runs warm."""
    c = server_base(salt=9)
    for y in (4, 6, 8, 10):
        for x in range(4, 12):
            c.px(x, y, G0)
            c.px(x, y + 1, G3)
    return c


def server_top():
    c = server_base(salt=10)
    for gy in (4, 7, 10):
        for gx in (4, 7, 10):
            c.rect(gx, gy, gx + 1, gy + 1, G0)
    return c


# ---------------------------------------------------------------- Reader

READER_LINES = [
    "###.####",
    "#####.##",
    "##.#####",
    "####.##.",
]
READER_ROWS = (4, 6, 9, 11)
PAGE = [
    "########",
    "#......#",
    "#.####.#",
    "#......#",
    "#.####.#",
    "#......#",
    "#.####.#",
    "########",
]


def reader_front(step=None):
    """Lines of an old text behind glass: dark while idle, lighting as a model reads them."""
    c = plate(salt=11)
    window(c, salt=11)
    for row, line in enumerate(READER_LINES):
        y = READER_ROWS[row]
        for i, ch in enumerate(line):
            if ch != "#":
                continue
            x = 4 + i
            if step is None:
                color = DIM[2]
            elif row < 2 or (row == 2 and i < step):
                color = AZURE[4]
            elif row == 2 and i == step:
                color = AZURE[5]
            else:
                color = DIM[2]
            c.px(x, y, color)
    if step is not None:
        c.px(4 + step, READER_ROWS[2] + 1, AZURE[3])
    return c


def reader_top():
    """A shallow tray to lay a shard in."""
    c = plate(salt=12)
    edge(c, 4, 4, 11, 11, SLATE[3], SLATE[2], 121)
    fill(c, 5, 5, 10, 10, STEEL[4], STEEL[5], 122)
    for y in (6, 9):
        for x in range(6, 10):
            c.px(x, y, STEEL[6])
    return c


def reader_side():
    c = plate(salt=13)
    engraved_glyph(c, PAGE, AZURE)
    return c


# ---------------------------------------------------------------- Bench and Fabricator

def bench_top():
    """A breadboard: a grid of holes, a chip in the middle and a wire from it to either side."""
    c = Canvas()
    fill(c, 0, 0, 15, 15, G2, G1, 131)
    edge(c, 0, 0, 15, 15, STEEL[4], STEEL[5], 132)
    fill(c, 2, 2, 13, 13, SLATE[1], mix(SLATE[1], SLATE[2], .4), 133)
    for y in (3, 6, 9, 12):
        for x in (3, 6, 9, 12):
            c.px(x, y, SLATE[3])
    edge(c, 6, 5, 9, 10, G3)
    fill(c, 7, 6, 8, 9, G2)
    for y in (6, 9):
        for x in (4, 5, 10, 11):
            c.px(x, y, TEAL[3])
        c.px(3, y, TEAL[5])
        c.px(12, y, TEAL[5])
    c.rect(7, 6, 8, 6, TEAL[4])
    return c


def bench_side():
    """Top rows: the table's edge with an accent stripe. Below: the metal its legs and shelf are made of."""
    c = Canvas()
    for x in range(16):
        c.px(x, 0, K0)
        c.px(x, 1, K1)
        c.px(x, 2, TEAL[2])
        c.px(x, 3, K3)
    fill(c, 0, 4, 15, 15, STEEL[1], STEEL[2], 141)
    return c


WAFER_STAGE = [
    "oooooooooo",
    "odd.dd.ddo",
    "odd.dd.ddo",
    "o........o",
    "odd.dd.ddo",
    "odd.dd.ddo",
    "o........o",
    "odd.dd.ddo",
    "odd.dd.ddo",
    "oooooooooo",
]


def fabricator_front(scan=None):
    """Dies on its stage behind glass; while it works, a violet line sweeps down them, leaving the pattern."""
    c = plate(salt=14)
    window(c, salt=14)
    for y in range(4, 12):
        for x in range(4, 12):
            if WAFER_STAGE[y - 3][x - 3] != "d":
                continue
            top = WAFER_STAGE[y - 4][x - 3] != "d"
            if scan is not None and y < 3 + scan:
                color = AMETHYST[3] if top else AMETHYST[2]
            elif scan is not None and y == 3 + scan:
                color = AMETHYST[5]
            else:
                color = SLATE[4] if top else SLATE[3]
            c.px(x, y, color)
    return c


def fabricator_side():
    """Vents for the lamp's heat."""
    c = plate(salt=15)
    for y in (4, 6, 8, 10):
        for x in range(4, 12):
            c.px(x, y, SLATE[1])
            c.px(x, y + 1, STEEL[0])
    return c


def fabricator_top():
    """The lamp's aperture, square lenses nested looking down."""
    c = plate(salt=16)
    ring = [
        "oooooooo",
        "oLLLLLLo",
        "oLllllLo",
        "oLlAAlLo",
        "oLlAAlLo",
        "oLllllLo",
        "oLLLLLLo",
        "oooooooo",
    ]
    c.stamp(4, 4, ring, {"o": G1, "L": STEEL[5], "l": AMETHYST[2], "A": AMETHYST[4]})
    return c


# the forge's display: four lines of text, a character on every other pixel
FORGE_ROWS = (4, 6, 9, 11)
FORGE_GAPS = [(0, 4), (2, 3), (1, 6), (3, 4), (0, 7), (2, 5), (1, 2), (3, 1), (0, 1), (2, 7), (1, 4)]
CHIP_TONES = [TEAL[2], TEAL[3], TEAL[1]]


# ---------------------------------------------------------------- the Refinery

# what each machine does to the text, engraved on its sides; the hue the family shares is the shape of data's, amber
REFINERY_GLYPHS = {
    "refinery_intake": [
        "#..##..#",
        "#..##..#",
        "#.#++#.#",
        "#..##..#",
        "#......#",
        "##.##.##",
        ".#.##.#.",
        ".##++##.",
    ],
    "duplicate_sieve": [
        "##.##.##",
        "##.##.##",
        "........",
        "#+#..#+#",
        "#+#..#+#",
        "........",
        "##.++.##",
        "##.++.##",
    ],
    "filter_bed": [
        "#..##..#",
        "........",
        "#..##..#",
        "........",
        ".#.++.#.",
        "........",
        "++++++++",
        "#..##..#",
    ],
    "quality_classifier": [
        "..####..",
        ".#....#.",
        "#..++..#",
        "#.++++.#",
        "#.++++.#",
        "#..++..#",
        ".#....#.",
        "..####..",
    ],
    "program_press": [
        ".##..##.",
        ".#....#.",
        ".#....#.",
        "##.++.##",
        "##.++.##",
        ".#....#.",
        ".#....#.",
        ".##..##.",
    ],
    "domain_mixer": [
        "##.++.##",
        ".#.++.#.",
        ".##++##.",
        "..#++#..",
        "..#++#..",
        ".##++##.",
        ".#.++.#.",
        "##.++.##",
    ],
    "contamination_check": [
        "###..###",
        "#......#",
        "#.####.#",
        "..#++#..",
        "..#++#..",
        "#.####.#",
        "#......#",
        "###..###",
    ],
}
# the lines each front shows of the text: which columns of which rows hold letters, dim while it waits
REFINE_ROWS = (4, 6, 8, 10)


def refine_lines(c, kept, tone):
    """Lines of text in a machine's window: the columns of each row in kept, in a tone by row."""
    for y, columns in zip(REFINE_ROWS, kept):
        for x in columns:
            c.px(x, y, tone(y, x))


def refinery_front(name, step=None):
    """An amber bezel, its machine's mark dim behind the glass while it waits; while it works, lines of text that thin, sort
    or run together as that machine makes them do, a frame at a time."""
    salt = 70 + sum(map(ord, name)) % 40
    c = plate(salt=salt)
    window(c, salt=salt)
    edge(c, 3, 3, 12, 12, AMBER[2], mix(AMBER[2], AMBER[1], .45), salt)
    full = range(4, 12)
    lines = [list(full)] * 4
    idle = lambda y, x: SLATE[3]
    if step is None:
        dim_glyph(c, REFINERY_GLYPHS[name])
        return c
    on = lambda y, x: AMBER[3] if (x + y) % 4 else AMBER[4]
    if name == "refinery_intake":
        # text comes in from the top a row at a time
        for k, y in enumerate(REFINE_ROWS):
            for x in full:
                c.px(x, y, AMBER[4] if k == step % 4 else SLATE[3])
    elif name == "duplicate_sieve":
        # the row that repeats the one above it falls out
        gone = step % 4
        refine_lines(c, [[] if k == gone else [x for x in full if (x + (k == (gone + 3) % 4)) % 3] for k in range(4)], on)
    elif name == "filter_bed":
        # fewer letters get through each layer down
        for k, y in enumerate(REFINE_ROWS):
            for x in full:
                if (x + step) % (k + 1) == 0:
                    c.px(x, y, AMBER[4 - min(k, 2)])
    elif name == "quality_classifier":
        # a line of attention goes down the text and passes or fails each line
        refine_lines(c, lines, idle)
        y = REFINE_ROWS[step % 4]
        for x in full:
            c.px(x, y, GREEN[4] if step % 8 < 4 else CORAL[4])
    elif name == "program_press":
        # the same line written over and over
        for k, y in enumerate(REFINE_ROWS):
            for x in full:
                if k <= step % 4 and (x % 3 or k % 2):
                    c.px(x, y, AMBER[3 + k % 2])
    elif name == "domain_mixer":
        # lines in the domains' colours trade places
        hues = (TEAL[4], AMETHYST[4], GREEN[4], AMBER[4])
        for k, y in enumerate(REFINE_ROWS):
            for x in full:
                c.px(x, y, hues[(k + x // 2 + step) % 4])
    else:
        # a mark passes over the text and stops where it finds something of the test's
        refine_lines(c, lines, idle)
        x0 = 4 + step % 6
        for k, y in enumerate(REFINE_ROWS):
            for x in (x0, x0 + 1):
                c.px(x, y, CORAL[4] if k == 2 and x0 == 6 else AMBER[4])
    return c


def refinery_side(name):
    c = plate(salt=112 + sum(map(ord, name)) % 40)
    engraved_glyph(c, REFINERY_GLYPHS[name], AMBER)
    return c


def tank_frame():
    """The tank's cage: posts at the corners and plates top and bottom, drawn once for all of them to take from."""
    c = Canvas()
    fill(c, 0, 0, 15, 15, STEEL[3], STEEL[4], 150)
    edge(c, 0, 0, 15, 15, STEEL[1], STEEL[2], 151)
    edge(c, 1, 1, 14, 14, SLATE[3], SLATE[2], 152)
    return c


def tank_glass():
    """Clear glass with a little light along its edges, nearly nothing in the middle."""
    c = Canvas()
    for i in range(16):
        for j in (0, 15):
            c.px(i, j, (210, 228, 235, 90))
            c.px(j, i, (210, 228, 235, 90))
    for x, y in ((3, 4), (4, 3), (5, 2), (11, 12), (12, 11), (13, 10)):
        c.px(x, y, (235, 245, 250, 70))
    return c


def tank_cap():
    """The cap: a port the stream comes in by, lit in amber."""
    c = plate(salt=153)
    edge(c, 5, 5, 10, 10, SLATE[3], SLATE[2], 154)
    c.rect(6, 6, 9, 9, SLATE[1])
    c.rect(7, 7, 8, 8, AMBER[3])
    return c


RELAY_DUMP = [
    "................",
    "....kKKKKKk.....",
    "...kGGGGGGGk....",
    "...GPPPPPPPG....",
    "...GPlPllPPGG...",
    "...GPPPPPPPGg...",
    "...GPllPlPPG....",
    "...GGPPPPPPGg...",
    "....GPlllPPGG...",
    "...gGPPPPPPG....",
    "...GPPlPlPPG....",
    "...GPPPPPPyG....",
    "...GGGGGGGGG....",
    "...gGG.gGGGg....",
    "................",
    "................",
]
TOKEN_CELL = [
    "................",
    ".....kKKKKk.....",
    ".....kKKKKk.....",
    "....GGGGGGGG....",
    "....Gww..wwG....",
    "....Gw.tt.wG....",
    "....G.tttt.G....",
    "....GtTTTTtG....",
    "....GtTTTTtG....",
    "....GTTTTTTG....",
    "....GTTTTTTG....",
    "....GTTTTTTG....",
    "....GGGGGGGG....",
    ".....kKKKKk.....",
    "................",
    "................",
]


def relay_dump():
    """A drive housing that came down with its mast: dented, its edge broken, its light still on."""
    c = Canvas()
    c.stamp(0, 0, RELAY_DUMP, {"k": K3, "K": K1, "G": G2, "g": G1, "P": mix(K0, STONE[2], .35), "l": K4, "y": AMBER[3]})
    outline(c, SLATE[0])
    return c


def token_cell():
    """A glass cell with tokens in it, capped in steel."""
    c = Canvas()
    c.stamp(0, 0, TOKEN_CELL, {"k": K3, "K": K1, "G": STEEL[5], "w": STEEL[1], "t": TEAL[3], "T": TEAL[2]})
    outline(c, SLATE[0])
    return c


def refinery_textures():
    for rows in REFINERY_GLYPHS.values():
        assert mirrored(rows), rows
    frames = {"refinery_intake": 4, "duplicate_sieve": 4, "filter_bed": 4, "quality_classifier": 8, "program_press": 4, "domain_mixer": 4,
              "contamination_check": 6}
    for name, count in frames.items():
        refinery_front(name).save(f"block/{name}_front.png")
        lit(f"block/{name}_front_on.png", [refinery_front(name, i) for i in range(count)], [refinery_front(name)], 5)
        refinery_side(name).save(f"block/{name}_side.png")
        machine_top(90 + sum(map(ord, name)) % 40).save(f"block/{name}_top.png")
    tank_frame().save("block/token_tank_frame.png")
    tank_glass().save("block/token_tank_glass.png")
    tank_cap().save("block/token_tank_cap.png")
    relay_dump().save("item/relay_dump.png")
    token_cell().save("item/token_cell.png")


def forge_front(step=None):
    """Lines of text behind glass; while it learns, neighbouring tokens join one pair at a time and the new one flashes."""
    c = plate(salt=17)
    window(c, salt=17)
    joined = set(FORGE_GAPS[:step or 0])
    last = FORGE_GAPS[step - 1] if step else None
    for row, y in enumerate(FORGE_ROWS):
        tokens, run = [], [0]
        for b in range(1, 8):
            if (row, b) in joined:
                run.append(b)
            else:
                tokens.append(run)
                run = [b]
        tokens.append(run)
        for k, token in enumerate(tokens):
            if step is None:
                tone = SLATE[3]
            elif last and last[0] == row and last[1] in token:
                tone = CORAL[5]
            else:
                tone = (CORAL[3], CORAL[4])[k % 2]
            for b in token:
                c.px(4 + b, y, tone)
    return c


def forge_side():
    """The slot a tokenizer card goes into."""
    c = plate(salt=18)
    edge(c, 4, 6, 11, 9, SLATE[3], SLATE[2], 181)
    c.rect(5, 7, 10, 8, SLATE[0])
    c.rect(6, 7, 9, 7, CORAL[3])
    for x in (5, 6, 9, 10):
        c.px(x, 11, STEEL[6])
    return c


def forge_top():
    """The intake: a page of text going down."""
    c = plate(salt=19)
    window(c, 4, 4, 11, 11, salt=19)
    c.rect(5, 5, 10, 10, STEEL[1])
    for y in (6, 8):
        for x in range(6, 10):
            c.px(x, y, STEEL[5])
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


def forecaster_front(step=None, ramp=None, every=4):
    """A field from above: nine crops by age, still while it only watches. Running ahead, they grow a look at a time
    and each one lights as the model grows it. Forecaster II's bezel is in the transformer's violet, and its field grows
    a look every other frame instead of every fourth."""
    c = plate(salt=21)
    window(c, salt=21)
    if ramp:
        edge(c, 3, 3, 12, 12, ramp[2], mix(ramp[2], ramp[1], .45), 26)
    for i, (start, phase) in enumerate(FIELD):
        x, y = 4 + i % 3 * 3, 4 + i // 3 * 3
        if step is None:
            color = mix(CROP[start], W1, 0.3)
        else:
            grew = (step + phase) % every == 0
            color = GREEN[5] if grew else CROP[(start + (step + phase) // every) % 8]
        c.rect(x, y, x + 1, y + 1, color)
    return c


# the ground in the Surveyor's window: where its ores lie, and the colour each shows once found
SURVEY_ORES = [(5, 5, "d8af93"), (9, 6, "fcdb3a"), (6, 8, "3a3a3a"), (10, 9, "e07a4a"), (4, 10, "ff4a4a"), (8, 11, "4ae8e0")]
# a core drill: the bit going down, its cutting edges in the block's hue
WATCH_GLYPH = [
    "#..#..+.",
    "#..#..+.",
    "#..#..+.",
    "########",
    "........",
    "...##...",
    "...++...",
]

SURVEY_PROBE = [
    "########",
    ".#.++.#.",
    "...##...",
    "...##...",
    "..#++#..",
    "...##...",
    "...++...",
    "...##...",
]


def surveyor_front(step=None):
    """The ground in cross-section: strata, and a few ores in them, dim while it waits. Surveying, a line sweeps down a
    row a frame, and each ore it has passed shows in its colour. The bezel is in attention's violet, the mask that makes
    it."""
    c = plate(salt=31)
    window(c, salt=31)
    edge(c, 3, 3, 12, 12, AMETHYST[2], mix(AMETHYST[2], AMETHYST[1], .45), 36)
    for y in range(4, 12):
        tone = mix(STONE[0], W1, .55) if y // 2 % 2 == 0 else mix(STONE[1], W1, .6)
        for x in range(4, 12):
            c.px(x, y, tone)
    line = None if step is None else 4 + step % 8
    for x, y, ore in SURVEY_ORES:
        c.px(x, y, rgb(ore) if line is not None and y < line else mix(rgb(ore), W1, .6))
    if line is not None:
        for x in range(4, 12):
            c.px(x, line, AMETHYST[4])
    return c


def watch_post_front(step=None):
    """A lens in its bezel, looking out: a dim pupil while all is well; while something is wrong it burns and pulses."""
    c = plate(salt=33)
    window(c, salt=33)
    edge(c, 3, 3, 12, 12, CORAL[2], mix(CORAL[2], CORAL[1], .45), 37)
    for y in range(4, 12):
        for x in range(4, 12):
            c.px(x, y, mix(W1, STONE[0], .25))
    ring = CORAL[4] if step is not None and step % 2 == 0 else CORAL[3] if step is not None else mix(TEAL[2], W1, .4)
    for x, y in ((6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (10, 6), (5, 7), (10, 7), (5, 8), (10, 8), (5, 9), (10, 9), (6, 10), (7, 10), (8, 10), (9, 10)):
        c.px(x, y, ring)
    pupil = CORAL[5] if step is not None else TEAL[3]
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        c.px(x, y, pupil)
    return c


def watch_post_side():
    c = plate(salt=34)
    engraved_glyph(c, WATCH_GLYPH, CORAL)
    return c


def surveyor_side():
    c = plate(salt=32)
    engraved_glyph(c, SURVEY_PROBE, AMETHYST)
    return c


# the flight frame: two thruster pods joined across the back, their cells lit, their nozzles below
FLIGHT_FRAME = [
    "................",
    "...oo......oo...",
    "..o##o....o##o..",
    "..o#+#oooo#+#o..",
    "..o#+######+#o..",
    "..o#+#o..o#+#o..",
    "..o#+#o..o#+#o..",
    "..o###o..o###o..",
    "..o#*#o..o#*#o..",
    "...o*o....o*o...",
    "....o......o....",
]


def flight_frame():
    c = Canvas()
    c.stamp(0, 2, FLIGHT_FRAME, {"o": INK, "#": STEEL[6], "+": TEAL[3], "*": AMBER[3]})
    return c


# the exosuit: a harness of plates across the shoulders and down the spine, a core lit in its chest, thrusters at its foot
EXOSUIT = [
    "..oooo....oooo..",
    ".o####o..o####o.",
    ".o#%%##oo##%%#o.",
    ".o%%o######o%%o.",
    ".o%%o#=++=#o%%o.",
    ".o%%o#=++=#o%%o.",
    ".o**oo#==#oo**o.",
    "..oo.o%##%o.oo..",
    ".....o%==%o.....",
    ".....o%##%o.....",
    ".....o%==%o.....",
    ".....oo**oo.....",
    "......oooo......",
]


def exosuit():
    c = Canvas()
    c.stamp(0, 1, EXOSUIT, {"o": INK, "#": STEEL[6], "%": STEEL[4], "=": SLATE[2], "+": TEAL[3], "*": AMBER[3]})
    return c


# the Wayfinder: a copper case, a dark glass face, an amethyst pivot, and a needle lit at its tip
WAYFINDER_CASE = [
    "...oooooooooo...",
    "..o##########o..",
    ".o#++++++++++#o.",
    "o#++--------++#o",
    "o#+----------+#o",
    "o#+----------+#o",
    "o#+----------+#o",
    "o#+----------+#o",
    "o#+----------+#o",
    "o#+----------+#o",
    "o#+----------+#o",
    "o#+----------+#o",
    "o#++--------++#o",
    ".o#++++++++++#o.",
    "..o##########o..",
    "...oooooooooo...",
]


def wayfinder(k):
    """Frame k of 32, as vanilla's compass frames run: its needle points (k - 16) * 11.25 degrees clockwise from up."""
    import math
    c = Canvas()
    c.stamp(0, 0, WAYFINDER_CASE, {"o": INK, "#": CORAL[3], "+": CORAL[2], "-": SLATE[0]})
    a = math.radians((k - 16) * 11.25)
    dx, dy = math.sin(a), -math.cos(a)
    cx = cy = 7.5
    for i in range(1, 11):
        r = i * 0.5
        for sign, color in ((1, TEAL[4]), (-1, STEEL[4])):
            if sign < 0 and r > 3:
                continue
            x, y = round(cx + sign * dx * r - 0.01), round(cy + sign * dy * r - 0.01)
            if 3 <= x <= 12 and 3 <= y <= 12:
                c.px(x, y, color)
    c.px(7, 7, AMETHYST[3])
    c.px(8, 8, AMETHYST[2])
    c.px(7, 8, AMETHYST[2])
    c.px(8, 7, AMETHYST[3])
    return c


def flight_frame_worn():
    """Transparent equipment atlas: FlightFrameLayer supplies the articulated model and harness."""
    return Canvas(64, 32)


# the exosuit worn, on the chestplate's body box: the plates and core on its front, the frame's pods with more nozzles on its back
EXOSUIT_FRONT = [
    "e#e..e#e",
    "e#e##e#e",
    "e######e",
    "e#=++=#e",
    "e#=++=#e",
    "e######e",
    ".e#==#e.",
    ".e####e.",
    ".e#==#e.",
    ".e####e.",
    ".e#==#e.",
    "..e##e..",
]
EXOSUIT_BACK = [
    "e#e..e#e",
    "#+#..#+#",
    "#+#ee#+#",
    "#+####+#",
    "#+#ee#+#",
    "#+#..#+#",
    "#+#..#+#",
    "###..###",
    "#*#..#*#",
    "#*#..#*#",
    "e*e..e*e",
    ".e....e.",
]
# an arm's faces, each 4 wide: a shoulder cap and a brace on the forearm, the outer one with a light in its cap
EXOSUIT_ARM_OUT = ["####", "#++#", "####", "e..e", "....", ".ee.", ".ee.", "eeee", "#==#", "#==#", "eeee", "...."]
EXOSUIT_ARM = ["####", "####", "####", "e..e", "....", "....", "....", "eeee", "#==#", "#==#", "eeee", "...."]


def exosuit_worn():
    """The suit as the chestplate's model maps it: a body box 8 wide, 12 tall and 4 deep with its faces from (16, 16), and
    arm boxes 4 by 12 by 4 with theirs from (40, 16): outer, front, inner and back from (40, 20)."""
    c = Canvas(64, 32)
    edge = STEEL[4]
    pal = {"e": edge, "#": STEEL[6], "=": SLATE[2], "+": TEAL[3], "*": AMBER[3]}
    # the shoulders over the top, joined by a rib along each side
    c.rect(20, 16, 22, 19, edge)
    c.rect(25, 16, 27, 19, edge)
    c.stamp(20, 20, EXOSUIT_FRONT, pal)
    c.stamp(32, 20, EXOSUIT_BACK, pal)
    for x in (16, 28):
        c.rect(x, 20, x + 3, 22, edge)
        c.rect(x, 26, x + 3, 26, edge)
    # the arms
    c.rect(44, 16, 47, 19, STEEL[6])
    c.stamp(40, 20, EXOSUIT_ARM_OUT, pal)
    for x in (44, 48, 52):
        c.stamp(x, 20, EXOSUIT_ARM, pal)
    return c



def flight_frame_model():
    """Material tiles for the frame's cuboids, with one texel per model pixel."""
    c = Canvas(128, 64)
    colors = ("b9c5cb", "7f929d", "33414b", "49b8ad", "e9ac56", "ffe3a0")
    for tile, color in enumerate(colors):
        base = rgb(color)
        for y in range(32):
            for x in range(32):
                # Small, deterministic clusters; geometry carries the edge shading.
                k = (x // 2 * 13 + y // 2 * 7 + tile * 3) % 11
                delta = -4 if k < 2 else 3 if k == 8 else 0
                c.px(tile % 4 * 32 + x, tile // 4 * 32 + y,
                     tuple(max(0, min(255, v + delta)) for v in base[:3]) + (255,))
    return c


def forecaster_side(ramp=GREEN):
    c = plate(salt=22)
    engraved_glyph(c, SPROUT, ramp)
    return c


def forecaster_top(ramp=GREEN):
    """An hourglass: it sees where the field is going."""
    c = plate(salt=23)
    engraved_glyph(c, HOURGLASS, ramp)
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
    outline(c, SLATE[0])
    return c


# ---------------------------------------------------------------- Relay

def relay(live=True):
    """One sheet for the relay's three parts, laid out where its model samples them. Its lamp is lit while a model is
    served on its network, and dark glass otherwise."""
    c = Canvas(fill=G2)
    # lamp sides, 6x5: a band of glass between a cap and a collar
    for x in range(6):
        c.px(x, 0, G3)
        c.px(x, 4, G0)
        for y in (1, 2, 3):
            c.px(x, y, G1 if x in (0, 5) else (TEAL[4] if y == 2 and x in (2, 3) else TEAL[3]) if live
                 else (SLATE[4] if y == 2 and x in (2, 3) else SLATE[3]))
    # lamp top and bottom, 6x6
    c.rect(10, 0, 15, 5, G2)
    for i in range(10, 16):
        c.px(i, 0, G3)
        c.px(i, 5, G0)
    for i in range(6):
        c.px(10, i, G3)
        c.px(15, i, G0)
    c.rect(12, 2, 13, 3, TEAL[4] if live else SLATE[3])
    c.px(12, 2, TEAL[5] if live else SLATE[4])
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
    fill(c, 0, 0, 15, 15, G2, G1, 151)
    for y in range(16):
        for x, color in ((0, G0), (1, TEAL_LINE[0]), (3, G0)):
            c.px(x, y, color)
            c.px(15 - x, y, color)
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
    """The channel seen end on: squares of light nested in the graphite."""
    c = Canvas()
    fill(c, 0, 0, 15, 15, G2, G1, 152)
    ring(c, 0, G3)
    rings = [(2, G0), (3, TEAL[1]), (4, TEAL[2]), (5, TEAL[3]), (6, TEAL[4])]
    for k, color in rings:
        ring(c, k, color)
    c.rect(7, 7, 8, 8, TEAL[5])
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
        for k, color in ((6, dark), (7, base), (8, base), (9, dark)):
            c.px(i, k, color)
            c.px(k, i, color)
    c.rect(6, 6, 9, 9, dark)
    c.rect(7, 7, 8, 8, signal)
    return c


def cable_colors(hex_):
    base = rgb(hex_)
    return base, mix(base, (255, 255, 255, 255), 0.35), mix(base, (16, 18, 24, 255), 0.5)


# ---------------------------------------------------------------- items

WEIGHT_CRYSTAL = [
    "................",
    ".......oo.......",
    "......oHho......",
    ".....oHhhmo.....",
    "....oHhhmmmo....",
    "....oHhmmmdo....",
    "....oHhmmmdo....",
    "....oHhmmmdo....",
    "....oHhmmmdo....",
    "....oHhmmmdo....",
    "....oHhmmmdo....",
    "....ohhmmddo....",
    ".....ohmmdo.....",
    "......omdo......",
    ".......oo.......",
    "................",
]


def weight_crystal():
    """A column of crystal with a pointed top and foot, its facets lit from the left."""
    c = Canvas()
    c.stamp(0, 0, WEIGHT_CRYSTAL, {"o": TEAL[0], "H": TEAL[5], "h": TEAL[4], "m": TEAL[3], "d": TEAL[2]})
    for y in (6, 9):
        c.px(7, y, TEAL[4])
        c.px(8, y, TEAL[4])
    return c


def outline(c, color):
    """Rings every drawn shape with a 1 px border in the item's own darkest tone, the way vanilla items are drawn."""
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
    outline(c, SLATE[0])
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


# data cards: datasets, programs and tokenizers are all kept on them, punched into a steel card with a cut corner
# and a row of contacts, a stripe in the color of what each holds
HOLE = SLATE[0]
CARD = [
    "................",
    "................",
    "...oooooooooooo.",
    "..oSSSSSSSSSSSo.",
    ".osssssssssssso.",
    ".oLPPPPPPPPPPPo.",
    ".oLPPPPPPPPPPPo.",
    ".oLPPPPPPPPPPPo.",
    ".oLPPPPPPPPPPPo.",
    ".oLPPPPPPPPPPPo.",
    ".oLPPPPPPPPPPPo.",
    ".oLPPPPPPPPPPPo.",
    ".oGGDGGDDGGDGGo.",
    ".oooooooooooooo.",
    "................",
    "................",
]


def card(ramp):
    """A blank card: steel with its corner cut, a stripe across the top and gold contacts along the bottom."""
    c = Canvas()
    c.stamp(0, 0, CARD, {"o": SLATE[1], "S": ramp[3], "s": ramp[2], "L": STEEL[0], "P": STEEL[1], "D": STEEL[3],
                         "G": BRASS[3]})
    fill(c, 3, 5, 12, 11, STEEL[1], STEEL[2], 77)
    return c


def punch(c, rows, x0=4, y0=5):
    for (x, y) in glyph_cells(rows):
        c.px(x0 + x, y0 + y, HOLE)


# one color per benchmark, on its dataset's stripe
BENCHMARK_COLORS = {"forecast": hue_ramp(85), "storage": AMBER, "errand": AZURE, "pattern": AMETHYST, "orders": TEAL,
                    "sorting": CORAL, "process": GREEN, "survey": hue_ramp(110), "flight": AZURE, "demand": hue_ramp(20), "watch": hue_ramp(350), "retrieval": hue_ramp(315), "reactor": hue_ramp(60)}
# what each dataset practices, punched into the card
DATASET_HOLES = {
    "reactor": [
        "...##...",
        "..#..#..",
        ".#.##.#.",
        ".#.##.#.",
        "..#..#..",
        "...##...",
        "#..##..#",
    ],
    "watch": [
        "..#..#..",
        "..#..#..",
        ".##.##..",
        "#..##..#",
        "#.......",
        "#...#...",
        "........",
    ],
    "demand": [
        "#..#..#.",
        "#..#..#.",
        "##.##.##",
        "#..##..#",
        "........",
        "#..##..#",
        "........",
    ],
    "forecast": [
        "...#....",
        "..##...#",
        ".#.#..##",
        "#..#.#.#",
        "...##..#",
        "........",
        "#..##..#",
    ],
    "storage": [
        "#..##..#",
        "#..#...#",
        "#..#...#",
        "#..##..#",
        "#...#..#",
        "#...#..#",
        "#..##..#",
    ],
    "errand": [
        ".....#..",
        "#..##..#",
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
        "#..##..#",
        "........",
        "##..##..",
        "##..##..",
    ],
    "orders": [
        "#..##..#",
        "#.....#.",
        "#..##..#",
        "#.....#.",
        "#..##..#",
        "..##....",
        "..#.....",
    ],
    # a melt's temperature settling between the two lines of its band
    "process": [
        "#..##..#",
        "#.......",
        ".#..#...",
        "..##.###",
        "........",
        "#..##..#",
        "........",
    ],
    # a frame held at its height: the hold between two lines, the path through it level
    "flight": [
        "#..##..#",
        "........",
        ".#....#.",
        "#..##..#",
        ".#....#.",
        "........",
        "#..##..#",
    ],
    # a row of ground, and above it the mark that finds the first ore in it
    "survey": [
        "...##...",
        "...##...",
        "..####..",
        "........",
        "#.#..#.#",
        "........",
        "#..##..#",
    ],
    # a lens over what it looks through
    "retrieval": [
        "..####..",
        ".#....#.",
        ".#....#.",
        ".#....#.",
        "..####..",
        ".....##.",
        "......##",
    ],
    # a funnel: many kinds in, each sent its own way
    "sorting": [
        "#..##..#",
        ".######.",
        "..####..",
        "...##...",
        "...##...",
        "........",
        "#..##..#",
    ],
}


def dataset(kind):
    """A punched card: the stripe in its benchmark's color, the practice punched below."""
    c = card(BENCHMARK_COLORS[kind])
    punch(c, DATASET_HOLES[kind])
    return c


# a program's lines, punched: runs of holes like instructions
PROGRAM_HOLES = [
    "##.#.##.",
    "........",
    "#.##.#..",
    "........",
    "##..#.##",
    "........",
    "#.#.##..",
]


def program_card(written):
    """A program card: blank, or punched with a program's lines."""
    c = card(BRASS)
    if written:
        punch(c, PROGRAM_HOLES)
    return c


# what an upgrade does, punched in light on its card
UPGRADE_MARKS = {
    "speed": (AMBER, ["#...#...", ".#...#..", "..#...#.", "...#...#", "..#...#.", ".#...#..", "#...#..."]),
    "stack": (TEAL, ["........", "########", "........", "########", "........", "########", "........"]),
    "range": (AZURE, ["#......#", "##....##", "###..###", "........", "###..###", "##....##", "#......#"]),
    "fluid": (AZURE, ["...##...", "...##...", "..####..", ".######.", ".######.", ".######.", "..####.."]),
    "energy": (CORAL, ["....###.", "...###..", "..###...", ".######.", "...###..", "..###...", ".###...."]),
    "carry": (GREEN, ["########", "#......#", "#.####.#", "#......#", "#.####.#", "#......#", "########"]),
    "lift": (AMETHYST, ["...##...", "..####..", ".##..##.", "##....##", "...##...", "..####..", ".##..##."]),
}


def upgrade(name):
    """An upgrade: a card in its hue, what it does marked on it in straight strokes."""
    ramp, rows = UPGRADE_MARKS[name]
    c = card(ramp)
    for row, line in enumerate(rows):
        for i, ch in enumerate(line):
            if ch == "#":
                c.px(4 + i, 5 + row, ramp[4])
    return c


# what each line of a written tokenizer shows: runs of the same letter are one token
TOKEN_LINES = ["aaab.ccc", "bb.aaaa.", "cccc.ab.", "aa.bbb.c"]


def tokenizer(written):
    """A violet-striped card. Blank, its lines are separate characters; written, they are tokens of every length."""
    c = card(AMETHYST)
    tones = {"a": TEAL[2], "b": AMBER[2], "c": AMETHYST[2]}
    for row, y in enumerate((5, 7, 9, 11)):
        for i, ch in enumerate(TOKEN_LINES[row]):
            x = 4 + i
            if written and ch in tones:
                c.px(x, y, tones[ch])
            elif not written and i % 2 == 0:
                c.px(x, y, HOLE)
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
    outline(c, rgb("3e2c1a"))
    return c


HOURGLASS_ITEM = [
    "................",
    "................",
    "..CCCCCCCCCCCC..",
    "..cccccccccccc..",
    "...P.GGGGGG.P...",
    "...P.GTTTTG.P...",
    "...P..GTTG..P...",
    "...P...tt...P...",
    "...P..GttG..P...",
    "...P.GGttGG.P...",
    "...P.GTTTTG.P...",
    "...P.TTTTTT.P...",
    "..CCCCCCCCCCCC..",
    "..cccccccccccc..",
    "................",
    "................",
]


def hourglass():
    """Brass ends and posts, two bulbs of glass, and sand of light running down between them."""
    c = Canvas()
    c.stamp(0, 0, HOURGLASS_ITEM, {"C": BRASS[4], "c": BRASS[2], "P": BRASS[3], "G": mix(STEEL[0], TEAL[5], .25),
                              "T": TEAL[4], "t": TEAL[5]})
    outline(c, BRASS[0])
    return c


CHECKPOINT = [
    "................",
    "......kKKk......",
    ".....kKKKKk.....",
    "....gGGGGGGg....",
    "....gwwwwwhg....",
    "....gwwTtwwg....",
    "....gwTttmwg....",
    "....gTttmmdg....",
    "....gwTtmdwg....",
    "....gwwtdwwg....",
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
                               "T": TEAL[5], "t": TEAL[4], "m": TEAL[3], "d": TEAL[2]})
    outline(c, SLATE[0])
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
    outline(c, SLATE[0])
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
    outline(c, STONE[0])
    return c


def salvaged_model():
    """A weight crystal gone dark, cracked through, still warm inside."""
    c = Canvas()
    dark = rgb("12171c")
    c.stamp(0, 0, WEIGHT_CRYSTAL, {"o": dark, "H": rgb("5d6b75"), "h": rgb("4b5862"), "m": rgb("3a464f"),
                                   "d": rgb("2c353d")})
    crack = [(8, 3), (7, 4), (7, 5), (8, 6), (8, 7), (7, 8), (7, 9), (8, 10), (8, 11), (7, 12)]
    for i, (x, y) in enumerate(crack):
        c.px(x, y, AMBER[4] if i in (4, 5) else AMBER[3])
    for x, y in ((9, 7), (6, 5), (9, 10)):
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
    outline(c, rgb("33241a"))
    return c


def relay_lights():
    """What of the relay's lamp glows while its network is live: its glass, on the sheet where the model samples it."""
    c = Canvas()
    for x in range(1, 5):
        for y in (1, 2, 3):
            c.px(x, y, TEAL[4] if y == 2 and x in (2, 3) else TEAL[3])
    c.rect(12, 2, 13, 3, TEAL[4])
    c.px(12, 2, TEAL[5])
    return c


# a mast's lattice, one side of it 6 px wide and repeating every 8 up: two legs, braces crossing between them and a girt
RELAY_LATTICE = [
    "L#..#L",
    "L#..#L",
    "L.##.L",
    "L.##.L",
    "L#..#L",
    "L#..#L",
    "LLLLLL",
    "L....L",
]


def relay_lattice():
    """The lattice of a relay mast, open between its braces."""
    c = Canvas()
    for y in range(16):
        for x, ch in enumerate(RELAY_LATTICE[y % 8]):
            if ch == "L":
                c.px(5 + x, y, STEEL[4] if x in (0, 5) else STEEL[5])
            elif ch == "#":
                c.px(5 + x, y, STEEL[6])
    return c


# a gate's pad, seen from above: a frame round a square well of light
GATE_WELL = [
    "########",
    "#......#",
    "#.++++.#",
    "#.+**+.#",
    "#.+**+.#",
    "#.++++.#",
    "#......#",
    "########",
]
# a Relay Link's face: two networks joined across a gap
LINK_GLYPH = [
    "##......##",
    "##......##",
    "..#....#..",
    "...#++#...",
    "...+**+...",
    "...+**+...",
    "...#++#...",
    "..#....#..",
    "##......##",
    "##......##",
]


def gate_top(open_):
    """A pad's top: a well that shows light while its network is open."""
    c = plate(salt=61)
    window(c, ramp=TEAL if open_ else None, salt=61)
    if open_:
        glow_glyph(c, GATE_WELL, TEAL)
    else:
        dim_glyph(c, GATE_WELL)
    return c


def gate_side():
    """A pad's side, in four rows the model samples, and its posts' faces under them."""
    c = Canvas()
    fill(c, 0, 0, 15, 0, STEEL[0], STEEL[1], 62)
    fill(c, 0, 1, 15, 1, SLATE[3], SLATE[2], 63)
    fill(c, 0, 2, 15, 2, STEEL[1], STEEL[2], 64)
    fill(c, 0, 3, 15, 3, STEEL[3], STEEL[4], 65)
    fill(c, 0, 4, 15, 15, STEEL[2], STEEL[3], 66)
    return c


def relay_link_face(working):
    """A Relay Link's six faces: dim while nothing crosses it, lit while it carries."""
    c = plate(salt=67)
    window(c, ramp=TEAL if working else None, salt=67)
    if working:
        glow_glyph(c, LINK_GLYPH, TEAL)
    else:
        dim_glyph(c, LINK_GLYPH)
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
    outline(c, SLATE[0])
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
    outline(c, SLATE[0])
    return c


# ---------------------------------------------------------------- storage

def mirrored(rows):
    """Whether a glyph reads the same mirrored left to right, as the storage glyphs below must."""
    return all(row == row[::-1] for row in rows)


# a Drive Rack is a 3D model (tools/data.py): eight bays in two columns of four, 6x2 px each and 2 px apart, recessed to
# a back panel 6 px deep; a drive in a bay is a cartridge 1 px behind the face. Bays in face coordinates (u, v).
RACK_BAY_U = (1, 9)
RACK_BAY_V = (1, 5, 9, 13)


def rack_front():
    """The rack's front: its rim, which is the block's edge, and the faces of its divider and shelves, a step darker
    for sitting 1 px further in; the bays themselves are holes in the model."""
    c = Canvas()
    fill(c, 0, 0, 15, 15, STEEL[2], STEEL[3], 311)
    ring(c, 0, STEEL[0], STEEL[1], 312)
    return c


def rack_inside():
    """The walls of the bays."""
    c = Canvas()
    fill(c, 0, 0, 15, 15, G1, G0, 314)
    return c


def rack_back_panel():
    """The back of the bays: a dark panel with a rail along each bay."""
    c = Canvas(fill=W1)
    for v0 in RACK_BAY_V:
        for u0 in RACK_BAY_U:
            c.rect(u0, v0, u0 + 5, v0, W2)
    return c


def drive_cartridge():
    """A drive seen in its bay: its face (u 0..6, v 0..2) a steel handle with a dark lens at one end, which the rack
    lights; its top, bottom and sides (the rest of the sheet) plain steel. Bays on the left show it mirrored, so the
    lenses sit at the rack's outer edges."""
    c = Canvas()
    fill(c, 0, 0, 15, 15, STEEL[2], STEEL[3], 313)
    c.rect(0, 0, 4, 0, STEEL[0])
    c.rect(0, 1, 4, 1, STEEL[1])
    c.rect(1, 0, 3, 0, STEEL[4])
    c.rect(5, 0, 5, 1, SLATE[1])
    return c


def rack_side():
    """Four vents, mirror-symmetric both ways."""
    c = plate(salt=32)
    for y in (4, 6, 9, 11):
        for x in range(3, 13):
            c.px(x, y, K4)
            c.px(x, y + 1, K0)
    return c


def rack_top():
    c = plate(salt=33)
    for x in (4, 6, 9, 11):
        for y in range(4, 12):
            c.px(x, y, STEEL[4])
    return c


# what a Storage Terminal's screen shows: stored kinds as little squares, by color, on a 3×3 grid of 2 px cells
TERMINAL_CELLS = [(4, 4, AMBER[4]), (7, 4, GREEN[4]), (10, 4, TEAL[4]), (4, 7, CORAL[4]), (7, 7, AZURE[4]),
                  (10, 7, AMETHYST[4]), (4, 10, GREEN[3]), (7, 10, AMBER[3])]


def storage_terminal_front(lit):
    """A screen of stored kinds, two by two pixels each, dark until its network has somewhere to keep things; lit, the
    last cell is a cursor."""
    c = plate(salt=34)
    window(c, ramp=AZURE if lit else None, salt=34)
    for x, y, color in TERMINAL_CELLS:
        c.rect(x, y, x + 1, y + 1, color if lit else DIM[2])
    c.rect(10, 10, 11, 11, AZURE[5] if lit else DIM[2])
    return c


TERMINAL_SIDE = [
    "##..##",
    "##..##",
    "......",
    "......",
    "##..##",
    "##..##",
]


def storage_terminal_side():
    c = plate(salt=35)
    engraved_glyph(c, TERMINAL_SIDE)
    return c


PORT_GLYPHS = {
    # an arrow into the network
    "import": [
        "...##...",
        "...##...",
        "...##...",
        ".######.",
        "..####..",
        "...##...",
        "........",
        "#..##..#",
    ],
    # an arrow out of it
    "export": [
        "#..##..#",
        "........",
        "...##...",
        "..####..",
        ".######.",
        "...##...",
        "...##...",
        "...##...",
    ],
    # a level kept at a mark
    "stock": [
        "#......#",
        "#......#",
        "#------#",
        "#++++++#",
        "#++++++#",
        "#++++++#",
        "#++++++#",
        "#..##..#",
    ],
    # a pane: what is behind it is open to the network
    "window": [
        "#..##..#",
        "#..##..#",
        "#..##..#",
        "#..##..#",
        "#..##..#",
        "#..##..#",
        "#..##..#",
        "#..##..#",
    ],
}


PORT_HUES = {"import": TEAL, "export": AMBER, "stock": GREEN, "window": AZURE}


def port_mode(mode, active):
    """What a Port does, drawn on its sides: dim while it waits, lit while it moves things."""
    c = plate(salt=36)
    hue = PORT_HUES[mode]
    window(c, ramp=hue if active else None, salt=36)
    if active:
        glow_glyph(c, PORT_GLYPHS[mode], hue)
    else:
        dim_glyph(c, PORT_GLYPHS[mode])
    return c


def port_back():
    """The face it joins its network through, as a rule: a dark jack, so its mode is its only colour."""
    c = plate(salt=37)
    main_input(c)
    return c


def port_front():
    """The face it holds against its inventory: an open slot."""
    c = plate(salt=38)
    edge(c, 4, 4, 11, 11, SLATE[3], SLATE[2], 381)
    fill(c, 5, 5, 10, 10, SLATE[1], mix(SLATE[1], SLATE[0], .5), 382)
    return c


PROCESSOR_GLYPH = [
    # what goes in up the middle, what comes back down both sides
    "...##...",
    "..####..",
    ".######.",
    "#..##..#",
    "#..##..#",
    "#..##..#",
    "#..##..#",
    "#......#",
]


def processor_side(working):
    """What a Processor does, drawn on its sides: dim while it waits, lit while its machine has work."""
    c = plate(salt=41)
    window(c, ramp=CORAL if working else None, salt=41)
    if working:
        glow_glyph(c, PROCESSOR_GLYPH, CORAL)
    else:
        dim_glyph(c, PROCESSOR_GLYPH)
    return c


def storage_link():
    """A 6 px band, slate with a copper core; the link model samples its arms from the bands and its joint from the
    middle."""
    c = Canvas()
    for i in range(16):
        for k, color in ((5, SLATE[1]), (6, SLATE[4]), (7, CORAL[2]), (8, CORAL[2]), (9, SLATE[4]), (10, SLATE[1])):
            c.px(i, k, color)
            c.px(k, i, color)
    edge(c, 5, 5, 10, 10, SLATE[1])
    fill(c, 6, 6, 9, 9, SLATE[3])
    c.rect(7, 7, 8, 8, CORAL[3])
    return c


def assembler_front(working):
    """A crafting grid on a screen: its nine cells dim while it waits, lit as it assembles."""
    c = plate(salt=39)
    window(c, ramp=AMBER if working else None, salt=39)
    tones = [AMBER[4], AMBER[3], AMBER[4], AMBER[3], AMBER[5], AMBER[3], AMBER[4], AMBER[3], AMBER[4]]
    for i in range(9):
        x, y = 4 + i % 3 * 3, 4 + i // 3 * 3
        c.rect(x, y, x + 1, y + 1, tones[i] if working else DIM[2])
    return c


def assembler_side():
    """A crafting grid of 2 px studs."""
    c = plate(salt=40)
    engraved_glyph(c, ASSEMBLER_SIDE)
    return c


# ---------------------------------------------------------------- power, in the coral of heat and making

FLAME = [
    "...##...",
    "...##...",
    "..#++#..",
    "..#++#..",
    ".#.++.#.",
    ".#++++#.",
    "#.++++.#",
    ".######.",
]
INGOTS = [
    "..####..",
    ".######.",
    "........",
    "..####..",
    ".######.",
]


def generator_front(working):
    """A firebox behind a grate: cold slate bars, or bars across a coral fire."""
    c = plate(salt=51)
    window(c, ramp=CORAL if working else None, salt=51)
    for y in (5, 8, 11):
        c.rect(4, y, 11, y, STEEL[5])
    if working:
        for y, tone in ((6, CORAL[5]), (7, CORAL[4]), (9, CORAL[4]), (10, CORAL[3])):
            c.rect(4, y, 11, y, tone)
    return c


def generator_side():
    c = plate(salt=52)
    engraved_glyph(c, FLAME, CORAL)
    return c


def machine_top(salt):
    """Vents across the top."""
    c = plate(salt=salt)
    for x in (4, 6, 9, 11):
        c.rect(x, 4, x, 11, SLATE[1])
        c.rect(x, 5, x, 10, SLATE[2])
    return c


def smelter_front(working):
    """A crucible seen through its window: empty and dark, or full and molten."""
    c = plate(salt=53)
    window(c, ramp=CORAL if working else None, salt=53)
    # the crucible: walls and a floor in steel
    c.rect(5, 6, 5, 11, STEEL[5])
    c.rect(10, 6, 10, 11, STEEL[5])
    c.rect(5, 11, 10, 11, STEEL[5])
    if working:
        c.rect(6, 8, 9, 10, CORAL[4])
        c.rect(6, 8, 9, 8, CORAL[5])
    return c


def smelter_side():
    c = plate(salt=54)
    engraved_glyph(c, INGOTS, CORAL)
    return c


def energy_cell(charge):
    """A gauge of four bars up the face, lit in coral from the bottom as the cell charges."""
    c = plate(salt=55)
    window(c, 5, 2, 10, 13, salt=55)
    for k in range(4):
        y = 11 - 3 * k
        tone = (CORAL[4], CORAL[5]) if k < charge else (SLATE[2], SLATE[3])
        c.rect(6, y - 1, 9, y, tone[0])
        c.rect(7, y - 1, 8, y - 1, tone[1])
    return c


def energy_cell_top():
    """Two terminals."""
    c = plate(salt=56)
    for x in (4, 10):
        edge(c, x, 6, x + 1, 9, SLATE[3], SLATE[2], 57)
        c.rect(x, 7, x + 1, 8, CORAL[3])
    return c


def power_cable():
    """A 4 px band, slate with a coral core, sampled by the cable model's arms and joint."""
    c = Canvas()
    for i in range(16):
        for k, color in ((6, SLATE[2]), (7, CORAL[3]), (8, CORAL[3]), (9, SLATE[2])):
            c.px(i, k, color)
            c.px(k, i, color)
    c.rect(6, 6, 9, 9, SLATE[3])
    c.rect(7, 7, 8, 8, CORAL[4])
    return c


ASSEMBLER_SIDE = [
    "##..##..##",
    "##..##..##",
    "..........",
    "..........",
    "##..##..##",
    "##..##..##",
    "..........",
    "..........",
    "##..##..##",
    "##..##..##",
]



STORAGE_DRIVE = [
    "................",
    "................",
    "...kKKKKKKKKk...",
    "...GGGGGGGGGG...",
    "...GWWWWWWWWG...",
    "...GWaWbWcWWG...",
    "...GWWWWWWWWG...",
    "...GWdWaWWWWG...",
    "...GWWWWWWWWG...",
    "...GWbWWWWWWG...",
    "...GWWWWWWWWG...",
    "...GGGGGGGGtG...",
    "...gGGGGGGGGg...",
    "....y.y.y.y.....",
    "................",
    "................",
]


def storage_drive():
    """A cartridge like the archive's, but its window shows the kinds it keeps, a dot each."""
    c = Canvas()
    c.stamp(0, 0, STORAGE_DRIVE, {"k": K3, "K": K1, "G": G2, "g": G1, "W": W1, "a": AMBER[4], "b": TEAL[4],
                                  "c": GREEN[4], "d": AMETHYST[4], "t": TEAL[4], "y": AMBER[3]})
    outline(c, SLATE[0])
    return c


# what a larger drive's window shows: its cells set closer, then in stacked sheets, then a block lit all through
DRIVE_WINDOWS = {
    "dense_drive": (TEAL, [
        "WWWWWWWW",
        "WaWbWaWb",
        "WWWWWWWW",
        "WbWaWbWa",
        "WWWWWWWW",
        "WaWbWaWb",
        "WWWWWWWW"]),
    "stacked_drive": (AZURE, [
        "WWWWWWWW",
        "aaaaaaaa",
        "WWWWWWWW",
        "bbbbbbbb",
        "WWWWWWWW",
        "aaaaaaaa",
        "WWWWWWWW"]),
    "deep_drive": (AMBER, [
        "aabbbbaa",
        "aabbbbaa",
        "abbccbba",
        "abbccbba",
        "abbccbba",
        "aabbbbaa",
        "aabbbbaa"]),
}


def larger_drive(name):
    """The same cartridge with a window of its own, a brighter band and a brighter foot for each size."""
    ramp, rows = DRIVE_WINDOWS[name]
    c = Canvas()
    c.stamp(0, 0, STORAGE_DRIVE, {"k": K3, "K": K1, "G": G2, "g": G1, "W": W1, "a": W1, "b": W1, "c": W1, "d": W1,
                                  "t": ramp[4], "y": ramp[3]})
    c.stamp(4, 4, rows, {"W": W1, "a": ramp[2], "b": ramp[4], "c": ramp[5]})
    outline(c, SLATE[0])
    return c


PORTABLE_DRIVE = [
    "................",
    "................",
    ".....kKKKKk.....",
    ".....k....k.....",
    "...kKKKKKKKKk...",
    "...GGGGGGGGGG...",
    "...GWWWWWWWWG...",
    "...GWaWbWcWWG...",
    "...GWWWWWWWWG...",
    "...GWdWaWWWWG...",
    "...GWWWWWWWWG...",
    "...GGGGGGGGGG...",
    "...GkGkGkGlGG...",
    "...gGGGGGGGGg...",
    "................",
    "................",
]


def portable_drive():
    """A cartridge with a handle and keys of its own: a drive that needs no rack to show what it holds."""
    c = Canvas()
    c.stamp(0, 0, PORTABLE_DRIVE, {"k": K3, "K": K1, "G": G2, "g": G1, "W": W1, "a": AMBER[4], "b": TEAL[4], "c": GREEN[4],
                                   "d": AMETHYST[4], "l": TEAL[4]})
    outline(c, SLATE[0])
    return c


WIRELESS_TERMINAL = [
    "..b.........b...",
    "..b.........b...",
    "...GGGGGGGGGG...",
    "...GWWWWWWWWG...",
    "...GWttWccWWG...",
    "...GWttWccWWG...",
    "...GWWWWWWWWG...",
    "...GWccWttWWG...",
    "...GWccWttWWG...",
    "...GWWWWWWWWG...",
    "...GGGGGGGGGG...",
    "...GkGkGkGkGG...",
    "...GGGGGGGGGG...",
    "...GkGkGkGlGG...",
    "...gGGGGGGGGg...",
    "................",
]


def wireless_terminal():
    """A Storage Terminal's screen in the hand, with two aerials: the kinds it shows are the network's, wherever that is."""
    c = Canvas()
    c.stamp(0, 0, WIRELESS_TERMINAL, {"G": G2, "g": G1, "W": W1, "t": AZURE[3], "c": AZURE[5], "k": K2, "l": AZURE[4],
                                      "b": K3})
    outline(c, SLATE[0])
    return c


def drive_dock_front(working):
    """Two bays above and two below, as drives wait in it and come out of it, and between them an arrow that lights."""
    c = plate(salt=71)
    window(c, ramp=AZURE if working else None, salt=71)
    for y in (4, 10):
        for x in (4, 9):
            c.rect(x, y, x + 2, y + 1, AZURE[3] if working else STEEL[5])
    c.rect(7, 6, 8, 7, AZURE[5] if working else SLATE[4])
    c.rect(6, 8, 9, 8, AZURE[5] if working else SLATE[4])
    c.rect(7, 9, 8, 9, AZURE[5] if working else SLATE[4])
    return c


DOCK_SIDE = [
    "######",
    "#....#",
    "######",
    "######",
    "#....#",
    "######",
]


def drive_dock_side():
    """Drives one over another."""
    c = plate(salt=72)
    engraved_glyph(c, DOCK_SIDE)
    return c


ACCESS_POINT = [
    "##....##",
    "#......#",
    "..####..",
    "..#++#..",
    "..#++#..",
    "..####..",
    "#......#",
    "##....##",
]


def access_point_front(lit_up):
    """A square within the corners of the space it reaches: dark until it has a network behind it."""
    c = plate(salt=73)
    window(c, ramp=AZURE if lit_up else None, salt=73)
    (glow_glyph if lit_up else dim_glyph)(*((c, ACCESS_POINT, AZURE) if lit_up else (c, ACCESS_POINT)))
    return c


ACCESS_POINT_SIDE = [
    "#....#",
    ".####.",
    ".#..#.",
    ".#..#.",
    ".####.",
    "#....#",
]


def access_point_side():
    c = plate(salt=74)
    engraved_glyph(c, ACCESS_POINT_SIDE)
    return c


LINK_DRIVER = [
    "...##...",
    "..####..",
    ".##..##.",
    "##....##",
    "...##...",
    "..####..",
    ".##..##.",
    "##....##",
]


def link_driver_front(working):
    """Chevrons driving upward, dark until power runs through them."""
    c = plate(salt=75)
    window(c, ramp=CORAL if working else None, salt=75)
    (glow_glyph if working else dim_glyph)(*((c, LINK_DRIVER, CORAL) if working else (c, LINK_DRIVER)))
    return c


LINK_DRIVER_SIDE = [
    "..##..",
    "..##..",
    "######",
    "######",
    "..##..",
    "..##..",
]


def link_driver_side():
    """A link's joint, in the coral of power."""
    c = plate(salt=76)
    engraved_glyph(c, LINK_DRIVER_SIDE, CORAL)
    return c


GUIDE = [
    "................",
    "..ooooooooooo...",
    ".oKCCCCCCCCCCo..",
    ".oKCCCCCCCCCCPo.",
    ".oKCCCCCCCCCCPo.",
    ".oKCCCCCCCCCCPo.",
    ".oKCCCCCCCCCCPo.",
    ".oKCCCCCCCCCCPo.",
    ".oKCCCCCCCCCCPo.",
    ".oKCCCCCCCCCCPo.",
    ".oKCCCCCCCCCCPo.",
    ".oKCCCCCCCCCCPo.",
    ".oKCCCCCCCCCCPo.",
    ".oKkkkkkkkkkkPo.",
    "..ooooooooooooo.",
    "................",
]


def guide():
    """The field notes: a teal-bound book with the Junction's sign on its cover."""
    c = Canvas()
    c.stamp(0, 0, GUIDE, {"o": TEAL[0], "K": TEAL[1], "C": TEAL[2], "k": TEAL[1], "P": K0})
    tone = {"#": TEAL[4], "-": TEAL[1], "+": TEAL[5]}
    for (x, y), ch in glyph_cells(GLYPHS["add"]).items():
        c.px(4 + x, 4 + y, tone[ch])
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


def block(faces, height, glow=None, ss=4):
    """A block `height` pixels high with top, left and right faces, outlined like the name. `glow` maps a face to the
    pixels of it that stay unshaded, by place: picked by color, a lit part would break wherever a surface mixes two
    tones."""
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
        lit = (glow or {}).get(name, ())
        for y in range(tex.height):
            for x in range(tex.width):
                if (x, y) not in lit:
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
    x0, y0 = centered(GLYPHS["add"])
    glyph = {(x0 + x, y0 + y) for x, y in glyph_cells(GLYPHS["add"])}
    return block({"top": side.im, "left": side.im, "right": front.im}, height, {"right": glyph})


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

# ---------------------------------------------------------------- screen icons

# Icons for buttons on the mod's screens, on vanilla's button: a light glyph in mostly two-pixel strokes, about 10 by
# 11 in the middle of 16 by 16, with a slate shadow under every pixel that has nothing below it, as AE2 draws its own.
ICON_LIGHT = rgb("f1f3f5")
ICON_SHADOW = SLATE[1]


def icon_ring(cx, cy, r, gap=None):
    """A ring two pixels thick around (cx, cy), leaving out the angles in gap (degrees, counterclockwise from the right)."""
    cells = set()
    for y in range(16):
        for x in range(16):
            if r - 1 <= math.hypot(x + .5 - cx, y + .5 - cy) < r + 1:
                a = math.degrees(math.atan2(cy - (y + .5), x + .5 - cx)) % 360
                if not (gap and gap[0] <= a <= gap[1]):
                    cells.add((x, y))
    return cells


def icon_cells(text):
    return {(x, y) for y, row in enumerate(text.strip("\n").split("\n")) for x, ch in enumerate(row) if ch == "#"}


ICONS = {
    # an eye, open in the dark
    "suit_night_vision": icon_cells("""
................
................
................
................
.....######.....
...##......##...
..##...##...##..
.##...####...##.
..##...##...##..
...##......##...
.....######.....
"""),
    # what takes the blow
    "suit_shield": icon_cells("""
................
................
...##########...
...##......##...
...##......##...
...##......##...
...##......##...
....##....##....
.....##..##.....
......####......
.......##.......
"""),
    # a stair, climbed without a jump
    "suit_step_assist": icon_cells("""
................
................
..........####..
..........####..
..........##....
..........##....
......######....
......######....
......##........
......##........
..######........
..######........
"""),
    # a long arm from the body
    "suit_reach": icon_cells("""
................
................
................
................
.##........##...
.##.........##..
.##..##########.
.##..##########.
.##.........##..
.##........##...
"""),
    # a pick
    "suit_vein_mining": icon_cells("""
................
....#######.....
...........##...
...........###..
..........####..
.........##.##..
........##..##..
.......##...##..
......##........
.....##.........
....##..........
...##...........
"""),
    # a second tier, over the first
    "suit_flight": icon_cells("""
................
................
.......##.......
......####......
.....##..##.....
....##....##....
...##......##...
................
.......##.......
......####......
.....##..##.....
....##....##....
...##......##...
"""),
    # the Refinery's toolbars
    "refinery_start": icon_cells("""
................
................
................
....##..........
....####........
....######......
....########....
....##########..
....##########..
....########....
....######......
....####........
....##..........
"""),
    # training a little reader on each cut, and the curve it leaves
    "refinery_measure": icon_cells("""
................
................
..#.............
..#.............
..#.............
..##............
..#.##..........
..#...##........
..#.....###.....
..#........###..
..#.............
..#############.
"""),
    "refinery_near": icon_cells("""
................
................
.......##.......
.....#####......
..##.##.##.##...
..##.##.##.##...
.####.####.###..
.##..##..##..#..
.##..##..##..#..
.####.####.###..
..##.##.##.##...
..##.##.##.##...
.....#####......
.......##.......
"""),
    "refinery_short": icon_cells("""
................
................
................
................
................
................
..#.###.#.......
..#.###.#.......
................
................
"""),
    "refinery_symbols": icon_cells("""
................
................
...#..#...##....
...#..#..#..#...
..######....#...
...#..#....#....
...#..#...#.....
..######........
...#..#...#.....
...#..#.........
"""),
    "refinery_words": icon_cells("""
................
................
................
................
..###.#.#####...
..#.#.#.#...#...
..###.#.#####...
..#.#.#.#.......
..###.#.#.......
................
"""),
    "refinery_repeats": icon_cells("""
................
................
..##########....
................
..##########....
................
..##########....
................
..##########....
................
"""),
    "refinery_endings": icon_cells("""
................
................
..########......
................
..######.##.....
................
..###.##........
................
..#######.##....
................
"""),
    "refinery_sure": icon_cells("""
................
................
...###..........
..#...#.........
......#.........
.....#..........
....#...........
....#...........
................
....#.......##..
............##..
"""),
    "refinery_program": icon_cells("""
................
................
...##.....##....
..#.........#...
..#.........#...
..#.........#...
.#...........#..
..#.........#...
..#.........#...
..#.........#...
...##.....##....
"""),
    "refinery_share": icon_cells("""
................
................
....######......
..##......##....
.#....##....#...
.#....##....#...
#.....####...#..
#.....####...#..
.#..........#...
.#..........#...
..##......##....
....######......
"""),
    # the last of a run on one kind of writing, at a lower rate
    "refinery_anneal": icon_cells("""
................
................
.####...........
.....##.........
.......##.......
.........##.....
...........##...
.............##.
................
.##############.
"""),
    "refinery_after": icon_ring(8, 8, 5.5) | icon_cells("""
................
................
................
................
.......#........
.......#........
.......#........
.......#####....
"""),
    "refinery_remove": icon_cells("""
................
................
..##......##....
...##....##.....
....##..##......
.....####.......
......##........
.....####.......
....##..##......
...##....##.....
..##......##....
"""),
    # how a run shares out its work: all on one device
    "split_one": icon_cells("""
................
................
................
...##########...
...#........#...
...#........#...
...#........#...
...#........#...
...#........#...
...#........#...
...#........#...
...#........#...
...##########...
"""),
    # the whole model on each device
    "split_data": icon_cells("""
................
................
...####..####...
...#..#..#..#...
...#..#..#..#...
...####..####...
................
................
................
...####..####...
...#..#..#..#...
...#..#..#..#...
...####..####...
"""),
    # each layer cut across the devices
    "split_tensor": icon_cells("""
................
................
................
...##########...
...#..#..#..#...
...#..#..#..#...
...#..#..#..#...
...#..#..#..#...
...#..#..#..#...
...#..#..#..#...
...#..#..#..#...
...#..#..#..#...
...##########...
"""),
    # the layers one after another along the devices
    "split_pipeline": icon_cells("""
................
..####..........
..#..#..........
..####..........
.....####.......
.....#..#.......
.....####.......
........####....
........#..#....
........####....
...........####.
...........#..#.
...........####.
"""),
    # back to the start, against the clock
    "reset": icon_ring(8, 8.5, 4.5, gap=(100, 160)) | icon_cells("""
................
................
.......#........
......##........
.....###........
......##........
.......#........
"""),
    "check": icon_ring(6.5, 6.5, 3.5) | icon_cells("""
................
................
................
................
................
................
................
................
................
.........##.....
..........##....
...........##...
............#...
"""),
    # the rate over a run: up, held, and down
    "schedule": icon_cells("""
................
................
.....#######....
....#.......#...
....#.......#...
...#.........#..
...#.........#..
..#...........#.
..#...........#.
.##############.
................
"""),
    # a rate rising to its full size
    "warmup": icon_cells("""
................
................
..........#####.
.........#......
........#.......
.......#........
......#.........
.....#..........
....#...........
..###...........
................
"""),
    # a run with an end to it
    "length": icon_cells("""
................
................
.............##.
.............##.
.............##.
.##..........##.
.##############.
.##..........##.
.............##.
.............##.
.............##.
"""),
    # numbers on a grid: a few wide bits
    "precision": icon_cells("""
................
................
..##..##..##....
..##..##..##....
................
..##..##..##....
..##..##..##....
................
..##..##........
..##..##........
................
"""),
    # a step along two directions at right angles
    "moves": icon_cells("""
................
................
...#............
..###...........
...#............
...#............
...#............
...#............
...#........#...
...##########...
............#...
"""),
    # rows filled with pieces of different lengths
    "packing": icon_cells("""
................
................
..#####.#####...
..#####.#####...
................
..##.#########..
..##.#########..
................
..#######.###...
..#######.###...
................
"""),
    # a step down the slope, as far as the rate says
    "learning_rate": icon_cells("""
................
................
................
...##...........
...##...........
...####.........
.....##.........
.....####.......
.......##.......
.......####.....
.........##.....
.......######...
........####....
.........##.....
"""),
    "batch": icon_cells("""
................
................
................
.......#######..
.......#.....#..
.....#######.#..
.....#.....#.#..
...#######.#.#..
...#.....#.###..
...#.....#.#....
...#.....###....
...#.....#......
...#######......
"""),
    # a gradient stopped at its ceiling
    "clip": icon_cells("""
................
................
................
...##########...
...##########...
................
.......##.......
......####......
.....######.....
....########....
.......##.......
.......##.......
.......##.......
"""),
    "home": icon_cells("""
................
................
................
.......##.......
......####......
.....######.....
....########....
...##########...
....##....##....
....##....##....
....##.##.##....
....##.##.##....
....########....
"""),
    # something to say: a bubble with its tail
    "tell": icon_cells("""
................
................
................
....########....
...##########...
...##......##...
...##......##...
...##......##...
...##########...
....########....
....###.........
....##..........
....#...........
"""),
    "run": icon_cells("""
................
................
................
......#.........
......##........
......###.......
......####......
......#####.....
......####......
......###.......
......##........
......#.........
"""),
    # out of the drive
    "dock_empty": icon_cells("""
................
...##########...
...#........#...
...##########...
................
.......##.......
.......##.......
.......##.......
.......##.......
....########....
.....######.....
......####......
.......##.......
................
................
................
"""),
    # into the drive
    "dock_fill": icon_cells("""
................
................
.......##.......
.......##.......
.......##.......
.......##.......
....########....
.....######.....
......####......
.......##.......
................
...##########...
...#........#...
...##########...
................
................
"""),
    "stop": icon_cells("""
................
................
................
................
....########....
....########....
....########....
....########....
....########....
....########....
....########....
....########....
"""),
    # hold a program where it is
    "pause": icon_cells("""
................
................
................
................
....###..###....
....###..###....
....###..###....
....###..###....
....###..###....
....###..###....
....###..###....
....###..###....
"""),
    # one step, and hold again
    "step": icon_cells("""
................
................
................
................
...#......##....
...##.....##....
...###....##....
...####...##....
...###....##....
...##.....##....
...#......##....
"""),
    # give the program the whole window
    "expand": icon_cells("""
................
................
................
...####..####...
...##......##...
...#........#...
...#........#...
................
...#........#...
...#........#...
...##......##...
...####..####...
"""),
    # a body anyone may use
    "open": icon_cells("""
................
................
.........####...
........##..##..
........#....#..
........#....#..
...#########.#..
...#########....
...#########....
...#########....
...#########....
...#########....
"""),
    # a body its owner's team may use
    "team": icon_cells("""
................
................
....##....##....
....##....##....
................
...####..####...
...####..####...
...####..####...
...####..####...
................
"""),
    # a body only its owner may use
    "locked": icon_cells("""
................
................
.....######.....
....##....##....
....#......#....
....#......#....
...##########...
...##########...
...##########...
...##########...
...##########...
...##########...
"""),
    # a Port's programs: the lines on a card
    "program": icon_cells("""
................
................
...##########...
...##########...
................
...#######......
...#######......
................
...##########...
...##########...
................
...#####........
...#####........
"""),
    # a Port's filter: narrowing down
    "filter": icon_cells("""
................
................
...##########...
...##########...
....########....
.....######.....
......####......
......####......
......####......
......####......
......####......
"""),
    # the pieces of programs, laid out to pick from
    "palette": icon_cells("""
................
................
................
................
...####..####...
...####..####...
...####..####...
................
...####..####...
...####..####...
...####..####...
"""),
    "speed_watch": icon_cells("""
................
................
................
................
.....######.....
...##......##...
..#....##....#..
.#....####....#.
..#....##....#..
...##......##...
.....######.....
"""),
    "speed_normal": icon_cells("""
................
................
................
......#.........
......##........
......###.......
......####......
......#####.....
......####......
......###.......
......##........
......#.........
"""),
    "speed_max": icon_cells("""
................
................
................
...#.....#......
...##....##.....
...###...###....
...####..####...
...#####.#####..
...####..####...
...###...###....
...##....##.....
...#.....#......
"""),
    # a Terminal's tabs: the network on a map, a reach with something at its middle
    "terminal_map": icon_ring(8, 8.5, 5) | icon_cells("""
................
................
................
................
................
................
................
................
.......##.......
.......##.......
"""),
    # what the base holds and does: a rack of two bays
    "terminal_base": icon_cells("""
................
................
................
...##########...
...#........#...
...#.##.....#...
...##########...
...##########...
...#........#...
...#.##.....#...
...##########...
"""),
    # the agents: a frame with two eyes
    "terminal_agents": icon_cells("""
................
................
.......##.......
.......##.......
...##########...
...#........#...
...#.##..##.#...
...#.##..##.#...
...#........#...
...##########...
.....##..##.....
.....##..##.....
"""),
    # writing with the served model
    "terminal_write": icon_cells("""
................
................
................
...##########...
................
...##########...
................
...##########...
................
...######.......
.........##.....
.........##.....
"""),
    # frame all of it
    "terminal_fit": icon_cells("""
................
................
...####..####...
...##......##...
...#........#...
................
.....######.....
.....######.....
................
...#........#...
...##......##...
...####..####...
"""),
    # another dimension: one square behind another
    "terminal_dimension": icon_cells("""
................
................
................
...#######......
...#.....#......
...#.#######....
...#.#...#.#....
...#.#...#.#....
...#######.#....
.....#.....#....
.....#######....
"""),
}


def screen_icon(cells):
    c = Canvas()
    for x, y in cells:
        c.px(x, y, ICON_LIGHT)
    for x, y in cells:
        if (x, y + 1) not in cells:
            c.px(x, y + 1, ICON_SHADOW)
    return c


# ---------------------------------------------------------------- energy and compute

DROPLET = [
    "...##...",
    "...##...",
    "..#++#..",
    ".#++++#.",
    ".#++++#.",
    "..####..",
]


def boiler_front(working):
    """A firebox under a water gauge: the gauge is always there, the fire only while it burns."""
    c = plate(salt=71)
    edge(c, 5, 3, 10, 5, SLATE[3], SLATE[2], 71)
    c.rect(6, 4, 9, 4, AZURE[3])
    window(c, 4, 7, 11, 12, ramp=CORAL if working else None, salt=72)
    for y in (9, 11):
        c.rect(5, y, 10, y, STEEL[5])
    if working:
        for y, tone in ((8, CORAL[5]), (10, CORAL[4])):
            c.rect(5, y, 10, y, tone)
    return c


def boiler_side():
    c = plate(salt=73)
    engraved_glyph(c, DROPLET, AZURE)
    return c


def boiler_top():
    """The port the steam goes up through."""
    c = plate(salt=74)
    edge(c, 4, 4, 11, 11, SLATE[3], SLATE[2], 74)
    fill(c, 5, 5, 10, 10, SLATE[1], mix(SLATE[1], SLATE[0], .5), 75)
    return c


def turbine_side(step=None):
    """The rotor seen through a slot, its vanes running past while it spins: step by step they rise, mirrored about the shaft."""
    c = plate(salt=76)
    window(c, 3, 3, 12, 12, ramp=AZURE if step is not None else None, salt=76)
    c.rect(7, 4, 8, 11, STEEL[5])
    for k in range(3):
        y = 4 + (k * 3 + (step or 0)) % 8
        tone = AZURE[5] if step is not None else SLATE[4]
        c.rect(4, y, 6, y, tone)
        c.rect(9, y, 11, y, tone)
    return c


def turbine_top():
    c = plate(salt=77)
    for x in (5, 7, 8, 10):
        c.rect(x, 4, x, 11, SLATE[2])
    return c


def solar_top(lit_up):
    """Sixteen cells in a light frame, deep blue, brighter under a sky that feeds them."""
    c = Canvas()
    ring(c, 0, STEEL[1], STEEL[2], 78)
    fill(c, 1, 1, 14, 14, SLATE[3], None, 78)
    deep, shine = (AZURE[2], AZURE[3]) if lit_up else (AZURE[0], AZURE[1])
    for y0 in (1, 5, 8, 12):
        for x0 in (1, 5, 8, 12):
            h, w = 3 if y0 in (1, 12) else 2, 3 if x0 in (1, 12) else 2
            c.rect(x0, y0, x0 + w - 1, y0 + h - 1, deep)
            c.px(x0 + (0 if x0 < 8 else w - 1), y0, shine)
    return c


def solar_side():
    """The edge of the frame, in the top six rows of a face."""
    c = plate(salt=79)
    c.rect(0, 10, 15, 15, STEEL[3])
    c.rect(0, 10, 15, 10, STEEL[1])
    c.rect(0, 15, 15, 15, STEEL[5])
    return c


def capacitor_bank(charge):
    """Two gauges of four bars up the face, lit from the bottom as it charges."""
    c = plate(salt=80)
    for x0 in (3, 9):
        window(c, x0, 2, x0 + 3, 13, salt=80)
        for k in range(4):
            y = 11 - 3 * k
            tone = (CORAL[4], CORAL[5]) if k < charge else (SLATE[2], SLATE[3])
            c.rect(x0 + 1, y - 1, x0 + 2, y, tone[0])
            c.rect(x0 + 1, y - 1, x0 + 2, y - 1, tone[1])
    return c


def capacitor_bank_top():
    """Four terminals."""
    c = plate(salt=81)
    for x in (4, 10):
        for y in (4, 10):
            edge(c, x, y, x + 1, y + 1, SLATE[3], SLATE[2], 81)
            c.px(x, y, CORAL[3])
            c.px(x + 1, y, CORAL[3])
    return c


def band(half, tones, joint):
    """A cable's texture: a band across and a band down, tones from its edge in to its middle, and its joint where they cross."""
    c = Canvas()
    lo = 8 - half
    across = list(tones) + list(reversed(tones))
    for i in range(16):
        for k, color in enumerate(across):
            c.px(i, lo + k, color)
            c.px(lo + k, i, color)
    for k, color in enumerate(joint):
        c.rect(lo + k, lo + k, 15 - lo - k, 15 - lo - k, color)
    return c


def heavy_cable():
    return band(3, (SLATE[2], CORAL[2], CORAL[3]), (SLATE[3], CORAL[3], CORAL[4]))


def bus_bar():
    return band(4, (STEEL[4], CORAL[3], CORAL[4], CORAL[5]), (STEEL[3], STEEL[5], CORAL[4], CORAL[5]))


def interconnect():
    return band(1, (TEAL[4],), (TEAL[5],))


def coolant_pipe(step=None):
    """A band of steel round a line of coolant: still, or running out from the middle in both directions, as pulses."""
    c = band(3, (STEEL[4], SLATE[3], AZURE[1]), (STEEL[3], SLATE[3], AZURE[2]))
    if step is not None:
        for i in range(16):
            for k in (7, 8):
                pulse = AZURE[4] if (i - step) % 4 == 0 or (15 - i - step) % 4 == 0 else AZURE[2]
                if i < 5 or i > 10:
                    c.px(i, k, pulse)
                    c.px(k, i, pulse)
    return c


def accelerator_node(ramp):
    """The bare chip at a finer process node: its die in the node's hue."""
    c = plate(salt=6)
    c.stamp(2, 2, CHIP, {"l": STEEL[6], "d": SLATE[2], "h": G3, "G": ramp[1], "t": ramp[3]})
    c.rect(7, 7, 8, 7, ramp[5])
    c.rect(7, 8, 8, 8, ramp[4])
    return c


def module_front():
    """A Rack Module's rim, in graphite: the block's edge, its divider and shelves; the bays are holes in the model."""
    c = Canvas()
    fill(c, 0, 0, 15, 15, G2, G3, 411)
    ring(c, 0, STEEL[2], STEEL[3], 412)
    return c


def module_side():
    """Ducts the height of a module, so a stacked rack reads as one column."""
    c = plate(salt=413, centre=(G2, G3))
    for x in (4, 7, 8, 11):
        c.rect(x, 0, x, 15, G1)
    return c


def module_top():
    c = plate(salt=414, centre=(G2, G3))
    for x in (5, 10):
        c.rect(x, 4, x, 11, SLATE[1])
    return c


def device_cartridge():
    """A device seen in its bay: a dark card with a light rail over the strip the module lights, plain on its other faces."""
    c = Canvas()
    fill(c, 0, 0, 15, 15, G1, G0, 415)
    c.rect(0, 0, 5, 0, STEEL[4])
    c.rect(0, 1, 5, 1, SLATE[1])
    return c


def switch_front(ramp, ports, working):
    """A display for what the cluster runs, the switch's front drawn on it, over a row or two of ports lit while it works."""
    c = plate(salt=416)
    window(c, 2, 2, 13, 10, salt=416)
    for y in ports:
        for x in (3, 5, 7, 8, 10, 12):
            c.px(x, y, ramp[4] if working else SLATE[3])
    return c


def switch_side(ramp):
    c = plate(salt=417)
    for y in (5, 7, 8, 10):
        c.rect(4, y, 11, y, SLATE[2])
    c.rect(7, 4, 8, 4, ramp[3])
    c.rect(7, 11, 8, 11, ramp[3])
    return c


def chiller_front(working):
    """A coolant port in a grille: the port is where a cooling line leaves it; it runs blue while the Chiller works."""
    c = plate(salt=418)
    window(c, 3, 3, 12, 12, ramp=AZURE if working else None, salt=418)
    for k in (5, 10):
        c.rect(4, k, 11, k, STEEL[5] if not working else AZURE[4])
    edge(c, 6, 6, 9, 9, SLATE[4], SLATE[3], 419)
    c.rect(7, 7, 8, 8, AZURE[5] if working else SLATE[1])
    return c


def chiller_side():
    c = plate(salt=420)
    for y in (4, 6, 9, 11):
        c.rect(3, y, 12, y, K4)
        c.rect(3, y + 1, 12, y + 1, K0)
    return c


def console_front(working):
    """A long screen ruled with a column for each rack, lit when the hall stands."""
    c = plate(salt=421)
    window(c, 2, 2, 13, 13, ramp=TEAL if working else None, salt=421)
    heights = (5, 8, 3, 9, 9, 3, 8, 5)
    for i, h in enumerate(heights):
        c.rect(4 + i, 12 - h, 4 + i, 12, TEAL[5] if working else SLATE[3])
    return c


def wall_front():
    """Dark glass, ruled so its panels line up; what it shows is drawn on it."""
    c = Canvas()
    fill(c, 0, 0, 15, 15, W0, mix(W0, W1, .4), 422)
    return c


def wall_side():
    c = plate(salt=423)
    c.rect(7, 3, 8, 12, TEAL[2])
    return c


def reactor_core_front(working):
    """A gauge of eight marks up a dark window, its middle two the band, the rest in steel; lit in amber while it runs."""
    c = plate(salt=424, centre=(STEEL[5], STEEL[6]))
    window(c, 3, 2, 12, 13, ramp=AMBER if working else None, salt=424)
    for k in range(8):
        y = 12 - k
        on = k in (3, 4)
        tone = (AMBER[5] if on else AMBER[3]) if working else (SLATE[4] if on else SLATE[3])
        c.rect(5, y, 10, y, tone)
    return c


def reactor_casing():
    """Heavy plate, dark for a machine: a panel with its corners bolted."""
    c = plate(salt=425, centre=(STEEL[5], STEEL[6]))
    edge(c, 4, 4, 11, 11, STEEL[6], STEEL[7], 425)
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        c.px(x, y, SLATE[2])
    return c


def reactor_glass():
    """A frame of the casing's steel round thick glass, with one rule of its tone across the corner of each pane."""
    c = Canvas()
    ring(c, 0, STEEL[5], STEEL[6], 426)
    ring(c, 1, STEEL[6], STEEL[7], 427)
    glass = (150, 190, 205, 90)
    fill(c, 2, 2, 13, 13, glass)
    for k in range(2, 5):
        c.px(k + 1, 2 + 4 - k, (210, 235, 240, 140))
        c.px(15 - k - 1, 2 + 4 - k, (210, 235, 240, 140))
    return c


def fuel_rod(burning):
    """Four rods in a frame: dark, or with their middles running amber."""
    c = plate(salt=428, centre=(SLATE[1], SLATE[2]))
    for x in (4, 6, 9, 11):
        c.rect(x, 3, x, 12, AMBER[4] if burning else STEEL[6])
        c.rect(x, 3, x, 3, STEEL[3])
        c.rect(x, 12, x, 12, STEEL[3])
    return c


def wafer_node(ramp):
    """A finer wafer: the same disc, its dies in the node's hue."""
    c = wafer()
    swap = {rgb("8fa3b8"): ramp[2], rgb("a9bccf"): ramp[3], rgb("6f8399"): ramp[1], rgb("e6f3ff"): ramp[5]}
    for y in range(16):
        for x in range(16):
            old = c.get(x, y)
            if old in swap:
                c.px(x, y, swap[old])
    return c


def reactor_fuel():
    """A rod in its sleeve."""
    c = Canvas()
    c.stamp(5, 1, ["..oo..", ".oSSo.", ".oSso.", ".oaao.", ".oaao.", ".oAao.", ".oAAo.", ".oAAo.", ".oaAo.", ".oaao.", ".oaao.", ".oSso.", ".oSSo.", "..oo.."],
            {"o": INK, "S": STEEL[3], "s": STEEL[5], "a": AMBER[3], "A": AMBER[5]})
    return c


def build():
    for op in GLYPHS:
        front, side = operator_faces(op)
        front.save(f"block/{op}_front.png")
        side.save(f"block/{op}_side.png")
        if ARITY.get(op, 1) == 0:
            blank_back(op).save(f"block/{op}_back.png")
    machine_back().save("block/machine_back.png")
    for name in UNIT_GLYPHS:
        unit_face(name, False).save(f"block/{name}.png")
        lit(f"block/{name}_on.png", [unit_face(name, True)], [unit_face(name, False)])

    core_front().save("block/model_core_front.png")
    core_side().save("block/model_core_side.png")
    lit("block/model_core_front_on.png", [core_front(i) for i in range(2)], [core_front()], 4)
    lit("block/model_core_side_on.png", [core_side(i) for i in range(5)], [core_side()], 3)

    accelerator().save("block/accelerator.png")
    lit("block/accelerator_hot.png", [accelerator_hot()], [accelerator()])
    casing().save("block/tensor_casing.png")

    server_front().save("block/model_server_front.png")
    server_front(crystal=True).save("block/model_server_front_loaded.png")
    lit("block/model_server_front_on.png", [server_front(True, i) for i in range(4)], [server_front(crystal=True)], 3)
    server_side().save("block/model_server_side.png")
    server_top().save("block/model_server_top.png")
    reader_front().save("block/reader_front.png")
    lit("block/reader_front_on.png", [reader_front(i) for i in range(8)], [reader_front()], 4)
    reader_top().save("block/reader_top.png")
    reader_side().save("block/reader_side.png")
    relay().save("block/relay.png")
    relay(False).save("block/relay_dark.png")
    relay_lights().save("block/relay_lights.png")
    relay_lattice().save("block/relay_lattice.png")
    gate_top(False).save("block/relay_gate_top.png")
    lit("block/relay_gate_top_on.png", [gate_top(True)], [gate_top(False)])
    gate_side().save("block/relay_gate_side.png")
    relay_link_face(False).save("block/relay_link.png")
    lit("block/relay_link_on.png", [relay_link_face(True)], [relay_link_face(False)])
    bench_top().save("block/bench_top.png")
    bench_side().save("block/bench_side.png")
    fabricator_front().save("block/fabricator_front.png")
    lit("block/fabricator_front_on.png", [fabricator_front(i) for i in range(11)], [fabricator_front()], 2)
    fabricator_side().save("block/fabricator_side.png")
    fabricator_top().save("block/fabricator_top.png")
    forge_front().save("block/bpe_forge_front.png")
    lit("block/bpe_forge_front_on.png", [forge_front(i) for i in range(len(FORGE_GAPS) + 1)], [forge_front()], 3)
    forge_side().save("block/bpe_forge_side.png")
    forge_top().save("block/bpe_forge_top.png")
    rack_front().save("block/drive_rack_front.png")
    rack_inside().save("block/drive_rack_inside.png")
    rack_back_panel().save("block/drive_rack_back_panel.png")
    drive_cartridge().save("block/drive_cartridge.png")
    rack_side().save("block/drive_rack_side.png")
    rack_top().save("block/drive_rack_top.png")
    storage_terminal_front(False).save("block/storage_terminal_front.png")
    lit("block/storage_terminal_front_on.png", [storage_terminal_front(True)], [storage_terminal_front(False)])
    storage_terminal_side().save("block/storage_terminal_side.png")
    for mode in PORT_GLYPHS:
        port_mode(mode, False).save(f"block/port_{mode}.png")
        lit(f"block/port_{mode}_on.png", [port_mode(mode, True)], [port_mode(mode, False)])
    port_back().save("block/port_back.png")
    port_front().save("block/port_front.png")
    storage_link().save("block/storage_link.png")
    for rows in [TERMINAL_SIDE, ASSEMBLER_SIDE, GATE_WELL, LINK_GLYPH, *PORT_GLYPHS.values()]:
        assert mirrored(rows) and len(rows[0]) % 2 == 0 and len(rows) % 2 == 0, rows
    # a glyph symmetric along an axis has to be even along it, or it sits half a pixel off the tile's centre
    assert mirrored(FLIGHT_FRAME), FLIGHT_FRAME
    for rows in (EXOSUIT, EXOSUIT_FRONT, EXOSUIT_BACK, EXOSUIT_ARM_OUT, EXOSUIT_ARM):
        assert mirrored(rows), rows
    for rows in [*GLYPHS.values(), *UNIT_GLYPHS.values(), SERVER_CRYSTAL, HOURGLASS, SPROUT, TERMINAL_SIDE, ASSEMBLER_SIDE, SURVEY_PROBE, *PORT_GLYPHS.values()]:
        assert not (mirrored(rows) and len(rows[0]) % 2), rows
        assert not (rows == rows[::-1] and len(rows) % 2), rows
    assert [u + 5 for u in RACK_BAY_U] == [15 - u for u in reversed(RACK_BAY_U)]
    assert [v + 1 for v in RACK_BAY_V] == [15 - v for v in reversed(RACK_BAY_V)]
    storage_drive().save("item/storage_drive.png")
    for name in DRIVE_WINDOWS:
        larger_drive(name).save(f"item/{name}.png")
    portable_drive().save("item/portable_drive.png")
    wireless_terminal().save("item/wireless_terminal.png")
    for rows in [DOCK_SIDE, ACCESS_POINT, ACCESS_POINT_SIDE, LINK_DRIVER, LINK_DRIVER_SIDE]:
        assert mirrored(rows) and len(rows[0]) % 2 == 0 and len(rows) % 2 == 0, rows
    drive_dock_front(False).save("block/drive_dock_front.png")
    lit("block/drive_dock_front_on.png", [drive_dock_front(True)], [drive_dock_front(False)])
    drive_dock_side().save("block/drive_dock_side.png")
    access_point_front(False).save("block/access_point_front.png")
    lit("block/access_point_front_on.png", [access_point_front(True)], [access_point_front(False)])
    access_point_side().save("block/access_point_side.png")
    link_driver_front(False).save("block/link_driver_front.png")
    lit("block/link_driver_front_on.png", [link_driver_front(True)], [link_driver_front(False)])
    link_driver_side().save("block/link_driver_side.png")
    # every face mirrors left to right but those that picture something one-sided or show data, the cartridge the
    # rack mirrors per column, and the relay's sheet of parts
    one_sided = {f"block/{op}_{face}.png" for op in ONE_SIDED for face in ("front", "side", "back")}
    data = {"block/forecaster_front", "block/forecaster_front_on", "block/forecaster_ii_front", "block/forecaster_ii_front_on", "block/forecaster_iii_front", "block/forecaster_iii_front_on",
            "block/surveyor_front", "block/surveyor_front_on",
            "block/watch_post_front", "block/watch_post_front_on",
            *[f"block/{name}_{face}" for name in REFINERY_GLYPHS for face in ("front", "front_on")],
            "block/reader_front", "block/reader_front_on",
            "block/bpe_forge_front_on", "block/storage_terminal_front_on", "block/model_core_side_on",
            "block/model_server_front_on", "block/drive_cartridge", "block/relay", "block/relay_dark", "block/relay_lights"}
    for rel, im in SAVED.items():
        if str(rel).startswith("block/") and im.size == (16, 16) and rel not in one_sided and rel[:-4] not in data:
            px = im.load()
            assert all(px[x, y] == px[15 - x, y] for y in range(16) for x in range(8)), f"{rel} is not mirrored"
    assembler_front(False).save("block/assembler_front.png")
    lit("block/assembler_front_on.png", [assembler_front(True)], [assembler_front(False)])
    assembler_side().save("block/assembler_side.png")
    processor_side(False).save("block/processor_side.png")
    lit("block/processor_side_on.png", [processor_side(True)], [processor_side(False)])
    generator_front(False).save("block/combustion_generator_front.png")
    lit("block/combustion_generator_front_on.png", [generator_front(True)], [generator_front(False)])
    generator_side().save("block/combustion_generator_side.png")
    machine_top(58).save("block/combustion_generator_top.png")
    flight_frame().save("item/flight_frame.png")
    refinery_textures()
    flight_frame_worn().save("entity/equipment/humanoid/flight_frame.png")
    flight_frame_model().save("entity/flight_frame.png")
    exosuit().save("item/exosuit.png")
    exosuit_worn().save("entity/equipment/humanoid/exosuit.png")
    assert mirrored(WAYFINDER_CASE), WAYFINDER_CASE
    for k in range(32):
        wayfinder(k).save(f"item/wayfinder_{k:02d}.png")
    surveyor_front().save("block/surveyor_front.png")
    lit("block/surveyor_front_on.png", [surveyor_front(i) for i in range(8)], [surveyor_front()], 3)
    surveyor_side().save("block/surveyor_side.png")
    watch_post_front().save("block/watch_post_front.png")
    lit("block/watch_post_front_on.png", [watch_post_front(i) for i in range(2)], [watch_post_front()], 6)
    watch_post_side().save("block/watch_post_side.png")
    machine_top(63).save("block/watch_post_top.png")
    machine_top(61).save("block/surveyor_top.png")
    smelter_front(False).save("block/smelter_front.png")
    lit("block/smelter_front_on.png", [smelter_front(True)], [smelter_front(False)])
    smelter_side().save("block/smelter_side.png")
    machine_top(59).save("block/smelter_top.png")
    energy_cell(0).save("block/energy_cell_0.png")
    for charge in range(1, 5):
        lit(f"block/energy_cell_{charge}.png", [energy_cell(charge)], [energy_cell(0)])
    energy_cell_top().save("block/energy_cell_top.png")
    power_cable().save("block/power_cable.png")
    forecaster_front().save("block/forecaster_front.png")
    lit("block/forecaster_front_on.png", [forecaster_front(i) for i in range(32)], [forecaster_front()], 3)
    forecaster_side().save("block/forecaster_side.png")
    forecaster_top().save("block/forecaster_top.png")
    forecaster_front(ramp=AMETHYST).save("block/forecaster_ii_front.png")
    lit("block/forecaster_ii_front_on.png", [forecaster_front(i, AMETHYST, 2) for i in range(16)],
        [forecaster_front(ramp=AMETHYST)], 3)
    forecaster_side(AMETHYST).save("block/forecaster_ii_side.png")
    forecaster_top(AMETHYST).save("block/forecaster_ii_top.png")
    forecaster_front(ramp=AMBER).save("block/forecaster_iii_front.png")
    lit("block/forecaster_iii_front_on.png", [forecaster_front(i, AMBER, 1) for i in range(8)],
        [forecaster_front(ramp=AMBER)], 2)
    forecaster_side(AMBER).save("block/forecaster_iii_side.png")
    forecaster_top(AMBER).save("block/forecaster_iii_top.png")
    animated([stream_side(i) for i in range(8)], "block/stream_side.png", 2)
    SAVED["block/stream_side.png"] = stream_side(0).im
    stream_end().save("block/stream_end.png")

    cable(TEAL[3], None, SLATE[3], TEAL[4]).save("block/cable.png")
    for dye, hex_ in DYES.items():
        base, light, dark = cable_colors(hex_)
        cable(base, light, dark, mix(base, (255, 255, 255, 255), 0.6)).save(f"block/{dye}_cable.png")

    weight_crystal().save("item/weight_crystal.png")
    probe().save("item/tensor_probe.png")
    checkpoint().save("item/checkpoint.png")
    archive_drive().save("item/archive_drive.png")
    tokenizer(False).save("item/tokenizer.png")
    tokenizer(True).save("item/tokenizer_written.png")
    program_card(False).save("item/program_card.png")
    program_card(True).save("item/program_card_written.png")
    for name in UPGRADE_MARKS:
        upgrade(name).save(f"item/{name}_upgrade.png")
    field_log().save("item/field_log.png")
    hourglass().save("item/hourglass.png")
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
    # energy and compute
    boiler_front(False).save("block/boiler_front.png")
    lit("block/boiler_front_on.png", [boiler_front(True)], [boiler_front(False)])
    boiler_side().save("block/boiler_side.png")
    boiler_top().save("block/boiler_top.png")
    turbine_side().save("block/turbine_side.png")
    lit("block/turbine_side_on.png", [turbine_side(i) for i in range(8)], [turbine_side()], 2)
    turbine_top().save("block/turbine_top.png")
    solar_top(False).save("block/solar_panel_top.png")
    solar_top(True).save("block/solar_panel_top_on.png")
    solar_side().save("block/solar_panel_side.png")
    capacitor_bank(0).save("block/capacitor_bank_0.png")
    for charge in range(1, 5):
        lit(f"block/capacitor_bank_{charge}.png", [capacitor_bank(charge)], [capacitor_bank(0)])
    capacitor_bank_top().save("block/capacitor_bank_top.png")
    heavy_cable().save("block/heavy_cable.png")
    bus_bar().save("block/bus_bar.png")
    interconnect().save("block/interconnect.png")
    coolant_pipe().save("block/coolant_pipe.png")
    animated([coolant_pipe(i) for i in range(4)], "block/coolant_pipe_on.png", 3)
    SAVED["block/coolant_pipe_on.png"] = coolant_pipe(0).im
    for name, ramp in (("accelerator_28", AZURE), ("accelerator_7", AMETHYST), ("accelerator_3", AMBER)):
        accelerator_node(ramp).save(f"block/{name}.png")
        lit(f"block/{name}_hot.png", [accelerator_hot()], [accelerator_node(ramp)])
    module_front().save("block/rack_module_front.png")
    rack_inside().save("block/rack_module_inside.png")
    rack_back_panel().save("block/rack_module_back_panel.png")
    device_cartridge().save("block/device_cartridge.png")
    module_side().save("block/rack_module_side.png")
    module_top().save("block/rack_module_top.png")
    for name, ramp, ports in (("rack_switch", TEAL, (12,)), ("pod_switch", AMETHYST, (11, 13))):
        switch_front(ramp, ports, False).save(f"block/{name}_front.png")
        lit(f"block/{name}_front_on.png", [switch_front(ramp, ports, True)], [switch_front(ramp, ports, False)])
        switch_side(ramp).save(f"block/{name}_side.png")
    chiller_front(False).save("block/chiller_front.png")
    lit("block/chiller_front_on.png", [chiller_front(True)], [chiller_front(False)])
    chiller_side().save("block/chiller_side.png")
    machine_top(82).save("block/chiller_top.png")
    console_front(False).save("block/hall_console_front.png")
    lit("block/hall_console_front_on.png", [console_front(True)], [console_front(False)])
    wall_front().save("block/load_wall_front.png")
    wall_side().save("block/load_wall_side.png")
    reactor_core_front(False).save("block/reactor_core_front.png")
    lit("block/reactor_core_front_on.png", [reactor_core_front(True)], [reactor_core_front(False)])
    reactor_casing().save("block/reactor_casing.png")
    reactor_glass().save("block/reactor_glass.png")
    fuel_rod(False).save("block/fuel_rod.png")
    lit("block/fuel_rod_on.png", [fuel_rod(True)], [fuel_rod(False)])
    for name, ramp in (("wafer_28", AZURE), ("wafer_7", AMETHYST), ("wafer_3", AMBER)):
        wafer_node(ramp).save(f"item/{name}.png")
    reactor_fuel().save("item/reactor_fuel.png")
    photomask(False).save("item/photomask.png")
    photomask(True).save("item/photomask_recorded.png")
    guide().save("item/guide.png")
    agent_skin().save("entity/agent.png")
    # the mod list scales it down to 50 px high
    for name, cells in ICONS.items():
        screen_icon(cells).save(f"gui/sprites/icon/{name}.png")
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
