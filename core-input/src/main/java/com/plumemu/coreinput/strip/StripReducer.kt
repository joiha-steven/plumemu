package com.plumemu.coreinput.strip

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction

/** Something the screen must do; the reducer only decides, it never does. */
sealed interface StripIntent {
    /** Play (or, for Apps, open) the item at [index] of system [systemId]. */
    data class Play(
        val systemId: String,
        val index: Int,
    ) : StripIntent

    /** Open the game menu for the item at [index] of [systemId]. */
    data class OpenGameMenu(
        val systemId: String,
        val index: Int,
    ) : StripIntent

    data object OpenSettings : StripIntent

    /** Switch list and grid for [systemId] (remembered per system by the settings). */
    data class ToggleView(
        val systemId: String,
    ) : StripIntent

    /**
     * Find the next ([forward]) or previous first letter from [fromIndex] in [systemId], then
     * call `StripState.focusRow`. The reducer does not know titles.
     */
    data class JumpLetter(
        val systemId: String,
        val fromIndex: Int,
        val forward: Boolean,
    ) : StripIntent

    /**
     * Add the item at [index] of [systemId] to Favorites or take it out (Y). Sent for any item with
     * games or details focused; the screen knows which items are games (Apps has none to mark).
     */
    data class ToggleFavorite(
        val systemId: String,
        val index: Int,
    ) : StripIntent

    /** Open search (X); the strip's focus stays where it is, for when search closes. */
    data object OpenSearch : StripIntent
}

internal data class Reduction(
    val focus: StripFocus,
    val intents: List<StripIntent> = emptyList(),
    /** Push right to play: this Right armed the focused game (a second fresh Right plays it). */
    val armsRight: Boolean = false,
)

/**
 * The main screen's focus rules, pure Kotlin. [isNarrow]: the screen is narrower than 768 dp, so
 * details are a third stop when there is box art.
 *
 * [pushRightPlays] (Settings › Controls › Push right to play, off by default): a fresh Right with
 * a game focused in a list arms it (with a details stop the list's Right goes to the stop and the arming happens there) ([Reduction.armsRight]); a second fresh Right while it is still
 * [isArmed] plays it, exactly as A. A repeat never arms or plays, and neither does the Right that
 * enters the games column from the systems column (the focus is still on the systems column then).
 * Whoever calls this ([StripState]) keeps the armed game and disarms it on any other input.
 * Grids, the systems column and the details column are unchanged.
 *
 * Lists wrap from end to start on a fresh press and stop at the end while a direction is held,
 * so a held D-pad never flies past the end. Grids do not wrap.
 */
internal object StripReducer {
    fun reduce(
        focus: StripFocus,
        action: Action,
        content: StripContent,
        isNarrow: Boolean,
        pushRightPlays: Boolean = false,
        isArmed: Boolean = false,
    ): Reduction {
        val ctx = Ctx(content, isNarrow && content.hasBoxArt)
        val current = focus.reconciled(content, ctx.hasDetailsStop)
        val entry = content.systems.getOrNull(current.systemIndex) ?: return Reduction(current)
        return when (action) {
            is Action.Move ->
                when {
                    !pushRightPlays || !ctx.isPushRightRow(current, entry, action) -> Reduction(ctx.move(current, entry, action))
                    // A held Right does nothing here: it never arms, plays or slides into details.
                    action.isRepeat -> Reduction(current)
                    isArmed -> ctx.enterOrPlay(current, entry)
                    else -> Reduction(current, armsRight = true)
                }
            Action.Enter, Action.Play -> ctx.enterOrPlay(current, entry)
            Action.Back -> Reduction(ctx.back(current))
            Action.NextSystem -> Reduction(ctx.changeSystem(current, step = 1))
            Action.PreviousSystem -> Reduction(ctx.changeSystem(current, step = -1))
            Action.JumpLetterNext, Action.JumpLetterPrevious -> ctx.jump(current, entry, action == Action.JumpLetterNext)
            Action.Menu ->
                if (current.column == Column.Systems) {
                    Reduction(current, listOf(StripIntent.OpenSettings))
                } else {
                    Reduction(current, listOf(StripIntent.OpenGameMenu(entry.id, current.row)))
                }
            // Grids need box art, except an entry whose grid does not (Apps' icons).
            Action.ToggleView ->
                Reduction(current, if (content.hasBoxArt || entry.hasGridWithoutArt) listOf(StripIntent.ToggleView(entry.id)) else emptyList())
            Action.OpenSettings -> Reduction(current, listOf(StripIntent.OpenSettings))
            Action.ToggleFavorite ->
                if (current.column == Column.Systems || entry.itemCount == 0) {
                    Reduction(current)
                } else {
                    Reduction(current, listOf(StripIntent.ToggleFavorite(entry.id, current.row)))
                }
            Action.Search -> Reduction(current, listOf(StripIntent.OpenSearch))
            is Action.TapItem -> ctx.tap(current, entry, action)
            is Action.LongPressItem -> ctx.longPress(current, action)
            // Only a screen that takes text gets these (InputRouter.textEntry); the strip never does.
            is Action.Erase, is Action.Typed -> Reduction(current)
        }
    }

