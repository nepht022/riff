package com.rodrigomachado.riff.desktop.pad

import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_LAST
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.glfwGetGamepadName
import org.lwjgl.glfw.GLFW.glfwGetGamepadState
import org.lwjgl.glfw.GLFW.glfwGetJoystickAxes
import org.lwjgl.glfw.GLFW.glfwGetJoystickButtons
import org.lwjgl.glfw.GLFW.glfwGetJoystickGUID
import org.lwjgl.glfw.GLFW.glfwGetJoystickHats
import org.lwjgl.glfw.GLFW.glfwGetJoystickName
import org.lwjgl.glfw.GLFW.glfwGetVersionString
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwJoystickIsGamepad
import org.lwjgl.glfw.GLFW.glfwJoystickPresent
import org.lwjgl.glfw.GLFW.glfwSetErrorCallback
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWGamepadState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Verifica o controle no PC: `./scripts/gw :desktop:runPadCheck`. Lista os controles que o GLFW vê (nome, GUID, se
 * a base de mapeamentos do SDL o conhece) e, por 20 s, imprime cada botão, eixo e direcional que mudar (no layout de
 * controle, quando conhecido, e cru), e no fim o intervalo real entre as leituras.
 */
fun main() {
    glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err))
    if (!glfwInit()) {
        println("O GLFW não iniciou: sem leitura de controle neste PC.")
        return
    }
    println("GLFW ${glfwGetVersionString()}")
    val pads = (GLFW_JOYSTICK_1..GLFW_JOYSTICK_LAST).filter { glfwJoystickPresent(it) }
    if (pads.isEmpty()) println("Nenhum controle encontrado: ligue o cabo e rode de novo.")
    for (pad in pads) {
        val mapping = if (glfwJoystickIsGamepad(pad)) {
            "mapeado como \"${glfwGetGamepadName(pad)}\""
        } else {
            "sem mapeamento (cru)"
        }
        println("Controle $pad: \"${glfwGetJoystickName(pad)}\", GUID ${glfwGetJoystickGUID(pad)}, $mapping")
    }
    pads.firstOrNull()?.let { watch(it) }
    glfwTerminate()
}

private fun watch(pad: Int) {
    println("Por 20 s, aperte △ ○ □ ✕ R1, Start, o direcional ↑/↓ e o analógico esquerdo.")
    val state = GLFWGamepadState.create()
    var last = emptyList<String>()
    val intervals = mutableListOf<Long>()
    var previousAt = System.nanoTime()
    val end = previousAt + WATCH_NS
    while (System.nanoTime() < end) {
        val now = System.nanoTime()
        intervals += now - previousAt
        previousAt = now
        val current = read(pad, state)
        (current - last.toSet()).forEach { println("  $it") }
        last = current
        Thread.sleep(1)
    }
    val averageMs = intervals.average() / NS_PER_MS
    val worstMs = (intervals.maxOrNull() ?: 0L) / NS_PER_MS.toDouble()
    println("Leituras: ${intervals.size}; intervalo médio %.2f ms, pior %.2f ms".format(averageMs, worstMs))
}

/** O que está apertado agora, uma linha por botão/eixo/direcional (eixos arredondados a 0,1). */
private fun read(pad: Int, state: GLFWGamepadState): List<String> = buildList {
    if (glfwJoystickIsGamepad(pad) && glfwGetGamepadState(pad, state)) {
        GAMEPAD_BUTTONS.forEachIndexed { index, name ->
            if (state.buttons(index).toInt() == GLFW_PRESS) add("controle: $name")
        }
        GAMEPAD_AXES.forEachIndexed { index, name ->
            val value = state.axes(index)
            if (abs(value) >= AXIS_SHOWN) add("controle: $name ${(value * 10).roundToInt() / 10.0}")
        }
    }
    glfwGetJoystickButtons(pad)?.let { buttons ->
        for (index in 0 until buttons.limit()) if (buttons.get(index).toInt() == GLFW_PRESS) add("cru: botão $index")
    }
    glfwGetJoystickAxes(pad)?.let { axes ->
        for (index in 0 until axes.limit()) {
            val value = axes.get(index)
            if (abs(value) >= AXIS_SHOWN) add("cru: eixo $index ${(value * 10).roundToInt() / 10.0}")
        }
    }
    glfwGetJoystickHats(pad)?.let { hats ->
        for (index in 0 until hats.limit()) {
            if (hats.get(index).toInt() != 0) add("cru: direcional $index = ${hats.get(index)}")
        }
    }
}

private val GAMEPAD_BUTTONS = listOf(
    "A (✕)", "B (○)", "X (□)", "Y (△)", "L1", "R1", "Back", "Start", "Guide", "L3", "R3",
    "direcional ↑", "direcional →", "direcional ↓", "direcional ←",
)
private val GAMEPAD_AXES = listOf(
    "analógico esq. X",
    "analógico esq. Y",
    "analógico dir. X",
    "analógico dir. Y",
    "L2",
    "R2",
)
private const val AXIS_SHOWN = 0.3f
private const val WATCH_NS = 20_000_000_000L
private const val NS_PER_MS = 1_000_000L
