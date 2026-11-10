package com.rodrigomachado.riff.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.ui.component.LocalSeparation
import com.rodrigomachado.riff.core.ui.component.SeparationControls
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uma separação em andamento com mais uma na fila: a palheta aparece no meio do topo. */
private object SeparatingWithQueue : SeparationControls {
    override val status = MutableStateFlow<SeparationStatus>(SeparationStatus.Running("1", "M", 0.4f, null))
    override val queue = MutableStateFlow(listOf(QueuedSeparation("2", "B")))
    override val waitingForCharger = MutableStateFlow(false)
    override fun cancel(chartId: String) = Unit
    override fun remove(chartId: String) = Unit
    override fun cancelAll() = Unit
    override fun dismissFailure() = Unit
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h780dp-xhdpi")
class HomeScreenshotTest {
    @Test
    fun home() = snapshot("Home")

    @Test
    fun homeWithTheSeparationQueue() = snapshot("Home_queue", SeparatingWithQueue)

    /** Com o Firebase da Sala configurado, o quadro da Sala liga. */
    @Test
    fun homeWithTheRoomOn() = snapshot("Home_room_on", roomOn = true)

    private fun snapshot(name: String, separation: SeparationControls? = null, roomOn: Boolean = false) {
        captureRoboImage("src/test/screenshots/$name.png") {
            CompositionLocalProvider(LocalSeparation provides separation) {
                RiffTheme {
                    StageBackground(Modifier.fillMaxSize()) {
                        HomeScreen(
                            onSolo = {},
                            onRanking = {},
                            onSettings = {},
                            onReport = {},
                            onRoom = if (roomOn) ({}) else null,
                        )
                    }
                }
            }
        }
    }
}
