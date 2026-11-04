package com.rodrigomachado.riff.feature.ranking

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.data.ranking.RankedSong
import com.rodrigomachado.riff.core.data.ranking.TableStanding
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * As telas do ranking no PC só com o teclado: ↓ sai do campo de busca (o de uma linha prenderia o foco), ←/→ e Enter
 * trocam a tabela, e no diálogo do código o Esc volta ao nome (não fecha tudo).
 */
@OptIn(ExperimentalTestApi::class)
class DesktopRankingKeysTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val tables = mutableListOf<RankingTable>()
    private val songs = mutableListOf<String>()

    private fun showSongs() {
        val song = RankedSong("m1", SONG, "Banda", 1, null, mapOf(RankingTable.PC_STRUM to TableStanding(1, null)))
        composeRule.setContent {
            RiffTheme {
                RankingSongsContent(
                    RankingSongsUiState(songs = listOf(song), table = RankingTable.PC_STRUM),
                    onQuery = {},
                    onSong = { songs += it.title },
                    onRetry = {},
                    onBack = {},
                    modifier = Modifier.menuKeys(onBack = {}),
                    onTable = { tables += it },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun downLeavesTheSearchFieldForTheSongs() {
        showSongs()
        composeRule.runOnUiThread { composeRule.onNode(hasSetTextAction()).performClick() }
        composeRule.waitUntil(TIMEOUT_MS) { focused(hasSetTextAction()) }

        press(Key.DirectionDown)

        assertTrue("↓ não saiu do campo de busca", !focused(hasSetTextAction()))
        assertTrue("↓ não chegou à música", focused(hasText(SONG)))
        press(Key.Enter)
        assertEquals(listOf(SONG), songs)
    }

    @Test
    fun arrowsAndEnterSwitchTheTable() {
        showSongs()

        repeat(MAX_PRESSES) { if (!focused(hasText(PHONE))) press(Key.DirectionDown) }
        assertTrue("↓ não chegou aos chips", focused(hasText(PHONE)))
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.Enter)

        assertEquals(listOf(RankingTable.PC_NO_STRUM), tables)
    }

    @Test
    fun escapeInTheCodeGoesBackToTheName() {
        var back = 0
        var dismissed = 0
        composeRule.setContent {
            RiffTheme {
                PlayerNameDialog(
                    PlayerNameUiState(step = NameStep.ENTER_CODE),
                    onName = {},
                    onSave = {},
                    onLater = null,
                    onDismiss = { dismissed++ },
                    link = LinkActions(onBack = { back++ }),
                )
            }
        }
        composeRule.waitForIdle()

        press(Key.Escape, into = DIALOG)

        assertEquals("o Esc não voltou ao nome", 1, back)
        assertEquals("o Esc fechou tudo", 0, dismissed)
    }

    private fun press(key: Key, into: SemanticsMatcher = isRoot()) {
        composeRule.runOnUiThread { composeRule.onNode(into).performKeyInput { pressKey(key) } }
        composeRule.waitForIdle()
    }

    private fun focused(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val SONG = "Canção"
        const val PHONE = "Celular"
        const val TIMEOUT_MS = 5_000L
        const val MAX_PRESSES = 6
        val DIALOG = isRoot() and hasAnyDescendant(hasText("Código do outro aparelho"))
    }
}
