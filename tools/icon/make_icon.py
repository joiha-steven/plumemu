"""Draws the Plumemu launcher icon from one set of numbers (owner, 2026-10-22).

A plum that is also a round game button: a crimson circle, a darker crimson seam, an off-white
stem and leaf, on black. The shapes are exact circles and arcs, so the same geometry gives the
Android vector drawables (adaptive foreground, monochrome for themed icons) and the PNGs for
Android 6-7.1, which have no adaptive icons. Run from the repository root (not with -I, which
hides a Pillow installed for the user):

    python3 tools/icon/make_icon.py                  # writes app/src/main/res/...
    python3 tools/icon/make_icon.py --preview DIR    # also mask and small-size previews

Needs Pillow only for the PNGs and previews.
"""

import math
import os
import sys

# Colours: the app's default accent, a darker crimson for the seam, the app's light text colour.
BACKGROUND = "#000000"
PLUM = "#DA4655"
SEAM = "#A8323F"
LEAF = "#EDEDED"

# Geometry in units of the plum's radius, origin at the plum's centre, y down. Taken from the
# owner's ChatGPT sketch (Drive Projects/Plumemu/image), with the leaf a little smaller.
STEM_BOTTOM = (0.0, -0.97)  # flat end, hidden just inside the plum
STEM_LENGTH = 0.42
STEM_LEAN = math.radians(15)  # to the right
STEM_WIDTH = 0.10
LEAF_BASE = (0.12, -1.07)
LEAF_LENGTH = 0.90
LEAF_ANGLE = math.radians(25)  # above horizontal, to the right
LEAF_WIDTH = 0.34
SEAM_START = (-0.03, -0.88)
SEAM_END = (-0.52, 0.14)
SEAM_TURN = math.radians(45)  # the seam's direction turns this much from start to end
SEAM_WIDTH = 0.09

# Adaptive icons: 108 x 108 dp; launchers may show anything inside the centred 66 dp circle. The
# mark fits a 54 dp circle, so the leaf keeps clear of every mask's edge (measured on the previews).
CANVAS = 108.0
SAFE_RADIUS = 27.0


def stem_top():
    x, y = STEM_BOTTOM
    return (x + STEM_LENGTH * math.sin(STEM_LEAN), y - STEM_LENGTH * math.cos(STEM_LEAN))


def leaf_tip():
    x, y = LEAF_BASE
    return (x + LEAF_LENGTH * math.cos(LEAF_ANGLE), y - LEAF_LENGTH * math.sin(LEAF_ANGLE))


def arc_radius(chord, sagitta):
    return (chord * chord / 4 + sagitta * sagitta) / (2 * sagitta)


def seam_circle():
    """Centre and radius of the circular arc from SEAM_START to SEAM_END that turns SEAM_TURN."""
    (x1, y1), (x2, y2) = SEAM_START, SEAM_END
    chord = math.hypot(x2 - x1, y2 - y1)
    r = chord / (2 * math.sin(SEAM_TURN / 2))
    mx, my = (x1 + x2) / 2, (y1 + y2) / 2
    d = math.sqrt(max(r * r - chord * chord / 4, 0))
    # Perpendicular to the chord, on the side away from the plum's left edge (the arc bulges left)
    px, py = -(y2 - y1) / chord, (x2 - x1) / chord
    if px < 0:
        px, py = -px, -py
    return (mx + px * d, my + py * d), r


def outline_points():
    """Points on every shape's outline, to fit the mark in the safe circle."""
    pts = [(math.cos(a) * 1.0, math.sin(a) * 1.0) for a in (i * math.tau / 64 for i in range(64))]
    for poly in (leaf_polygon(1.0, (0, 0), 32), stem_polygon(1.0, (0, 0), 16)):
        pts.extend(poly)
    return pts


def placement():
    """Plum radius in dp and the plum centre's offset, so the mark sits centred in the safe circle."""
    pts = outline_points()
    best = None
    for i in range(-400, 401):
        dy = i / 1000
        far = max(math.hypot(x, y + dy) for x, y in pts)
        if best is None or far < best[0]:
            best = (far, dy)
    far, dy = best
    radius = SAFE_RADIUS / far
    return radius, (CANVAS / 2, CANVAS / 2 + dy * radius)


# Polygons (for the PNGs and the fit) -----------------------------------------------------------

def to_canvas(points, radius, centre):
    cx, cy = centre
    return [(cx + x * radius, cy + y * radius) for x, y in points]


def circle_polygon(radius, centre, n=256):
    cx, cy = centre
    return [(cx + radius * math.cos(a), cy + radius * math.sin(a)) for a in (i * math.tau / n for i in range(n))]


