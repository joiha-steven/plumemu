package com.plumemu.library.scrape

import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.media.SavedMedia

/**
 * What the person chose in "Fix box art...", stored for the game's first fix key
 * with `is_person_choice` set by [LibraryScrapeSource.storePersonChoice]: runs never replace it,
 * Download missing and Re-download never mark it.
 */
sealed interface PersonChoice {
    /**
     * A ScreenScraper game ([com.plumemu.scraper.Scraper.byGameId]) and the pictures saved for it
     * the way a run saves them ([com.plumemu.scraper.work.RunPictures]), possibly none. Its title
     * names the game over every automatic name, never over the person's own fix.
     */
    data class Picked(val result: ScrapeResult.Matched, val media: List<SavedMedia>) : PersonChoice

    /** "No box art": no pictures; the home shows the titled tile and no run fills it. The name stays. */
    data object NoBoxArt : PersonChoice
}

/** What [LibraryScrapeSource.storePersonChoice] did. */
enum class PersonChoiceStore {
    STORED,

    /**
     * Stored ("No box art" after a pick); the game still shows the pick's title and waits for the
     * naming pass (`needs_identify`): run `LibraryScanner.nameWaiting()` to name it now.
     */
    STORED_TO_NAME_AGAIN,

    /** The game is no longer in the library. */
    GAME_GONE,

    /** The game is being named again (a rescan): its fix key may change, so nothing is stored. */
    NOT_NAMED_YET,
}
