package com.rodrigomachado.riff.desktop.pad

/**
 * O botão escolhido [button] (texto de `Controls.pad`) está apertado nesta leitura? `m:N` é o botão N do layout num
 * controle mapeado e, num cru, o da ordem crua do DualShock 3 (o direcional ↑/↓ também pelo chapéu, como antes da
 * escolha); `r:N` é o botão cru N em qualquer controle; `h:up`/`h:down`, o chapéu 0. Outro texto nunca está apertado.
 */
internal fun PadSnapshot.isPressed(button: String?): Boolean = when {
    button == null -> false
    button == HAT_UP_TEXT -> hat and HAT_UP != 0
    button == HAT_DOWN_TEXT -> hat and HAT_DOWN != 0
    button.startsWith(RAW_PREFIX) -> button.index(RAW_PREFIX)?.let { it in raw } == true
    button.startsWith(MAPPED_PREFIX) -> button.index(MAPPED_PREFIX)?.let(::isMappedPressed) == true
    else -> false
}

private fun PadSnapshot.isMappedPressed(index: Int): Boolean = layout?.let { index in it }
    ?: (RAW_OF_MAPPED[index]?.let { it in raw } == true || hatOfMapped(index)?.let { hat and it != 0 } == true)

private fun hatOfMapped(index: Int): Int? = when (index) {
    MappedButton.DPAD_UP -> HAT_UP
    MappedButton.DPAD_DOWN -> HAT_DOWN
    else -> null
}

private fun String.index(prefix: String): Int? = removePrefix(prefix).toIntOrNull()

/**
 * Os botões que desceram desde [before] (nulo = acabou de ligar: tudo o que está apertado), como texto para a escolha:
 * `m:N` num controle mapeado; `r:N` e `h:up`/`h:down` num cru.
 */
internal fun PadSnapshot.pressedSince(before: PadSnapshot?): List<String> {
    val mapped = layout
    if (mapped != null) return (mapped - before?.layout.orEmpty()).sorted().map { "$MAPPED_PREFIX$it" }
    val hatDown = hat and (before?.hat ?: 0).inv()
    val hats = listOf(HAT_UP to HAT_UP_TEXT, HAT_DOWN to HAT_DOWN_TEXT).filter { hatDown and it.first != 0 }
    return (raw - before?.raw.orEmpty()).sorted().map { "$RAW_PREFIX$it" } + hats.map { it.second }
}

private const val MAPPED_PREFIX = "m:"
private const val RAW_PREFIX = "r:"
private const val HAT_UP_TEXT = "h:up"
private const val HAT_DOWN_TEXT = "h:down"

/** O botão do layout do GLFW na ordem crua do DualShock 3 (a base escolhida para o modo cru). */
@Suppress("MagicNumber") // a tabela é a própria ordem do DualShock 3
private val RAW_OF_MAPPED: Map<Int, Int> = mapOf(
    MappedButton.Y to RawButton.TRIANGLE,
    MappedButton.B to RawButton.CIRCLE,
    MappedButton.X to RawButton.SQUARE,
    MappedButton.A to RawButton.CROSS,
    4 to 10, // L1
    MappedButton.RIGHT_BUMPER to RawButton.R1,
    6 to 0, // Select
    MappedButton.START to RawButton.START,
    8 to 16, // PS
    9 to 1, // L3
    10 to 2, // R3
    MappedButton.DPAD_UP to RawButton.UP,
    12 to 5, // direcional →
    MappedButton.DPAD_DOWN to RawButton.DOWN,
    14 to 7, // direcional ←
)
