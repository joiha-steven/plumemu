"""The `<queries>` manifest of the platforms module, written from the converted data.

From Android 11 (API 30) an app sees another app only if its manifest says so. Plumemu lists every
emulator package of the platform data and the MAIN/LAUNCHER intent (the Apps entry), never
QUERY_ALL_PACKAGES (docs/conventions/android.md). The manifest merger carries this file into the
app, so the list stays in step with the data without anyone editing it by hand.
"""

from xml.sax.saxutils import quoteattr

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Written by tools/platforms/convert.py from the platform data; never edit by hand.
     Lets Plumemu see the installed emulators and the apps for the Apps entry on Android 11
     and later without QUERY_ALL_PACKAGES. -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <queries>
"""

FOOTER = """        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>

</manifest>
"""


def emulator_packages(data):
    """Every package an emulator of [data] may be installed as (preferred build and variants), sorted."""
    packages = set()
    for emulator in data["emulators"]:
        if "package" in emulator:
            packages.add(emulator["package"])
        for variant in emulator.get("variants", []):
            packages.add(variant["package"])
    return sorted(packages)


def render(data):
    lines = [f"        <package android:name={quoteattr(package)} />\n" for package in emulator_packages(data)]
    return HEADER + "".join(lines) + FOOTER
