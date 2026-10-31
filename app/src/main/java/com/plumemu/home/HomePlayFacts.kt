package com.plumemu.home

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import com.plumemu.battery.BatteryPace
import com.plumemu.battery.PickingBatteryLine
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.emulatorLabel
import kotlinx.coroutines.ensureActive

/**
 * One library game's play, as read for the details column: [lastPlayedDaysAgo] null when never
 * played (counted like the game's Info panel, `daysAgo`), and the emulator A would start
 * ([emulator] null when none is installed), named in the Info panel's words when shown.
 */
@Immutable
internal data class GamePlay(
    val lastPlayedDaysAgo: Int?,
    val playTimeMinutes: Int,
    val emulator: EmulatorToStart?,
    /** The game's usual battery pace; null: never measured, or not read (no battery line). */
    val batteryPace: BatteryPace? = null,
)

/**
 * Where the details column reads the focused game's play facts. The library's is `content/LibraryPlayFacts` (`AppGraph.homePlayFacts()`,
 * given through [HomeCallbacks.playFacts]); [None] reads nothing (the fakes, the benchmark, tests).
 */
internal interface HomePlayFactsSource {
    /** [gameId]'s play facts, read off the main thread; null when the library does not know the game. Throws what the database throws. */
    suspend fun read(gameId: Long): GamePlay?

    object None : HomePlayFactsSource {
        override suspend fun read(gameId: Long): GamePlay? = null
    }
}

/** The main screen's play facts source; [HomePlayFactsSource.None] outside one. Provided by `MainScreen` (next to `ProvideHomeArt`). */
internal val LocalHomePlayFacts = staticCompositionLocalOf<HomePlayFactsSource> { HomePlayFactsSource.None }

/** The facts table's state in the details column. */
@Immutable
internal sealed interface ShownPlay {
    /** Nothing to show and no room kept: no source, not a library game, the game unknown, or the read failed. */
    data object Absent : ShownPlay

    /** Being read (the focus has not rested yet, or the read runs): the table's room is kept empty. */
    data object Reading : ShownPlay

    /** A row's own facts (the fakes'), shown as given. */
    data class Fixed(val play: PlayFacts) : ShownPlay

    /** Read from the library. */
    data class Read(val play: GamePlay) : ShownPlay
}

/**
 * [game]'s play facts: its own (the fakes, they win), else read through [LocalHomePlayFacts] once the
 * focus has rested on it ([awaitDetailsSettle], the synopsis's settle; a held key reads none), on
 * the source's thread. A move away cancels the read. Read again for the same game when [content]
 * changes (a game played and back: Recent changed), keeping the shown facts until then. A read that
 * throws is logged and the column shows without the table.
 */
@Composable
internal fun rememberShownPlay(
    game: GameRowModel,
    content: HomeContent,
): ShownPlay {
    game.details?.play?.let { return ShownPlay.Fixed(it) }
    val source = LocalHomePlayFacts.current
    val id = game.id.toLongOrNull()
    if (source === HomePlayFactsSource.None || id == null) return ShownPlay.Absent
    val shown = remember(source, id) { mutableStateOf<ShownPlay>(ShownPlay.Reading) }
    LaunchedEffect(source, id, content) {
        awaitDetailsSettle()
        shown.value =
            try {
                source.read(id)?.let(ShownPlay::Read) ?: ShownPlay.Absent
            } catch (e: Exception) {
                // Only this effect's own cancel (a move away) is rethrown: Room throws a
                // CancellationException of its own when the database closed.
                coroutineContext.ensureActive()
                Log.e(TAG, "Play facts of game $id could not be read (${e.javaClass.simpleName}); the details show without them", e)
                ShownPlay.Absent
            }
    }
    return shown.value
}

/**
 * The facts table for [shown] in [style]: the table, or while it is read the same room left empty
 * (three empty lines of the same style and gaps, so nothing below moves when it arrives), or nothing.
 */
@Composable
internal fun DetailsFacts(
    shown: ShownPlay,
    style: TextStyle,
) {
    when (shown) {
        ShownPlay.Absent -> Unit
        ShownPlay.Reading ->
            Column(modifier = Modifier.testTag(HomeTags.DETAILS_FACTS_PENDING), verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap)) {
                repeat(FACT_ROWS) { BasicText(text = "", style = style, maxLines = 1) }
            }
        is ShownPlay.Fixed -> Facts(shown.play, style)
        is ShownPlay.Read -> {
            val facts = PlayFacts(shown.play.lastPlayedDaysAgo, shown.play.playTimeMinutes, emulatorLabel(shown.play.emulator))
            val pace = shown.play.batteryPace
            if (pace == null) {
                Facts(facts, style)
            } else {
                // Under the table, in its value colour. It wraps to three lines at the largest sizes (as in Info) rather
                // than lose its words: the synopsis under it gives up the line. Where the cover column has too little
                // height left it drops "on this charge", then is left out with its gap (never cut).
                Column {
                    Facts(facts, style)
                    PickingBatteryLine(pace, style.copy(color = PlumTheme.colors.text2), maxLines = BATTERY_LINES, topGap = PlumDimens.factsRowGap)
                }
            }
        }
    }
}

/** Last played, play time, emulator. */
private const val FACT_ROWS = 3

/** The battery line's most lines under the table. */
private const val BATTERY_LINES = 3
