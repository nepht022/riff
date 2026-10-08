package com.rodrigomachado.riff

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rodrigomachado.riff.core.ui.navigation.dropUnlessResumed
import com.rodrigomachado.riff.feature.gameplay.GameplayRoute
import com.rodrigomachado.riff.feature.gameplay.GameplayScreen
import com.rodrigomachado.riff.feature.library.LibraryRoute
import com.rodrigomachado.riff.feature.library.LibraryScreen
import com.rodrigomachado.riff.feature.library.TrackToMatch
import com.rodrigomachado.riff.feature.nowplaying.NowPlayingRoute
import com.rodrigomachado.riff.feature.nowplaying.NowPlayingScreen
import com.rodrigomachado.riff.feature.results.ResultsRoute
import com.rodrigomachado.riff.feature.results.ResultsScreen

/**
 * Tocando agora → Jogo → Resultado, e a biblioteca (jogar com o áudio da pasta ou escolher o mapa de
 * uma música). As features não se conhecem; a ligação é feita aqui. Cada ação só vale com a tela em
 * primeiro plano, para um toque duplo não navegar duas vezes.
 */
@Composable
fun RiffNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = NowPlayingRoute, modifier = modifier) {
        composable<NowPlayingRoute> {
            NowPlayingScreen(
                onPlay = dropUnlessResumed { request ->
                    navController.navigate(
                        GameplayRoute(
                            request.chartId,
                            request.difficulty.name,
                            request.trackUri,
                            request.trackOffsetMs,
                        ),
                    )
                },
                onChooseChart = dropUnlessResumed { trackUri, title ->
                    navController.navigate(LibraryRoute(matchTrackUri = trackUri, matchTrackTitle = title))
                },
                onOpenLibrary = dropUnlessResumed { navController.navigate(LibraryRoute()) },
            )
        }
        composable<LibraryRoute> { entry ->
            val route = entry.toRoute<LibraryRoute>()
            LibraryScreen(
                onPlay = dropUnlessResumed { chartId, difficulty ->
                    navController.navigate(GameplayRoute(chartId, difficulty.name))
                },
                match = route.matchTrackUri?.let { TrackToMatch(it, route.matchTrackTitle.orEmpty()) },
                onMatched = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable<GameplayRoute> { entry ->
            val route = entry.toRoute<GameplayRoute>()
            GameplayScreen(
                onFinished = { scoreId ->
                    navController.navigate(ResultsRoute(scoreId, route.spotifyTrackUri, route.trackOffsetMs)) {
                        popUpTo<GameplayRoute> { inclusive = true }
                    }
                },
                onExit = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable<ResultsRoute> { entry ->
            val route = entry.toRoute<ResultsRoute>()
            ResultsScreen(
                onPlayAgain = dropUnlessResumed { chartId, difficulty ->
                    navController.navigate(
                        GameplayRoute(chartId, difficulty.name, route.spotifyTrackUri, route.trackOffsetMs),
                    ) { popUpTo<ResultsRoute> { inclusive = true } }
                },
                onDone = dropUnlessResumed { navController.popBackStack() },
            )
        }
    }
}
