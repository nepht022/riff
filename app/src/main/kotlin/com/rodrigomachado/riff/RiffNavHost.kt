package com.rodrigomachado.riff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.navigation.dropUnlessResumed
import com.rodrigomachado.riff.feature.calibration.CalibrationRoute
import com.rodrigomachado.riff.feature.calibration.CalibrationScreen
import com.rodrigomachado.riff.feature.gameplay.GameplayRoute
import com.rodrigomachado.riff.feature.gameplay.GameplayScreen
import com.rodrigomachado.riff.feature.home.HomeRoute
import com.rodrigomachado.riff.feature.home.HomeScreen
import com.rodrigomachado.riff.feature.library.HiltLibraryViewModel
import com.rodrigomachado.riff.feature.library.LibraryRoute
import com.rodrigomachado.riff.feature.library.LibraryScreen
import com.rodrigomachado.riff.feature.licenses.LicensesRoute
import com.rodrigomachado.riff.feature.licenses.LicensesScreen
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
        startDestination = HomeRoute,
        modifier = modifier,
        predictivePopEnterTransition = { RiffNavTransitions.backEnter },
        predictivePopExitTransition = { RiffNavTransitions.backExit },
    ) {
        homeDestination(navController)
        settingsDestinations(navController, back) { renamingPlayer = true }
        libraryDestinations(navController)
        rankingDestinations(navController)
        searchDestinations(navController)
        matchDestinations(navController) { renamingPlayer = true }
    }
    // O nome do ranking na primeira abertura, por cima da tela inicial ou da Biblioteca.
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
            navController.navigate(LibraryRoute) { launchSingleTop = true }
            onLibraryOpened()
        }
    }
}

/** A tela inicial: Sozinho abre a Biblioteca; Ranking, Configurações e Reportar. */
private fun NavGraphBuilder.homeDestination(navController: NavHostController) {
    composable<HomeRoute> {
        HomeScreen(
            onSolo = dropUnlessResumed { navController.navigate(LibraryRoute) },
            onRanking = dropUnlessResumed { navController.navigate(RankingRoute) },
            onSettings = dropUnlessResumed { navController.navigate(SettingsRoute) },
            onReport = dropUnlessResumed { navController.navigate(ReportRoute) },
        )
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
    composable<GameplayRoute> {
        GameplayScreen(
            onFinished = { scoreId ->
                navController.navigate(ResultsRoute(scoreId)) {
                    popUpTo<GameplayRoute> { inclusive = true }
                }
            },
            onExit = dropUnlessResumed { navController.popBackStack() },
        )
    }
    composable<ResultsRoute> {
        ResultsScreen(
            onPlayAgain = dropUnlessResumed { chartId, difficulty ->
                navController.navigate(GameplayRoute(chartId, difficulty.name)) {
                    popUpTo<ResultsRoute> { inclusive = true }
                }
            },
            onDone = dropUnlessResumed { navController.popBackStack() },
            onChooseName = onChooseName,
            onSeeRanking = dropUnlessResumed { chartId, title, difficulty ->
                navController.navigate(RankingBoardRoute(chartId, title, difficulty.name))
            },
        )
    }
}

/** A Biblioteca: jogar com o áudio da pasta. */
private fun NavGraphBuilder.libraryDestinations(navController: NavHostController) {
    composable<LibraryRoute> {
        LibraryScreen(
            onPlay = dropUnlessResumed { chartId, difficulty ->
                navController.navigate(GameplayRoute(chartId, difficulty.name))
            },
            onBack = dropUnlessResumed { navController.popBackStack() },
            onSearch = dropUnlessResumed { navController.navigate(SearchRoute()) },
            viewModel = hiltViewModel<HiltLibraryViewModel>(),
        )
    }
}

/** Pesquisa no Encore, aberta pela Biblioteca. */
private fun NavGraphBuilder.searchDestinations(navController: NavHostController) {
    composable<SearchRoute> {
        SearchScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onPlay = dropUnlessResumed { chartId, difficulty ->
                navController.navigate(GameplayRoute(chartId, difficulty.name))
            },
        )
    }
}

/** Configurações, licenças, Reportar e calibração, abertas a partir da tela inicial. */
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
            onLicenses = dropUnlessResumed { navController.navigate(LicensesRoute) },
        )
    }
    composable<LicensesRoute> {
        LicensesScreen(onBack = dropUnlessResumed { navController.popBackStack() })
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
