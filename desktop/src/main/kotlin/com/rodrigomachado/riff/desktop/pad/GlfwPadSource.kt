package com.rodrigomachado.riff.desktop.pad

import org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_LAST
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.glfwGetGamepadState
import org.lwjgl.glfw.GLFW.glfwGetJoystickAxes
import org.lwjgl.glfw.GLFW.glfwGetJoystickButtons
import org.lwjgl.glfw.GLFW.glfwGetJoystickHats
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwJoystickIsGamepad
import org.lwjgl.glfw.GLFW.glfwJoystickPresent
import org.lwjgl.glfw.GLFW.glfwPollEvents
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFWGamepadState

/**
 * O controle pelo GLFW: o primeiro joystick ligado, no layout de controle quando a base do SDL o conhece
 * (`glfwGetGamepadState`), senão cru (botões, eixo 1 e chapéu 0 pelo índice). Sem controle, procura de novo a cada
 * leitura: um controle ligado depois é encontrado.
 */
class GlfwPadSource : PadSource {
    private var started = false
    private var pad: Int? = null
    private val state by lazy { GLFWGamepadState.create() }

    override fun init(): Boolean {
        started = glfwInit()
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

    private fun read(pad: Int): PadSnapshot? = if (glfwJoystickIsGamepad(pad) && glfwGetGamepadState(pad, state)) {
        PadSnapshot(
            mapped = true,
            buttons = (0 until GAMEPAD_BUTTONS).filterTo(mutableSetOf()) { state.buttons(it).toInt() == GLFW_PRESS },
            leftY = state.axes(GLFW_GAMEPAD_AXIS_LEFT_Y),
        )
    } else {
        // Sumiu entre a procura e a leitura: os três voltam nulos.
        glfwGetJoystickButtons(pad)?.let { buttons ->
            PadSnapshot(
                mapped = false,
                buttons = (0 until buttons.limit()).filterTo(mutableSetOf()) { buttons.get(it).toInt() == GLFW_PRESS },
                leftY = glfwGetJoystickAxes(pad)?.takeIf { it.limit() > RAW_LEFT_Y }?.get(RAW_LEFT_Y) ?: 0f,
                hat = glfwGetJoystickHats(pad)?.takeIf { it.limit() > 0 }?.get(0)?.toInt() ?: 0,
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
