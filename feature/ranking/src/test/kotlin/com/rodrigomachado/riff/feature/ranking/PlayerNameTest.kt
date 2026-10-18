package com.rodrigomachado.riff.feature.ranking

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.data.ranking.NameResult
import com.rodrigomachado.riff.core.data.ranking.Player
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class PlayerNameTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ranking = FakeRanking()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun savingAFreeNameCloses() = runTest {
        val vm = PlayerNameViewModel(ranking)
        vm.onName("Rodrigo")

        vm.save()

        assertEquals(listOf("Rodrigo"), ranking.registered)
        assertTrue(vm.state.value.done)
    }

    @Test
    fun aTakenNameShowsTheMessageAndKeepsTheDialog() = runTest {
        ranking.answer = NameResult.Taken
        val vm = PlayerNameViewModel(ranking)
        vm.onName("Rodrigo")

        vm.save()

        assertEquals(NameResult.Taken, vm.state.value.error)
        assertFalse(vm.state.value.done)
    }

    @Test
    fun anInvalidNameIsCaughtBeforeSending() = runTest {
        val vm = PlayerNameViewModel(ranking)
        vm.onName("ab")

        vm.save()

        assertEquals(NameResult.Invalid, vm.state.value.error)
        assertTrue(ranking.registered.isEmpty())
    }

    @Test
    fun theFieldStartsWithTheCurrentName() = runTest {
        ranking.player.value = Player("Rodrigo", "k")

        assertEquals("Rodrigo", PlayerNameViewModel(ranking).state.value.name)
    }

    /** "Agora não" vale só até o app abrir de novo (o repositório guarda só na memória). */
    @Test
    fun notNowHidesItForThisSession() = runTest {
        assertTrue(shouldAskForName(configured = true, Player(null, null), askedLater = false))
        PlayerNameViewModel(ranking).later()

        assertTrue(ranking.askedLater.value)
        assertFalse(shouldAskForName(configured = true, Player(null, null), askedLater = true))
        assertFalse(shouldAskForName(configured = true, Player("Rodrigo", "k"), askedLater = false))
        assertFalse(shouldAskForName(configured = false, Player(null, null), askedLater = false))
    }

    @Test
    fun theDialogExplainsATakenName() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            RiffTheme {
                PlayerNameDialog(
                    PlayerNameUiState(name = "Rodrigo", error = NameResult.Taken),
                    onName = {},
                    onSave = { events += "save" },
                    onLater = { events += "later" },
                )
            }
        }

        composeRule.onNodeWithText("Digite seu nome").assertExists()
        composeRule.onNodeWithText("Esse nome já é de outra pessoa").assertExists()
        composeRule.onNodeWithText("Salvar").performClick()
        composeRule.onNodeWithText("Agora não").performClick()
        assertEquals(listOf("save", "later"), events)
    }

    @Test
    fun theDialogSaysWhoSeesTheNameAndExplainsTheLimit() {
        composeRule.setContent {
            RiffTheme {
                PlayerNameDialog(
                    PlayerNameUiState(name = "Rodrigo", error = NameResult.Limit),
                    onName = {},
                    onSave = {},
                    onLater = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Seu nome e suas melhores pontuações ficam visíveis para todos que jogam o Riff. " +
                "O celular é identificado por um código aleatório.",
        ).assertExists()
        composeRule.onNodeWithText("Muitas tentativas agora: tente de novo daqui a uma hora").assertExists()
    }

    @Test
    fun renamingHasNoNotNow() {
        composeRule.setContent {
            RiffTheme {
                PlayerNameDialog(
                    PlayerNameUiState(name = "Rodrigo"),
                    onName = {},
                    onSave = {},
                    onLater = null,
                )
            }
        }

        composeRule.onNodeWithText("Agora não").assertDoesNotExist()
    }
}
