package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.art.ChartThumbImages
import com.rodrigomachado.riff.core.ui.art.loadOrNull
import com.rodrigomachado.riff.core.ui.theme.RiffColors

object ChartThumbTestTags {
    /** A imagem do mapa (sem ela, as iniciais). */
    const val ART = "chart_thumb_art"
}

/** As iniciais ocupam ~40% do lado (em dp, não em sp: é desenho, não texto a ler, e não estoura o quadrado). */
private const val INITIALS_SCALE = 0.4f

/**
 * A capa de um mapa num quadrado de [size]: a imagem do mapa (cortada ao meio) quando [images] a tem; sem ela (sem
 * leitor, sem imagem, imagem que não abre, ou enquanto lê), as iniciais do [title] em papel sobre uma cor fixa do
 * mapa. É enfeite: o título vem ao lado, então os leitores de tela não a anunciam. Com um [ChartThumbImages], a capa
 * já guardada aparece na primeira composição (sem piscar ao rolar a lista).
 */
@Composable
fun ChartThumb(chartId: String, title: String, size: Dp, images: ChartArtImages?, modifier: Modifier = Modifier) {
    // O estado é do mapa: outro mapa no mesmo lugar (lista que muda) começa sem a capa do anterior.
    var art by remember(chartId, images) { mutableStateOf((images as? ChartThumbImages)?.cached(chartId)) }
    LaunchedEffect(chartId, images) {
        if (images != null) art = images.loadOrNull(chartId)
    }
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(thumbColor(chartId))
            .semantics { hideFromAccessibility() },
        contentAlignment = Alignment.Center,
    ) {
        val image: ImageBitmap? = art
        if (image != null) {
            Image(
                image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().testTag(ChartThumbTestTags.ART),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                thumbInitials(title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = with(LocalDensity.current) { (size * INITIALS_SCALE).toSp() },
                ),
                color = RiffColors.Paper,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** As cores das capas sem imagem: vermelho, tinta e marrom do tema; o papel das iniciais se lê nas três. */
private val ThumbPalette = listOf(RiffColors.TourRed, RiffColors.Ink, RiffColors.StampWarning)

/** A cor do mapa sem imagem: a mesma para o mesmo [chartId] (hash próprio, igual em toda plataforma e execução). */
internal fun thumbColor(chartId: String): Color {
    val hash = chartId.fold(0) { acc, char -> HASH_PRIME * acc + char.code }
    return ThumbPalette[(hash % ThumbPalette.size + ThumbPalette.size) % ThumbPalette.size]
}

private const val HASH_PRIME = 31

/**
 * A primeira letra (ou número) das duas primeiras palavras do título, em maiúsculas, pulando artigos e preposições
 * curtas ("Through the Fire and Flames" → "TF"); um título só delas usa as que tem; sem letra nenhuma, "?".
 */
internal fun thumbInitials(title: String): String {
    val words = title.split(' ', '\t', '\n').filter { word -> word.any { it.isLetterOrDigit() } }
    val significant = words.filter { it.filter(Char::isLetterOrDigit).lowercase() !in MinorWords }.ifEmpty { words }
    return significant
        .take(2)
        .joinToString("") { word -> word.first { it.isLetterOrDigit() }.uppercase() }
        .ifEmpty { "?" }
}

/** Artigos, preposições e conjunções curtas (português e inglês), que não viram inicial. */
private val MinorWords = setOf(
    "a", "an", "and", "at", "in", "of", "on", "or", "the", "to",
    "as", "da", "das", "de", "do", "dos", "e", "em", "na", "no", "o", "os", "um", "uma",
)
