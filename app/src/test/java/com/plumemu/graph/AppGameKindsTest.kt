package com.plumemu.graph

import com.plumemu.coredata.db.AppKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Which installed apps are Android games or ports: the person's choice, then the data, then Android's mark. */
class AppGameKindsTest {
    private val retroArch = "com.retroarch"
    private val plumemu = "com.plumemu"

    private fun classify(
        vararg apps: InstalledApp,
        known: Map<String, String> = emptyMap(),
        choices: Map<String, AppKind> = emptyMap(),
        emulators: Set<String> = setOf(retroArch),
    ) = appGameSystems(apps.toList(), known, choices, emulators, plumemu)

    private fun app(packageName: String, isGame: Boolean = false) = InstalledApp(packageName, packageName, isGame)

    @Test
    fun androidsGameMarkMakesAnAndroidGameAndAnUnmarkedAppStaysAnApp() {
        assertEquals(mapOf("com.game" to "android"), classify(app("com.game", isGame = true), app("com.tool")))
    }

    @Test
    fun aKnownPortAppGoesToPortsAndIsNotAlsoAnAndroidGame() {
        val marked = classify(app("com.port", isGame = true), known = mapOf("com.port" to "ports"))
        assertEquals(mapOf("com.port" to "ports"), marked)
    }

    @Test
    fun aKnownAndroidGameIsListedWithoutAndroidsMark() {
        assertEquals(mapOf("com.game" to "android"), classify(app("com.game"), known = mapOf("com.game" to "android")))
    }

    @Test
    fun aKnownAppNamingAnotherSystemIsIgnored() {
        assertEquals(emptyMap<String, String>(), classify(app("com.x"), known = mapOf("com.x" to "snes")))
    }

    @Test
    fun thePersonsChoiceBeatsAndroidsMarkAndTheKnownList() {
        val apps = arrayOf(app("com.flagged", isGame = true), app("com.known"), app("com.plain"))
        val known = mapOf("com.known" to "ports")
        val choices = mapOf("com.flagged" to AppKind.PORT, "com.known" to AppKind.GAME, "com.plain" to AppKind.GAME)

        assertEquals(
            mapOf("com.flagged" to "ports", "com.known" to "android", "com.plain" to "android"),
            classify(*apps, known = known, choices = choices),
        )
    }

    @Test
    fun notAGameTakesAMarkedOrKnownAppOut() {
        val marked =
            classify(
                app("com.flagged", isGame = true),
                app("com.known"),
                known = mapOf("com.known" to "ports"),
                choices = mapOf("com.flagged" to AppKind.APP, "com.known" to AppKind.APP),
            )
        assertEquals(emptyMap<String, String>(), marked)
    }

    @Test
    fun anEmulatorIsNeverListedAutomaticallyEvenWithTheGameMarkOrInTheKnownList() {
        assertFalse(retroArch in classify(app(retroArch, isGame = true)))
        assertFalse(retroArch in classify(app(retroArch), known = mapOf(retroArch to "android")))
    }

    @Test
    fun thePersonCanStillMarkAnEmulator() {
        assertEquals(mapOf(retroArch to "android"), classify(app(retroArch, isGame = true), choices = mapOf(retroArch to AppKind.GAME)))
    }

    @Test
    fun plumemuItselfIsNeverListedNotEvenByAChoice() {
        assertEquals(emptyMap<String, String>(), classify(app(plumemu, isGame = true), known = mapOf(plumemu to "android")))
        assertEquals(emptyMap<String, String>(), classify(app(plumemu), choices = mapOf(plumemu to AppKind.GAME)))
    }

    @Test
    fun aPackageWithSeveralLauncherActivitiesIsOneApp() {
        val marked = classify(InstalledApp("com.game", "Game", isGameFlag = false), InstalledApp("com.game", "Game editor", isGameFlag = true))
        assertEquals(mapOf("com.game" to "android"), marked)
    }
}
