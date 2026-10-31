package com.plumemu.home

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.plumemu.R
import com.plumemu.platforms.SystemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.plumemu.coreui.testing.ReferenceShots

/** The facts line's text for the four shapes of data, and what it drops when it is too wide. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SystemFactsTest {
    private val sep = " · "
    private val dash = "–"

    @Test
    fun snesReadsMakerKindAndTheYearsItWasMade() =
        assertEquals(listOf("Nintendo · Home console · 1990${dash}2003", "Nintendo · 1990${dash}2003", "1990${dash}2003"), SnesInfo.factsIn())

    @Test
    fun switchIsStillMadeSoItsYearsEndInPresent() =
        assertEquals("Nintendo · Home console · 2017${dash}present", SwitchInfo.factsIn().first())

    @Test
    fun withoutAnEndYearTheLaunchYearStandsAlone() = assertEquals("Nintendo · Handheld · 2004", NdsInfo.factsIn().first())

    @Test
    fun arcadeHasNoFactsSoTheBadgeStandsAlone() = assertEquals(emptyList<String>(), ArcadeInfo.factsIn())

    @Test
    fun arcadeSaysItsGamesComeFromManyMakers() {
        assertEquals(listOf("Arcade games from many makers"), ArcadeAboutInfo.factsIn())
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Arcade games from many makers", context.getString(R.string.system_facts_many_makers))
    }

    @Test
    fun theManyMakersLineStandsInForAMissingMakerOnly() {
        val withMaker = SystemInfo("X", "Acme", SystemKind.Console, 1990, 1995, manyMakers = true)
        assertEquals("Acme · Home console · 1990${dash}1995", withMaker.factsIn().first())
        val shipped =
            listOf("arcade", "mame", "fbneo").map { id -> systemInfoOf(shippedSystem(id)) }
        shipped.forEach { assertTrue(it!!.manyMakers) }
        assertTrue("a system with a maker never gets the line", !systemInfoOf(shippedSystem("snes"))!!.manyMakers)
    }

    @Test
    fun aboutIsPresentOnlyForTheSystemsThatHaveIt() {
        assertEquals(R.string.system_about_snes, systemInfoOf(shippedSystem("snes"))!!.about)
        assertEquals(R.string.system_about_arcade, systemInfoOf(shippedSystem("arcade"))!!.about)
        assertEquals(null, systemInfoOf(shippedSystem("amiga"))!!.about)
        assertEquals(null, SnesInfo.about)
    }

    @Test
    fun aMissingPartLeavesOutItsSeparator() {
        val noMaker = SystemInfo("X", null, SystemKind.Computer, 1985, 1995)
        assertEquals(listOf("Computer · 1985${dash}1995", "1985${dash}1995"), noMaker.factsIn())
        val noYear = SystemInfo("X", "Acme", SystemKind.Os)
        assertEquals(listOf("Acme · Operating system", "Acme"), noYear.factsIn())
    }

    @Test
    fun theLineDropsTheKindFirstThenTheMaker() {
        val lines = SnesInfo.factsIn()
        assertEquals(3, lines.size)
        assertTrue("each fallback is shorter", lines.zipWithNext().all { (a, b) -> b.length < a.length })
        assertTrue("the kind goes first", "Home console" !in lines[1] && "Nintendo" in lines[1])
        assertTrue("the maker goes next", "Nintendo" !in lines[2])
    }

    @Test
    fun theKindWordsAreStringResourcesInEnglish() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val words = SystemKind.entries.map { context.getString(it.wordRes()) }
        assertEquals(listOf("Home console", "Handheld", "Arcade board", "Computer", "Add-on", "Operating system"), words)
        assertEquals("present", context.getString(R.string.system_years_present))
    }
}
