package com.plumemu.coreui.components.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keyboard's layout and rules (docs/design/rules.md › Keyboard), without Compose. */
class KeyboardModelTest {
    private val withShift = KeyboardModel(hasShift = true, page = KeyboardPage.Letters)
    private val search = KeyboardModel(hasShift = false, page = KeyboardPage.Letters)

    /** The model focused on the key showing [label] (KeyboardTestKit's [idOf]). */
    private fun KeyboardModel.at(label: String): KeyboardModel = focusOn(idOf(label))

    private fun labelOf(key: KeyboardKey): String =
        when (key) {
            is KeyboardKey.Character -> key.char.toString()
            is KeyboardKey.Page -> key.label
            KeyboardKey.Shift -> "SHIFT"
            KeyboardKey.Delete -> "DEL"
            KeyboardKey.Hide -> "HIDE"
            KeyboardKey.Space -> "SPACE"
            KeyboardKey.Action -> "ACTION"
        }

    private fun KeyboardModel.focusedLabel(): String = labelOf(focusedSlot.key)

    private fun KeyboardModel.go(
        direction: KeyboardDirection,
        isRepeat: Boolean = false,
    ): KeyboardModel = move(direction, isRepeat).first

    private fun rowLabels(
        page: KeyboardPage,
        hasShift: Boolean = true,
    ): List<String> = KeyboardLayout.rows(page, hasShift).map { row -> row.joinToString(" ") { labelOf(it.key) } }

    // Layout

    @Test
    fun pagesHaveTheApprovedRows() {
        assertEquals(
            listOf(
                "q w e r t y u i o p",
                "a s d f g h j k l",
                "SHIFT z x c v b n m DEL",
                "123 HIDE SPACE ACTION",
            ),
            rowLabels(KeyboardPage.Letters),
        )
        assertEquals(
            listOf(
                "1 2 3 4 5 6 7 8 9 0",
                "@ # $ % & - _ + ( )",
                "=\\< . , : ; ! ? / DEL",
                "ABC HIDE SPACE ACTION",
            ),
            rowLabels(KeyboardPage.Symbols),
        )
        assertEquals(
            listOf(
                "[ ] { } < > = * ^ ~",
                "` | \\ ' \"",
                "#+= . , : ; ! ? / DEL",
                "ABC HIDE SPACE ACTION",
            ),
            rowLabels(KeyboardPage.Symbols2),
        )
    }

    @Test
    fun spansAndOffsetsMatchTheBoard() {
        val letters = KeyboardLayout.rows(KeyboardPage.Letters, hasShift = true)
        assertEquals(0.5f, letters[1].first().start)
        assertEquals(listOf(1.5f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1.5f), letters[2].map { it.span })
        assertEquals(listOf(1.5f, 1f, 4.5f, 3f), letters[3].map { it.span })
        for (page in KeyboardPage.entries) {
            val rows = KeyboardLayout.rows(page, hasShift = true)
            assertEquals(KeyboardLayout.ROWS, rows.size)
            for (row in rows) {
                // Keys follow each other without overlapping and stay within the 10 columns.
                row.zipWithNext().forEach { (a, b) -> assertEquals("$page: $b after $a", a.start + a.span, b.start) }
                assertTrue("$page row ends at ${row.last().start + row.last().span}", row.last().start + row.last().span <= KeyboardLayout.COLUMNS)
            }
            // Rows three and four fill the width exactly; the bottom row is the same on every page.
            assertEquals(10f, rows[2].last().start + rows[2].last().span)
            assertEquals(10f, rows[3].last().start + rows[3].last().span)
        }
    }

    @Test
    fun withoutShiftTheHoleStaysAndTheLettersKeepTheirPlaces() {
        val withIt = KeyboardLayout.rows(KeyboardPage.Letters, hasShift = true)
        val without = KeyboardLayout.rows(KeyboardPage.Letters, hasShift = false)
        assertEquals("z x c v b n m DEL", rowLabels(KeyboardPage.Letters, hasShift = false)[2])
        assertEquals(withIt[2].drop(1), without[2])
        assertEquals(1.5f, without[2].first().start)
        // No shift key on any page; the symbol pages are unchanged.
        for (page in KeyboardPage.entries) {
            assertTrue(KeyboardLayout.rows(page, hasShift = false).flatten().none { it.key == KeyboardKey.Shift })
        }
        assertEquals(rowLabels(KeyboardPage.Symbols), rowLabels(KeyboardPage.Symbols, hasShift = false))
        assertEquals(rowLabels(KeyboardPage.Symbols2), rowLabels(KeyboardPage.Symbols2, hasShift = false))
    }

