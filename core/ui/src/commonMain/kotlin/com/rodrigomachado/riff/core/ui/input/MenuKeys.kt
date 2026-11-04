package com.rodrigomachado.riff.core.ui.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

/** No PC as setas e o Esc andam pelos menus; no celular, não (o celular fica igual, inclusive com teclado físico). */
expect val menuKeysEnabled: Boolean

/**
 * As teclas dos menus do PC numa tela inteira (ou num diálogo): as setas que nenhum componente usou movem o foco; a
 * primeira seta com nada focado foca o primeiro item; Esc chama [onBack] (nulo: o Esc passa adiante). A tela é ela
 * mesma um alvo de foco, para receber a primeira seta e a que vem depois de o foco se perder: pega o foco quando fica
 * ativa e de novo quando o item focado some. Só a de cima pega o foco (a última aberta, ativa: `RESUMED`): um
 * diálogo aberto não o disputa com a tela de trás, nem a tela que sai com a que entra. No celular é `this`.
 */
fun Modifier.menuKeys(onBack: (() -> Unit)?): Modifier =
    if (menuKeysEnabled) this then MenuKeysElement(onBack) else this

private data class MenuKeysElement(val onBack: (() -> Unit)?) : ModifierNodeElement<MenuKeysNode>() {
    override fun create() = MenuKeysNode(onBack)

    override fun update(node: MenuKeysNode) {
        node.onBack = onBack
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "menuKeys"
    }
}

/** As telas e os diálogos com [menuKeys] na tela, na ordem em que apareceram (só na thread da janela). */
private val opened = mutableListOf<MenuKeysNode>()

private class MenuKeysNode(var onBack: (() -> Unit)?) :
    DelegatingNode(),
    KeyInputModifierNode,
    CompositionLocalConsumerModifierNode {
    private val screen = delegate(FocusTargetModifierNode(onFocusChange = ::onFocusChange))
    private var lifecycle: Lifecycle? = null
    private val onResume = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) claimFocus() }

    private val resumed: Boolean get() = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true

    override fun onAttach() {
        opened += this
        // Antes de observar: uma tela já ativa recebe o ON_RESUME na hora, dentro do addObserver.
        // Lido só aqui de propósito: o nó vive dentro de uma tela (destino da navegação ou diálogo), cujo dono do ciclo
        // de vida não muda enquanto ele existe.
        @Suppress("SuspiciousCompositionLocalModifierRead")
        val current = currentValueOf(LocalLifecycleOwner).lifecycle
        lifecycle = current
        current.addObserver(onResume)
    }

    override fun onDetach() {
        opened -= this
        lifecycle?.removeObserver(onResume)
        lifecycle = null
        // O diálogo fechou: a tela de trás volta a ser a de cima.
        opened.lastOrNull { it.resumed }?.claimFocus()
    }

    // O item focado sumiu (música apagada, tela refeita): a tela pega o foco de volta, e a próxima seta acha outro.
    private fun onFocusChange(previous: FocusState, current: FocusState) {
        if (previous.hasFocus && !current.hasFocus) claimFocus()
    }

    private fun claimFocus() {
        coroutineScope.launch {
            val onTop = opened.lastOrNull { it.resumed } === this@MenuKeysNode
            if (isAttached && onTop && !screen.focusState.hasFocus) screen.requestFocus()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            Key.Escape -> return onBack?.invoke() != null
            else -> return false
        }
        val focus = currentValueOf(LocalFocusManager)
        // Só a tela tem o foco: a seta entra nela e foca o primeiro item.
        if (screen.focusState.isFocused) focus.moveFocus(FocusDirection.Enter) else focus.moveFocus(direction)
        return true
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false
}

/**
 * Num controle que usaria ↑/↓ como ←/→ (o Slider), nos menus do PC ↑/↓ movem o foco para a linha de cima ou de
 * baixo, e só ←/→ mudam o valor. Vem antes do componente. No celular é `this`.
 */
fun Modifier.upDownLeave(): Modifier = if (menuKeysEnabled) this then UpDownLeaveElement else this

private data object UpDownLeaveElement : ModifierNodeElement<UpDownLeaveNode>() {
    override fun create() = UpDownLeaveNode()

    override fun update(node: UpDownLeaveNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "upDownLeave"
    }
}

private class UpDownLeaveNode :
    Modifier.Node(),
    KeyInputModifierNode,
    CompositionLocalConsumerModifierNode {
    // Antes do componente (a prévia desce da raiz até o focado): ele nem vê as setas ↑/↓.
    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            else -> return false
        }
        if (event.type == KeyEventType.KeyDown) currentValueOf(LocalFocusManager).moveFocus(direction)
        return true
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false
}

/**
 * Num campo de várias linhas, onde ↑/↓ andam no texto, nos menus do PC Tab e Shift+Tab levam o foco ao próximo item
 * e ao anterior (sem escrever a tabulação): a saída do campo pelo teclado. Vem antes do componente. No celular é
 * `this`.
 */
fun Modifier.tabLeaves(): Modifier = if (menuKeysEnabled) this then TabLeavesElement else this

private data object TabLeavesElement : ModifierNodeElement<TabLeavesNode>() {
    override fun create() = TabLeavesNode()

    override fun update(node: TabLeavesNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "tabLeaves"
    }
}

private class TabLeavesNode :
    Modifier.Node(),
    KeyInputModifierNode,
    CompositionLocalConsumerModifierNode {
    // Antes do campo: ele não vê o Tab (nem o caractere que viria dele).
    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        if (event.key != Key.Tab) return false
        if (event.type == KeyEventType.KeyDown) {
            val direction = if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
            currentValueOf(LocalFocusManager).moveFocus(direction)
        }
        return true
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false
}
