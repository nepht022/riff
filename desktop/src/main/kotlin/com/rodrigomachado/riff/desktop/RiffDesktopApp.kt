package com.rodrigomachado.riff.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    RiffTheme {
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
}

/** Português é o padrão, mesmo num Windows em inglês (como no celular); escolher o idioma no PC vem depois. */
object DesktopLocale {
    fun applyDefault() = Locale.setDefault(Locale.forLanguageTag("pt-BR"))
}
