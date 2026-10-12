package com.rodrigomachado.riff.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.DifficultyDialog
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.component.difficultyLabel
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Etiquetas para testes e para os percursos automáticos de desempenho (`:baselineprofile`). */
object LibraryTestTags {
    const val LIST = "library_list"
}

@Composable
fun LibraryScreen(
    onPlay: (chartId: String, difficulty: Difficulty) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    match: TrackToMatch? = null,
    onMatched: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri.toString()) else viewModel.onFolderPickCancelled()
    }
    if (state.matchSaved) LaunchedEffect(Unit) { onMatched() }
    LibraryContent(
        state = state,
        onPickFolder = { pickFolder.launch(null) },
        onRescan = viewModel::onRescan,
        onReportDismissed = viewModel::onReportDismissed,
        onPlay = onPlay,
        onBack = onBack,
        modifier = modifier,
        matchingTitle = match?.title,
        onChartMatched = { chartId -> match?.let { viewModel.chooseChartFor(it.trackUri, chartId) } },
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
    matchingTitle: String? = null,
    onChartMatched: (String) -> Unit = {},
) {
    // Guarda o id (sobrevive à rotação da tela); o mapa vem da lista atual.
    var choosingId by rememberSaveable { mutableStateOf<String?>(null) }
    val choosing = choosingId?.let { id -> state.charts.firstOrNull { it.id == id } }
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.library_back), color = RiffColors.Paper) }
        PosterTitle(
            stringResource(R.string.library_title),
            style = MaterialTheme.typography.displaySmall,
        )
        matchingTitle?.let { title ->
            Text(
                stringResource(R.string.library_match_title),
                style = MaterialTheme.typography.titleMedium,
                color = RiffColors.Paper,
            )
            Text(title, style = MaterialTheme.typography.bodyLarge, color = RiffColors.PaperDim)
        }
        when {
            state.isLoading -> LoadingState()

            state.folderAccessLost -> ErrorState(stringResource(R.string.library_access_lost), onRetry = onPickFolder)

            state.importFailed -> ErrorState(stringResource(R.string.library_import_failed), onRetry = onRescan)

            !state.hasFolder -> EmptyState(
                title = stringResource(R.string.library_no_folder_title),
                message = stringResource(R.string.library_no_folder_message),
                actionLabel = stringResource(R.string.library_choose_folder),
                onAction = onPickFolder,
            )

            state.isImporting -> LoadingState(
                message = stringResource(R.string.library_importing, state.scannedFolders),
            )

            state.charts.isEmpty() && state.report?.failures.isNullOrEmpty() -> EmptyState(
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_message),
                actionLabel = stringResource(R.string.library_choose_another_folder),
                onAction = onPickFolder,
            )

            else -> ChartList(state.charts, state.report, onPickFolder, onRescan, onReportDismissed) { chart ->
                if (matchingTitle != null) onChartMatched(chart.id) else choosingId = chart.id
            }
        }
    }
    choosing?.let { chart ->
        DifficultyDialog(
            title = chart.title,
            options = chart.difficulties.sorted(),
            label = { stringResource(difficultyLabel(it.ordinal)) },
            onChoose = { difficulty ->
                choosingId = null
                onPlay(chart.id, difficulty)
            },
            onDismiss = { choosingId = null },
        )
    }
}

@Composable
private fun ChartList(
    charts: List<Chart>,
    report: ImportReport?,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onReportDismissed: () -> Unit,
    onChartClick: (Chart) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.testTag(LibraryTestTags.LIST),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PosterButton(stringResource(R.string.library_rescan), onClick = onRescan)
                TextButton(onClick = onPickFolder) {
                    Text(stringResource(R.string.library_change_folder), color = RiffColors.Paper)
                }
            }
        }
        report?.let { item { ImportReportCard(it, onReportDismissed) } }
        items(charts, key = { it.id }) { chart ->
            TrackCard(
                title = chart.title,
                artist = chart.artist.ifBlank { stringResource(R.string.library_unknown_artist) },
                onClick = { onChartClick(chart) },
                badge = chart.difficulties.maxOrNull()?.let { highest -> difficultyBadge(highest) },
            )
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
            report.failures.forEach { failure ->
                Text(
                    "${failure.folderName}: ${failure.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiffColors.Ink,
                )
            }
        }
        PosterButton(stringResource(R.string.library_report_ok), onClick = onDismiss)
    }
}

/** Carimbo com a dificuldade mais alta do mapa. */
private fun difficultyBadge(difficulty: Difficulty): @Composable () -> Unit = {
    StatusBadge(stringResource(difficultyLabel(difficulty.ordinal)), BadgeTone.NEUTRAL)
}
