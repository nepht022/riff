package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.data.toModel
import com.rodrigomachado.riff.core.database.ScoreDao
import com.rodrigomachado.riff.core.datastore.RiffPreferences
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** O nome e a chave deste celular (no DataStore, que entra no backup: trocar de celular mantém o nome). */
interface PlayerStore {
    val player: Flow<Player>

    suspend fun save(name: String, key: String)

    suspend fun saveKey(key: String)

    /** Tira só o nome (a chave continua): o pedido do nome aparece de novo. */
    suspend fun forgetName()
}

class DataStorePlayerStore @Inject constructor(private val preferences: RiffPreferences) : PlayerStore {
    override val player: Flow<Player> = preferences.player.map { (name, key) -> Player(name, key) }

    override suspend fun save(name: String, key: String) = preferences.setPlayer(name, key)

    override suspend fun saveKey(key: String) = preferences.setPlayerKey(key)

    override suspend fun forgetName() = preferences.forgetPlayerName()
}

/** O histórico de partidas deste celular, para saber se uma partida é a melhor e enviar os melhores antigos. */
interface ScoreHistory {
    suspend fun of(chartId: String, difficulty: Difficulty): List<ScoreRecord>

    suspend fun all(): List<ScoreRecord>
}

class RoomScoreHistory @Inject constructor(private val dao: ScoreDao) : ScoreHistory {
    override suspend fun of(chartId: String, difficulty: Difficulty) =
        dao.of(chartId, difficulty.name).mapNotNull { it.toModel() }

    override suspend fun all() = dao.all().mapNotNull { it.toModel() }
}
