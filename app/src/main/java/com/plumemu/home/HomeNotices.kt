package com.plumemu.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.staticCompositionLocalOf
import com.plumemu.R
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.graph.ClosedAtOnce
import com.plumemu.graph.CoreQuestion
import com.plumemu.graph.LibraryNotice
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What a change check found, for the top bar's notice and the dot on a system's row
 *. Provided by `MainScreen` as [LocalHomeNotices].
 */
@Immutable
internal class HomeNotices(
    val notice: StateFlow<LibraryNotice?> = MutableStateFlow(null),
    /** Ids of the systems that got new games and have not been opened since. */
    val newGameSystems: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
    /** The top bar showed [LibraryNotice] for its time. */
    val onNoticeShown: (LibraryNotice) -> Unit = {},
    /** The person opened this system: its dot goes. */
    val onSystemOpened: (String) -> Unit = {},
    /** Why the library is empty, while it is (the games area's message). */
    val emptyLibrary: StateFlow<EmptyLibrary> = MutableStateFlow(EmptyLibrary.NoFolder),
    /** A short line under the empty library's message after a folder ask went wrong (string resource id), or null. */
    val folderMessage: StateFlow<Int?> = MutableStateFlow(null),
    /** An emulator that was started from here came back within seconds: the game panel's message says so once. */
    val closedAtOnce: StateFlow<ClosedAtOnce?> = MutableStateFlow(null),
    /** The message was shown (or dropped): forget it. */
    val onClosedAtOnceShown: (ClosedAtOnce) -> Unit = {},
    /** A launch with a RetroArch core never confirmed came back: the home asks whether the game started, once. */
    val coreQuestion: StateFlow<CoreQuestion?> = MutableStateFlow(null),
    /** The question was shown (or dropped): forget it. */
    val onCoreQuestionShown: (CoreQuestion) -> Unit = {},
    /** The question's panel closed (Yes, B, or after No's emulators): its held play counts now unless No took it back. */
    val onCoreQuestionOver: (CoreQuestion) -> Unit = {},
    /** The home started asking (again): a question an earlier home showed can no longer be answered. */
    val onCoreQuestionsRestarted: () -> Unit = {},
)

/** What an empty library is waiting for. */
internal enum class EmptyLibrary {
    /** No game folder at all: A adds one. */
    NoFolder,

    /** Folders exist and are being read. */
    Scanning,

    /** Folders exist, the scan ended and found no games. */
    NoGames,
}

internal val LocalHomeNotices = staticCompositionLocalOf { HomeNotices() }

/** How long the top bar keeps the notice before it fades back to the usual text. */
internal const val NOTICE_SHOWN_MS: Long = 4_000L

/** The notice the top bar shows now (null: the usual text) and the opacity of the place text. */
internal class ShownNotice(val notice: LibraryNotice?, val alpha: () -> Float)

/**
 * Takes the next notice from [LocalHomeNotices]: the usual text fades out ([PlumMotion.FADE_IN_MS]),
 * the notice fades in and stays [NOTICE_SHOWN_MS], then it fades out and the usual text fades back.
 * With motion off the texts swap at once. Nothing takes focus and nothing is drawn over the screen.
 */
@Composable
internal fun rememberShownNotice(): ShownNotice {
    val notices = LocalHomeNotices.current
    val incoming by notices.notice.collectAsState()
    var shown by remember { mutableStateOf<LibraryNotice?>(null) }
    val alpha = remember { Animatable(1f) }
    // Read at each fade (not captured when the notice arrives), so Animations turned off mid-notice is honoured.
    val isMotionOn by rememberUpdatedState(PlumTheme.motion.isOn)
    LaunchedEffect(incoming?.serial) {
        val next = incoming ?: return@LaunchedEffect
        val spec = tween<Float>(PlumMotion.FADE_IN_MS, easing = PlumMotion.EaseOut)
        suspend fun fadeTo(target: Float) = if (isMotionOn) { alpha.animateTo(target, spec); Unit } else alpha.snapTo(target)
        fadeTo(0f)
        shown = next
        fadeTo(1f)
        delay(NOTICE_SHOWN_MS)
        fadeTo(0f)
        shown = null
        fadeTo(1f)
        notices.onNoticeShown(next)
    }
    return remember(shown) { ShownNotice(shown) { alpha.value } }
}

/** "+24 games" or, when only removals happened, "\u22123 games". */
@Composable
internal fun noticeLead(
    notice: LibraryNotice,
    numbers: NumberFormat,
): String =
    if (notice.added <= 0 && notice.removed <= 0 && notice.unknownFolders.isNotEmpty()) {
        val names = notice.unknownFolders
        if (names.size == 1) {
            stringResource(R.string.home_notice_unknown_folder, names.single())
        } else {
            pluralStringResource(R.plurals.home_notice_unknown_folders, names.size, numbers.format(names.size))
        }
    } else if (notice.added > 0) {
        pluralStringResource(R.plurals.home_notice_added, notice.added, numbers.format(notice.added))
    } else {
        pluralStringResource(R.plurals.home_notice_removed, notice.removed, numbers.format(notice.removed))
    }

/** The system's name for one system, "3 systems" for several; null when only games went away. */
@Composable
internal fun noticeTail(
    notice: LibraryNotice,
    content: HomeContent,
    numbers: NumberFormat,
): String? {
    // Words only: where to answer. No pop-up, no focus change.
    if (notice.added <= 0 && notice.removed <= 0 && notice.unknownFolders.isNotEmpty()) return stringResource(R.string.home_notice_unknown_where)
    if (notice.added <= 0 || notice.addedSystems.isEmpty()) return null
    val only = notice.addedSystems.singleOrNull()
    val name = content.entries.firstOrNull { it.kind == EntryKind.System && it.id == only }?.let { entryName(it) }
    return name ?: pluralStringResource(R.plurals.home_systems_count, notice.addedSystems.size, numbers.format(notice.addedSystems.size))
}
