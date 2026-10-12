package com.rodrigomachado.riff

/**
 * Percebe a volta para uma tela anterior (botão Voltar, gesto do celular ou Sair), para tocar o som de voltar
 * uma vez só, venha de onde vier. Guarda os ids das telas abertas: mostrar uma que já estava abaixo é voltar.
 * O estado sobrevive à recriação da Activity ([save] e [restore]): os ids das telas também sobrevivem.
 */
class BackNavigation private constructor(private val shown: MutableList<String>, private var silent: Boolean) {
    constructor() : this(mutableListOf(), false)

    /** A tela [entryId] apareceu; verdadeiro se foi uma volta que deve soar. */
    fun onShown(entryId: String): Boolean {
        // A mesma tela de novo (a navegação repete a atual ao recriar a Activity) não é mudança.
        if (shown.lastOrNull() == entryId) return false
        // O silêncio vale só para a mudança seguinte, seja ela uma volta ou não.
        val silenced = silent
        silent = false
        val index = shown.indexOf(entryId)
        if (index < 0) {
            shown += entryId
            return false
        }
        shown.subList(index + 1, shown.size).clear()
        return !silenced
    }

    /** A próxima volta é a conclusão de uma ação (escolher o mapa, salvar a calibração): já soou como confirmação. */
    fun silenceNextReturn() {
        silent = true
    }

    /** O silêncio pendente e os ids das telas, nessa ordem. */
    fun save(): ArrayList<String> = arrayListOf(silent.toString()).apply { addAll(shown) }

    companion object {
        fun restore(saved: List<String>): BackNavigation =
            BackNavigation(saved.drop(1).toMutableList(), saved.firstOrNull() == true.toString())
    }
}
