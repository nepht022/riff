package com.rodrigomachado.riff.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
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
    fun trackCardDark() = snapshot("TrackCard_dark", darkTheme = true) { SampleTrackCard() }

    @Test
    fun trackCardLight() = snapshot("TrackCard_light", darkTheme = false) { SampleTrackCard() }

    @Test
    fun statusBadgesDark() = snapshot("StatusBadge_dark", darkTheme = true) { AllBadges() }

    @Test
    fun statusBadgesLight() = snapshot("StatusBadge_light", darkTheme = false) { AllBadges() }

    @Test
    fun emptyState() = snapshot("EmptyState_dark", darkTheme = true) {
        EmptyState(
            title = "Nenhum mapa importado",
            message = "Importe uma pasta com mapas do Clone Hero para começar.",
            actionLabel = "Importar pasta",
            onAction = {},
        )
    }

    @Test
    fun errorState() = snapshot("ErrorState_dark", darkTheme = true) {
        ErrorState(message = "Não foi possível conectar ao Spotify.", onRetry = {})
    }

    @Composable
    private fun SampleTrackCard() {
        TrackCard(
            title = "Through the Fire and Flames",
            artist = "DragonForce",
            onClick = {},
            badge = { StatusBadge("Mapa encontrado", BadgeTone.SUCCESS) },
        )
    }

    @Composable
    private fun AllBadges() {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BadgeTone.entries.forEach { StatusBadge(it.name, it) }
        }
    }

    private fun snapshot(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface {
                    Box(Modifier.padding(Spacing.md)) { content() }
                }
            }
        }
    }
}
