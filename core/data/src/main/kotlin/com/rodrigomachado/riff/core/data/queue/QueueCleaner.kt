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
) {
    /** Depois de cada leitura da fila. */
    suspend fun keepOnly(currentTrackUri: String?, window: List<String>) {
        val keep = window.toSet() + listOfNotNull(currentTrackUri)
        delete { it.trackUri !in keep }
    }

    /** Na abertura do app: com a fila desligada, as sobras saem já; ligada, a primeira leitura decide. */
    suspend fun atStartup() {
        if (!settings.settings.first().queuePrefetch) delete { true }
    }

    private suspend fun delete(which: (QueueDownload) -> Boolean) {
        if (!settings.settings.first().queueDeleteUnplayed) return
        val ids = downloads.unplayed().filter(which).map { it.chartId }
        if (ids.isEmpty()) return
        val result = deleter.delete(ids)
        // Só sai do registro o que foi apagado de fato (no banco, a linha some junto com o mapa).
        Log.i(TAG, "mapas da fila apagados: ${result.deleted} (falhas: ${result.failed})")
    }

    private companion object {
        const val TAG = "RiffQueue"
    }
}
