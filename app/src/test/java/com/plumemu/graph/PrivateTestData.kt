package com.plumemu.graph

import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Test data that stays out of the public source copy. A test that needs it calls [assumePresent]: it
 * skips with the reason where the data is absent, but fails in the private repository (where the file
 * listing the private paths exists), because there a missing file means someone moved or renamed it.
 */
internal object PrivateTestData {
    /** The working directory of a module's unit tests is the module. */
    private val inPrivateRepository = File("../scripts/public-paths.txt").isFile

    /** [resource] is a classpath resource such as `/dir/file.tsv`; [PrivateTestData] is on the same classpath as the tests. */
    fun assumePresent(resource: String) {
        val found = PrivateTestData::class.java.getResource(resource) != null
        val reason = "$resource is absent (private test data, not in the public source)"
        if (!found && inPrivateRepository) fail("$reason; this is the private repository, so the file was moved or renamed")
        assumeTrue(reason, found)
    }

    /** The bundled name packs (identify module's assets), which the app's tests open through its asset manager. */
    fun assumePacks() {
        val found = File("../identify/src/main/assets/identify/packs.txt").isFile
        val reason = "no bundled name packs in this build (private data, absent from the public source)"
        if (!found && inPrivateRepository) fail("$reason; this is the private repository, so the file was moved or renamed")
        assumeTrue(reason, found)
    }
}