    /** Every printable ASCII character (0x20..0x7E) comes from some key: password fields need them all. */
    @Test
    fun everyPrintableAsciiCharacterIsReachable() {
        val typed = mutableSetOf<Char>()
        for (page in KeyboardPage.entries) {
            for (shift in listOf(ShiftMode.Off, ShiftMode.Locked)) {
                val model = KeyboardModel(hasShift = true, page = page, shift = shift)
                model.rows.forEachIndexed { r, row ->
                    row.indices.forEach { i ->
                        when (val event = model.tap(KeyId(r, i)).second) {
                            is KeyboardEvent.Type -> typed += event.char
                            KeyboardEvent.Space -> typed += ' '
                            else -> Unit
                        }
                    }
                }
            }
        }
        val missing = (0x20..0x7E).map { it.toChar() }.filterNot { it in typed }
        assertEquals("not reachable: $missing", emptyList<Char>(), missing)
        assertTrue("only ASCII: ${typed.filter { it.code !in 0x20..0x7E }}", typed.all { it.code in 0x20..0x7E })
    }

    // Navigation

    @Test
    fun leftAndRightStepOneKey() {
        assertEquals("w", withShift.at("q").go(KeyboardDirection.Right).focusedLabel())
        assertEquals("e", withShift.at("r").go(KeyboardDirection.Left).focusedLabel())
        assertEquals("SPACE", withShift.at("HIDE").go(KeyboardDirection.Right).focusedLabel())
    }

    @Test
    fun aFreshPressAtARowEdgeWrapsToTheOtherEnd() {
        assertEquals("p", withShift.at("q").go(KeyboardDirection.Left).focusedLabel())
        assertEquals("q", withShift.at("p").go(KeyboardDirection.Right).focusedLabel())
        assertEquals("DEL", withShift.at("SHIFT").go(KeyboardDirection.Left).focusedLabel())
        assertEquals("123", withShift.at("ACTION").go(KeyboardDirection.Right).focusedLabel())
    }

    @Test
    fun aHeldPressStopsAtTheRowEdge() {
        val (atLeft, left) = withShift.at("q").move(KeyboardDirection.Left, isRepeat = true)
        assertEquals(KeyboardMove.Stayed, left)
        assertEquals("q", atLeft.focusedLabel())
        val (atRight, right) = withShift.at("l").move(KeyboardDirection.Right, isRepeat = true)
        assertEquals(KeyboardMove.Stayed, right)
        assertEquals("l", atRight.focusedLabel())
        // Inside the row a held press still moves.
        assertEquals("e", withShift.at("w").go(KeyboardDirection.Right, isRepeat = true).focusedLabel())
    }

    @Test
    fun upAndDownGoToTheNearestKeyByCentre() {
        // Space's centre (4.75 columns) is nearest v (5.0) on the row above.
        assertEquals("v", withShift.at("SPACE").go(KeyboardDirection.Up).focusedLabel())
        assertEquals("SPACE", withShift.at("v").go(KeyboardDirection.Down).focusedLabel())
        assertEquals("ACTION", withShift.at("DEL").go(KeyboardDirection.Down).focusedLabel())
        assertEquals("SHIFT", withShift.at("123").go(KeyboardDirection.Up).focusedLabel())
        // l (9.0) is as near o (8.5) as p (9.5): the left one.
        assertEquals("o", withShift.at("l").go(KeyboardDirection.Up).focusedLabel())
        assertEquals("a", withShift.at("q").go(KeyboardDirection.Down).focusedLabel())
    }

