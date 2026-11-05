package com.rodrigomachado.riff.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RankingTableTest {
    @Test
    fun theWireNamesAreTheScriptOnes() {
        assertEquals(listOf("celular", "pc_palheta", "pc_sem_palheta"), RankingTable.entries.map { it.wire })
    }

    /** O script de antes da 4d não manda a tabela: tudo é do celular. Uma tabela desconhecida não é aceita. */
    @Test
    fun readingTheWire() {
        assertEquals(RankingTable.PC_STRUM, RankingTable.fromWire("pc_palheta"))
        assertEquals(RankingTable.PHONE, RankingTable.fromWire(null))
        assertEquals(RankingTable.PHONE, RankingTable.fromWire(""))
        assertNull(RankingTable.fromWire("tablet"))
    }

    @Test
    fun aPcMatchCountsInTheTableOfItsMode() {
        assertEquals(RankingTable.PC_STRUM, RankingTable.pc(strum = true))
        assertEquals(RankingTable.PC_NO_STRUM, RankingTable.pc(strum = false))
    }

    @Test
    fun aScoreIsFromThePhoneUnlessSaid() {
        assertEquals(RankingTable.PHONE, ScoreRecord(0, "m", Difficulty.HARD, 1, 1f, 1, 1, 0, 0, 0).rankingTable)
    }
}
