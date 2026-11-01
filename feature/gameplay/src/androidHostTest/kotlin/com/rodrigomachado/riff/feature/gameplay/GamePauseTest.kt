package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** A pausa da partida: Continuar, a sincronia, Sair e o motivo quando o áudio está ocupado. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class GamePauseTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun showPaused(audioBusy: Boolean = false) {
        val session = GameSession(
            listOf(note(60_000)),
            FakeSongPlayer(),
            FakeSfxPlayer(),
            FakeTime(),
            audioOffsetMs = 0,
        )
        val state = GameplayUiState.Ready(
            "Música",
            "Banda",
            Difficulty.EXPERT,
            session,
            Settings.DEFAULT,
            isPaused = true,
            audioBusy = audioBusy,
        )
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    state,
                    reducedMotion = true,
                    onPause = {},
                    onResume = {},
                    onExit = {},
                    onSessionFinished = {},
                    animate = false,
                )
            }
        }
    }

    /** Sem o Spotify, a pausa não tem anterior/tocar/próxima. */
    @Test
    fun thePauseHasNoSongControls() {
        showPaused()

        composeRule.onNodeWithText("Continuar").assertExists()
        composeRule.onNodeWithContentDescription("Próxima música", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Música anterior", substring = true).assertDoesNotExist()
    }

    /** Continuar no meio de uma ligação pausa de novo: a pausa explica por quê. */
    @Test
    fun aBusyAudioIsExplainedInThePause() {
        showPaused(audioBusy = true)

        composeRule.onNodeWithText("O áudio do celular está ocupado (uma ligação?). Continue quando ele liberar.")
            .assertExists()
    }

    /** Em inglês (o Locale padrão do processo, como o app define): o motivo da pausa com o apóstrofo simples. */
    @Test
    @Config(qualifiers = "en")
    fun inEnglishTheBusyAudioReadsWithAPlainApostrophe() {
        val system = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            showPaused(audioBusy = true)
            composeRule.waitForIdle()
        } finally {
            Locale.setDefault(system)
        }

        composeRule.onNodeWithText("Paused").assertExists()
        composeRule.onNodeWithText("The phone's audio is busy (a call?). Resume when it frees up.").assertExists()
    }
}
