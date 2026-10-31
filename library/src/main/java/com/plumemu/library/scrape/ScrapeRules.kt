package com.plumemu.library.scrape

/**
 * Bump when the scraper starts judging ScreenScraper's answers differently, so results stored
 * before are wrong and nothing else would ask them again. The app stores the version its last
 * repair used (`flags.scrapeRulesVersion`) and, when lower, calls
 * `LibraryScanner.askAgainForNewScrapeRules` once before a scan; the scan names the games whose
 * name came from a forgotten match, and the box-art run after it asks them again, not `fresh`,
 * so the scraper's cached answers are judged again and saved pictures are reused.
 *
 * 1: a serial's answer counts only for the requested system (scraper's
 * `SystemIds.answerAccepted`); before, a SNES or GBA cartridge's code could give a 3DS game.
 * Repairs the matches found by a serial alone (`ScrapedGameDao.forgetSerialOnlyHashMatches`).
 *
 * 3: a serial sent with a CRC is checked too: a fan translation's CRC is in no
 * database and its code gave a 3DS or DS game. Repairs the hash matches whose stored answer is on
 * another system ([WrongSystemMatches]).
 *
 * 4: the same for the cartridge games whose code the naming pass had dropped before version 3 ran
 * (a hack or translation: identify's `HackNames`), judged as if their code had been sent,
 * and the games version 3 left showing the wrong title for lack of a file-name title, a guard seen
 * only in tests ([WrongSystemMatches.forgetCodeDroppedMatches], [FileNameTitles.showWhereMissing]).
 * The scraper itself judges answers as in version 3.
 *
 * Each repair runs once per library: only when the stored version is below the version that
 * brought it ([SERIAL_ONLY_REPAIR_SINCE_VERSION], [NO_MATCH_RETRY_SINCE_VERSION],
 * [WRONG_SYSTEM_REPAIR_SINCE_VERSION], [CODE_DROPPED_REPAIR_SINCE_VERSION]). Running an older one again would forget results the
 * rules then in force judged right and spend ScreenScraper quota asking them again.
 */
const val SCRAPE_RULES_VERSION = 4

/** 1: the serial-only matches are forgotten (`ScrapedGameDao.forgetSerialOnlyHashMatches`) by libraries below this version only. */
const val SERIAL_ONLY_REPAIR_SINCE_VERSION = 1

/** 3: the matches answered on another system are forgotten ([WrongSystemMatches]) by libraries below this version only. */
const val WRONG_SYSTEM_REPAIR_SINCE_VERSION = 3

/** 4: code-dropped cartridge games' wrong-system matches and version 3's leftovers ([WrongSystemMatches.forgetCodeDroppedMatches]) by libraries below this version only. */
const val CODE_DROPPED_REPAIR_SINCE_VERSION = 4

/**
 * 2: the name search tries the title without punctuation and its part before a subtitle separator
 * when the title as given finds nothing ("Captain Toad: Treasure Tracker": ScreenScraper's
 * search answers an empty entry for the colon). Every stored no-match (not a person's choice) is made
 * due once (`ScrapedGameDao.makeNoMatchesDueNow`) instead of waiting 30 days. Version 1 did not touch
 * no-matches, so only a stored version below this one makes them due.
 */
const val NO_MATCH_RETRY_SINCE_VERSION = 2
