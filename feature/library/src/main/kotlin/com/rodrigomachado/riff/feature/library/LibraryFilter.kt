package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import java.text.Normalizer

/** Os mapas cuja música ou artista contém [filter], sem diferenciar maiúsculas nem acentos. */
internal fun List<Chart>.matching(filter: String): List<Chart> {
    val wanted = filter.folded()
    if (wanted.isEmpty()) return this
    return filter { it.title.folded().contains(wanted) || it.artist.folded().contains(wanted) }
}

private val ACCENTS = Regex("\\p{Mn}+")

private fun String.folded() = Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()

/** Lupa desenhada aqui (nada de ícone de biblioteca de terceiros): um aro e o cabo. */
@Composable
internal fun MagnifierButton(description: String, onClick: () -> Unit) {
    IconButton(
        onClick = withMenuSound(MenuSound.NAVIGATE, onClick),
        modifier = Modifier.semantics { contentDescription = description },
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

@Composable
internal fun FilterField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.library_filter_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(LibraryTestTags.FILTER),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = RiffColors.Paper,
            unfocusedTextColor = RiffColors.Paper,
            unfocusedBorderColor = RiffColors.PaperDim,
            unfocusedLabelColor = RiffColors.PaperDim,
        ),
    )
}
