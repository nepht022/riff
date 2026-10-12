package com.rodrigomachado.riff

/**
 * Percebe a volta para uma tela anterior (botão Voltar, gesto do celular ou Sair), para tocar o som de voltar
 * uma vez só, venha de onde vier. Guarda os ids das telas abertas: mostrar uma que já estava abaixo é voltar.
 */
class BackNavigation {
    private val shown = mutableListOf<String>()
    private var silent = false

    /** A tela [entryId] apareceu; verdadeiro se foi uma volta que deve soar. */
    fun onShown(entryId: String): Boolean {
        // A mesma tela de novo (girar o celular) não é volta.
        if (shown.lastOrNull() == entryId) return false
        val index = shown.indexOf(entryId)
        if (index < 0) {
            shown += entryId
            return false
        }
        shown.subList(index + 1, shown.size).clear()
        return !silent.also { silent = false }
    }

    /** A próxima volta é a conclusão de uma ação (escolher o mapa, salvar a calibração): já soou como confirmação. */
    fun silenceNextReturn() {
        silent = true
    }
}
