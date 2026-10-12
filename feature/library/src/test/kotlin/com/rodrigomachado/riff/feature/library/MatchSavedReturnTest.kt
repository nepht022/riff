package com.rodrigomachado.riff.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MatchSavedReturnTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun aMatchSavedWhileInTheBackgroundReturnsWhenTheScreenComesBack() {
        val owner = TestOwner().apply { registry.currentState = Lifecycle.State.STARTED } // app em segundo plano
        var returns = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MatchSavedReturn(matchSaved = true) { returns++ }
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, returns) // a navegação seria descartada agora

        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()

        assertEquals(1, returns)
    }
}
