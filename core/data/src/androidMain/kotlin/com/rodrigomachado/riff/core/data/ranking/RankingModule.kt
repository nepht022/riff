package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.data.RankingConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RankingModule {
    @Binds
    abstract fun rankingRepository(impl: AppsScriptRankingRepository): RankingRepository

    @Binds
    abstract fun playerStore(impl: DataStorePlayerStore): PlayerStore

    @Binds
    abstract fun scoreHistory(impl: RoomScoreHistory): ScoreHistory

    companion object {
        @Provides
        fun rankingApi(): RankingApi = RankingApi(RankingConfig.RANKING_URL, UrlConnectionRankingTransport)

        @Provides
        @Singleton
        @RankingScope
        fun rankingScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
