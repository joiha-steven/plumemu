#!/usr/bin/env python3
"""Triage screenshot goldens that differ from what the current code renders.

Roborazzi's compare/verify tasks write `<name>_actual.png` (and `_compare.png`) to
`<module>/build/outputs/roborazzi/` for every screenshot that differs from its golden in
`<module>/src/test/screenshots/<name>.png`; unchanged ones write nothing. This script pairs
them and reports, per pair: the max channel delta, the pixels over 8/255 (`over8`), a blank
band (a run of flat rows covering >= 10% of the height where the golden has content) and a
near-blank picture (the golden has content, the actual one hardly any).

Usage: python3 scripts/golden-triage.py [--root DIR] [--limit N] [--rerecord-safe]
Exit 0: every pair at or under the limit and none blank; 1: otherwise; 2: cannot run.
Standard library only; reads 8-bit RGB/RGBA non-interlaced PNGs and refuses others.
"""
import argparse
import collections
import re
import struct
import sys
import zlib
from pathlib import Path

BAND_FRACTION = 0.10     # a flat run this tall (of the height) is a blank band
NEAR_BLANK_STD = 2.0     # luma standard deviation under this is "no content"
OUT_SUBDIR = Path("build/outputs/roborazzi")
GOLDEN_SUBDIR = Path("src/test/screenshots")
BUCKETS = [(0, 0), (1, 2), (3, 8), (9, 32), (33, 128), (129, 255)]


class Unsupported(Exception):
    pass


