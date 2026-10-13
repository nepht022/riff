package com.rodrigomachado.riff.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
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
    private val sounds = mutableListOf<MenuSound>()

    @get:Rule
    val composeRule = createComposeRule()

    private var leftHanded: Boolean? = null
    private var menuSounds: Boolean? = null
    private var back = false

    private val events = mutableListOf<String>()

    private fun show(settings: Settings = Settings.DEFAULT, separationUsage: SeparationUsage = SeparationUsage(0, 0)) =
        composeRule.setContent {
            CompositionLocalProvider(LocalMenuSound provides { sounds += it }) {
                RiffTheme {
                    SettingsContent(
                        settings = settings,
                        onMasterVolume = {},
                        onHitVolume = {},
                        onMissVolume = {},
                        onNoteSpeed = {},
                        onLeftHanded = { leftHanded = it },
                        onMenuSounds = { menuSounds = it },
                        onPreviewHit = {},
                        onPreviewMiss = {},
                        onCalibrate = {},
                        onBack = { back = true },
                        separationUsage = separationUsage,
                        onDeleteSeparations = { events += "delete" },
                        onAskAlways = { events += "askAlways:$it" },
                    )
                }
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

    @Test
    fun calibrationShowsTheCurrentDelayAndOpens() {
        var opened = false
        composeRule.setContent {
            RiffTheme {
                SettingsContent(
                    settings = Settings.DEFAULT.copy(audioOffsetMs = -42),
                    onMasterVolume = {},
                    onHitVolume = {},
                    onMissVolume = {},
                    onNoteSpeed = {},
                    onLeftHanded = {},
                    onMenuSounds = {},
                    onPreviewHit = {},
                    onPreviewMiss = {},
                    onCalibrate = { opened = true },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Calibrar atraso do áudio").performScrollTo().performClick()

        composeRule.onNodeWithText("42 ms").assertExists()
        assertEquals(true, opened)
    }

    @Test
    fun withoutCalibrationItSaysSo() {
        show()

        composeRule.onNodeWithText("Não calibrado").performScrollTo().assertExists()
    }

    @Test
    fun theMenuSoundsSwitchTurnsThemOffSilently() {
        show()

        composeRule.onNodeWithTag(SettingsTestTags.MENU_SOUNDS).performScrollTo().performClick()
        composeRule.onNodeWithTag(SettingsTestTags.LEFT_HANDED).performScrollTo().performClick()

        assertEquals(false, menuSounds)
        // O interruptor dos sons não soa por conta própria (ao desligar, ainda estaria ligado): o app soa ao ligar.
        assertEquals(listOf(MenuSound.NAVIGATE), sounds)
    }

    @Test
    fun theAiSectionDeletesAfterConfirmingAndAsksAgain() {
        show(Settings.DEFAULT, separationUsage = SeparationUsage(12, 96_000_000))

        composeRule.onNodeWithText("GUITARRA SEPARADA POR IA").performScrollTo().assertExists()
        composeRule.onNodeWithText("12 músicas · 96 MB").assertExists()
        composeRule.onNodeWithText("Apagar separações").performScrollTo().performClick()
        composeRule.onNodeWithText("Apagar").performClick() // confirmação
        composeRule.onNodeWithTag(SettingsTestTags.ASK_ALWAYS).performScrollTo().performClick()

        assertEquals(listOf("delete", "askAlways:true"), events)
    }

    @Test
    fun oneSeparatedSongIsSingular() {
        show(Settings.DEFAULT, separationUsage = SeparationUsage(1, 8_600_000))

        composeRule.onNodeWithText("1 música · 8,6 MB").performScrollTo().assertExists()
    }

    @Test
    fun withNothingSeparatedItSaysSo() {
        show(Settings.DEFAULT, separationUsage = SeparationUsage(0, 0))

        composeRule.onNodeWithText("Nenhuma música separada").performScrollTo().assertExists()
    }

    @Test
    fun theAiActionsLineUpWithTheOtherSettingsAndExplainAskingAgain() {
        show(Settings.DEFAULT, separationUsage = SeparationUsage(1, 8_600_000))

        val calibrate = composeRule.onNodeWithText("Calibrar atraso do áudio", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        for (action in listOf("Apagar separações", "Perguntar de novo")) {
            val bounds = composeRule.onNodeWithText(action, useUnmergedTree = true).performScrollTo()
                .getUnclippedBoundsInRoot()
            assertEquals(action, calibrate.left.value, bounds.left.value, 0.5f)
        }
        composeRule.onNodeWithText("Sem separar", substring = true).performScrollTo().assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ASK_ALWAYS).assertIsOff()
    }
}
