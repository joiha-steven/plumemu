#!/usr/bin/env python3
"""Build Plumemu's Jost: the Jost variable font plus Vietnamese, arrows and a check mark.

    python3 tools/fonts/build_jost.py            # writes core-ui/src/main/res/font/jost_{400..800}.ttf
    python3 tools/fonts/build_jost.py --out DIR  # writes the five files to DIR instead

Jost (Indestructible Type, OFL 1.1) has no horn, no hook above and no dot below, so Vietnamese
text would fall back to another font letter by letter. For each weight this script:

1. instances the variable font at that weight (static faces: Android 6 has no variable fonts);
2. measures that instance's own strokes and draws the horn, the hook above, the dot below,
   arrows and a check mark at those weights, so the new shapes match the face at every weight;
3. composes every Vietnamese letter from Jost's own letters and marks, stacking two marks the
   Vietnamese way (a tone beside the circumflex, a tone above the breve), squashed where a
   capital stack would otherwise rise above the ascent;
4. gives the new letters the kerning classes of their base letters and adds mark/mkmk anchors,
   so combining sequences (o + U+031B + U+0301) position too;
5. subsets to Latin-1, Latin Extended-A, Vietnamese, punctuation and the UI symbols, keeping
   only the `kern`, `mark`, `mkmk` and `tnum` features.

fontTools is the only requirement (a local build tool, not an app dependency). The output is
the same byte for byte on every run.
"""
import argparse
import hashlib
import io
import math
import sys
import unicodedata
from pathlib import Path

from fontTools.feaLib.builder import addOpenTypeFeaturesFromString
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import Glyph, GlyphComponent, flagOverlapSimple
from fontTools.varLib import instancer

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SOURCE = HERE / "source" / "Jost[wght].ttf"
SOURCE_SHA256 = "6343b70971000b04c5d401c96ae08ce371086135e999d5e1e1413039c0213076"
WEIGHTS = (400, 500, 600, 700, 800)
FAMILY = "Jost"
# 2026-11-03 00:00 UTC in seconds since 1904, pinned so a rebuild is identical byte for byte.
HEAD_TIME = 3876508800
# Highest point any mark may reach: the ascent the V2 boards lay Jost out with.
ASCENT = 1070
TOP_MARGIN = 5


# ---------------------------------------------------------------- outline helpers

def contours_of(font, name):
    """A simple glyph's contours as lists of (x, y, on_curve)."""
    glyf = font["glyf"]
    g = glyf[name]
    coords, ends, flags = g.getCoordinates(glyf)
    out, start = [], 0
    for end in ends:
        out.append([(coords[i][0], coords[i][1], bool(flags[i] & 1)) for i in range(start, end + 1)])
        start = end + 1
    return out


def flatten(contour, steps=8):
    """A TrueType contour as a closed polyline."""
    pts = list(contour)
    # Expand implied on-curve points between two off-curve points.
    full = []
    n = len(pts)
    for i in range(n):
        p, q = pts[i], pts[(i + 1) % n]
        full.append(p)
        if not p[2] and not q[2]:
            full.append(((p[0] + q[0]) / 2, (p[1] + q[1]) / 2, True))
    while not full[0][2]:
        full.append(full.pop(0))
    out = []
    n = len(full)
    i = 0
    while i < n:
        p = full[i]
        q = full[(i + 1) % n]
        if q[2]:
            out.append((p[0], p[1]))
            i += 1
        else:
            r = full[(i + 2) % n]
            for k in range(steps):
                t = k / steps
                out.append(((1 - t) ** 2 * p[0] + 2 * t * (1 - t) * q[0] + t * t * r[0],
                            (1 - t) ** 2 * p[1] + 2 * t * (1 - t) * q[1] + t * t * r[1]))
            i += 2
    return out


def polylines(font, name):
    return [flatten(c) for c in contours_of(font, name)]


def crossings_y(polys, x):
    """Heights where the vertical line at x crosses the outline, sorted."""
    ys = []
    for poly in polys:
        for (x0, y0), (x1, y1) in zip(poly, poly[1:] + poly[:1]):
            if (x0 <= x < x1) or (x1 <= x < x0):
                ys.append(y0 + (y1 - y0) * (x - x0) / (x1 - x0))
    return sorted(ys)


def crossings_x(polys, y):
    """Positions where the horizontal line at y crosses the outline, sorted."""
    xs = []
    for poly in polys:
        for (x0, y0), (x1, y1) in zip(poly, poly[1:] + poly[:1]):
            if (y0 <= y < y1) or (y1 <= y < y0):
                xs.append(x0 + (x1 - x0) * (y - y0) / (y1 - y0))
    return sorted(xs)


def bbox(font, name):
    g = font["glyf"][name]
    g.recalcBounds(font["glyf"])
    return g.xMin, g.yMin, g.xMax, g.yMax


def signed_area(poly):
    return sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(poly, poly[1:] + poly[:1])) / 2


def outer(poly):
    """Turn a contour the way Jost's outer contours turn (counter-clockwise), so a new stroke
    that overlaps a letter adds to it instead of cutting a hole where they cross."""
    return list(reversed(poly)) if signed_area(poly) < 0 else poly


