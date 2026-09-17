package com.example.feature.code.editor

/**
 * Efficient bounded undo/redo stack for editor text states.
 *
 * Prevents memory bloat by capping history at [maxCapacity] items.
 */
class CodeEditHistory(private val maxCapacity: Int = 50) {

    data class EditSnapshot(
        val text: String,
        val selectionStart: Int = 0,
        val selectionEnd: Int = 0
    )

    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()

    val canUndo: Boolean get() = undoStack.size > 1
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun initialize(initialText: String, cursor: Int = 0) {
        undoStack.clear()
        redoStack.clear()
        undoStack.addLast(EditSnapshot(text = initialText, selectionStart = cursor, selectionEnd = cursor))
    }

    /**
     * Records a new edit action. Clears redo stack and prunes oldest undo item if capacity reached.
     */
    fun push(text: String, selectionStart: Int = 0, selectionEnd: Int = 0) {
        val current = undoStack.lastOrNull()
        if (current != null && current.text == text) {
            // No substantive change, just update cursor
            return
        }

        if (undoStack.size >= maxCapacity) {
            undoStack.removeFirst()
        }

        undoStack.addLast(EditSnapshot(text = text, selectionStart = selectionStart, selectionEnd = selectionEnd))
        redoStack.clear()
    }

    /**
     * Reverts to the previous snapshot.
     */
    fun undo(): EditSnapshot? {
        if (undoStack.size <= 1) return null

        val current = undoStack.removeLast()
        redoStack.addLast(current)
        return undoStack.lastOrNull()
    }

    /**
     * Re-applies a previously undone snapshot.
     */
    fun redo(): EditSnapshot? {
        if (redoStack.isEmpty()) return null

        val next = redoStack.removeLast()
        undoStack.addLast(next)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
