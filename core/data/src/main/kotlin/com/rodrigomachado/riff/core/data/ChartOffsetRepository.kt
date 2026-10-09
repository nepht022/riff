package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.database.ChartOffsetDao
import com.rodrigomachado.riff.core.database.ChartOffsetEntity
import javax.inject.Inject

/** Limite do atraso geral (calibração), em ms para cada lado. */
const val MAX_SYNC_OFFSET_MS = 500

/**
 * Limite do ajuste por música, em ms para cada lado. Segundos, não ms: o áudio de um mapa pode ter uma
 * contagem de entrada que a versão do Spotify não tem.
 */
const val MAX_SONG_OFFSET_MS = 10_000

/** Ajuste fino de sincronia por mapa da biblioteca. */
interface ChartOffsetRepository {
    /** 0 quando o mapa nunca foi ajustado. */
    suspend fun get(chartId: String): Int

    suspend fun save(chartId: String, offsetMs: Int)
}

class RoomChartOffsetRepository @Inject constructor(private val dao: ChartOffsetDao) : ChartOffsetRepository {
    override suspend fun get(chartId: String): Int = dao.get(chartId)?.limited() ?: 0

    override suspend fun save(chartId: String, offsetMs: Int) = dao.upsert(
        ChartOffsetEntity(chartId, offsetMs.limited()),
    )
}

internal fun Int.limited(): Int = coerceIn(-MAX_SONG_OFFSET_MS, MAX_SONG_OFFSET_MS)
