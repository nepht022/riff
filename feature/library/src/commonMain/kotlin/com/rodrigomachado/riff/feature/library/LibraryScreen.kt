package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportFailureKind
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.KeyHintBar
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.rememberMenuSound
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_access_lost
import com.rodrigomachado.riff.feature.library.resources.library_chip_none
import com.rodrigomachado.riff.feature.library.resources.library_choose_another_folder
import com.rodrigomachado.riff.feature.library.resources.library_choose_folder
import com.rodrigomachado.riff.feature.library.resources.library_empty_message
import com.rodrigomachado.riff.feature.library.resources.library_empty_title
import com.rodrigomachado.riff.feature.library.resources.library_failure
import com.rodrigomachado.riff.feature.library.resources.library_failure_duplicate
import com.rodrigomachado.riff.feature.library.resources.library_failure_invalid_chart
import com.rodrigomachado.riff.feature.library.resources.library_failure_no_access
import com.rodrigomachado.riff.feature.library.resources.library_failure_too_large
import com.rodrigomachado.riff.feature.library.resources.library_failure_unexpected
import com.rodrigomachado.riff.feature.library.resources.library_failure_unreadable
import com.rodrigomachado.riff.feature.library.resources.library_failure_with_detail
import com.rodrigomachado.riff.feature.library.resources.library_filter_none
import com.rodrigomachado.riff.feature.library.resources.library_import_failed
import com.rodrigomachado.riff.feature.library.resources.library_importing
import com.rodrigomachado.riff.feature.library.resources.library_no_folder_message
import com.rodrigomachado.riff.feature.library.resources.library_no_folder_title
import com.rodrigomachado.riff.feature.library.resources.library_pick_title
import com.rodrigomachado.riff.feature.library.resources.library_report_failed
import com.rodrigomachado.riff.feature.library.resources.library_report_imported
import com.rodrigomachado.riff.feature.library.resources.library_report_more
import com.rodrigomachado.riff.feature.library.resources.library_report_ok
import com.rodrigomachado.riff.feature.library.resources.library_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Etiquetas para testes e para os percursos automáticos de desempenho (`:baselineprofile`). */
object LibraryTestTags {
    const val LIST = "library_list"
    const val FILTER = "library_filter"

    /** A barra de teclas do PC. */
    const val KEY_HINTS = "library_key_hints"
}

@Composable
fun LibraryScreen(
    onPlay: (chartId: String, difficulty: Difficulty) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Pesquisar no Encore. */
    onSearch: () -> Unit = {},
    /** O modo de escolha da sala: [onPlay] devolve o mapa e a dificuldade em vez de jogar (ver [LibraryPickRoute]). */
    pickForRoom: Boolean = false,
    viewModel: LibraryViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickFolder = rememberFolderPicker { uri ->
        if (uri != null) viewModel.onFolderPicked(uri) else viewModel.onFolderPickCancelled()
    }
    LaunchedEffect(state.play) {
        state.play?.let {
            onPlay(it.chartId, it.difficulty)
            viewModel.onPlayHandled()
        }
    }
    // A notificação mostra o progresso; sem a permissão (Android 13+), a separação roda do mesmo jeito.
    val separate = rememberNotificationPermissionThen(viewModel::onSeparate)
    val separateSelected = rememberNotificationPermissionThen(viewModel::onSeparateSelected)
    LibraryContent(
        state = state,
        onPickFolder = pickFolder,
        onRescan = viewModel::onRescan,
        onReportDismissed = viewModel::onReportDismissed,
        onPlay = onPlay,
        onBack = onBack,
        modifier = modifier,
        onSearch = onSearch,
        onDifficultyChosen = viewModel::onDifficultyChosen,
        pickForRoom = pickForRoom,
        onSeparate = separate,
        onPlayWithoutSeparating = viewModel::onPlayWithoutSeparating,
        onPromptDismissed = viewModel::onPromptDismissed,
        images = viewModel.images,
        deletion = DeletionCallbacks(
            onDelete = { viewModel.onDeleteRequested(listOf(it)) },
            onStartSelecting = viewModel::onStartSelecting,
            onToggle = viewModel::onToggleSelected,
            onCancelSelecting = viewModel::onCancelSelecting,
            onToggleAll = viewModel::onToggleAll,
            onDeleteSelected = viewModel::onDeleteSelected,
            onSeparateSelected = separateSelected,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed,
            onFailureShown = viewModel::onDeletionFailureShown,
        ),
    )
}

