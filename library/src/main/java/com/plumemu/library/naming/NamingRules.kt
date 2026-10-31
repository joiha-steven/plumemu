package com.plumemu.library.naming

import com.plumemu.identify.arcade.ArcadeSystems

/**
 * Bump when the identifier names some systems' games differently with the same name packs, so
 * rows named before keep the old answer: the pack version they were named with is still
 * current, so nothing else sends them back to the naming pass. Add the systems of the new
 * version to [CHANGES]. The app stores the version its last upgrade marking used
 * (`flags.namingRulesVersion`) and, when lower, calls `LibraryScanner.nameAgainForNewRules`
 * once; the next pass names those rows again from what is cached (arcade and launch files are
 * never opened; others from their cached fingerprints when the file is unchanged).
 *
 * 1: arcade sets get the regions their MAME description names (`ArcadeTitles.regions`); rows of
 * 0.0.2-alpha had none.
 * 2: arcade sets of one game in one directory are one row (`ArcadeCopies`); 0.0.2-alpha and
 * version 1 showed every set, titled by its description when two shared a short title.
 * 3: every system (see [ALL_SYSTEMS_SINCE]): a hack or translation keeps the original's disc
 * serial or cartridge code and is no longer named as the original (`HackNames`); the file-name
 * cleaning rules of the same release (TitleCleaner, NameCandidates, JunkFilter) change too.
 */
const val NAMING_RULES_VERSION = 3

/** The systems whose naming changed in each version above 0. */
private val CHANGES: Map<Int, Set<String>> = mapOf(
    1 to ArcadeSystems.IDS,
    2 to ArcadeSystems.IDS,
)

/**
 * The first version that names every system's rows again, not a list of systems: a rule that
 * reaches all of them (file-name cleaning, the code-versus-file-name choice) has no system list
 * worth keeping. A later version needing only some systems goes in [CHANGES] as before.
 */
internal const val ALL_SYSTEMS_SINCE = 3

/** True when rows named when the last marking used [storedVersion] were named by rules that reached every system. */
internal fun allSystemsNamedDifferentlySince(storedVersion: Int): Boolean = storedVersion < ALL_SYSTEMS_SINCE

/** The systems whose rows were named by older rules when the last marking used [storedVersion]. */
internal fun systemsNamedDifferentlySince(storedVersion: Int): Set<String> =
    CHANGES.filterKeys { it > storedVersion }.values.flatten().toSet()