    /** Focus [column] if the cursor may stand there (used when a strip drag settles). */
    fun focusColumn(
        focus: StripFocus,
        column: Column,
        content: StripContent,
        isNarrow: Boolean,
    ): StripFocus {
        val hasDetailsStop = isNarrow && content.hasBoxArt
        return focus.copy(column = column).reconciled(content, hasDetailsStop)
    }

    /** Focus game [index] of the focused system (after a letter jump), keeping the column. */
    fun focusRow(
        focus: StripFocus,
        index: Int,
        content: StripContent,
        isNarrow: Boolean,
    ): StripFocus {
        val hasDetailsStop = isNarrow && content.hasBoxArt
        val current = focus.reconciled(content, hasDetailsStop)
        val id = current.systemId ?: return current
        val entry = content.systems.firstOrNull { it.id == id } ?: return current
        return current.withRow(entry, index).reconciled(content, hasDetailsStop)
    }

    /**
     * The focus on game 0 of system [systemId] (the games column; the systems column when that
     * system has no games). The other systems keep their remembered rows. Unchanged when
     * [systemId] is not in [content].
     */
    fun landOn(
        focus: StripFocus,
        systemId: String,
        content: StripContent,
        isNarrow: Boolean,
    ): StripFocus {
        val hasDetailsStop = isNarrow && content.hasBoxArt
        val index = content.systems.indexOfFirst { it.id == systemId }
        if (index < 0) return focus.reconciled(content, hasDetailsStop)
        val entry = content.systems[index]
        val column = if (entry.itemCount > 0) Column.Games else Column.Systems
        return focus.copy(column = column, systemIndex = index, systemId = entry.id).withRow(entry, 0).reconciled(content, hasDetailsStop)
    }