def arc_points(c, r, a1, a2, n):
    return [(c[0] + r * math.cos(a1 + (a2 - a1) * i / n), c[1] + r * math.sin(a1 + (a2 - a1) * i / n)) for i in range(n + 1)]


def leaf_polygon(radius, centre, n=64):
    (bx, by), (tx, ty) = LEAF_BASE, leaf_tip()
    r = arc_radius(LEAF_LENGTH, LEAF_WIDTH / 2)
    d = r - LEAF_WIDTH / 2
    mx, my = (bx + tx) / 2, (by + ty) / 2
    ux, uy = (tx - bx) / LEAF_LENGTH, (ty - by) / LEAF_LENGTH
    nx, ny = uy, -ux  # left of the direction of travel
    pts = []
    for side in (1, -1):
        c = (mx - side * nx * d, my - side * ny * d)
        a1 = math.atan2(by - c[1], bx - c[0]) if side == 1 else math.atan2(ty - c[1], tx - c[0])
        a2 = math.atan2(ty - c[1], tx - c[0]) if side == 1 else math.atan2(by - c[1], bx - c[0])
        # take the short way round
        while a2 - a1 > math.pi:
            a2 -= math.tau
        while a2 - a1 < -math.pi:
            a2 += math.tau
        pts.extend(arc_points(c, r, a1, a2, n)[:-1])
    return to_canvas(pts, radius, centre)


def stem_polygon(radius, centre, n=32):
    (bx, by), (tx, ty) = STEM_BOTTOM, stem_top()
    w = STEM_WIDTH / 2
    ux, uy = (tx - bx) / STEM_LENGTH, (ty - by) / STEM_LENGTH
    nx, ny = -uy, ux
    a0 = math.atan2(ny, nx)
    cap = arc_points((tx, ty), w, a0, a0 - math.pi, n)
    pts = [(bx + nx * w, by + ny * w)] + cap + [(bx - nx * w, by - ny * w)]
    return to_canvas(pts, radius, centre)


