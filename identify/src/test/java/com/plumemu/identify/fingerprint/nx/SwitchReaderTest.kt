package com.plumemu.identify.fingerprint.nx

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.TitleKind
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/** Switch title ids from generated NSP, NSZ and XCI bytes and from file names; no real file or id. */
class SwitchReaderTest {
    private val reader = FingerprintReader()

    private fun read(bytes: ByteArray, name: String, system: String? = "switch"): Fingerprints =
        reader.read(ByteArraySource(bytes), name, system, FingerprintReader.Depth.QUICK)

    @Test fun nspTicketNamesTheContentAndWinsOverTheFileName() {
        val nsp = pfs0(listOf(NCA, rights(UPDATE) + ".tik", rights(UPDATE) + ".cert", "$NCA_ID.cnmt.xml"))
        val read = read(nsp, "Zork Saga [0100ABCD00020000][v0].nsp")
        assertEquals(Container.PFS0, read.container)
        assertEquals(UPDATE, read.titleId)
        assertEquals(TitleKind.UPDATE, read.titleKind)
        assertEquals("the update is looked up by its game's id", BASE, read.productCode)
        assertEquals(CodeKind.SWITCH, read.codeKind)
        assertTrue(read.notes.toString(), read.notes.any { "file name says 0100ABCD00020000" in it })
        assertEquals(listOf("code:SWITCH:$BASE"), read.fixKeys())
    }

    @Test fun contentMetaNamedByATitleIdGivesItAndOneNamedByAContentIdDoesNot() {
        assertEquals(DLC, read(pfs0(listOf(NCA, "$DLC.cnmt.xml")), "zork.nsp").titleId)
        assertEquals(TitleKind.DLC, read(pfs0(listOf(NCA, "${DLC.lowercase()}.cnmt.xml")), "zork.nsp").titleKind)
        val hashNamed = read(pfs0(listOf(NCA, "$NCA_ID.cnmt.xml")), "zork.nsp")
        assertNull("a 32-digit content id is not a title id", hashNamed.titleId)
        assertTrue(hashNamed.notes.toString(), hashNamed.notes.any { "no ticket or content meta" in it })
    }

    @Test fun nszIsReadLikeAnNsp() {
        val nsz = pfs0(listOf(NCA.replace(".nca", ".ncz"), rights(BASE) + ".tik"))
        val read = read(nsz, "zork.nsz", system = null)
        assertEquals(Container.PFS0, read.container)
        assertEquals(BASE, read.titleId)
        assertEquals(TitleKind.BASE, read.titleKind)
    }

    @Test fun nspWithoutATicketFallsBackToTheFileName() {
        val read = read(pfs0(listOf(NCA, "$NCA_ID.cnmt.nca")), "Zork Saga[0100abcd00011001][DLC].nsp")
        assertEquals(DLC, read.titleId)
        assertEquals(BASE, read.productCode)
        assertEquals(TitleKind.DLC, read.titleKind)
    }

    @Test fun mergedNspIsNamedByItsGame() {
        val merged = pfs0(listOf(rights(DLC) + ".tik", rights(UPDATE) + ".tik", rights(BASE) + ".tik", NCA))
        val read = read(merged, "zork.nsp")
        assertEquals(BASE, read.titleId)
        assertTrue(read.notes.toString(), read.notes.any { "3 titles inside" in it })
    }

    @Test fun xciIsNamedByItsFileNameAndOnlyItsHeadIsTouched() {
        val xci = ByteArray(0x8000).also { System.arraycopy("HEAD".toByteArray(), 0, it, 0x100, 4) }
        val counting = CountingSource(ByteArraySource(xci))
        val read = reader.read(counting, "Zork Saga [0100ABCD00010000]+[v1.3.0+2DLC].xci", "switch", FingerprintReader.Depth.QUICK)
        assertEquals(Container.XCI, read.container)
        assertEquals(BASE, read.titleId)
        assertTrue("read ${counting.bytesRead} bytes", counting.bytesRead <= 64)
        assertNull(read(xci, "Zork Saga.xci").titleId)
    }

    @Test fun anEmptyFileIsNamedByItsFileName() {
        val read = read(ByteArray(0), "netta [0100ABCD00010000][v0].nsp")
        assertEquals(BASE, read.productCode)
        assertEquals(Container.NONE, read.container)
    }

