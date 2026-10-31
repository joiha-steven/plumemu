#!/usr/bin/env python3
"""Builds the CHD LZMA hunks and the 7z archives for ChdLzmaTest and SevenZipDirectoryTest.

Python 3 standard library only. Run it from anywhere:

    python3 identify/src/test/resources/containers/make_fixtures.py

It rewrites the fixture files and manifest.txt next to itself, from made-up content only (a fake
PlayStation disc whose SYSTEM.CNF boots the made-up serial SLUS-999.99, filler text, random
bytes). It reuses the LZMA helpers of ../lzma/make_fixtures.py, whose docstring explains how CHD
stores LZMA and how a raw LZMA1 stream without an end marker is made with liblzma.

CHD hunks (the Kotlin test builds the CHD around them with PackedImages.chd):
  ps1-cd.cdlz.bin   a `cdlz` hunk of 8 CD frames (sectors 16-23 of the fake disc, Mode 1): ECC
                    bitmap (frames 0 and 3 flagged, their sync cleared), 2-byte big-endian
                    length, raw LZMA of the 8 x 2352 sector bytes, raw deflate of the subcode.
  ps1-cd-64k.cdlz.bin  the same in a hunk of 27 frames (sectors 0-26), so its length takes 3
                    bytes.
  ps1-dvd.lzma.bin  an `lzma` hunk of 8 cooked 2048-byte sectors (16-23): one raw LZMA stream.

7z archives (7-Zip DOC/7zFormat.txt), built byte by byte: file data packed with LZMA2 (never
decoded by the reader), the header plain or packed as named.

Manifest: `file kind key=value ...`, then for a 7z one line per entry,
`entry <name>|<size>|<crc32>|<file or dir>` (names may hold spaces; a letter outside ASCII is
written as \\uXXXX, the english-only guard reads this file).
  chd-cdlz / chd-lzma: hunk=<bytes> crc=<crc32 of the whole decoded hunk> first=<first sector>
    lzma=<bytes of the LZMA part>
  7z: header=<plain|lzma|lzma2|bcj|aes> pack=<file offset of the packed header, 0 if plain>
    note=<text a note must contain, if the header is not read>
"""

import lzma
import os
import random
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "lzma"))
from make_fixtures import chd_dictionary, crc, lzma2_chunks, props_byte, text  # noqa: E402

SECTOR = 2048
RAW_SECTOR = 2352
FRAME = 2448
FIRST = 16
COUNT = 8
ECC_FRAMES = (0, 3)


# --- the fake disc --------------------------------------------------------------------------

def record(name, lba, size, directory):
    length = 33 + len(name) + (1 if len(name) % 2 == 0 else 0)
    out = bytearray(length)
    out[0] = length
    out[2:10] = struct.pack("<I", lba) + struct.pack(">I", lba)
    out[10:18] = struct.pack("<I", size) + struct.pack(">I", size)
    out[25] = 2 if directory else 0
    out[32] = len(name)
    out[33:33 + len(name)] = name
    return bytes(out)


def disc_sectors(rng):
    """Sectors 16-23 of a fake PS1 disc: PVD, terminator, root, SYSTEM.CNF, filler."""
    cnf = b"BOOT = cdrom:\\SLUS_999.99;1\r\nTCB = 4\r\nEVENT = 10\r\nSTACK = 801FFFF0\r\n"
    sectors = [bytearray(SECTOR) for _ in range(COUNT)]
    pvd = sectors[0]
    pvd[0] = 1
    pvd[1:7] = b"CD001\x01"
    pvd[40:72] = b"TEST_DISC".ljust(32)
    pvd[156:156 + 34] = record(b"\x00", 18, SECTOR, True)
    sectors[1][0:7] = b"\xffCD001\x01"
    root = b"".join([
        record(b"\x00", 18, SECTOR, True),
        record(b"\x01", 18, SECTOR, True),
        record(b"SYSTEM.CNF;1", 19, len(cnf), False),
        record(b"SLUS_999.99;1", 20, 4 * SECTOR, False),
    ])
    sectors[2][0:len(root)] = root
    sectors[3][0:len(cnf)] = cnf
    for i in range(4, COUNT):
        sectors[i][:] = text(rng, SECTOR)
    return [bytes(s) for s in sectors]


