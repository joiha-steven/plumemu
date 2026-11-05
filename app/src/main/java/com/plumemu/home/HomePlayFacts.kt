package com.plumemu.home

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.currentCoroutineContext
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

    /** The read ended with nothing (the game is unknown to the library, or it threw): the table's room stays, empty. */
    data object Unread : ShownPlay

    /** A row's own facts (the fakes'), shown as given. */
    data class Fixed(val play: PlayFacts) : ShownPlay

    /** Read from the library. */
    data class Read(val play: GamePlay) : ShownPlay
}

/**
 * [game]'s play facts: its own (the fakes, they win), else read through [LocalHomePlayFacts] once the
 * focus has rested on it ([awaitDetailsSettle], the synopsis's settle; a held key reads none), on
 * the source's thread. A move away cancels the read. Read again for the same game when [content]
 * changes (a game played and back: Recent changed), keeping the shown facts until then; that second read
 * never starts the first over, and a [content] that changes every few frames starves only the second. A read that
 * throws is logged and the column shows without the table, its room kept ([ShownPlay.Unread]).
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
    // A game seen a moment ago shows its last read in the first frame (read again after the settle, which then fills in what changed).
    val shown = remember(source, id) { mutableStateOf<ShownPlay>(PlayKept.of(source, id)?.let(ShownPlay::Read) ?: ShownPlay.Reading) }
    LaunchedEffect(source, id) {
        awaitDetailsSettle()
        shown.value = readPlay(source, id).also { if (it is ShownPlay.Read) PlayKept.keep(source, id, it.play) }
    }
    val firstContent = remember(source, id) { booleanArrayOf(true) }
    LaunchedEffect(source, id, content) {
        if (firstContent[0]) {
            firstContent[0] = false
            return@LaunchedEffect
        }
        if (shown.value === ShownPlay.Reading) return@LaunchedEffect
        awaitDetailsSettle()
        shown.value = readPlay(source, id).also { if (it is ShownPlay.Read) PlayKept.keep(source, id, it.play) }
    }
    return shown.value
}

/** The play facts of the last [LIMIT] games read from one source, for returning to a game (main thread only). A new source starts it over. */
private object PlayKept {
    private const val LIMIT = 8
    private var source: java.lang.ref.WeakReference<HomePlayFactsSource>? = null
    private val plays = LinkedHashMap<Long, GamePlay>()

    fun of(from: HomePlayFactsSource, id: Long): GamePlay? = if (source?.get() === from) plays[id] else null

    fun keep(from: HomePlayFactsSource, id: Long, play: GamePlay) {
        if (source?.get() !== from) {
            plays.clear()
            source = java.lang.ref.WeakReference(from)
        }
        plays.remove(id)
        plays[id] = play
        if (plays.size > LIMIT) plays.remove(plays.keys.first())
    }
}

private suspend fun readPlay(
    source: HomePlayFactsSource,
    id: Long,
): ShownPlay =
    try {
        source.read(id)?.let(ShownPlay::Read) ?: ShownPlay.Unread
    } catch (e: Exception) {
        // Only this effect's own cancel (a move away) is rethrown: Room throws a
        // CancellationException of its own when the database closed.
        currentCoroutineContext().ensureActive()
        Log.e(TAG, "Play facts of game $id could not be read (${e.javaClass.simpleName}); the details show without them", e)
        ShownPlay.Unread
    }

/**
 * The facts table for [shown] in [style], in a box of the same height whether it is being read, empty or filled (three one-line rows and
 * a battery line, so nothing below or beside it moves when the values arrive), or nothing at all when there is no source to read.
 */
@Composable
internal fun DetailsFacts(
    shown: ShownPlay,
    style: TextStyle,
) {
    when (shown) {
        ShownPlay.Absent -> Unit
        is ShownPlay.Fixed -> Facts(shown.play, style)
        else -> {
            val read = shown as? ShownPlay.Read
            Column {
                if (read == null) {
                    Column(modifier = Modifier.fillMaxWidth().testTag(HomeTags.DETAILS_FACTS_PENDING), verticalArrangement = Arrangement.spacedBy(PlumDimens.factsRowGap)) {
                        repeat(FACT_ROWS) { BasicText(text = HELD_LINE, style = style, maxLines = 1) }
                    }
                } else {
                    Facts(PlayFacts(read.play.lastPlayedDaysAgo, read.play.playTimeMinutes, emulatorLabel(read.play.emulator)), style)
                }
                // Under the table, in its value colour, up to [BATTERY_LINES] lines (it drops "on this charge", then is left out, rather than be cut). One line of its room is held.
                Box(Modifier.padding(top = PlumDimens.factsRowGap)) {
                    // The held line stands only where no line can come; with a pace the line itself is there (a held line under it would be cut where room is short).
                    val pace = read?.play?.batteryPace
                    if (pace == null) BasicText(text = HELD_LINE, style = style, maxLines = 1) else PickingBatteryLine(pace, style.copy(color = PlumTheme.colors.text2), maxLines = BATTERY_LINES)
                }
            }
        }
    }
}

/** The battery line's most lines under the table: it wraps rather than lose its words (it drops them, then is left out, only when even that does not fit). */
private const val BATTERY_LINES = 3

/** Last played, play time, emulator. */
private const val FACT_ROWS = 3

