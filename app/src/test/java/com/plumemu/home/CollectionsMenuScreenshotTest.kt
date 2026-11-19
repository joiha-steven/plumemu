package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.OpenPanel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The game menu's collection panels at 768x432 and 576x432, Graphite and Paper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class CollectionsMenuScreenshotTest {
    private fun shot(name: String, panel: OpenPanel) = ReferenceShots.capture(name) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }

    private fun shotInRpg(name: String, panel: OpenPanel) = ReferenceShots.capture(name) { HomeAt(CollectionsShotsBase.content(), CollectionsShotsBase.rpg, panel = panel) }

    @Test fun menu() = shot("collections_menu", CollectionsMenuShotsBase.menu())

    @Test fun picker() = shot("collections_picker", CollectionsMenuShotsBase.picker())

    @Test fun name() = shot("collections_name", CollectionsMenuShotsBase.name())

    @Test fun menuRemove() = shotInRpg("collections_menu_remove", CollectionsMenuShotsBase.menuRemove())
}
