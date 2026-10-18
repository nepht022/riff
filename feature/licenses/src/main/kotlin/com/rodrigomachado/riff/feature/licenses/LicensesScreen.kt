package com.rodrigomachado.riff.feature.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: LicensesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LicensesContent(state, onBack, modifier)
}

/** O que o app guarda e envia, e as licenças de tudo que vem de fora (tocar num item mostra o texto). */
@Composable
fun LicensesContent(state: LicensesUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val contentTitle = stringResource(R.string.licenses_content)
    val librariesTitle = stringResource(R.string.licenses_libraries)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Sem som aqui: a navegação toca o de voltar ao sair da tela.
        item { TextButton(onClick = onBack) { Text(stringResource(R.string.licenses_back), color = RiffColors.Paper) } }
        item { PosterTitle(stringResource(R.string.licenses_title), style = MaterialTheme.typography.displaySmall) }
        item { Heading(stringResource(R.string.licenses_privacy)) }
        item {
            Text(
                stringResource(R.string.licenses_privacy_text),
                style = MaterialTheme.typography.bodyMedium,
                color = RiffColors.Paper,
            )
        }
        entries(contentTitle, state.content)
        entries(librariesTitle, state.libraries)
    }
}

private fun LazyListScope.entries(title: String, list: List<LicenseEntry>) {
    if (list.isEmpty()) return
    item { Heading(title) }
    items(list, key = { "$title/${it.name}" }) { Entry(it) }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = RiffColors.Paper)
}

@Composable
private fun Entry(entry: LicenseEntry) {
    var open by rememberSaveable(entry.name) { mutableStateOf(false) }
    val text = entry.text
    Column(
        Modifier.fillMaxWidth().background(RiffColors.Paper, MaterialTheme.shapes.small)
            .then(
                if (text == null) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = withMenuSound(MenuSound.NAVIGATE) { open = !open })
                },
            )
            .padding(Spacing.md),
    ) {
        Text(entry.name, style = MaterialTheme.typography.titleMedium, color = RiffColors.Ink)
        Text(
            listOf(entry.author, entry.license).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = RiffColors.Ink.copy(alpha = SECONDARY_ALPHA),
        )
        if (open && text != null) {
            Text(
                text,
                Modifier.padding(top = Spacing.sm),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = RiffColors.Ink,
            )
        }
    }
}

private const val SECONDARY_ALPHA = 0.7f