@Composable
@Suppress("LongParameterList") // o estado, as capas e o que cada botão da tela faz
fun LibraryContent(
    state: LibraryUiState,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onReportDismissed: () -> Unit,
    onPlay: (chartId: String, difficulty: Difficulty) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    onDifficultyChosen: (Chart, Difficulty) -> Unit = { chart, difficulty -> onPlay(chart.id, difficulty) },
    onSeparate: () -> Unit = {},
    onPlayWithoutSeparating: () -> Unit = {},
    onPromptDismissed: () -> Unit = {},
    deletion: DeletionCallbacks = DeletionCallbacks(),
    /**
     * O modo de escolha da sala: a dificuldade escolhida vai direto para [onPlay] (sem a pergunta da separação, que é
     * para jogar agora), o título é "Escolher música" e não há "+ Mapas".
     */
    pickForRoom: Boolean = false,
    /** As capas dos cartões (sem elas, as iniciais). */
    images: ChartArtImages? = null,
) {
    // Guarda o id (sobrevive à rotação da tela); o mapa vem da lista atual.
    var choosingId by rememberSaveable { mutableStateOf<String?>(null) }
    val choosing = choosingId?.let { id -> state.charts.firstOrNull { it.id == id } }
    val playMenuSound = rememberMenuSound()
    var filter by rememberSaveable { mutableStateOf("") }
    var chip by rememberSaveable { mutableStateOf(LibraryChip.ALL) }
    val clearFilter = Modifier.escapeClosesFilter(filter.isNotEmpty()) { filter = "" }
    val pad = LocalGamepad.current
    val menu = LibraryMenuActions(
        onRescan = onRescan.takeIf { state.hasFolder },
        onPickFolder = onPickFolder,
        onSelect = deletion.onStartSelecting.takeIf { state.charts.isNotEmpty() && !state.deletion.selecting },
    )
    BoxWithConstraints(clearFilter.then(modifier).fillMaxSize()) {
        // No PC (janela larga) a coluna fica no meio, sem esticar os cartões de ponta a ponta.
        // O widthIn vem antes do fillMaxWidth: estica até o máximo do PC, nunca além.
        val width = if (maxWidth >= WIDE_MIN_WIDTH) Modifier.widthIn(max = WIDE_CONTENT) else Modifier
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).align(Alignment.CenterHorizontally).then(width).fillMaxWidth()
                    .padding(horizontal = Spacing.md),
            ) {
                LibraryTop(onBack, menu)
                TitleRow(pickForRoom, onSearch)
                LibraryBody(state, onPickFolder, onRescan) {
                    val list = ListFilter(filter, chip, onFilter = { filter = it }, onChip = { chip = it })
                    ChartList(state, list, images, onReportDismissed, deletion) { chart ->
                        playMenuSound(MenuSound.NAVIGATE)
                        choosingId = chart.id
                    }
                }
            }
            if (pad != null) {
                val connected by pad.connected.collectAsState()
                KeyHintBar(libraryKeys(), connected, Modifier.testTag(LibraryTestTags.KEY_HINTS))
            }
        }
    }
    choosing?.let { chart ->
        val chosen = if (pickForRoom) { picked: Chart, d: Difficulty -> onPlay(picked.id, d) } else onDifficultyChosen
        DifficultyChoice(chart, onClose = { choosingId = null }, onChosen = chosen)
    }
    state.prompt?.let { SeparationPrompt(it, onSeparate, onPlayWithoutSeparating, onPromptDismissed) }
    DeletionDialogs(state.deletion, deletion, onAllow = onPickFolder)
}

