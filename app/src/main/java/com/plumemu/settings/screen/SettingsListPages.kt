package com.plumemu.settings.screen

/** Library › ROM folders and Emulators › a system: where their panel pages put the focus. */
internal object SettingsListPages {
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

    /**
     * The unknown folder the panel is about: by the id kept in the focus while it is open, else the one
     * under the cursor in Library's rows (the wide panel previews it beside the row).
     */
    fun unknownFolderAt(
        focus: SettingsFocus,
        content: SettingsContent,
    ): SettingsFolderRow? {
        if (focus.column == SettingsColumn.Panel && focus.page == PanelPage.UnknownFolder) {
            return content.unknownFolders.firstOrNull { it.key == focus.folderKey }
        }
        return if (focus.section == SettingsSection.Library && focus.optionIn(content) == SettingsOption.UnknownFolder) {
            content.unknownFolderAtRow(focus.optionIndexIn(content))
        } else {
            null
        }
    }

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
     * Library's rows after [before] became [after]: the cursor stays on the same option, and on the same unknown folder
     * by id (answering moves a folder below the unknown ones). Null when nothing moves. A folder that left closes its panel.
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
        val now =
            if (option == SettingsOption.UnknownFolder) {
                val key = before.unknownFolderAtRow(at)?.key
                val first = rowsAfter.indexOf(SettingsOption.UnknownFolder)
                val place = after.unknownFolders.indexOfFirst { it.key == key }
                if (first >= 0 && place >= 0) first + place else -1
            } else {
                rowsAfter.indexOf(option)
            }
        if (now < 0) {
            val panelOpen = focus.column == SettingsColumn.Panel
            val moved = focus.rememberedOption + (library to at.coerceAtMost((rowsAfter.size - 1).coerceAtLeast(0)))
            return if (panelOpen) focus.copy(column = SettingsColumn.Options, page = null, panelIndex = 0, rememberedOption = moved) else focus.copy(rememberedOption = moved)
        }
        return if (now == at) null else focus.copy(rememberedOption = focus.rememberedOption + (library to now))
    }

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
