package com.rodrigomachado.riff.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class ComponentScreenshotTest {

    @Test
    fun posterTitle() = snapshot("PosterTitle") { PosterTitle("RIFF") }

    @Test
    fun posterButton() = snapshot("PosterButton") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PosterButton("Jogar", onClick = {})
            PosterButton("Jogar", onClick = {}, enabled = false)
        }
    }

    @Test
    fun trackCards() = snapshot("TrackCard") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TrackCard(
                title = "Through the Fire and Flames",
                artist = "DragonForce",
                tilted = true,
                onClick = {},
                badge = { StatusBadge("Mapa encontrado", BadgeTone.SUCCESS) },
            )
            TrackCard(
                title = "Um título de música absurdamente longo que precisa ser cortado em duas linhas no cartão",
                artist = "Uma banda com nome igualmente comprido demais para caber",
                tilted = true,
                badge = { StatusBadge("Confirmar mapa", BadgeTone.WARNING) },
            )
            TrackCard(
                title = "Garota de Ipanema",
                artist = "Tom Jobim",
                badge = { StatusBadge("Sem mapa", BadgeTone.NEUTRAL) },
            )
        }
    }

    @Test
    fun statusBadges() = snapshot("StatusBadge") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BadgeTone.entries.forEach { StatusBadge(it.name, it) }
        }
    }

    @Test
    fun emptyState() = snapshot("EmptyState") {
        EmptyState(
            title = "Nenhum mapa importado",
            message = "Importe uma pasta com mapas do Clone Hero para começar.",
            actionLabel = "Importar pasta",
            onAction = {},
        )
    }

    @Test
    fun errorState() = snapshot("ErrorState") {
        ErrorState(message = "Não foi possível conectar ao Spotify.", onRetry = {})
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme {
                StageBackground {
                    Column(Modifier.padding(Spacing.md)) { content() }
                }
            }
        }
    }
}
