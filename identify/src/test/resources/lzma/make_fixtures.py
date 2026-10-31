#!/usr/bin/env python3
"""Builds the LZMA / LZMA2 fixtures for LzmaDecoderTest from made-up content.

Python 3 standard library only (the `lzma` module wraps liblzma). Run it from anywhere:

    python3 identify/src/test/resources/lzma/make_fixtures.py

It rewrites the .bin files and manifest.txt next to itself. The output is deterministic for a
given liblzma version; the tests only check what the manifest says, so a re-run with another
liblzma version is fine as long as the files and the manifest are committed together.

Manifest: one fixture per line, `file kind size crc32 key=value ...`, where kind is
  alone      .lzma file: 5 property bytes, 8-byte little-endian size (-1: end marker), stream
  raw-eopm   raw LZMA1 stream with an end marker; props=<hex byte> dict=<bytes>
  raw        raw LZMA1 stream without an end marker, size known (how CHD stores a hunk);
             props=<hex byte> dict=<bytes>
  lzma2      raw LZMA2 chunks ending with a 0x00 control byte; dictprop=<0..40>
and prefix4096=<crc32> is the CRC-32 of the first 4,096 bytes when the content is longer.

How CHD stores LZMA (MAME src/lib/util/chdcodec.cpp, master, read 2026-10-21 and checked again
  in step I1d: the level is 6, not 8):
  - chd_lzma_compressor::configure_properties: LzmaEncProps_Init, level 6, reduceSize = the
    hunk size, LzmaEncProps_Normalize. That gives lc=3, lp=0, pb=2 and a dictionary of
    max(4096, hunk size) (the level-6 default of 64 MB is cut down to reduceSize).
  - The decoder never reads properties from the file: it rebuilds them the same way and calls
    LzmaEnc_WriteProperties, which writes the dictionary rounded up to 2^n or 3*2^n
    (to a multiple of 1 MB from 2 MB up). Props byte 0x5D.
  - LzmaEnc_MemEncode is called with writeEndMark = 0: a hunk is a raw stream (no header) with
    no end marker; LzmaDec_DecodeToBuf with LZMA_FINISH_END must produce exactly the hunk size
    and consume exactly the compressed length.
  - `cdlz` (CD LZMA) compresses the sector data of a hunk with LZMA and the subcode with zlib:
    the LZMA part is frames * 2352 bytes (hunk bytes / 2448 frames) and its dictionary is
    worked out from that size, not from the hunk size. The compressed hunk starts with
    (frames + 7) / 8 ECC flag bytes and a 2-byte (3 when the hunk is 64 KB or more)
    big-endian length of the LZMA part.

liblzma cannot write a raw LZMA1 stream without an end marker, but the payload of an LZMA2
chunk is exactly that (range coder flushed, no marker, state and dictionary reset on the first
chunk). The `raw` fixtures are the payload of a single-chunk LZMA2 stream.
"""

import lzma
import os
import random
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
CD_FRAMES = 8
CD_SECTOR = 2352


def chd_dictionary(size):
    """The dictionary CHD writes for a given LZMA input size (see the module docstring)."""
    size = max(size, 4096)
    if size >= 1 << 21:
        mask = (1 << 20) - 1
        return (size + mask) & ~mask
    i = 22
    while True:
        v = (2 + (i & 1)) << (i >> 1)
        if v >= size:
            return v
        i += 1


WORDS = ("plum zeta orbit quill marrow tundra fable gizmo lantern cobalt wisp ember "
         "harbor nimbus quartz saffron velvet yonder kettle meadow pixel rover sable "
         "thistle umber vortex willow xenon yarrow zephyr").split()


def text(rng, size):
    out = []
    length = 0
    while length < size:
        word = rng.choice(WORDS)
        if rng.random() < 0.1:
            word = word.capitalize() + "."
        out.append(word)
        length += len(word) + 1
    return (" ".join(out) + " ").encode("ascii")[:size]


