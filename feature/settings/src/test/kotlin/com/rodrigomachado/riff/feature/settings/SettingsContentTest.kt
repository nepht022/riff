package com.rodrigomachado.riff.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rodrigomachado.riff.core.model.AppLanguage
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.TrackLook
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "pt-rBR")
class SettingsContentTest {
    private val sounds = mutableListOf<MenuSound>()

    @get:Rule
    val composeRule = createComposeRule()

    private var leftHanded: Boolean? = null
    private var menuSounds: Boolean? = null
    private var back = false

    private val events = mutableListOf<String>()

    private val looks = mutableListOf<TrackLook>()
    private val languages = mutableListOf<AppLanguage>()

    /** Abre a aba e esquece o som do toque nela (cada teste confere só os sons do que testa). */
    private fun tab(name: String) {
        composeRule.onNodeWithText(name).performClick()
        sounds.clear()
    }

    private fun show(
        settings: Settings = Settings.DEFAULT,
        separationUsage: SeparationUsage = SeparationUsage(0, 0),
        language: AppLanguage = AppLanguage.PORTUGUESE,
    ) = composeRule.setContent {
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
                    onTrackLook = { looks += it },
                    onShowChartImage = { events += "image:$it" },
                    language = language,
                    onLanguage = { languages += it },
                )
            }
        }
    }

    @Test
    fun showsEverySettingWithItsCurrentValue() {
        show(Settings.DEFAULT.copy(masterVolume = 0.8f, noteSpeed = 1.25f))

        composeRule.onNodeWithText("Configurações").assertExists()
        tab("Sons")
        composeRule.onNodeWithText("Volume geral").assertExists()
        composeRule.onNodeWithText("80%").assertExists()
        composeRule.onNodeWithText("Som de acerto").assertExists()
        composeRule.onNodeWithText("Som de erro").assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.MASTER_VOLUME)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.8f, 0f..1f, steps = 19))
        tab("Jogo")
        composeRule.onNodeWithText("Velocidade das notas").performScrollTo()
        composeRule.onNodeWithText("1,25×").assertExists()
    }

    @Test
    fun theSpotifyVolumeLimitIsExplained() {
        show()
        tab("Sons")

        composeRule.onNodeWithText("Não muda o volume do Spotify; use o volume do celular.").assertExists()
    }

    @Test
    fun leftHandedModeCanBeTurnedOn() {
        show()
        tab("Jogo")

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

        composeRule.onNodeWithText("Jogo").performClick()
        composeRule.onNodeWithText("Calibrar atraso do áudio").performScrollTo().performClick()

        composeRule.onNodeWithText("42 ms").assertExists()
        assertEquals(true, opened)
    }

    @Test
    fun withoutCalibrationItSaysSo() {
        show()
        tab("Jogo")

        composeRule.onNodeWithText("Não calibrado").performScrollTo().assertExists()
    }

    @Test
    fun theMenuSoundsSwitchTurnsThemOffSilently() {
        show()

        composeRule.onNodeWithTag(SettingsTestTags.MENU_SOUNDS).performScrollTo().performClick()
        // O interruptor dos sons não soa por conta própria (ao desligar, ainda estaria ligado): o app soa ao ligar.
        assertEquals(emptyList<MenuSound>(), sounds)
        tab("Jogo")
        composeRule.onNodeWithTag(SettingsTestTags.LEFT_HANDED).performScrollTo().performClick()

        assertEquals(false, menuSounds)
        assertEquals(listOf(MenuSound.NAVIGATE), sounds)
    }

    @Test
    fun theAiSectionDeletesAfterConfirmingAndAsksAgain() {
        show(Settings.DEFAULT, separationUsage = SeparationUsage(12, 96_000_000))
        tab("Guitarra IA")

        composeRule.onNodeWithText("12 músicas · 96 MB").assertExists()
        composeRule.onNodeWithText("Apagar separações").performScrollTo().performClick()
        composeRule.onNodeWithText("Apagar").performClick() // confirmação
        composeRule.onNodeWithTag(SettingsTestTags.ASK_ALWAYS).performScrollTo().performClick()

        assertEquals(listOf("delete", "askAlways:false"), events)
    }

    @Test
    fun oneSeparatedSongIsSingular() {
        show(Settings.DEFAULT, separationUsage = SeparationUsage(1, 8_600_000))
        tab("Guitarra IA")

        composeRule.onNodeWithText("1 música · 8,6 MB").performScrollTo().assertExists()
    }

    @Test
    fun withNothingSeparatedItSaysSo() {
        show(Settings.DEFAULT, separationUsage = SeparationUsage(0, 0))
        tab("Guitarra IA")

        composeRule.onNodeWithText("Nenhuma música separada").performScrollTo().assertExists()
    }

    @Test
    fun theAiActionsLineUpWithTheOtherSettingsAndExplainAskingAgain() {
        show(Settings.DEFAULT, separationUsage = SeparationUsage(1, 8_600_000))
        tab("Jogo")
        val calibrate = composeRule.onNodeWithText("Calibrar atraso do áudio", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        tab("Guitarra IA")

        for (action in listOf("Apagar separações", "Perguntar sempre se separa")) {
            val bounds = composeRule.onNodeWithText(action, useUnmergedTree = true).performScrollTo()
                .getUnclippedBoundsInRoot()
            assertEquals(action, calibrate.left.value, bounds.left.value, 0.5f)
        }
        composeRule.onNodeWithText("Sem separar", substring = true).performScrollTo().assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ASK_ALWAYS).assertIsOn()
    }

    @Test
    fun choosingABackgroundOpensTheListAndSavesIt() {
        show()
        tab("Visual da pista")

        composeRule.onNodeWithText("Fundo da pista").performScrollTo().performClick()
        composeRule.onNodeWithText("Neon").performScrollTo().performClick()

        assertEquals(listOf(TrackLook.DEFAULT.copy(background = HighwayBackground.NEON)), looks)
        assertEquals(listOf(MenuSound.NAVIGATE, MenuSound.NAVIGATE), sounds)
        composeRule.onNodeWithText("Neon").assertDoesNotExist() // a lista fecha
    }

    @Test
    fun everyChoiceListsAllItsOptions() {
        show()
        tab("Visual da pista")

        composeRule.onNodeWithText("Design dos botões").performScrollTo().performClick()

        for (name in listOf("Pedais de efeito", "Knobs de amplificador", "Alto-falantes", "Palhetas")) {
            composeRule.onNodeWithText(name).assertExists()
        }
    }

    @Test
    fun theFourChoicesShowTheCurrentNames() {
        show(Settings.DEFAULT.copy(noteStyle = NoteStyle.SKULLS, fretStyle = FretStyle.KNOBS))
        tab("Visual da pista")

        composeRule.onNodeWithText("Palco").performScrollTo().assertExists()
        composeRule.onNodeWithText("Sem desenho").performScrollTo().assertExists()
        composeRule.onNodeWithText("Caveiras").performScrollTo().assertExists()
        composeRule.onNodeWithText("Knobs de amplificador").performScrollTo().assertExists()
    }

    @Test
    fun theMapImageSwitchStartsOnAndTurnsOff() {
        show()
        tab("Visual da pista")

        composeRule.onNodeWithText("Imagem do mapa").performScrollTo().assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.CHART_IMAGE).performScrollTo().assertIsOn().performClick()

        assertEquals(listOf("image:false"), events)
    }

    @Test
    fun opensOnTheGeneralTabWithTheLanguage() {
        show()

        composeRule.onNodeWithText("Geral").assertIsSelected()
        composeRule.onNodeWithText("Idioma").assertExists()
        composeRule.onNodeWithText("Português").assertExists()
        composeRule.onNodeWithText("Sons do menu").assertExists()
        composeRule.onNodeWithText("Volume geral").assertDoesNotExist()
    }

    @Test
    fun eachTabShowsItsOwnSettings() {
        show()

        val firstOfEach = listOf(
            "Sons" to "Volume geral",
            "Jogo" to "Velocidade das notas",
            "Visual da pista" to "Fundo da pista",
            "Guitarra IA" to "Apagar separações",
            "Geral" to "Idioma",
        )
        for ((tab, setting) in firstOfEach) {
            composeRule.onNodeWithText(tab).performClick().assertIsSelected()
            composeRule.onNodeWithText(setting).assertExists()
        }
        assertEquals(List(firstOfEach.size) { MenuSound.NAVIGATE }, sounds)
    }

    @Test
    fun choosingEnglishChangesTheLanguage() {
        show()

        composeRule.onNodeWithText("Idioma").performClick()
        composeRule.onNodeWithText("English").performClick()

        assertEquals(listOf(AppLanguage.ENGLISH), languages)
        composeRule.onNodeWithText("English").assertDoesNotExist() // a lista fecha
    }

    @Test
    fun theLanguageRowShowsTheCurrentOne() {
        show(language = AppLanguage.ENGLISH)

        composeRule.onNodeWithText("English").assertExists()
    }
}
