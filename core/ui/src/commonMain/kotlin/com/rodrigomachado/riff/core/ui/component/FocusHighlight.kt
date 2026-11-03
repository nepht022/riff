package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors

/** No PC o destaque do foco aparece; no celular, não (o celular fica igual, inclusive com teclado físico). */
expect val showsFocusHighlight: Boolean

private val RingWidth = 2.dp
private val SeamWidth = 1.dp
private const val GLOW_ALPHA = 0.14f

/**
 * Destaque do foco do teclado ou do controle (PC): contorno de papel de 2 dp com os cantos de [shape], por fora do
 * componente, separado dele por um fio de tinta (o contorno aparece no palco escuro, no vermelho do pôster e no
 * cartão de papel), e um leve clarão por cima. Vem antes do `clickable`/`selectable`/`toggleable` (ou é o `modifier`
 * do componente do Material): acende com o foco desse elemento, não com o de um filho. No celular é `this`.
 */
fun Modifier.focusHighlight(shape: Shape = RoundedCornerShape(4.dp)): Modifier =
    if (showsFocusHighlight) this then FocusHighlightElement(shape) else this

private data class FocusHighlightElement(val shape: Shape) : ModifierNodeElement<FocusHighlightNode>() {
    override fun create() = FocusHighlightNode(shape)

    override fun update(node: FocusHighlightNode) {
        node.shape = shape
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusHighlight"
        properties["shape"] = shape
    }
}

private class FocusHighlightNode(var shape: Shape) :
    Modifier.Node(),
    FocusEventModifierNode,
    DrawModifierNode {
    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused != focused) {
            focused = focusState.isFocused
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        drawOutline(shape.createOutline(size, layoutDirection, this), RiffColors.Paper.copy(alpha = GLOW_ALPHA))
        val seam = SeamWidth.toPx()
        val ring = RingWidth.toPx()
        // Fio de tinta colado ao componente e, por fora dele, o contorno de papel (traços centrados na linha).
        outlineAround(inset = seam / 2, stroke = seam, color = RiffColors.Ink)
        outlineAround(inset = seam + ring / 2, stroke = ring, color = RiffColors.Paper)
    }

    private fun ContentDrawScope.outlineAround(inset: Float, stroke: Float, color: Color) {
        val grown = Size(size.width + 2 * inset, size.height + 2 * inset)
        translate(-inset, -inset) {
            drawOutline(shape.createOutline(grown, layoutDirection, this), color, style = Stroke(stroke))
        }
    }
}
