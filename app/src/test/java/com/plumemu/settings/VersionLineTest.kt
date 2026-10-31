package com.plumemu.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionLineTest {
    @Test
    fun aboutNamesTheVersionAndTheBuildNumber() {
        assertEquals("0.0.1-alpha (112)", versionLine("0.0.1-alpha", 112))
    }
}
