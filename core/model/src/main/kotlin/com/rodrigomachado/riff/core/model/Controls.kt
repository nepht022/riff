package com.rodrigomachado.riff.core.model

/** O que uma tecla ou um botão do controle faz na partida do PC (palheta ↑ e ↓ separadas, como no Clone Hero). */
enum class ControlAction { FRET_1, FRET_2, FRET_3, FRET_4, FRET_5, STRUM_UP, STRUM_DOWN, PAUSE }

/**
 * Os controles escolhidos no PC: uma tecla e um botão por ação. [strum] desligado dispensa a palheta (apertar a
 * cor na hora basta). As teclas são o `Key.keyCode` do Compose no PC: a localização AWT da tecla nos 32 bits de
 * cima e o código AWT (`java.awt.event.KeyEvent.VK_*`) nos de baixo, ver [desktopKey]; este módulo não depende do
 * Compose, por isso guarda o número. Os botões são
 * texto: `m:<índice>` no layout de controle do GLFW (controle mapeado), `r:<índice>` o botão cru e `h:up`/`h:down`
 * o chapéu. O Esc (pausa) e o analógico esquerdo (palheta) são fixos e não entram aqui.
 */
data class Controls(
    val strum: Boolean = true,
    val keys: Map<ControlAction, Long> = DEFAULT_KEYS,
    val pad: Map<ControlAction, String> = DEFAULT_PAD,
) {
    /** [action] passa a usar [keyCode]; a ação que já usava essa tecla fica com a antiga de [action] (troca). */
    fun withKey(action: ControlAction, keyCode: Long): Controls = copy(keys = keys.swapping(action, keyCode))

    /** [action] passa a usar [button]; a ação que já usava esse botão fica com o antigo de [action] (troca). */
    fun withPad(action: ControlAction, button: String): Controls = copy(pad = pad.swapping(action, button))

    companion object {
        /** A S J K L, ↑, ↓ e Enter (o `Key.keyCode` de `Key.A`… `Key.Enter`), como antes da escolha. */
        val DEFAULT_KEYS: Map<ControlAction, Long> = mapOf(
            ControlAction.FRET_1 to desktopKey(65),
            ControlAction.FRET_2 to desktopKey(83),
            ControlAction.FRET_3 to desktopKey(74),
            ControlAction.FRET_4 to desktopKey(75),
            ControlAction.FRET_5 to desktopKey(76),
            ControlAction.STRUM_UP to desktopKey(38),
            ControlAction.STRUM_DOWN to desktopKey(40),
            ControlAction.PAUSE to desktopKey(10),
        )

        /**
         * O `Key.keyCode` do Compose no PC para o código AWT [awtCode] numa tecla comum
         * (`KeyEvent.KEY_LOCATION_STANDARD` = 1 nos 32 bits de cima): `desktopKey(65)` é o `Key.A.keyCode`.
         */
        fun desktopKey(awtCode: Int): Long = (STANDARD_LOCATION shl Int.SIZE_BITS) or awtCode.toLong()

        private const val STANDARD_LOCATION = 1L

        /** △ ○ □ ✕ R1, direcional ↑ e ↓, Start (índices do layout de controle do GLFW), como antes da escolha. */
        val DEFAULT_PAD: Map<ControlAction, String> = mapOf(
            ControlAction.FRET_1 to "m:3",
            ControlAction.FRET_2 to "m:1",
            ControlAction.FRET_3 to "m:2",
            ControlAction.FRET_4 to "m:0",
            ControlAction.FRET_5 to "m:5",
            ControlAction.STRUM_UP to "m:11",
            ControlAction.STRUM_DOWN to "m:13",
            ControlAction.PAUSE to "m:7",
        )
    }
}

private fun <V> Map<ControlAction, V>.swapping(action: ControlAction, value: V): Map<ControlAction, V> {
    val holder = entries.firstOrNull { it.key != action && it.value == value }?.key
    val previous = this[action]
    return buildMap {
        putAll(this@swapping)
        put(action, value)
        if (holder != null && previous != null) put(holder, previous)
    }
}
