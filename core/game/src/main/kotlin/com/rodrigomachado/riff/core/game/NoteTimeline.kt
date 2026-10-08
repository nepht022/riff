package com.rodrigomachado.riff.core.game

import com.rodrigomachado.riff.core.model.Note

/** Busca binária nas notas ordenadas por tempo, para desenhar só o que está visível. */
object NoteTimeline {
    /** Índice da primeira nota com `timeMs >= [timeMs]` (ou `notes.size` se não houver). */
    fun firstAtOrAfter(notes: List<Note>, timeMs: Long): Int {
        var low = 0
        var high = notes.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (notes[mid].timeMs < timeMs) low = mid + 1 else high = mid
        }
        return low
    }

    /** Índices das notas com tempo entre [fromMs] e [toMs], inclusive. */
    fun visibleRange(notes: List<Note>, fromMs: Long, toMs: Long): IntRange =
        firstAtOrAfter(notes, fromMs) until firstAtOrAfter(notes, toMs + 1)
}