def stroke(centre, width):
    """Outline of an open polyline drawn with a butt-capped pen of the given width (mitred joins)."""
    h = width / 2
    left, right = [], []
    n = len(centre)
    for i, (x, y) in enumerate(centre):
        if i == 0:
            dx, dy = centre[1][0] - x, centre[1][1] - y
            ux, uy = -dy, dx
            d = math.hypot(ux, uy)
            nx, ny, scale = ux / d, uy / d, 1.0
        elif i == n - 1:
            dx, dy = x - centre[i - 1][0], y - centre[i - 1][1]
            ux, uy = -dy, dx
            d = math.hypot(ux, uy)
            nx, ny, scale = ux / d, uy / d, 1.0
        else:
            ax, ay = x - centre[i - 1][0], y - centre[i - 1][1]
            bx, by = centre[i + 1][0] - x, centre[i + 1][1] - y
            da, db = math.hypot(ax, ay), math.hypot(bx, by)
            n1 = (-ay / da, ax / da)
            n2 = (-by / db, bx / db)
            mx, my = n1[0] + n2[0], n1[1] + n2[1]
            dm = math.hypot(mx, my)
            nx, ny = mx / dm, my / dm
            cos_half = nx * n1[0] + ny * n1[1]
            scale = 1 / max(cos_half, 0.3)
        left.append((x + nx * h * scale, y + ny * h * scale))
        right.append((x - nx * h * scale, y - ny * h * scale))
    return outer(left + right[::-1])


def arc(cx, cy, r, a0, a1, step_deg=6):
    """Points on a circle from angle a0 to a1 (degrees, either direction)."""
    count = max(2, int(abs(a1 - a0) / step_deg) + 1)
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * k / (count - 1))),
             cy + r * math.sin(math.radians(a0 + (a1 - a0) * k / (count - 1)))) for k in range(count)]


def simple_glyph(font, contours, overlap=False):
    """A simple glyph from point contours; every point is on-curve (curves are finely sampled)."""
    pen = TTGlyphPen(None)
    for c in contours:
        c = [(round(x), round(y)) for x, y in c]
        c = [p for i, p in enumerate(c) if p != c[i - 1]]
        pen.moveTo(c[0])
        for p in c[1:]:
            pen.lineTo(p)
        pen.closePath()
    g = pen.glyph()
    if overlap and g.numberOfContours > 0:
        g.flags[0] |= flagOverlapSimple
    g.recalcBounds(font["glyf"])
    return g


def add_glyph(font, name, glyph, advance):
    glyf = font["glyf"]
    if name not in glyf:
        font.setGlyphOrder(font.getGlyphOrder() + [name])
    glyf[name] = glyph
    glyph.recalcBounds(glyf)
    font["hmtx"][name] = (advance, getattr(glyph, "xMin", 0))


def composite(font, parts, metrics_from):
    """A composite glyph; parts are (glyph name, dx, dy[, (xx, yy) scale])."""
    g = Glyph()
    g.numberOfContours = -1
    g.components = []
    for part in parts:
        c = GlyphComponent()
        c.glyphName, c.x, c.y = part[0], round(part[1]), round(part[2])
        c.flags = 0x4  # ROUND_XY_TO_GRID
        if part[0] == metrics_from:
            c.flags |= 0x200  # USE_MY_METRICS
        if len(part) > 3 and part[3] != (1, 1):
            c.transform = [[part[3][0], 0], [0, part[3][1]]]
        g.components.append(c)
    g.components[0].flags |= 0x400  # OVERLAP_COMPOUND: marks may touch the letter
    return g


# ---------------------------------------------------------------- measurements

class Face:
    """Numbers measured on one static instance."""

    def __init__(self, font):
        self.font = font
        glyf = font["glyf"]
        x0, _, x1, _ = bbox(font, "dotlessi")
        self.stem = x1 - x0
        # Thickness of the breve at its middle: the weight Jost gives its marks.
        bx0, _, bx1, _ = bbox(font, "uni0306")
        ys = crossings_y(polylines(font, "uni0306"), (bx0 + bx1) / 2)
        self.mark = ys[1] - ys[0]
        # The bowl of o at mid height: horizontal stroke thickness and centre.
        o = polylines(font, "o")
        ox0, oy0, ox1, oy1 = bbox(font, "o")
        xs = crossings_x(o, (oy0 + oy1) / 2)
        self.bowl = xs[-1] - xs[-2]
        # The minus sets the axis and thickness of arrows.
        mx0, my0, mx1, my1 = bbox(font, "minus")
        self.axis = (my0 + my1) / 2
        self.bar = my1 - my0
        self.minus_width = font["hmtx"]["minus"][0]
        self.xheight = 460
        self.cap = 700
        self.dot = bbox(font, "uni0307")
        self.glyf = glyf


