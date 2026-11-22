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
# screens and holes
W0, W1, W2, W3 = SLATE[0], SLATE[1], SLATE[2], SLATE[3]
DIM = [SLATE[1], SLATE[2], SLATE[3], SLATE[4]]
# graphite for the Model Core and the Model Server
G0, G1, G2, G3 = (ok(L, .03, 270) for L in (.30, .36, .42, .49))
BRASS = [rgb(h) for h in ["3b2810", "6a4819", "9a6c2a", "c8973f", "e6c06e", "faeab8"]]
STONE = [rgb(h) for h in ["4b4842", "6f6b63", "9b968b", "beb9ae", "dcd8cf", "f1efe9"]]
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
        "#######+",
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
        "########",
        "########",
    ],
    # the heads, joined back into one vector
    "merge_heads": [
        "########",
        "########",
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


def machine_back():
    back = plate(salt=3)
    main_input(back, TEAL)
    return back


def blank_back(op):
    back = plate(salt=3)
    engraved_glyph(back, GLYPHS[op], HUE[op])
    return back


# ---------------------------------------------------------------- Model Core


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


def casing():
    """Plain housing: an inner panel with a rivet in each corner."""
    c = plate(salt=7)
    edge(c, 4, 4, 11, 11, STEEL[4], STEEL[5], 71)
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        c.px(x, y, STEEL[7])
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


# ---------------------------------------------------------------- BPE Forge

# the forge's display: four lines of text, a character on every other pixel
FORGE_ROWS = (4, 6, 9, 11)
FORGE_GAPS = [(0, 4), (2, 3), (1, 6), (3, 4), (0, 7), (2, 5), (1, 2), (3, 1), (0, 1), (2, 7), (1, 4)]


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


# a probe through three layers of the stream
SCOPE_GLYPH = [
    "...##...",
    "########",
    "...##...",
    "########",
    "...##...",
    "########",
    "...##...",
]
# a vise with a specimen in its jaws
RIG_GLYPH = [
    "##....##",
    "##....##",
    "##.##.##",
    "##.##.##",
    "##.##.##",
    "########",
    "...##...",
]
# what the Scope's trace passes through, one height for each column of its window
SCOPE_TRACE = [8, 8, 7, 5, 5, 6, 9, 10, 9, 8, 7, 7, 8, 9, 7, 6]


def stream_scope_front(step=None):
    """A display with a graticule: a dim trace at rest; while it works, the trace runs across it."""
    c = plate(salt=35)
    window(c, salt=35)
    edge(c, 3, 3, 12, 12, AZURE[2], mix(AZURE[2], AZURE[1], .45), 39)
    for y in range(4, 12):
        for x in range(4, 12):
            c.px(x, y, mix(W1, STONE[0], .25))
    for x in (6, 9):
        for y in range(4, 12):
            c.px(x, y, mix(W1, AZURE[1], .45))
    for y in (6, 9):
        for x in range(4, 12):
            c.px(x, y, mix(W1, AZURE[1], .45))
    for x in range(4, 12):
        y = SCOPE_TRACE[(x + (step or 0)) % len(SCOPE_TRACE)]
        c.px(x, y, AZURE[5] if step is not None else mix(AZURE[3], W1, .35))
    return c


def stream_scope_side():
    c = plate(salt=36)
    engraved_glyph(c, SCOPE_GLYPH, AZURE)
    return c


def trial_rig_front(step=None):
    """A display of a score against a look at it: bars falling away; while it works, the last of them flickers."""
    c = plate(salt=37)
    window(c, ramp=AMBER if step is not None else None, salt=37)
    edge(c, 3, 3, 12, 12, AMBER[2], mix(AMBER[2], AMBER[1], .45), 40)
    for y in range(4, 12):
        for x in range(4, 12):
            c.px(x, y, mix(W1, STONE[0], .25))
    heights = [7, 7, 6, 5, 3, 2, 1, 1]
    for i, h in enumerate(heights):
        lit_bar = step is not None and i == 4 + (step or 0) % 4
        for y in range(11, 11 - h, -1):
            c.px(4 + i, y, AMBER[5] if lit_bar else AMBER[3] if step is not None else mix(AMBER[2], W1, .4))
    return c


def trial_rig_side():
    c = plate(salt=38)
    engraved_glyph(c, RIG_GLYPH, AMBER)
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


def forecaster_side(ramp=GREEN):
    c = plate(salt=22)
    engraved_glyph(c, SPROUT, ramp)
    return c


def forecaster_top(ramp=GREEN):
    """An hourglass: it sees where the field is going."""
    c = plate(salt=23)
    engraved_glyph(c, HOURGLASS, ramp)
    return c


# ---------------------------------------------------------------- stream and cables

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
BENCHMARK_COLORS = {"forecast": hue_ramp(85), "storage": AMBER, "errand": AZURE, "pattern": AMETHYST, "sorting": CORAL, "modular": hue_ramp(270)}
# what each dataset practices, punched into the card
DATASET_HOLES = {
    "modular": [
        "##.##.##",
        "#......#",
        "...##...",
        "#.####.#",
        "...##...",
        "#......#",
        "##.##.##",
    ],
    "forecast": [
        "...#....",
        "..##...#",
        ".#.#..##",
        "#..#.#.#",
        "...##..#",
        "........",
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
    # a funnel: many kinds in, each sent its own way
    "sorting": [
        "########",
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


# ---------------------------------------------------------------- checks

def mirrored(rows):
    """Whether a glyph reads the same mirrored left to right."""
    return all(row == row[::-1] for row in rows)


# ---------------------------------------------------------------- machine tops

def machine_top(salt):
    """Vents across the top."""
    c = plate(salt=salt)
    for x in (4, 6, 9, 11):
        c.rect(x, 4, x, 11, SLATE[1])
        c.rect(x, 5, x, 10, SLATE[2])
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
    "decay": icon_cells("""
................
................
..##............
..##............
..##..##........
..##..##........
..##..##........
..##..##..##....
..##..##..##....
..##..##..##....
..##..##..##..##
..##..##..##..##
................
................
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
}


def screen_icon(cells):
    c = Canvas()
    for x, y in cells:
        c.px(x, y, ICON_LIGHT)
    for x, y in cells:
        if (x, y + 1) not in cells:
            c.px(x, y + 1, ICON_SHADOW)
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
    lit("block/model_core_front_on.png", [core_front(i) for i in range(2)], [core_front()], 4)
    lit("block/model_core_side_on.png", [core_side(i) for i in range(5)], [core_side()], 3)

    casing().save("block/tensor_casing.png")

    reader_front().save("block/reader_front.png")
    lit("block/reader_front_on.png", [reader_front(i) for i in range(8)], [reader_front()], 4)
    reader_top().save("block/reader_top.png")
    reader_side().save("block/reader_side.png")
    forge_front().save("block/bpe_forge_front.png")
    lit("block/bpe_forge_front_on.png", [forge_front(i) for i in range(len(FORGE_GAPS) + 1)], [forge_front()], 3)
    forge_side().save("block/bpe_forge_side.png")
    forge_top().save("block/bpe_forge_top.png")
    # a glyph symmetric along an axis has to be even along it, or it sits half a pixel off the tile's centre
    for rows in [*GLYPHS.values(), SPROUT]:
        assert not (mirrored(rows) and len(rows[0]) % 2), rows
        assert not (rows == rows[::-1] and len(rows) % 2), rows
    # every face mirrors left to right but those that picture something one-sided or show data
    one_sided = {f"block/{op}_{face}.png" for op in ONE_SIDED for face in ("front", "side", "back")}
    data = {"block/forecaster_front", "block/forecaster_front_on", "block/stream_scope_front", "block/stream_scope_front_on",
            "block/trial_rig_front", "block/trial_rig_front_on", "block/reader_front", "block/reader_front_on",
            "block/bpe_forge_front_on", "block/model_core_side_on"}
    for rel, im in SAVED.items():
        if str(rel).startswith("block/") and im.size == (16, 16) and rel not in one_sided and rel[:-4] not in data:
            px = im.load()
            assert all(px[x, y] == px[15 - x, y] for y in range(16) for x in range(8)), f"{rel} is not mirrored"
    assert mirrored(WAYFINDER_CASE), WAYFINDER_CASE
    for k in range(32):
        wayfinder(k).save(f"item/wayfinder_{k:02d}.png")
    stream_scope_front().save("block/stream_scope_front.png")
    lit("block/stream_scope_front_on.png", [stream_scope_front(i) for i in range(len(SCOPE_TRACE))], [stream_scope_front()], 2)
    stream_scope_side().save("block/stream_scope_side.png")
    machine_top(64).save("block/stream_scope_top.png")
    trial_rig_front().save("block/trial_rig_front.png")
    lit("block/trial_rig_front_on.png", [trial_rig_front(i) for i in range(4)], [trial_rig_front()], 4)
    trial_rig_side().save("block/trial_rig_side.png")
    machine_top(65).save("block/trial_rig_top.png")
    forecaster_front().save("block/forecaster_front.png")
    lit("block/forecaster_front_on.png", [forecaster_front(i) for i in range(32)], [forecaster_front()], 3)
    forecaster_side().save("block/forecaster_side.png")
    forecaster_top().save("block/forecaster_top.png")
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
    field_log().save("item/field_log.png")
    archive_shard().save("item/archive_shard.png")
    salvaged_model().save("item/salvaged_model.png")
    for kind in BENCHMARK_COLORS:
        dataset(kind).save(f"item/{kind}_dataset.png")
    guide().save("item/guide.png")
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
