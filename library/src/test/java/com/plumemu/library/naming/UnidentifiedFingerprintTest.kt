package com.plumemu.library.naming

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.CrcSource
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.TitleKind
import com.plumemu.identify.source.RandomAccessSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What the statistics may send of an unidentified game: never a name, only what tells the file apart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnidentifiedFingerprintTest {
    private val secret = "Secret Game (USA)"

    @Test
    fun aZipGivesTheInnerGamesCrcAndLengthAndNoName() {
        val stored = FingerprintCodec.encode(
            Fingerprints(
                size = 30_000,
                container = Container.ZIP,
                innerName = "$secret.nes",
                entryCount = 1,
                contentSize = 40_960,
                crc32 = 0x0A1B2C3DL,
                crcSource = CrcSource.ZIP_ENTRY_CONTENT,
                headerBytesSkipped = 16,
                internalTitle = secret,
                archiveCrc32 = 7,
            ),
        )
        assertEquals(UnidentifiedFingerprint("zip", 40_960, 0x0A1B2C3DL, null, null, null), UnidentifiedFingerprint.of(stored))
    }

    @Test
    fun aDiscGivesItsSerialAndStrongestTrackKey() {
        val stored = FingerprintCodec.encode(
            Fingerprints(size = 72, container = Container.CUE, productCode = "SLUS-99999", codeKind = CodeKind.PS1, internalTitle = secret, trackKeys = listOf(0x1122L, 0x3344L)),
        )
        assertEquals(UnidentifiedFingerprint("cue", 72, null, "ps1", "SLUS-99999", 0x1122L), UnidentifiedFingerprint.of(stored))
    }

    @Test
    fun aDummyCodeOrASizeAloneGivesNothingToSend() {
        assertNull(UnidentifiedFingerprint.of(FingerprintCodec.encode(Fingerprints(size = 1_024, container = Container.NONE))))
        assertNull(UnidentifiedFingerprint.of(FingerprintCodec.encode(Fingerprints(size = 1_024, container = Container.NONE, productCode = "0000", codeKind = CodeKind.GBA))))
        assertNull(UnidentifiedFingerprint.of(FingerprintCodec.encode(Fingerprints(size = 0, container = Container.NONE, contentSize = 0, crc32 = 0))))
        assertNull(UnidentifiedFingerprint.of(null))
        assertNull(UnidentifiedFingerprint.of("not json"))
    }

    @Test
    fun noFieldCanHoldANameAPathOrATitle() {
        // Every field the type has; a new one must be added here on purpose, and must not be a name.
        assertEquals(
            listOf("code", "codeKind", "container", "crc", "size", "trackKey"),
            UnidentifiedFingerprint::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.sorted(),
        )
        val stored = FingerprintCodec.encode(
            Fingerprints(
                size = 5_000,
                container = Container.ZIP,
                innerName = "/storage/emulated/0/Roms/$secret.sfc",
                contentSize = 4_096,
                crc32 = 1,
                productCode = "ZZZE",
                codeKind = CodeKind.SNES,
                internalTitle = secret,
                titleIsCanonical = true,
                titleId = "0100ABCD00010800",
                titleKind = TitleKind.UPDATE,
            ),
        )
        val sent = UnidentifiedFingerprint.of(stored).toString()
        assertFalse(sent, sent.contains("Secret") || sent.contains("storage") || sent.contains("0100ABCD"))
    }

    @Test
    fun aSwitchIdTakenFromTheFileNameIsNeverSent() {
        // A game card image: its header carries no title id, so the reader takes the one in the name.
        val read = FingerprintReader().read(ByteArraySource(xci()), "Game [0100ABCD12340000].xci", "switch", FingerprintReader.Depth.FULL)
        assertEquals("identification still uses the name's id", "0100ABCD12340000", read.productCode)
        val stored = FingerprintCodec.encode(read, "switch")
        val sent = UnidentifiedFingerprint.of(stored)
        assertEquals("the whole-file CRC still goes", UnidentifiedFingerprint("xci", 0x8000, read.crc32!!, null, null, null), sent)
        assertFalse(sent.toString(), sent.toString().contains("0100ABCD"))
        // A rescan from the stored value keeps it unsent.
        val again = FingerprintCodec.encode(FingerprintCodec.decode(stored)!!, "switch")
        assertEquals(sent, UnidentifiedFingerprint.of(again))
    }

    @Test
    fun aStoredValueWithoutTheCodesOriginSendsNoCode() {
        // Written before the origin was stored: the code may have come from a file name.
        val old = """{"v":1,"size":72,"container":"CUE","crc":10,"contentSize":64,"code":"SLUS-99999","codeKind":"PS1"}"""
        assertEquals(UnidentifiedFingerprint("cue", 64, 10, null, null, null), UnidentifiedFingerprint.of(old))
        val oldSwitch =
            """{"v":1,"rv":2,"size":64,"container":"XCI","code":"0100ABCD12340000","codeKind":"SWITCH","titleId":"0100ABCD12340000","titleKind":"BASE"}"""
        assertNull(UnidentifiedFingerprint.of(oldSwitch))
    }

    @Test
    fun aSwitchIdReadFromTheFilesContentIsSent() {
        // An NSP whose ticket names the game; the file name says nothing.
        val nsp = pfs0(listOf("0100abcd12340000" + "0".repeat(14) + "0b.tik"))
        val read = FingerprintReader().read(ByteArraySource(nsp), "netta.nsp", "switch", FingerprintReader.Depth.FULL)
        assertEquals("0100ABCD12340000", read.productCode)
        val stored = FingerprintCodec.encode(read, "switch")
        val sent = UnidentifiedFingerprint.of(stored)
        assertEquals("switch", sent?.codeKind)
        assertEquals("0100ABCD12340000", sent?.code)
        // And still after a rescan from the stored value.
        assertEquals(sent, UnidentifiedFingerprint.of(FingerprintCodec.encode(FingerprintCodec.decode(stored)!!, "switch")))
    }

    /** Generated bytes held in memory. */
    private class ByteArraySource(private val bytes: ByteArray) : RandomAccessSource {
        override val size: Long get() = bytes.size.toLong()

        override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            val n = minOf(length.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), dst, offset, n)
            return n
        }

        override fun close() = Unit
    }

    private fun xci() = ByteArray(0x8000).also { System.arraycopy("HEAD".toByteArray(), 0, it, 0x100, 4) }

    /** A PFS0 partition (an NSP) listing [names], each file 32 bytes of zeros after the table. */
    private fun pfs0(names: List<String>): ByteArray {
        val dataSize = 32
        val table = java.io.ByteArrayOutputStream()
        val nameOffsets = names.map { name -> table.size().also { table.write(name.toByteArray()); table.write(0) } }
        while (table.size() % 0x20 != 0) table.write(0)
        val tableBytes = table.toByteArray()
        val header = ByteBuffer.allocate(0x10 + 0x18 * names.size + tableBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        header.put("PFS0".toByteArray()).putInt(names.size).putInt(tableBytes.size).putInt(0)
        names.indices.forEach { i -> header.putLong(i.toLong() * dataSize).putLong(dataSize.toLong()).putInt(nameOffsets[i]).putInt(0) }
        header.put(tableBytes)
        return header.array() + ByteArray(dataSize * names.size)
    }
}
