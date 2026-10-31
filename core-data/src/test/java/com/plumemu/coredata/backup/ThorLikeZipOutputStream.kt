package com.plumemu.coredata.backup

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A zip stream that damages an entry the way an AYN Thor did (build 380), where
 * the JVM's own Deflater does not: a compression level changed once an entry was written does not
 * take, and the next deflated entry loses the first 512 bytes of its deflate stream (one
 * DeflaterOutputStream buffer), so it no longer inflates ("invalid block type").
 *
 * The bytes pulled from the Thor were exactly the level-6 deflate of that picture given twice, with
 * the first 512 bytes missing. This keeps the part
 * that makes the entry unreadable (the missing head); the CRC and size it writes stay right, as
 * the Thor's did. It also counts the level changes asked after the first entry. Seen on the Thor:
 * only the first box-art entry was damaged, because the level then stayed the database's and the
 * account's `setLevel(DEFAULT_COMPRESSION)` asked for the level in effect.
 */
internal class ThorLikeZipOutputStream(
    out: OutputStream,
) : ZipOutputStream(out) {
    var levelChangesAfterAnEntry = 0
        private set

    private var entries = 0
    private var effectiveLevel = Deflater.DEFAULT_COMPRESSION
    private var damageNextDeflated = false
    private var undamaged: OutputStream? = null

    override fun setLevel(level: Int) {
        if (entries == 0) {
            super.setLevel(level)
            effectiveLevel = level
        } else {
            levelChangesAfterAnEntry++
            // The change never takes, so asking for the level in effect again (the account, last) did no harm on the Thor.
            if (level != effectiveLevel) damageNextDeflated = true
        }
    }

    override fun putNextEntry(e: ZipEntry) {
        super.putNextEntry(e)
        entries++
        if (damageNextDeflated && e.method != ZipEntry.STORED) {
            damageNextDeflated = false
            undamaged = out
            out = DropsFirstBytes(out, LOST_BYTES)
        }
    }

    override fun closeEntry() {
        super.closeEntry()
        undamaged?.let {
            out = it
            undamaged = null
        }
    }

    private class DropsFirstBytes(
        out: OutputStream,
        private var toDrop: Int,
    ) : FilterOutputStream(out) {
        override fun write(b: Int) {
            if (toDrop > 0) toDrop-- else out.write(b)
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            val dropped = minOf(toDrop, len)
            toDrop -= dropped
            if (len > dropped) out.write(b, off + dropped, len - dropped)
        }
    }

    private companion object {
        const val LOST_BYTES = 512
    }
}
