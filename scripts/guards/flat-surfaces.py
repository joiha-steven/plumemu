#!/usr/bin/env python3
"""No gradient on a surface (docs/design/rules.md > Depth, core-ui's FlatSurface).

A button, key cap, chip, row slab, card, tile, field or tray has one flat face; its depth comes from its
edges and shadows. In Kotlin main sources, fails on a gradient brush or shader (`Brush.verticalGradient`,
`horizontalGradient`, `linearGradient`, `radialGradient`, `sweepGradient`, `android.graphics`
`LinearGradient`, `RadialGradient`, `SweepGradient`) outside the files listed in ALLOWED, where a
gradient is not a surface: list edges that fade, scrims, shadows and glows, the page's grain and backdrop,
charts, a colour slider's spectrum, the slab's top line fading down its corners and the poster covers that stand in for box
art. A new such use is added to ALLOWED with its reason. Test sources are exempt.
"""

import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "flat-surfaces"

PATTERN = re.compile(
    r"\bBrush\s*\.\s*(?:verticalGradient|horizontalGradient|linearGradient|radialGradient|sweepGradient)\b"
    r"|\b(?:LinearGradient|RadialGradient|SweepGradient)\s*\("
)

# Path suffixes where a gradient is not a surface, and why.
ALLOWED = {
    "core-ui/src/main/java/com/plumemu/coreui/components/EdgeFade.kt": "list edges that fade (an alpha mask)",
    "core-ui/src/main/java/com/plumemu/coreui/components/PageGrain.kt": "the page's grain seen through a fading edge",
    "core-ui/src/main/java/com/plumemu/coreui/components/GlidingPill.kt": "the pill's shadow tails fading at a clip (an alpha mask)",
    "core-ui/src/main/java/com/plumemu/coreui/components/keyboard/PlumKeyboard.kt": "the tray's upward shadow",
    "core-ui/src/main/java/com/plumemu/coreui/theme/FocusSlab.kt": "the slab's top line fading down its corners (an edge, not a face)",
    "app/src/main/java/com/plumemu/settings/screen/SettingsColorSliderRows.kt": "a colour slider's spectrum: the values it picks",
    "core-ui/src/main/java/com/plumemu/coreui/components/PlumSlider.kt": "the hue slider's spectrum: the values it picks",
    "app/src/main/java/com/plumemu/update/ui/ReleaseNotes.kt": "the notes' fade at a scrolled edge (an alpha mask)",
    "app/src/main/java/com/plumemu/setup/SetupScreen.kt": "setup's fade at a scrolled edge (an alpha mask)",
    "app/src/main/java/com/plumemu/secondscreen/SecondScreenGameCard.kt": "the description's fade where it is cut (an alpha mask)",
    "app/src/main/java/com/plumemu/secondscreen/SecondScreenBackdrop.kt": "the page's accent glow",
    "app/src/main/java/com/plumemu/secondscreen/InGameBatteryChart.kt": "a chart's areas, its line ahead and the now dot's glow",
    "app/src/main/java/com/plumemu/home/BoxFrontLayout.kt": "a poster cover standing in for box art (RULES)",
    "app/src/main/java/com/plumemu/home/AppCardLayout.kt": "an app game's icon card standing in for box art (RULES)",
    "app/src/main/java/com/plumemu/home/BenchmarkCoverArt.kt": "benchmark-only stand-in pictures",
}


def main(argv):
    report = guardlib.Reporter(NAME)
    for full, rel in guardlib.files_from_args(argv):
        if not guardlib.is_kotlin_main_source(rel) or any(rel.endswith(path) for path in ALLOWED):
            continue
        code, bare = guardlib.strip_kotlin(guardlib.read_text(full))
        lines = code.splitlines()
        seen = set()
        for match in PATTERN.finditer(bare):
            number = guardlib.line_of(code, match.start())
            if number in seen:
                continue
            seen.add(number)
            report.fail(rel, number, f"gradient '{' '.join(match.group(0).split())}' on a surface: draw one flat colour "
                                     f"(FlatSurface.mean) with edges and shadows  | {lines[number - 1].strip()}")
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
