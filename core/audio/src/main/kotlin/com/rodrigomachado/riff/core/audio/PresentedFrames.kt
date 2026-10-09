package com.rodrigomachado.riff.core.audio

/**
 * Quantos quadros já saíram no alto-falante, a partir dos timestamps da saída (`AudioTrack.getTimestamp`).
 * Depois de começar ou continuar, só vale um timestamp tirado depois disso: o anterior é de antes da pausa e,
 * extrapolado, pularia para a cabeça de leitura (adiantada pela latência de saída). Até o som voltar, a posição
 * fica onde parou e [isPresenting] é falso. E nunca anda mais do que o tempo desde que começou: logo depois de
 * continuar, a primeira leitura às vezes vem centenas de ms adiantada.
 */
internal class PresentedFrames(private val sampleRate: Int) {
    var frames = 0L
        private set

    var isPresenting = false
        private set

    private var startedAtNs = 0L
    private var framesAtStart = 0L

    fun started(nowNs: Long) {
        startedAtNs = nowNs
        framesAtStart = frames
        isPresenting = false
    }

    fun paused() {
        isPresenting = false
    }

    fun update(head: Long, stampFrame: Long?, stampNs: Long?, nowNs: Long): Long {
        if (stampFrame != null && stampNs != null && stampNs >= startedAtNs) {
            val sinceStart = framesAtStart + (nowNs - startedAtNs) * sampleRate / NANOS_PER_SECOND
            val extrapolated = stampFrame + (nowNs - stampNs) * sampleRate / NANOS_PER_SECOND
            frames = extrapolated.coerceIn(0, minOf(head, sinceStart))
            isPresenting = true
        } else if (nowNs - startedAtNs >= NO_TIMESTAMP_FALLBACK_NS) {
            // Saída que nunca dá timestamp: a cabeça de leitura é o melhor que há.
            frames = head
            isPresenting = true
        }
        return frames
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NO_TIMESTAMP_FALLBACK_NS = 1_000_000_000L
    }
}
