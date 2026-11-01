package com.rodrigomachado.riff.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rodrigomachado.riff.core.audio.menuSoundsTurnedOn
import com.rodrigomachado.riff.core.audio.playMenuSound
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.feature.home.HomeRoute
import com.rodrigomachado.riff.feature.home.HomeScreen
import com.rodrigomachado.riff.feature.library.LibraryRoute
import com.rodrigomachado.riff.feature.library.LibraryScreen
import java.util.Locale

/** O Riff no PC: tela inicial e Biblioteca; jogar, Ranking, Configurações e Reportar vêm nas próximas etapas. */
@Composable
fun RiffDesktopApp(graph: DesktopGraph) {
    val navController = rememberNavController()
    // Como no celular: a preferência é lida na hora do toque (mudar as Configurações não recompõe o app).
    val settings = graph.settings.settings.collectAsState(initial = null)
    val playMenuSound: (MenuSound) -> Unit = remember(graph) { { playMenuSound(graph.menuSounds, settings.value, it) } }
    LaunchedEffect(graph) {
        var previous: Settings? = null
        snapshotFlow { settings.value }.collect { current ->
            if (menuSoundsTurnedOn(previous, current)) playMenuSound(MenuSound.NAVIGATE)
            previous = current
        }
    }
    RiffTheme {
        CompositionLocalProvider(LocalMenuSound provides playMenuSound) { DesktopScreens(graph, navController) }
    }
}

@Composable
private fun DesktopScreens(graph: DesktopGraph, navController: NavHostController) {
    StageBackground(Modifier.fillMaxSize()) {
        NavHost(navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                HomeScreen(
                    onSolo = { navController.navigate(LibraryRoute) },
                    onRanking = {},
                    onSettings = {},
                    onReport = {},
                )
            }
            composable<LibraryRoute> {
                LibraryScreen(
                    onPlay = { _, _ -> },
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel { graph.libraryViewModel() },
                )
            }
        }
    }
}

/** Português é o padrão, mesmo num Windows em inglês (como no celular); escolher o idioma no PC vem depois. */
object DesktopLocale {
    fun applyDefault() = Locale.setDefault(Locale.forLanguageTag("pt-BR"))
}