    @Test fun idsInFileNames() {
        val cases = listOf(
            "Zork [0100ABCD00010000][v0].nsp" to BASE,
            "Zork[0100abcd00010000].nsp" to BASE,
            "Zork_0100ABCD00010800_v65536.nsp" to UPDATE,
            "Zork (0100ABCD00011001).nsp" to DLC,
            "Zork [0100ABCD00010000][0100ABCD00010800].nsp" to BASE,
            "folder/0100ABCD00010000.nsp" to BASE,
            "Zork.nsp" to null,
            "Zork [100ABCD00010000].nsp" to null,
            "Zork [0100ABCD000100001].nsp" to null,
            "Zork [20100ABCD00010000].nsp" to null,
            "Zork [0200ABCD00010000].nsp" to null,
            "1234567890abcdef0100123456789abc.nca" to null,
            "Zork [deadbeef0100ABCD00010000].nsp" to null,
            "Zork [0100ABCD00010000beef].nsp" to null,
        )
        for ((name, id) in cases) assertEquals(name, id, SwitchReader.fromFileName(name))
    }

    @Test fun kindsAndBases() {
        assertEquals(TitleKind.BASE, SwitchReader.kindOf(BASE))
        assertEquals(TitleKind.UPDATE, SwitchReader.kindOf(UPDATE))
        assertEquals(TitleKind.DLC, SwitchReader.kindOf(DLC))
        assertEquals(TitleKind.DLC, SwitchReader.kindOf("0100ABCD00011FFF"))
        assertEquals("the add-on base itself is no DLC", TitleKind.OTHER, SwitchReader.kindOf("0100ABCD00011000"))
        for (id in listOf(BASE, UPDATE, DLC, "0100ABCD00011FFF")) assertEquals(id, BASE, SwitchReader.baseOf(id))
    }

    @Test fun otherSystemsAndExtensionsAreNotSwitchFiles() {
        assertNull(read(pfs0(listOf(rights(BASE) + ".tik")), "zork [0100ABCD00010000].nsp", system = "gba").titleId)
        assertNull(read(ByteArray(16), "zork [0100ABCD00010000].bin", system = null).titleId)
        assertEquals(BASE, read(ByteArray(16), "zork [0100ABCD00010000].nro").titleId)
    }

    @Test fun damagedTablesGiveNotesNotExceptions() {
        val good = pfs0(listOf(NCA, rights(UPDATE) + ".tik"))
        val name = "zork [0100ABCD00010000].nsp"
        val huge = good.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 0x7FFFFFFF) }
        assertTrue(read(huge, name).notes.any { "not read" in it })
        assertEquals("falls back to the file name", BASE, read(huge, name).titleId)
        val badOffset = good.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(0x10 + 0x18 + 0x10, 9999) }
        assertEquals(BASE, read(badOffset, name).titleId)
        for (length in 0..good.size) {
            val id = read(good.copyOf(length), name).titleId
            assertTrue("cut at $length: $id", id == UPDATE || id == BASE)
        }
        val random = Random(5)
        repeat(500) {
            val bytes = good.copyOf()
            repeat(4) { bytes[random.nextInt(minOf(bytes.size, 0x80))] = random.nextInt(256).toByte() }
            read(bytes, name) // must not throw
        }
    }

    @Test fun aNormalNspCostsUnderAKilobyte() {
        val nsp = pfs0(listOf(NCA, "$NCA_ID.cnmt.nca", rights(BASE) + ".tik", rights(BASE) + ".cert", "$NCA_ID.cnmt.xml"), dataSize = 1 shl 20)
        val counting = CountingSource(ByteArraySource(nsp))
        reader.read(counting, "zork.nsp", "switch", FingerprintReader.Depth.QUICK)
        assertTrue("read ${counting.bytesRead} bytes", counting.bytesRead < 1024)
    }

    companion object {
        const val BASE = "0100ABCD00010000"
        const val UPDATE = "0100ABCD00010800"
        const val DLC = "0100ABCD00011001"
        private const val NCA_ID = "0123456789abcdef0123456789abcdef"
        private const val NCA = "$NCA_ID.nca"

        /** A rights id: the title id, 7 zero bytes and a key generation. */
        fun rights(titleId: String): String = titleId.lowercase() + "0".repeat(14) + "0b"

        /** A PFS0 partition listing [names], each file [dataSize] bytes of zeros after the table. */
        fun pfs0(names: List<String>, dataSize: Int = 32): ByteArray {
            val table = java.io.ByteArrayOutputStream()
            val nameOffsets = names.map { name -> table.size().also { table.write(name.toByteArray()); table.write(0) } }
            while (table.size() % 0x20 != 0) table.write(0)
            val tableBytes = table.toByteArray()
            val header = ByteBuffer.allocate(0x10 + 0x18 * names.size + tableBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            header.put("PFS0".toByteArray()).putInt(names.size).putInt(tableBytes.size).putInt(0)
            names.indices.forEach { i ->
                header.putLong(i.toLong() * dataSize).putLong(dataSize.toLong()).putInt(nameOffsets[i]).putInt(0)
            }
            header.put(tableBytes)
            return header.array() + ByteArray(dataSize * names.size)
        }
    }
}
