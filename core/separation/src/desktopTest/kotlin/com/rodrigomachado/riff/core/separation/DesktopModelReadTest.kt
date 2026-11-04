package com.rodrigomachado.riff.core.separation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.management.ManagementFactory
import kotlin.random.Random

/** O modelo (130 MB) lido uma vez só para a heap: num array do tamanho exato, sem cópia passageira. */
class DesktopModelReadTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val threads = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean

    @Test
    fun theModelIsReadIntoAnArrayOfItsExactSize() {
        assumeTrue("sem a conta de memória por thread neste Java", threads?.isThreadAllocatedMemorySupported == true)
        val expected = Random(SEED).nextBytes(SIZE)
        val file = tmp.newFile("modelo.onnx").apply { writeBytes(expected) }
        val url = file.toURI().toURL()
        DesktopSeparation.readModel(url) // aquece (classes carregadas fora da conta)

        val id = Thread.currentThread().id
        val before = threads!!.getThreadAllocatedBytes(id)
        val bytes = DesktopSeparation.readModel(url)
        val allocated = threads.getThreadAllocatedBytes(id) - before

        assertArrayEquals(expected, bytes)
        assertTrue("alocou $allocated bytes para ler $SIZE", allocated < SIZE + SLACK_BYTES)
    }

    private companion object {
        const val SIZE = 32 shl 20
        const val SLACK_BYTES = 1L shl 20
        const val SEED = 4
    }
}
