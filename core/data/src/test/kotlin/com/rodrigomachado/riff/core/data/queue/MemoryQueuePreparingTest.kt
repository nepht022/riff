package com.rodrigomachado.riff.core.data.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryQueuePreparingTest {
    /** A mesma faixa enfileirada de novo durante a pesquisa só sai do preparo quando as duas terminam. */
    @Test
    fun aTrackPreparedTwiceLeavesOnlyAfterBoth() {
        val preparing = MemoryQueuePreparing()
        preparing.start("t")
        preparing.start("t")

        preparing.done("t")
        assertEquals(setOf("t"), preparing.tracks.value)

        preparing.done("t")
        assertEquals(emptySet<String>(), preparing.tracks.value)
    }
}
