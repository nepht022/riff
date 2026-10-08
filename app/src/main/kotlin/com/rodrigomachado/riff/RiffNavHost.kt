package com.rodrigomachado.riff

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rodrigomachado.riff.feature.gameplay.GameplayRoute
import com.rodrigomachado.riff.feature.gameplay.GameplayScreen
import com.rodrigomachado.riff.feature.library.LibraryRoute
import com.rodrigomachado.riff.feature.library.LibraryScreen
import com.rodrigomachado.riff.feature.results.ResultsRoute
import com.rodrigomachado.riff.feature.results.ResultsScreen

/** Biblioteca → Jogo → Resultado. As features não se conhecem; a ligação é feita aqui. */
@Composable
fun RiffNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = LibraryRoute, modifier = modifier) {
        composable<LibraryRoute> {
            LibraryScreen(
                onPlay = { chartId, difficulty -> navController.navigate(GameplayRoute(chartId, difficulty.name)) },
            )
        }
        composable<GameplayRoute> {
            GameplayScreen(
                onFinished = { scoreId ->
                    navController.navigate(ResultsRoute(scoreId)) { popUpTo<GameplayRoute> { inclusive = true } }
                },
                onExit = { navController.popBackStack() },
            )
        }
        composable<ResultsRoute> {
            ResultsScreen(
                onPlayAgain = { chartId, difficulty ->
                    navController.navigate(
                        GameplayRoute(chartId, difficulty.name),
                    ) { popUpTo<ResultsRoute> { inclusive = true } }
                },
                onBackToLibrary = { navController.popBackStack(LibraryRoute, inclusive = false) },
            )
        }
    }
}
