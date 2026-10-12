package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.rodrigomachado.riff.core.ui.R
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

object StatusViewsTestTags {
    const val RETRY = "status_views_retry"
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    CenteredColumn(modifier) {
        CircularProgressIndicator(color = RiffColors.Paper)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = RiffColors.PaperDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CenteredColumn(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = RiffColors.Paper, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = RiffColors.PaperDim,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            PosterButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    CenteredColumn(modifier) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.PaperDim, // spec da identidade visual: mensagem em papel apagado
            textAlign = TextAlign.Center,
        )
        PosterButton(
            stringResource(R.string.core_ui_retry),
            onClick = onRetry,
            modifier = Modifier.testTag(StatusViewsTestTags.RETRY),
        )
    }
}

@Composable
private fun CenteredColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
