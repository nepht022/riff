package com.rodrigomachado.riff.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rodrigomachado.riff.core.audio.menuSoundsTurnedOn
import com.rodrigomachado.riff.core.audio.playMenuSound
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.navigation.dropUnlessResumed
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.feature.gameplay.GameplayRoute
import com.rodrigomachado.riff.feature.gameplay.GameplayScreen
import com.rodrigomachado.riff.feature.home.HomeRoute
import com.rodrigomachado.riff.feature.home.HomeScreen
import com.rodrigomachado.riff.feature.library.LibraryRoute
import com.rodrigomachado.riff.feature.library.LibraryScreen
import com.rodrigomachado.riff.feature.results.ResultsRoute
import com.rodrigomachado.riff.feature.results.ResultsScreen
import java.util.Locale

/** O Riff no PC: tela inicial, Biblioteca, partida e resultado; Ranking, Configurações e Reportar vêm depois. */
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
    // Trocar o idioma refaz as telas já no idioma novo (os textos leem o Locale padrão), com a mesma pilha.
    val language by graph.languages.language.collectAsState()
    RiffTheme {
        CompositionLocalProvider(LocalMenuSound provides playMenuSound) {
            key(language) { DesktopScreens(graph, navController) }
        }
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
                    onPlay = dropUnlessResumed { chartId, difficulty ->
                        navController.navigate(GameplayRoute(chartId, difficulty.name))
                    },
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel { graph.libraryViewModel() },
                )
            }
            matchDestinations(graph, navController)
        }
    }
}

/**
 * A partida e o resultado, com a mesma pilha do celular: o Resultado toma o lugar da partida e "Jogar de novo", o do
 * Resultado (Biblioteca → Partida → Resultado → Biblioteca, sem tela repetida). O ranking no PC é da etapa 4: o
 * Resultado não mostra o cartão, e as ações dele não fazem nada.
 */
private fun NavGraphBuilder.matchDestinations(graph: DesktopGraph, navController: NavHostController) {
    composable<GameplayRoute> { entry ->
        val route = entry.toRoute<GameplayRoute>()
        GameplayScreen(
            onFinished = { scoreId ->
                navController.navigate(ResultsRoute(scoreId)) {
                    popUpTo<GameplayRoute> { inclusive = true }
                }
            },
            onExit = dropUnlessResumed { navController.popBackStack() },
            viewModel = viewModel { graph.gameplayViewModel(route.chartId, Difficulty.valueOf(route.difficulty)) },
        )
    }
    composable<ResultsRoute> { entry ->
        val route = entry.toRoute<ResultsRoute>()
        ResultsScreen(
            onPlayAgain = dropUnlessResumed { chartId, difficulty ->
                navController.navigate(GameplayRoute(chartId, difficulty.name)) {
                    popUpTo<ResultsRoute> { inclusive = true }
                }
            },
            onDone = dropUnlessResumed { navController.popBackStack() },
            viewModel = viewModel { graph.resultsViewModel(route.scoreId) },
        )
    }
}

/** Português, o idioma padrão (os testes voltam a ele; o jogo usa o escolhido, [DesktopAppLanguageStore]). */
object DesktopLocale {
    fun applyDefault() = Locale.setDefault(Locale.forLanguageTag("pt-BR"))
}
