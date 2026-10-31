package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Apps entry's menu over the Apps list, "Browser" focused, with and without Uninstall,
 * and the round tile of apps that ship no icon of their own in the grid. 768x432 and 576x432, Graphite and Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AppsMenuScreenshotTest {
    private val app = GameRef(AppsGridApps[0].id, AppsGridApps[0].title)

    private fun menu(name: String, canUninstall: Boolean, textSize: TextSize = TextSize.Medium) {
        val panel = OpenPanel(app, PanelScreen.AppMenu(canUninstall))
        ReferenceShots.capture(name, textSize = textSize) { HomeAt(appsGridLibrary(), gridActions(APPS_ENTRY, 0), panel = panel) }
    }

    @Test
    fun appMenu() = menu("apps_menu", canUninstall = true)

    @Test
    fun appMenuWithoutUninstall() = menu("apps_menu_no_uninstall", canUninstall = false)

    @Test
    fun appMenuAtLargestText() = menu("apps_menu", canUninstall = true, textSize = TextSize.Largest)

    /** The first eight apps have no icon of their own: round tiles with their first letter, beside tiles with a picture. */
    @Test
    fun appsWithoutAnIconOfTheirOwn() =
        ReferenceShots.capture("home_grid_apps_fallback") {
            AppsGridBoard(focused = 0, icons = FakeAppIcons(missing = AppsGridApps.take(8).map { it.id }.toSet()))
        }
}
