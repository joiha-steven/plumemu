package com.plumemu.collections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CollectionPickerTest {
    private val rpg = PickerCollection(1, "RPG")
    private val hacks = PickerCollection(2, "Hacks")
    private val three = listOf(rpg, hacks, PickerCollection(3, "Kids"))

    @Test
    fun rowsAreOwnInOrderThenNewCollection() {
        val rows = PickerState(listOf(rpg, hacks), setOf(2)).rows
        assertEquals(
            listOf(PickerRow.Collection(1, "RPG", false), PickerRow.Collection(2, "Hacks", true), PickerRow.NewCollection),
            rows,
        )
    }

    @Test
    fun noCollectionsLeavesOnlyNewCollection() {
        assertEquals(listOf<PickerRow>(PickerRow.NewCollection), PickerState(emptyList(), emptySet()).rows)
    }

    @Test
    fun chooseAnUntickedRowAddsAndTicksAtOnce() {
        val choice = PickerState(three, emptySet()).onChoose(PickerRow.Collection(1, "RPG", false))
        assertEquals(PickerEffect.Add(1), choice.effect)
        assertEquals(setOf(1L), choice.state.holding)
    }

    @Test
    fun chooseATickedRowRemovesAndUnticks() {
        val choice = PickerState(three, setOf(1, 2)).onChoose(PickerRow.Collection(1, "RPG", true))
        assertEquals(PickerEffect.Remove(1), choice.effect)
        assertEquals(setOf(2L), choice.state.holding)
    }

    @Test
    fun newCollectionAsksForANameAndChangesNothing() {
        val state = PickerState(three, setOf(1))
        val choice = state.onChoose(PickerRow.NewCollection)
        assertEquals(PickerEffect.AskName, choice.effect)
        assertSame(state, choice.state)
    }

    @Test
    fun aCollectionRemovedWhileOpenDoesNothingAndIsNoTick() {
        val state = PickerState(listOf(rpg), setOf(1, 2)) // 2 was deleted
        assertEquals(listOf<PickerRow>(PickerRow.Collection(1, "RPG", true), PickerRow.NewCollection), state.rows)
        val choice = state.onChoose(PickerRow.Collection(2, "Hacks", true))
        assertEquals(PickerEffect.None, choice.effect)
        assertSame(state, choice.state)
    }

    @Test
    fun theMenuValueForZeroOneAndThreeCollections() {
        assertEquals(MenuValue.None, menuValue(emptySet(), three))
        assertEquals(MenuValue.One("Hacks"), menuValue(setOf(2), three))
        assertEquals(MenuValue.Many(3), menuValue(setOf(1, 2, 3), three))
    }

    @Test
    fun theMenuValueIgnoresADeletedCollection() {
        assertEquals(MenuValue.None, menuValue(setOf(9), three))
        assertEquals(MenuValue.One("RPG"), menuValue(setOf(1, 9), three))
    }
}
