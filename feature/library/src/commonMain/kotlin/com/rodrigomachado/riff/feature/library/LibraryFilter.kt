package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeysEnabled
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_filter_hint
import org.jetbrains.compose.resources.stringResource
import java.text.Normalizer

/** Os mapas cuja música ou artista contém [filter], sem diferenciar maiúsculas nem acentos. */
internal fun List<Chart>.matching(filter: String): List<Chart> {
    val wanted = filter.folded()
    if (wanted.isEmpty()) return this
    return filter { it.title.folded().contains(wanted) || it.artist.folded().contains(wanted) }
}

/**
 * Nos menus do PC, Esc com o filtro aberto ([filtering]) fecha o filtro ([onClose]) em vez de voltar de tela, com o
 * foco onde estiver na Biblioteca. Vem antes do [com.rodrigomachado.riff.core.ui.input.menuKeys] da tela (a prévia
 * desce da raiz). No celular é `this`.
 */
internal fun Modifier.escapeClosesFilter(filtering: Boolean, onClose: () -> Unit): Modifier = if (!menuKeysEnabled) {
    this
} else {
    // Sempre na corrente (só a condição muda): abrir o filtro não refaz os nós da tela, nem o foco dela.
    onPreviewKeyEvent { event ->
        if (!filtering || event.key != Key.Escape) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) onClose()
        true
    }
}

private val ACCENTS = Regex("\\p{Mn}+")

private fun String.folded() = Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()

/** Lupa desenhada aqui (nada de ícone de biblioteca de terceiros): um aro e o cabo. */
@Composable
internal fun MagnifierButton(description: String, onClick: () -> Unit) {
    IconButton(
        onClick = withMenuSound(MenuSound.NAVIGATE, onClick),
        modifier = Modifier.semantics { contentDescription = description }.focusHighlight(CircleShape),
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.5.dp.toPx()
            val radius = size.width * 0.32f
            val center = Offset(size.width * 0.42f, size.height * 0.42f)
            drawCircle(RiffColors.Paper, radius, center, style = Stroke(stroke))
            val start = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f)
            drawLine(RiffColors.Paper, start, Offset(size.width * 0.9f, size.height * 0.9f), stroke, StrokeCap.Round)
        }
    }
}

/**
 * O campo do filtro. Nos menus do PC ↑/↓ saem dele (o campo de uma linha as usaria para levar o cursor ao começo ou
 * ao fim, e o foco ficaria preso: o controle não tem outra saída); ←/→ continuam andando no texto.
 */
@Composable
internal fun FilterField(value: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val upDownLeave = if (!menuKeysEnabled) {
        Modifier
    } else {
        Modifier.onPreviewKeyEvent { event ->
            val direction = when (event.key) {
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionDown -> FocusDirection.Down
                else -> return@onPreviewKeyEvent false
            }
            if (event.type == KeyEventType.KeyDown) focus.moveFocus(direction)
            true
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(Res.string.library_filter_hint)) },
        singleLine = true,
        modifier = upDownLeave.fillMaxWidth().testTag(LibraryTestTags.FILTER),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = RiffColors.Paper,
            unfocusedTextColor = RiffColors.Paper,
            unfocusedBorderColor = RiffColors.PaperDim,
            unfocusedLabelColor = RiffColors.PaperDim,
        ),
    )
}
