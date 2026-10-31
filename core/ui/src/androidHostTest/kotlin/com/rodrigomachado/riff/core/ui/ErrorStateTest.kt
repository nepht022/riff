package com.rodrigomachado.riff.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.StatusViewsTestTags
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ErrorStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun retryButtonCallsOnRetry() {
        var retries = 0
        composeRule.setContent {
            RiffTheme { ErrorState(message = "Falhou", onRetry = { retries++ }) }
        }

        composeRule.onNodeWithTag(StatusViewsTestTags.RETRY).performClick()

        assertEquals(1, retries)
    }
}