def bcd(value):
    return (value // 10) << 4 | value % 10


def raw_frame(lba, data):
    """A Mode 1 raw sector (sync, BCD address, mode, data, zero EDC/ECC) and 96 subcode zeros."""
    address = lba + 150
    header = bytes((bcd(address // 4500), bcd(address // 75 % 60), bcd(address % 75), 1))
    sector = b"\x00" + b"\xff" * 10 + b"\x00" + header + data
    return sector.ljust(RAW_SECTOR, b"\x00") + bytes(FRAME - RAW_SECTOR)


def raw_lzma(data):
    """Raw LZMA1, lc=3 lp=0 pb=2, CHD dictionary, no end marker (one LZMA2 chunk's payload)."""
    filters = [{"id": lzma.FILTER_LZMA2, "preset": 6, "dict_size": chd_dictionary(len(data)),
                "lc": 3, "lp": 0, "pb": 2}]
    chunks = lzma2_chunks(lzma.compress(data, format=lzma.FORMAT_RAW, filters=filters))
    assert len(chunks) == 1 and chunks[0][0] >= 0xE0, "not one LZMA chunk"
    return chunks[0][2]


def raw_deflate(data):
    packer = zlib.compressobj(9, zlib.DEFLATED, -15)
    return packer.compress(data) + packer.flush()


def cdlz_hunk(sectors, first):
    count = len(sectors)
    frames = b"".join(raw_frame(first + i, s) for i, s in enumerate(sectors))
    base = bytearray()
    subcode = bytearray()
    bitmap = bytearray((count + 7) // 8)
    for i in range(count):
        frame = bytearray(frames[i * FRAME:(i + 1) * FRAME])
        if i in ECC_FRAMES:
            bitmap[i // 8] |= 1 << (i % 8)
            frame[0:12] = bytes(12)
        base += frame[:RAW_SECTOR]
        subcode += frame[RAW_SECTOR:]
    packed = raw_lzma(bytes(base))
    # MAME: the length takes 3 bytes when the hunk is 64 KB or more.
    length = struct.pack(">I", len(packed))[1:] if len(frames) >= 65536 else struct.pack(">H", len(packed))
    hunk = bytes(bitmap) + length + packed + raw_deflate(bytes(subcode))
    return hunk, frames, len(packed)


# --- 7z -------------------------------------------------------------------------------------

K_END, K_HEADER, K_MAIN, K_FILES, K_PACK, K_UNPACK, K_SUBSTREAMS = 0, 1, 4, 5, 6, 7, 8
K_SIZE, K_CRC, K_FOLDER, K_UNPACK_SIZE, K_NUM_STREAMS = 9, 10, 11, 12, 13
K_EMPTY_STREAM, K_EMPTY_FILE, K_NAME, K_MTIME, K_ATTRIBUTES, K_ENCODED = 14, 15, 17, 20, 21, 23
LZMA_ID, LZMA2_ID, BCJ_ID, AES_ID = b"\x03\x01\x01", b"\x21", b"\x03\x03\x01\x03", b"\x06\xf1\x07\x01"


def num(n):
    """A 7z number: i leading 1 bits in the first byte, then i little-endian bytes."""
    for i in range(8):
        if n < 1 << (7 * (i + 1)):
            first = ((0xFF << (8 - i)) & 0xFF) | (n >> (8 * i))
            return bytes((first,)) + (n & ((1 << (8 * i)) - 1)).to_bytes(i, "little")
    return b"\xff" + n.to_bytes(8, "little")


def bits(flags):
    out = bytearray((len(flags) + 7) // 8)
    for i, flag in enumerate(flags):
        if flag:
            out[i // 8] |= 0x80 >> (i % 8)
    return bytes(out)


def digests(values):
    return b"\x01" + b"".join(struct.pack("<I", v) for v in values)


def coder(method, props=b"", ins=1, outs=1):
    flags = len(method) | (0x20 if props else 0) | (0x10 if (ins, outs) != (1, 1) else 0)
    out = bytes((flags,)) + method
    if (ins, outs) != (1, 1):
        out += num(ins) + num(outs)
    if props:
        out += num(len(props)) + props
    return out


def folder_one(method, props):
    return num(1) + coder(method, props)


def folder_chain(first, first_props, second, second_props):
    """Two coders: coder 0's input is bound to coder 1's output; coder 1 reads the pack."""
    return num(2) + coder(first, first_props) + coder(second, second_props) + num(0) + num(1)


def lzma2_pack(data):
    filters = [{"id": lzma.FILTER_LZMA2, "preset": 6, "dict_size": 1 << 16}]
    return lzma.compress(data, format=lzma.FORMAT_RAW, filters=filters)


LZMA2_PROPS = bytes((8,))  # 64 KB dictionary
LZMA_PROPS = bytes((props_byte(3, 0, 2),)) + struct.pack("<I", 1 << 16)


def streams_info(pack_pos, pack_sizes, folders, unpack_sizes, folder_crcs, substreams):
    out = bytes((K_PACK,)) + num(pack_pos) + num(len(pack_sizes)) + bytes((K_SIZE,))
    out += b"".join(num(s) for s in pack_sizes) + bytes((K_END,))
    out += bytes((K_UNPACK, K_FOLDER)) + num(len(folders)) + b"\x00" + b"".join(folders)
    out += bytes((K_UNPACK_SIZE,)) + b"".join(num(s) for s in unpack_sizes)
    if folder_crcs is not None:
        out += bytes((K_CRC,)) + digests(folder_crcs)
    out += bytes((K_END,))
    if substreams is not None:
        out += bytes((K_SUBSTREAMS,)) + substreams + bytes((K_END,))
    return out + bytes((K_END,))


def files_info(entries):
    """entries: (name, data or None for a directory)."""
    out = num(len(entries))
    empty = [data is None or len(data) == 0 for _, data in entries]
    if any(empty):
        field = bits(empty)
        out += bytes((K_EMPTY_STREAM,)) + num(len(field)) + field
        field = bits([data is not None for (_, data), e in zip(entries, empty) if e])
        out += bytes((K_EMPTY_FILE,)) + num(len(field)) + field
    names = b"\x00" + b"".join(name.encode("utf-16-le") + b"\x00\x00" for name, _ in entries)
    out += bytes((K_NAME,)) + num(len(names)) + names
    times = b"\x01\x00" + b"".join(struct.pack("<Q", 133000000000000000 + i) for i in range(len(entries)))
    out += bytes((K_MTIME,)) + num(len(times)) + times
    attributes = b"\x01\x00" + b"".join(struct.pack("<I", 0x10 if d is None else 0x20) for _, d in entries)
    out += bytes((K_ATTRIBUTES,)) + num(len(attributes)) + attributes
    return out + bytes((K_END,))


def main_streams(entries, solid, crc_in_folder):
    """Packs the data of every non-empty file; returns (packed bytes, streams info)."""
    datas = [data for _, data in entries if data]
    if solid:
        packs = [lzma2_pack(b"".join(datas))]
        folders = [folder_one(LZMA2_ID, LZMA2_PROPS)]
        unpack = [sum(len(d) for d in datas)]
        sub = bytes((K_NUM_STREAMS,)) + num(len(datas))
        sub += bytes((K_SIZE,)) + b"".join(num(len(d)) for d in datas[:-1])
        sub += bytes((K_CRC,)) + digests([zlib.crc32(d) for d in datas])
        return b"".join(packs), streams_info(0, [len(p) for p in packs], folders, unpack, None, sub)
    packs = [lzma2_pack(d) for d in datas]
    folders = [folder_one(LZMA2_ID, LZMA2_PROPS) for _ in datas]
    unpack = [len(d) for d in datas]
    if crc_in_folder:
        # The CRCs in the folders: the substreams info then lists nothing (7-Zip writes the same).
        return b"".join(packs), streams_info(0, [len(p) for p in packs], folders, unpack,
                                             [zlib.crc32(d) for d in datas], b"")
    sub = bytes((K_CRC,)) + digests([zlib.crc32(d) for d in datas])
    return b"".join(packs), streams_info(0, [len(p) for p in packs], folders, unpack, None, sub)


def raw_lzma_or_eopm(data):
    """7-Zip writes header LZMA without an end marker; fall back to one for tiny headers."""
    try:
        return raw_lzma(data)
    except AssertionError:
        filters = [{"id": lzma.FILTER_LZMA1, "preset": 6, "dict_size": 1 << 16, "lc": 3, "lp": 0, "pb": 2}]
        return lzma.compress(data, format=lzma.FORMAT_RAW, filters=filters)


def encode_header(header, kind, pack_pos, rng, lc_lp=None):
    """(packed header bytes, kEncodedHeader bytes) for a header coder [kind]."""
    if kind == "lzma2":
        packed, folder = lzma2_pack(header), folder_one(LZMA2_ID, LZMA2_PROPS)
        sizes = [len(header)]
    elif kind in ("lzma", "lclp"):
        props = LZMA_PROPS if kind == "lzma" else bytes((props_byte(*lc_lp, 2),)) + LZMA_PROPS[1:]
        packed, folder = raw_lzma_or_eopm(header), folder_one(LZMA_ID, props)
        sizes = [len(header)]
    elif kind == "bcj":
        packed, folder = raw_lzma_or_eopm(header), folder_chain(BCJ_ID, b"", LZMA_ID, LZMA_PROPS)
        sizes = [len(header), len(header)]
    else:  # aes: what 7-Zip writes with -mhe, the bytes made up
        packed = bytes(rng.randrange(256) for _ in range((len(header) + 15) // 16 * 16))
        aes_props = bytes((0xD3, 0x0F)) + bytes(rng.randrange(256) for _ in range(16))
        folder = folder_chain(AES_ID, aes_props, LZMA_ID, LZMA_PROPS)
        sizes = [len(header), len(header)]
    info = streams_info(pack_pos, [len(packed)], [folder], sizes, [zlib.crc32(header)], None)
    return packed, bytes((K_ENCODED,)) + info


def seven_zip(entries, header_kind, rng, solid=False, crc_in_folder=False, lc_lp=None):
    if any(data for _, data in entries):
        data, streams = main_streams(entries, solid, crc_in_folder)
        main = bytes((K_MAIN,)) + streams
    else:
        # Only empty files and directories: 7-Zip writes no kMainStreamsInfo at all.
        data, main = b"", b""
    header = bytes((K_HEADER,)) + main + bytes((K_FILES,)) + files_info(entries) + bytes((K_END,))
    pack_offset = 0
    if header_kind != "plain":
        packed, next_header = encode_header(header, header_kind, len(data), rng, lc_lp)
        pack_offset = 32 + len(data)
        data += packed
    else:
        next_header = header
    tail = struct.pack("<QQI", len(data), len(next_header), zlib.crc32(next_header))
    start = b"7z\xbc\xaf\x27\x1c\x00\x04" + struct.pack("<I", zlib.crc32(tail)) + tail
    return start + data + next_header, pack_offset


# --- main -----------------------------------------------------------------------------------

def made_up(rng, size, tag):
    head = ("TEST %s " % tag).encode("ascii")
    return (head + text(rng, size))[:size]


def main():
    rng = random.Random(20261022)
    lines = ["# file kind key=value ... / entry name|size|crc32|file-or-dir (written by make_fixtures.py)"]

    sectors = disc_sectors(rng)
    hunk, frames, lzma_len = cdlz_hunk(sectors, FIRST)
    with open(os.path.join(HERE, "ps1-cd.cdlz.bin"), "wb") as out:
        out.write(hunk)
    lines.append("ps1-cd.cdlz.bin chd-cdlz hunk=%d crc=%s first=%d lzma=%d"
                 % (len(frames), crc(frames), FIRST, lzma_len))
    # 27 frames (66,096 bytes): sectors 0-26 in hunk 0, a 3-byte length.
    big = [bytes(SECTOR)] * FIRST + sectors + [text(rng, SECTOR) for _ in range(3)]
    hunk, frames, lzma_len = cdlz_hunk(big, 0)
    with open(os.path.join(HERE, "ps1-cd-64k.cdlz.bin"), "wb") as out:
        out.write(hunk)
    lines.append("ps1-cd-64k.cdlz.bin chd-cdlz hunk=%d crc=%s first=0 lzma=%d"
                 % (len(frames), crc(frames), lzma_len))
    cooked = b"".join(sectors)
    packed = raw_lzma(cooked)
    with open(os.path.join(HERE, "ps1-dvd.lzma.bin"), "wb") as out:
        out.write(packed)
    lines.append("ps1-dvd.lzma.bin chd-lzma hunk=%d crc=%s first=%d lzma=%d"
                 % (len(cooked), crc(cooked), FIRST, len(packed)))

    archives = [
        ("plain.7z", "plain", [("Test Game \u00e9 (ZZZE).gba", made_up(rng, 40000, "GBA"))],
         dict(crc_in_folder=True), None),
        ("lzma-header.7z", "lzma", [("Test Game (NZZE).gba", made_up(rng, 32768, "GBA")),
                                    ("readme.txt", made_up(rng, 900, "TXT"))], {}, None),
        ("lzma2-header.7z", "lzma2", [("Test Game.nes", made_up(rng, 24592, "NES")),
                                      ("cover.png", made_up(rng, 30000, "PNG"))], {}, None),
        ("solid.7z", "lzma2", [("Test Game (Disk 1).gb", made_up(rng, 8192, "GB1")),
                               ("Test Game (Disk 2).gb", made_up(rng, 32768, "GB2")),
                               ("notes.txt", made_up(rng, 500, "TXT"))], dict(solid=True), None),
        ("empty-and-dir.7z", "lzma", [("roms", None), ("roms/empty.gbc", b""),
                                      ("roms/Test Game.gbc", made_up(rng, 16384, "GBC"))], {}, None),
        ("bcj-header.7z", "bcj", [("Test Game.gba", made_up(rng, 4096, "BCJ"))], {}, "BCJ+LZMA not read"),
        ("encrypted-header.7z", "aes", [("Test Game.gba", made_up(rng, 4096, "AES"))], {}, "encrypted"),
        ("lclp-header.7z", "lclp", [("Test Game.gba", made_up(rng, 4096, "LCLP"))], dict(lc_lp=(4, 1)),
         "lc=4 lp=1 not read"),
        # Last, so the random bytes of the archives above stay the same.
        ("only-empty.7z", "lzma", [("saves", None), ("saves/empty.srm", b""), ("empty.gba", b"")], {}, None),
    ]
    for name, kind, entries, options, note in archives:
        archive, pack = seven_zip(entries, kind, rng, **options)
        with open(os.path.join(HERE, name), "wb") as out:
            out.write(archive)
        line = "%s 7z header=%s pack=%d" % (name, kind, pack)
        if note:
            line += " note=" + note.replace(" ", "_")
        lines.append(line)
        for entry, data in entries:
            size = 0 if data is None else len(data)
            escaped = "".join(c if ord(c) < 128 else "\\u%04x" % ord(c) for c in entry)
            lines.append("entry %s|%d|%s|%s" % (escaped, size, crc(data or b""), "dir" if data is None else "file"))

    with open(os.path.join(HERE, "manifest.txt"), "w", encoding="ascii") as out:
        out.write("\n".join(lines) + "\n")
    print("%d fixtures" % (3 + len(archives)))


if __name__ == "__main__":
    main()
