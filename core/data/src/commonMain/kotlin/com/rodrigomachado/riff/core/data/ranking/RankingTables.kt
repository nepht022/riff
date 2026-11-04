package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.model.RankingTable

/**
 * A tabela de uma partida gravada, lida neste aparelho. A migração do banco (versão 14) marcou todas as partidas de
 * antes como "celular"; no PC elas são de antes do ranking chegar lá e contam como sem palheta. A regra fica só aqui.
 */
fun RankingTable.forThisDevice(): RankingTable = forDevice(pc = runsOnPc)

/** [forThisDevice] com o aparelho dito: [pc] verdadeiro no app do PC. */
fun RankingTable.forDevice(pc: Boolean): RankingTable =
    if (pc && this == RankingTable.PHONE) RankingTable.PC_NO_STRUM else this

/**
 * A tabela gravada no banco ([RankingTable.wire]) lida neste aparelho; uma que este app não conhece (gravada por um
 * app mais novo) conta como a do aparelho.
 */
internal fun storedTable(wire: String): RankingTable =
    (RankingTable.fromWire(wire) ?: RankingTable.PHONE).forThisDevice()

/** Verdadeiro no app do PC, falso no celular. */
internal expect val runsOnPc: Boolean
