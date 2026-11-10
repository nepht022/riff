package com.rodrigomachado.riff.feature.gameplay

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.PlayableChart
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/** O que os testes do ViewModel da partida usam: os fakes e a montagem do ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class GameplayViewModelTestBase {
    protected val loader = FakeChartTrackLoader()
    protected val settings = FakeSettingsRepository(Settings.DEFAULT.copy(hitVolume = 0.3f, audioOffsetMs = -40))
    protected val scores = FakeScoreRepository()
    protected val player = FakeSongPlayer()
    protected val sfx = FakeSfxPlayer()
    protected var playersCreated = 0
    protected val stemPlayer = FakeStemPlayer()
    protected var stemsCreated: Pair<String, List<String>>? = null
    protected val time = FakeTime()
    protected val chartOffsets = FakeChartOffsetRepository()

    protected val chart = Chart(
        "a", "Música", "Banda", null, null, null, "raiz/a", ChartFormat.CHART,
        setOf(
            Difficulty.EXPERT,
        ),
    )

    @Before
    open fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    open fun tearDown() = Dispatchers.resetMain()

    protected val interruptions = FakePlaybackInterruptions()
    protected var interruptionsCreated = 0
    protected val art = FakeImage()
    protected val artRequests = mutableListOf<String>()
    protected val ranking = RecordingRanking()
    protected var artFailure: Exception? = null
    protected var playingWhenArtAsked: Boolean? = null

    /** As partidas de sala pedidas à fábrica: o código e a rodada da rota. */
    protected val matchStartsAsked = mutableListOf<Pair<String, Int>>()

    // Pela subclasse do Hilt: confere também a leitura dos argumentos da rota (SavedStateHandle).
    protected fun viewModel(): GameplayViewModel = hiltViewModel(
        SavedStateHandle(mapOf("chartId" to "a", "difficulty" to "EXPERT")),
        matchStart = null,
        timeSource = time,
    )

    /** A partida da sala [code], rodada [round], com o [matchStart] dado pela fábrica e o relógio [timeSource]. */
    protected fun roomViewModel(
        matchStart: MatchStart,
        timeSource: TimeSource,
        code: String = "ABCD",
        round: Int = 3,
    ): GameplayViewModel = hiltViewModel(
        SavedStateHandle(mapOf("chartId" to "a", "difficulty" to "EXPERT", "roomCode" to code, "round" to round)),
        matchStart,
        timeSource,
    )

    private fun hiltViewModel(
        route: SavedStateHandle,
        matchStart: MatchStart?,
        timeSource: TimeSource,
    ): GameplayViewModel = HiltGameplayViewModel(
        route,
        loader,
        settings,
        scores,
        chartOffsets,
        {
            playersCreated++
            player
        },
        { guitar, backing ->
            stemsCreated = guitar to backing
            stemPlayer
        },
        { sfx },
        timeSource,
        {
            interruptionsCreated++
            interruptions
        },
        { chartId ->
            artRequests += chartId
            playingWhenArtAsked = player.isPlaying
            artFailure?.let { throw it }
            art
        },
        ranking,
        { code, round ->
            matchStartsAsked += code to round
            checkNotNull(matchStart) { "partida sozinho não pede a partida de sala" }
        },
    )

    /** Como o PC monta a partida: a tabela do ranking vem do modo daquela partida (com ou sem palheta). */
    protected fun pcViewModel(): GameplayViewModel = GameplayViewModel(
        "a",
        Difficulty.EXPERT,
        loader,
        settings,
        scores,
        chartOffsets,
        { player },
        { _, _ -> stemPlayer },
        { sfx },
        time,
        { interruptions },
        { art },
        ranking,
        rankingTable = { RankingTable.pc(it.controls.strum) },
    )

    protected fun playable(audio: String? = "raiz/a/song.ogg") =
        PlayableChart(chart, Difficulty.EXPERT, listOf(note(1_000), note(2_000)), audio?.let { ChartAudio.Single(it) })
}
