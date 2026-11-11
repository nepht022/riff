package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * A partida na janela deitada do PC: a pista central na altura toda, placar e combo à esquerda, multiplicador à
 * direita e o progresso embaixo. Em 16:9 e 21:9 (janela larga), com até 1% dos pixels de folga (a suavização das
 * bordas muda entre Windows e Linux), como nos outros screenshots do PC.
 */
class DesktopGameplayScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val system = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(system)

    private fun capture(
        name: String,
        width: Dp,
        height: Dp,
        state: GameplayUiState.Ready,
        liveOthers: State<List<LiveScore>>? = null,
    ) {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        composeRule.setContent {
            Box(Modifier.size(width, height)) {
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
                            liveOthers = liveOthers,
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/desktop/$name.png", roborazziOptions = Tolerance)
    }

    @Test
    fun wideWindow() = capture("Gameplay_wide", 960.dp, 540.dp, midSong())

    @Test
    fun ultraWideWindowWithTheMapImage() = capture("Gameplay_ultrawide_art", 1008.dp, 432.dp, midSong(art = gradient()))

    @Test
    fun pausedInAWideWindow() = capture("Gameplay_wide_paused", 960.dp, 540.dp, midSong().copy(isPaused = true))

    /** A partida de sala com o placar ao vivo cheio, na coluna do placar à esquerda, embaixo do combo. */
    @Test
    fun roomMatchWithTheLiveBoard() = capture(
        "Gameplay_wide_room_live",
        960.dp,
        540.dp,
        midSong().copy(inRoom = true),
        mutableStateOf(liveOthersSample()),
    )

    private companion object {
        val Tolerance = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))
    }
}

/**
 * No meio da música: 12 notas acertadas em seguida (combo e ×2), a amarela acabou de ser tocada (chama e
 * "PERFEITO!", o botão ainda apertado) e as próximas descendo.
 */
private fun midSong(art: ImageBitmap? = null): GameplayUiState.Ready {
    val time = FakeTime()
    val player = FakeSongPlayer()
    val played = (0 until PLAYED).map { Note(FIRST_MS + it * GAP_MS, it % LANES, 0, false) }
    val strike = Note(STRIKE_MS, 2, 0, false)
    val upcoming = listOf(
        Note(4_450, 0, 0, false),
        Note(4_900, 1, 0, true),
        Note(5_350, 3, 900, false),
        Note(5_800, 4, 0, false),
        Note(6_250, 2, 0, false),
        Note(6_700, 0, 0, false),
    )
    val session = GameSession(played + strike + upcoming, player, FakeSfxPlayer(), time, audioOffsetMs = 0)
    session.start()
    (played + strike).forEachIndexed { pointer, note ->
        player.positionMs = note.timeMs
        time.now = note.timeMs
        session.tick()
        session.pressPointer(pointer.toLong(), note.lane)
        if (note != strike) session.releasePointer(pointer.toLong())
    }
    time.now += FLAME_AGE_MS
    return GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, Settings.DEFAULT, art = art)
}

/** Imagem do mapa de mentira: um degradê colorido (deitado, como a capa de um mapa no PC costuma ser). */
private fun gradient(): ImageBitmap {
    val image = ImageBitmap(ART_WIDTH, ART_HEIGHT)
    val size = Size(ART_WIDTH.toFloat(), ART_HEIGHT.toFloat())
    val paint = Paint().apply {
        shader = LinearGradientShader(Offset.Zero, Offset(size.width, size.height), listOf(ArtStart, ArtEnd))
    }
    Canvas(image).drawRect(Rect(Offset.Zero, size), paint)
    return image
}

private val ArtStart = Color(0xFF2266FF)
private val ArtEnd = Color(0xFFFF6622)
private const val PLAYED = 12
private const val LANES = 5
private const val FIRST_MS = 1_000L
private const val GAP_MS = 250L
private const val STRIKE_MS = 4_000L
private const val FLAME_AGE_MS = 40L
private const val ART_WIDTH = 700
private const val ART_HEIGHT = 400