def decode_png(data):
    """Return (width, height, channels, rows) with rows a list of bytes of w*channels."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise Unsupported("not a PNG file")
    pos, idat, ihdr = 8, [], None
    while pos + 8 <= len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if kind == b"IHDR":
            ihdr = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat.append(body)
        elif kind == b"IEND":
            break
    if ihdr is None or not idat:
        raise Unsupported("PNG without IHDR or IDAT")
    w, h, depth, ctype, _comp, _filt, interlace = ihdr
    if depth != 8 or ctype not in (2, 6) or interlace != 0:
        raise Unsupported(f"only 8-bit RGB/RGBA non-interlaced PNG is supported "
                          f"(bit depth {depth}, colour type {ctype}, interlace {interlace})")
    ch = 3 if ctype == 2 else 4
    stride = w * ch
    raw = zlib.decompress(b"".join(idat))
    if len(raw) != h * (stride + 1):
        raise Unsupported("PNG pixel data has the wrong length")
    rows, prev = [], bytearray(stride)
    for y in range(h):
        off = y * (stride + 1)
        ft, line = raw[off], bytearray(raw[off + 1:off + 1 + stride])
        if ft == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 255
        elif ft == 2:
            line = bytearray((a + b) & 255 for a, b in zip(line, prev))
        elif ft == 3:
            for i in range(stride):
                left = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 255
        elif ft == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[i] = (line[i] + pred) & 255
        elif ft != 0:
            raise Unsupported(f"unknown PNG filter type {ft}")
        rows.append(bytes(line))
        prev = line
    return w, h, ch, rows


def to_rgba(rows, w, ch):
    if ch == 4:
        return rows
    out = []
    for r in rows:
        o = bytearray(b"\xff" * (w * 4))
        o[0::4], o[1::4], o[2::4] = r[0::3], r[1::3], r[2::3]
        out.append(bytes(o))
    return out


def load(path):
    try:
        w, h, ch, rows = decode_png(Path(path).read_bytes())
    except Unsupported as e:
        raise Unsupported(f"{path}: {e}")
    except (zlib.error, struct.error, OSError) as e:
        raise Unsupported(f"{path}: cannot read ({e})")
    return w, h, to_rgba(rows, w, ch)


def flat_runs(rows):
    """Yield (start, end_exclusive, colour) for runs of rows that are one flat colour."""
    start, colour = None, None
    for y, r in enumerate(rows + [None]):
        c = r[:4] if r is not None and r == r[:4] * (len(r) // 4) else None
        if c is not None and c == colour:
            continue
        if colour is not None:
            yield start, y, colour
        start, colour = y, c


def luma_std(rows):
    n = s = s2 = 0
    for r in rows[::2]:
        g = r[1::8]  # every other pixel's green channel
        n += len(g)
        s += sum(g)
        s2 += sum(v * v for v in g)
    if n == 0:
        return 0.0
    return max(s2 / n - (s / n) ** 2, 0) ** 0.5


def compare(golden, actual):
    gw, gh, grows = golden
    aw, ah, arows = actual
    if (gw, gh) != (aw, ah):
        return {"max": 255, "over8": max(gw * gh, aw * ah), "size": True,
                "band": False, "blank": False}
    mx = over8 = 0
    for ra, rg in zip(arows, grows):
        if ra == rg:
            continue
        d = [abs(a - b) for a, b in zip(ra, rg)]
        mx = max(mx, max(d))
        over8 += sum(1 for p, q, r, s in zip(d[0::4], d[1::4], d[2::4], d[3::4])
                     if max(p, q, r, s) > 8)
    band = False
    for s, e, colour in flat_runs(arows):
        if (e - s) >= BAND_FRACTION * ah and any(g != colour * gw for g in grows[s:e]):
            band = True
            break
    blank = luma_std(arows) < NEAR_BLANK_STD <= luma_std(grows)
    return {"max": mx, "over8": over8, "size": False, "band": band, "blank": blank}


def find_pairs(root):
    """Return (pairs, found_any_output_dir); pair = (module, name, golden, actual)."""
    pairs, found = [], False
    for out in sorted(root.glob("*/" + str(OUT_SUBDIR))):
        found = True
        module = out.parent.parent.parent
        for actual in sorted(out.rglob("*_actual.png")):
            rel = actual.relative_to(out)
            name = rel.with_name(rel.name[:-len("_actual.png")])
            golden = module / GOLDEN_SUBDIR / (str(name) + ".png")
            pairs.append((module, str(name), golden, actual))
    return pairs, found


def test_classes(module, name):
    """Gradle class names of the test file whose literal is the longest stem of `name`."""
    best, hit = 0, []
    for src in (module / "src/test").rglob("*.kt"):
        text = src.read_text(errors="replace")
        for lit in set(re.findall(r'"([a-z0-9_]{3,})"', text)):
            if (name == lit or name.startswith(lit + "_")) and len(lit) >= best:
                if len(lit) > best:
                    hit, best = [], len(lit)
                hit.append((src, text))
    classes = []
    for src, text in hit:
        pkg = re.search(r"^package\s+([\w.]+)", text, re.M)
        for m in re.finditer(r"^(?:(?:internal|public|open|final)\s+)*class\s+(\w+)", text, re.M):
            classes.append((pkg.group(1) + "." if pkg else "") + m.group(1))
    return classes


def rerecord_command(root, rows):
    """rows: (module, name, ok). A class is safe when every pair mapped to it is ok."""
    by_class = collections.defaultdict(lambda: [True, None])
    unmapped = []
    for module, name, ok in rows:
        classes = test_classes(module, name)
        if not classes:
            unmapped.append(name)
            continue
        for c in classes:
            by_class[c][0] &= ok
            by_class[c][1] = module
    parts = collections.defaultdict(list)
    for c, (ok, module) in sorted(by_class.items()):
        if ok:
            parts[module].append(c)
    cmd = ["./gradlew"]
    for module, classes in sorted(parts.items()):
        cmd.append(f":{module.name}:recordRoborazziDebug")
        cmd += [f"--tests {c}" for c in classes]
    return (" ".join(cmd) if len(cmd) > 1 else None), unmapped


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--root", default=str(Path(__file__).resolve().parent.parent))
    ap.add_argument("--limit", type=int, default=8)
    ap.add_argument("--rerecord-safe", action="store_true")
    a = ap.parse_args(argv)
    root = Path(a.root)
    pairs, found = find_pairs(root)
    if not found:
        print(f"golden-triage: no {OUT_SUBDIR} in any module of {root}; "
              "run ./gradlew compareRoborazziDebug first", file=sys.stderr)
        return 2
    hist = collections.Counter()
    problems, rows = [], []
    try:
        for module, name, golden, actual in pairs:
            rel = str((module / GOLDEN_SUBDIR / (name + ".png")).relative_to(root))
            if not golden.is_file():
                problems.append(f"{rel}: no golden (new picture)")
                rows.append((module, name, False))
                continue
            r = compare(load(golden), load(actual))
            hist[next(i for i, (lo, hi) in enumerate(BUCKETS) if lo <= r["max"] <= hi)] += 1
            bad = r["max"] > a.limit or r["band"] or r["blank"]
            if bad:
                flags = "".join([" [size-differs]" if r["size"] else "",
                                 " [blank-band]" if r["band"] else "",
                                 " [near-blank]" if r["blank"] else ""])
                problems.append(f"{rel}: max={r['max']} over8={r['over8']}{flags}")
            rows.append((module, name, not bad))
    except Unsupported as e:
        print(f"golden-triage: {e}", file=sys.stderr)
        return 2
    print(f"golden-triage: {len(pairs)} pairs, limit {a.limit}, {len(problems)} over the limit "
          "or blank")
    for i, (lo, hi) in enumerate(BUCKETS):
        print(f"  max delta {lo}-{hi}: {hist[i]}")
    for line in problems:
        print(line)
    if a.rerecord_safe:
        cmd, unmapped = rerecord_command(root, rows)
        for n in unmapped:
            print(f"no test class found for {n}; not in the command")
        print(cmd if cmd else "golden-triage: no test class with only safe pairs")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
