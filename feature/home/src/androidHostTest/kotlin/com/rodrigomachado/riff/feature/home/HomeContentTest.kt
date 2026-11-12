package com.rodrigomachado.riff.feature.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.input.Gamepad
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.PadEvent
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class HomeContentTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val events = mutableListOf<String>()

    /**
     * [roomOn]: o Firebase da Sala configurado (o app passa o onRoom só então); [state]: as partidas do banco;
     * [pad]: o controle do PC (nulo no celular, sem a barra de teclas).
     */
    private fun show(roomOn: Boolean = false, state: HomeUiState = HomeUiState(), pad: Gamepad? = null) =
        composeRule.setContent {
            CompositionLocalProvider(LocalGamepad provides pad) {
                RiffTheme {
                    HomeContent(
                        state = state,
                        images = null,
                        onSolo = { events += "solo" },
                        onRanking = { events += "ranking" },
                        onSettings = { events += "settings" },
                        onReport = { events += "report" },
                        onContinue = { chartId, difficulty -> events += "continue $chartId $difficulty" },
                        onRoom = if (roomOn) ({ events += "room" }) else null,
                    )
                }
            }
        }

    @Test
    fun soloOpensTheLibraryAndRankingOpensTheRanking() {
        show()

        composeRule.onNodeWithTag(HomeTestTags.SOLO).performClick()
        composeRule.onNodeWithTag(HomeTestTags.RANKING).performClick()

        assertEquals(listOf("solo", "ranking"), events)
    }

    @Test
    fun theCornerIconsOpenReportAndSettings() {
        show()

        composeRule.onNodeWithContentDescription("Reportar problema").performClick()
        composeRule.onNodeWithContentDescription("Configurações").performClick()

        assertEquals(listOf("report", "settings"), events)
    }

    @Test
    fun withoutConfigTheRoomTileStaysComingSoon() {
        show(roomOn = false)

        val room = composeRule.onNodeWithTag(HomeTestTags.ROOM)
        room.assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Sala, em breve").assertExists()
        room.performClick()

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun withConfigTheRoomTileOpensTheRoom() {
        show(roomOn = true)

        val room = composeRule.onNodeWithTag(HomeTestTags.ROOM)
        room.assertIsEnabled()
        composeRule.onNodeWithText("em breve").assertDoesNotExist()
        room.performClick()

        assertEquals(listOf("room"), events)
    }

    /** Num celular baixo os quadros encolhem: o Ranking continua na tela. */
    @Test
    @Config(qualifiers = "pt-rBR-w320dp-h480dp")
    fun onAShortScreenTheRankingStillShows() {
        show()

        composeRule.onNodeWithTag(HomeTestTags.RANKING).assertIsDisplayed()
    }

    /** Deitado e com a fonte grande do Android, nada cabe: a tela rola até o Ranking. */
    @Test
    @Config(qualifiers = "pt-rBR-w780dp-h360dp-land", fontScale = 2f)
    fun inLandscapeWithALargeFontTheRankingIsReachedByScrolling() {
        show()

        composeRule.onNodeWithTag(HomeTestTags.RANKING).performScrollTo().assertIsDisplayed().performClick()

        assertEquals(listOf("ranking"), events)
    }

    /** Com o Riff em inglês (o Locale padrão do processo, como o app define), a tela inicial fica em inglês. */
    @Test
    @Config(qualifiers = "en")
    fun inEnglishTheTilesAreInEnglish() {
        val system = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            show()
            composeRule.waitForIdle()
        } finally {
            Locale.setDefault(system)
        }

        composeRule.onNodeWithText("Solo").assertExists() // pt: "Sozinho"
    }

    /** "Continuar": a última partida (capa, título, artista, dificuldade, recorde); tocar abre essa música e nível. */
    @Test
    fun continueOpensTheLastMatch() {
        show(state = HomeUiState(last = LAST))

        composeRule.onNodeWithText("Continuar").assertExists()
        composeRule.onNodeWithText("Música Um").assertExists()
        composeRule.onNodeWithText("Banda", substring = true).assertExists()
        composeRule.onNodeWithText("Recorde 12.345", substring = true).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.CONTINUE).performScrollTo().performClick()

        assertEquals(listOf("continue a HARD"), events)
    }

    /** Primeira vez (sem partidas): sem "Continuar" nem "Últimos recordes", e o resto continua no lugar. */
    @Test
    fun homeWithoutMatchesHidesContinue() {
        show()

        composeRule.onNodeWithTag(HomeTestTags.CONTINUE).assertDoesNotExist()
        composeRule.onNodeWithText("Continuar").assertDoesNotExist()
        composeRule.onNodeWithText("Últimos recordes").assertDoesNotExist()
        composeRule.onNodeWithTag(HomeTestTags.SOLO).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.ROOM).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.RANKING).assertIsDisplayed()
    }

    /** "Últimos recordes": até 3, com título e pontos. */
    @Test
    fun recentBestsShowUpToThree() {
        val bests = (1..4).map { LAST.copy(chartId = "c$it", title = "Recorde de $it", best = it * 1_000L) }
        show(state = HomeUiState(last = LAST, recentBests = bests))

        composeRule.onNodeWithText("Últimos recordes").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag(HomeTestTags.RECENT_BEST).assertCountEquals(3)
        composeRule.onNodeWithText("Recorde de 3").assertExists()
        composeRule.onNodeWithText("3.000").assertExists()
        composeRule.onNodeWithText("Recorde de 4").assertDoesNotExist()
    }

    /**
     * PC (janela larga, com o controle do PC): três quadros iguais lado a lado (Sozinho, Sala, Ranking) com a linha
     * de apoio, Reportar e Configurações com texto e a barra de teclas (com os símbolos do controle ligado).
     */
    @Test
    @Config(qualifiers = "pt-rBR-w1024dp-h768dp")
    fun pcShowsThreeTilesAndKeyHints() {
        show(roomOn = true, state = HomeUiState(last = LAST), pad = PadOn)

        val solo = composeRule.onNodeWithTag(HomeTestTags.SOLO).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val room = composeRule.onNodeWithTag(HomeTestTags.ROOM).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val ranking =
            composeRule.onNodeWithTag(HomeTestTags.RANKING).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(solo.top, room.top)
        assertEquals(solo.top, ranking.top)
        assertEquals(solo.width, ranking.width, 1f)
        assertEquals(solo.height, ranking.height, 1f)
        composeRule.onNodeWithText("Biblioteca e Encore").assertExists()
        composeRule.onNodeWithText("Até 4, celular e PC").assertExists()
        // Com texto na tela (conferido na screenshot); o leitor de tela lê a descrição, a mesma do ícone do celular.
        composeRule.onNodeWithContentDescription("Reportar problema").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Configurações").assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.KEY_HINTS).assertIsDisplayed()
        composeRule.onNodeWithText("Mover").assertExists()
        composeRule.onNodeWithText("✕").assertExists()
        // O "Continuar" é do celular.
        composeRule.onNodeWithTag(HomeTestTags.CONTINUE).assertDoesNotExist()
    }

    /** No PC, os quadros crescem com a janela: quase metade da altura, como no protótipo (340 de 720). */
    @Test
    @Config(qualifiers = "pt-rBR-w1280dp-h720dp")
    fun pcTilesFillAlmostHalfTheHeight() {
        show(roomOn = true, pad = PadOn)

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val solo = composeRule.onNodeWithTag(HomeTestTags.SOLO).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val share = solo.height / root.height
        assertTrue("os quadros ocupam ${share * 100}% da altura", share in 0.42f..0.5f)
    }

    /** Na janela mínima do PC (800×500, a barra de título fora), tudo cabe ou rola: nada cortado. */
    @Test
    @Config(qualifiers = "pt-rBR-w800dp-h460dp")
    fun pcSmallestWindowReachesEveryTile() {
        show(roomOn = true, pad = PadOn)

        listOf(HomeTestTags.SOLO, HomeTestTags.ROOM, HomeTestTags.RANKING).forEach {
            composeRule.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("Os melhores de cada música").assertIsDisplayed()
    }

    /** A escala da tela do PC: a do protótipo em 1280×720, com limites para a janela mínima e para telas 4K. */
    @Test
    fun pcScaleFollowsTheWindowWithinLimits() {
        assertEquals(1f, wideHomeScale(1280.dp, 690.dp), 0.01f)
        assertEquals(0.7f, wideHomeScale(800.dp, 460.dp), 0.001f)
        assertEquals(1.8f, wideHomeScale(3840.dp, 2100.dp), 0.001f)
        // O lado mais apertado manda.
        assertEquals(wideHomeScale(1600.dp, 600.dp), wideHomeScale(2000.dp, 600.dp), 0.001f)
    }

    /** No celular não há barra de teclas, nem deitado. */
    @Test
    @Config(qualifiers = "pt-rBR-w780dp-h360dp-land")
    fun thePhoneHasNoKeyHints() {
        show()

        composeRule.onNodeWithTag(HomeTestTags.KEY_HINTS).assertDoesNotExist()
    }

    private object PadOn : Gamepad {
        override val events = emptyFlow<PadEvent>()
        override val connected = MutableStateFlow(true)
        override val presses = emptyFlow<String>()
    }

    /** Com fonte grande, o artista longo é cortado, a dificuldade do "Continuar" nunca. */
    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h780dp", fontScale = 2f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // a medida do texto de verdade
    fun theContinueDifficultyIsNeverCutWithALargeFont() {
        val last = HomeMatch("a", "Música Um", "Uma Banda Com Um Nome Bem Comprido", Difficulty.EXPERT, best = 1)
        show(state = HomeUiState(last = last))

        composeRule.onNodeWithText("Expert", substring = true).performScrollTo()
        // A dificuldade é um texto próprio (no cartão, os textos se juntam para o leitor de tela).
        val difficulty = composeRule.onNodeWithText("Expert", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        difficulty.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        // A largura que o texto ganhou cabe a palavra inteira.
        val layout = layouts.single()
        assertTrue(
            "a dificuldade foi cortada",
            layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.size.width,
        )
    }

    private companion object {
        val LAST = HomeMatch("a", "Música Um", "Banda", Difficulty.HARD, best = 12_345)
    }
}
