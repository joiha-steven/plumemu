package com.plumemu.restart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The maps reading of [staleCodeOf], on lines shaped like a real API 37 process's and the Thor's. */
class StaleCodeTest {
    private val appDir = "/data/app/~~kQ3nZ==/com.plumemu-Xy7a==/"
    private val oat = "/data/app/~~kQ3nZ==/com.plumemu-Xy7a==/oat/arm64"
    private val inodes = mutableMapOf("$oat/base.odex" to 4411L, "$oat/base.vdex" to 4412L, "$oat/base.art" to 4413L)

    private fun line(
        inode: Long,
        path: String,
    ) = "7b2c400000-7b2c5f2000 r--p 00000000 fd:21 $inode                    $path"

    private val unrelated =
        listOf(
            "12c00000-2ac00000 rw-p 00000000 00:00 0                                  [anon:dalvik-main space]",
            "7b2a000000-7b2a001000 ---p 00000000 00:00 0 ",
            line(901, "/apex/com.android.art/lib64/libart.so"),
            line(902, "/system/framework/arm64/boot-framework.oat"),
            line(903, "/data/app/~~kQ3nZ==/com.plumemu-Xy7a==/base.apk"),
        )

    private fun maps(vararg lines: String) = (unrelated + lines).joinToString("\n")

    private val unreadable = mutableSetOf<String>()

    private fun check(maps: String?) =
        staleCodeOf(maps, appDir) { path ->
            if (path in unreadable) PathNow.Unreadable("errno 13") else inodes[path]?.let { PathNow.Inode(it) } ?: PathNow.Gone
        }

    @Test
    fun theSameFilesAsOnDiskAreFresh() {
        val maps = maps(line(4411, "$oat/base.odex"), line(4411, "$oat/base.odex"), line(4412, "$oat/base.vdex"), line(4413, "$oat/base.art"))
        assertEquals(StaleCode.Fresh, check(maps))
    }

    @Test
    fun aDeletedMappingIsStale() {
        assertEquals(StaleCode.Stale("$oat/base.odex", "deleted"), check(maps(line(4412, "$oat/base.vdex"), line(4411, "$oat/base.odex (deleted)"))))
    }

    @Test
    fun aDeletedDexoptTempFileIsStale() {
        val verdict = check(maps(line(5120, "$oat/base.odex.8WfHre.tmp (deleted)")))
        assertEquals(StaleCode.Stale("$oat/base.odex.8WfHre.tmp", "deleted"), verdict)
    }

    @Test
    fun aBackupMappedMidCompileThatIsGoneIsStale() {
        assertEquals(StaleCode.Stale("$oat/base.vdex.backup", "gone"), check(maps(line(4411, "$oat/base.odex"), line(3999, "$oat/base.vdex.backup"))))
    }

    @Test
    fun aPathNowHoldingAnotherFileIsStale() {
        inodes["$oat/base.vdex"] = 7001
        assertEquals(StaleCode.Stale("$oat/base.vdex", "replaced"), check(maps(line(4411, "$oat/base.odex"), line(4412, "$oat/base.vdex"))))
    }

    /** Only "no such file" is gone: a path denied or failing otherwise says nothing. */
    @Test
    fun aMappedPathThatCannotBeStatedForAnotherReasonIsUnknown() {
        unreadable += "$oat/base.vdex"
        assertTrue(check(maps(line(4411, "$oat/base.odex"), line(4412, "$oat/base.vdex"))) is StaleCode.Unknown)
        assertEquals("a deleted mapping still says stale", StaleCode.Stale("$oat/base.odex", "deleted"), check(maps(line(4412, "$oat/base.vdex"), line(4411, "$oat/base.odex (deleted)"))))
    }

    @Test
    fun unreadableMapsAreUnknown() {
        assertTrue(check(null) is StaleCode.Unknown)
    }

    @Test
    fun noCompiledCodeMappedIsUnknown() {
        assertTrue(check(maps()) is StaleCode.Unknown)
        assertTrue(check("") is StaleCode.Unknown)
    }

    @Test
    fun anotherAppsOatFilesAreIgnored() {
        val other = "/data/app/~~p0q==/com.google.android.webview-Ab==/oat/arm64/base.odex (deleted)"
        val sibling = "/data/app/~~kQ3nZ==/com.plumemu.other-Xy7a==/oat/arm64/base.vdex (deleted)"
        assertTrue("only another app's code", check(maps(line(80, other), line(81, sibling))) is StaleCode.Unknown)
        assertEquals(StaleCode.Fresh, check(maps(line(80, other), line(4411, "$oat/base.odex"))))
    }

    @Test
    fun aPathWithSpacesIsReadWhole() {
        val dir = "/data/app/my apps/com.plumemu-1"
        val maps = line(12, "$dir/oat/arm/base.odex (deleted)")
        assertEquals(StaleCode.Stale("$dir/oat/arm/base.odex", "deleted"), staleCodeOf(maps, dir) { PathNow.Gone })
    }
}
