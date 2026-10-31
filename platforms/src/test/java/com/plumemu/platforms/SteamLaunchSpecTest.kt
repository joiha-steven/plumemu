package com.plumemu.platforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Steam games with the shipped data: the `.steam` file holds the Steam app id and each Steam app
 * gets it in its own shape.
 */
class SteamLaunchSpecTest {
    private val catalog = loadShipped()
    private val broforce = SteamAppId.parse("274190".toByteArray())!!

    // Nothing of the file is passed on: the game is inside the Steam app.
    private val location = GameLocation.Both("/storage/emulated/0/ROMs/steam/Broforce.steam", "content://x/Broforce.steam")

    @Test
    fun aSteamFileGoesOnlyToTheSteamAppsAndADesktopFileOnlyToWinNative() {
        val steam = catalog.system("steam")!!
        assertEquals(listOf("gamenative", "gamehub-lite", "droiddeck"), catalog.emulatorsFor(steam, "Broforce.steam").map { it.id })
        assertEquals(listOf("gamenative", "gamehub-lite", "droiddeck"), catalog.defaultCandidatesFor("steam", "Broforce.STEAM").map { it.id })
        assertEquals(listOf("winnative"), catalog.emulatorsFor(steam, "Broforce.desktop").map { it.id })
        assertEquals(setOf("steam"), catalog.emulator("gamenative")!!.accepts)
        // WinNative: every extension of its systems but .steam (Epic, GOG, Amazon files stay its own).
        assertEquals(setOf("amazon", "app", "desktop", "epic", "gog", "pcgame"), catalog.emulator("winnative")!!.accepts)
        assertEquals(listOf("winnative"), catalog.emulatorsFor(catalog.system("epic")!!, "Fortnite.epic").map { it.id })
        assertTrue(catalog.emulatorsFor(catalog.system("windows")!!, "Witcher.gog").any { it.id == "winnative" })
        // Without `accepts` an emulator takes every file of its systems, as before.
        val snes = catalog.system("snes")!!
        assertEquals(catalog.emulatorsOf(snes), catalog.emulatorsFor(snes, "Zork.sfc"))
        assertTrue(catalog.emulatorsOf(snes).all { it.accepts == null })
        // The windows system no longer claims .steam: such a file is a Steam game wherever it lies.
        assertFalse("steam" in catalog.system("windows")!!.extensions)
    }

    @Test
    fun steamListsTheThreeAppsBeforeWinNative() {
        val steam = catalog.system("steam")!!
        assertEquals(listOf("gamenative", "gamehub-lite", "droiddeck", "winnative"), steam.emulatorIds)
        assertTrue("steam" in steam.extensions)
        assertEquals(
            setOf("gamenative", "gamehub-lite", "droiddeck"),
            catalog.emulators.filter { it.game.mode == PassMode.SteamAppId }.map { it.id }.toSet(),
        )
        // Not legal-risk: Automatic may recommend them.
        assertEquals(listOf("gamenative", "gamehub-lite", "droiddeck", "winnative"), catalog.defaultCandidates("steam").map { it.id })
    }

    @Test
    fun gameNativeGetsTheIdAsAnIntExtraWithItsStore() {
        val built = LaunchSpecs.build(catalog.emulator("gamenative")!!, location, steamAppId = broforce)
        val expected = LaunchSpec(
            "app.gamenative", "app.gamenative.MainActivity", "app.gamenative.LAUNCH_GAME", null, null, null,
            mapOf("game_source" to ExtraValue.Text("STEAM"), "app_id" to ExtraValue.Number(274190)), emptySet(),
        )
        assertEquals(LaunchResult.Built(expected), built)
    }

    @Test
    fun gameHubLiteGetsTheIdAsAStringExtraUnderEitherPackage() {
        val hub = catalog.emulator("gamehub-lite")!!
        val activity = "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"
        val expected = LaunchSpec(
            "gamehub.lite", activity, "gamehub.lite.LAUNCH_GAME", null, null, null,
            mapOf("autoStartGame" to ExtraValue.Flag(true), "steamAppId" to ExtraValue.Text("274190")), emptySet(),
        )
        assertEquals(LaunchResult.Built(expected), LaunchSpecs.build(hub, location, steamAppId = broforce))
        val other = LaunchSpecs.build(hub, location, "emuready.gamehub.lite", steamAppId = broforce)
        assertEquals(LaunchResult.Built(expected.copy(packageName = "emuready.gamehub.lite")), other)
    }

    @Test
    fun droidDeckGetsTheIdInItsLink() {
        val built = LaunchSpecs.build(catalog.emulator("droiddeck")!!, location, steamAppId = broforce)
        val expected = LaunchSpec(
            "com.droiddeck.launcher", null, "android.intent.action.VIEW", null, "droiddeck://game/274190", null,
            emptyMap(), emptySet(),
        )
        assertEquals(LaunchResult.Built(expected), built)
    }

    @Test
    fun aSteamAppWithoutAnIdIsRefused() {
        for (id in listOf("gamenative", "gamehub-lite", "droiddeck")) {
            assertEquals(LaunchResult.NeedsSteamAppId(id), LaunchSpecs.build(catalog.emulator(id)!!, location))
        }
    }

    @Test
    fun anAppIdIsDigitsAndNothingElse() {
        fun utf8(text: String) = text.toByteArray(Charsets.UTF_8)
        assertEquals(274190, SteamAppId.parse(utf8("274190"))?.value)
        assertEquals(274190, SteamAppId.parse(utf8("  274190\r\n"))?.value)
        assertEquals(274190, SteamAppId.parse(utf8("\t274190\n\n"))?.value)
        assertEquals(274190, SteamAppId.parse(utf8("\uFEFF274190\n"))?.value)
        assertEquals(274190, SteamAppId.parse(utf8("274190\u0000"))?.value)
        assertEquals("274190", SteamAppId.parse(utf8("0274190"))?.text)
        assertEquals(Int.MAX_VALUE, SteamAppId.parse(utf8(Int.MAX_VALUE.toString()))?.value)
        val bad = listOf(
            "", "   \n", "0", "-274190", "+274190", "274 190", "274190\n620", "Broforce", "274190a", "0x42F0E",
            "2147483648", "99999999999", "\u0664\u0662", "274190.0",
        )
        for (text in bad) assertNull("'$text'", SteamAppId.parse(utf8(text)))
    }

    @Test
    fun aUtf16FileWithItsByteOrderMarkIsRead() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        assertEquals(274190, SteamAppId.parse(bom + "274190\r\n".toByteArray(Charsets.UTF_16LE))?.value)
        val bigEndian = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "274190".toByteArray(Charsets.UTF_16BE)
        assertEquals(274190, SteamAppId.parse(bigEndian)?.value)
        assertTrue(SteamAppId.isEmptyFile(bom + "\r\n".toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun emptyIsToldFromWrong() {
        for (blank in listOf("", " \r\n", "\uFEFF", "\u0000")) assertTrue("'$blank'", SteamAppId.isEmptyFile(blank.toByteArray()))
        assertFalse(SteamAppId.isEmptyFile("Broforce".toByteArray()))
        // The launcher reads at most a few KB; a full buffer of digits is still no app id.
        assertNull(SteamAppId.parse(ByteArray(SteamAppId.MAX_FILE_BYTES) { '7'.code.toByte() }))
        assertTrue(SteamAppId.MAX_FILE_BYTES in 64..4096)
    }
}
