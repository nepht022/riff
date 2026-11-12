package com.rodrigomachado.riff.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Biblioteca do design novo: o menu ⋮, os chips, o cartão com o recorde e o X em cada cartão fora do modo
 * Selecionar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LibraryDesignTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sounds = mutableListOf<MenuSound>()
    private val events = mutableListOf<String>()
    private val two = listOf(chart("a", title = "Música A"), chart("b", title = "Solta na raiz"))

    private fun show(state: LibraryUiState) = composeRule.setContent {
        CompositionLocalProvider(LocalMenuSound provides { sounds += it }) {
            RiffTheme {
                LibraryContent(
                    state = state,
                    onPickFolder = { events += "pick" },
                    onRescan = { events += "rescan" },
                    onReportDismissed = {},
                    onPlay = { _, _ -> },
                    onBack = {},
                    deletion = DeletionCallbacks(onStartSelecting = { events += "select" }),
                )
            }
        }
    }

    private fun openMenu() {
        composeRule.onNodeWithContentDescription("Mais opções").performClick()
    }

    /** Cada cartão diz o maior recorde do mapa (em qualquer dificuldade) ou que ainda não há; e artista · duração. */
    @Test
    fun cardShowsRecordOrNoRecordYet() {
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(
                    chart("a", title = "Com Pontos").copy(durationMs = 205_000),
                    chart("b", title = "Sem Pontos", artist = ""),
                ),
                bests = mapOf("a" to ChartBest(123_456, Difficulty.HARD)),
            ),
        )

        composeRule.onNodeWithText("Recorde 123.456 · Difícil").assertExists()
        composeRule.onNodeWithText("Banda · 3:25").assertExists()
        composeRule.onNodeWithText("Ainda sem recorde").assertExists()
        composeRule.onNodeWithText("Artista desconhecido").assertExists()
    }

    /**
     * Os chips filtram: "Guitarra separada" (a do mapa ou a da IA) e "Com recorde"; "Todas · N" volta a todas. O
     * número é o da biblioteca inteira.
     */
    @Test
    @Config(qualifiers = "pt-rBR-w411dp-h1400dp")
    fun chipsFilterSeparateGuitarAndWithRecord() {
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(
                    chart("own", title = "Própria").copy(separateGuitar = true),
                    chart("ai", title = "Da IA"),
                    chart("rec", title = "Com Pontos"),
                    chart("none", title = "Nada"),
                ),
                aiSeparated = setOf("ai"),
                bests = mapOf("rec" to ChartBest(1_000, Difficulty.EXPERT)),
            ),
        )
        fun shown(vararg titles: String) = listOf("Própria", "Da IA", "Com Pontos", "Nada").forEach {
            if (it in titles) {
                composeRule.onNodeWithText(it).assertExists()
            } else {
                composeRule.onNodeWithText(it).assertDoesNotExist()
            }
        }

        shown("Própria", "Da IA", "Com Pontos", "Nada")
        composeRule.onNodeWithText("Guitarra separada").performClick()
        shown("Própria", "Da IA")
        composeRule.onNodeWithText("Com recorde").performClick()
        shown("Com Pontos")
        composeRule.onNodeWithText("Todas · 4").performClick()
        shown("Própria", "Da IA", "Com Pontos", "Nada")
    }

    /** Um chip sem nenhum mapa diz que não há (e não confunde com o filtro de texto). */
    @Test
    fun aChipWithoutChartsSaysSo() {
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(chart("a"))))

        composeRule.onNodeWithText("Com recorde").performClick()

        composeRule.onNodeWithText("Nenhum mapa neste filtro").assertExists()
    }

    /** O ⋮ do topo abre o menu com Reimportar, Trocar pasta e Selecionar; escolher um fecha o menu. */
    @Test
    fun menuHasReimportChangeFolderAndSelect() {
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = two))

        composeRule.onNodeWithText("Reimportar").assertDoesNotExist()
        openMenu()
        composeRule.onNodeWithText("Reimportar").performClick()
        composeRule.onNodeWithText("Reimportar").assertDoesNotExist()
        openMenu()
        composeRule.onNodeWithText("Trocar pasta").performClick()
        openMenu()
        composeRule.onNodeWithText("Selecionar").performClick()

        assertEquals(listOf("rescan", "pick", "select"), events)
        assertEquals(List(6) { MenuSound.NAVIGATE }, sounds)
    }

    /** O X de apagar fica sempre à vista fora do modo Selecionar e some nele (lá, a caixa de marcar). */
    @Test
    fun theXShowsOutsideSelectingAndHidesInside() {
        var state by mutableStateOf(LibraryUiState(isLoading = false, hasFolder = true, charts = two))
        composeRule.setContent {
            RiffTheme {
                LibraryContent(
                    state = state,
                    onPickFolder = {},
                    onRescan = {},
                    onReportDismissed = {},
                    onPlay = { _, _ -> },
                    onBack = {},
                    deletion = DeletionCallbacks(
                        onDelete = { events += "delete:$it" },
                        onStartSelecting = { state = state.copy(deletion = DeletionState(selecting = true)) },
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Excluir Música A").performClick()
        openMenu()
        composeRule.onNodeWithText("Selecionar").performClick()
        composeRule.onAllNodesWithContentDescription("Excluir", substring = true).assertCountEquals(0)

        assertEquals(listOf("delete:a"), events)
    }

    /** O "+ Mapas" (ao lado do título) fica alinhado à direita com o ⋮ do topo. */
    @Test
    @Config(qualifiers = "pt-rBR-w411dp-h900dp")
    fun moreChartsLinesUpWithTheMenuOnTheRight() {
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = two))

        val more = composeRule.onNodeWithText("+ Mapas").getUnclippedBoundsInRoot()
        val menu = composeRule.onNodeWithContentDescription("Mais opções").getUnclippedBoundsInRoot()
        // O botão do ⋮ tem 40 dp no meio do toque de 48 dp: a borda dele fica até 4 dp para dentro.
        assertEquals(menu.right.value, more.right.value, 4.5f)
    }
}
