package com.rodrigomachado.riff.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Excluir mapas: o X de cada cartão, a barra da seleção embaixo e os diálogos. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LibraryDeletionTest {
    private val sounds = mutableListOf<MenuSound>()
    private val events = mutableListOf<String>()

    @get:Rule
    val composeRule = createComposeRule()

    private fun showDeletion(state: LibraryUiState) = composeRule.setContent {
        CompositionLocalProvider(LocalMenuSound provides { sounds += it }) {
            RiffTheme {
                LibraryContent(
                    state = state,
                    onPickFolder = { events += "pick" },
                    onRescan = { events += "rescan" },
                    onReportDismissed = {},
                    onPlay = { _, _ -> events += "play" },
                    onBack = {},
                    deletion = DeletionCallbacks(
                        onDelete = { events += "delete:$it" },
                        onStartSelecting = { events += "select" },
                        onToggle = { events += "toggle:$it" },
                        onCancelSelecting = { events += "cancelSelect" },
                        onToggleAll = { events += "toggleAll:${it.joinToString(",")}" },
                        onDeleteSelected = { events += "deleteSelected" },
                        onSeparateSelected = { events += "separateSelected" },
                        onConfirm = { events += "confirm" },
                        onDismiss = { events += "dismiss" },
                        onFailureShown = { events += "failureShown" },
                    ),
                )
            }
        }
    }

    private val two = listOf(chart("a", title = "Música A"), chart("b", title = "Solta na raiz"))

    /** Fora do modo Selecionar, cada mapa tem o X vermelho sempre à vista, menos o solto na raiz. */
    @Test
    fun xShowsOnEveryCardOutsideSelecting() {
        showDeletion(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(chart("a", title = "Música A"), chart("c", title = "Música C"), two[1]),
                protectedIds = setOf("b"),
            ),
        )

        composeRule.onAllNodesWithContentDescription("Excluir", substring = true).assertCountEquals(2)
        composeRule.onNodeWithContentDescription("Excluir Música A").assertExists()
        composeRule.onNodeWithContentDescription("Excluir Música C").assertExists()
        composeRule.onNodeWithContentDescription("Excluir Solta na raiz").assertDoesNotExist()
    }

    /** O X pede para excluir aquele mapa (a confirmação vem do ViewModel); tocar no cartão continua jogando. */
    @Test
    fun xAsksToDeleteThatChart() {
        showDeletion(LibraryUiState(isLoading = false, hasFolder = true, charts = two, protectedIds = setOf("b")))

        composeRule.onNodeWithContentDescription("Excluir Música A").performClick()

        assertEquals(listOf("delete:a"), events)
        assertEquals(listOf(MenuSound.NAVIGATE), sounds)
    }

    /** No modo Selecionar o X some (fica a caixa de marcar) e as ações vão para a barra fixa embaixo da lista. */
    @Test
    fun selectingHidesTheXAndShowsTheBottomBar() {
        showDeletion(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = two,
                deletion = DeletionState(selecting = true, selected = setOf("a")),
            ),
        )

        composeRule.onAllNodesWithContentDescription("Excluir", substring = true).assertCountEquals(0)
        val list = composeRule.onNodeWithTag(LibraryTestTags.LIST).getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag(LibraryTestTags.SELECTION_BAR).getUnclippedBoundsInRoot()
        assertTrue("a barra não está embaixo da lista", bar.top >= list.bottom)
        composeRule.onNodeWithTag(LibraryTestTags.SELECTION_BAR).assertIsDisplayed()
        composeRule.onNodeWithText("1 marcada").assertIsDisplayed()
        composeRule.onNodeWithText("Excluir (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Separar (0)").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelar").assertIsDisplayed()
        composeRule.onNodeWithText("Marcar todas").assertIsDisplayed()
    }

    /** A barra conta as marcadas e cada botão chama o seu; com todas marcadas, oferece desmarcar. */
    @Test
    fun bottomBarCountsAndActs() {
        showDeletion(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = two,
                deletion = DeletionState(selecting = true, selected = setOf("a", "b")),
                separableSelected = 1,
            ),
        )

        composeRule.onNodeWithText("2 marcadas").assertExists()
        composeRule.onNodeWithText("Separar (1)").performClick()
        composeRule.onNodeWithText("Excluir (2)").performClick()
        composeRule.onNodeWithText("Desmarcar todas").performClick()
        composeRule.onNodeWithText("Cancelar").performClick()

        assertEquals(listOf("separateSelected", "deleteSelected", "toggleAll:a,b", "cancelSelect"), events)
    }

    /** Sem nada marcado, "Nenhuma marcada" (no português o plural do zero seria "0 marcada"). */
    @Test
    fun withNothingMarkedTheBarButtonsAreOff() {
        showDeletion(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = two,
                deletion = DeletionState(selecting = true),
            ),
        )

        composeRule.onNodeWithText("Nenhuma marcada").assertExists()
        composeRule.onNodeWithText("Excluir (0)").assertIsNotEnabled()
        composeRule.onNodeWithText("Separar (0)").assertIsNotEnabled()
    }

    // Alta: o filtro e os chips ficam acima da seleção, e o segundo cartão precisa estar na tela.
    @Test
    @Config(qualifiers = "pt-rBR-w411dp-h1200dp")
    fun selectingShowsCheckboxesAndDeletesTheChosenOnes() {
        showDeletion(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = two,
                deletion = DeletionState(selecting = true, selected = setOf("a")),
            ),
        )

        composeRule.onNodeWithText("Solta na raiz").performClick()
        composeRule.onNodeWithText("Excluir (1)").performClick()
        composeRule.onNodeWithText("Cancelar").performClick()

        assertEquals(listOf("toggle:b", "deleteSelected", "cancelSelect"), events)
    }

    @Test
    fun theSelectionSeparatesOnlyWhatNeedsIt() {
        showDeletion(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = two,
                deletion = DeletionState(selecting = true, selected = setOf("a", "b")),
                separableSelected = 1,
            ),
        )

        composeRule.onNodeWithText("Separar (1)").performClick()

        assertEquals(listOf("separateSelected"), events)
    }

    @Test
    fun withNothingToSeparateTheButtonIsOff() {
        showDeletion(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = two,
                deletion = DeletionState(selecting = true, selected = setOf("a")),
            ),
        )

        composeRule.onNodeWithText("Separar (0)").assertIsNotEnabled()
    }

    @Test
    fun withEverythingChosenTheIconOffersToClear() {
        showDeletion(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = two,
                protectedIds = setOf("b"),
                deletion = DeletionState(selecting = true, selected = setOf("a")),
            ),
        )

        composeRule.onNodeWithText("Desmarcar todas").assertExists()
    }

    @Test
    fun theConfirmationWarnsItCannotBeUndone() {
        showDeletion(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = two,
                deletion = DeletionState(ask = DeleteAsk(listOf("a"), title = "Música A", alsoDeleted = 2)),
            ),
        )

        composeRule.onNodeWithText("Excluir \u201cMúsica A\u201d?").assertExists()
        composeRule.onNodeWithText("Não dá para desfazer", substring = true).assertExists()
        composeRule.onNodeWithText("mais 2 mapas", substring = true).assertExists()
        composeRule.onAllNodesWithText("Excluir").onLast().performClick()

        assertEquals(listOf("confirm"), events)
        assertEquals(listOf(MenuSound.CONFIRM), sounds)
    }

    @Test
    fun aFailureAndThePermissionRequestAreShown() {
        showDeletion(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = two,
                deletion = DeletionState(needsPermission = true),
            ),
        )

        composeRule.onNodeWithText("Permitir").performClick()

        assertEquals(listOf("pick"), events)
    }
}
