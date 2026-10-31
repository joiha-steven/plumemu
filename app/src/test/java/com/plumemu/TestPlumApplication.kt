package com.plumemu

import com.plumemu.telemetry.Telemetry

/** The app's Application in Robolectric tests (robolectric.properties): everything but usage statistics. */
class TestPlumApplication : PlumApplication() {
    override fun makeTelemetry(): Telemetry? = null
}
