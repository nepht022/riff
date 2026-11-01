package com.rodrigomachado.riff.core.data

import androidx.sqlite.SQLiteException
import com.rodrigomachado.riff.core.database.ChartOffsetDao
import com.rodrigomachado.riff.core.database.ChartOffsetEntity
import javax.inject.Inject

/** Limite do atraso geral (calibração), em ms para cada lado. */
const val MAX_SYNC_OFFSET_MS = 500

/**
 * Limite do ajuste por música, em ms para cada lado. Segundos, não ms: o áudio de um mapa pode ter uma
 * contagem de entrada longa.
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

    override suspend fun save(chartId: String, offsetMs: Int) = ignoringRemovedChart {
        dao.upsert(ChartOffsetEntity(chartId, offsetMs.limited()))
    }
}

internal fun Int.limited(): Int = coerceIn(-MAX_SONG_OFFSET_MS, MAX_SONG_OFFSET_MS)

/**
 * O mapa saiu da biblioteca (reimportada) com a partida em andamento: a chave estrangeira recusa o ajuste, e não há
 * mais onde guardá-lo. A partida segue com o valor na memória.
 */
internal suspend fun ignoringRemovedChart(save: suspend () -> Unit) {
    try {
        save()
    } catch (e: SQLiteException) {
        if (!e.isConstraintViolation()) throw e
        // mapa removido: nada a salvar
    }
}

/** A gravação esbarrou numa regra do banco (o mapa referenciado já não existe). */
internal expect fun SQLiteException.isConstraintViolation(): Boolean
