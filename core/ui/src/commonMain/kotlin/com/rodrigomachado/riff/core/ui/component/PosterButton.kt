package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

private val ShadowOffset = 5.dp
private val PressDepth = 3.dp
private val FocusRingWidth = 3.dp
private const val TILT_DEGREES = -1f
private const val DISABLED_ALPHA = 0.45f

/**
 * Medidas para dois botões lado a lado no celular (Resultado, sala): o rótulo um pouco menor e menos margem, para
 * "Jogar de novo" e "Não estou pronto" caberem numa linha em 360 dp (com fonte grande, quebram sem cortar).
 */
object PosterButtonDefaults {
    val PairPadding = Spacing.sm

    @Composable
    fun pairTextStyle(): TextStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp)
}

object PosterButtonTestTags {
    /** Contorno que aparece com o foco do teclado ou do controle. */
    const val FOCUS_RING = "poster_button_focus_ring"
}

/** Botão principal de pôster: vermelho, texto em papel, sombra dura que some ao pressionar. */
@Composable
fun PosterButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sound: MenuSound? = null,
    /** Largura mínima: botões que se revezam no mesmo lugar (Jogar / Escolher mapa) ficam do mesmo tamanho. */
    minWidth: Dp = 48.dp,
    /** O rótulo; dois botões lado a lado no celular usam [PosterButtonDefaults.pairTextStyle]. */
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
    /** A margem dos lados do rótulo; lado a lado, [PosterButtonDefaults.PairPadding]. */
    horizontalPadding: Dp = Spacing.lg,
    /** O fundo e o rótulo: o vermelho do pôster ou, no secundário, papel com tinta. */
    containerColor: Color = RiffColors.TourRed,
    contentColor: Color = RiffColors.Paper,
    /** O botão ocupa a largura mínima que o pai der (barra com botões de largura igual); sem isso, a do rótulo. */
    fillWidth: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val shift = if (pressed) PressDepth else 0.dp
    Box(
        modifier
            .rotate(TILT_DEGREES)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(end = ShadowOffset, bottom = ShadowOffset),
        propagateMinConstraints = fillWidth,
    ) {
        if (!pressed) {
            Box(Modifier.matchParentSize().offset(ShadowOffset, ShadowOffset).background(RiffColors.Ink))
        }
        Box(
            Modifier
                .offset(shift, shift)
                .background(containerColor)
                .focusHighlight(RectangleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = withMenuSound(sound, onClick),
                )
                .defaultMinSize(minWidth = minWidth, minHeight = 48.dp)
                .padding(horizontal = horizontalPadding, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = textStyle,
                color = contentColor,
                textAlign = TextAlign.Center, // rótulo que quebra em duas linhas continua centralizado
            )
        }
        // Foco do teclado ou do controle no celular: contorno de papel em volta do botão (a indicação padrão está
        // desligada). No PC, o destaque comum (focusHighlight) faz esse papel.
        if (focused && !showsFocusHighlight) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(FocusRingWidth, RiffColors.Paper)
                    .testTag(PosterButtonTestTags.FOCUS_RING),
            )
        }
    }
}
