#!/usr/bin/env python3
"""Generates Plumemu's three interface sounds into app/src/main/res/raw/.

These sounds are synthesized here from sine waves and light FM (a sine bending the phase of another
sine). They are our own work: nothing is copied, sampled or transcribed from any console's menu
sounds, so there is nothing to credit in THIRD_PARTY.md. The WAV files are generated output; never
edit them by hand, change the numbers below and run

    python3 tools/sound/make_sounds.py

Python standard library only. Output: 16-bit mono PCM, 22,050 Hz.

Design (owner, 2026-10-26: "in the spirit of the PSP's menu sounds, but not a copy"). What we take
from that spirit is only the character: soft, short, airy, slightly glassy, a quick decay, nothing
that startles. Every pitch, envelope and length below is chosen here. Glassy means a high partial at
an inharmonic ratio (so it rings like a glass edge, not a beep), or a little FM whose modulation
index dies away faster than the tone, which gives the tick its bright first instant and a clean tail.
Airy means a faint high partial well above the tone, and no sub-bass at all.

- ui_move: a very soft tick, 32 ms. 2.3 kHz carrier with FM at 1.5x (index 1.4, gone in about 6 ms)
  and a 5.1 kHz glass partial at 8 percent. Decay 8 ms. Peak -20 dBFS.
- ui_confirm: a brighter two-partial chime, 120 ms. 1.48 kHz, then 2.22 kHz (a fifth above)
  entering 14 ms later, both with a trace of FM shimmer, and a 4.5 kHz glass partial. Decay 34 ms.
  Peak -14 dBFS.
- ui_back: a lower falling tone, 100 ms. 880 Hz and a soft 1.32 kHz partial gliding down 25 percent
  over the sound (1.0 to 0.75). Decay 30 ms. Peak -17 dBFS.

Every sound has a 1 ms fade-in (no pop on the first sample), an exponential decay, and a short
fade-out to exact silence (no click at the end). Levels are peak dBFS, deliberately low: the app
scales them again with Settings > Advanced > Sounds (Quiet is 35 percent, Loud is full), and they
sit under game audio and the person's own media volume.
"""
import math
import struct
import sys
import wave
from pathlib import Path

RATE = 22050
FADE_IN_S = 0.001   # 1 ms: the first sample is 0, so the sound never pops on
FADE_OUT_S = 0.004  # 4 ms: the last samples reach 0, so the sound never clicks off

OUT_DIR = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "raw"


def dbfs(db):
    """Linear amplitude (0..1) of a peak level given in dBFS."""
    return 10 ** (db / 20)


def render(duration_s, tau_s, partials, glide=None):
    """Sum of partials under an exponential decay, as floats in -1..1 before levelling.

    partials: dicts with `freq` (Hz) and `amp` (relative), and optionally `delay` (seconds before the
    partial enters), and FM: `fm_ratio` (modulator frequency over `freq`), `fm_index` (peak phase
    deviation, radians) and `fm_tau` (seconds for the index to fall to 1/e).
    tau_s: decay time constant; amplitude falls to 1/e every tau_s seconds.
    glide: optional (start_ratio, end_ratio) multiplying every frequency, linear in time (the
    phase is integrated, so a falling pitch stays smooth).
    """
    count = int(duration_s * RATE)
    phases = [0.0] * len(partials)
    mod_phases = [0.0] * len(partials)
    samples = []
    for i in range(count):
        t = i / RATE
        ratio = 1.0
        if glide:
            ratio = glide[0] + (glide[1] - glide[0]) * (i / max(count - 1, 1))
        value = 0.0
        for k, part in enumerate(partials):
            freq = part["freq"] * ratio
            phases[k] += 2 * math.pi * freq / RATE
            bend = 0.0
            if part.get("fm_index"):
                mod_phases[k] += 2 * math.pi * freq * part["fm_ratio"] / RATE
                bend = part["fm_index"] * math.exp(-t / part["fm_tau"]) * math.sin(mod_phases[k])
            entered = t - part.get("delay", 0.0)
            if entered < 0:
                continue
            # A late partial fades in over 1 ms like the whole sound does, so it never clicks in.
            value += part["amp"] * min(1.0, entered / FADE_IN_S) * math.sin(phases[k] + bend)
        envelope = math.exp(-t / tau_s)
        envelope *= min(1.0, t / FADE_IN_S)
        envelope *= min(1.0, (duration_s - t) / FADE_OUT_S)
        samples.append(value * envelope)
    return samples


def level(samples, peak_db):
    """Scales so the highest sample sits at peak_db dBFS."""
    top = max(abs(s) for s in samples)
    gain = dbfs(peak_db) / top
    return [s * gain for s in samples]


def write(name, samples):
    path = OUT_DIR / f"{name}.wav"
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", round(s * 32767)) for s in samples))
    return path


# name -> samples. The numbers are explained in the docstring.
SOUNDS = {
    "ui_move": lambda: level(
        render(
            0.032,
            0.008,
            [
                {"freq": 2300, "amp": 1.0, "fm_ratio": 1.5, "fm_index": 1.4, "fm_tau": 0.006},
                {"freq": 5100, "amp": 0.08},
            ],
        ),
        -20,
    ),
    "ui_confirm": lambda: level(
        render(
            0.120,
            0.034,
            [
                {"freq": 1480, "amp": 1.0, "fm_ratio": 2.0, "fm_index": 0.5, "fm_tau": 0.020},
                {"freq": 2220, "amp": 0.75, "delay": 0.014, "fm_ratio": 2.0, "fm_index": 0.4, "fm_tau": 0.020},
                {"freq": 4500, "amp": 0.07, "delay": 0.014},
            ],
        ),
        -14,
    ),
    "ui_back": lambda: level(
        render(
            0.100,
            0.030,
            [
                {"freq": 880, "amp": 1.0},
                {"freq": 1320, "amp": 0.3},
            ],
            glide=(1.0, 0.75),
        ),
        -17,
    ),
}


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, make in SOUNDS.items():
        path = write(name, make())
        print(f"{path.relative_to(OUT_DIR.parents[4])}: {path.stat().st_size} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
