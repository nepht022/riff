package com.rodrigomachado.riff.feature.search

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** No PC, o campo da busca não prende quem usa só o teclado: ↓ sai dele para os resultados. */
class DesktopSearchKeysTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun downLeavesTheSearchField() {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        val chart = EncoreChart("a", "Lithium", "Nirvana", "highfine", 1_000, setOf(Difficulty.EXPERT), false)
        composeRule.setContent {
            RiffTheme {
                SearchContent(
                    state = SearchUiState(
                        query = "nirvana",
                        results = listOf(ResultItem(chart, ItemState.Available, separateGuitar = null)),
                    ),
                    onQueryChange = {},
                    onSearch = {},
                    onLoadMore = {},
                    onDownload = {},
                    onPlay = { _, _ -> },
                    onAllowWrite = {},
                    onBack = {},
                    onShowVersions = {},
                    onOnlySeparateGuitar = {},
                    onSearchMoreWithGuitar = {},
                )
            }
        }
        val field = composeRule.onNodeWithText("nirvana")
        composeRule.runOnUiThread { field.performClick() }
        field.assertIsFocused()

        composeRule.runOnUiThread { field.performKeyInput { pressKey(Key.DirectionDown) } }

        field.assertIsNotFocused()
    }
}
