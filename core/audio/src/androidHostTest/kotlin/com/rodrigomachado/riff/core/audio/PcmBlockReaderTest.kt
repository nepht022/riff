package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmBlockReaderTest {
    private class FakeBlocks(vararg blocks: ByteBuffer?) : DecodedBlocks {
        val queue = ArrayDeque(blocks.toList())
        override var channelCount = 1
        override val floatOutput = false
        override var endOfStream = false
        var released = 0
        var onNext: () -> Unit = {}

        override fun next(): ByteBuffer? {
            onNext()
            return if (queue.isEmpty()) null else queue.removeFirst()
        }

        override fun release() {
            released++
        }
    }

    private fun shorts(vararg values: Short, extraBytes: Int = 0): ByteBuffer {
        val buffer = ByteBuffer.allocate(values.size * 2 + extraBytes).order(ByteOrder.nativeOrder())
        values.forEach { buffer.putShort(it) }
        repeat(extraBytes) { buffer.put(0) }
        buffer.flip()
        return buffer
    }

    @Test
    fun readsTheBlocksInOrder() {
        val blocks = FakeBlocks(shorts(16_384, 0), shorts(-16_384)).apply {
            onNext = { if (queue.isEmpty()) endOfStream = true }
        }
        val out = FloatArray(4)

        assertEquals(3, PcmBlockReader(blocks).read(out, 4))
        assertEquals(0.5f, out[0])
        assertEquals(-0.5f, out[2])
    }

    @Test(timeout = 2_000)
    fun aDecoderThatStopsDeliveringFailsInsteadOfHangingTheAudioThread() {
        val blocks = FakeBlocks()

        assertThrows(IllegalStateException::class.java) { PcmBlockReader(blocks).read(FloatArray(4), 4) }
    }

    @Test(timeout = 2_000)
    fun aBlockEndingInHalfASampleIsReleased() {
        val blocks = FakeBlocks(shorts(100, extraBytes = 1), shorts(200)).apply {
            onNext = { if (queue.isEmpty()) endOfStream = true }
        }

        assertEquals(2, PcmBlockReader(blocks).read(FloatArray(2), 2))
        assertEquals(2, blocks.released)
    }

    @Test
    fun aChannelChangeInTheMiddleFailsLoudlyInsteadOfReadingGarbage() {
        val blocks = FakeBlocks(shorts(1, 2), shorts(3, 4))
        val reader = PcmBlockReader(blocks)
        reader.read(FloatArray(2), 2)
        blocks.onNext = { blocks.channelCount = 2 }

        assertThrows(IllegalStateException::class.java) { reader.read(FloatArray(4), 2) }
    }
}
