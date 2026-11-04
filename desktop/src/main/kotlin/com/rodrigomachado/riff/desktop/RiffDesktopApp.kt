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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.createSavedStateHandle
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
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.input.LocalPadMenuSuspension
import com.rodrigomachado.riff.core.ui.input.PadMenuSuspension
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.navigation.dropUnlessResumed
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.desktop.pad.PadMenuKeys
import com.rodrigomachado.riff.feature.calibration.CalibrationRoute
import com.rodrigomachado.riff.feature.calibration.CalibrationScreen
import com.rodrigomachado.riff.feature.gameplay.GameplayRoute
import com.rodrigomachado.riff.feature.gameplay.GameplayScreen
import com.rodrigomachado.riff.feature.home.HomeRoute
import com.rodrigomachado.riff.feature.home.HomeScreen
import com.rodrigomachado.riff.feature.library.LibraryRoute
import com.rodrigomachado.riff.feature.library.LibraryScreen
import com.rodrigomachado.riff.feature.licenses.LicensesRoute
import com.rodrigomachado.riff.feature.licenses.LicensesScreen
import com.rodrigomachado.riff.feature.report.ReportRoute
import com.rodrigomachado.riff.feature.report.ReportScreen
import com.rodrigomachado.riff.feature.results.ResultsRoute
import com.rodrigomachado.riff.feature.results.ResultsScreen
import com.rodrigomachado.riff.feature.search.SearchRoute
import com.rodrigomachado.riff.feature.search.SearchScreen
import com.rodrigomachado.riff.feature.settings.SettingsRoute
import com.rodrigomachado.riff.feature.settings.SettingsScreen
import com.rodrigomachado.riff.feature.settings.SettingsTab
import java.util.Locale

/**
 * O Riff no PC: tela inicial, Biblioteca, pesquisa no Encore, partida, resultado, Configurações, Reportar e Licenças;
 * o Ranking vem depois. Fora da partida (que tem as próprias teclas), cada tela anda pelo teclado ([menuKeys]): setas
 * movem o foco, Esc volta. O controle anda também, virando essas teclas ([PadMenuKeys]) que [sendMenuKey] entrega à
 * janela (nulo: o controle só vale na partida e na escolha de botão).
 */
@Composable
fun RiffDesktopApp(graph: DesktopGraph, sendMenuKey: ((awtKeyCode: Int) -> Unit)? = null) {
    val navController = rememberNavController()
    // Como no celular: a preferência é lida na hora do toque (mudar as Configurações não recompõe o app).
    val settings = graph.settings.settings.collectAsState(initial = null)
    val playMenuSound: (MenuSound) -> Unit = remember(graph) { { playMenuSound(graph.menuSounds, settings.value, it) } }
    LaunchedEffect(graph) {
        var previous: Settings? = null
        snapshotFlow { settings.value }.collect { current ->
            if (menuSoundsTurnedOn(previous, current)) playMenuSound(MenuSound.NAVIGATE)
            // Os botões escolhidos chegam à leitura do controle (a thread dele lê a cada leitura).
            current?.let { graph.gamepad.setBindings(it.controls.pad) }
            previous = current
        }
    }
    val padSuspension = remember { PadMenuSuspension() }
    if (sendMenuKey != null) PadOnTheMenus(graph, padSuspension, sendMenuKey)
    // Trocar o idioma refaz as telas já no idioma novo (os textos leem o Locale padrão), com a mesma pilha.
    val language by graph.languages.language.collectAsState()
    RiffTheme {
        CompositionLocalProvider(
            LocalMenuSound provides playMenuSound,
            LocalGamepad provides graph.gamepad,
            LocalPadMenuSuspension provides padSuspension,
        ) {
            key(language) { DesktopScreens(graph, navController) }
        }
    }
}

/**
 * Cada botão que desce, com a janela focada e sem quem o tire dos menus ([suspension]: a partida tocando, a escolha
 * de um botão), vira a tecla dos menus. A suspensão é vista na hora da descida, na coleta (que só repassa): o ✕ que
 * desceu com a partida tocando não vira um Enter depois, nem um botão segurado ao trocar de tela.
 */
