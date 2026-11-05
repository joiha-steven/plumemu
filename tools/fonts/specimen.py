#!/usr/bin/env python3
"""Render a specimen of the built Jost beside Plum Sans: Vietnamese and a few Plumemu strings.

    python3 tools/fonts/specimen.py OUT.png [FONT_DIR]

FONT_DIR holds jost_{400,700}.ttf (default: core-ui/src/main/res/font). Lines are shaped and
drawn by HarfBuzz's hb-view (Homebrew `harfbuzz`) and laid out with Pillow.
"""
import html
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parent.parent.parent
FONTS = REPO / "core-ui/src/main/res/font"
# The first line shows the new letters and symbols; the others are Plumemu's own Vietnamese
# strings (by resource name), read from the translation so this file stays in English.
SAMPLE = " ".join(chr(c) for c in (0x1A0, 0x1AF, 0x1A1, 0x1B0, 0x1EA4, 0x1EA6, 0x1EA8, 0x1EAA, 0x1EAC,
                                   0x1EAE, 0x1EB0, 0x1EB2, 0x1EB4, 0x1EB6, 0x1EA3, 0x1EA1, 0x1EC9,
                                   0x1ECB, 0x1ECF, 0x1ECD, 0x1EE7, 0x1EE5, 0x1EF7, 0x1EF5, 0x2192,
                                   0x2191, 0x2193, 0x2713))
STRINGS = [(40, "setup_load_failed_body"), (40, "setup_folder_sd_hint"), (30, "setup_folder_title"),
           (30, "setup_folder_point_use"), (30, "setup_message_save_failed"),
           (30, "home_favorites_empty")]


def vietnamese(name):
    text = (REPO / "app/src/main/res/values-vi/strings.xml").read_text(encoding="utf-8")
    m = re.search(rf'<string name="{name}">(.*?)</string>', text, re.S)
    return html.unescape(m.group(1)).replace("\\'", "'").replace("%1$s", "A")


def strip(font, size, text, tmp):
    out = Path(tmp) / f"{abs(hash((str(font), size, text)))}.png"
    subprocess.run(["hb-view", str(font), text, f"--font-size={size}", "--margin=0",
                    "--foreground=1E1F24", "--background=FFFFFF", "-o", str(out)], check=True)
    return Image.open(out).convert("RGB")


def main():
    out = Path(sys.argv[1])
    jost_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else FONTS
    faces = []
    for weight in (400, 700):
        faces.append((f"Jost (Plumemu build) {weight}", jost_dir / f"jost_{weight}.ttf"))
        faces.append((f"Plum Sans {weight}", FONTS / f"plum_sans_{weight}.ttf"))
    label_font = ImageFont.truetype(str(FONTS / "plum_sans_600.ttf"), 22)
    blocks = []
    with tempfile.TemporaryDirectory() as tmp:
        for label, font in faces:
            lines = [(64, SAMPLE)] + [(size, vietnamese(name)) for size, name in STRINGS]
            blocks.append((label, [strip(font, size, text, tmp) for size, text in lines]))
    pad, gap = 48, 14
    width = max(im.width for _, ims in blocks for im in ims) + 2 * pad
    height = pad + sum(40 + sum(im.height + gap for im in ims) + 36 for _, ims in blocks) + pad
    sheet = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(sheet)
    y = pad
    for label, ims in blocks:
        draw.text((pad, y), label, font=label_font, fill=(62, 123, 224))
        y += 40
        for im in ims:
            sheet.paste(im, (pad, y))
            y += im.height + gap
        y += 22
        draw.line((pad, y, width - pad, y), fill=(220, 222, 228), width=2)
        y += 14
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
