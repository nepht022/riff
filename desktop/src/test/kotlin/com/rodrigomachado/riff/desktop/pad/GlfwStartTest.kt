package com.rodrigomachado.riff.desktop.pad

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_HAT_BUTTONS

/** O GLFW iniciado para o controle: sem o chapéu repetido como botões (no modo cru, viraria cores). */
class GlfwStartTest {
    @Test
    fun theHatIsNotRepeatedAsButtons() {
        val calls = mutableListOf<String>()

        val started = startGlfwForPads(
            hint = { hint, value -> calls += "hint $hint=$value" },
            init = {
                calls += "init"
                true
            },
        )

        assertEquals(true, started)
        // A dica só vale antes do glfwInit.
        assertEquals(listOf("hint $GLFW_JOYSTICK_HAT_BUTTONS=$GLFW_FALSE", "init"), calls)
    }
}
