package com.rodrigomachado.riff.feature.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.licenses.resources.Res
import com.rodrigomachado.riff.feature.licenses.resources.licenses_back
import com.rodrigomachado.riff.feature.licenses.resources.licenses_content
import com.rodrigomachado.riff.feature.licenses.resources.licenses_libraries
import com.rodrigomachado.riff.feature.licenses.resources.licenses_privacy
import com.rodrigomachado.riff.feature.licenses.resources.licenses_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: LicensesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LicensesContent(state, onBack, modifier)
}

/** O que o app guarda e envia, e as licenças de tudo que vem de fora (tocar num item mostra o texto). */
@Composable
fun LicensesContent(state: LicensesUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val contentTitle = stringResource(Res.string.licenses_content)
    val librariesTitle = stringResource(Res.string.licenses_libraries)
    // Os abertos ficam aqui: o texto de cada um vira itens da lista, desenhados só quando aparecem.
    var opened by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val toggle = { key: String -> opened = if (key in opened) opened - key else opened + key }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Sem som aqui: a navegação toca o de voltar ao sair da tela.
        item {
            TextButton(onClick = onBack, modifier = Modifier.focusHighlight(CircleShape)) {
                Text(stringResource(Res.string.licenses_back), color = RiffColors.Paper)
            }
        }
        item { PosterTitle(stringResource(Res.string.licenses_title), style = MaterialTheme.typography.displaySmall) }
        item { Heading(stringResource(Res.string.licenses_privacy)) }
        item {
            Text(
                stringResource(privacyText),
                style = MaterialTheme.typography.bodyMedium,
                color = RiffColors.Paper,
            )
        }
        entries(contentTitle, state.content, opened, toggle)
        entries(librariesTitle, state.libraries, opened, toggle)
    }
}

private fun LazyListScope.entries(
    title: String,
    list: List<LicenseEntry>,
    opened: List<String>,
    toggle: (String) -> Unit,
) {
    if (list.isEmpty()) return
    item { Heading(title) }
    for ((index, entry) in list.withIndex()) {
        // Com a posição: no PC há nomes repetidos (o androidx e o fork da JetBrains, como "Lifecycle Runtime").
        val key = "$title/$index/${entry.name}"
        val open = key in opened
        item(key = key) { Entry(entry) { toggle(key) } }
        val text = entry.text
        if (open && text != null) {
            // Por parágrafo: o aviso do ONNX Runtime tem ~330 KB e travaria a tela num Text só.
            val paragraphs = text.split(PARAGRAPH_BREAK)
            items(paragraphs.size, key = { "$key/$it" }) { LicenseParagraph(paragraphs[it]) }
        }
    }
}

@Composable
private fun LicenseParagraph(text: String) {
    Text(
        text,
        // Puxado para cima, sobre o vão da lista: os parágrafos formam um bloco de papel só, abaixo do cartão.
        Modifier.fillMaxWidth().offset(y = -Spacing.md).background(RiffColors.Paper)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = RiffColors.Ink,
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = RiffColors.Paper)
}

@Composable
private fun Entry(entry: LicenseEntry, onToggle: () -> Unit) {
    val text = entry.text
    Column(
        Modifier.fillMaxWidth().background(RiffColors.Paper, MaterialTheme.shapes.small)
            .then(
                if (text == null) {
                    Modifier
                } else {
                    // No PC o item com texto é focado pelas setas e abre com o Enter.
                    Modifier.focusHighlight(MaterialTheme.shapes.small)
                        .clickable(onClick = withMenuSound(MenuSound.NAVIGATE, onToggle))
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
    }
}

private const val SECONDARY_ALPHA = 0.7f

/** Linha em branco entre parágrafos, também com o fim de linha do Windows. */
private val PARAGRAPH_BREAK = Regex("\r?\n\r?\n")
