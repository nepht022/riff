package com.rodrigomachado.riff.core.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Onde a música começa de verdade no áudio de um mapa, pulando silêncio e contagem de entrada (batidas
 * curtas de baqueta antes da banda). A versão do Spotify costuma começar direto na música: a diferença vira
 * o ajuste de sincronia da faixa.
 *
 * Regra: o primeiro trecho de 50 ms em que pelo menos 80% do segundo seguinte fica a menos de 30 dB da parte
 * mais alta. Batidas soltas não sustentam o segundo; uma introdução baixa, mas contínua, sustenta.
 */
object MusicStart {
    const val WINDOW_MS = 50
    private const val RANGE_DB = 30.0
    private const val SUSTAIN_WINDOWS = 20
    private const val SUSTAIN_SHARE = 0.8
    private const val SILENCE = 1e-9

    /** [power]: potência média (amostra ao quadrado) de cada janela de [WINDOW_MS]. `null` sem música. */
    fun find(power: DoubleArray): Long? {
        val loudest = power.maxOrNull() ?: return null
        if (loudest < SILENCE) return null
        val threshold = loudest * 10.0.pow(-RANGE_DB / 10)
        val loud = BooleanArray(power.size) { power[it] >= threshold }
        val start = loud.indices.firstOrNull { i ->
            val end = minOf(power.size, i + SUSTAIN_WINDOWS)
            loud[i] && (i until end).count { loud[it] } >= SUSTAIN_SHARE * (end - i)
        } ?: return null
        return start.toLong() * WINDOW_MS
    }
}

/** Decodifica o começo do áudio de um mapa e acha onde a música começa. */
fun interface MusicStartFinder {
    /** `null` se nenhuma faixa abrir ou só houver silêncio. */
    suspend fun musicStartMs(uris: List<String>): Long?
}

class PcmMusicStartFinder(private val opener: PcmSourceOpener) : MusicStartFinder {
    override suspend fun musicStartMs(uris: List<String>): Long? = withContext(Dispatchers.IO) { find(uris) }

    private suspend fun find(uris: List<String>): Long? {
        var total = DoubleArray(0)
        for (uri in uris) {
            // Faixa que não abre ou não decodifica: conta com as outras. Sair da partida interrompe tudo.
            val power = runCatching { opener.open(uri).use { windowPower(it) } }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull() ?: continue
            if (power.size > total.size) total = total.copyOf(power.size)
            for (i in power.indices) total[i] += power[i]
        }
        return MusicStart.find(total)
    }

    /** Decodifica até 30 s; para entre um bloco e outro se a partida for embora (saiu durante o carregamento). */
    private suspend fun windowPower(source: PcmSource): DoubleArray {
        val window = source.sampleRate * MusicStart.WINDOW_MS / 1_000
        val windows = ANALYZED_MS / MusicStart.WINDOW_MS
        val buffer = FloatArray(window * source.channelCount)
        val power = DoubleArray(windows)
        var count = 0
        while (count < windows) {
            currentCoroutineContext().ensureActive()
            val frames = source.read(buffer, window)
            if (frames == 0) break
            var sum = 0.0
            for (i in 0 until frames * source.channelCount) sum += buffer[i] * buffer[i]
            power[count++] = sum / (frames * source.channelCount)
        }
        return power.copyOf(count)
    }

    private companion object {
        /** O começo basta: contagens de entrada e silêncios iniciais duram segundos. */
        const val ANALYZED_MS = 30_000
    }
}
