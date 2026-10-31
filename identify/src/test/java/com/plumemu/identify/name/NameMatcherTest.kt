package com.plumemu.identify.name

import com.plumemu.identify.pack.ConsolePackBuilder
import com.plumemu.identify.pack.TestPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {
    private val titles = listOf(
        "Zork Quest", "Zork Quest II", "The Moonlit Harbor", "Ice & Fire Brigade", "Caf\u00e9 Racer Deluxe",
        "Pelvon's Revenge", "Starfield Tactics: Second Front", "Dravon Knight Red", "Dravon Knight Rex",
        "Mega Torn X", "Mega Torn 10", "Galfen Island", "Sarqui Racing 3", "Lumtor Golf '98",
        "Trinim Chronicles Final Mix",
    )

    private val pack = ConsolePackBuilder("test", 1).let { b ->
        titles.forEach { b.release(b.title(it)) }
        TestPacks.open(b.build())
    }
    private val matcher = NameMatcher(pack)

    /** Query (already cleaned by TitleCleaner) to the expected title, or null for no match. */
    private val cases: List<Pair<String, String?>> = listOf(
        "Zork Quest" to "Zork Quest",
        "zork quest" to "Zork Quest",
        "ZORK QUEST" to "Zork Quest",
        "Zork Quest II" to "Zork Quest II",
        "Zork Quest 2" to "Zork Quest II", // roman numeral
        "The Zork Quest 2" to "Zork Quest II", // leading article
        "Zork Quest 02" to "Zork Quest II", // leading zero
        "Zork Qeust 2" to "Zork Quest II", // swapped letters
        "Zork Qest" to "Zork Quest", // typo, and no number: never the sequel
        "Zork Quest 3" to null, // a different number is a different game
        "Zork Qeutt" to null, // a swap and a typo in ten letters: 0.8, under the threshold
        "Moonlit Harbor" to "The Moonlit Harbor",
        "Moonlit Harbor The" to "The Moonlit Harbor", // trailing article
        "A Moonlit Harbor" to "The Moonlit Harbor",
        "Moonlit Harbour" to "The Moonlit Harbor", // spelling
        "Ice and Fire Brigade" to "Ice & Fire Brigade",
        "Ice & Fire Brigade" to "Ice & Fire Brigade",
        "Cafe Racer Deluxe" to "Caf\u00e9 Racer Deluxe", // accents
        "CAF\u00c9 RACER DELUXE" to "Caf\u00e9 Racer Deluxe",
        "Pelvons Revenge" to "Pelvon's Revenge", // apostrophe
        "Pelvon\u2019s Revenge" to "Pelvon's Revenge",
        "Starfield Tactics - Second Front" to "Starfield Tactics: Second Front", // separators
        "Starfield Tactics Second Front" to "Starfield Tactics: Second Front",
        "Starfield Tactic: Second Front" to "Starfield Tactics: Second Front",
        "Starfeild Tactcs: Second Front" to "Starfield Tactics: Second Front", // two typos, long name
        "Starfield Tactics" to null, // a missing subtitle is not guessed
        "Dravon Knight Red" to "Dravon Knight Red",
        "Dravon Knight Reg" to null, // as close to Red as to Rex: ambiguous
        "Mega Torn X" to "Mega Torn X", // a single X stays a letter
        "Mega Torn 10" to "Mega Torn 10",
        "Mega Torn XI" to null,
        "Galfen Islnd" to "Galfen Island",
        "Galfen" to null,
        "Sarqui Racing III" to "Sarqui Racing 3",
        "Sarqui Racing" to null,
        "Lumtor Golf 98" to "Lumtor Golf '98",
        "Trinim Chronicle Final Mix" to "Trinim Chronicles Final Mix",
        "Trinim Chronicles Final Mx Extra" to null,
        "Qwerty Asdf" to null, // unrelated
        "" to null,
        "   " to null,
        "!!!" to null,
    )

    @Test fun table() {
        assertTrue(cases.size >= 30)
        val failures = cases.mapNotNull { (query, expected) ->
            val got = matcher.match(query)?.let { pack.titleName(it.titleIndex) }
            if (got == expected) null else "'$query': expected $expected, got $got"
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    @Test fun exactAndFuzzyAreToldApart() {
        val exact = matcher.match("the zork quest 2")
        assertTrue(exact?.isExact == true)
        assertEquals(1.0, exact?.score ?: 0.0, 0.0)
        val fuzzy = matcher.match("Zork Qeust 2")
        assertFalse(fuzzy?.isExact ?: true)
        assertTrue((fuzzy?.score ?: 0.0) in NameMatcher.DEFAULT_THRESHOLD..1.0)
    }

    @Test fun twoTitlesWithTheSameKeyStillMatch() {
        val b = ConsolePackBuilder("test", 1)
        b.release(b.title("Zork Quest", publisher = "One"))
        b.release(b.title("ZORK QUEST", publisher = "Two"))
        val twins = TestPacks.open(b.build())
        assertTrue(NameMatcher(twins).match("Zork Quest")?.isExact == true)
        assertTrue("same key on both sides is not ambiguous", NameMatcher(twins).match("Zork Qest") != null)
    }

    /** Titles with the same key are told apart by the file's own spelling, then region, then source. */
    @Test fun sameKeyTitlesPickTheClosestSpelling() {
        val b = ConsolePackBuilder("gb", 1)
        val softlist = b.source("MAME software lists", "0.289")
        val noIntro = b.source("No-Intro", "2026")
        listOf(
            "Wario Land 2", "Wario Land II", "KAO the Kangaroo", "Kao the Kangaroo", "Castlevania - Order Of Ecclesia",
            "Castlevania - Order of Ecclesia", "Mask", "The Mask", "World Cup USA 94", "World Cup USA '94",
            "RoboCop Versus The Terminator", "RoboCop versus The Terminator",
            "The Flintstones - The Surprise at Dinosaur Peak", "The Flintstones - The Surprise at Dinosaur Peak!",
        ).forEach { b.release(b.title(it), source = softlist) }
        b.release(b.title("Zork Fighters!"), regions = listOf("USA"), source = noIntro)
        b.release(b.title("Zork Fighters?"), regions = listOf("Japan"), source = noIntro)
        val packed = TestPacks.open(b.build())
        val m = NameMatcher(packed)
        fun named(vararg candidates: String, regions: List<String> = emptyList()) =
            m.match(candidates.toList(), regions)?.let { packed.titleName(it.titleIndex) }
        assertEquals("Wario Land II", named("Wario Land II"))
        assertEquals("Wario Land 2", named("Wario Land 2"))
        assertEquals("Kao the Kangaroo", named("Kao the Kangaroo"))
        assertEquals("KAO the Kangaroo", named("KAO the Kangaroo"))
        assertEquals("Castlevania - Order of Ecclesia", named("Castlevania - Order of Ecclesia"))
        assertEquals("The Mask", named("The Mask"))
        assertEquals("World Cup USA '94", named("World Cup USA '94"))
        assertEquals("RoboCop versus The Terminator", named("RoboCop versus The Terminator"))
        assertEquals("The Flintstones - The Surprise at Dinosaur Peak!", named("The Flintstones - The Surprise at Dinosaur Peak!"))
        // A fuzzy match (one typo) also lands on the closest spelling of its key.
        assertEquals("The Flintstones - The Surprise at Dinosaur Peak!", named("The Flintstones - The Surprise at Dinosuar Peak!"))
        // Equally far: a release in the file's region wins, then the better source.
        assertEquals("Zork Fighters!", named("Zork Fighters", regions = listOf("USA")))
        assertEquals("Zork Fighters?", named("Zork Fighters", regions = listOf("Japan")))
        assertEquals("Zork Fighters?", named("Zork Fighters?", regions = listOf("USA")))
    }

    @Test fun aNameWithoutPunctuationDoesNotChooseBetweenPunctuations() {
        val b = ConsolePackBuilder("gc", 1)
        val redump = b.source("Redump", "2026")
        val gametdb = b.source("GameTDB", "2026")
        b.release(b.title("Zork of Persia - Warrior Within"), source = redump)
        b.release(b.title("Zork of Persia: Warrior Within"), source = gametdb)
        val packed = TestPacks.open(b.build())
        val m = NameMatcher(packed)
        fun named(title: String) = m.match(title)?.let { packed.titleName(it.titleIndex) }
        assertEquals("Zork of Persia - Warrior Within", named("Zork of Persia Warrior Within"))
        assertEquals("Zork of Persia: Warrior Within", named("Zork of Persia: Warrior Within"))
        assertEquals("Zork of Persia: Warrior Within", named("Zork of Persia:Warrior Within"))
    }

    @Test fun sourceRankBreaksTheLastTie() {
        val b = ConsolePackBuilder("gb", 1)
        val first = b.source("No-Intro", "2026")
        val second = b.source("MAME software lists", "0.289")
        b.release(b.title("Zork-Quest"), source = second)
        b.release(b.title("Zork:Quest"), source = first)
        val packed = TestPacks.open(b.build())
        assertEquals("Zork:Quest", NameMatcher(packed).match("Zork Quest")?.let { packed.titleName(it.titleIndex) })
    }

    /** Names written as one word, from the corpus's no-space files. */
    @Test fun oneWordNamesMatchTheirTitleRunTogether() {
        val b = ConsolePackBuilder("gba", 1)
        listOf(
            "SpongeBob SquarePants and Friends - Battle for Volcano Island", "Lodoss-tou Senki", "Zhongguo Xiangqi - Chinese Chese",
            "Magic Knight Rayearth 2 - Making of Magic Knight", "Puyo Puyo Tsuu", "Zork Quest III", "Zork Quest",
            "Tom & Zork", "Flash ZorkSaturn Vol. 10", "Flash Zork Saturn Vol. 10",
        ).forEach { b.release(b.title(it)) }
        val packed = TestPacks.open(b.build())
        val m = NameMatcher(packed)
        fun named(title: String) = m.match(NameCandidates.of(title, "gba"))?.let { packed.titleName(it.titleIndex) }
        assertEquals("SpongeBob SquarePants and Friends - Battle for Volcano Island", named("SpongeBobSquarePantsandFriendsBattleforVolcanoIsland"))
        assertEquals("Lodoss-tou Senki", named("LodosstouSenki"))
        assertEquals("Zhongguo Xiangqi - Chinese Chese", named("ZhongguoXiangqiChineseChese"))
        assertEquals("Magic Knight Rayearth 2 - Making of Magic Knight", named("MagicKnightRayearth2MakingofMagicKnight"))
        assertEquals("Puyo Puyo Tsuu", named("PuyoPuyoTsuu"))
        assertEquals("Zork Quest III", named("ZorkQuestIII"))
        assertEquals("Zork Quest III", named("zorkquestiii"))
        assertEquals("Tom & Zork", named("TomZork"))
        assertEquals("Tom & Zork", named("TomAndZork"))
        // The run-together form of the name itself comes before a split of it.
        assertEquals("Flash ZorkSaturn Vol. 10", named("FlashZorkSaturnVol10"))
        assertEquals(null, named("ZorkQuestIV"))
        assertEquals(null, named("ZorkQuestXtreme"))
        // A spaced name is never run together: "Zork Quest III Plus" is not "Zork Quest III".
        assertEquals(null, named("Zork Quest III Plus"))
    }

    /** A near match that differs by an edition word or a whole short word is another game. */
    @Test fun nearMatchAcrossAnEditionOrAShortWordIsRefused() {
        val b = ConsolePackBuilder("gbc", 1)
        listOf(
            "Harvest Moon GB", "Legend of the River King GB", "Captain Tsubasa II - Super Striker", "Tomb Raider Chronicles",
            "Yu Yu Hakusho Dai-2-dan - Ankoku Bujutsukai Hen", "Galfen Island Adventure Collection Plus",
        ).forEach { b.release(b.title(it)) }
        val packed = TestPacks.open(b.build())
        val m = NameMatcher(packed)
        fun named(title: String) = m.match(title)?.let { packed.titleName(it.titleIndex) }
        assertEquals(null, named("Harvest Moon GBC"))
        assertEquals(null, named("Legend of the River King GBC"))
        assertEquals(null, named("Captain Tsubasa Vol. II - Super Striker"))
        assertEquals(null, named("Tomb Raider V - Chronicles"))
        assertEquals(null, named("Galfen Island Adventure Collection"))
        assertEquals("Galfen Island Adventure Collection Plus", named("Galfen Island Adventure Collection Plsu"))
        // Typos still match, and an equal key always does.
        assertEquals("Harvest Moon GB", named("Harvset Moon GB"))
        assertEquals("Legend of the River King GB", named("Legend of the Rivr King GB"))
        assertEquals("Tomb Raider Chronicles", named("Tomb Raider - Chronicles"))
        assertEquals("Yu Yu Hakusho Dai-2-dan - Ankoku Bujutsukai Hen", named("Yuu Yuu Hakusho Dai-2-dan - Ankoku Bujutsukai Hen"))
        assertTrue(m.differsByEdition(listOf("zork", "gbc"), listOf("zork", "gb")))
        assertTrue(m.differsByEdition(listOf("zork", "vol", "2"), listOf("zork", "2")))
        assertFalse(m.differsByEdition(listOf("zork", "quests"), listOf("zork", "quest")))
        assertFalse(m.differsByEdition(listOf("zork", "quest", "chronicle"), listOf("zork", "quest")))
        assertFalse(m.differsByEdition(listOf("zork", "quest", "cd1"), listOf("zork", "quest")))
        assertFalse(m.differsByEdition(listOf("pachi", "slot"), listOf("pachi", "and", "slot")))
    }

    /** Every candidate is tried for an equal key before any is scored fuzzily. */
    @Test fun anExactLaterCandidateBeatsAFuzzyFirstOne() {
        val b = ConsolePackBuilder("nds", 1)
        listOf("Zork Quest", "Zork Quests", "1943 - The Battle of Zork", "The Battle of Zork").forEach { b.release(b.title(it)) }
        val packed = TestPacks.open(b.build())
        val m = NameMatcher(packed)
        fun named(vararg candidates: String) = m.match(candidates.toList())?.let { packed.titleName(it.titleIndex) }
        assertEquals("Zork Quest", named("Zork Qeust", "Zork Quest"))
        assertEquals("1943 - The Battle of Zork", named("1943 - The Battle of Zork", "The Battle of Zork"))
        assertEquals(null, named())
    }

    @Test fun emptyPackMatchesNothing() {
        val empty = TestPacks.open(ConsolePackBuilder("test", 1).build())
        assertEquals(null, NameMatcher(empty).match("Zork Quest"))
    }

    @Test fun matchKeys() {
        val keys = listOf(
            "The Zork Quest II" to "zork quest 2",
            "Zork Quest, The" to "zork quest", // comma is punctuation; trailing article dropped
            "A" to "a", // an article alone stays
            "Zork - Quest: Part VIII" to "zork quest part 8",
            "Stra\u00dfe & Fj\u00f8rd" to "strasse and fjord",
            "\u0110\u1ea1i Zork" to "dai zork",
            "Zork X" to "zork x",
            "Zork XX" to "zork 20",
            "Disk A" to "disk a",
            "Mix Vi" to "mix 6",
        )
        keys.forEach { (title, key) -> assertEquals(title, key, MatchKey.of(title)) }
        assertEquals(listOf("2", "98"), MatchKey.numbers("zork 2 golf 98 x9"))
    }
}
