package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.model.ScoreRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * O ranking ainda não existe no PC (etapa 4): desligado ([isConfigured] falso, o Resultado não mostra o cartão),
 * nada é enviado e as listas respondem como sem internet.
 */
object NoRanking : RankingRepository {
    private val noStatus: StateFlow<SubmitStatus?> = MutableStateFlow(null)

    override val isConfigured: Boolean = false
    override val player: Flow<Player> = flowOf(Player(name = null, key = null))
    override val askedLater: StateFlow<Boolean> = MutableStateFlow(false)

    override fun askLater() = Unit

    override suspend fun register(name: String): NameResult = NameResult.Offline

    override fun submitIfBest(record: ScoreRecord, title: String, artist: String) = Unit

    override fun statusFor(scoreId: Long): StateFlow<SubmitStatus?> = noStatus

    override suspend fun flush() = Unit

    override suspend fun songs(query: String): ApiAnswer<List<RankedSong>> = ApiAnswer.Offline

    override suspend fun board(chartId: String): ApiAnswer<List<BoardEntry>> = ApiAnswer.Offline

    override suspend fun linkCode(): LinkResult = LinkResult.Offline

    override suspend fun link(code: String): LinkResult = LinkResult.Offline
}
