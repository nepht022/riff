package com.rodrigomachado.riff.feature.library

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** O filtro e os chips da Biblioteca: o que está à mostra é o "todos" do Marcar todas. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LibraryVisibleChartsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val toggled = mutableListOf<List<String>>()

    private val songs = listOf(
        chart("a", title = "Smells Like Teen Spirit", artist = "Nirvana"),
        chart("b", title = "Bohemian Rhapsody", artist = "Queen"),
        chart("c", title = "Música Brasileira", artist = "Banda"),
    )

    private fun show(state: LibraryUiState) = composeRule.setContent {
        RiffTheme {
            LibraryContent(
                state = state,
                onPickFolder = {},
                onRescan = {},
                onReportDismissed = {},
                onPlay = { _, _ -> },
                onBack = {},
                deletion = DeletionCallbacks(onToggleAll = { toggled += it }),
            )
        }
    }

    /** Com o filtro, marcar todos leva só os mapas à mostra (o mesmo conjunto do "todos marcados" da caixa). */
    @Test
    @Config(qualifiers = "pt-rBR-w411dp-h1200dp")
    fun selectAllWithAFilterSendsOnlyTheVisible() {
        val selecting = DeletionState(selecting = true)
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = songs, deletion = selecting))

        composeRule.onNodeWithTag(LibraryTestTags.FILTER).performTextReplacement("nirv")
        composeRule.onNodeWithText("Marcar todas").performClick()

        assertEquals(listOf(listOf("a")), toggled)
    }

    /** Os chips são um grupo de escolha única: o leitor de tela diz que é um entre eles. */
    @Test
    fun theChipsAreOneSelectableGroup() {
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = songs))

        composeRule.onNode(hasText("Guitarra separada") and hasParent(isSelectableGroup)).assertExists()
        composeRule.onNode(hasText("Com recorde") and hasParent(isSelectableGroup)).assertExists()
    }

    private val isSelectableGroup = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
}
