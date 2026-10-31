package com.plumemu.library.scrape

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which file names ask for the parent game's box art (ScrapeRequest.hack): only the Hack tag. */
class ScrapeHackFlagTest {
    @Test fun `a Hack tag sets the flag`() = assertTrue(ScrapeHackFlag.of("Crash Bandicoot - N. Sane Trilogy (USA) [Hack].pbp"))

    @Test fun `a GoodTools h tag sets the flag`() = assertTrue(ScrapeHackFlag.of("Super Mario Bros (U) [h1].nes"))

    @Test fun `a title that starts with a dot-hack is not a hack`() = assertFalse(ScrapeHackFlag.of(".hack//Infection (USA).iso"))

    @Test fun `a translation is not a hack`() = assertFalse(ScrapeHackFlag.of("Final Fantasy (J) [T+Eng].nes"))

    @Test fun `an untagged name is not a hack`() = assertFalse(ScrapeHackFlag.of("Crash Team Racing - Nitro Kart (USA).pbp"))
}
