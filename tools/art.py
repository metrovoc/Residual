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
# the hues: the mark, the Wayfinder's crystal, a model at work, the Wayfinder's case
TEAL, AMETHYST, AMBER, CORAL = (hue_ramp(h) for h in (190, 302, 72, 38))
# light casing
K0, K1, K2, K3, K4 = STEEL[0], STEEL[1], STEEL[2], STEEL[4], STEEL[6]
NEUTRAL = (SLATE[1], SLATE[3])
# graphite for the Model Core and the Model Server
G0, G1, G2 = (ok(L, .03, 270) for L in (.30, .36, .42))
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


def side_input(c, ramp):
    """A square port, a ring and a pin inside it in the face's hue."""
    edge(c, 5, 5, 10, 10, SLATE[3], SLATE[2], 43)
    fill(c, 6, 6, 9, 9, SLATE[1])
    edge(c, 6, 6, 9, 9, ramp[2])
    c.rect(7, 7, 8, 7, ramp[4])
    c.rect(7, 8, 8, 8, ramp[3])


# the mod's mark: two flows that meet and go on as one
JUNCTION = [
    "..#--#..",
    "..#--#..",
    "###--###",
    "---++---",
    "---++---",
    "###--###",
    "..#--#..",
    "..#--#..",
]


def junction_faces():
    """The Junction's front, its glyph lit on a screen, and its side with a port."""
    front = plate(salt=1)
    window(front, ramp=TEAL, salt=1)
    glow_glyph(front, JUNCTION, TEAL)
    side = plate(salt=2, centre=(mix(STEEL[2], TEAL[4], .18), mix(STEEL[3], TEAL[4], .18)))
    side_input(side, TEAL)
    return front, side


# ---------------------------------------------------------------- Model Core


def core_base(salt):
    """The Core's face: the shared frame round a graphite centre."""
    return plate(salt=salt, centre=(G1, mix(G1, G2, .5)))


def core_front(phase=None):
    """The front: square rings round a core, amber; holding a drive, a pulse runs out along the four traces to the
    frame."""
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
    """Every other face: a grid of cells, flickering while it holds a drive."""
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


# ---------------------------------------------------------------- a hall's consoles

LEAF = hue_ramp(145)

# what each console does, as a sign on its screen; the hue says what it works on: teal the run, amber the model's shape,
# amethyst attention, green the feed-forward part, coral what takes away
CONSOLES = {
    "exit": (CORAL, ["...#....", "..##....", ".#######", "########", ".#######", "..##....", "...#....", "........"]),
    "task": (TEAL, ["........", "#.#####.", "........", "#.#####.", "........", "#.#####.", "........", "........"]),
    "run": (TEAL, ["..#.....", "..##....", "..###...", "..####..", "..###...", "..##....", "..#.....", "........"]),
    "step": (TEAL, [".#...##.", ".##..##.", ".###.##.", ".######.", ".###.##.", ".##..##.", ".#...##.", "........"]),
    "pace": (TEAL, ["#...#...", "##..##..", "###.###.", "########", "###.###.", "##..##..", "#...#...", "........"]),
    "rate": (TEAL, ["......##", ".....##.", "....##..", "...##...", "..##....", ".##.....", "########", "........"]),
    "restart": (TEAL, ["..####..", ".#....#.", "#......#", "#......#", "#...#..#", ".#..##..", "..#####.", "....##.."]),
    "example": (TEAL, ["#..#....", ".#..#...", "..#..#..", "...#..#.", "..#..#..", ".#..#...", "#..#....", "........"]),
    "width": (AMBER, ["........", "..#..#..", ".#....#.", "########", ".#....#.", "..#..#..", "........", "........"]),
    "positions": (AMBER, ["........", "......#.", "......#.", "...#..#.", "...#..#.", "#..#..#.", "#..#..#.", "........"]),
    "heads": (AMETHYST, ["........", "##.##.##", "##.##.##", "........", ".#.#..#.", ".#.#..#.", ".#.#..#.", "........"]),
    "norm": (AMBER, ["........", "########", "........", ".######.", "........", "########", "........", "........"]),
    "add_attention": (AMETHYST, ["...##...", "...##...", "########", "########", "...##...", "...##...", "........", "........"]),
    "add_feed_forward": (LEAF, ["...##...", "...##...", "########", "########", "...##...", "...##...", "........", "........"]),
    "switch": (AMBER, ["...##...", ".#.##.#.", "#..##..#", "#..##..#", "#......#", ".#....#.", "..####..", "........"]),
    "remove": (CORAL, ["#......#", ".#....#.", "..#..#..", "...##...", "..#..#..", ".#....#.", "#......#", "........"]),
}


