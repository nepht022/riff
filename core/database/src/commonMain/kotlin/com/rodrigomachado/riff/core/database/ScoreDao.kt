package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {
    @Insert
    suspend fun insert(score: ScoreEntity): Long

    @Query("SELECT * FROM scores WHERE id = :id")
    suspend fun get(id: Long): ScoreEntity?

    @Query("SELECT * FROM scores WHERE chartId = :chartId AND difficulty = :difficulty")
    suspend fun of(chartId: String, difficulty: String): List<ScoreEntity>

    @Query("SELECT * FROM scores")
    suspend fun all(): List<ScoreEntity>

    // Empate: vale o recorde mais antigo (igualar não é "NEW BEST").
    @Query(
        "SELECT * FROM scores WHERE chartId = :chartId AND difficulty = :difficulty " +
            "ORDER BY score DESC, id ASC LIMIT 1",
    )
    suspend fun best(chartId: String, difficulty: String): ScoreEntity?

    /**
     * O recorde da mesma dificuldade gravado antes da partida [beforeId] (o "antes: N" do Resultado; empate: o mais
     * antigo, como no [best]); nulo quando ela foi a primeira.
     */
    @Query(
        "SELECT * FROM scores WHERE chartId = :chartId AND difficulty = :difficulty AND id < :beforeId " +
            "ORDER BY score DESC, id ASC LIMIT 1",
    )
    suspend fun bestBefore(chartId: String, difficulty: String, beforeId: Long): ScoreEntity?

    /** A última partida jogada (de qualquer mapa); empate no horário: a gravada por último. */
    @Query("SELECT * FROM scores ORDER BY playedAtEpochMs DESC, id DESC LIMIT 1")
    suspend fun last(): ScoreEntity?

    /**
     * O recorde de hoje de cada mapa e dificuldade (o mesmo critério do [best]), do jogado por último ao mais
     * antigo, até [limit].
     */
    @Query(
        "SELECT * FROM scores AS s WHERE s.id = (" +
            "SELECT b.id FROM scores AS b WHERE b.chartId = s.chartId AND b.difficulty = s.difficulty " +
            "ORDER BY b.score DESC, b.id ASC LIMIT 1" +
            ") ORDER BY s.playedAtEpochMs DESC, s.id DESC LIMIT :limit",
    )
    suspend fun recentBests(limit: Int): List<ScoreEntity>

    /**
     * O maior recorde de cada mapa, em qualquer dificuldade (empate: o mais antigo, como no [best]); mapa sem partidas
     * fica de fora. Acompanha o banco (a Biblioteca mostra a partida nova ao voltar a ela).
     */
    @Query(
        "SELECT * FROM scores AS s WHERE s.id = (" +
            "SELECT b.id FROM scores AS b WHERE b.chartId = s.chartId ORDER BY b.score DESC, b.id ASC LIMIT 1" +
            ")",
    )
    fun observeChartBests(): Flow<List<ScoreEntity>>
}
