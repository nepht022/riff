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
 * `m:N` num controle mapeado. Num cru, cada botão físico tem um nome só: o do layout quando a ordem crua do DualShock 3
 * o conhece (△ cru 12 é `m:3`, como o padrão; o direcional, botão ou chapéu, é `m:11`/`m:13`), senão `r:N`. Assim a
 * troca das escolhas percebe o mesmo botão em duas ações.
 */
internal fun PadSnapshot.pressedSince(before: PadSnapshot?): List<String> {
    val mapped = layout
    if (mapped != null) return (mapped - before?.layout.orEmpty()).sorted().map { "$MAPPED_PREFIX$it" }
    val hatDown = hat and (before?.hat ?: 0).inv()
    val hats = listOf(HAT_UP to MappedButton.DPAD_UP, HAT_DOWN to MappedButton.DPAD_DOWN)
        .filter { hatDown and it.first != 0 }
        .map { "$MAPPED_PREFIX${it.second}" }
    val buttons = (raw - before?.raw.orEmpty()).sorted().map { index ->
        MAPPED_OF_RAW[index]?.let { "$MAPPED_PREFIX$it" } ?: "$RAW_PREFIX$index"
    }
    return (buttons + hats).distinct()
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

/** A tabela ao contrário: o nome (do layout) de cada botão cru do DualShock 3. */
private val MAPPED_OF_RAW: Map<Int, Int> = RAW_OF_MAPPED.entries.associate { (mapped, raw) -> raw to mapped }
