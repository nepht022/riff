package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.data.toModel
import com.rodrigomachado.riff.core.database.ScoreEntity
import com.rodrigomachado.riff.core.model.RankingTable
import org.junit.Assert.assertEquals
import org.junit.Test

/** Decisão 5 no PC: o que a migração marcou "celular" é de antes da 4d e conta sem palheta (o padrão do PC). */
class PcRankingTableTest {
    private fun score(table: String) = ScoreEntity(1, "m", "HARD", 1, 1f, 1, 1, 0, 0, 0, null, table)

    @Test
    fun anOldPcMatchCountsWithoutStrum() {
        assertEquals(RankingTable.PC_NO_STRUM, RankingTable.PHONE.forThisDevice())
        assertEquals(RankingTable.PC_NO_STRUM, score("celular").toModel()?.rankingTable)
    }

    @Test
    fun aNewPcMatchKeepsItsTable() {
        assertEquals(RankingTable.PC_STRUM, score("pc_palheta").toModel()?.rankingTable)
        assertEquals(RankingTable.PC_NO_STRUM, score("pc_sem_palheta").toModel()?.rankingTable)
    }
}
