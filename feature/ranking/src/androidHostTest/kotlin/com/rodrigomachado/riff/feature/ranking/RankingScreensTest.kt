package com.rodrigomachado.riff.feature.ranking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.ranking.ApiAnswer
import com.rodrigomachado.riff.core.data.ranking.Board
import com.rodrigomachado.riff.core.data.ranking.BoardEntry
import com.rodrigomachado.riff.core.data.ranking.BoardLine
import com.rodrigomachado.riff.core.data.ranking.LinkResult
import com.rodrigomachado.riff.core.data.ranking.RankedSong
import com.rodrigomachado.riff.core.data.ranking.RankingMetric
import com.rodrigomachado.riff.core.data.ranking.TableStanding
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class RankingScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ranking = FakeRanking()
    private val dispatcher = StandardTestDispatcher()
    private val board = Board(
        listOf(Difficulty.HARD, Difficulty.EXPERT),
        Difficulty.HARD,
        27,
        listOf(
            BoardLine(1, "Ana", 61_200.0, you = false),
            BoardLine(3, "Rodrigo", 48_300.0, you = true),
        ),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun boardViewModel(difficulty: String? = "HARD") = HiltRankingBoardViewModel(
        SavedStateHandle(mapOf("chartId" to "m1", "title" to "Música", "difficulty" to difficulty)),
        ranking,
    )

    private fun entry(
        name: String,
        points: Double,
        difficulty: Difficulty = Difficulty.HARD,
        you: Boolean = false,
        table: RankingTable = RankingTable.PHONE,
    ) = BoardEntry(name, you, difficulty, points, 0.9, 50.0, 70.0, 30_000.0, 85, table)

    /** A lista vem uma vez; a busca filtra no celular, na hora. */
    @Test
    fun searchingSongsFiltersWithoutAskingAgain() = runTest(dispatcher) {
        ranking.songsAnswer = ApiAnswer.Ok(
            listOf(RankedSong("m1", "Schism", "TOOL", 3, null), RankedSong("m2", "Holy Diver", "Dio", 2, null)),
        )
        val vm = RankingViewModel(ranking)
        advanceUntilIdle()

        vm.onQuery("tool")

        assertEquals(listOf(""), ranking.songQueries)
        assertEquals(listOf("Schism"), vm.state.value.shown.map { it.title })
    }

    @Test
    fun boardStartsOnTheDifficultyFromTheRoute() = runTest(dispatcher) {
        ranking.boardAnswer = ApiAnswer.Ok(listOf(entry("Ana", 1.0, Difficulty.EASY), entry("Bia", 2.0)))
        val vm = boardViewModel()
        advanceUntilIdle()

        assertEquals(Difficulty.HARD, vm.state.value.board?.difficulty)
        assertEquals(listOf("Bia"), vm.state.value.board?.lines?.map { it.name })
    }

    /** O placar vem inteiro uma vez; trocar de número, de sentido, de dificuldade ou buscar é na hora. */
    @Test
    fun filtersDoNotAskTheServerAgain() = runTest(dispatcher) {
        ranking.boardAnswer = ApiAnswer.Ok(
            listOf(entry("Ana", 300.0), entry("Bruno", 200.0, you = true), entry("Zé", 999.0, Difficulty.EXPERT)),
        )
        val vm = boardViewModel()
        advanceUntilIdle()

        vm.onToggleDirection()
        assertEquals(listOf("Bruno", "Ana"), vm.state.value.board?.lines?.map { it.name })
        vm.onQuery("ana")
        assertEquals(listOf("Ana"), vm.state.value.board?.lines?.map { it.name })
        vm.onDifficulty(Difficulty.EXPERT)
        vm.onMetric(RankingMetric.COMBO)
        advanceUntilIdle()

        assertEquals(listOf("m1"), ranking.boardQueries)
    }

    @Test
    fun theFirstLoadSaysItIsLoading() {
        composeRule.setContent {
            RiffTheme { RankingBoardContent(RankingBoardUiState(title = "Música", loading = true), BoardActions()) }
        }

        composeRule.onNodeWithText("Carregando…").assertExists()
    }

    @Test
    fun songsListShowsPlayersAndYourPlace() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            RiffTheme {
                RankingSongsContent(
                    RankingSongsUiState(songs = listOf(RankedSong("m1", "Through the Fire", "DragonForce", 27, 3))),
                    onQuery = {},
                    onSong = { events += it.chartId },
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("DragonForce · 27 jogadores com pontuação · Você em 3º").assertExists()
        composeRule.onNodeWithText("Through the Fire").performClick()
        assertEquals(listOf("m1"), events)
    }

    @Test
    fun oneSingularPlayer() {
        composeRule.setContent {
            RiffTheme {
                RankingSongsContent(
                    RankingSongsUiState(songs = listOf(RankedSong("m1", "Teen Spirit", "Nirvana", 1, 1))),
                    onQuery = {},
                    onSong = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Nirvana · 1 jogador com pontuação · Você em 1º").assertExists()
    }

    @Test
    fun boardMarksYouAndShowsTheValues() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            RiffTheme {
                RankingBoardContent(
                    RankingBoardUiState(title = "Música", board = board),
                    actions = BoardActions(onToggleDirection = { events += "sentido" }),
                )
            }
        }

        composeRule.onNodeWithText("Rodrigo (você)").assertExists()
        composeRule.onNodeWithText("48.300").assertExists()
        composeRule.onNodeWithText("3º").assertExists()
        composeRule.onNodeWithText("↓ maior").performClick()
        assertEquals(listOf("sentido"), events)
    }

    @Test
    fun valuesFollowTheMetric() {
        assertEquals("88%", formatValue(RankingMetric.ACCURACY, 0.876))
        assertEquals("0:38", formatValue(RankingMetric.CLEAN, 38_500.0))
        assertEquals("42", formatValue(RankingMetric.COMBO, 42.0))
        assertEquals("70/85", formatValue(RankingMetric.PERFECT, 70.0, outOf = 85))
        assertEquals("70", formatValue(RankingMetric.PERFECT, 70.0, outOf = null))
    }

    @Test
    fun offlineShowsTryAgain() {
        var retried = false
        composeRule.setContent {
            RiffTheme {
                RankingSongsContent(RankingSongsUiState(offline = true), {}, {}, { retried = true }, {})
            }
        }

        composeRule.onNodeWithText("Sem conexão com o ranking").assertExists()
        composeRule.onNodeWithText("Tentar de novo").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun notConfiguredExplains() = runTest(dispatcher) {
        ranking.isConfigured = false
        val vm = RankingViewModel(ranking)
        advanceUntilIdle()

        assertEquals(true, vm.state.value.notConfigured)
        assertEquals(emptyList<String>(), ranking.songQueries)
    }

    /** O placar abre na tabela que vem de fora (a do aparelho) e troca sem voltar ao script. */
    @Test
    fun theBoardOpensOnTheGivenTableAndSwitches() = runTest(dispatcher) {
        ranking.boardAnswer = ApiAnswer.Ok(
            listOf(
                entry("Ana", 300.0),
                entry("Bia", 200.0, table = RankingTable.PC_STRUM),
                entry("Caio", 100.0, table = RankingTable.PC_NO_STRUM),
            ),
        )
        val vm = RankingBoardViewModel("m1", "Música", "HARD", ranking, RankingTable.PC_STRUM)
        advanceUntilIdle()
        assertEquals(RankingTable.PC_STRUM, vm.state.value.table)
        assertEquals(listOf("Bia"), vm.state.value.board?.lines?.map { it.name })

        vm.onTable(RankingTable.PC_NO_STRUM)
        assertEquals(listOf("Caio"), vm.state.value.board?.lines?.map { it.name })
        vm.onTable(RankingTable.PHONE)
        assertEquals(listOf("Ana"), vm.state.value.board?.lines?.map { it.name })
        assertEquals(listOf("m1"), ranking.boardQueries)
    }

    @Test
    fun onThePhoneTheBoardOpensOnThePhoneTable() = runTest(dispatcher) {
        val vm = boardViewModel()
        advanceUntilIdle()

        assertEquals(RankingTable.PHONE, vm.state.value.table)
    }

    @Test
    fun theBoardShowsTheThreeTablesAndAsksToSwitch() {
        val chosen = mutableListOf<RankingTable>()
        composeRule.setContent {
            RiffTheme {
                RankingBoardContent(
                    RankingBoardUiState(title = "Música", board = board),
                    BoardActions(onTable = { chosen += it }),
                )
            }
        }

        composeRule.onNodeWithText("Celular").assertExists()
        composeRule.onNodeWithText("PC sem palheta").assertExists()
        composeRule.onNodeWithText("PC com palheta").performClick()
        assertEquals(listOf(RankingTable.PC_STRUM), chosen)
    }

    /** A lista mostra as músicas de qualquer tabela, com os números da tabela escolhida. */
    @Test
    fun theSongsFollowTheChosenTable() {
        val song = RankedSong(
            "m1",
            "Schism",
            "TOOL",
            5,
            1,
            mapOf(RankingTable.PHONE to TableStanding(3, null), RankingTable.PC_STRUM to TableStanding(2, 1)),
        )
        val chosen = mutableListOf<RankingTable>()
        composeRule.setContent {
            RiffTheme {
                RankingSongsContent(
                    RankingSongsUiState(songs = listOf(song), table = RankingTable.PC_STRUM),
                    onQuery = {},
                    onSong = {},
                    onRetry = {},
                    onBack = {},
                    onTable = { chosen += it },
                )
            }
        }

        composeRule.onNodeWithText("TOOL · 2 jogadores com pontuação · Você em 1º").assertExists()
        composeRule.onNodeWithText("Celular").performClick()
        assertEquals(listOf(RankingTable.PHONE), chosen)
    }

    @Test
    fun aSongWithNobodyInTheTableSaysSo() {
        val song = RankedSong("m1", "Schism", "TOOL", 3, null, mapOf(RankingTable.PHONE to TableStanding(3, null)))
        composeRule.setContent {
            RiffTheme {
                RankingSongsContent(
                    RankingSongsUiState(songs = listOf(song), table = RankingTable.PC_NO_STRUM),
                    onQuery = {},
                    onSong = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("TOOL · ninguém nesta tabela").assertExists()
    }

    @Test
    fun theSongsListOpensOnTheGivenTable() = runTest(dispatcher) {
        val vm = RankingViewModel(ranking, RankingTable.PC_NO_STRUM)
        advanceUntilIdle()
        assertEquals(RankingTable.PC_NO_STRUM, vm.state.value.table)

        vm.onTable(RankingTable.PHONE)

        assertEquals(RankingTable.PHONE, vm.state.value.table)
        assertEquals(listOf(""), ranking.songQueries)
    }

    /** "Usar este nome em outro aparelho": o código e a contagem pelo validoPor do script. */
    @Test
    fun theLinkCodeCountsDown() = runTest(dispatcher) {
        val vm = PlayerNameViewModel(ranking)
        vm.showLinkCode()
        runCurrent()
        assertEquals(NameStep.SHOW_CODE, vm.state.value.step)
        assertEquals("ABC234", vm.state.value.linkCode)
        assertEquals(600, vm.state.value.secondsLeft)

        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(539, vm.state.value.secondsLeft)

        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(0, vm.state.value.secondsLeft)
    }

    @Test
    fun theCodeIsShownBigWithTheTimeLeft() {
        composeRule.setContent {
            RiffTheme {
                PlayerNameDialog(
                    PlayerNameUiState(
                        name = "Rodrigo",
                        step = NameStep.SHOW_CODE,
                        linkCode = "ABC234",
                        secondsLeft = 545,
                    ),
                    onName = {},
                    onSave = {},
                    onLater = null,
                )
            }
        }

        composeRule.onNodeWithText("ABC234").assertExists()
        composeRule.onNodeWithText("Vale por 9:05").assertExists()
    }

    @Test
    fun anExpiredCodeOffersAnother() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            RiffTheme {
                PlayerNameDialog(
                    PlayerNameUiState(
                        name = "Rodrigo",
                        step = NameStep.SHOW_CODE,
                        linkCode = "ABC234",
                        secondsLeft = 0,
                    ),
                    onName = {},
                    onSave = {},
                    onLater = null,
                    link = LinkActions(onShowCode = { events += "outro" }),
                )
            }
        }

        composeRule.onNodeWithText("O código venceu").assertExists()
        composeRule.onNodeWithText("Pedir outro").performClick()
        assertEquals(listOf("outro"), events)
    }

    @Test
    fun theNameDialogOffersBothWaysToLink() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            RiffTheme {
                PlayerNameDialog(
                    PlayerNameUiState(name = "Rodrigo", hasName = true),
                    onName = {},
                    onSave = {},
                    onLater = null,
                    link = LinkActions(onShowCode = { events += "mostrar" }, onEnterCode = { events += "digitar" }),
                )
            }
        }

        composeRule.onNodeWithText("Usar este nome em outro aparelho").performClick()
        composeRule.onNodeWithText("Já tenho um nome em outro aparelho").performClick()
        assertEquals(listOf("mostrar", "digitar"), events)
    }

    /** Sem nome ainda, não há o que levar para outro aparelho. */
    @Test
    fun withoutANameOnlyLinkingIsOffered() {
        composeRule.setContent {
            RiffTheme {
                PlayerNameDialog(PlayerNameUiState(), onName = {}, onSave = {}, onLater = {})
            }
        }

        composeRule.onNodeWithText("Usar este nome em outro aparelho").assertDoesNotExist()
        composeRule.onNodeWithText("Já tenho um nome em outro aparelho").assertExists()
    }

    @Test
    fun linkingWithTheCodeCloses() = runTest(dispatcher) {
        val vm = PlayerNameViewModel(ranking)
        vm.enterLinkCode()
        vm.onCode("abc234")
        vm.link()
        runCurrent()

        assertEquals(listOf("abc234"), ranking.linkedCodes)
        assertTrue(vm.state.value.done)
    }

    @Test
    fun theCodeFieldTakesLowercaseAndSends() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            RiffTheme {
                PlayerNameDialog(
                    PlayerNameUiState(step = NameStep.ENTER_CODE),
                    onName = {},
                    onSave = {},
                    onLater = {},
                    link = LinkActions(onCode = { events += it }, onLink = { events += "vincular" }),
                )
            }
        }

        composeRule.onNodeWithText("Código").performTextInput("abc234")
        composeRule.onNodeWithText("Vincular").performClick()
        assertEquals(listOf("abc234", "vincular"), events)
    }

    private val linkErrors = mapOf(
        LinkResult.InvalidCode to "Código errado ou vencido",
        LinkResult.Limit to "Muitas tentativas agora: tente de novo daqui a uma hora",
        LinkResult.Outdated to "O ranking precisa ser atualizado. Tente de novo mais tarde.",
        LinkResult.Offline to "Sem internet: tente de novo depois",
    )

    @Test
    fun eachLinkErrorKeepsTheDialog() = runTest(dispatcher) {
        for (result in linkErrors.keys) {
            ranking.linkAnswer = result
            val vm = PlayerNameViewModel(ranking)
            vm.enterLinkCode()
            vm.onCode("ABC234")
            vm.link()
            runCurrent()
            assertEquals(result, vm.state.value.linkError)
            assertEquals(false, vm.state.value.done)
        }
    }

    @Test
    fun eachLinkErrorIsExplained() {
        var state by mutableStateOf(PlayerNameUiState(step = NameStep.ENTER_CODE))
        composeRule.setContent {
            RiffTheme { PlayerNameDialog(state, onName = {}, onSave = {}, onLater = {}) }
        }
        for ((result, text) in linkErrors) {
            state = PlayerNameUiState(step = NameStep.ENTER_CODE, code = "ABC234", linkError = result)
            composeRule.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun theOldScriptSaysTheRankingMustBeUpdatedWhenAskingForACode() = runTest(dispatcher) {
        ranking.codeAnswer = LinkResult.Outdated
        val vm = PlayerNameViewModel(ranking)

        vm.showLinkCode()
        runCurrent()

        assertEquals(LinkResult.Outdated, vm.state.value.linkError)
        assertEquals(null, vm.state.value.linkCode)
    }
}
