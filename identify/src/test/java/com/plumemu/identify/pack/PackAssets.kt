package com.plumemu.identify.pack

import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * The private data some tests read from the source tree. The public source copy has neither the
 * bundled name packs nor the name-pack tools folder, so those tests call [assumePacks] / [assumeNamesTool] and are
 * skipped with the reason; they run unchanged wherever the data exists.
 */
internal object PackAssets {
    /** The bundled packs; the working directory of a module's unit tests is the module. */
    val dir = File("src/main/assets/identify")
    val present: Boolean = File(dir, "packs.txt").isFile

    /**
     * The private repository has the file that lists the private paths; the public copy does not.
     * Where it exists, missing data means a file was moved or renamed, so a test fails instead of skipping.
     */
    private val inPrivateRepository = File("../scripts/public-paths.txt").isFile

    private fun require(found: Boolean, reason: String) {
        if (!found && inPrivateRepository) fail("$reason; this is the private repository, so the file was moved or renamed")
        assumeTrue(reason, found)
    }

    fun assumePacks() = require(
        present,
        "no bundled name packs in this build (identify/src/main/assets/identify is private data, absent from the public source)",
    )

    /** A Python test of the name-pack tools that a Kotlin test compares against. */
    fun assumeNamesTool(path: String) = require(
        File(path).isFile,
        "$path is absent (tools/names is private, absent from the public source)",
    )
}
