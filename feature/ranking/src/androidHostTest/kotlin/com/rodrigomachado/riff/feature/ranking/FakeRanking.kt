package com.rodrigomachado.riff.feature.ranking

import com.rodrigomachado.riff.core.data.ranking.ApiAnswer
import com.rodrigomachado.riff.core.data.ranking.BoardEntry
import com.rodrigomachado.riff.core.data.ranking.NameResult
import com.rodrigomachado.riff.core.data.ranking.Player
import com.rodrigomachado.riff.core.data.ranking.RankedSong
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.data.ranking.SubmitStatus
import com.rodrigomachado.riff.core.model.ScoreRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeRanking(override var isConfigured: Boolean = true) : RankingRepository {
    override val player = MutableStateFlow(Player(null, null))
    override val askedLater = MutableStateFlow(false)
    var answer: NameResult = NameResult.Ok
    val registered = mutableListOf<String>()
    var songsAnswer: ApiAnswer<List<RankedSong>> = ApiAnswer.Ok(emptyList())
    var boardAnswer: ApiAnswer<List<BoardEntry>> = ApiAnswer.Ok(emptyList())
    val songQueries = mutableListOf<String>()
    val boardQueries = mutableListOf<String>()

    override fun askLater() {
        askedLater.value = true
    }

    override suspend fun register(name: String): NameResult {
        registered += name
        if (answer == NameResult.Ok) player.value = Player(name.trim(), "k")
        return answer
    }

    override fun submitIfBest(record: ScoreRecord, title: String, artist: String) = Unit

    override fun statusFor(scoreId: Long): StateFlow<SubmitStatus?> = MutableStateFlow(null)

    override suspend fun flush() = Unit

    override suspend fun songs(query: String): ApiAnswer<List<RankedSong>> {
        songQueries += query
        return songsAnswer
    }

    override suspend fun board(chartId: String): ApiAnswer<List<BoardEntry>> {
        boardQueries += chartId
        return boardAnswer
    }
}
