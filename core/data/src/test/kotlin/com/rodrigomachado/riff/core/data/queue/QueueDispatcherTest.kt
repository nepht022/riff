package com.rodrigomachado.riff.core.data.queue

import android.os.Process
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Os downloads da fila rodam em prioridade baixa: a pista da partida vem antes (spec Spotify contínuo §6). */
@RunWith(RobolectricTestRunner::class)
class QueueDispatcherTest {
    @Test
    fun queueWorkRunsAtBackgroundPriority() = runBlocking {
        val priority = withContext(queueDispatcher()) { Process.getThreadPriority(Process.myTid()) }

        assertEquals(Process.THREAD_PRIORITY_BACKGROUND, priority)
    }
}
