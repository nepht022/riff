package com.rodrigomachado.riff.core.ui.navigation

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
class DropUnlessResumedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun clicksOnAScreenThatIsLeavingAreDropped() {
        val calls = mutableListOf<Pair<String, Int>>()
        lateinit var action: (String, Int) -> Unit
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                action = dropUnlessResumed { a: String, b: Int -> calls += a to b }
            }
        }

        composeRule.runOnIdle { action("a", 1) }
        owner.registry.currentState = Lifecycle.State.STARTED // a tela está saindo
        composeRule.runOnIdle { action("b", 2) }

        assertEquals(listOf("a" to 1), calls)
    }
}
