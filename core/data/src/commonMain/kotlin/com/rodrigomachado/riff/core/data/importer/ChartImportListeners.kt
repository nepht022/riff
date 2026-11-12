package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.model.Chart
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quem quer saber do fim de cada importação (Reimportar, Trocar pasta, o pacote baixado do Encore): as capas
 * guardadas na memória, por exemplo, que podem ter mudado com a pasta.
 */
@Singleton
class ChartImportListeners @Inject constructor() {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun add(listener: () -> Unit) {
        listeners += listener
    }

    fun notifyImported() = listeners.forEach { it() }
}

/** O [importer] que avisa os [listeners] ao fim de cada importação, mesmo da que falhou no meio. */
class NotifyingChartImporter(private val importer: ChartImporter, private val listeners: ChartImportListeners) :
    ChartImporter {
    override suspend fun import(rootUri: String, onProgress: (scannedFolders: Int) -> Unit): ImportReport = try {
        importer.import(rootUri, onProgress)
    } finally {
        listeners.notifyImported()
    }

    override suspend fun importPackage(fileUri: String, name: String): Chart = try {
        importer.importPackage(fileUri, name)
    } finally {
        listeners.notifyImported()
    }
}
