package com.plumemu.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.plumemu.R

/** The last step's title column: [status] is a quiet line under the description (names still being filled in), or null. */
internal data class ReadyTitle(val title: String, val description: String, val status: String? = null)

/** "Ready" is true only with games: no folder (Set up later) and a folder holding none say what is so. */
@Composable
internal fun readyTitleOf(state: SetupUiState): ReadyTitle =
    when {
        state.folders.isEmpty() -> ReadyTitle(stringResource(R.string.setup_ready_no_folder_title), stringResource(R.string.setup_ready_no_folder_desc))
        state.gameCount == 0 -> ReadyTitle(stringResource(R.string.setup_ready_empty_title), stringResource(R.string.setup_ready_empty_desc))
        else -> ReadyTitle(stringResource(R.string.setup_ready_title), stringResource(R.string.setup_ready_desc), status = if (state.isNaming) stringResource(R.string.setup_ready_naming) else null)
    }
