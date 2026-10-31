package com.plumemu.platforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrokenDataTest {
    private val valid = """
        {"formatVersion":2,
         "systems":[{"id":"nes","name":"NES","shortName":"NES","displayName":"NES","order":0,"folders":["nes"],"extensions":["nes"],
                     "cover":"landscape","tier":"tested","emulators":["e1"]}],
         "emulators":[{"id":"e1","name":"E","package":"com.example.emu","game":{"mode":"path","target":"data"}}]}
    """.trimIndent()

    /** [valid] with one more field on its emulator. */
    private fun withEmulatorField(field: String) =
        valid.replace("\"package\":\"com.example.emu\",", "\"package\":\"com.example.emu\",$field,")

    private fun load(text: String, log: RecordingLog = RecordingLog()) = PlatformLoader.load(text, "test.json", log)

    private fun failureOf(text: String): Pair<PlatformFailure, RecordingLog> {
        val log = RecordingLog()
        val result = load(text, log)
        assertTrue("expected a failure, got $result", result is PlatformLoadResult.Failed)
        return (result as PlatformLoadResult.Failed).failure to log
    }

    private fun assertOneLogLine(log: RecordingLog, vararg parts: String) {
        assertEquals(log.lines.toString(), 1, log.lines.size)
        for (part in parts) assertTrue("'$part' not in ${log.lines[0]}", log.lines[0].contains(part))
    }

    @Test
    fun aValidFileLoads() {
        val log = RecordingLog()
        val result = load(valid, log)
        assertTrue(result is PlatformLoadResult.Loaded)
        assertTrue(log.lines.isEmpty())
    }

    @Test
    fun invalidJsonIsRejected() {
        val (failure, log) = failureOf("{ this is not json")
        assertTrue(failure is PlatformFailure.NotJson)
        assertOneLogLine(log, "test.json", "not valid JSON")
    }

    @Test
    fun anEmptyFileAndANonObjectAreRejected() {
        assertTrue(failureOf("").first is PlatformFailure.NotJson)
        assertTrue(failureOf("[1,2]").first is PlatformFailure.NotJson)
    }

    @Test
    fun aMissingFieldNamesTheField() {
        val (failure, log) = failureOf(valid.replace("\"shortName\":\"NES\",", ""))
        assertTrue(failure is PlatformFailure.MissingField)
        assertEquals("shortName", (failure as PlatformFailure.MissingField).field)
        assertOneLogLine(log, "test.json", "shortName")
    }

    @Test
    fun aDuplicateSystemIdIsRejected() {
        val system = valid.substringAfter("\"systems\":[").substringBefore("],\n \"emulators\"")
        val (failure, log) = failureOf(valid.replace(system, "$system,$system"))
        assertEquals("nes", (failure as PlatformFailure.DuplicateId).id)
        assertEquals("system", failure.kind)
        assertOneLogLine(log, "two systems", "'nes'")
    }

    @Test
    fun aDuplicateEmulatorIdIsRejected() {
        val emulator = """{"id":"e1","name":"E","package":"com.example.emu","game":{"mode":"path","target":"data"}}"""
        val (failure, log) = failureOf(valid.replace(emulator, "$emulator,$emulator"))
        assertEquals("emulator", (failure as PlatformFailure.DuplicateId).kind)
        assertOneLogLine(log, "two emulators", "'e1'")
    }

    @Test
    fun aDanglingEmulatorIdIsRejected() {
        val (failure, log) = failureOf(valid.replace("\"emulators\":[\"e1\"]", "\"emulators\":[\"e1\",\"ghost\"]"))
        failure as PlatformFailure.DanglingEmulator
        assertEquals("nes", failure.systemId)
        assertEquals("ghost", failure.emulatorId)
        assertOneLogLine(log, "'nes'", "'ghost'")
    }

    @Test
    fun anUnknownFormatVersionIsRejected() {
        val (failure, log) = failureOf(valid.replace("\"formatVersion\":2", "\"formatVersion\":3"))
        assertEquals(3, (failure as PlatformFailure.UnsupportedVersion).found)
        assertOneLogLine(log, "formatVersion 3")
    }

    @Test
    fun aMissingFormatVersionIsRejected() {
        val (failure, _) = failureOf(valid.replace("\"formatVersion\":2,", ""))
        assertEquals(null, (failure as PlatformFailure.UnsupportedVersion).found)
    }

    @Test
    fun emptyFoldersAndExtensionsAreRejected() {
        val (noFolders, log) = failureOf(valid.replace("\"folders\":[\"nes\"]", "\"folders\":[]"))
        assertEquals("folders", (noFolders as PlatformFailure.EmptyField).field)
        assertOneLogLine(log, "'folders' of 'nes'")
        val (noExtensions, _) = failureOf(valid.replace("\"extensions\":[\"nes\"]", "\"extensions\":[]"))
        assertEquals("extensions", (noExtensions as PlatformFailure.EmptyField).field)
    }

    @Test
    fun badValuesAreRejectedWithTheFieldAndId() {
        val cases = mapOf(
            "\"cover\":\"landscape\"" to ("cover" to "\"cover\":\"square\""),
            "\"tier\":\"tested\"" to ("tier" to "\"tier\":\"maybe\""),
            "\"package\":\"com.example.emu\"" to ("package" to "\"package\":\"not a package\""),
            "\"mode\":\"path\"" to ("game.mode" to "\"mode\":\"telepathy\""),
            "\"extensions\":[\"nes\"]" to ("extensions" to "\"extensions\":[\".NES\"]"),
        )
        for ((original, change) in cases) {
            val (field, replacement) = change
            val (failure, log) = failureOf(valid.replace(original, replacement))
            assertEquals(field, (failure as PlatformFailure.BadValue).field)
            assertOneLogLine(log, "test.json", "'$field'")
        }
    }

    @Test
    fun aCoverRatioOutOfRangeOrAgainstItsClassIsRejected() {
        // `valid` is landscape: a ratio of 1.3 fits it, 0.9 does not, 9 is no box at all.
        val withRatio = { ratio: String -> valid.replace("\"cover\":\"landscape\"", "\"cover\":\"landscape\",\"coverRatio\":$ratio") }
        assertEquals(1.3, (load(withRatio("1.3")) as PlatformLoadResult.Loaded).catalog.system("nes")!!.coverRatio, 0.0)
        for (ratio in listOf("0.9", "9", "0")) {
            val (failure, log) = failureOf(withRatio(ratio))
            assertEquals(ratio, "coverRatio", (failure as PlatformFailure.BadValue).field)
            assertOneLogLine(log, "test.json", "'coverRatio' of 'nes'")
        }
    }

    @Test
    fun aSystemColourMustBeSixHexDigitsAfterAHash() {
        val withColor = { color: String -> valid.replace("\"cover\":\"landscape\"", "\"cover\":\"landscape\",\"color\":\"$color\"") }
        assertEquals(0x336699, (load(withColor("#336699")) as PlatformLoadResult.Loaded).catalog.system("nes")!!.color)
        for (color in listOf("336699", "#369", "#33669G", "red")) {
            val (failure, log) = failureOf(withColor(color))
            assertEquals(color, "color", (failure as PlatformFailure.BadValue).field)
            assertOneLogLine(log, "test.json", "'color' of 'nes'")
        }
    }

    /** [valid] whose system carries these extra fields. */
    private fun withSystemFields(fields: String) = valid.replace("\"cover\":\"landscape\"", "\"cover\":\"landscape\",$fields")

    @Test
    fun aKindAndAnEndYearAreReadWhenTheyFitTheMakerAndYear() {
        val ok = withSystemFields("\"maker\":\"Nintendo\",\"year\":1983,\"kind\":\"console\",\"endYear\":2003")
        val system = (load(ok) as PlatformLoadResult.Loaded).catalog.system("nes")!!
        assertEquals(SystemKind.Console, system.kind)
        assertEquals(2003, system.endYear)
        val stillMade = (load(withSystemFields("\"maker\":\"Nintendo\",\"year\":1983,\"kind\":\"add-on\"")) as PlatformLoadResult.Loaded).catalog.system("nes")!!
        assertEquals(SystemKind.AddOn, stillMade.kind)
        assertEquals(null, stillMade.endYear)
    }

    @Test
    fun aBadKindIsRejected() {
        for (kind in listOf("spaceship", "Console", "")) {
            val (failure, log) = failureOf(withSystemFields("\"maker\":\"Nintendo\",\"year\":1983,\"kind\":\"$kind\""))
            assertEquals("kind", (failure as PlatformFailure.BadValue).field)
            assertOneLogLine(log, "test.json", "'kind' of 'nes'")
        }
    }

    @Test
    fun aKindWithoutAYearIsRejected() {
        val (failure, log) = failureOf(withSystemFields("\"maker\":\"Nintendo\",\"kind\":\"console\""))
        assertEquals("kind", (failure as PlatformFailure.BadValue).field)
        assertOneLogLine(log, "test.json", "'kind' of 'nes'")
    }

    @Test
    fun aKindWithoutAMakerIsRejected() {
        val (failure, log) = failureOf(withSystemFields("\"kind\":\"console\""))
        assertEquals("kind", (failure as PlatformFailure.BadValue).field)
        assertOneLogLine(log, "test.json", "'kind' of 'nes'")
    }

    @Test
    fun anEndYearBeforeTheYearOrWithoutAYearIsRejected() {
        val cases = listOf(
            "\"maker\":\"N\",\"year\":1983,\"endYear\":1982",
            "\"maker\":\"N\",\"endYear\":2003",
        )
        for (fields in cases) {
            val (failure, log) = failureOf(withSystemFields(fields))
            assertEquals(fields, "endYear", (failure as PlatformFailure.BadValue).field)
            assertOneLogLine(log, "test.json", "'endYear' of 'nes'")
        }
        // The year it started is a legal end year (a system made for one year). No clock is read: any later year loads.
        assertTrue(load(withSystemFields("\"maker\":\"N\",\"year\":1983,\"endYear\":1983")) is PlatformLoadResult.Loaded)
        assertTrue(load(withSystemFields("\"maker\":\"N\",\"year\":1983,\"endYear\":2999")) is PlatformLoadResult.Loaded)
    }

    @Test
    fun inProductionNeedsAYearAndExcludesAnEndYear() {
        val ok = (load(withSystemFields("\"maker\":\"N\",\"year\":2017,\"kind\":\"console\",\"inProduction\":true")) as PlatformLoadResult.Loaded).catalog.system("nes")!!
        assertTrue(ok.inProduction)
        assertEquals(null, ok.endYear)
        val cases = listOf(
            "\"maker\":\"N\",\"year\":2017,\"endYear\":2020,\"inProduction\":true",
            "\"maker\":\"N\",\"inProduction\":true",
            "\"maker\":\"N\",\"year\":2017,\"inProduction\":false",
            "\"maker\":\"N\",\"year\":2017,\"inProduction\":true",
        )
        for (fields in cases) {
            val (failure, log) = failureOf(withSystemFields(fields))
            assertEquals(fields, "inProduction", (failure as PlatformFailure.BadValue).field)
            assertOneLogLine(log, "test.json", "'inProduction' of 'nes'")
        }
    }

    @Test
    fun aVitaTitleIdInAnArrayExtraIsRead() {
        val text = valid.replace(
            "\"game\":{\"mode\":\"path\",\"target\":\"data\"}",
            "\"game\":{\"mode\":\"vita_title_id\",\"target\":\"extra_array\",\"key\":\"AppStartParameters\",\"prefix\":[\"-r\"]}",
        )
        val catalog = (load(text) as PlatformLoadResult.Loaded).catalog
        assertEquals(
            GamePass(PassMode.VitaTitleId, GameTarget.ExtraArray, "AppStartParameters", listOf("-r")),
            catalog.emulator("e1")!!.game,
        )
    }

    @Test
    fun anArrayExtraWithoutAKeyNameOrAPrefixOnAnotherTargetIsRejected() {
        val (noKey, _) = failureOf(valid.replace("\"target\":\"data\"", "\"target\":\"extra_array\""))
        assertEquals("game.key", (noKey as PlatformFailure.BadValue).field)
        val (prefixOnData, log) = failureOf(valid.replace("\"target\":\"data\"", "\"target\":\"data\",\"prefix\":[\"-r\"]"))
        assertEquals("game.prefix", (prefixOnData as PlatformFailure.BadValue).field)
        assertOneLogLine(log, "test.json", "'game.prefix'", "e1")
    }

    @Test
    fun aTitleIdOutsideAnArrayExtraOrAnArrayExtraWithoutTitleIdIsRejected() {
        val pairs = listOf(
            "\"mode\":\"vita_title_id\",\"target\":\"data\"",
            "\"mode\":\"vita_title_id\",\"target\":\"extra\",\"key\":\"k\"",
            "\"mode\":\"path\",\"target\":\"extra_array\",\"key\":\"k\"",
            "\"mode\":\"uri\",\"target\":\"extra_array\",\"key\":\"k\",\"prefix\":[\"-r\"]",
        )
        for (pair in pairs) {
            val (failure, log) = failureOf(valid.replace("\"mode\":\"path\",\"target\":\"data\"", pair))
            assertEquals(pair, "game", (failure as PlatformFailure.BadValue).field)
            assertOneLogLine(log, "test.json", "'game'", "e1")
        }
    }

    @Test
    fun aSteamAppIdInATypedExtraOrATemplateIsRead() {
        fun gameOf(game: String) = (load(valid.replace("{\"mode\":\"path\",\"target\":\"data\"}", game)) as PlatformLoadResult.Loaded)
            .catalog.emulator("e1")!!.game
        assertEquals(
            GamePass(PassMode.SteamAppId, GameTarget.Extra, "app_id", idType = IdType.Number),
            gameOf("{\"mode\":\"steam_app_id\",\"target\":\"extra\",\"key\":\"app_id\",\"type\":\"int\"}"),
        )
        assertEquals(
            GamePass(PassMode.SteamAppId, GameTarget.Extra, "steamAppId", idType = IdType.Text),
            gameOf("{\"mode\":\"steam_app_id\",\"target\":\"extra\",\"key\":\"steamAppId\",\"type\":\"string\"}"),
        )
        assertEquals(
            GamePass(PassMode.SteamAppId, GameTarget.Data, null, template = "droiddeck://game/{id}"),
            gameOf("{\"mode\":\"steam_app_id\",\"target\":\"data\",\"template\":\"droiddeck://game/{id}\"}"),
        )
    }

    @Test
    fun aSteamAppIdWithAnUnknownTypeNoKeyOrATemplateWithoutTheIdIsRejected() {
        val cases = listOf(
            "\"mode\":\"steam_app_id\",\"target\":\"extra\",\"key\":\"k\",\"type\":\"long\"" to "game.type",
            "\"mode\":\"steam_app_id\",\"target\":\"extra\",\"key\":\"k\"" to "game.type",
            "\"mode\":\"steam_app_id\",\"target\":\"extra\",\"type\":\"int\"" to "game.key",
            "\"mode\":\"steam_app_id\",\"target\":\"data\",\"template\":\"droiddeck://game/\"" to "game.template",
            "\"mode\":\"steam_app_id\",\"target\":\"data\",\"template\":\"x://{id}/{id}\"" to "game.template",
            "\"mode\":\"steam_app_id\",\"target\":\"data\"" to "game.template",
            "\"mode\":\"steam_app_id\",\"target\":\"extra_array\",\"key\":\"k\"" to "game",
            "\"mode\":\"path\",\"target\":\"extra\",\"key\":\"k\",\"type\":\"int\"" to "game.type",
            "\"mode\":\"uri\",\"target\":\"data\",\"template\":\"x://{id}\"" to "game.template",
            "\"mode\":\"path\",\"target\":\"data\",\"pathExtensions\":[\"m3u\"]" to "game.pathExtensions",
            "\"mode\":\"uri\",\"target\":\"data\",\"pathExtensions\":[\".m3u\"]" to "game.pathExtensions",
            "\"mode\":\"steam_app_id\",\"target\":\"extra\",\"key\":\"k\",\"type\":\"int\",\"template\":\"x://{id}\"" to "game.template",
        )
        for ((pair, field) in cases) {
            val (failure, log) = failureOf(valid.replace("\"mode\":\"path\",\"target\":\"data\"", pair))
            assertEquals(pair, field, (failure as PlatformFailure.BadValue).field)
            assertOneLogLine(log, "test.json", "'$field'", "e1")
        }
    }

    @Test
    fun acceptsIsReadAndLimitsTheFilesAnEmulatorIsOfferedFor() {
        val catalog = (load(withEmulatorField("\"accepts\":[\"nes\"]")) as PlatformLoadResult.Loaded).catalog
        val e1 = catalog.emulator("e1")!!
        assertEquals(setOf("nes"), e1.accepts)
        assertTrue(e1.acceptsFile("Zork.NES"))
        assertFalse(e1.acceptsFile("Zork.zip"))
        val all = (load(valid) as PlatformLoadResult.Loaded).catalog.emulator("e1")!!
        assertEquals(null, all.accepts)
        assertTrue("no accepts: every file, as before", all.acceptsFile("Zork.zip"))
    }

    @Test
    fun acceptsOutsideTheEmulatorsSystemsOrMalformedIsRejected() {
        for (accepts in listOf("[\"iso\"]", "[]", "[\"NES\"]", "[\".nes\"]", "[\"\"]", "[\"nes\",\"nes\"]")) {
            val (failure, log) = failureOf(withEmulatorField("\"accepts\":$accepts"))
            assertEquals(accepts, "accepts", (failure as PlatformFailure.BadValue).field)
            assertOneLogLine(log, "test.json", "'accepts'", "e1")
        }
    }

    @Test
    fun anExtraKeyWithoutAKeyNameIsRejected() {
        val (failure, _) = failureOf(valid.replace("\"target\":\"data\"", "\"target\":\"extra\""))
        assertEquals("game.key", (failure as PlatformFailure.BadValue).field)
    }

    @Test
    fun aFolderUsedBySecondSystemIsRejected() {
        val second = """{"id":"fc","name":"FC","shortName":"FC","displayName":"FC","order":1,"folders":["nes"],"extensions":["fc"],
            |"cover":"portrait","tier":"untested","emulators":["e1"]}""".trimMargin()
        val (failure, log) = failureOf(valid.replace("],\n \"emulators\"", ",$second],\n \"emulators\""))
        assertEquals("nes", (failure as PlatformFailure.DuplicateFolder).folder)
        assertOneLogLine(log, "folder 'nes'")
    }

    /** [valid] plus a second system on its own folder; [fields] replaces its displayName and order. */
    private fun withSecondSystem(fields: String): String {
        val second = """{"id":"fc","name":"FC","shortName":"FC",$fields,"folders":["fc"],"extensions":["fc"],
            |"cover":"portrait","tier":"untested","emulators":["e1"]}""".trimMargin()
        return valid.replace("],\n \"emulators\"", ",$second],\n \"emulators\"")
    }

    @Test
    fun aBlankDisplayNameIsRejected() {
        val (failure, log) = failureOf(valid.replace("\"displayName\":\"NES\"", "\"displayName\":\"  \""))
        assertEquals("displayName", (failure as PlatformFailure.EmptyField).field)
        assertOneLogLine(log, "displayName", "'nes'")
    }

    @Test
    fun aMissingDisplayNameOrOrderIsRejected() {
        val (noName, _) = failureOf(valid.replace("\"displayName\":\"NES\",", ""))
        assertEquals("displayName", (noName as PlatformFailure.MissingField).field)
        val (noOrder, _) = failureOf(valid.replace("\"order\":0,", ""))
        assertEquals("order", (noOrder as PlatformFailure.MissingField).field)
    }

    @Test
    fun aDuplicateDisplayNameIsRejected() {
        val (failure, log) = failureOf(withSecondSystem("\"displayName\":\"NES\",\"order\":1"))
        assertEquals("NES", (failure as PlatformFailure.DuplicateDisplayName).displayName)
        assertOneLogLine(log, "displayName 'NES'", "'nes'", "'fc'")
    }

    @Test
    fun aDuplicateOrderIsRejected() {
        val (failure, log) = failureOf(withSecondSystem("\"displayName\":\"Famicom\",\"order\":0"))
        assertEquals(0, (failure as PlatformFailure.DuplicateOrder).order)
        assertOneLogLine(log, "order 0", "'nes'", "'fc'")
    }

    @Test
    fun noSystemsOrNoEmulatorsIsRejected() {
        val (noSystems, log) = failureOf("""{"formatVersion":2,"systems":[],"emulators":[]}""")
        assertEquals("systems", (noSystems as PlatformFailure.EmptyField).field)
        assertOneLogLine(log, "'systems'")
        val systems = valid.substringAfter("\"systems\":").substringBefore(",\n \"emulators\"")
        val (noEmulators, _) = failureOf("""{"formatVersion":2,"systems":$systems,"emulators":[]}""")
        assertEquals("emulators", (noEmulators as PlatformFailure.EmptyField).field)
    }

    @Test
    fun anEmulatorListedTwiceBySystemIsRejected() {
        val (failure, log) = failureOf(valid.replace("\"emulators\":[\"e1\"]", "\"emulators\":[\"e1\",\"e1\"]"))
        assertEquals("e1", (failure as PlatformFailure.DuplicateEmulatorRef).emulatorId)
        assertOneLogLine(log, "system 'nes'", "'e1' twice")
    }

    @Test
    fun aCoreNameIsAPlainFileName() {
        for (core in listOf("../evil.so", "dir/core.so", "")) {
            val withCore = withEmulatorField("\"core\":\"$core\"")
            val (failure, log) = failureOf(withCore)
            assertEquals("core", (failure as PlatformFailure.BadValue).field)
            assertOneLogLine(log, "'core' of 'e1'")
        }
    }

    @Test
    fun aCorePlaceholderNeedsACore() {
        val extras = """"game":{"mode":"path","target":"data"},"extras":{"LIBRETRO":"/cores/{core}"}"""
        val (failure, log) = failureOf(valid.replace("\"game\":{\"mode\":\"path\",\"target\":\"data\"}", extras))
        assertEquals("extras", (failure as PlatformFailure.BadValue).field)
        assertOneLogLine(log, "'extras' of 'e1'")
        val withCore = valid.replace("\"game\":{\"mode\":\"path\",\"target\":\"data\"}", "\"core\":\"x.so\",$extras")
        assertTrue(load(withCore) is PlatformLoadResult.Loaded)
    }

    @Test
    fun aMissingFieldNamesThePathAndTheId() {
        val (failure, log) = failureOf(valid.replace("\"name\":\"E\",", ""))
        failure as PlatformFailure.MissingField
        assertEquals("name", failure.field)
        assertEquals("$.emulators[0]", failure.path)
        assertEquals("e1", failure.id)
        assertEquals(
            "Platform data rejected: test.json is missing field 'name' at $.emulators[0] (id 'e1')",
            log.lines.single(),
        )
    }

    @Test
    fun aWrongTypeIsNotReportedAsInvalidJson() {
        val (failure, log) = failureOf(valid.replace("\"folders\":[\"nes\"]", "\"folders\":\"nes\""))
        failure as PlatformFailure.WrongType
        assertTrue(failure.path, failure.path.contains("systems") && failure.path.contains("folders"))
        assertEquals("nes", failure.id)
        assertOneLogLine(log, "wrong type", "(id 'nes')")
        assertTrue(!log.lines[0].contains("not valid JSON"))
    }

    @Test
    fun theLegalRiskFlagIsReadAndDefaultsToFalse() {
        val loaded = (load(valid) as PlatformLoadResult.Loaded).catalog
        assertEquals(false, loaded.emulator("e1")!!.legalRisk)
        val risky = withEmulatorField("\"legalRisk\":true")
        val catalog = (load(risky) as PlatformLoadResult.Loaded).catalog
        assertEquals(true, catalog.emulator("e1")!!.legalRisk)
        assertTrue(catalog.defaultCandidates("nes").isEmpty())
        assertEquals(listOf("e1"), loaded.defaultCandidates("nes").map { it.id })
        assertTrue(loaded.defaultCandidates("nothing").isEmpty())
    }

    private val appsSystem = """{"id":"android","name":"Android games","shortName":"Android","displayName":"Android games","order":1,
        "folders":[],"extensions":[],"cover":"portrait","tier":"untested","emulators":[],"source":"apps"}"""

    /** [valid] with an apps system added and the given `knownApps` object. */
    private fun withApps(knownApps: String, system: String = appsSystem) =
        valid.replace("\"cover\":\"landscape\",\"tier\":\"tested\",\"emulators\":[\"e1\"]}", "\"cover\":\"landscape\",\"tier\":\"tested\",\"emulators\":[\"e1\"]},$system")
            .replace("\"emulators\":[{\"id\"", "\"knownApps\":$knownApps,\"emulators\":[{\"id\"")

    @Test
    fun anAppsSystemMayBeEmptyAndKnownAppsLoad() {
        val result = load(withApps("""{"com.example.game":"android"}"""))
        assertTrue(result.toString(), result is PlatformLoadResult.Loaded)
        val catalog = (result as PlatformLoadResult.Loaded).catalog
        assertTrue(catalog.system("android")!!.fromInstalledApps)
        assertEquals("android", catalog.knownApps["com.example.game"])
    }

    @Test
    fun aSystemWithoutTheAppsMarkStillNeedsFoldersExtensionsAndEmulators() {
        val noEmulators = valid.replace("\"emulators\":[\"e1\"]}],", "\"emulators\":[]}],")
        val (failure, log) = failureOf(noEmulators)
        assertEquals("emulators", (failure as PlatformFailure.EmptyField).field)
        assertOneLogLine(log, "'emulators' of 'nes'")
        assertTrue(failureOf(valid.replace("\"folders\":[\"nes\"]", "\"folders\":[]")).first is PlatformFailure.EmptyField)
        assertTrue(failureOf(valid.replace("\"extensions\":[\"nes\"]", "\"extensions\":[]")).first is PlatformFailure.EmptyField)
    }

    @Test
    fun anAppsSystemWithFilesOrAnUnknownSourceIsRejected() {
        val withFolder = appsSystem.replace("\"folders\":[]", "\"folders\":[\"android\"]")
        assertEquals("folders", (failureOf(withApps("{}", withFolder)).first as PlatformFailure.BadValue).field)
        val withEmulator = appsSystem.replace("\"emulators\":[]", "\"emulators\":[\"e1\"]")
        assertEquals("emulators", (failureOf(withApps("{}", withEmulator)).first as PlatformFailure.BadValue).field)
        val unknown = appsSystem.replace("\"apps\"", "\"cloud\"")
        assertEquals("source", (failureOf(withApps("{}", unknown)).first as PlatformFailure.BadValue).field)
    }

    @Test
    fun aKnownAppOfAnUnknownSystemIsRejected() {
        val (failure, log) = failureOf(withApps("""{"com.example.game":"nosuch"}"""))
        assertEquals("com.example.game", (failure as PlatformFailure.BadKnownApp).packageName)
        assertOneLogLine(log, "knownApps entry 'com.example.game'", "'nosuch'")
    }

    @Test
    fun aKnownAppOfAFileSystemIsRejected() {
        val (failure, log) = failureOf(withApps("""{"com.example.game":"nes"}"""))
        assertEquals("com.example.game", (failure as PlatformFailure.BadKnownApp).packageName)
        assertOneLogLine(log, "knownApps entry 'com.example.game'", "'nes'")
    }

    @Test
    fun aKnownAppWithABadPackageNameIsRejected() {
        for (bad in listOf("notapackage", "com..x", "1com.example", "com.exa mple")) {
            val failure = failureOf(withApps("""{"$bad":"android"}""")).first
            assertTrue(bad, failure is PlatformFailure.BadKnownApp)
        }
    }

    @Test
    fun knownAppsThatIsNotAnObjectOrNotAStringValueIsRejected() {
        assertTrue(failureOf(withApps("""["com.example.game"]""")).first is PlatformFailure.WrongType)
        assertTrue(failureOf(withApps("""{"com.example.game":7}""")).first is PlatformFailure.WrongType)
    }

    // A package listed twice cannot be seen here: the JSON parser keeps the last of two equal keys.
    // The converter guards it instead (test_convert.py, AppsSystemTests.test_the_known_apps_table_has_no_package_twice).
}