def pattern(size):
    out = bytearray()
    period = 3
    while len(out) < size:
        block = bytes((i * 7 + period) & 0xFF for i in range(period))
        out += block * (64 // period + 3)
        out += b"ABCD" * period
        period = period % 61 + 2
    return bytes(out[:size])


def cd_hunk(rng):
    """Eight made-up Mode 1 sectors: sync, header, text payload, zero EDC/ECC area."""
    out = bytearray()
    for lba in range(CD_FRAMES):
        sector = bytearray(CD_SECTOR)
        sector[0:12] = b"\x00" + b"\xff" * 10 + b"\x00"
        sector[12:16] = bytes((0, 2, lba + 16, 1))
        sector[16:16 + 2048] = text(rng, 2048)
        assert len(sector) == CD_SECTOR
        out += sector
    return bytes(out)


def big(rng):
    out = bytearray()
    while len(out) < 200 * 1024:
        roll = rng.random()
        if roll < 0.7:
            out += text(rng, rng.randrange(500, 4000))
        elif roll < 0.85:
            out += bytes(rng.randrange(256) for _ in range(rng.randrange(50, 600)))
        else:
            out += bytes(rng.randrange(100, 3000))
    return bytes(out[:200 * 1024])


def props_byte(lc, lp, pb):
    return (pb * 5 + lp) * 9 + lc


def lzma1_filter(lc=3, lp=0, pb=2, dict_size=1 << 16):
    return [{"id": lzma.FILTER_LZMA1, "preset": 6, "dict_size": dict_size,
             "lc": lc, "lp": lp, "pb": pb}]


def lzma2_filter(lc=3, lp=0, pb=2, dict_size=1 << 16):
    return [{"id": lzma.FILTER_LZMA2, "preset": 6, "dict_size": dict_size,
             "lc": lc, "lp": lp, "pb": pb}]


def lzma2_chunks(stream):
    """Splits a raw LZMA2 stream into (control, header bytes, payload, unpacked size)."""
    chunks = []
    pos = 0
    while True:
        control = stream[pos]
        if control == 0:
            assert pos + 1 == len(stream), "data after the end byte"
            return chunks
        if control in (1, 2):
            size = struct.unpack(">H", stream[pos + 1:pos + 3])[0] + 1
            chunks.append((control, stream[pos:pos + 3], stream[pos + 3:pos + 3 + size], size))
            pos += 3 + size
            continue
        assert control >= 0x80, "bad control byte"
        unpacked = ((control & 0x1F) << 16) + struct.unpack(">H", stream[pos + 1:pos + 3])[0] + 1
        packed = struct.unpack(">H", stream[pos + 3:pos + 5])[0] + 1
        header = 6 if control >= 0xC0 else 5
        chunks.append((control, stream[pos:pos + header],
                       stream[pos + header:pos + header + packed], unpacked))
        pos += header + packed


def lzma2_raw(data, **kw):
    return lzma.compress(data, format=lzma.FORMAT_RAW, filters=lzma2_filter(**kw))


def lzma2_without_end(data, control, **kw):
    """LZMA2 chunks of [data] with the first chunk's control byte rewritten to [control]."""
    stream = lzma2_raw(data, **kw)
    first = lzma2_chunks(stream)[0]
    assert first[0] >= 0xE0, "expected an LZMA chunk with a dictionary reset first"
    header = bytearray(first[1])
    header[0] = control | (header[0] & 0x1F)
    if control < 0xC0:
        del header[5]  # no properties byte below 0xC0
    return bytes(header) + stream[len(first[1]):-1]


def aligned(data):
    """Pads to a multiple of 16 bytes ending with 0x00, so a spliced chunk sees the same
    position (lp, pb) and literal context (previous byte 0) as when it was encoded alone."""
    pad = 16 - len(data) % 16
    return data + bytes(pad)


def mixed_lzma2(rng):
    """A, an uncompressed chunk, a state reset, new properties, a dictionary reset, an
    uncompressed chunk with a dictionary reset and an LZMA chunk with new properties."""
    a = aligned(text(rng, 3000))
    u = aligned(bytes(rng.randrange(256) for _ in range(700)))
    b = aligned(text(rng, 2500))
    c = aligned(text(rng, 2000))
    d = aligned(text(rng, 1500) + a[:500])
    e = aligned(bytes(rng.randrange(256) for _ in range(300)))
    f = text(rng, 1800)
    stream = lzma2_raw(a)[:-1]
    stream += bytes((2,)) + struct.pack(">H", len(u) - 1) + u
    stream += lzma2_without_end(b, 0xA0)
    stream += lzma2_without_end(c, 0xC0, lc=1, lp=2, pb=1)
    stream += lzma2_without_end(d, 0xE0, lc=0, lp=0, pb=0)
    stream += bytes((1,)) + struct.pack(">H", len(e) - 1) + e
    stream += lzma2_without_end(f, 0xC0, lc=4, lp=0, pb=4)
    stream += b"\x00"
    controls = [chunk[0] for chunk in lzma2_chunks(stream)]
    for wanted in (2, 0xA0, 0xC0, 0xE0, 1):
        assert any(c == wanted or (c & 0xE0) == wanted for c in controls), hex(wanted)
    return stream, a + u + b + c + d + e + f


def crc(data):
    return "%08x" % (zlib.crc32(data) & 0xFFFFFFFF)


def main():
    rng = random.Random(20261021)
    contents = {
        "text": text(rng, 12000),
        "zeros": bytes(65536),
        "random": bytes(rng.randrange(256) for _ in range(8192)),
        "pattern": pattern(20000),
        "cdhunk": cd_hunk(rng),
        "big": big(rng),
        "empty": b"",
    }
    fixtures = []

    def add(name, kind, payload, data, **extra):
        fixtures.append((name, kind, payload, data, extra))

    for name in ("text", "zeros", "random", "pattern", "big", "empty"):
        add(name + ".lzma", "alone", lzma.compress(contents[name], format=lzma.FORMAT_ALONE),
            contents[name])

    for name in ("text", "pattern", "cdhunk"):
        dict_size = chd_dictionary(len(contents[name]))
        payload = lzma.compress(contents[name], format=lzma.FORMAT_RAW,
                                filters=lzma1_filter(dict_size=dict_size))
        add(name + ".raw-eopm.bin", "raw-eopm", payload, contents[name],
            props="%02x" % props_byte(3, 0, 2), dict=dict_size)

    for name in ("text", "zeros", "pattern", "cdhunk"):
        dict_size = chd_dictionary(len(contents[name]))
        chunks = lzma2_chunks(lzma2_raw(contents[name], dict_size=dict_size))
        assert len(chunks) == 1 and chunks[0][0] >= 0xE0, name + " is not one LZMA chunk"
        add(name + ".raw.bin", "raw", chunks[0][2], contents[name],
            props="%02x" % props_byte(3, 0, 2), dict=dict_size)

    for name in ("text", "random", "big"):
        stream = lzma2_raw(contents[name])
        add(name + ".lzma2.bin", "lzma2", stream, contents[name], dictprop=16)
    mixed, mixed_data = mixed_lzma2(rng)
    add("mixed.lzma2.bin", "lzma2", mixed, mixed_data, dictprop=16)

    lines = ["# file kind size crc32 key=value ... (written by make_fixtures.py)"]
    total = 0
    for name, kind, payload, data, extra in fixtures:
        with open(os.path.join(HERE, name), "wb") as out:
            out.write(payload)
        total += len(payload)
        if len(data) > 4096:
            extra["prefix4096"] = crc(data[:4096])
        fields = [name, kind, str(len(data)), crc(data)]
        fields += ["%s=%s" % (k, v) for k, v in extra.items()]
        lines.append(" ".join(fields))
    with open(os.path.join(HERE, "manifest.txt"), "w") as out:
        out.write("\n".join(lines) + "\n")
    print("%d fixtures, %d bytes compressed" % (len(fixtures), total))


if __name__ == "__main__":
    main()