def mark_contour_offset(font, composed, mark):
    """Where Jost placed `mark`'s outline inside the precomposed glyph `composed`: (dx, dy)."""
    target = contours_of(font, mark)[0]
    for c in contours_of(font, composed):
        if len(c) == len(target):
            dx, dy = c[0][0] - target[0][0], c[0][1] - target[0][1]
            if all(abs(a[0] - b[0] - dx) <= 1 and abs(a[1] - b[1] - dy) <= 1 for a, b in zip(c, target)):
                return dx, dy
    raise ValueError(f"{mark} not found in {composed}")


# ---------------------------------------------------------------- new shapes

def draw_marks(face):
    """The hook above and the dot below, in the weight of Jost's own marks at this instance."""
    font = face.font
    w = face.mark
    # Hook above: the top of a question mark, a round bowl over a short tail, as tall as the
    # acute. Drawn around x = 0; it is placed by its bounding box later.
    _, ay0, _, ay1 = bbox(font, "acute")
    height = (ay1 - ay0) * 0.97
    top = height - w / 2
    r = (height - w) * 0.36
    bowl = arc(0.0, top - r, r, 170, -90)
    tail_top = top - 2 * r
    tail = [(0.0, tail_top - tail_top * k / 3) for k in range(1, 4)]
    add_glyph(font, "uni0309", simple_glyph(font, [stroke(bowl + tail, w)]), 0)

    # Dot below: Jost's own combining dot, hung below the baseline.
    dx0, dy0, dx1, dy1 = face.dot
    gap = 70 + (dy1 - dy0 - 110) * 0.3
    dot = [(x - dx0, y - dy1 - gap) for x, y in flatten(contours_of(font, "uni0307")[0])]
    add_glyph(font, "uni0323", simple_glyph(font, [outer(dot)]), 0)


def horn_centreline(start, heading0, heading1, top, w):
    """An arc leaving `start` at heading0 (degrees) and turning left to heading1, sized so that
    the flat end of a stroke of width w reaches `top`."""
    sx, sy = start
    c0, c1 = math.cos(math.radians(heading0)), math.cos(math.radians(heading1))
    # The arc rises r (cos h0 - cos h1); the end cap adds w/2 cos h1 above its centre.
    r = (top - w / 2 * c1 - sy) / (c0 - c1)
    cx = sx - r * math.sin(math.radians(heading0))
    cy = sy + r * c0
    # A point at heading h on this circle: centre + r (sin h, -cos h); as angle: h - 90.
    return arc(cx, cy, r, heading0 - 90, heading1 - 90)


HORNED = {"o": "ohorn", "u": "uhorn", "O": "Ohorn", "U": "Uhorn"}


def draw_horned(face):
    """o, u, O and U with horn (U+01A0, U+01A1, U+01AF, U+01B0) and the combining horn U+031B.

    The horn leaves the bowl of o (or the top of u's right stem) up and to the right and bends
    upright to a flat end, at 85 % of the letter's own stroke."""
    font = face.font
    made = {}
    for base, cap in (("o", False), ("u", False), ("O", True), ("U", True)):
        polys = polylines(font, base)
        x0, y0, x1, y1 = bbox(font, base)
        top = face.cap if cap else face.xheight
        if base in ("o", "O"):
            xs = crossings_x(polys, (y0 + y1) / 2)
            letter_stroke = xs[-1] - xs[-2]
            w = letter_stroke * 0.85
            # Start on the middle line of the bowl at 45 degrees, inside its stroke.
            rx = (x1 - x0 - letter_stroke) / 2
            ry = (y1 - y0 - letter_stroke) / 2
            ang = math.radians(45)
            start = ((x0 + x1) / 2 + rx * math.cos(ang), (y0 + y1) / 2 + ry * math.sin(ang))
            headings = (30, 90)
        else:
            xs = crossings_x(polys, top - 120)
            letter_stroke = xs[-1] - xs[-2]
            w = letter_stroke * 0.85
            # Start in the middle of the right stem, the horn's top edge level with the stem's.
            start = ((xs[-1] + xs[-2]) / 2, top - w / 2)
            headings = (30, 90)
        # As high above the letter as Plum Sans's horn: 130 units over x, 125 over the capitals.
        rise = 130 if not cap else 125
        centre = horn_centreline(start, headings[0], headings[1], top + rise, w)
        horn = stroke(centre, w)
        hx1 = max(x for x, _ in horn)
        adv = font["hmtx"][base][0]
        rsb = adv - x1
        # The horn may hang into the side bearing a little, as in Plum Sans.
        new_adv = round(max(adv, hx1 + rsb * 0.35))
        add_glyph(font, HORNED[base], horn_glyph(font, base, horn), new_adv)
        made[HORNED[base]] = horn
    # Combining horn: the lower-case o's horn, drawn left of the origin so that o + U+031B
    # puts it where U+01A1 has it.
    adv = font["hmtx"]["o"][0]
    shifted = [(x - adv, y) for x, y in made["ohorn"]]
    add_glyph(font, "uni031B", simple_glyph(font, [shifted]), 0)
    return made