def seam_polygon(radius, centre, n=96):
    c, r = seam_circle()
    a1 = math.atan2(SEAM_START[1] - c[1], SEAM_START[0] - c[0])
    a2 = math.atan2(SEAM_END[1] - c[1], SEAM_END[0] - c[0])
    while a2 - a1 > math.pi:
        a2 -= math.tau
    while a2 - a1 < -math.pi:
        a2 += math.tau
    w = SEAM_WIDTH / 2
    outer = arc_points(c, r + w, a1, a2, n)
    inner = arc_points(c, r - w, a2, a1, n)
    step = 1 if a2 > a1 else -1
    end_cap = arc_points(SEAM_END, w, a2, a2 + step * math.pi, n // 4)
    start_cap = arc_points(SEAM_START, w, a1 + step * math.pi, a1 + step * math.tau, n // 4)
    return to_canvas(outer + end_cap + inner + start_cap, radius, centre)


# Vector drawables -------------------------------------------------------------------------------

def f(v):
    return f"{v:.3f}".rstrip("0").rstrip(".")


def circle_path(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0z"


def poly_path(points):
    head, *rest = points
    return f"M{f(head[0])},{f(head[1])}" + "".join(f"L{f(x)},{f(y)}" for x, y in rest) + "z"


def vector(paths, comment):
    body = "\n".join(paths)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Generated by tools/icon/make_icon.py; edit the numbers there, not this file. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}
</vector>
"""


def path(data, color, even_odd=False):
    rule = '\n        android:fillType="evenOdd"' if even_odd else ""
    return f'    <path\n        android:fillColor="{color}"{rule}\n        android:pathData="{data}" />'


def write_res(res):
    radius, centre = placement()
    plum = circle_path(centre[0], centre[1], radius)
    stem = poly_path(stem_polygon(radius, centre, 12))
    leaf = poly_path(leaf_polygon(radius, centre, 24))
    seam = poly_path(seam_polygon(radius, centre, 32))
    files = {
        "drawable/ic_launcher_foreground.xml": vector(
            [path(stem, LEAF), path(leaf, LEAF), path(plum, PLUM), path(seam, SEAM)],
            "The launcher icon's foreground: plum, seam, stem and leaf.",
        ),
        "drawable/ic_launcher_monochrome.xml": vector(
            # Android tints this one colour; the seam is a gap cut out of the plum
            [path(stem, "#FFFFFFFF"), path(leaf, "#FFFFFFFF"), path(plum + seam, "#FFFFFFFF", even_odd=True)],
            "Themed icons (Android 13+): the same shapes in one colour, the seam cut out.",
        ),
        "values/ic_launcher_background.xml": f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/icon/make_icon.py. -->
<resources>
    <color name="ic_launcher_background">{BACKGROUND}</color>
</resources>
""",
    }
    adaptive = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/icon/make_icon.py. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
    files["mipmap-anydpi-v26/ic_launcher.xml"] = adaptive
    files["mipmap-anydpi-v26/ic_launcher_round.xml"] = adaptive
    for name, text in files.items():
        os.makedirs(os.path.join(res, os.path.dirname(name)), exist_ok=True)
        with open(os.path.join(res, name), "w", encoding="utf-8") as fh:
            fh.write(text)
    return radius, centre


# PNGs -------------------------------------------------------------------------------------------

def render(size, shape, crop=72.0, scale=8, background=BACKGROUND, mono=False):
    """The icon as a launcher shows it: the centre `crop` dp of the canvas, cut to `shape`."""
    from PIL import Image, ImageDraw

    big = size * scale
    k = big / crop
    off = (CANVAS - crop) / 2
    radius, centre = placement()

    def px(points):
        return [((x - off) * k, (y - off) * k) for x, y in points]

    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    mask = Image.new("L", (big, big), 0)
    md = ImageDraw.Draw(mask)
    if shape == "circle":
        md.ellipse((0, 0, big - 1, big - 1), fill=255)
    elif shape == "square":
        md.rectangle((0, 0, big - 1, big - 1), fill=255)
    elif shape == "rounded":
        md.rounded_rectangle((0, 0, big - 1, big - 1), radius=big * 0.18, fill=255)
    elif shape == "squircle":
        n = 5
        pts = []
        for i in range(720):
            a = i * math.tau / 720
            c, s = math.cos(a), math.sin(a)
            pts.append((big / 2 + big / 2 * math.copysign(abs(c) ** (2 / n), c), big / 2 + big / 2 * math.copysign(abs(s) ** (2 / n), s)))
        md.polygon(pts, fill=255)
    elif shape == "teardrop":
        md.ellipse((0, 0, big - 1, big - 1), fill=255)
        md.rectangle((big / 2, big / 2, big - 1, big - 1), fill=255)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, big, big), fill=background)
    ink = "#FFFFFF" if mono else None
    d.polygon(px(stem_polygon(radius, centre)), fill=ink or LEAF)
    d.polygon(px(leaf_polygon(radius, centre)), fill=ink or LEAF)
    d.polygon(px(circle_polygon(radius, centre)), fill=ink or PLUM)
    d.polygon(px(seam_polygon(radius, centre)), fill=background if mono else SEAM)
    out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def write_pngs(res):
    for name, size in DENSITIES.items():
        folder = os.path.join(res, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        render(size, "rounded").save(os.path.join(folder, "ic_launcher.png"), optimize=True)
        render(size, "circle").save(os.path.join(folder, "ic_launcher_round.png"), optimize=True)


def write_preview(out):
    from PIL import Image, ImageDraw

    os.makedirs(out, exist_ok=True)
    render(512, "square", crop=CANVAS).save(os.path.join(out, "icon-full-108dp-512.png"))
    render(512, "rounded").save(os.path.join(out, "icon-512.png"))
    shapes = ["circle", "squircle", "rounded", "teardrop"]
    sheet = Image.new("RGBA", (4 * 220 + 40, 220 + 2 * 120 + 60), "#3A3D44")
    for i, s in enumerate(shapes):
        sheet.paste(render(200, s), (20 + i * 220, 20), render(200, s))
    for i, s in enumerate(shapes):
        for j, size in enumerate((48, 32)):
            im = render(size, s)
            sheet.paste(im, (20 + i * 220 + j * 70, 250), im)
        mono = render(48, s, mono=True, background="#00000000")
        sheet.paste(mono, (20 + i * 220 + 140, 250), mono)
    light = Image.new("RGBA", (4 * 220 + 40, 100), "#E8ECF0")
    for i, s in enumerate(shapes):
        im = render(48, s)
        light.paste(im, (20 + i * 220, 26), im)
    sheet.paste(light, (0, 330))
    ImageDraw.Draw(sheet)
    sheet.save(os.path.join(out, "icon-masks.png"))


def main(argv):
    res = os.path.join("app", "src", "main", "res")
    radius, centre = write_res(res)
    write_pngs(res)
    print(f"plum radius {radius:.2f} dp, centre {centre[0]:.2f},{centre[1]:.2f}; mark fits a {2 * SAFE_RADIUS:.0f} dp circle")
    if "--preview" in argv:
        write_preview(argv[argv.index("--preview") + 1])
        print("previews written")


if __name__ == "__main__":
    main(sys.argv[1:])
