package com.rodrigomachado.riff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.navigation.dropUnlessResumed
import com.rodrigomachado.riff.feature.calibration.CalibrationRoute
import com.rodrigomachado.riff.feature.calibration.CalibrationScreen
import com.rodrigomachado.riff.feature.gameplay.GameplayRoute
import com.rodrigomachado.riff.feature.gameplay.GameplayScreen
import com.rodrigomachado.riff.feature.library.LibraryRoute
import com.rodrigomachado.riff.feature.library.LibraryScreen
import com.rodrigomachado.riff.feature.library.TrackToMatch
import com.rodrigomachado.riff.feature.nowplaying.NowPlayingAutoPlay
import com.rodrigomachado.riff.feature.nowplaying.NowPlayingRoute
import com.rodrigomachado.riff.feature.nowplaying.NowPlayingScreen
import com.rodrigomachado.riff.feature.ranking.PlayerNameGate
import com.rodrigomachado.riff.feature.ranking.RankingBoardRoute
import com.rodrigomachado.riff.feature.ranking.RankingBoardScreen
import com.rodrigomachado.riff.feature.ranking.RankingRoute
import com.rodrigomachado.riff.feature.ranking.RankingSongsScreen
import com.rodrigomachado.riff.feature.report.ReportRoute
import com.rodrigomachado.riff.feature.report.ReportScreen
import com.rodrigomachado.riff.feature.results.ResultsRoute
import com.rodrigomachado.riff.feature.results.ResultsScreen
import com.rodrigomachado.riff.feature.search.SearchRoute
import com.rodrigomachado.riff.feature.search.SearchScreen
import com.rodrigomachado.riff.feature.settings.SettingsRoute
import com.rodrigomachado.riff.feature.settings.SettingsScreen

/**
 * Tocando agora → Jogo → Resultado, e a biblioteca (jogar com o áudio da pasta ou escolher o mapa de
 * uma música). As features não se conhecem; a ligação é feita aqui. Cada ação só vale com a tela em
 * primeiro plano, para um toque duplo não navegar duas vezes.
 */
@Composable
fun RiffNavHost(modifier: Modifier = Modifier, openLibrary: Boolean = false, onLibraryOpened: () -> Unit = {}) {
    val navController = rememberNavController()
    // Salvo com a tela: girar o celular ou o Android fechar o app em segundo plano não perde as telas abertas.
    val back = rememberSaveable(saver = Saver({ it.save() }, BackNavigation::restore)) { BackNavigation() }
    NavigationEffects(navController, back, openLibrary, onLibraryOpened)
    // Trocar o nome do ranking pelas Configurações (o mesmo diálogo da primeira abertura).
    var renamingPlayer by rememberSaveable { mutableStateOf(false) }
    NavHost(
        navController,
        startDestination = NowPlayingRoute,
        modifier = modifier,
        predictivePopEnterTransition = { RiffNavTransitions.backEnter },
        predictivePopExitTransition = { RiffNavTransitions.backExit },
    ) {
        composable<NowPlayingRoute> { entry ->
            NowPlayingScreen(
                autoPlayRequests = entry.savedStateHandle,
                onPlay = dropUnlessResumed { request ->
                    navController.navigate(
                        GameplayRoute(
                            request.chartId,
                            request.difficulty.name,
                            request.trackUri,
                            request.trackOffsetMs,
                            request.confirmFirst,
                        ),
                    )
                },
                onChooseChart = dropUnlessResumed { trackUri, title ->
                    navController.navigate(LibraryRoute(matchTrackUri = trackUri, matchTrackTitle = title))
                },
                onOpenLibrary = dropUnlessResumed { navController.navigate(LibraryRoute()) },
                onOpenSettings = dropUnlessResumed { navController.navigate(SettingsRoute) },
                onOpenReport = dropUnlessResumed { navController.navigate(ReportRoute) },
                onOpenRanking = dropUnlessResumed { navController.navigate(RankingRoute) },
                onSearch = dropUnlessResumed { track ->
                    navController.navigate(
                        SearchRoute(
                            "${track.artist} ${track.title}",
                            track.trackUri,
                            track.title,
                            track.artist,
                            track.durationMs,
                        ),
                    )
                },
            )
        }
        settingsDestinations(navController, back) { renamingPlayer = true }
        libraryDestinations(navController, back)
        rankingDestinations(navController)
        searchDestinations(navController, back)
        matchDestinations(navController) { renamingPlayer = true }
    }
    // O nome do ranking na primeira abertura, por cima do Tocando agora ou da Biblioteca.
    PlayerNameGate(renaming = renamingPlayer, onRenameClosed = { renamingPlayer = false })
}