def horn_glyph(font, base, horn):
    """The base letter's own contours (curves kept) plus the horn contour."""
    pen = TTGlyphPen(None)
    font["glyf"][base].draw(pen, font["glyf"])
    h = [(round(x), round(y)) for x, y in horn]
    h = [p for i, p in enumerate(h) if p != h[i - 1]]
    pen.moveTo(h[0])
    for p in h[1:]:
        pen.lineTo(p)
    pen.closePath()
    g = pen.glyph()
    g.flags[0] |= flagOverlapSimple
    g.recalcBounds(font["glyf"])
    return g


def draw_symbols(face):
    """Arrows and the check mark at the minus's axis and the stem's weight."""
    font = face.font
    t = face.bar * 0.9
    half = 300  # shaft half length: the arrows span 600 units, about the width of the O's bowl
    arm = 170 + 1.2 * t
    side = 60
    adv = 2 * half + 2 * side
    ax = face.axis

    def arrow(direction, both=False):
        # Built pointing right around the origin, then turned.
        tip = half
        shaft = stroke([(-half + (t * 0.71 if both else 0), 0), (tip - t * 0.71, 0)], t)
        d = arm / math.sqrt(2)
        head = stroke([(tip - d, d), (tip, 0), (tip - d, -d)], t)
        parts = [shaft, head]
        if both:
            parts.append(stroke([(-tip + d, -d), (-tip, 0), (-tip + d, d)], t))
        angle = {"right": 0, "up": 90, "left": 180, "down": 270}[direction]
        ca, sa = round(math.cos(math.radians(angle))), round(math.sin(math.radians(angle)))
        out = []
        for p in parts:
            q = [(x * ca - y * sa, x * sa + y * ca) for x, y in p]
            out.append(outer([(x + adv / 2, y + ax) for x, y in q]))
        return out

    for code, direction, both in ((0x2192, "right", False), (0x2190, "left", False),
                                  (0x2191, "up", False), (0x2193, "down", False),
                                  (0x2194, "right", True)):
        name = f"uni{code:04X}"
        add_glyph(font, name, simple_glyph(font, arrow(direction, both), overlap=True), adv)

    # Check mark: a short arm down to the right, a long one up, mitred at the bottom; it sits on
    # the baseline and rises to the cap height like the other symbols.
    tc = face.stem * 0.92
    left = (side + tc * 0.6, ax + 40)
    low = (side + 215, tc * 0.5 + 20)
    high = (adv - side - tc * 0.6, face.cap - tc * 0.5)
    check = stroke([left, low, high], tc)
    add_glyph(font, "uni2713", simple_glyph(font, [check]), adv)


# ---------------------------------------------------------------- Vietnamese

TONES = {"\u0300": "grave", "\u0301": "acute", "\u0303": "tilde", "\u0309": "hook", "\u0323": "dot"}
COMB = {"grave": "gravecomb", "acute": "acutecomb", "tilde": "tildecomb", "hook": "uni0309",
        "dot": "uni0323", "circ": "uni0302", "breve": "uni0306"}
# The precomposed letters are built from the spacing marks: Jost drew its own accented letters
# with those, and its combining marks are narrower at the heavy weights.
PIECE = {"grave": "grave", "acute": "acute", "tilde": "tilde", "hook": "uni0309", "dot": "uni0323",
         "circ": "circumflex", "breve": "breve"}
SPACING = {"grave": "grave", "acute": "acute", "tilde": "tilde", "circ": "circumflex", "breve": "breve"}
# The twelve Vietnamese vowels, lower case then capitals (a, a breve, a circumflex, e, ...).
VOWELS = unicodedata.normalize("NFC", "aa\u0306a\u0302ee\u0302ioo\u0302o\u031buu\u031by")
VOWELS += VOWELS.upper()


def vietnamese_letters():
    """Every vowel with each of the five tones, plus the four horned letters, as (char, base, shape, tone)."""
    out = []
    for v in VOWELS:
        nfd = unicodedata.normalize("NFD", v)
        letter, marks = nfd[0], nfd[1:]
        shape = {"\u0302": "circ", "\u0306": "breve", "\u031b": "horn", "": ""}[marks]
        out.append((v, letter, shape, None))
        for t, tone in TONES.items():
            ch = unicodedata.normalize("NFC", v + t)
            if len(ch) != 1:
                raise ValueError(f"{v}+{tone} has no precomposed form")
            out.append((ch, letter, shape, tone))
    return out


def glyph_for(font, ch):
    return font.getBestCmap().get(ord(ch))


