package com.rodrigomachado.riff.desktop.pad

import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_HAT_BUTTONS
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_LAST
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.glfwGetGamepadState
import org.lwjgl.glfw.GLFW.glfwGetJoystickAxes
import org.lwjgl.glfw.GLFW.glfwGetJoystickButtons
import org.lwjgl.glfw.GLFW.glfwGetJoystickHats
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwInitHint
import org.lwjgl.glfw.GLFW.glfwJoystickIsGamepad
import org.lwjgl.glfw.GLFW.glfwJoystickPresent
import org.lwjgl.glfw.GLFW.glfwPollEvents
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFWGamepadState

/**
 * Inicia o GLFW para ler controles. Sem a dica, o GLFW repete cada direcional em chapéu como 4 botões a mais no fim da
 * lista crua, e um clone com 12 botões apertaria cores ao palhetar (o 14 é o ✕ do DualShock 3).
 */
internal fun startGlfwForPads(hint: (Int, Int) -> Unit = ::glfwInitHint, init: () -> Boolean = ::glfwInit): Boolean {
    hint(GLFW_JOYSTICK_HAT_BUTTONS, GLFW_FALSE)
    return init()
}

/**
 * O controle pelo GLFW: o primeiro joystick ligado, com os botões crus e o chapéu 0 pelo índice sempre (um botão `r:`
 * escolhido vale em qualquer controle) e, quando a base do SDL o conhece, também no layout de controle
 * (`glfwGetGamepadState`, com o analógico dele); senão, o analógico é o eixo cru 1. Sem controle, procura de novo a
 * cada leitura: um controle ligado depois é encontrado.
 */
class GlfwPadSource : PadSource {
    private var started = false
    private var pad: Int? = null
    private val state by lazy { GLFWGamepadState.create() }

    override fun init(): Boolean {
        started = startGlfwForPads()
        return started
    }

    override fun poll(): PadSnapshot? {
        // As ligações e desligamentos de controle chegam como mensagens da janela auxiliar do GLFW (desta thread).
        glfwPollEvents()
        val current = pad?.takeIf { glfwJoystickPresent(it) }
            ?: (GLFW_JOYSTICK_1..GLFW_JOYSTICK_LAST).firstOrNull { glfwJoystickPresent(it) }
        pad = current
        return current?.let(::read)
    }

    /** Sumiu entre a procura e a leitura: os botões crus voltam nulos, e a leitura também. */
    private fun read(pad: Int): PadSnapshot? {
        val buttons = glfwGetJoystickButtons(pad) ?: return null
        val raw = (0 until buttons.limit()).filterTo(mutableSetOf()) { buttons.get(it).toInt() == GLFW_PRESS }
        val hat = glfwGetJoystickHats(pad)?.takeIf { it.limit() > 0 }?.get(0)?.toInt() ?: 0
        return if (glfwJoystickIsGamepad(pad) && glfwGetGamepadState(pad, state)) {
            PadSnapshot(
                raw = raw,
                hat = hat,
                leftY = state.axes(GLFW_GAMEPAD_AXIS_LEFT_Y),
                layout = (0 until GAMEPAD_BUTTONS).filterTo(mutableSetOf()) { state.buttons(it).toInt() == GLFW_PRESS },
            )
        } else {
            PadSnapshot(
                raw = raw,
                hat = hat,
                leftY = glfwGetJoystickAxes(pad)?.takeIf { it.limit() > RAW_LEFT_Y }?.get(RAW_LEFT_Y) ?: 0f,
            )
        }
    }

    override fun close() {
        if (started) glfwTerminate()
        started = false
    }

    private companion object {
        /** `GLFW_GAMEPAD_BUTTON_LAST + 1`. */
        const val GAMEPAD_BUTTONS = 15

        /** O eixo cru do analógico esquerdo na vertical (o 1 no DualShock 3 e na maioria dos controles). */
        const val RAW_LEFT_Y = 1
    }
}
