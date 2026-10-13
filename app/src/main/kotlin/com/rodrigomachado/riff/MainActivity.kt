package com.rodrigomachado.riff

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.component.LocalSeparation
import com.rodrigomachado.riff.core.ui.component.SeparationControls
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var menuSounds: MenuSoundPlayer

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var separations: SeparationScheduler

    // A notificação da separação abre a Biblioteca (com o app fechado ou já aberto).
    private val openLibrary = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Só numa tela nova: restaurada (rotação, processo recriado), o pedido já foi atendido.
        openLibrary.value = savedInstanceState == null && consumeOpenLibrary(intent)
        // O tema é sempre escuro: os ícones das barras do sistema ficam claros
        // mesmo com o aparelho no modo claro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val settings = settingsRepository.settings.collectAsState(initial = null)
            // Criada uma vez e lendo a preferência na hora do toque: mudar as Configurações não recompõe o app.
            val playMenuSound: (MenuSound) -> Unit = remember { { playMenuSound(menuSounds, settings.value, it) } }
            LaunchedEffect(Unit) {
                var previous: Settings? = null
                snapshotFlow { settings.value }.collect { current ->
                    if (menuSoundsTurnedOn(previous, current)) playMenuSound(MenuSound.NAVIGATE)
                    previous = current
                }
            }
            val separationControls = remember { separationControls(separations) }
            CompositionLocalProvider(
                LocalMenuSound provides playMenuSound,
                LocalSeparation provides separationControls,
            ) {
                RiffApp(openLibrary.value) { openLibrary.value = false }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (consumeOpenLibrary(intent)) openLibrary.value = true
    }
}

private fun separationControls(scheduler: SeparationScheduler) = object : SeparationControls {
    override val status = scheduler.status

    override fun cancel() = scheduler.cancel()

    override fun dismissFailure() = scheduler.dismissFailure()
}

@Composable
private fun RiffApp(openLibrary: Boolean, onLibraryOpened: () -> Unit) {
    RiffTheme {
        // Etiquetas viram resource-id: os percursos de desempenho (UiAutomator) acham os elementos.
        StageBackground(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
            RiffNavHost(Modifier.safeDrawingPadding(), openLibrary, onLibraryOpened)
        }
    }
}