class Placer:
    """Places marks over and under Jost's letters the way Jost's own accented letters do."""

    def __init__(self, face):
        self.face = face
        font = face.font
        self.font = font
        # Each mark's bounding box in its own (combining) glyph.
        self.box = {k: bbox(font, g) for k, g in PIECE.items()}
        self.comb_box = {k: bbox(font, g) for k, g in COMB.items()}
        # Where Jost put each mark over o and over O (bottom of the mark, x of its centre).
        self.lc, self.uc = {}, {}
        for table, base in ((self.lc, "o"), (self.uc, "O")):
            centre = self.centre_x(base)
            for kind, prefix in (("acute", "acute"), ("grave", "grave"), ("tilde", "tilde"),
                                 ("circ", "circumflex"), ("breve", "breve")):
                # Jost drew its accented letters with the spacing marks' outlines.
                dx, dy = mark_contour_offset(font, base + prefix, SPACING[kind])
                b = bbox(font, SPACING[kind])
                table[kind] = ((b[0] + b[2]) / 2 + dx - centre, b[1] + dy)
        # The hook sits where the circumflex does; its centre over the letter's.
        self.lc["hook"] = (0.0, self.lc["circ"][1])
        self.uc["hook"] = (0.0, self.uc["circ"][1])

    def centre_x(self, base):
        """The x the marks are centred on: the circumflex's centre in Jost's own precomposed letters"""
        font = self.font
        circ = {"dotlessi": "icircumflex", "i": "icircumflex"}.get(base, base + "circumflex")
        if circ in font["glyf"]:
            dx, _ = mark_contour_offset(font, circ, "circumflex")
            b = bbox(font, "circumflex")
            return (b[0] + b[2]) / 2 + dx
        x0, _, x1, _ = bbox(font, base)
        return (x0 + x1) / 2

    def at(self, kind, cx, bottom, scale=(1, 1)):
        """Component offset that puts mark `kind` centred at cx with its bottom at `bottom`."""
        b = self.box[kind]
        sx, sy = scale
        return (PIECE[kind], cx - (b[0] + b[2]) / 2 * sx, bottom - b[1] * sy, scale)

    def width(self, kind, sx=1):
        b = self.box[kind]
        return (b[2] - b[0]) * sx

    def height(self, kind, sy=1):
        b = self.box[kind]
        return (b[3] - b[1]) * sy

    def dot_below(self, base, letter):
        """Dot below: under the centre of the letter, or beside the descender of y."""
        font = self.font
        b = self.box["dot"]
        x0, y0, x1, y1 = bbox(font, base)
        if letter == "y":
            # The tail of y runs down to the left; the dot goes to its right, at the level the
            # other dots hang.
            polys = polylines(font, base)
            mid = (b[1] + b[3]) / 2
            xs = crossings_x(polys, mid)
            cx = (xs[-1] if xs else (x0 + x1) / 2) + self.face.mark * 0.9 + (b[2] - b[0]) / 2
        elif letter == "Y":
            polys = polylines(font, base)
            xs = crossings_x(polys, 40)
            cx = (xs[0] + xs[-1]) / 2
        elif letter in "ai":
            cx = self.centre_x(letter)
        else:
            cx = (x0 + x1) / 2
        return ("uni0323", cx - (b[0] + b[2]) / 2, 0, (1, 1))

    def compose(self, ch, letter, shape, tone, horned):
        """Component list for one Vietnamese letter."""
        font = self.font
        upper = letter.isupper()
        table = self.uc if upper else self.lc
        if shape == "horn":
            base = horned[letter]
            plain = letter
        elif letter == "i" and tone not in (None, "dot"):
            base = plain = "dotlessi"
        else:
            base = plain = glyph_for(font, letter)
        cx = self.centre_x("i" if plain == "dotlessi" else plain)
        parts = [(base, 0, 0, (1, 1))]
        if tone == "dot":
            parts.append(self.dot_below(plain, letter))
        top_tone = tone if tone in ("grave", "acute", "tilde", "hook") else None

        if shape in ("circ", "breve"):
            lower = shape
            lx, ly = table[lower]
            if not top_tone:
                parts.append(self.at(lower, cx + lx, ly))
                return parts
            if upper:
                # A capital's stack sits closer to the letter, as the ascent leaves no room.
                ly = self.face.cap + (ly - self.face.cap) * 0.8
            # Squash a capital's stack until it fits under the ascent: the lower mark down to
            # 60 % of its height, the tone with it down to 80 %.
            k = 1.0
            while True:
                parts_try = self.stack(lower, top_tone, cx + lx, ly, k, upper, 0.5 + 0.5 * k)
                top = max(p[2] + self.box[kind][3] * p[3][1] for kind, p in parts_try)
                if top <= ASCENT - TOP_MARGIN or not upper or k <= 0.6:
                    break
                k -= 0.02
            parts.extend(p for _, p in parts_try)
            return parts
        if top_tone:
            tx, ty = table[top_tone]
            parts.append(self.at(top_tone, cx + tx, ty))
        return parts

    def outline(self, part):
        """Polylines of a placed mark component, for measuring the space between two marks."""
        name, dx, dy, (sx, sy) = part
        return [[(x * sx + dx, y * sy + dy) for x, y in poly] for poly in polylines(self.font, name)]

    @staticmethod
    def clearance(a, b):
        """Smallest distance between two outlines; 0 when they touch or overlap."""
        def segments(polys):
            return [(p[i - 1], p[i]) for p in polys for i in range(len(p))]

        def inside(pt, polys):
            return any(len(crossings_x([poly], pt[1])) and
                       sum(1 for x in crossings_x([poly], pt[1]) if x > pt[0]) % 2 for poly in polys)

        def seg_dist(p, a, b):
            vx, vy = b[0] - a[0], b[1] - a[1]
            t = ((p[0] - a[0]) * vx + (p[1] - a[1]) * vy) / (vx * vx + vy * vy or 1)
            t = max(0, min(1, t))
            return math.hypot(p[0] - a[0] - t * vx, p[1] - a[1] - t * vy)

        def cross(p, q, r, t):
            def side(a, b, c):
                return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
            return side(p, q, r) * side(p, q, t) < 0 and side(r, t, p) * side(r, t, q) < 0

        sa, sb = segments(a), segments(b)
        if inside(a[0][0], b) or inside(b[0][0], a):
            return 0.0
        if any(cross(*s, *t) for s in sa for t in sb):
            return 0.0
        best = math.inf
        for p in (q for poly in a for q in poly):
            best = min(best, min(seg_dist(p, *s) for s in sb))
        for p in (q for poly in b for q in poly):
            best = min(best, min(seg_dist(p, *s) for s in sa))
        return best

    def stack(self, lower, tone, cx, bottom, sy, upper, ts=1.0):
        """Two stacked marks: the circumflex with a tone beside it, or the breve with one above."""
        f = self.face
        first = self.at(lower, cx, bottom, (1, sy))
        out = [(lower, first)]
        lower_top = bottom + self.height(lower, sy)
        gap = f.mark * (0.4 if upper else 0.5)
        if lower == "circ" and tone in ("acute", "grave", "hook"):
            # Beside the circumflex on its right, as Plum Sans does: start over the middle of
            # the right arm and move up and to the right until the tone clears the arm by `gap`.
            arm = self.outline(first)
            half = self.width("circ") / 2
            x = cx + half * 0.45
            foot = bottom + self.height("circ", sy) * 0.25
            step = math.radians(58)
            while True:
                part = self.at(tone, x, foot, (ts, ts))
                if self.clearance(arm, self.outline(part)) >= gap:
                    break
                x += 2 * math.cos(step)
                foot += 2 * math.sin(step)
            out.append((tone, part))
        else:
            # Above, centred: the tilde over the circumflex, any tone over the breve.
            out.append((tone, self.at(tone, cx, lower_top + gap * 0.9, (1, ts))))
        return out


