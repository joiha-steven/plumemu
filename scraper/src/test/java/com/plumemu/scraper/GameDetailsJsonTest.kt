package com.plumemu.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The JSON the library stores: stable names, a format version, lenient reading. */
class GameDetailsJsonTest {
    private val full = GameDetails(
        names = listOf(TextByRegion("us", "Comet Runner"), TextByRegion("jp", "Komet")),
        synopses = listOf(TextByLanguage("en", " Pilot a comet. "), TextByLanguage("fr", "Pilotez.")),
        dates = listOf(TextByRegion("us", "1993-04-02")),
        system = IdName("1", "Megadrive"),
        publisher = IdName("50", "Nimbus Soft"),
        developer = IdName(null, "Nimbus Team"),
        players = "1-2",
        rating = 15,
        genres = listOf(GameTag("7", "Shooter", main = true, parentId = "10", names = listOf(TextByLanguage("en", "Shooter")))),
        modes = listOf(GameTag("102", names = listOf(TextByLanguage("en", "Co-op")))),
        families = listOf(GameTag("2720", names = listOf(TextByLanguage("fr", "Comet")))),
        themes = listOf(GameTag("401")),
        styles = listOf(GameTag("501", main = true)),
        classifications = listOf(Classification("PEGI", "7")),
        rotation = 270,
        resolution = "224x288",
        controls = 6,
        buttonColours = listOf(ButtonColour("P1_BUTTON1", "FF0000")),
        cloneOf = "4000",
        notGame = false,
        topStaff = true,
        media = listOf(
            MediaRef(MediaKind.BoxFront, "us", "https://example.test/box", "png", "a1", 200_000),
            MediaRef(MediaKind.Support, null, "https://example.test/support", null, null, null),
            MediaRef(MediaKind.Screenshot, "wor", "https://example.test/ss", "png", "b1", 40_000),
            MediaRef(MediaKind.TitleScreen, "wor", "https://example.test/title", "png", "b2", 30_000),
            MediaRef(MediaKind.Manual, "us", "https://example.test/manual", "pdf", "c1", 2_500_000),
        ),
    )

    @Test fun `every field survives a round trip`() {
        assertEquals(full, GameDetails.fromJson(full.toJson()))
    }

    @Test fun `the JSON carries the format version and leaves defaults out`() {
        assertEquals("""{"format":1,"details":{}}""", GameDetails().toJson())
        assertEquals(GameDetails(), GameDetails.fromJson(GameDetails().toJson()))
        assertEquals(1, GameDetails.JSON_FORMAT)
    }

    /** Written by hand: a rename of a Kotlin property or enum entry must not change what is read. */
    @Test fun `JSON of format 1 written by hand reads to the same value`() {
        val text = """{"format":1,"details":{
            "names":[{"region":"us","text":"Comet Runner"},{"region":"jp","text":"Komet"}],
            "synopses":[{"language":"en","text":" Pilot a comet. "},{"language":"fr","text":"Pilotez."}],
            "dates":[{"region":"us","text":"1993-04-02"}],
            "system":{"id":"1","name":"Megadrive"},"publisher":{"id":"50","name":"Nimbus Soft"},"developer":{"name":"Nimbus Team"},
            "players":"1-2","rating":15,
            "genres":[{"id":"7","shortName":"Shooter","main":true,"parentId":"10","names":[{"language":"en","text":"Shooter"}]}],
            "modes":[{"id":"102","names":[{"language":"en","text":"Co-op"}]}],
            "families":[{"id":"2720","names":[{"language":"fr","text":"Comet"}]}],
            "themes":[{"id":"401"}],"styles":[{"id":"501","main":true}],
            "classifications":[{"type":"PEGI","value":"7"}],
            "rotation":270,"resolution":"224x288","controls":6,
            "buttonColours":[{"control":"P1_BUTTON1","hex":"FF0000"}],
            "cloneOf":"4000","notGame":false,"topStaff":true,
            "media":[
              {"kind":"box-front","region":"us","url":"https://example.test/box","format":"png","md5":"a1","size":200000},
              {"kind":"support","url":"https://example.test/support"},
              {"kind":"screenshot","region":"wor","url":"https://example.test/ss","format":"png","md5":"b1","size":40000},
              {"kind":"title-screen","region":"wor","url":"https://example.test/title","format":"png","md5":"b2","size":30000},
              {"kind":"manual","region":"us","url":"https://example.test/manual","format":"pdf","md5":"c1","size":2500000}
            ]}}"""

        assertEquals(full, GameDetails.fromJson(text))
    }

    @Test fun `missing and null fields take their defaults`() {
        val text = """{"format":1,"details":{"names":null,"rating":null,"main":null,
            "genres":[{"id":"7","main":null,"names":[{"text":null}]}],"classifications":[{}]}}"""

        assertEquals(
            GameDetails(genres = listOf(GameTag("7", names = listOf(TextByLanguage(null, "")))), classifications = listOf(Classification())),
            GameDetails.fromJson(text),
        )
    }

    @Test fun `a later format is read for what this version understands, and its number is told`() {
        val text = """{"format":9,"writtenBy":"a later app","details":{
            "names":[{"region":"us","text":"Comet","script":"latin"}],"ageGroups":{"a":[1,2]},"rotation":90}}"""

        assertEquals(GameDetails(names = listOf(TextByRegion("us", "Comet")), rotation = 90), GameDetails.fromJson(text))
        assertEquals(9, GameDetails.jsonFormatOf(text))
        assertEquals(GameDetails.JSON_FORMAT, GameDetails.jsonFormatOf(GameDetails().toJson()))
        assertNull(GameDetails.jsonFormatOf("""{"details":{}}"""))
        assertNull(GameDetails.jsonFormatOf("not json"))
    }

    @Test fun `a media reference of an unknown kind or without a url is dropped, not the details`() {
        val text = """{"format":1,"details":{"players":"2","media":[
            {"kind":"video","url":"https://example.test/video"},
            {"kind":"screenshot","url":"https://example.test/ss"},
            {"kind":"manual"},
            {"url":"https://example.test/nokind"},
            "not an object"]}}"""

        assertEquals(
            GameDetails(players = "2", media = listOf(MediaRef(MediaKind.Screenshot, null, "https://example.test/ss", null, null))),
            GameDetails.fromJson(text),
        )
    }

    @Test fun `a wrong-typed field takes its default alone, a wrong list entry is dropped alone`() {
        val text = """{"format":1,"details":{"rating":"high","players":"2","names":{"us":"x"},"rotation":[90],
            "genres":[{"id":"7","main":"yes"},{"id":"8"}],"system":{"id":"1","name":"Megadrive"}}}"""

        assertEquals(
            GameDetails(players = "2", genres = listOf(GameTag("8")), system = IdName("1", "Megadrive")),
            GameDetails.fromJson(text),
        )
    }

    @Test fun `text without stored details gives null and never throws`() {
        for (text in listOf("", "not json", "[]", "null", "{}", """{"format":1}""", """{"details":"x"}""", """{"details":[]}""")) {
            assertNull(text, GameDetails.fromJson(text))
        }
    }
}