/** O som de voltar entre telas e a Biblioteca aberta pela notificação da separação. */
@Composable
private fun NavigationEffects(
    navController: NavHostController,
    back: BackNavigation,
    openLibrary: Boolean,
    onLibraryOpened: () -> Unit,
) {
    val playMenuSound = LocalMenuSound.current
    // Som de voltar ao sair de uma tela, pelo botão Voltar, pelo gesto do celular ou pelo Sair da partida.
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { if (back.onShown(it.id)) playMenuSound(MenuSound.BACK) }
    }
    LaunchedEffect(openLibrary) {
        if (openLibrary) {
            navController.navigate(LibraryRoute()) { launchSingleTop = true }
            onLibraryOpened()
        }
    }
}

/** O ranking: as músicas e o placar de cada uma. */
private fun NavGraphBuilder.rankingDestinations(navController: NavHostController) {
    composable<RankingRoute> {
        RankingSongsScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onSong = dropUnlessResumed { song -> navController.navigate(RankingBoardRoute(song.chartId, song.title)) },
        )
    }
    composable<RankingBoardRoute> {
        RankingBoardScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
}

/** A partida e o resultado. */
private fun NavGraphBuilder.matchDestinations(navController: NavHostController, onChooseName: () -> Unit) {
    composable<GameplayRoute> { entry ->
        val route = entry.toRoute<GameplayRoute>()
        GameplayScreen(
            onFinished = { scoreId ->
                navController.navigate(ResultsRoute(scoreId, route.spotifyTrackUri, route.trackOffsetMs)) {
                    popUpTo<GameplayRoute> { inclusive = true }
                }
            },
            onExit = dropUnlessResumed { navController.popBackStack() },
            // Segue para a próxima música: volta ao Tocando agora, que começa a próxima se ela tiver mapa.
            onNextSong = dropUnlessResumed { difficulty, fromTrackUri, waitMs ->
                navController.previousBackStackEntry?.savedStateHandle?.let {
                    NowPlayingAutoPlay.request(it, difficulty, fromTrackUri, waitMs)
                }
                navController.popBackStack()
            },
            // Painel de confirmação: a escolha de mapa da biblioteca para esta faixa, no lugar da partida.
            onChooseOther = dropUnlessResumed { trackUri, title ->
                navController.navigate(LibraryRoute(matchTrackUri = trackUri, matchTrackTitle = title)) {
                    popUpTo<GameplayRoute> { inclusive = true }
                }
            },
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
            onChooseName = onChooseName,
            onSeeRanking = dropUnlessResumed { chartId, title, difficulty ->
                navController.navigate(RankingBoardRoute(chartId, title, difficulty.name))
            },
        )
    }
}

/** A Biblioteca: jogar com o áudio da pasta ou escolher o mapa de uma música do Spotify. */
private fun NavGraphBuilder.libraryDestinations(navController: NavHostController, back: BackNavigation) {
    composable<LibraryRoute> { entry ->
        val route = entry.toRoute<LibraryRoute>()
        LibraryScreen(
            onPlay = dropUnlessResumed { chartId, difficulty ->
                navController.navigate(GameplayRoute(chartId, difficulty.name))
            },
            onBack = dropUnlessResumed { navController.popBackStack() },
            match = route.matchTrackUri?.let { TrackToMatch(it, route.matchTrackTitle.orEmpty()) },
            // Escolher o mapa já soou como confirmação: a volta automática fica em silêncio.
            onMatched = dropUnlessResumed {
                back.silenceNextReturn()
                navController.popBackStack()
            },
            onSearch = dropUnlessResumed { match ->
                navController.navigate(SearchRoute(matchTrackUri = match?.trackUri, matchTrackTitle = match?.title))
            },
        )
    }
}

/** Pesquisa no Encore, aberta pela Biblioteca ou pelo Tocando agora. */
private fun NavGraphBuilder.searchDestinations(navController: NavHostController, back: BackNavigation) {
    composable<SearchRoute> {
        SearchScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onPlay = dropUnlessResumed { chartId, difficulty ->
                navController.navigate(GameplayRoute(chartId, difficulty.name))
            },
            // Baixar já soou como confirmação: a volta automática ao Tocando agora fica em silêncio.
            onMatched = dropUnlessResumed {
                back.silenceNextReturn()
                navController.popBackStack(NowPlayingRoute, inclusive = false)
            },
        )
    }
}

/** Configurações e calibração, abertas a partir de "Tocando agora". */
private fun NavGraphBuilder.settingsDestinations(
    navController: NavHostController,
    back: BackNavigation,
    onRankingName: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onCalibrate = dropUnlessResumed { navController.navigate(CalibrationRoute) },
            onRankingName = onRankingName,
        )
    }
    composable<ReportRoute> {
        ReportScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
    composable<CalibrationRoute> {
        CalibrationScreen(
            onDone = dropUnlessResumed { navController.popBackStack() },
            // Salvar já soou como confirmação: a volta para as Configurações fica em silêncio.
            onSaved = dropUnlessResumed {
                back.silenceNextReturn()
                navController.popBackStack()
            },
        )
    }
}
