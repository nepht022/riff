package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.database.ChartOffsetDao
import com.rodrigomachado.riff.core.database.ChartOffsetEntity
import javax.inject.Inject

/** Limite do ajuste fino de sincronia (e do atraso geral), em ms para cada lado. */
const val MAX_SYNC_OFFSET_MS = 500

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

internal fun Int.limited(): Int = coerceIn(-MAX_SYNC_OFFSET_MS, MAX_SYNC_OFFSET_MS)
