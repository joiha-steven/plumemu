package com.plumemu.collections

/** An own collection as the picker lists it. */
data class PickerCollection(val id: Long, val name: String)

sealed interface PickerRow {
    data class Collection(val id: Long, val name: String, val ticked: Boolean) : PickerRow

    data object NewCollection : PickerRow
}

/** What choosing a row asks the data layer to do. [None]: the row's collection is gone, nothing to do. */
sealed interface PickerEffect {
    data class Add(val collectionId: Long) : PickerEffect

    data class Remove(val collectionId: Long) : PickerEffect

    /** Open the keyboard; the name makes a collection holding the game. */
    data object AskName : PickerEffect

    data object None : PickerEffect
}

/** The new state (ticks already changed) and the effect to run. The picker stays open. */
data class PickerChoice(val state: PickerState, val effect: PickerEffect)

/**
 * The game menu's "Add to collection…" picker: [collections] in position order and [holding], the ids of the collections
 * that hold the game. Ids in [holding] with no collection in the list (one just deleted) are not ticks.
 */
data class PickerState(val collections: List<PickerCollection>, val holding: Set<Long>) {
    val rows: List<PickerRow>
        get() = collections.map { PickerRow.Collection(it.id, it.name, it.id in holding) } + PickerRow.NewCollection

    fun onChoose(row: PickerRow): PickerChoice =
        when (row) {
            PickerRow.NewCollection -> PickerChoice(this, PickerEffect.AskName)
            is PickerRow.Collection ->
                when {
                    collections.none { it.id == row.id } -> PickerChoice(this, PickerEffect.None)
                    row.id in holding -> PickerChoice(copy(holding = holding - row.id), PickerEffect.Remove(row.id))
                    else -> PickerChoice(copy(holding = holding + row.id), PickerEffect.Add(row.id))
                }
        }
}

/** The game menu's value for "Add to collection…". */
sealed interface MenuValue {
    data object None : MenuValue

    data class One(val name: String) : MenuValue

    /** A count marker: the screen writes "n collections". */
    data class Many(val count: Int) : MenuValue
}

/** None when the game is in no collection, the name when in one, the count when in more; a deleted collection does not count. */
fun menuValue(holding: Set<Long>, collections: List<PickerCollection>): MenuValue {
    val held = collections.filter { it.id in holding }
    return when (held.size) {
        0 -> MenuValue.None
        1 -> MenuValue.One(held.single().name)
        else -> MenuValue.Many(held.size)
    }
}
