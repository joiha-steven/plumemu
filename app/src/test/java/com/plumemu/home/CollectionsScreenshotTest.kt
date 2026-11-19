package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Collections screens at 768x432 and 576x432, Graphite and Paper: the column, the folders page, a folder as a grid and a list, and the empty folders. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class CollectionsScreenshotTest {
    private fun shot(name: String, actions: List<com.plumemu.coreinput.Action>, grid: Boolean = false, content: HomeContent = CollectionsShotsBase.content()) =
        ReferenceShots.capture(name) { HomeAt(content, actions, gridIds = if (grid) CollectionsShotsBase.gridIds else emptySet()) }

    @Test fun column() = shot("collections_column", CollectionsShotsBase.column)

    @Test fun folders() = shot("collections_folders", CollectionsShotsBase.folders)

    @Test fun folderGrid() = shot("collections_folder_grid", CollectionsShotsBase.rpg, grid = true)

    @Test fun folderList() = shot("collections_folder_list", CollectionsShotsBase.rpg)

    @Test fun emptyOwn() = shot("collections_empty_own", CollectionsShotsBase.emptyOwn)

    @Test fun emptyMostPlayed() = shot("collections_empty_most_played", CollectionsShotsBase.mostPlayedOpen, content = CollectionsShotsBase.content(mostPlayed = 0))

    // Settings › Library › Collections.
    private fun settingsShot(name: String, focus: com.plumemu.settings.screen.SettingsFocus, content: com.plumemu.settings.screen.SettingsContent) =
        ReferenceShots.capture(name) { com.plumemu.settings.screen.SettingsAt(focus, content = content) }

    @Test fun settingsList() = settingsShot("settings_collections_list", com.plumemu.settings.screen.SettingsCollectionsShotsBase.list, com.plumemu.settings.screen.SettingsCollectionsShotsBase.content)

    @Test fun settingsItem() = settingsShot("settings_collections_item", com.plumemu.settings.screen.SettingsCollectionsShotsBase.item, com.plumemu.settings.screen.SettingsCollectionsShotsBase.content)

    @Test fun settingsDelete() = settingsShot("settings_collections_delete", com.plumemu.settings.screen.SettingsCollectionsShotsBase.delete, com.plumemu.settings.screen.SettingsCollectionsShotsBase.content)

}