def add_vietnamese(face):
    font = face.font
    placer = Placer(face)
    cmap_add = {}
    kern_like = {}
    for ch, letter, shape, tone in vietnamese_letters():
        if glyph_for(font, ch):
            continue
        if tone is None and shape == "horn":
            name = HORNED[letter]
            cmap_add[ord(ch)] = name
            kern_like[name] = (letter, False)
            continue
        name = f"uni{ord(ch):04X}"
        parts = placer.compose(ch, letter, shape, tone, HORNED)
        base = parts[0][0]
        g = composite(font, parts, base)
        add_glyph(font, name, g, font["hmtx"][base][0])
        cmap_add[ord(ch)] = name
        kern_like[name] = (letter, shape != "horn")
    return cmap_add, kern_like


def add_dong(face, cmap_add):
    """The dong sign U+20AB: d with stroke over a bar."""
    font = face.font
    x0, _, x1, _ = bbox(font, "dcroat")
    adv = font["hmtx"]["dcroat"][0]
    t = face.bar
    bar = [(x0, -150), (x0, -150 + t), (x1, -150 + t), (x1, -150)]
    pen = TTGlyphPen(None)
    font["glyf"]["dcroat"].draw(pen, font["glyf"])
    pen.moveTo(bar[0])
    for p in bar[1:]:
        pen.lineTo(p)
    pen.closePath()
    g = pen.glyph()
    g.recalcBounds(font["glyf"])
    add_glyph(font, "uni20AB", g, adv)
    cmap_add[0x20AB] = "uni20AB"


def set_cmap(font, extra):
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap.update(extra)


# ---------------------------------------------------------------- kerning, anchors

def extend_kerning(font, kern_like):
    """New letters kern like their base letters; horned ones only on their left side."""
    gpos = font["GPOS"].table
    for lookup in gpos.LookupList.Lookup:
        if lookup.LookupType != 2:
            continue
        for st in lookup.SubTable:
            if st.Format != 2:
                continue
            c1, c2 = st.ClassDef1.classDefs, st.ClassDef2.classDefs
            cov = st.Coverage.glyphs
            cmap = font.getBestCmap()
            for name, (letter, right_side) in kern_like.items():
                src = cmap[ord(letter)]
                if src in c2:
                    c2[name] = c2[src]
                if right_side and src in cov:
                    if src in c1:
                        c1[name] = c1[src]
                    cov.append(name)
            order = {g: i for i, g in enumerate(font.getGlyphOrder())}
            st.Coverage.glyphs = sorted(set(cov), key=order.get)


