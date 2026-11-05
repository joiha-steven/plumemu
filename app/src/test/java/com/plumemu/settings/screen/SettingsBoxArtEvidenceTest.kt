package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.account.UtcTimeZone
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.UiEvidenceBase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Evidence pictures ([UiEvidenceBase]) of Settings › Accounts › Box art with the second option focused: no blank band under the description. */
abstract class SettingsBoxArtEvidence(
    size: String,
) : UiEvidenceBase(size) {
    @get:Rule
    val utc = UtcTimeZone()

    @Test
    fun secondOptionFocused() =
        shoot("settings-box-art-snap") {
            val content = AccountShotContent.of(AccountStatus.SignedIn("plumtester"))
            val settings = settingsForShot().let { it.copy(library = it.library.copy(boxArtSource = null)) }
            SettingsAt(AccountShotContent.focusOn(content, SettingsOption.BoxArtSource, PanelPage.BoxArtSource).copy(panelIndex = 1), settings, content)
        }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SettingsBoxArtEvidence640x480Test : SettingsBoxArtEvidence("640x480")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class SettingsBoxArtEvidence832x468Test : SettingsBoxArtEvidence("832x468")
