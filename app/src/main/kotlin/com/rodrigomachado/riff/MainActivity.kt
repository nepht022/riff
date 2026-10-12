package com.rodrigomachado.riff

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var menuSounds: MenuSoundPlayer

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            CompositionLocalProvider(LocalMenuSound provides playMenuSound) {
                RiffApp()
            }
        }
    }
}

@Composable
private fun RiffApp() {
    RiffTheme {
        // Etiquetas viram resource-id: os percursos de desempenho (UiAutomator) acham os elementos.
        StageBackground(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
            RiffNavHost(Modifier.safeDrawingPadding())
        }
    }
}