    @Test
    fun aTieGoesRightGoingDownAndLeftGoingUpLikeAPhysicalKeyboard() {
        val down = KeyboardDirection.Down
        val up = KeyboardDirection.Up
        // Letters, rows one and two are staggered by half a key: every key sits between two of the other row.
        val oneToTwo = mapOf("w" to "s", "e" to "d", "r" to "f", "t" to "g", "y" to "h", "u" to "j", "i" to "k", "o" to "l")
        for ((top, below) in oneToTwo) assertEquals("$top down", below, withShift.at(top).go(down).focusedLabel())
        val twoToOne = mapOf("a" to "q", "s" to "w", "d" to "e", "f" to "r", "g" to "t", "h" to "y", "j" to "u", "k" to "i", "l" to "o")
        for ((below, top) in twoToOne) assertEquals("$below up", top, withShift.at(below).go(up).focusedLabel())
        // Rows two to three and three to four have no ties on the letters page (centres line up or are far
        // apart); the rule is checked on every page, with and without shift, wherever a tie does exist.
        var ties = 0
        for (page in KeyboardPage.entries) {
            for (hasShift in listOf(true, false)) {
                val model = KeyboardModel(hasShift = hasShift, page = page)
                val rows = model.rows
                for (r in rows.indices) {
                    for (i in rows[r].indices) {
                        for (by in listOf(-1, 1)) {
                            val other = rows.getOrNull(r + by) ?: continue
                            val centre = rows[r][i].centre
                            val best = other.minOf { kotlin.math.abs(it.centre - centre) }
                            val nearest = other.indices.filter { kotlin.math.abs(other[it].centre - centre) == best }
                            if (nearest.size < 2) continue
                            ties++
                            val moved = model.focusOn(KeyId(r, i)).go(if (by > 0) down else up)
                            val expected = if (by > 0) nearest.last() else nearest.first()
                            assertEquals("$page shift=$hasShift row $r key $i by $by", KeyId(r + by, expected), moved.focus)
                        }
                    }
                }
            }
        }
        assertTrue("ties found: $ties", ties >= 36)
    }

    @Test
    fun aMoveAndItsOppositeRetraceTheSameKeysMidChain() {
        val down = KeyboardDirection.Down
        val up = KeyboardDirection.Up
        assertEquals("d", withShift.at("e").go(down).go(down).go(up).focusedLabel())
        val chain = withShift.at("ACTION").go(up).go(up).go(up)
        assertEquals("o", chain.focusedLabel())
        assertEquals("k", chain.go(down).focusedLabel())
        // For every key on every page: walk up to three moves one way; after each, the opposite move returns to the
        // key just left (mid-chain, where the anchor and the key left differ, not only from a fresh start).
        var checked = 0
        for (page in KeyboardPage.entries) {
            for (hasShift in listOf(true, false)) {
                val model = KeyboardModel(hasShift = hasShift, page = page)
                for (r in model.rows.indices) {
                    for (i in model.rows[r].indices) {
                        for ((way, back) in listOf(down to up, up to down)) {
                            var previous = model.focusOn(KeyId(r, i))
                            repeat(3) { step ->
                                val (moved, result) = previous.move(way, isRepeat = false)
                                if (result != KeyboardMove.Moved) return@repeat
                                val (returned, backResult) = moved.move(back, isRepeat = false)
                                checked++
                                assertEquals(
                                    "$page shift=$hasShift from ${labelOf(model.focusOn(KeyId(r, i)).focusedSlot.key)}, " +
                                        "step ${step + 1} $way then $back",
                                    KeyboardMove.Moved to previous.focus,
                                    backResult to returned.focus,
                                )
                                previous = moved
                            }
                        }
                    }
                }
            }
        }
        assertTrue("checked $checked", checked > 100)
    }

    @Test
    fun upThenDownComesBackToTheSameKey() {
        val start = withShift.at("l")
        val up = start.go(KeyboardDirection.Up)
        assertEquals("o", up.focusedLabel())
        // From o alone, Down is as near k (8.0) as l (9.0) and takes the right one, l; the remembered column agrees.
        assertEquals("l", withShift.at("o").go(KeyboardDirection.Down).focusedLabel())
        assertEquals("l", up.go(KeyboardDirection.Down).focusedLabel())

        // A run across every row and back: Space → v → g → t, then down again to Space.
        var m = withShift.at("SPACE")
        val path = mutableListOf<String>()
        repeat(3) {
            m = m.go(KeyboardDirection.Up)
            path += m.focusedLabel()
        }
        repeat(3) {
            m = m.go(KeyboardDirection.Down)
            path += m.focusedLabel()
        }
        assertEquals(listOf("v", "g", "t", "g", "v", "SPACE"), path)
    }

