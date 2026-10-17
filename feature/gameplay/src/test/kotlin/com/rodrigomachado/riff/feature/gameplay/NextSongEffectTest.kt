package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rodrigomachado.riff.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A cortina de notificações aberta (tela só pausada) quando a partida segue para a próxima: a navegação só aceita
 * com a tela à frente, então o pedido espera em vez de se perder (e deixar a tela vazia).
 */
@RunWith(RobolectricTestRunner::class)
class NextSongEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun theNextSongWaitsForTheScreenToBeInFront() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.STARTED }
        val calls = mutableListOf<String>()
        val next = GameplayUiState.NextSong(Difficulty.EXPERT, "spotify:track:x", 6_000)
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                NextSongEffect(next) { difficulty, from, wait -> calls += "$difficulty $from $wait" }
            }
        }
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), calls)

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()

        assertEquals(listOf("EXPERT spotify:track:x 6000"), calls)
    }
}
