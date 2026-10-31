package com.plumemu.scraper

import com.plumemu.scraper.ScraperFixture.Companion.json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** ScreenScraper writes HTML entities inside its text fields; a person must read the characters. */
class HtmlEntitiesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val synopsisWithEntities =
        "Beat &quot;The Master&quot; &amp; his crew. Don&#39;t panic, don&#x27;t run. Caf&eacute; &lt;3 &amp;quot;kept&amp;quot;"
    private val synopsisDecoded =
        "Beat \"The Master\" & his crew. Don't panic, don't run. Caf\u00e9 <3 &quot;kept&quot;"

    @Test fun `decode reads named, decimal and hex entities once`() {
        assertEquals(synopsisDecoded, HtmlEntities.decode(synopsisWithEntities))
        assertEquals("a\u00a0b", HtmlEntities.decode("a&nbsp;b"))
        assertEquals("<>'\"&", HtmlEntities.decode("&lt;&gt;&apos;&quot;&amp;"))
        assertEquals("\u00ff\u00c0\u00e9", HtmlEntities.decode("&yuml;&Agrave;&eacute;"))
    }

    @Test fun `decode leaves other text and unreadable entities alone`() {
        val plain = "Tom & Jerry; 100% \"fun\" <b> &unknownthing; &#xZZ; &#0; &#1114112; & ; &"
        assertEquals(plain, HtmlEntities.decode(plain))
        assertEquals("", HtmlEntities.decode(""))
        val controls = "a&#1;b&#x1F;c&#8;d"
        assertEquals("control characters stay written out", controls, HtmlEntities.decode(controls))
        assertEquals("tab, line feed, carriage return decode", "a\tb\nc\rd", HtmlEntities.decode("a&#9;b&#10;c&#x0D;d"))
    }

    @Test fun `encode is the inverse of decode`() {
        for (text in listOf("AT&T", "\"q\" <b> 'x'", "&quot;kept&quot;", "plain")) {
            assertEquals(text, HtmlEntities.decode(HtmlEntities.encode(text)))
        }
    }

    @Test fun `a match shows decoded text in the picked values and in every detail`() = runTest {
        ScraperFixture(tmp.newFolder()).use { fx ->
            fx.server.enqueue(
                json("jeuInfos-match.json") {
                    it.replace("Sonic must stop Dr. Robotnik.", synopsisWithEntities)
                        .replace(""""text": "Sega"""", """"text": "Sega &amp; Co"""")
                        .replace(""""text": "Sonic Team"""", """"text": "Sonic Team &quot;AM&quot;"""")
                        .replace(""""text": "Sonic The Hedgehog"}""", """"text": "Sonic &amp; Knuckles"}""")
                },
            )
            val matched = fx.scraper(fx.config()).scrape(fx.request(fx.rom())) as ScrapeResult.Matched
            assertEquals(synopsisDecoded, matched.info.synopsis)
            assertEquals("Sega & Co", matched.info.publisher)
            assertEquals("Sonic Team \"AM\"", matched.info.developer)
            // The stored details keep the text as given; the screen decodes it once on read.
            assertEquals("Sega &amp; Co", matched.details.publisher?.name)
            assertTrue(matched.details.synopses.any { HtmlEntities.decode(it.text.trim()) == synopsisDecoded })
            assertTrue(matched.details.names.any { it.text == "Sonic &amp; Knuckles" })
        }
    }
}