def add_marks(font, face, placer):
    """mark and mkmk lookups for the combining marks, merged into the existing GPOS.

    A text that keeps its marks apart (o + U+031B + U+0301) is usually composed back into one
    letter by the shaper, as the font has every precomposed form; these anchors cover the rest.
    """
    top_marks = {"gravecomb": "grave", "acutecomb": "acute", "tildecomb": "tilde",
                 "uni0302": "circ", "uni0306": "breve", "uni0309": "hook"}
    gap_lc = placer.lc["circ"][1] - face.xheight
    gap = face.mark * 0.5
    lines, mkmk = [], []
    for g, kind in top_marks.items():
        b = placer.comb_box[kind]
        dx, bottom = placer.lc[kind]
        ax = round((b[0] + b[2]) / 2 - dx)
        # The point that lands on a letter's top anchor puts the mark where Jost puts it on o.
        lines.append(f"markClass {g} <anchor {ax} {round(b[1] - gap_lc)}> @TOP;")
        # A second mark stacks above this one.
        mkmk.append(f"pos mark {g} <anchor {ax} {round(b[3] + gap - gap_lc)}> mark @TOP;")
    db = placer.comb_box["dot"]
    lines.append(f"markClass uni0323 <anchor {round((db[0] + db[2]) / 2)} 0> @BOTTOM;")
    cap_anchor = placer.uc["circ"][1] - gap_lc
    tops, bottoms = [], []
    cmap = font.getBestCmap()
    for code in sorted(cmap):
        ch = chr(code)
        name = cmap[code]
        if unicodedata.category(ch) not in ("Lu", "Ll") or not code < 0x250:
            continue
        if name not in HORNED.values() and unicodedata.normalize("NFD", ch) != ch:
            continue  # letters that already carry a mark
        if font["glyf"][name].isComposite():
            continue
        x0, _, x1, y1 = bbox(font, name)
        cx = placer.centre_x(name)
        if ch.isupper():
            ty = cap_anchor
        elif name in HORNED.values() or y1 <= face.xheight + 15:
            ty = face.xheight
        else:
            ty = y1 + 30  # ascenders and the dotted i, j
        tops.append(f"pos base {name} <anchor {round(cx)} {round(ty)}> mark @TOP;")
        bottoms.append(f"pos base {name} <anchor {round((x0 + x1) / 2)} 0> mark @BOTTOM;")
    fea = "\n".join(lines) + "\nfeature mark {\n" + "\n".join(tops + bottoms) + \
        "\n} mark;\nfeature mkmk {\n" + "\n".join(mkmk) + "\n} mkmk;\n"
    merge_gpos(font, fea)


def merge_gpos(font, fea):
    """Compile `fea` alone, then append its lookups and features to the font's own GPOS."""
    scratch = TTFont()
    scratch.setGlyphOrder(font.getGlyphOrder())
    scratch["GDEF"] = font["GDEF"]
    addOpenTypeFeaturesFromString(scratch, fea, tables=["GPOS"])
    new = scratch["GPOS"].table
    old = font["GPOS"].table
    base_lookup = len(old.LookupList.Lookup)
    old.LookupList.Lookup.extend(new.LookupList.Lookup)
    old.LookupList.LookupCount = len(old.LookupList.Lookup)
    for rec in new.FeatureList.FeatureRecord:
        rec.Feature.LookupListIndex = [i + base_lookup for i in rec.Feature.LookupListIndex]
        rec.Feature.LookupCount = len(rec.Feature.LookupListIndex)
    records = old.FeatureList.FeatureRecord + new.FeatureList.FeatureRecord
    tags = [r.FeatureTag for r in old.FeatureList.FeatureRecord]
    order = sorted(range(len(records)), key=lambda i: (records[i].FeatureTag, i))
    remap = {old_i: new_i for new_i, old_i in enumerate(order)}
    old.FeatureList.FeatureRecord = [records[i] for i in order]
    old.FeatureList.FeatureCount = len(records)
    added = [remap[len(tags) + i] for i in range(len(new.FeatureList.FeatureRecord))]
    for srec in old.ScriptList.ScriptRecord:
        systems = [srec.Script.DefaultLangSys] + [l.LangSys for l in srec.Script.LangSysRecord]
        for ls in systems:
            if ls is None:
                continue
            ls.FeatureIndex = sorted([remap[i] for i in ls.FeatureIndex] + added)
            ls.FeatureCount = len(ls.FeatureIndex)
    gdef = font["GDEF"].table
    classes = gdef.GlyphClassDef.classDefs
    for g in ("uni0309", "uni0323", "uni031B"):
        classes[g] = 3


# ---------------------------------------------------------------- whole build

def subset_codepoints():
    cps = set(range(0x20, 0x7F)) | set(range(0xA0, 0x180))
    cps |= {0x1A0, 0x1A1, 0x1AF, 0x1B0, 0x218, 0x219, 0x21A, 0x21B}
    cps |= set(range(0x1EA0, 0x1EFA))
    cps |= {0x300, 0x301, 0x302, 0x303, 0x304, 0x306, 0x307, 0x308, 0x309, 0x30A, 0x30B, 0x30C,
            0x31B, 0x323, 0x326, 0x327, 0x328}
    cps |= set(range(0x2010, 0x2023)) | {0x2026, 0x2030, 0x2039, 0x203A, 0x2044, 0x20AB, 0x20AC,
                                         0x2122, 0x2190, 0x2191, 0x2192, 0x2193, 0x2194, 0x2212,
                                         0x2713, 0x2219, 0x2260, 0x2264, 0x2265, 0x221E, 0x2248}
    return cps


