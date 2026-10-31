package com.plumemu.graph

import com.plumemu.R
import com.plumemu.home.EmptyLibrary
import com.plumemu.home.HomeNotices
import com.plumemu.settings.screen.FolderAddState
import com.plumemu.settings.screen.SettingsActivityActions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * "A Add folder" on an empty home: Settings' folder picker. Its answer is Settings' too
 * (`GraphSettingsLibrary.onPicked` adds the folder and scans it); [homeNotices] shows what went
 * wrong as a line in the empty library's message. A device with no picker is said at once.
 */
internal fun AppGraph.addFolderFromHome(actions: SettingsActivityActions) {
    settingsLibrary.startFolderPick()
    libraryNotices.pickerAsked(isMissing = !actions.openFolderPicker())
}

/** What the home screen shows besides its games: the library's news, why an empty library is empty, and a refused folder ask. */
internal fun AppGraph.homeNotices(): HomeNotices {
    // Collected only while the empty message is on screen; "reading" until the first answer, the likeliest state at startup.
    val why =
        combine(database.scanDao().observeFolders(), startup.scanState) { folders, scan ->
            when {
                folders.isEmpty() -> EmptyLibrary.NoFolder
                scan.isBusy -> EmptyLibrary.Scanning
                else -> EmptyLibrary.NoGames
            }
        }.stateIn(appScope, SharingStarted.WhileSubscribed(), EmptyLibrary.Scanning)
    val problem =
        combine(settingsLibrary.folderAdd, libraryNotices.pickerMissing) { added, pickerMissing ->
            when {
                pickerMissing -> R.string.setup_message_no_picker
                added == FolderAddState.AlreadyAdded -> R.string.setup_message_already_added
                added == FolderAddState.NotKept -> R.string.setup_message_not_kept
                added == FolderAddState.CouldNotOpen -> R.string.setup_message_could_not_open
                else -> null
            }
        }.stateIn(appScope, SharingStarted.WhileSubscribed(), null)
    return HomeNotices(libraryNotices.notice, libraryNotices.newGameSystems, libraryNotices::noticeShown, libraryNotices::opened, why, problem, libraryNotices.closedAtOnce, libraryNotices::closedAtOnceShown, libraryNotices.coreQuestion, libraryNotices::coreQuestionShown, ::coreQuestionOver, ::coreQuestionsRestarted)
}
