package com.rodrigomachado.riff.desktop

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * A trava de um Riff só por pasta de dados (`riff.lock`): dois abertos disputariam a fila e apagariam a pasta
 * temporária um do outro. O sistema solta a trava se o processo morrer; [close] a solta ao fechar.
 */
class InstanceLock private constructor(private val channel: FileChannel, private val lock: FileLock) : AutoCloseable {
    override fun close() {
        try {
            lock.release()
        } finally {
            channel.close()
        }
    }

    companion object {
        const val FILE_NAME = "riff.lock"

        /** A trava de [dataDir], ou `null` se outro Riff (outro processo ou este mesmo) já a tem. */
        fun tryAcquire(dataDir: File): InstanceLock? {
            dataDir.mkdirs()
            val channel = FileChannel.open(
                File(dataDir, FILE_NAME).toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null // já travada por este processo (dois DesktopGraph nos testes)
            } catch (e: IOException) {
                channel.close()
                throw e
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return InstanceLock(channel, lock)
        }
    }
}
