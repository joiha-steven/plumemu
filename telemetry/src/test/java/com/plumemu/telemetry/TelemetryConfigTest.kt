package com.plumemu.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** HTTPS only; plain http only to the local `wrangler dev`, and only when the debug build asks. */
class TelemetryConfigTest {
    private fun config(
        endpoint: String,
        local: Boolean = false,
    ) = TelemetryConfig(endpoint, "0.0.3-beta", 1, TelemetryConfig.BuildType.Debug, allowLocalCleartext = local)

    @Test
    fun theLiveServerIsAllowed() {
        assertEquals("plumemu.stevensdesk.com", config("https://plumemu.stevensdesk.com").apiBase()?.host)
    }

    @Test
    fun plainHttpIsRefusedWithoutTheLocalSwitch() {
        assertNull(config("http://10.0.2.2:8787").apiBase())
        assertNull(config("http://plumemu.stevensdesk.com").apiBase())
    }

    @Test
    fun theLocalSwitchAllowsOnlyLocalHosts() {
        assertEquals(8787, config("http://10.0.2.2:8787", local = true).apiBase()?.port)
        assertEquals("localhost", config("http://localhost:8787", local = true).apiBase()?.host)
        assertNull(config("http://plumemu.stevensdesk.com", local = true).apiBase())
        assertNull(config("http://192.168.1.5:8787", local = true).apiBase())
    }

    @Test
    fun notAUrlIsRefused() {
        assertNull(config("").apiBase())
        assertNull(config("plumemu.stevensdesk.com").apiBase())
    }
}
