package com.rodrigomachado.riff.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Uma pontuação esperando ir para o ranking online (sem internet, ou sem nome de jogador ainda). [scoreId] liga à
 * partida que a gerou; nulo no envio do histórico. Sem chave estrangeira: a partida pode sair do histórico antes.
 */
@Entity(tableName = "ranking_pending")
data class RankingPendingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scoreId: Long?,
    val chartId: String,
    val difficulty: String,
    val title: String,
    val artist: String,
    val points: Long,
    val accuracy: Float,
    val combo: Int,
    val perfect: Int,
    val cleanMs: Long?,
    val createdAtEpochMs: Long,
    /** Total de notas da partida; nulo nas filas de antes da medida. */
    val notes: Int? = null,
    /** A tabela do ranking em que a pontuação conta; as filas de antes da versão 14 ficam "celular". */
    @ColumnInfo(defaultValue = RANKING_TABLE_PHONE) val rankingTable: String = RANKING_TABLE_PHONE,
)

@Dao
interface RankingPendingDao {
    @Insert
    suspend fun insert(pending: RankingPendingEntity): Long

    @Query("SELECT * FROM ranking_pending ORDER BY id")
    suspend fun all(): List<RankingPendingEntity>

    @Query("DELETE FROM ranking_pending WHERE id = :id")
    suspend fun delete(id: Long)
}
