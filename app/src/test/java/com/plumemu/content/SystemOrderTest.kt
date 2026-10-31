package com.plumemu.content

import com.plumemu.coredata.settings.SystemOrderSetting
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings › Library › System order: A to Z, Maker & year, Manual, and hidden systems. */
class SystemOrderTest {
    private val nes = OrderFacts("nes", "NES", "Nintendo", 1983)
    private val snes = OrderFacts("snes", "Super Nintendo", "Nintendo", 1990)
    private val gba = OrderFacts("gba", "Game Boy Advance", "Nintendo", 2001)
    private val megadrive = OrderFacts("megadrive", "Mega Drive", "Sega", 1988)
    private val psx = OrderFacts("psx", "PlayStation", "Sony", 1994)
    private val threeDo = OrderFacts("3do", "3DO", "Panasonic", 1993)
    private val arcade = OrderFacts("arcade", "Arcade")
    private val scummvm = OrderFacts("scummvm", "ScummVM")

    private fun arrange(
        systems: List<OrderFacts>,
        arrangement: SystemArrangement,
        locale: Locale = Locale.ENGLISH,
    ): List<String> = SystemOrder.arrange(systems, arrangement, locale) { it }.map { it.id }

    @Test
    fun aToZIsByTheShownNameIgnoringCase() {
        val lower = OrderFacts("x1", "amiga")
        val systems = listOf(snes, psx, lower, gba, threeDo, arcade)
        assertEquals(
            "digits first, then letters whatever their case",
            listOf("3do", "x1", "arcade", "gba", "psx", "snes"),
            arrange(systems, SystemArrangement(SystemOrderSetting.Name)),
        )
        assertEquals("the default is A to Z", SystemOrderSetting.Name, SystemArrangement().order)
    }

    @Test
    fun aToZFollowsThePersonsLanguage() {
        val omega = OrderFacts("a", "Omega")
        val ovrigt = OrderFacts("b", "\u00D6vrigt")
        val zeta = OrderFacts("c", "Zeta")
        val systems = listOf(zeta, ovrigt, omega)
        assertEquals("English sorts \u00D6 with O", listOf("a", "b", "c"), arrange(systems, SystemArrangement(), Locale.ENGLISH))
        assertEquals("Swedish sorts \u00D6 after Z", listOf("a", "c", "b"), arrange(systems, SystemArrangement(), Locale.forLanguageTag("sv")))
    }

    @Test
    fun makerAndYearGroupsByMakerOldestFirstAndUnknownLastByName() {
        val noYear = OrderFacts("odd", "Oddity", "Atari", null)
        val systems = listOf(scummvm, psx, gba, arcade, snes, megadrive, nes, threeDo, noYear)
        assertEquals(
            listOf("nes", "snes", "gba", "3do", "megadrive", "psx", "arcade", "odd", "scummvm"),
            arrange(systems, SystemArrangement(SystemOrderSetting.Maker)),
        )
    }

    @Test
    fun manualFollowsTheStoredListNewSystemsGoLastByNameAndGoneOnesAreSkipped() {
        val manual = SystemArrangement(SystemOrderSetting.Manual, manual = listOf("psx", "removed", "snes", "nes"))
        val systems = listOf(nes, snes, gba, psx, threeDo)
        assertEquals(
            "the stored list first (an id without games is skipped), then the new ones A to Z",
            listOf("psx", "snes", "nes", "3do", "gba"),
            arrange(systems, manual),
        )
    }

    @Test
    fun switchingAwayFromManualAndBackKeepsTheManualList() {
        val manual = SystemArrangement(SystemOrderSetting.Manual, manual = listOf("psx", "nes", "snes"))
        val systems = listOf(nes, snes, psx)
        assertEquals(listOf("nes", "psx", "snes"), arrange(systems, manual.copy(order = SystemOrderSetting.Name)))
        assertEquals(listOf("psx", "nes", "snes"), arrange(systems, manual.copy(order = SystemOrderSetting.Name).copy(order = SystemOrderSetting.Manual)))
    }

    @Test
    fun hiddenSystemsAreSplitOffInOrderAndTheLastShownOneNeverHides() {
        val arrangement = SystemArrangement(hidden = setOf("snes", "nes"))
        val arranged = SystemOrder.arrange(listOf(nes, snes, gba, psx), arrangement, Locale.ENGLISH) { it }
        val (shown, hidden) = SystemOrder.split(arranged, arrangement) { it.id }
        assertEquals(listOf("gba", "psx"), shown.map { it.id })
        assertEquals("kept in the column's order", listOf("nes", "snes"), hidden.map { it.id })

        val everything = SystemArrangement(hidden = setOf("nes", "snes"))
        val (allShown, noneHidden) = SystemOrder.split(listOf(nes, snes), everything) { it.id }
        assertEquals("a column with games never reads as empty", listOf("nes", "snes"), allShown.map { it.id })
        assertEquals(emptyList<OrderFacts>(), noneHidden)
    }

    @Test
    fun aMoveKeepsTheSlotsOfSystemsWithoutGamesNow() {
        // psx's folder is away (an SD card out); the person moves gba to the top.
        val stored = listOf("snes", "psx", "nes", "gba")
        assertEquals(listOf("gba", "psx", "snes", "nes"), SystemOrder.manualAfterMove(listOf("gba", "snes", "nes"), stored))
        assertEquals("new systems fill in after the stored ones", listOf("nes", "snes", "3do"), SystemOrder.manualAfterMove(listOf("nes", "snes", "3do"), listOf("snes", "nes")))
        assertEquals("nothing stored yet", listOf("a", "b"), SystemOrder.manualAfterMove(listOf("a", "b"), emptyList()))
    }
}
