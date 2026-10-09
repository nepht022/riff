package com.rodrigomachado.riff.feature.settings

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class SettingsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var leftHanded: Boolean? = null
    private var back = false

    private fun show(settings: Settings = Settings.DEFAULT) = composeRule.setContent {
        RiffTheme {
            SettingsContent(
                settings = settings,
                onMasterVolume = {},
                onHitVolume = {},
                onMissVolume = {},
                onNoteSpeed = {},
                onLeftHanded = { leftHanded = it },
                onPreviewHit = {},
                onPreviewMiss = {},
                onBack = { back = true },
            )
        }
    }

    @Test
    fun showsEverySettingWithItsCurrentValue() {
        show(Settings.DEFAULT.copy(masterVolume = 0.8f, noteSpeed = 1.25f))

        composeRule.onNodeWithText("Configurações").assertExists()
        composeRule.onNodeWithText("Volume geral").assertExists()
        composeRule.onNodeWithText("80%").assertExists()
        composeRule.onNodeWithText("Som de acerto").assertExists()
        composeRule.onNodeWithText("Som de erro").assertExists()
        composeRule.onNodeWithText("Velocidade das notas").performScrollTo()
        composeRule.onNodeWithText("1,25×").assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.MASTER_VOLUME)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.8f, 0f..1f, steps = 19))
    }

    @Test
    fun theSpotifyVolumeLimitIsExplained() {
        show()

        composeRule.onNodeWithText("Não muda o volume do Spotify; use o volume do celular.").assertExists()
    }

    @Test
    fun leftHandedModeCanBeTurnedOn() {
        show()

        composeRule.onNodeWithTag(SettingsTestTags.LEFT_HANDED).performScrollTo().assertIsOff().performClick()

        assertEquals(true, leftHanded)
    }

    @Test
    fun backLeavesTheScreen() {
        show()

        composeRule.onNodeWithText("Voltar").performClick()

        assertEquals(true, back)
    }
}
