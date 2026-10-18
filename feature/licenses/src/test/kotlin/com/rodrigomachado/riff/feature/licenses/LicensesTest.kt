package com.rodrigomachado.riff.feature.licenses

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeLicenseSource : LicenseSource {
    override suspend fun content() = listOf(
        LicenseEntry("Demucs v4", "Meta Platforms", "MIT", "MIT License\nCopyright (c) Meta"),
    )

    override suspend fun libraries() = listOf(
        LicenseEntry("Room", "The Android Open Source Project", "Apache 2.0", null),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LicensesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state = LicensesUiState(
        content = listOf(LicenseEntry("Demucs v4", "Meta Platforms", "MIT", "MIT License\nCopyright (c) Meta")),
        libraries = listOf(LicenseEntry("Room", "The Android Open Source Project", "Apache 2.0", null)),
    )

    @Test
    fun theViewModelLoadsBothLists() = runTest(dispatcher) {
        val vm = LicensesViewModel(FakeLicenseSource())
        testScheduler.advanceUntilIdle()

        assertEquals(state, vm.state.value)
    }

    @Test
    fun itShowsPrivacyContentAndLibraries() {
        composeRule.setContent { RiffTheme { LicensesContent(state, onBack = {}) } }

        composeRule.onNodeWithText("Licenças e privacidade").assertExists()
        composeRule.onNodeWithText("Privacidade").assertExists()
        composeRule.onNodeWithText(
            "Para tirar seu nome e suas pontuações do ranking, peça pelo Reportar.",
            substring = true,
        )
            .performScrollTo().assertExists()
        composeRule.onNodeWithText("Demucs v4").performScrollTo().assertExists()
        composeRule.onNodeWithText("Meta Platforms · MIT").assertExists()
        // A lista é preguiçosa: rola até o item existir.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Room"))
        composeRule.onNodeWithText("Room").assertExists()
    }

    @Test
    fun tappingAnItemShowsItsLicenseText() {
        composeRule.setContent { RiffTheme { LicensesContent(state, onBack = {}) } }

        composeRule.onNodeWithText("MIT License\nCopyright (c) Meta").assertDoesNotExist()
        composeRule.onNodeWithText("Demucs v4").performScrollTo().performClick()
        composeRule.onNodeWithText("MIT License\nCopyright (c) Meta").performScrollTo().assertExists()
    }

    @Test
    fun backGoesBack() {
        val events = mutableListOf<String>()
        composeRule.setContent { RiffTheme { LicensesContent(state, onBack = { events += "back" }) } }

        composeRule.onNodeWithText("Voltar").performClick()

        assertEquals(listOf("back"), events)
    }
}
