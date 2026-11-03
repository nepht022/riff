package com.rodrigomachado.riff.desktop.pad

/** Um PC sem controle, para os testes do app: o GLFW não é iniciado (o CI não tem tela). */
object NoPadSource : PadSource {
    override fun init() = false

    override fun poll(): PadSnapshot? = null

    override fun close() = Unit
}
