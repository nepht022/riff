package com.rodrigomachado.riff.feature.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportFailureKind
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.SeparationIndicator
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.component.rememberMenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Etiquetas para testes e para os percursos automáticos de desempenho (`:baselineprofile`). */
object LibraryTestTags {
    const val LIST = "library_list"
    const val FILTER = "library_filter"
}

@Composable
fun LibraryScreen(
    onPlay: (chartId: String, difficulty: Difficulty) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Pesquisar no Encore. */
    onSearch: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri.toString()) else viewModel.onFolderPickCancelled()
    }
    LaunchedEffect(state.play) {
        state.play?.let {
            onPlay(it.chartId, it.difficulty)
            viewModel.onPlayHandled()
        }
    }
    // A notificação mostra o progresso; sem a permissão (Android 13+), a separação roda do mesmo jeito.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onSeparate()
    }
    val askNotificationsForSelection =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onSeparateSelected()
        }
    LibraryContent(
        state = state,
        onPickFolder = { pickFolder.launch(null) },
        onRescan = viewModel::onRescan,
        onReportDismissed = viewModel::onReportDismissed,
        onPlay = onPlay,
        onBack = onBack,
        modifier = modifier,
        onSearch = onSearch,
        onDifficultyChosen = viewModel::onDifficultyChosen,
        onSeparate = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onSeparate()
            }
        },
        onPlayWithoutSeparating = viewModel::onPlayWithoutSeparating,
        onPromptDismissed = viewModel::onPromptDismissed,
        deletion = DeletionCallbacks(
            onDelete = { viewModel.onDeleteRequested(listOf(it)) },
            onStartSelecting = viewModel::onStartSelecting,
            onToggle = viewModel::onToggleSelected,
            onCancelSelecting = viewModel::onCancelSelecting,
            onToggleAll = viewModel::onToggleAll,
            onDeleteSelected = viewModel::onDeleteSelected,
            onSeparateSelected = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotificationsForSelection.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onSeparateSelected()
                }
            },
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed,
            onFailureShown = viewModel::onDeletionFailureShown,
        ),
    )
}

@Composable
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
) {
    // Guarda o id (sobrevive à rotação da tela); o mapa vem da lista atual.
    var choosingId by rememberSaveable { mutableStateOf<String?>(null) }
    val choosing = choosingId?.let { id -> state.charts.firstOrNull { it.id == id } }
    val playMenuSound = rememberMenuSound()
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        LibraryHeader(onBack, onSearch)
        var filtering by rememberSaveable { mutableStateOf(false) }
        var filter by rememberSaveable { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PosterTitle(
                stringResource(R.string.library_title),
                Modifier.weight(1f),
                style = MaterialTheme.typography.displaySmall,
            )
            MagnifierButton(stringResource(R.string.library_filter_open)) {
                filtering = !filtering
                if (!filtering) filter = ""
            }
        }
        if (filtering) FilterField(filter) { filter = it }
        // Só a lista filtrada fica guardada; o resto do estado (seleção, importação, aviso) é sempre o atual.
        val charts = remember(state.charts, filter) { state.charts.matching(filter) }
        val shown = state.copy(charts = charts)
        if (filter.isNotBlank() && shown.charts.isEmpty() && state.charts.isNotEmpty()) {
            Text(
                stringResource(R.string.library_filter_none),
                Modifier.padding(vertical = Spacing.md),
                style = MaterialTheme.typography.bodyLarge,
                color = RiffColors.PaperDim,
            )
            return@Column
        }
        LibraryBody(shown, onPickFolder, onRescan) {
            ChartList(shown, onPickFolder, onRescan, onReportDismissed, deletion) { chart ->
                playMenuSound(MenuSound.NAVIGATE)
                choosingId = chart.id
            }
        }
    }
    choosing?.let { chart ->
        DifficultyChoice(chart, onClose = { choosingId = null }, onChosen = onDifficultyChosen)
    }
    state.prompt?.let { SeparationPrompt(it, onSeparate, onPlayWithoutSeparating, onPromptDismissed) }
    DeletionDialogs(state.deletion, deletion, onAllow = onPickFolder)
}

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

        state.folderAccessLost -> LibraryError(stringResource(R.string.library_access_lost), onPickFolder)

        state.importFailed -> LibraryError(stringResource(R.string.library_import_failed), onRescan)

        !state.hasFolder -> EmptyState(
            title = stringResource(R.string.library_no_folder_title),
            message = stringResource(R.string.library_no_folder_message),
            actionLabel = stringResource(R.string.library_choose_folder),
            onAction = onPickFolder,
            actionSound = MenuSound.NAVIGATE,
        )

        state.isImporting -> LoadingState(
            message = stringResource(R.string.library_importing, state.scannedFolders),
        )

        state.charts.isEmpty() && state.report?.failures.isNullOrEmpty() -> EmptyState(
            title = stringResource(R.string.library_empty_title),
            message = stringResource(R.string.library_empty_message),
            actionLabel = stringResource(R.string.library_choose_another_folder),
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

/** Voltar e Pesquisar (no Encore): sempre à mão, até com a biblioteca vazia; a palheta da separação no meio. */
@Composable
private fun LibraryHeader(onBack: () -> Unit, onSearch: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Text(stringResource(R.string.library_back), color = RiffColors.Paper)
        }
        SeparationIndicator(Modifier.align(Alignment.Center))
        TextButton(
            onClick = withMenuSound(MenuSound.NAVIGATE, onSearch),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Text(stringResource(R.string.library_search), color = RiffColors.Paper)
        }
    }
}

