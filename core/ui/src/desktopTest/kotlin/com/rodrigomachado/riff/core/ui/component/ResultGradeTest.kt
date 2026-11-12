package com.rodrigomachado.riff.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultGradeTest {
    @Test
    fun gradeLimits() {
        assertEquals(ResultGrade.S, ResultGrade.of(1f))
        assertEquals(ResultGrade.S, ResultGrade.of(0.98f))
        assertEquals(ResultGrade.A, ResultGrade.of(0.9799f))
        assertEquals(ResultGrade.A, ResultGrade.of(0.90f))
        assertEquals(ResultGrade.B, ResultGrade.of(0.8999f))
        assertEquals(ResultGrade.B, ResultGrade.of(0.80f))
        assertEquals(ResultGrade.C, ResultGrade.of(0.7999f))
        assertEquals(ResultGrade.C, ResultGrade.of(0f))
    }
}