def build(weight, source_bytes):
    vf = TTFont(io.BytesIO(source_bytes))
    font = instancer.instantiateVariableFont(vf, {"wght": weight}, updateFontNames=False)
    face = Face(font)
    draw_marks(face)
    draw_horned(face)
    cmap_add = {0x309: "uni0309", 0x323: "uni0323", 0x31B: "uni031B"}
    draw_symbols(face)
    for code in (0x2190, 0x2191, 0x2192, 0x2193, 0x2194, 0x2713):
        cmap_add[code] = f"uni{code:04X}"
    viet, kern_like = add_vietnamese(face)
    cmap_add.update(viet)
    add_dong(face, cmap_add)
    set_cmap(font, cmap_add)
    # Jost kerns y and Y with acute or grave not at all; give them y's classes too.
    for ch in "\u00fd\u1ef3\u00dd\u1ef2":
        name = glyph_for(font, ch)
        kern_like.setdefault(name, ("y" if ch.islower() else "Y", True))
    extend_kerning(font, kern_like)
    add_marks(font, face, Placer(face))
    font = subset(font)
    rename(font, weight)
    font["head"].created = font["head"].modified = HEAD_TIME
    font.recalcTimestamp = False
    if "DSIG" in font:
        del font["DSIG"]
    return font, face


def subset(font):
    opts = Options()
    opts.layout_features = ["kern", "mark", "mkmk", "tnum"]
    opts.name_IDs = ["*"]
    opts.name_languages = ["*"]
    opts.notdef_outline = True
    opts.glyph_names = False
    opts.drop_tables += ["DSIG"]
    opts.hinting = True
    sub = Subsetter(opts)
    sub.populate(unicodes=sorted(subset_codepoints()))
    buf = io.BytesIO()
    font.save(buf)
    f = TTFont(io.BytesIO(buf.getvalue()))
    sub.subset(f)
    return f


def rename(font, weight):
    style = {400: "Regular", 500: "Medium", 600: "SemiBold", 700: "Bold", 800: "ExtraBold"}[weight]
    name = font["name"]
    for rec in list(name.names):
        if rec.nameID in (1, 2, 3, 4, 6, 16, 17, 25) or rec.nameID >= 256:
            name.removeNames(nameID=rec.nameID)
    full = f"{FAMILY} {style}"
    ps = f"{FAMILY}-{style}"
    version = name.getDebugName(5) or "Version 3.710"
    # Ids 1 and 2 use the RIBBI convention; 16 and 17 carry the real weight name.
    legacy_family = FAMILY if style in ("Regular", "Bold") else f"{FAMILY} {style}"
    legacy_style = style if style in ("Regular", "Bold") else "Regular"
    name.setName(legacy_family, 1, 3, 1, 0x409)
    name.setName(legacy_style, 2, 3, 1, 0x409)
    name.setName(f"{version.split(';')[0]};Plumemu;{ps}", 3, 3, 1, 0x409)
    name.setName(full, 4, 3, 1, 0x409)
    name.setName(ps, 6, 3, 1, 0x409)
    name.setName(FAMILY, 16, 3, 1, 0x409)
    name.setName(style, 17, 3, 1, 0x409)
    name.setName(f"{version.split(';')[0]}; Plumemu build: Vietnamese, arrows, check mark", 5, 3, 1, 0x409)
    font["OS/2"].usWeightClass = weight
    sel = font["OS/2"].fsSelection & ~0b1100001
    sel |= 0x20 if style == "Bold" else (0x40 if style == "Regular" else 0)
    font["OS/2"].fsSelection = sel
    font["head"].macStyle = 1 if style == "Bold" else 0
    font["post"].formatType = 3.0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=REPO / "core-ui/src/main/res/font")
    args = ap.parse_args()
    data = SOURCE.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != SOURCE_SHA256:
        sys.exit(f"{SOURCE} has sha256 {digest}, expected {SOURCE_SHA256}: not the Jost release this "
                 "recipe was measured on.")
    args.out.mkdir(parents=True, exist_ok=True)
    for weight in WEIGHTS:
        font, _ = build(weight, data)
        path = args.out / f"jost_{weight}.ttf"
        font.save(path)
        glyf = font["glyf"]
        top_name = max(font.getGlyphOrder(), key=lambda g: getattr(glyf[g], "yMax", 0))
        top = glyf[top_name].yMax
        reach = f"highest {top_name} at {top}" + (f", {top - ASCENT} above the ascent" if top > ASCENT else "")
        print(f"{reach}; ", end="")
        print(f"{path.relative_to(REPO) if path.is_relative_to(REPO) else path}: "
              f"{path.stat().st_size} bytes, {len(font.getGlyphOrder())} glyphs")


if __name__ == "__main__":
    main()
