package com.scanforge.core.domain.selection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelectionTest {

    @Test
    fun `enter seeds the set and activates`() {
        val state = SelectionState.Idle.reduce(SelectionAction.Enter(7))
        assertTrue(state.active)
        assertEquals(setOf(7L), state.selectedIds)
    }

    @Test
    fun `toggle adds then removes`() {
        val afterAdd = SelectionState.Idle
            .reduce(SelectionAction.Enter(1))
            .reduce(SelectionAction.Toggle(2))
        assertEquals(setOf(1L, 2L), afterAdd.selectedIds)

        val afterRemove = afterAdd.reduce(SelectionAction.Toggle(2))
        assertEquals(setOf(1L), afterRemove.selectedIds)
        assertTrue(afterRemove.active)
    }

    @Test
    fun `deselecting the last item exits selection mode`() {
        val state = SelectionState.Idle
            .reduce(SelectionAction.Enter(1))
            .reduce(SelectionAction.Toggle(1))
        assertEquals(SelectionState.Idle, state)
        assertFalse(state.active)
    }

    @Test
    fun `select all selects every id`() {
        val state = SelectionState.Idle.reduce(SelectionAction.SelectAll(listOf(1, 2, 3)))
        assertTrue(state.active)
        assertEquals(setOf(1L, 2L, 3L), state.selectedIds)
        assertTrue(state.isAllSelected(3))
        assertFalse(state.isAllSelected(4))
    }

    @Test
    fun `select all of nothing is idle`() {
        assertEquals(SelectionState.Idle, SelectionState.Idle.reduce(SelectionAction.SelectAll(emptyList())))
    }

    @Test
    fun `remove drops an id without exiting while others remain`() {
        val state = SelectionState(active = true, selectedIds = setOf(1, 2))
            .reduce(SelectionAction.Remove(1))
        assertEquals(setOf(2L), state.selectedIds)
        assertTrue(state.active)
    }

    @Test
    fun `remove of the last id exits`() {
        val state = SelectionState(active = true, selectedIds = setOf(5))
            .reduce(SelectionAction.Remove(5))
        assertEquals(SelectionState.Idle, state)
    }

    @Test
    fun `clear exits selection mode`() {
        val state = SelectionState(active = true, selectedIds = setOf(1, 2, 3))
            .reduce(SelectionAction.Clear)
        assertEquals(SelectionState.Idle, state)
    }

    @Test
    fun `count and isSelected reflect the set`() {
        val state = SelectionState(active = true, selectedIds = setOf(1, 2))
        assertEquals(2, state.count)
        assertTrue(state.isSelected(1))
        assertFalse(state.isSelected(9))
    }
}
