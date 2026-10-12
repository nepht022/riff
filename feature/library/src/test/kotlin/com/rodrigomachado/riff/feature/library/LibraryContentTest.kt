package com.rodrigomachado.riff.feature.library

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportFailureKind
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Difficulty
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
    @get:Rule
    val composeRule = createComposeRule()

    private var picks = 0
    private var rescans = 0
    private var dismissals = 0
    private var played: Pair<String, Difficulty>? = null

    private fun show(state: LibraryUiState) = composeRule.setContent {
        RiffTheme {
            LibraryContent(
                state = state,
                onPickFolder = { picks++ },
                onRescan = { rescans++ },
                onReportDismissed = { dismissals++ },
                onPlay = { id, difficulty -> played = id to difficulty },
                onBack = {},
            )
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
}
