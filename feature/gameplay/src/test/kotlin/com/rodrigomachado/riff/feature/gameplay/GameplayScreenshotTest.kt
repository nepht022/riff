package com.rodrigomachado.riff.feature.gameplay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.Note
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class GameplayScreenshotTest {
    @Test
    fun stageMidSong() {
        val time = FakeTime()
        val player = FakeSongPlayer().apply { positionMs = 4_000 }
        val notes = listOf(
            Note(4_000, 2, 0, false),
            Note(4_450, 0, 0, false),
            Note(4_900, 1, 0, true),
            Note(5_350, 3, 900, false),
            Note(5_800, 4, 0, false),
            Note(6_250, 2, 0, false),
            Note(6_700, 0, 0, false),
        )
        val session = GameSession(notes, player, FakeSfxPlayer(), time, audioOffsetMs = 0).also {
            it.start()
            it.tick()
            it.press(2) // acerta a nota amarela agora: chama e "PERFEITO!"
        }
        time.now = 40
        val state = GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, Settings.DEFAULT)
        captureRoboImage("src/test/screenshots/Gameplay_stage.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    GameStage(
                        state,
                        reducedMotion = false,
                        onPause = {},
                        onResume = {},
                        onExit = {},
                        onSessionFinished = {},
                        animate = false,
                    )
                }
            }
        }
    }

    @Test
    fun stageMidSongLeftHandedWithAMissedNote() {
        val time = FakeTime()
        val player = FakeSongPlayer().apply { positionMs = 3_650 }
        val notes = listOf(
            Note(3_500, 1, 0, false), // passa sem ser tocada: fica esmaecida
            Note(4_000, 2, 0, false),
            Note(4_450, 0, 0, false),
            Note(4_900, 1, 0, true),
            Note(5_350, 3, 900, false),
            Note(5_800, 4, 0, false),
        )
        val session = GameSession(notes, player, FakeSfxPlayer(), time, audioOffsetMs = 0).also {
            it.start()
            it.tick()
        }
        // Em 0,5× a pista mostra até ~200 ms abaixo da linha: a nota perdida (julgada aos 100 ms) aparece.
        val settings = Settings.DEFAULT.copy(leftHanded = true, noteSpeed = 0.5f)
        val state = GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, settings)
        captureRoboImage("src/test/screenshots/Gameplay_stage_lefthanded.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    GameStage(
                        state,
                        reducedMotion = false,
                        onPause = {},
                        onResume = {},
                        onExit = {},
                        onSessionFinished = {},
                        animate = false,
                    )
                }
            }
        }
    }

    /** Imagem do mapa (um degradê colorido gerado aqui) atrás da pista, com outro visual escolhido. */
    @Test
    fun stageWithTheMapImageAndAnotherLook() {
        val time = FakeTime()
        val player = FakeSongPlayer().apply { positionMs = 4_000 }
        val notes = listOf(
            Note(4_000, 2, 0, false),
            Note(4_450, 0, 0, false),
            Note(4_900, 1, 0, true),
            Note(5_350, 3, 900, false),
            Note(5_800, 4, 0, false),
        )
        val session = GameSession(notes, player, FakeSfxPlayer(), time, audioOffsetMs = 0).also {
            it.start()
            it.tick()
        }
        val image = Bitmap.createBitmap(400, 700, Bitmap.Config.ARGB_8888).also {
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    400f,
                    700f,
                    0xFF2266FF.toInt(),
                    0xFFFF6622.toInt(),
                    Shader.TileMode.CLAMP,
                )
            }
            Canvas(it).drawRect(0f, 0f, 400f, 700f, paint)
        }
        val settings = Settings.DEFAULT.copy(
            highwayBackground = HighwayBackground.LEATHER,
            highwayArt = HighwayArt.WINGED_SKULL,
            noteStyle = NoteStyle.PICKS,
            fretStyle = FretStyle.PEDALS,
        )
        val state = GameplayUiState.Ready(
            "Música",
            "Banda",
            Difficulty.EXPERT,
            session,
            settings,
            art = image.asImageBitmap(),
        )
        captureRoboImage("src/test/screenshots/Gameplay_stage_art.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    GameStage(
                        state,
                        reducedMotion = false,
                        onPause = {},
                        onResume = {},
                        onExit = {},
                        onSessionFinished = {},
                        animate = false,
                    )
                }
            }
        }
    }
}
