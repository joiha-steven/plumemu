package com.plumemu.library.scrape

import com.plumemu.scraper.ScrapeRequest

/**
 * Whether the box-art lookup after a rename (a chosen title or the person's own name) may ask
 * ScreenScraper for a game ([LibraryScrapeSource.renameLookup]). Docs:
 *.
 */
sealed interface RenameLookup {
    /**
     * Ask for [title] (the person's name, as stored) with [request]'s system and regions.
     * [fileMatchId]: a run already matched the file itself (by hash, serial, or file name and size)
     * to this ScreenScraper game, without a box front; only that game may then be stored.
     */
    data class Ready(val title: String, val request: ScrapeRequest, val fileMatchId: String? = null) : RenameLookup

    /** Nothing is asked, for [reason]. */
    data class Skip(val reason: RenameSkip) : RenameLookup
}

/** Why the lookup after a rename asks nothing, or stores nothing ([LibraryScrapeSource.storeRenameMatch]). */
enum class RenameSkip {
    /** The game is no longer in the library. */
    GAME_GONE,

    /** The game is being named again (a rescan): its key may change. */
    BEING_NAMED,

    /** The game's fix is no longer a chosen title or own name, or holds another name than the one searched. */
    NAME_CHANGED,

    /** The person picked its box art or chose "No box art" in Fix box art: never touched. */
    PERSON_CHOICE,

    /** A run already gave it a box front: left alone, Fix box art changes it. */
    HAS_BOX_ART,

    /** A run matched the file itself to another ScreenScraper game than the name found: the file wins. */
    MATCHED_BY_FILE,

    /** Not a game the box-art run looks up (a pinned shortcut). */
    NOT_LOOKED_UP,
}
