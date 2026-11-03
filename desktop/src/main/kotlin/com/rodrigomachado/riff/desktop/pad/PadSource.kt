package com.rodrigomachado.riff.desktop.pad

/**
 * Uma leitura do controle: os botões crus apertados ([raw], pelo índice do joystick) e o chapéu 0 (os bits
 * [HAT_UP]/[HAT_DOWN]) sempre; os apertados no layout de controle do GLFW ([layout]) só quando a base do SDL conhece o
 * controle (nulo = controle cru); e o analógico esquerdo na vertical (−1 = para cima .. 1 = para baixo).
 */
data class PadSnapshot(val raw: Set<Int>, val hat: Int = 0, val leftY: Float = 0f, val layout: Set<Int>? = null)

/**
 * De onde vem o controle. Tudo é chamado na mesma thread (a do [GlfwGamepad]): [init] uma vez (falso = sem leitura de
 * controle neste PC), [poll] a cada leitura (nulo = nenhum controle ligado) e [close] no fim.
 */
interface PadSource : AutoCloseable {
    fun init(): Boolean

    fun poll(): PadSnapshot?
}

/** Os botões do layout de controle do GLFW (os números de `GLFW_GAMEPAD_BUTTON_*`). */
object MappedButton {
    const val A = 0
    const val B = 1
    const val X = 2
    const val Y = 3
    const val RIGHT_BUMPER = 5
    const val START = 7
    const val DPAD_UP = 11
    const val DPAD_DOWN = 13
}

/** A ordem crua do DualShock 3 (a base escolhida para o modo cru): o controle fora da base do SDL. */
object RawButton {
    const val START = 3
    const val UP = 4
    const val DOWN = 6
    const val R1 = 11
    const val TRIANGLE = 12
    const val CIRCLE = 13
    const val CROSS = 14
    const val SQUARE = 15
}

/** O chapéu para cima e para baixo (os bits de `GLFW_HAT_UP`/`GLFW_HAT_DOWN`). */
const val HAT_UP = 1
const val HAT_DOWN = 4
