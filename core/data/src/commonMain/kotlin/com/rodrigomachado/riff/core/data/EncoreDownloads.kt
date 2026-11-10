package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.database.EncoreChartDao
import com.rodrigomachado.riff.core.database.EncoreChartEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Quais pacotes do Encore já estão na biblioteca (para a pesquisa mostrar "Já na biblioteca"). */
interface EncoreDownloads {
    val md5s: Flow<Set<String>>

    suspend fun remember(chartId: String, md5: String)

    /** O mapa da biblioteca baixado deste pacote (para o Jogar da pesquisa). */
    suspend fun chartIdOf(md5: String): String?

    /** O pacote do Encore de onde veio o mapa [chartId] (null = não veio do Encore): a sala o leva aos outros. */
    suspend fun md5Of(chartId: String): String?
}

class RoomEncoreDownloads @Inject constructor(private val dao: EncoreChartDao) : EncoreDownloads {
    override val md5s: Flow<Set<String>> = dao.observeMd5s().map { it.toSet() }

    override suspend fun remember(chartId: String, md5: String) = dao.upsert(EncoreChartEntity(chartId, md5))

    override suspend fun chartIdOf(md5: String): String? = dao.chartIdOf(md5)

    override suspend fun md5Of(chartId: String): String? = dao.md5Of(chartId)
}