    @Test
    fun aSidewaysMoveForgetsTheRememberedColumn() {
        val m = withShift.at("l").go(KeyboardDirection.Up).go(KeyboardDirection.Left)
        assertEquals("i", m.focusedLabel())
        assertNull(m.anchor)
        // From i (7.5) Down: j (7.0) and k (8.0) are as near; Down takes the right one.
        assertEquals("k", m.go(KeyboardDirection.Down).focusedLabel())
    }

    @Test
    fun upFromTheTopRowAndDownFromTheBottomRowLeaveTheKeyboard() {
        val top = withShift.at("t")
        val (afterUp, up) = top.move(KeyboardDirection.Up, isRepeat = false)
        assertEquals(KeyboardMove.LeftAbove, up)
        assertEquals(top, afterUp)
        val bottom = withShift.at("SPACE")
        val (afterDown, down) = bottom.move(KeyboardDirection.Down, isRepeat = false)
        assertEquals(KeyboardMove.LeftBelow, down)
        assertEquals(bottom, afterDown)
        // A held key stops there instead: holding a direction never carries the cursor out.
        assertEquals(KeyboardMove.Stayed, top.move(KeyboardDirection.Up, isRepeat = true).second)
        assertEquals(KeyboardMove.Stayed, bottom.move(KeyboardDirection.Down, isRepeat = true).second)
        assertEquals(KeyboardMove.Moved, top.move(KeyboardDirection.Down, isRepeat = true).second)
    }

    @Test
    fun withoutShiftNavigationSkipsTheHole() {
        assertEquals("z", search.at("123").go(KeyboardDirection.Up).focusedLabel())
        assertEquals("z", search.at("a").go(KeyboardDirection.Down).focusedLabel())
        assertEquals("DEL", search.at("z").go(KeyboardDirection.Left).focusedLabel())
        assertEquals("z", search.at("DEL").go(KeyboardDirection.Right).focusedLabel())
        assertEquals(KeyboardMove.Stayed, search.at("z").move(KeyboardDirection.Left, isRepeat = true).second)
        // The letters keep the centres they have with shift: d (3.0) is above x (3.0).
        assertEquals("x", search.at("d").go(KeyboardDirection.Down).focusedLabel())
    }

    // Pressing

    @Test
    fun everyKeyKindGivesItsEvent() {
        assertEquals(KeyboardEvent.Type('q'), withShift.at("q").press().second)
        assertEquals(KeyboardEvent.Delete, withShift.at("DEL").press().second)
        assertEquals(KeyboardEvent.Space, withShift.at("SPACE").press().second)
        assertEquals(KeyboardEvent.Action, withShift.at("ACTION").press().second)
        assertEquals(KeyboardEvent.Hide, withShift.at("HIDE").press().second)
        val symbols = withShift.copy(page = KeyboardPage.Symbols)
        assertEquals(KeyboardEvent.Type('@'), symbols.at("@").press().second)
        assertEquals(KeyboardEvent.Type('7'), symbols.at("7").press().second)
        assertEquals(KeyboardEvent.Delete, symbols.at("DEL").press().second)
        // Shift and page keys act on the keyboard and tell the caller nothing.
        assertNull(withShift.at("SHIFT").press().second)
        assertNull(withShift.at("123").press().second)
        assertNull(symbols.at("ABC").press().second)
        assertNull(symbols.at("=\\<").press().second)
    }

    @Test
    fun aTapFocusesAndPressesAtOnce() {
        val (after, event) = withShift.tap(withShift.idOf("m"))
        assertEquals(KeyboardEvent.Type('m'), event)
        assertEquals("m", after.focusedLabel())
    }

    @Test
    fun shiftOnceTypesOneCapitalThenTurnsOff() {
        val once = withShift.at("SHIFT").press().first
        assertEquals(ShiftMode.Once, once.shift)
        val (afterA, a) = once.tap(once.idOf("a"))
        assertEquals(KeyboardEvent.Type('A'), a)
        assertEquals(ShiftMode.Off, afterA.shift)
        assertEquals(KeyboardEvent.Type('b'), afterA.tap(afterA.idOf("b")).second)
    }

