package com.plumemu.home

import com.plumemu.identify.name.TitleCleaner
import java.text.Normalizer
import java.util.Locale

/**
 * The letter a game files under: L1 / R1 ([letterJumpTarget]) jump by it. Lists are ordered by the games' sort name
 * (identify's `TitleCleaner.sortName`: no case, no accents, no leading "The" / "A" / "An", no leading symbols),
 * so the letter is the first character of that, and a digit, symbol or blank is [SYMBOL_KEY]:
 * "The Legend of Zelda" is under L. A row without a sort name (the fakes) gets one from its title the same way.
 */
internal object LetterKey {
    const val SYMBOL_KEY = '#'

    fun of(row: GameRowModel): Char = ofSortName(row.sortName.ifEmpty { TitleCleaner.sortName(row.title) })

    fun ofSortName(sortName: String): Char {
        val first = sortName.firstOrNull() ?: return SYMBOL_KEY
        if (!Character.isLetter(first)) return SYMBOL_KEY
        return first.toString().uppercase(Locale.ROOT).first()
    }

    /**
     * An app's sort name: its label's first letters folded (accents, case) and nothing dropped. The Apps list is
     * ordered by the label alone (launcher `InstalledApps`), so "The Weather" stays under T there.
     */
    fun foldedLabel(label: String): String =
        Normalizer.normalize(label.trim(), Normalizer.Form.NFD).filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }.lowercase(Locale.ROOT)
}
