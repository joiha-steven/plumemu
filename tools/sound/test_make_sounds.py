"""Checks the generated interface sounds: python3 -m unittest discover -s tools/sound"""
import math
import struct
import unittest
import wave

import make_sounds as gen

# name -> (min ms, max ms, expected peak dBFS)
EXPECTED = {
    "ui_move": (25, 40, -20),
    "ui_confirm": (100, 130, -14),
    "ui_back": (90, 110, -17),
}


def load(name):
    path = gen.OUT_DIR / f"{name}.wav"
    with wave.open(str(path), "rb") as f:
        params = f.getparams()
        raw = f.readframes(params.nframes)
    samples = struct.unpack(f"<{params.nframes}h", raw)
    return path, params, samples


class SoundFiles(unittest.TestCase):
    def test_files_match_generator(self):
        # Hand-edited or stale files fail: the committed bytes must equal a fresh render.
        for name, make in gen.SOUNDS.items():
            path, _, samples = load(name)
            fresh = [round(s * 32767) for s in make()]
            self.assertEqual(list(samples), fresh, name)

    def test_format_duration_peak_size(self):
        for name, (low_ms, high_ms, peak_db) in EXPECTED.items():
            path, p, samples = load(name)
            self.assertEqual((p.nchannels, p.sampwidth, p.framerate), (1, 2, 22050), name)
            ms = 1000 * p.nframes / p.framerate
            self.assertTrue(low_ms <= ms <= high_ms, f"{name} {ms:.1f} ms")
            peak = 20 * math.log10(max(abs(s) for s in samples) / 32767)
            self.assertAlmostEqual(peak, peak_db, delta=0.3, msg=name)
            self.assertLess(path.stat().st_size, 8 * 1024, name)

    def test_no_click_at_start_or_end(self):
        for name in EXPECTED:
            _, _, samples = load(name)
            # DC step at the edges would click: the first and last samples must be silent, and
            # the first millisecond must ramp (22 samples), not jump.
            self.assertEqual(samples[0], 0, name)
            self.assertLessEqual(abs(samples[-1]), 2, name)
            self.assertLess(max(abs(s) for s in samples[:3]), 0.1 * max(abs(s) for s in samples), name)
            self.assertAlmostEqual(sum(samples) / len(samples) / 32767, 0, delta=0.002, msg=name)


if __name__ == "__main__":
    unittest.main()