    @Test
    fun shiftTwiceLocksAndAThirdPressTurnsItOff() {
        val locked = withShift.at("SHIFT").press().first.press().first
        assertEquals(ShiftMode.Locked, locked.shift)
        val (afterA, a) = locked.tap(locked.idOf("a"))
        val (afterB, b) = afterA.tap(afterA.idOf("b"))
        assertEquals(listOf(KeyboardEvent.Type('A'), KeyboardEvent.Type('B')), listOf(a, b))
        assertEquals(ShiftMode.Locked, afterB.shift)
        assertEquals(ShiftMode.Off, afterB.at("SHIFT").press().first.shift)
    }

    @Test
    fun shiftIsSpentOnALetterOnly() {
        // Shift once, then a digit from the symbols page: the digit as it is, shift still waiting for a letter.
        val once = withShift.toggleShift()
        assertEquals(ShiftMode.Once, once.shift)
        val symbols = once.toPage(KeyboardPage.Symbols)
        val (after, one) = symbols.tap(symbols.idOf("1"))
        assertEquals(KeyboardEvent.Type('1'), one)
        assertEquals(ShiftMode.Once, after.shift)
    }

    @Test
    fun toggleShiftFollowsTheKeysCycleAndDoesNothingWithoutShift() {
        assertEquals(
            listOf(ShiftMode.Once, ShiftMode.Locked, ShiftMode.Off),
            generateSequence(withShift.toggleShift()) { it.toggleShift() }.take(3).map { it.shift }.toList(),
        )
        assertEquals(search, search.toggleShift())
        assertEquals(KeyboardEvent.Type('a'), search.toggleShift().at("a").press().second)
    }

    // Pages

    @Test
    fun pageKeysSwitchPagesAndKeepTheFocusInPlace() {
        val symbols = withShift.at("123").press().first
        assertEquals(KeyboardPage.Symbols, symbols.page)
        assertEquals("ABC", symbols.focusedLabel())
        val letters = symbols.press().first
        assertEquals(KeyboardPage.Letters, letters.page)
        assertEquals("123", letters.focusedLabel())
    }

    @Test
    fun symbols2AndBack() {
        val symbols = withShift.copy(page = KeyboardPage.Symbols).at("=\\<")
        val more = symbols.press().first
        assertEquals(KeyboardPage.Symbols2, more.page)
        assertEquals("#+=", more.focusedLabel())
        val back = more.press().first
        assertEquals(KeyboardPage.Symbols, back.page)
        assertEquals("=\\<", back.focusedLabel())
        // ABC on Symbols 2 goes straight to the letters.
        val letters = more.at("ABC").press().first
        assertEquals(KeyboardPage.Letters, letters.page)
        assertEquals("123", letters.focusedLabel())
    }

    @Test
    fun aPageChangeFocusesTheNearestKeyOnTheSameRow() {
        // p (9.5) → 0 (9.5); l (9.0) → ( (8.5) or ) (9.5): the left one; m (8.0) → / (8.0).
        assertEquals("0", withShift.at("p").togglePage().focusedLabel())
        assertEquals("(", withShift.at("l").togglePage().focusedLabel())
        assertEquals("/", withShift.at("m").togglePage().focusedLabel())
        // Symbols 2's short second row: & (4.5) → | (4.0) or \ (5.0): the left one.
        assertEquals("|", withShift.copy(page = KeyboardPage.Symbols).at("&").toPage(KeyboardPage.Symbols2).focusedLabel())
        // Without shift, =\< (0.75) lands on z: the hole is skipped.
        assertEquals("z", search.copy(page = KeyboardPage.Symbols).at("=\\<").togglePage().focusedLabel())
        // togglePage from Symbols 2 goes to the letters.
        assertEquals(KeyboardPage.Letters, withShift.copy(page = KeyboardPage.Symbols2).togglePage().page)
    }

    @Test
    fun aFieldStartsOnItsPageAtTheFirstKey() {
        val hex = KeyboardSpec(actionLabel = "Done", hasShift = false, startPage = KeyboardPage.Symbols).startModel()
        assertEquals(KeyboardPage.Symbols, hex.page)
        assertEquals("1", hex.focusedLabel())
        assertEquals(ShiftMode.Off, hex.shift)
        assertEquals("q", KeyboardSpec("Search", hasShift = false).startModel().focusedLabel())
    }

    @Test(expected = IllegalArgumentException::class)
    fun focusingAKeyThatIsNotThereFails() {
        search.focusOn(KeyId(2, 8))
    }
}
