package com.scanforge.core.domain.selection

/**
 * State of the document library's multi-select (batch) mode. [active] tracks whether the selection
 * UI (contextual action bar) is showing; [selectedIds] is the current set. Kept as a pure value type
 * with a [reduce] function so the batch-selection behaviour is unit testable without any UI.
 */
data class SelectionState(
    val active: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
) {
    val count: Int get() = selectedIds.size

    fun isSelected(id: Long): Boolean = id in selectedIds

    /** True when every id in [total] candidates is selected (and there is at least one). */
    fun isAllSelected(total: Int): Boolean = total > 0 && selectedIds.size == total

    companion object {
        val Idle = SelectionState()
    }
}

/** Intents that drive [SelectionState]. */
sealed interface SelectionAction {
    /** Enter selection mode, seeding it with [id] (typically a long-press). */
    data class Enter(val id: Long) : SelectionAction

    /** Add the id if absent, remove it if present. Deselecting the last item exits the mode. */
    data class Toggle(val id: Long) : SelectionAction

    /** Select every id in [ids], entering selection mode if needed. */
    data class SelectAll(val ids: Collection<Long>) : SelectionAction

    /** Drop an id that no longer exists (e.g. deleted) from the selection without exiting. */
    data class Remove(val id: Long) : SelectionAction

    /** Leave selection mode and clear the set. */
    data object Clear : SelectionAction
}

/**
 * Computes the next [SelectionState] for an action. Toggling the final selected item — or clearing —
 * leaves selection mode entirely, matching the expected contextual-action-bar behaviour.
 */
fun SelectionState.reduce(action: SelectionAction): SelectionState = when (action) {
    is SelectionAction.Enter -> SelectionState(active = true, selectedIds = setOf(action.id))

    is SelectionAction.Toggle -> {
        val next = if (action.id in selectedIds) selectedIds - action.id else selectedIds + action.id
        if (next.isEmpty()) SelectionState.Idle else SelectionState(active = true, selectedIds = next)
    }

    is SelectionAction.SelectAll -> {
        val next = action.ids.toSet()
        if (next.isEmpty()) SelectionState.Idle else SelectionState(active = true, selectedIds = next)
    }

    is SelectionAction.Remove -> {
        val next = selectedIds - action.id
        if (next.isEmpty()) SelectionState.Idle else copy(selectedIds = next)
    }

    SelectionAction.Clear -> SelectionState.Idle
}