/** O título ("Biblioteca" ou, na sala, "Escolher música") e, fora da sala, o "+ Mapas" (a pesquisa no Encore). */
@Composable
private fun TitleRow(pickForRoom: Boolean, onSearch: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PosterTitle(
            stringResource(if (pickForRoom) Res.string.library_pick_title else Res.string.library_title),
            Modifier.weight(1f),
            style = MaterialTheme.typography.displaySmall,
        )
        if (!pickForRoom) MoreChartsButton(onSearch)
    }
}

/** O filtro de texto e o chip escolhido (os dois guardados pela tela), e como mudá-los. */
private class ListFilter(
    val text: String,
    val chip: LibraryChip,
    val onFilter: (String) -> Unit,
    val onChip: (LibraryChip) -> Unit,
)

/** Carregando, erro, sem pasta, importando, vazia — ou a lista ([list]). */
@Composable
private fun LibraryBody(
    state: LibraryUiState,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    list: @Composable () -> Unit,
) {
    when {
        state.isLoading -> LoadingState()

        state.folderAccessLost -> LibraryError(stringResource(Res.string.library_access_lost), onPickFolder)

        state.importFailed -> LibraryError(stringResource(Res.string.library_import_failed), onRescan)

        !state.hasFolder -> EmptyState(
            title = stringResource(Res.string.library_no_folder_title),
            message = stringResource(Res.string.library_no_folder_message),
            actionLabel = stringResource(Res.string.library_choose_folder),
            onAction = onPickFolder,
            actionSound = MenuSound.NAVIGATE,
        )

        state.isImporting -> LoadingState(
            message = stringResource(Res.string.library_importing, state.scannedFolders),
        )

        state.charts.isEmpty() && state.report?.failures.isNullOrEmpty() -> EmptyState(
            title = stringResource(Res.string.library_empty_title),
            message = stringResource(Res.string.library_empty_message),
            actionLabel = stringResource(Res.string.library_choose_another_folder),
            onAction = onPickFolder,
            actionSound = MenuSound.NAVIGATE,
        )

        else -> list()
    }
}

/** Os erros da biblioteca levam a escolher a pasta ou reimportar: navegar, não confirmar. */
@Composable
private fun LibraryError(message: String, onRetry: () -> Unit) {
    ErrorState(message, onRetry, retrySound = MenuSound.NAVIGATE)
}

/**
 * O filtro, os chips e, rolando com eles (a fonte grande não espreme a lista), a seleção, o aviso da importação e os
 * cartões. Só a lista filtrada é calculada aqui; o resto do estado (seleção, importação, aviso) é sempre o atual.
 */
