package com.plumemu.coreui.components.keyboard

import androidx.compose.runtime.Immutable
import com.plumemu.coreui.theme.PlumDimens

/*
 * Plumemu's own on-screen keyboard, the layout:
 * three pages of four rows over 10 columns. Pure data, no Compose beyond the @Immutable marker.
 */

/** The keyboard's pages: letters, digits and the common symbols, then every other ASCII symbol. */
enum class KeyboardPage {
    Letters,
    Symbols,
    Symbols2,
}

/** What a key is. Character keys type; the others act or change the keyboard's own state. */
@Immutable
sealed interface KeyboardKey {
    /** Types [char] (a letter in upper case while shift is on). */
    data class Character(
        val char: Char,
    ) : KeyboardKey

    data object Shift : KeyboardKey

    data object Delete : KeyboardKey

    /** Goes to [target]; [label] is what the key shows ("123", "ABC", "=\<", "#+="), symbols, not words. */
    data class Page(
        val target: KeyboardPage,
        val label: String,
    ) : KeyboardKey

    /** Puts the keyboard away. */
    data object Hide : KeyboardKey

    data object Space : KeyboardKey

    /** The field's natural action; its label comes from [KeyboardSpec.actionLabel]. */
    data object Action : KeyboardKey
}

/**
 * A key in its row: it starts [start] columns from the row's left (gaps not counted: column `c`
 * starts at `(width − block) / 2 + c × (column + gap)`, the block 476 wide and centred) and spans [span] columns, the gaps inside it included.
 */
@Immutable
data class KeySlot(
    val key: KeyboardKey,
    val start: Float,
    val span: Float,
) {
    /** The key's centre in columns: what Up and Down measure by. */
    val centre: Float get() = start + span / 2f
}

/**
 * What a text field asks of the keyboard. [actionLabel] is the action key's word, which the
 * caller passes already translated ("Search", "Next", "Sign in", "Done"). Without [hasShift]
 * (Search, which ignores case) the shift key's place stays empty and the letters keep theirs.
 */
@Immutable
data class KeyboardSpec(
    val actionLabel: String,
    val hasShift: Boolean = true,
    val startPage: KeyboardPage = KeyboardPage.Letters,
)

/** The rows of every page, top to bottom, each left to right. */
object KeyboardLayout {
    const val COLUMNS = PlumDimens.KEYBOARD_COLUMNS
    const val ROWS = PlumDimens.KEYBOARD_ROWS

    /** Worded keys on rows three and four: wider than a letter. */
    private const val WIDE = 1.5f
    private const val SPACE = 4.5f
    private const val ACTION = 3f

    /** The second row of letters starts half a key in, like a phone keyboard. */
    private const val LETTERS_ROW_TWO_OFFSET = 0.5f

    /** The five keys of Symbols 2's second row sit in the middle of it. */
    private const val SYMBOLS2_ROW_TWO_OFFSET = 2.5f

    /** Every ASCII punctuation mark not on [KeyboardPage.Symbols] (password fields need them all). */
    private const val SYMBOLS2_ROW_ONE = "[]{}<>=*^~"
    private const val SYMBOLS2_ROW_TWO = "`|\\'\""

    /** The punctuation row is the same on both symbol pages, so flipping between them never moves it. */
    private const val PUNCTUATION = ".,:;!?/"

    const val LABEL_TO_SYMBOLS = "123"
    const val LABEL_TO_LETTERS = "ABC"
    const val LABEL_TO_SYMBOLS2 = "=\\<"
    const val LABEL_BACK_TO_SYMBOLS = "#+="

    /**
     * The rows of [page]. Without [hasShift] the shift key is left out but every other key keeps
     * its start, so the hole is where shift would be.
     */
    fun rows(
        page: KeyboardPage,
        hasShift: Boolean,
    ): List<List<KeySlot>> = (if (hasShift) withShift else withoutShift).getValue(page)

    private val letters: List<List<KeySlot>> =
        listOf(
            characters("qwertyuiop"),
            characters("asdfghjkl", LETTERS_ROW_TWO_OFFSET),
            rowThree(KeyboardKey.Shift, "zxcvbnm"),
            bottomRow(KeyboardKey.Page(KeyboardPage.Symbols, LABEL_TO_SYMBOLS)),
        )

    private val symbols: List<List<KeySlot>> =
        listOf(
            characters("1234567890"),
            characters("@#$%&-_+()"),
            rowThree(KeyboardKey.Page(KeyboardPage.Symbols2, LABEL_TO_SYMBOLS2), PUNCTUATION),
            bottomRow(KeyboardKey.Page(KeyboardPage.Letters, LABEL_TO_LETTERS)),
        )

    private val symbols2: List<List<KeySlot>> =
        listOf(
            characters(SYMBOLS2_ROW_ONE),
            characters(SYMBOLS2_ROW_TWO, SYMBOLS2_ROW_TWO_OFFSET),
            rowThree(KeyboardKey.Page(KeyboardPage.Symbols, LABEL_BACK_TO_SYMBOLS), PUNCTUATION),
            bottomRow(KeyboardKey.Page(KeyboardPage.Letters, LABEL_TO_LETTERS)),
        )

    private val withShift: Map<KeyboardPage, List<List<KeySlot>>> =
        mapOf(KeyboardPage.Letters to letters, KeyboardPage.Symbols to symbols, KeyboardPage.Symbols2 to symbols2)

    private val withoutShift: Map<KeyboardPage, List<List<KeySlot>>> =
        withShift.mapValues { (_, rows) -> rows.map { row -> row.filterNot { it.key == KeyboardKey.Shift } } }

    private fun characters(
        chars: String,
        offset: Float = 0f,
    ): List<KeySlot> = chars.mapIndexed { i, c -> KeySlot(KeyboardKey.Character(c), offset + i, 1f) }

    /** A wide key, seven characters, a wide delete: 1.5 + 7 + 1.5. */
    private fun rowThree(
        first: KeyboardKey,
        chars: String,
    ): List<KeySlot> =
        listOf(KeySlot(first, 0f, WIDE)) +
            characters(chars, WIDE) +
            KeySlot(KeyboardKey.Delete, WIDE + chars.length, WIDE)

    /** Page key 1.5, hide 1, space 4.5, action 3. */
    private fun bottomRow(page: KeyboardKey.Page): List<KeySlot> =
        listOf(
            KeySlot(page, 0f, WIDE),
            KeySlot(KeyboardKey.Hide, WIDE, 1f),
            KeySlot(KeyboardKey.Space, WIDE + 1f, SPACE),
            KeySlot(KeyboardKey.Action, WIDE + 1f + SPACE, ACTION),
        )
}
