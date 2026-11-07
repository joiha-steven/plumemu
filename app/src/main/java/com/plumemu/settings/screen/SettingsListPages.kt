package com.plumemu.settings.screen

/** Library › ROM folders and Emulators › a system: where their panel pages put the focus. */
internal object SettingsListPages {
    /** Library's rows whose panel is a plain list, opened on its first item. */
    val listPageOf: Map<SettingsOption, PanelPage> =
        mapOf(
            SettingsOption.HiddenGames to PanelPage.HiddenGames,
            SettingsOption.UnidentifiedGames to PanelPage.UnidentifiedGames,
            SettingsOption.FilesNotAdded to PanelPage.FilesNotAdded,
        )

    /**
     * Library › Files not added › line [index]: on an unknown folder's line, that folder's page, on its
     * current answer; null on any other line.
     */
    fun toUnknownFolder(
        index: Int,
        content: SettingsContent,
        focus: SettingsFocus,
    ): SettingsFocus? {
        val folder = (content.notAdded.getOrNull(index) as? NotAddedItem.Folder)?.folder ?: return null
        val row = content.unknownFolders.firstOrNull { it.key == folder } ?: return null
        return focus.copy(page = PanelPage.UnknownFolder, panelIndex = answerIndex(row, content), folderKey = folder)
    }

    /** Back to Files not added, on the line of the folder that was open (the first line when it left). */
    fun backToFilesNotAdded(
        focus: SettingsFocus,
        content: SettingsContent,
    ): SettingsFocus = focus.copy(page = PanelPage.FilesNotAdded, panelIndex = lineOf(focus.folderKey, content).coerceAtLeast(0), folderKey = null)

    /** Where [folder]'s line is in Files not added; -1 when it has none. */
    private fun lineOf(
        folder: FolderKey?,
        content: SettingsContent,
    ): Int = content.notAdded.indexOfFirst { it is NotAddedItem.Folder && it.folder == folder }

    /** The line of Files not added the cursor stays on: the same line when it is still there, else the nearest. */
    fun filesNotAddedLineAfter(
        at: Int,
        before: SettingsContent,
        after: SettingsContent,
    ): Int {
        val key = before.notAdded.getOrNull(at)?.key
        val kept = after.notAdded.indexOfFirst { it.key == key }
        return if (kept >= 0) kept else at.coerceAtMost(after.notAdded.lastIndex)
    }

    /** Back to the folders list, on the folder the confirm was about (or the nearest item when it left). */
    fun backToFolders(
        focus: SettingsFocus,
        content: SettingsContent,
    ): SettingsFocus {
        val at = content.folders.indexOfFirst { it.id == focus.folderId }
        val index = if (at >= 0) at else focus.panelIndex
        return focus.copy(page = PanelPage.Folders, panelIndex = index.coerceIn(0, content.folders.size))
    }

    /**
     * Library › Hidden games › item [index]: shows that game again. The focus stays on the same
     * place (the next game moves up into it); null when the list ends with it and the panel closes.
     */
    fun unhideAt(
        index: Int,
        content: SettingsContent,
        work: SettingsWork,
        focus: SettingsFocus,
    ): SettingsFocus? {
        val games = content.hiddenGames
        val game = games.getOrNull(index.coerceAtMost(games.lastIndex)) ?: return null
        work.unhideGame(game.id)
        return if (games.size <= 1) null else focus.copy(panelIndex = index.coerceAtMost(games.size - 2))
    }

    /** The unknown folder the panel is about, by the id kept in the focus; null when it left. */
    fun unknownFolderAt(
        focus: SettingsFocus,
        content: SettingsContent,
    ): SettingsFolderRow? = content.unknownFolders.firstOrNull { it.key == focus.folderKey }

    /** The panel item of the folder's current answer: 0 is Ignore this folder, then the systems. */
    fun answerIndex(
        row: SettingsFolderRow,
        content: SettingsContent,
    ): Int =
        when (val answer = row.answer) {
            is FolderAnswer.OfSystem -> content.folderSystemChoices.indexOfFirst { it.id == answer.id }.let { if (it < 0) 0 else it + 1 }
            FolderAnswer.Ignored, FolderAnswer.Unanswered -> 0
        }

    /**
     * Library's rows after [before] became [after]: the cursor stays on the same option. Null when nothing moves. An option that
     * left closes its panel.
     */
    fun libraryFocusKept(
        focus: SettingsFocus,
        before: SettingsContent,
        after: SettingsContent,
    ): SettingsFocus? {
        val library = SettingsSection.Library
        if (focus.section != library) return null
        val rowsBefore = library.rowsIn(before)
        val rowsAfter = library.rowsIn(after)
        val at = focus.rememberedOption[library] ?: return null
        val option = rowsBefore.getOrNull(at) ?: return null
        val now = rowsAfter.indexOf(option)
        if (now < 0) {
            val moved = focus.rememberedOption + (library to at.coerceAtMost((rowsAfter.size - 1).coerceAtLeast(0)))
            return focus.leavingPanel(moved)
        }
        return if (now == at) null else focus.copy(rememberedOption = focus.rememberedOption + (library to now))
    }

    /**
     * Accounts' rows after [before] became [after]: ScreenScraper comes and goes with the source and the account. The cursor
     * stays on the same option; when ScreenScraper itself left it lands on Box art, and its panel closes.
     */
    fun accountsFocusKept(
        focus: SettingsFocus,
        before: SettingsContent,
        after: SettingsContent,
    ): SettingsFocus? {
        val accounts = SettingsSection.Accounts
        if (focus.section != accounts) return null
        val at = (focus.rememberedOption[accounts] ?: 0).coerceAtLeast(0)
        val rowsAfter = accounts.rowsIn(after)
        val option = accounts.rowsIn(before).getOrNull(at) ?: return null
        val now = rowsAfter.indexOf(option)
        if (now < 0) return focus.leavingPanel(focus.rememberedOption + (accounts to rowsAfter.indexOf(SettingsOption.BoxArtSource).coerceAtLeast(0)))
        return if (now == at) null else focus.copy(rememberedOption = focus.rememberedOption + (accounts to now))
    }

    /** Grid size, Home screen's last row, left because the button hints came back: the cursor lands on the row above it. */
    fun homeScreenFocusKept(
        focus: SettingsFocus,
        before: SettingsContent,
        after: SettingsContent,
    ): SettingsFocus? {
        val home = SettingsSection.HomeScreen
        if (focus.section != home) return null
        val rowsAfter = home.rowsIn(after)
        val wasOnIt = home.rowsIn(before).getOrNull(focus.rememberedOption[home] ?: 0) == SettingsOption.GridSize
        if (!wasOnIt || SettingsOption.GridSize in rowsAfter) return null
        return focus.leavingPanel(focus.rememberedOption + (home to rowsAfter.lastIndex))
    }

    /** The row under an open panel left: [remembered] is the row the cursor lands on, in the options column if a panel was open. */
    private fun SettingsFocus.leavingPanel(remembered: Map<SettingsSection, Int>): SettingsFocus =
        if (column == SettingsColumn.Panel) copy(column = SettingsColumn.Options, page = null, panelIndex = 0, rememberedOption = remembered) else copy(rememberedOption = remembered)

    /** The system of the focused Emulators row: system rows come first, in [SettingsContent.systems] order. */
    fun systemAt(
        focus: SettingsFocus,
        content: SettingsContent,
    ): SystemEmulators? =
        if (focus.section == SettingsSection.Emulators && focus.optionIn(content) == SettingsOption.SystemEmulator) {
            content.systems.getOrNull(focus.optionIndexIn(content))
        } else {
            null
        }
}
