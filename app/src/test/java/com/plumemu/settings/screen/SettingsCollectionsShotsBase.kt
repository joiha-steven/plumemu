package com.plumemu.settings.screen

/** Library › Collections as data: four collections (one with a very long name), and the pages of the second. */
internal object SettingsCollectionsShotsBase {
    val collections =
        listOf(
            SettingsCollection(1, "RPG", 12),
            SettingsCollection(2, "Couch co-op", 3),
            SettingsCollection(3, "Road trip with a very long name that cannot fit", 0),
            SettingsCollection(4, "Handheld classics", 1),
        )

    val content = ShotContent.library.copy(collections = collections)

    private fun at(page: PanelPage, index: Int, content: SettingsContent = this.content) = panelAt(SettingsOption.Collections, page, index, content).copy(collectionId = 2)

    val list = at(PanelPage.Collections, 1).copy(collectionId = null)
    val item = at(PanelPage.Collection, 1)
    val delete = at(PanelPage.CollectionDelete, SettingsModel.CONFIRM_CANCEL)
    val row = optionsAt(SettingsOption.Collections, content)
}
