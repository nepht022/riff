package com.rodrigomachado.riff.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportFailureKind
import com.rodrigomachado.riff.core.data.importer.ImportReport
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LibraryContentTest {
    private val sounds = mutableListOf<MenuSound>()

    @get:Rule
    val composeRule = createComposeRule()

    private var picks = 0
    private var rescans = 0
    private var dismissals = 0
    private var played: Pair<String, Difficulty>? = null
    private var searched = 0

    private fun show(state: LibraryUiState) = composeRule.setContent {
        CompositionLocalProvider(LocalMenuSound provides { sounds += it }) {
            RiffTheme {
                LibraryContent(
                    state = state,
                    onPickFolder = { picks++ },
                    onRescan = { rescans++ },
                    onReportDismissed = { dismissals++ },
                    onPlay = { id, difficulty -> played = id to difficulty },
                    onBack = {},
                    onSearch = { searched++ },
                )
            }
        }
    }

    @Test
    fun withoutFolderOffersToChooseOne() {
        show(LibraryUiState(isLoading = false, hasFolder = false))

        composeRule.onNodeWithText("Nenhuma pasta escolhida").assertExists()
        composeRule.onNodeWithText("Escolher pasta").performClick()

        assertEquals(1, picks)
    }

    @Test
    fun listShowsChartsWithUnknownArtistFallbackAndHighestDifficulty() {
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(
                    chart(
                        "a",
                        title = "Sem Artista",
                        artist = "",
                        difficulties = setOf(Difficulty.EASY, Difficulty.HARD),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Sem Artista").assertExists()
        composeRule.onNodeWithText("Artista desconhecido").assertExists()
        composeRule.onNodeWithText("DIFÍCIL").assertExists()
        composeRule.onNodeWithText("Reimportar").performClick()
        assertEquals(1, rescans)
    }

    @Test
    fun reportShowsCountsFailuresAndCanBeDismissed() {
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(chart("a")),
                report = ImportReport(
                    importedCount = 1,
                    failures = listOf(
                        ImportFailure("Quebrada", ImportFailureKind.INVALID_CHART, "linha 3: nota inválida"),
                        ImportFailure("Cópia", ImportFailureKind.DUPLICATE, "Original"),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("1 mapa importado").assertExists()
        composeRule.onNodeWithText("2 pastas com erro").assertExists()
        composeRule.onNodeWithText("Quebrada: mapa inválido (linha 3: nota inválida)").assertExists()
        composeRule.onNodeWithText("Cópia: mapa repetido, igual ao de Original").assertExists()
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(1, dismissals)
    }

    @Test
    fun lostAccessAsksToChooseTheFolderAgain() {
        show(LibraryUiState(isLoading = false, hasFolder = true, folderAccessLost = true))

        composeRule.onNodeWithText("Perdi o acesso à pasta. Escolha-a de novo.").assertExists()
        composeRule.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, picks)
    }

    @Test
    fun importingShowsHowManyFoldersWereRead() {
        show(LibraryUiState(isLoading = false, hasFolder = true, isImporting = true, scannedFolders = 12))

        composeRule.onNodeWithText("Lendo pastas… 12").assertExists()
    }

    @Test
    fun failedImportOffersToTryAgain() {
        show(LibraryUiState(isLoading = false, hasFolder = true, importFailed = true))

        composeRule.onNodeWithText("Não foi possível importar a pasta.").assertExists()
        composeRule.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, rescans)
    }

    @Test
    fun tappingAChartOffersItsDifficultiesAndPlaysTheChosenOne() {
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(chart("a", title = "Música", difficulties = setOf(Difficulty.EASY, Difficulty.EXPERT))),
            ),
        )

        composeRule.onNodeWithText("Música").performClick()
        composeRule.onNodeWithText("Escolha a dificuldade").assertExists()
        composeRule.onNodeWithText("Médio").assertDoesNotExist()
        composeRule.onNodeWithText("Expert").performClick()

        assertEquals("a" to Difficulty.EXPERT, played)
    }

    @Test
    fun inMatchModeTappingAChartPicksItWithoutAskingTheDifficulty() {
        var picked: String? = null
        composeRule.setContent {
            RiffTheme {
                LibraryContent(
                    state = LibraryUiState(
                        isLoading = false,
                        hasFolder = true,
                        charts = listOf(chart("a", title = "Música")),
                    ),
                    onPickFolder = {},
                    onRescan = {},
                    onReportDismissed = {},
                    onPlay = { _, _ -> error("não deveria jogar") },
                    onBack = {},
                    matchingTitle = "Bohemian Rhapsody",
                    onChartMatched = { picked = it },
                )
            }
        }

        composeRule.onNodeWithText("Qual mapa é esta música?").assertExists()
        composeRule.onNodeWithText("Bohemian Rhapsody").assertExists()
        composeRule.onNodeWithText("Música").performClick()

        composeRule.onNodeWithText("Escolha a dificuldade").assertDoesNotExist()
        assertEquals("a", picked)
    }

    @Test
    fun backLeavesTheLibrary() {
        var back = false
        composeRule.setContent {
            RiffTheme {
                LibraryContent(
                    state = LibraryUiState(isLoading = false, hasFolder = true),
                    onPickFolder = {},
                    onRescan = {},
                    onReportDismissed = {},
                    onPlay = { _, _ -> },
                    onBack = { back = true },
                )
            }
        }

        composeRule.onNodeWithText("Voltar").performClick()

        assertEquals(true, back)
    }

    @Test
    fun theChartListHasATagForTheAutomatedJourneys() {
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(chart("a", title = "Música"))))

        composeRule.onNodeWithTag(LibraryTestTags.LIST).assertExists()
    }

    @Test
    fun theDifficultyDialogSurvivesRotatingTheScreen() {
        val restoration = StateRestorationTester(composeRule)
        val state = LibraryUiState(
            isLoading = false,
            hasFolder = true,
            charts = listOf(chart("a", title = "Música", difficulties = setOf(Difficulty.EXPERT))),
        )
        restoration.setContent {
            RiffTheme {
                LibraryContent(
                    state = state,
                    onPickFolder = {},
                    onRescan = {},
                    onReportDismissed = {},
                    onPlay = { id, difficulty -> played = id to difficulty },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Música").performClick()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Escolha a dificuldade").assertExists()
        composeRule.onNodeWithText("Expert").performClick()
        assertEquals("a" to Difficulty.EXPERT, played)
    }

    @Test
    fun aLongFailureListShowsTheFirstTenAndHowManyMore() {
        val failures = List(13) { ImportFailure("Pasta $it", ImportFailureKind.UNREADABLE) }
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                report = ImportReport(importedCount = 0, failures = failures),
            ),
        )

        composeRule.onNodeWithText("Pasta 9: não deu para ler os arquivos").assertExists()
        composeRule.onNodeWithText("Pasta 10: não deu para ler os arquivos").assertDoesNotExist()
        composeRule.onNodeWithText("e mais 3 pastas").assertExists()
    }

    @Test
    fun realErrorsComeBeforeDuplicatesSoTheyAreNotHidden() {
        val duplicates = List(12) { ImportFailure("Cópia $it", ImportFailureKind.DUPLICATE, "Original $it") }
        val broken = ImportFailure("Quebrada", ImportFailureKind.INVALID_CHART, "linha 3")
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                report = ImportReport(importedCount = 12, failures = duplicates + broken),
            ),
        )

        composeRule.onNodeWithText("Quebrada: mapa inválido (linha 3)").assertExists()
    }

    @Test
    fun libraryButtonsNavigateAndChoosingTheDifficultyConfirms() {
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(chart("a", title = "Música"))))

        composeRule.onNodeWithText("Reimportar").performClick()
        composeRule.onNodeWithText("Trocar pasta").performClick()
        composeRule.onNodeWithText("Música").performClick()
        composeRule.onNodeWithText("Expert").performClick()

        assertEquals(listOf(MenuSound.NAVIGATE, MenuSound.NAVIGATE, MenuSound.NAVIGATE, MenuSound.CONFIRM), sounds)
    }

    @Test
    fun theErrorButtonsNavigateLikeChoosingAFolderAndRescanning() {
        show(LibraryUiState(isLoading = false, hasFolder = true, folderAccessLost = true))
        composeRule.onNodeWithText("Tentar de novo").performClick()

        assertEquals(listOf(MenuSound.NAVIGATE), sounds)
    }

    @Test
    fun searchOpensTheEncoreSearchWithANavigateSound() {
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(chart("a"))))

        composeRule.onNodeWithText("Pesquisar").performClick()

        assertEquals(1, searched)
        assertEquals(listOf(MenuSound.NAVIGATE), sounds)
    }

    private val events = mutableListOf<String>()

    private fun showWithPrompt(state: LibraryUiState) = composeRule.setContent {
        CompositionLocalProvider(LocalMenuSound provides { sounds += it }) {
            RiffTheme {
                LibraryContent(
                    state = state,
                    onPickFolder = {},
                    onRescan = {},
                    onReportDismissed = {},
                    onPlay = { _, _ -> events += "play" },
                    onBack = {},
                    onDifficultyChosen = { c, d -> events += "difficulty:${c.id}:$d" },
                    onSeparate = { events += "separate" },
                    onPlayWithoutSeparating = { events += "playWithout" },
                    onPromptDismissed = { events += "back" },
                )
            }
        }
    }

    @Test
    fun theAiBadgeShowsOnSeparatedSongs() {
        val song = chart("a", title = "Música")
        showWithPrompt(
            LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(song), aiSeparated = setOf("a")),
        )

        composeRule.onNodeWithText("GUITARRA SEPARADA IA").assertExists()
    }

    @Test
    fun thePromptOffersToSeparateOrPlay() {
        val song = chart("a", title = "Música")
        showWithPrompt(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(song),
                prompt = SeparationAsk("a", "Música", Difficulty.EXPERT, lowMemory = true),
            ),
        )

        composeRule.onNodeWithText("Separar a guitarra com IA?").assertExists()
        composeRule.onNodeWithText("só apareça ao acertá-las", substring = true).assertExists()
        composeRule.onNodeWithText("Pode não funcionar neste celular.", substring = true).assertExists()
        composeRule.onNodeWithText("Separar").performClick()
        composeRule.onNodeWithText("Sem separar").performClick()
        composeRule.onAllNodesWithText("Voltar").onLast().performClick() // o do diálogo (o outro é o da tela)

        assertEquals(listOf("separate", "playWithout", "back"), events)
        assertEquals(listOf(MenuSound.CONFIRM, MenuSound.CONFIRM, MenuSound.BACK), sounds)
    }

    @Test
    fun aBusyPromptOnlyOffersToPlay() {
        showWithPrompt(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(chart("a", title = "Música")),
                prompt = SeparationAsk("a", "Música", Difficulty.EXPERT, lowMemory = false, busy = true),
            ),
        )

        composeRule.onNodeWithText("Já tem uma separação em andamento").assertExists()
        composeRule.onNodeWithText("Separar").assertDoesNotExist()
    }

    @Test
    fun choosingADifficultyGoesThroughTheViewModel() {
        showWithPrompt(
            LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(chart("a", title = "Música"))),
        )

        composeRule.onNodeWithText("Música").performClick()
        composeRule.onNodeWithText("Expert").performClick()

        assertEquals(listOf("difficulty:a:EXPERT"), events)
    }

    @Test
    fun aChartThatCameWithItsOwnGuitarShowsTheMark() {
        showWithPrompt(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(chart("a", title = "Música").copy(separateGuitar = true)),
            ),
        )

        composeRule.onNodeWithText("GUITARRA SEPARADA").assertExists()
        composeRule.onNodeWithText("GUITARRA SEPARADA IA").assertDoesNotExist()
    }

    private fun showDeletion(state: LibraryUiState) = composeRule.setContent {
        CompositionLocalProvider(LocalMenuSound provides { sounds += it }) {
            RiffTheme {
                LibraryContent(
                    state = state,
                    onPickFolder = { events += "pick" },
                    onRescan = {},
                    onReportDismissed = {},
                    onPlay = { _, _ -> events += "play" },
                    onBack = {},
                    deletion = DeletionCallbacks(
                        onDelete = { events += "delete:$it" },
                        onStartSelecting = { events += "select" },
                        onToggle = { events += "toggle:$it" },
                        onCancelSelecting = { events += "cancelSelect" },
                        onDeleteSelected = { events += "deleteSelected" },
                        onConfirm = { events += "confirm" },
                        onDismiss = { events += "dismiss" },
                        onFailureShown = { events += "failureShown" },
                    ),
                )
            }
        }
    }

    private val two = listOf(chart("a", title = "Música A"), chart("b", title = "Solta na raiz"))

    @Test
    fun eachChartHasARedXExceptTheOneLooseInTheRoot() {
        showDeletion(LibraryUiState(isLoading = false, hasFolder = true, charts = two, protectedIds = setOf("b")))

        composeRule.onAllNodesWithContentDescription("Excluir", substring = true).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Excluir Música A").performClick()

        assertEquals(listOf("delete:a"), events)
        assertEquals(listOf(MenuSound.NAVIGATE), sounds)
    }

    @Test
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
    fun theSelectButtonSitsWithReimport() {
        showDeletion(LibraryUiState(isLoading = false, hasFolder = true, charts = two))

        composeRule.onNodeWithText("Selecionar").performClick()

        assertEquals(listOf("select"), events)
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

    @Test
    @Config(qualifiers = "pt-rBR-w411dp-h900dp")
    fun selectLinesUpWithSearchOnTheRight() {
        showDeletion(LibraryUiState(isLoading = false, hasFolder = true, charts = two))

        val search = composeRule.onNodeWithText("Pesquisar", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val select = composeRule.onNodeWithText("Selecionar", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(search.right.value, select.right.value, 1.5f) // arredondamento da medida do texto
    }
}