def console_side():
    return core_base(salt=7)


def console_front(role=None):
    """The face toward the walkway: a screen, dark, or lit with what the console does."""
    c = core_base(salt=8)
    window(c, ramp=CONSOLES[role][0] if role else None, salt=8)
    if role:
        glow_glyph(c, CONSOLES[role][1], CONSOLES[role][0])
    return c


# ---------------------------------------------------------------- items

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


def outline(c, color):
    """Rings every drawn shape with a 1 px border in the item's own darkest tone, the way vanilla items are drawn."""
    filled = {(x, y) for y in range(16) for x in range(16) if c.get(x, y)[3]}
    for x, y in filled:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in filled:
                c.px(x + dx, y + dy, color)


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


CROP = [rgb(h) for h in ["2f5a2a", "3a6b2c", "467c2e", "568c30", "6e9a33", "8ca637", "aeaf3e", "d9b44a"]]


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


MODEL_DRIVE = [
    "................",
    "................",
    "...kKKKKKKKKk...",
    "...GGGGGGGGGG...",
    "...GDDDDDDDDG...",
    "...GaaDccDbbG...",
    "...GaaDccDbbG...",
    "...GDDDDDDDDG...",
    "...GbbDaaDccG...",
    "...GbbDaaDccG...",
    "...GDDDDDDDDG...",
    "...GGGGGGGGtG...",
    "...gGGGGGGGGg...",
    "....y.y.y.y.....",
    "................",
    "................",
]


def model_drive(written):
    """The archive drive's cartridge with a window of cells where its label would be, as on the Model Core's sides:
    dark while it is blank, lit once it holds a model."""
    c = Canvas()
    cells = {"a": AMBER[2], "b": AMBER[3], "c": AMBER[4], "t": AMBER[4]} if written else {"a": AMBER[1], "b": AMBER[1], "c": AMBER[1], "t": G0}
    c.stamp(0, 0, MODEL_DRIVE, {"k": K3, "K": K1, "G": G2, "g": G1, "D": G0, "y": AMBER[3]} | cells)
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


def mirrored(rows):
    """Whether a glyph reads the same mirrored left to right."""
    return all(row == row[::-1] for row in rows)


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
    for (x, y), ch in glyph_cells(JUNCTION).items():
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
    """The mod's mark as a block in the inventory, its glyph lit."""
    front, side = junction_faces()
    x0, y0 = centered(JUNCTION)
    glyph = {(x0 + x, y0 + y) for x, y in glyph_cells(JUNCTION)}
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


def build():
    core_front().save("block/model_core_front.png")
    core_side().save("block/model_core_side.png")
    lit("block/model_core_front_on.png", [core_front(i) for i in range(2)], [core_front()], 4)
    lit("block/model_core_side_on.png", [core_side(i) for i in range(5)], [core_side()], 3)
    console_side().save("block/console_side.png")
    for role in CONSOLES:
        lit(f"block/console_{role}.png", [console_front(role)], [console_front()])
    # every face mirrors left to right but those that show data or a sign
    for rel, im in SAVED.items():
        if str(rel).startswith("block/") and im.size == (16, 16) and rel != "block/model_core_side_on.png" and "console_" not in rel:
            px = im.load()
            assert all(px[x, y] == px[15 - x, y] for y in range(16) for x in range(8)), f"{rel} is not mirrored"
    assert mirrored(WAYFINDER_CASE), WAYFINDER_CASE
    for k in range(32):
        wayfinder(k).save(f"item/wayfinder_{k:02d}.png")
    model_drive(False).save("item/model_drive.png")
    model_drive(True).save("item/model_drive_written.png")
    archive_drive().save("item/archive_drive.png")
    field_log().save("item/field_log.png")
    archive_shard().save("item/archive_shard.png")
    guide().save("item/guide.png")
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
