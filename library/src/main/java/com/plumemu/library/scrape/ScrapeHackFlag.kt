package com.plumemu.library.scrape

import com.plumemu.identify.name.VariantTag
import com.plumemu.identify.name.VariantTags

/** Whether a stored file name is tagged as a hack (`[Hack]`, `[h1]`): the scraper then tries the parent game's box art if it finds none. */
internal object ScrapeHackFlag {
    fun of(fileName: String): Boolean = VariantTag.Hack in VariantTags.of(fileName.substringBeforeLast('.'))
}
