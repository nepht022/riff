package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.Chart
import kotlinx.coroutines.flow.Flow

interface ChartRepository {
    /** Mapas da biblioteca, ordenados por artista e título. */
    fun observeCharts(): Flow<List<Chart>>

    suspend fun getChart(id: String): Chart?

    /** Grava (ou atualiza) um mapa encontrado na importação [scanId]. */
    suspend fun saveScanned(chart: Chart, scanId: Long)

    /** Tira os mapas da biblioteca (o recorde, os ajustes e a separação saem junto, em cascata). */
    suspend fun remove(ids: Collection<String>)

    /** Apaga os mapas que não apareceram na importação [scanId] (pastas que sumiram). */
    suspend fun removeNotSeenIn(scanId: Long)

    /**
     * Marca como vistos na importação [scanId] os mapas da pasta [folderUri] e das subpastas.
     * Usado quando a pasta não pôde ser lida, para a limpeza não apagar o que já estava importado.
     */
    suspend fun keepFolder(folderUri: String, scanId: Long)
}
