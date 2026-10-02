package com.sagoma.planimetria.editor

/**
 * Cronologia annulla/ripeti (§8). Salva solo stati di dati immutabili: nessun riferimento
 * a elementi grafici entra nella cronologia.
 */
class History<T>(private val limit: Int = 200) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()

    /** Da chiamare con lo stato *prima* di ogni modifica. */
    fun record(state: T) {
        past.addLast(state)
        if (past.size > limit) past.removeFirst()
        future.clear()
    }

    /** Annulla l'ultima registrazione se la modifica si è rivelata nulla (es. trascinamento senza spostamento). */
    fun discardLast() {
        past.removeLastOrNull()
    }

    fun undo(current: T): T? {
        val prev = past.removeLastOrNull() ?: return null
        future.addLast(current)
        return prev
    }

    fun redo(current: T): T? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(current)
        return next
    }
}