@Composable
@Suppress("LongParameterList") // o estado, o filtro, as capas e o que a lista faz
private fun ChartList(
    state: LibraryUiState,
    filter: ListFilter,
    images: ChartArtImages?,
    onReportDismissed: () -> Unit,
    deletion: DeletionCallbacks,
    onChartClick: (Chart) -> Unit,
) {
    val charts = remember(state.charts, state.aiSeparated, state.bests, filter.text, filter.chip) {
        state.charts.matching(filter.text).filter { filter.chip.accepts(it, state) }
    }
    LazyColumn(
        modifier = Modifier.testTag(LibraryTestTags.LIST),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item(key = "filter") { FilterField(filter.text, filter.onFilter) }
        item(key = "chips") { LibraryChips(filter.chip, state.charts.size, filter.onChip) }
        if (state.deletion.selecting) {
            item(key = "selection") {
                // O "todos" é o que está à mostra (filtro e chip), na caixa e ao tocar nela.
                val deletable = charts.filter { it.id !in state.protectedIds }
                val allSelected = deletable.isNotEmpty() && deletable.all { it.id in state.deletion.selected }
                val visible = charts.map { it.id }
                SelectionActions(state.deletion.selected.size, state.separableSelected, visible, allSelected, deletion)
            }
        }
        state.report?.let { item(key = "report") { ImportReportCard(it, onReportDismissed) } }
        if (charts.isEmpty() && state.charts.isNotEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(
                        if (filter.text.isNotBlank()) Res.string.library_filter_none else Res.string.library_chip_none,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = RiffColors.PaperDim,
                )
            }
        }
        items(charts, key = { it.id }) { chart ->
            DeletableChart(
                title = chart.title,
                chartId = chart.id,
                selecting = state.deletion.selecting,
                selected = chart.id in state.deletion.selected,
                canDelete = chart.id !in state.protectedIds,
                callbacks = deletion,
            ) { modifier, onClickOverride ->
                LibraryCard(
                    chart = chart,
                    best = state.bests[chart.id],
                    aiSeparated = chart.id in state.aiSeparated,
                    images = images,
                    onClick = onClickOverride ?: { onChartClick(chart) },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun ImportReportCard(report: ImportReport, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(RiffColors.Paper, MaterialTheme.shapes.small).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            pluralStringResource(Res.plurals.library_report_imported, report.importedCount, report.importedCount),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Ink,
        )
        if (report.failures.isNotEmpty()) {
            Text(
                pluralStringResource(Res.plurals.library_report_failed, report.failures.size, report.failures.size),
                style = MaterialTheme.typography.titleSmall,
                color = RiffColors.StampError,
            )
            // Até 10 no cartão: uma pasta com centenas de mapas quebrados não vira um cartão sem fim.
            // Os erros que pedem ação vêm antes das cópias repetidas, para não ficarem escondidos no "e mais".
            report.failures.sortedBy { it.kind == ImportFailureKind.DUPLICATE }.take(
                MAX_FAILURES_SHOWN,
            ).forEach { failure ->
                Text(failureText(failure), style = MaterialTheme.typography.bodySmall, color = RiffColors.Ink)
            }
            val more = report.failures.size - MAX_FAILURES_SHOWN
            if (more > 0) {
                Text(
                    pluralStringResource(Res.plurals.library_report_more, more, more),
                    style = MaterialTheme.typography.bodySmall,
                    color = RiffColors.Ink,
                )
            }
        }
        PosterButton(stringResource(Res.string.library_report_ok), onClick = onDismiss, sound = MenuSound.NAVIGATE)
    }
}

private const val MAX_FAILURES_SHOWN = 10

/** "Pasta: motivo", com o motivo no idioma do aparelho e o detalhe técnico só quando ajuda (linha do erro no mapa). */
@Composable
private fun failureText(failure: ImportFailure): String {
    val label = when (failure.kind) {
        ImportFailureKind.INVALID_CHART -> stringResource(Res.string.library_failure_invalid_chart)
        ImportFailureKind.FILE_TOO_LARGE -> stringResource(Res.string.library_failure_too_large)
        ImportFailureKind.UNREADABLE -> stringResource(Res.string.library_failure_unreadable)
        ImportFailureKind.NO_ACCESS -> stringResource(Res.string.library_failure_no_access)
        ImportFailureKind.UNEXPECTED -> stringResource(Res.string.library_failure_unexpected)
        ImportFailureKind.DUPLICATE -> stringResource(Res.string.library_failure_duplicate, failure.detail.orEmpty())
    }
    val showDetail = failure.kind == ImportFailureKind.INVALID_CHART || failure.kind == ImportFailureKind.UNEXPECTED
    val detail = failure.detail?.takeIf { showDetail }
    return if (detail != null) {
        stringResource(Res.string.library_failure_with_detail, failure.folderName, label, detail)
    } else {
        stringResource(Res.string.library_failure, failure.folderName, label)
    }
}

/** A partir desta largura, o layout do PC (o celular deitado, ~780 dp, continua no do celular). */
private val WIDE_MIN_WIDTH = 800.dp

/** A largura máxima da coluna no PC. */
private val WIDE_CONTENT = 760.dp
