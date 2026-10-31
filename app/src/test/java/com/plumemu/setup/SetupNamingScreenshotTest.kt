package com.plumemu.setup

import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Setup while the games are still being named: the scan step in its naming phase and the library
 * ready with its quiet line, at the two reference sizes and at the Retroid Pocket Classic's 620 x 540 (Graphite).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupNamingScreenshotTest {
    @Test
    fun scanStepNaming() = ReferenceShots.capture("setup_scan_naming") { SetupAt(SetupFakes.scanningNaming) }

    @Test
    fun readyStillNaming() = ReferenceShots.capture("setup_ready_naming") { SetupAt(SetupFakes.readyStillNaming) }

    @Test
    fun classicSize() {
        RuntimeEnvironment.setQualifiers("w620dp-h540dp-land-xhdpi")
        for ((name, state) in listOf("setup_scan_naming" to SetupFakes.scanningNaming, "setup_ready_naming" to SetupFakes.readyStillNaming)) {
            captureRoboImage(filePath = "${ReferenceShots.OUTPUT_DIR}/${name}_620x540_graphite.png") { PlumTheme(MediumText.Graphite) { SetupAt(state) } }
        }
    }
}
