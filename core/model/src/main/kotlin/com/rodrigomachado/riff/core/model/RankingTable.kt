package com.rodrigomachado.riff.core.model

/**
 * Em que tabela do ranking online uma partida conta, com o nome do protocolo do script ([wire]): o celular, o PC
 * tocando com palheta ou o PC sem palheta. Cada tabela tem o próprio placar; o recorde local continua um só.
 */
enum class RankingTable(val wire: String) {
    PHONE("celular"),
    PC_STRUM("pc_palheta"),
    PC_NO_STRUM("pc_sem_palheta"),
    ;

    companion object {
        /** Sem tabela (o script de antes da 4d, as linhas antigas) é o celular; uma desconhecida é nula. */
        fun fromWire(wire: String?): RankingTable? =
            if (wire.isNullOrEmpty()) PHONE else entries.firstOrNull { it.wire == wire }

        /** A partida do PC conta na tabela do modo em que foi jogada ("Tocar com palheta"). */
        fun pc(strum: Boolean): RankingTable = if (strum) PC_STRUM else PC_NO_STRUM
    }
}
