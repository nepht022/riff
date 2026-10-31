package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MenuSoundTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aButtonPlaysTheSoundOfItsRoleBeforeItsAction() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            RiffTheme {
                CompositionLocalProvider(LocalMenuSound provides { events += "som:$it" }) {
                    Column {
                        PosterButton("Jogar", onClick = { events += "ação" }, sound = MenuSound.CONFIRM)
                        PosterButton("Pausar", onClick = { events += "pausa" })
                    }
                }
            }
        }

        composeRule.onNodeWithText("Jogar").performClick()
        composeRule.onNodeWithText("Pausar").performClick()

        // Sem papel (como o Pausar da partida), o botão fica em silêncio.
        assertEquals(listOf("som:CONFIRM", "ação", "pausa"), events)
    }

    /** Tela no meio de uma animação de entrada ou saída: começada, mas ainda não em primeiro plano. */
    private class StartedOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.STARTED }
    }

    @Test
    fun aTapIgnoredByAScreenThatIsNotReadyMakesNoSound() {
        val sounds = mutableListOf<MenuSound>()
        composeRule.setContent {
            RiffTheme {
                CompositionLocalProvider(
                    LocalMenuSound provides { sounds += it },
                    LocalLifecycleOwner provides StartedOwner(),
                ) {
                    PosterButton("Biblioteca", onClick = {}, sound = MenuSound.NAVIGATE)
                }
            }
        }

        // A navegação descarta o toque (dropUnlessResumed): sem ação, o som também não toca.
        composeRule.onNodeWithText("Biblioteca").performClick()

        assertEquals(emptyList<MenuSound>(), sounds)
    }
}