@Composable
private fun ChartList(
    state: LibraryUiState,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onReportDismissed: () -> Unit,
    deletion: DeletionCallbacks,
    onChartClick: (Chart) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.testTag(LibraryTestTags.LIST),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item {
            if (state.deletion.selecting) {
                val deletable = state.charts.filter { it.id !in state.protectedIds }
                val allSelected = deletable.isNotEmpty() && deletable.all { it.id in state.deletion.selected }
                SelectionActions(state.deletion.selected.size, state.separableSelected, allSelected, deletion)
            } else {
                ListActions(onRescan, onPickFolder, deletion.onStartSelecting)
            }
        }
        state.report?.let { item { ImportReportCard(it, onReportDismissed) } }
        items(state.charts, key = { it.id }) { chart ->
            DeletableChart(
                title = chart.title,
                chartId = chart.id,
                selecting = state.deletion.selecting,
                selected = chart.id in state.deletion.selected,
                canDelete = chart.id !in state.protectedIds,
                callbacks = deletion,
            ) { modifier, onClickOverride ->
                TrackCard(
                    title = chart.title,
                    artist = chart.artist.ifBlank { stringResource(R.string.library_unknown_artist) },
                    modifier = modifier,
                    onClick = onClickOverride ?: { onChartClick(chart) },
                    badge = { ChartBadges(chart, aiSeparated = chart.id in state.aiSeparated) },
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
            pluralStringResource(R.plurals.library_report_imported, report.importedCount, report.importedCount),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Ink,
        )
        if (report.failures.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.library_report_failed, report.failures.size, report.failures.size),
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
                    pluralStringResource(R.plurals.library_report_more, more, more),
                    style = MaterialTheme.typography.bodySmall,
                    color = RiffColors.Ink,
                )
            }
        }
        PosterButton(stringResource(R.string.library_report_ok), onClick = onDismiss, sound = MenuSound.NAVIGATE)
    }
}

private const val MAX_FAILURES_SHOWN = 10

/** "Pasta: motivo", com o motivo no idioma do aparelho e o detalhe técnico só quando ajuda (linha do erro no mapa). */
@Composable
private fun failureText(failure: ImportFailure): String {
    val label = when (failure.kind) {
        ImportFailureKind.INVALID_CHART -> stringResource(R.string.library_failure_invalid_chart)
        ImportFailureKind.FILE_TOO_LARGE -> stringResource(R.string.library_failure_too_large)
        ImportFailureKind.UNREADABLE -> stringResource(R.string.library_failure_unreadable)
        ImportFailureKind.NO_ACCESS -> stringResource(R.string.library_failure_no_access)
        ImportFailureKind.UNEXPECTED -> stringResource(R.string.library_failure_unexpected)
        ImportFailureKind.DUPLICATE -> stringResource(R.string.library_failure_duplicate, failure.detail.orEmpty())
    }
    val showDetail = failure.kind == ImportFailureKind.INVALID_CHART || failure.kind == ImportFailureKind.UNEXPECTED
    val detail = failure.detail?.takeIf { showDetail }
    return if (detail != null) {
        stringResource(R.string.library_failure_with_detail, failure.folderName, label, detail)
    } else {
        stringResource(R.string.library_failure, failure.folderName, label)
    }
}