@Composable
private fun PadOnTheMenus(graph: DesktopGraph, suspension: PadMenuSuspension, sendMenuKey: (awtKeyCode: Int) -> Unit) {
    val window = LocalWindowInfo.current
    LaunchedEffect(graph, suspension, sendMenuKey) {
        val keys = PadMenuKeys(sendMenuKey)
        graph.gamepad.presses.collect { button ->
            // O foco de agora: o GLFW lê o controle com a janela em segundo plano (Alt+Tab).
            if (window.isWindowFocused && suspension.allows(button)) keys.onPress(button)
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
                    onSettings = dropUnlessResumed { navController.navigate(SettingsRoute) },
                    onReport = dropUnlessResumed { navController.navigate(ReportRoute) },
                    // Na tela inicial o Esc não faz nada (fechar o jogo é pela janela).
                    modifier = Modifier.menuKeys(onBack = null),
                )
            }
            composable<LibraryRoute> {
                LibraryScreen(
                    onPlay = dropUnlessResumed { chartId, difficulty ->
                        navController.navigate(GameplayRoute(chartId, difficulty.name))
                    },
                    onBack = { navController.popBackStack() },
                    onSearch = dropUnlessResumed { navController.navigate(SearchRoute()) },
                    modifier = Modifier.menuKeys(onBack = dropUnlessResumed { navController.popBackStack() }),
                    viewModel = viewModel { graph.libraryViewModel() },
                )
            }
            searchDestination(graph, navController)
            matchDestinations(graph, navController)
            settingsDestinations(graph, navController)
            reportDestination(graph, navController)
        }
    }
}

/**
 * A pesquisa no Encore, aberta pela Biblioteca, como no celular: "Jogar" de um mapa baixado abre a partida por cima
 * da pesquisa (o Resultado volta a ela); Voltar e Esc voltam à Biblioteca, que já tem os mapas baixados.
 */
private fun NavGraphBuilder.searchDestination(graph: DesktopGraph, navController: NavHostController) {
    composable<SearchRoute> { entry ->
        val route = entry.toRoute<SearchRoute>()
        SearchScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onPlay = dropUnlessResumed { chartId, difficulty ->
                navController.navigate(GameplayRoute(chartId, difficulty.name))
            },
            modifier = Modifier.menuKeys(onBack = dropUnlessResumed { navController.popBackStack() }),
            viewModel = viewModel { graph.searchViewModel(route.query) },
        )
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
            modifier = Modifier.menuKeys(onBack = dropUnlessResumed { navController.popBackStack() }),
        )
    }
}

/**
 * As Configurações, a calibração e as Licenças, como no celular, sem o que no PC é da etapa 4: a aba da guitarra por
 * IA e o nome no ranking. No PC a volta não tem som (não há o `BackNavigation` do celular), então Salvar a calibração
 * só volta às Configurações.
 */
private fun NavGraphBuilder.settingsDestinations(graph: DesktopGraph, navController: NavHostController) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onCalibrate = dropUnlessResumed { navController.navigate(CalibrationRoute) },
            viewModel = viewModel { graph.settingsViewModel() },
            modifier = Modifier.menuKeys(onBack = dropUnlessResumed { navController.popBackStack() }),
            onRankingName = {},
            onLicenses = dropUnlessResumed { navController.navigate(LicensesRoute) },
            tabs = SettingsTab.entries - SettingsTab.AI,
        )
    }
    composable<CalibrationRoute> {
        CalibrationScreen(
            onDone = dropUnlessResumed { navController.popBackStack() },
            viewModel = viewModel { graph.calibrationViewModel() },
            // Medindo, o Esc fica na área de medição (para a medição); fora dela, volta.
            modifier = Modifier.menuKeys(onBack = dropUnlessResumed { navController.popBackStack() }),
        )
    }
    composable<LicensesRoute> {
        LicensesScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            modifier = Modifier.menuKeys(onBack = dropUnlessResumed { navController.popBackStack() }),
            viewModel = viewModel { graph.licensesViewModel() },
        )
    }
}

/**
 * O Reportar, aberto pelo envelope da tela inicial: o mesmo formulário e o mesmo script do celular. O que foi escrito
 * fica enquanto a tela existir (trocar o idioma não apaga). Esc volta; com a lista de categorias aberta, só a fecha.
 */
private fun NavGraphBuilder.reportDestination(graph: DesktopGraph, navController: NavHostController) {
    composable<ReportRoute> {
        ReportScreen(
            onBack = dropUnlessResumed { navController.popBackStack() },
            modifier = Modifier.menuKeys(onBack = dropUnlessResumed { navController.popBackStack() }),
            viewModel = viewModel { graph.reportViewModel(createSavedStateHandle()) },
        )
    }
}

/** Português, o idioma padrão (os testes voltam a ele; o jogo usa o escolhido, [DesktopAppLanguageStore]). */
object DesktopLocale {
    fun applyDefault() = Locale.setDefault(Locale.forLanguageTag("pt-BR"))
}
