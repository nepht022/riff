package com.rodrigomachado.riff.core.data.queue

import android.util.Log
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.library.ChartDeleter
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Apaga os mapas da fila que ninguém jogou (spec §7). Um mapa da fila só vale para a música atual ou para as
 * próximas: fora disso, ou a música passou sem ser jogada, ou saiu da fila. Jogados, baixados na mão ou baixados
 * com "apagar" desligada não têm registro da fila e nunca chegam aqui.
 */
class QueueCleaner @Inject constructor(
    private val downloads: QueueDownloads,
    private val deleter: ChartDeleter,
    private val settings: SettingsRepository,
    private val matches: MatchActivity,
) {
    /** Depois de cada leitura da fila; devolve as músicas cujos mapas foram apagados (se voltarem, baixam de novo). */
    suspend fun keepOnly(currentTrackUri: String?, window: List<String>): List<String> {
        val keep = window.toSet() + listOfNotNull(currentTrackUri)
        return delete { it.trackUri !in keep }
    }

    /** Na abertura do app: com a fila desligada, as sobras saem já; ligada, a primeira leitura decide. */
    suspend fun atStartup() {
        if (!settings.settings.first().queuePrefetch) delete { true }
    }

    private suspend fun delete(which: (QueueDownload) -> Boolean): List<String> {
        // Com partida aberta, nada sai (nem o mapa sendo carregado, nem disco ocupado durante a pista).
        if (!settings.settings.first().queueDeleteUnplayed || matches.inMatch.value) return emptyList()
        val gone = downloads.unplayed().filter { which(it) && !matches.isOpen(it.chartId) }
        if (gone.isEmpty()) return emptyList()
        val result = deleter.delete(gone.map { it.chartId })
        // Só sai do registro o que foi apagado de fato (no banco, a linha some junto com o mapa).
        Log.i(TAG, "mapas da fila apagados: ${result.deleted} (falhas: ${result.failed})")
        return gone.map { it.trackUri }
    }

    private companion object {
        const val TAG = "RiffQueue"
    }
}
