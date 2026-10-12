package com.rodrigomachado.riff.core.model

/** Papel de um botão do menu, que decide o som de guitarra do toque. */
enum class MenuSound {
    /** Abrir uma tela ou escolher algo sem começar nada. */
    NAVIGATE,

    /** Começar ou salvar algo. */
    CONFIRM,

    /** Sair de uma tela ou desfazer. */
    BACK,
}