    private class Ctx(
        val content: StripContent,
        val hasDetailsStop: Boolean,
    ) {
        fun move(
            focus: StripFocus,
            entry: SystemEntry,
            move: Action.Move,
        ): StripFocus =
            when (focus.column) {
                Column.Systems ->
                    when (move.direction) {
                        Direction.Up, Direction.Down -> {
                            val index = step(focus.systemIndex, content.systems.size, move.direction == Direction.Down, move.isRepeat)
                            selectSystem(focus, index, Column.Systems)
                        }
                        Direction.Right -> if (entry.itemCount > 0) focus.copy(column = Column.Games) else focus
                        Direction.Left -> focus
                    }
                Column.Games -> if (entry.gridColumns > 1) moveInGrid(focus, entry, move.direction) else moveInList(focus, entry, move)
                Column.Details ->
                    when (move.direction) {
                        Direction.Up, Direction.Down -> withRow(focus, entry, step(focus.row, entry.itemCount, move.direction == Direction.Down, move.isRepeat))
                        Direction.Left -> focus.copy(column = Column.Games)
                        Direction.Right -> focus
                    }
            }

        fun isPushRightRow(
            focus: StripFocus,
            entry: SystemEntry,
            move: Action.Move,
        ): Boolean =
            move.direction == Direction.Right && entry.gridColumns <= 1 && entry.itemCount > 0 &&
                // With a details stop (narrow, box art) the list's Right goes to the stop and the rule applies there.
                if (hasDetailsStop && entry.hasDetails) focus.column == Column.Details else focus.column == Column.Games

        private fun moveInList(
            focus: StripFocus,
            entry: SystemEntry,
            move: Action.Move,
        ): StripFocus =
            when (move.direction) {
                Direction.Up, Direction.Down -> withRow(focus, entry, step(focus.row, entry.itemCount, move.direction == Direction.Down, move.isRepeat))
                Direction.Left -> focus.copy(column = Column.Systems)
                Direction.Right -> if (hasDetailsStop && entry.hasDetails) focus.copy(column = Column.Details) else focus
            }

        private fun moveInGrid(
            focus: StripFocus,
            entry: SystemEntry,
            direction: Direction,
        ): StripFocus {
            val columns = entry.gridColumns
            val row = focus.row
            val last = entry.itemCount - 1
            return when (direction) {
                Direction.Up -> if (row - columns >= 0) withRow(focus, entry, row - columns) else focus
                Direction.Down ->
                    when {
                        row + columns <= last -> withRow(focus, entry, row + columns)
                        // A partial last row below: land on its last cover.
                        last / columns > row / columns -> withRow(focus, entry, last)
                        else -> focus
                    }
                Direction.Left -> if (row % columns == 0) focus.copy(column = Column.Systems) else withRow(focus, entry, row - 1)
                Direction.Right ->
                    when {
                        row % columns != columns - 1 && row < last -> withRow(focus, entry, row + 1)
                        hasDetailsStop && entry.hasDetails -> focus.copy(column = Column.Details)
                        else -> focus
                    }
            }
        }

        fun enterOrPlay(
            focus: StripFocus,
            entry: SystemEntry,
        ): Reduction =
            when (focus.column) {
                Column.Systems -> Reduction(if (entry.itemCount > 0) focus.copy(column = Column.Games) else focus)
                Column.Games, Column.Details -> Reduction(focus, listOf(StripIntent.Play(entry.id, focus.row)))
            }

        fun back(focus: StripFocus): StripFocus =
            when (focus.column) {
                Column.Systems -> focus
                Column.Games -> focus.copy(column = Column.Systems)
                Column.Details -> focus.copy(column = Column.Games)
            }

        /**
         * L1 / R1, wrapping. On the system column every entry counts; with games focused, empty
         * entries (an empty Recent or Favorites) are skipped so the cursor stays in a list.
         */
        fun changeSystem(
            focus: StripFocus,
            step: Int,
        ): StripFocus {
            val size = content.systems.size
            val skipEmpty = focus.column != Column.Systems
            for (offset in 1 until size) {
                val index = Math.floorMod(focus.systemIndex + step * offset, size)
                if (!skipEmpty || content.systems[index].itemCount > 0) return selectSystem(focus, index, focus.column)
            }
            return focus
        }

        fun jump(
            focus: StripFocus,
            entry: SystemEntry,
            forward: Boolean,
        ): Reduction =
            if (focus.column == Column.Systems) {
                Reduction(focus)
            } else {
                Reduction(focus, listOf(StripIntent.JumpLetter(entry.id, focus.row, forward)))
            }

        fun tap(
            focus: StripFocus,
            entry: SystemEntry,
            tap: Action.TapItem,
        ): Reduction {
            val index = tap.index
            return when (tap.column) {
                Column.Systems ->
                    when {
                        index !in content.systems.indices -> Reduction(focus)
                        // A second tap on the focused system opens its games.
                        focus.column == Column.Systems && index == focus.systemIndex -> enterOrPlay(focus, entry)
                        focus.column == Column.Systems -> Reduction(selectSystem(focus, index, Column.Systems))
                        // The system column's visible edge: the current system goes back to the
                        // column, another one changes system.
                        index == focus.systemIndex -> Reduction(focus.copy(column = Column.Systems))
                        else -> Reduction(selectSystem(focus, index, focus.column))
                    }
                Column.Games ->
                    when {
                        index !in 0 until entry.itemCount -> Reduction(focus)
                        focus.column == Column.Games && index == focus.row ->
                            Reduction(focus, listOf(StripIntent.Play(entry.id, index)))
                        focus.column == Column.Details && index == focus.row -> Reduction(focus.copy(column = Column.Games))
                        focus.column == Column.Details -> Reduction(withRow(focus, entry, index))
                        else -> Reduction(withRow(focus, entry, index).copy(column = Column.Games))
                    }
                Column.Details ->
                    when {
                        entry.itemCount == 0 -> Reduction(focus)
                        focus.column == Column.Details -> Reduction(focus, listOf(StripIntent.Play(entry.id, focus.row)))
                        hasDetailsStop && entry.hasDetails -> Reduction(focus.copy(column = Column.Details))
                        else -> Reduction(focus)
                    }
            }
        }

        fun longPress(
            focus: StripFocus,
            press: Action.LongPressItem,
        ): Reduction {
            // On the system column: Settings, the touch path that works with the hint bar hidden (SPEC › Controls).
            if (press.column == Column.Systems) return Reduction(focus, listOf(StripIntent.OpenSettings))
            if (press.column != Column.Games) return Reduction(focus)
            val entry = content.systems.getOrNull(focus.systemIndex) ?: return Reduction(focus)
            if (press.index !in 0 until entry.itemCount) return Reduction(focus)
            val focused = withRow(focus, entry, press.index).copy(column = Column.Games)
            return Reduction(focused, listOf(StripIntent.OpenGameMenu(entry.id, press.index)))
        }

        /** Focus system [index]; the games column only if it has games (its row is restored). */
        private fun selectSystem(
            focus: StripFocus,
            index: Int,
            column: Column,
        ): StripFocus {
            val target = content.systems[index]
            val newColumn = if (target.itemCount == 0) Column.Systems else column
            return focus.copy(column = newColumn, systemIndex = index, systemId = target.id).reconciled(content, hasDetailsStop)
        }

        private fun withRow(
            focus: StripFocus,
            entry: SystemEntry,
            row: Int,
        ): StripFocus = focus.withRow(entry, row)

        /** One step in a list of [size]: wraps on a fresh press, stops at the ends while held. */
        private fun step(
            index: Int,
            size: Int,
            forward: Boolean,
            isRepeat: Boolean,
        ): Int {
            if (size <= 0) return 0
            val next = if (forward) index + 1 else index - 1
            return when {
                next in 0 until size -> next
                isRepeat -> index
                else -> Math.floorMod(next, size)
            }
        }
    }
}
