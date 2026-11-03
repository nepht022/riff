package com.rodrigomachado.riff.core.ui.input

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Quem tira o controle dos menus tira só enquanto está na tela, e só os botões que escolheu (os de agora). */
class PadMenuSuspensionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aTakerHoldsItsButtonsOnlyWhileShown() {
        val suspension = PadMenuSuspension()
        var shown by mutableStateOf(true)
        var only by mutableStateOf("m:7")
        composeRule.setContent {
            CompositionLocalProvider(LocalPadMenuSuspension provides suspension) {
                if (shown) TakePadFromMenus { it == only }
            }
        }

        composeRule.runOnIdle {
            assertFalse(suspension.allows("m:7"))
            assertTrue(suspension.allows("m:0"))
        }
        only = "m:0"
        composeRule.runOnIdle {
            assertTrue(suspension.allows("m:7"))
            assertFalse(suspension.allows("m:0"))
        }
        shown = false
        composeRule.runOnIdle { assertTrue(suspension.allows("m:0")) }
    }
}
