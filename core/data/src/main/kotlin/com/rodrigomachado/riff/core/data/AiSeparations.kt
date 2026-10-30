package com.rodrigomachado.riff.core.data

import android.database.SQLException
import com.rodrigomachado.riff.core.database.AiSeparationDao
import com.rodrigomachado.riff.core.database.AiSeparationEntity
import com.rodrigomachado.riff.core.database.SeparationQueueDao
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Qualifier

const val SEPARATED_GUITAR = "guitar.m4a"
const val SEPARATED_BACKING = "backing.m4a"

/** `filesDir/separacoes`: uma pasta por música separada pela IA. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SeparationsDir

enum class AiSeparationState { SEPARATED, DECLINED }

/** Os dois arquivos que a IA gerou: a guitarra e o resto da música. */
data class SeparatedFiles(val guitar: File, val backing: File)

interface AiSeparationRepository {
    val separatedIds: Flow<Set<String>>
    val usage: Flow<SeparationUsage>

    suspend fun stateOf(chartId: String): AiSeparationState?

    suspend fun separatedFiles(chartId: String): SeparatedFiles?

    /** Pasta temporária vazia para gravar a separação de [chartId]. */
    fun workDir(chartId: String): File

    /** Troca a pasta temporária pela definitiva e marca a música como separada. */
    suspend fun commit(chartId: String, workDir: File)

    suspend fun decline(chartId: String)

    suspend fun resetDeclines()

    /** Apaga todas as separações (as recusas ficam). */
    suspend fun deleteAll()

    /** Apaga os arquivos de músicas que saíram da biblioteca (a linha já saiu em cascata). */
    suspend fun pruneOrphans()
}

/** Põe músicas na fila de separação, cancela e acompanha; separa uma por vez. */
interface SeparationScheduler {
    /** A música que está separando (ou a falha da última). */
    val status: StateFlow<SeparationStatus>

    /** As que esperam a vez, sem a que está separando. */
    val queue: StateFlow<List<QueuedSeparation>>

    /** Fila parada esperando o celular ir para a tomada ("Separar só carregando"). */
    val waitingForCharger: StateFlow<Boolean>

    /** Menos de 4 GB de RAM: a pergunta avisa que pode não funcionar. */
    val lowMemoryDevice: Boolean

    /** No fim da fila, na ordem dada. */
    fun enqueue(songs: List<QueuedSeparation>)

    /** Cancela [chartId] se for a que está separando; a fila segue. */
    fun cancelCurrent(chartId: String)

    fun remove(chartId: String)

    /** Cancela a atual e esvazia a fila. */
    fun cancelAll()

    /** Some com o aviso de falha. */
    fun dismissFailure()
}

class RoomAiSeparationRepository @Inject constructor(
    private val dao: AiSeparationDao,
    @SeparationsDir private val root: File,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AiSeparationRepository {
    override val separatedIds = dao.observeIds(AiSeparationState.SEPARATED.name).map { it.toSet() }
    override val usage = dao.observeUsage().map { SeparationUsage(it.songs, it.bytes) }

    override suspend fun stateOf(chartId: String) = dao.stateOf(chartId)?.let(AiSeparationState::valueOf)

    override suspend fun separatedFiles(chartId: String): SeparatedFiles? = withContext(io) {
        if (stateOf(chartId) != AiSeparationState.SEPARATED) return@withContext null
        val dir = File(root, dirName(chartId))
        SeparatedFiles(File(dir, SEPARATED_GUITAR), File(dir, SEPARATED_BACKING))
            .takeIf { it.guitar.isFile && it.backing.isFile }
    }

    override fun workDir(chartId: String): File = File(root, dirName(chartId) + TMP).apply {
        deleteRecursively()
        mkdirs()
    }

    override suspend fun commit(chartId: String, workDir: File) = withContext(io) {
        val target = File(root, dirName(chartId))
        target.deleteRecursively()
        if (!workDir.renameTo(target)) throw IOException("não deu para guardar a separação de $chartId")
        val bytes = target.listFiles().orEmpty().sumOf { it.length() }
        try {
            dao.upsert(AiSeparationEntity(chartId, AiSeparationState.SEPARATED.name, bytes, System.currentTimeMillis()))
        } catch (e: SQLException) {
            target.deleteRecursively() // o mapa saiu da biblioteca durante a separação
            throw IOException("o mapa $chartId saiu da biblioteca", e)
        }
    }

    override suspend fun decline(chartId: String) =
        dao.upsert(AiSeparationEntity(chartId, AiSeparationState.DECLINED.name, 0, System.currentTimeMillis()))

    override suspend fun resetDeclines() = dao.deleteState(AiSeparationState.DECLINED.name)

    override suspend fun deleteAll() = withContext(io) {
        dao.deleteState(AiSeparationState.SEPARATED.name)
        root.listFiles().orEmpty().filterNot { it.name.endsWith(TMP) }.forEach { it.deleteRecursively() }
    }

    override suspend fun pruneOrphans() = withContext(io) {
        val keep = dao.idsWith(AiSeparationState.SEPARATED.name).map(::dirName).toSet()
        root.listFiles().orEmpty()
            .filter { !it.name.endsWith(TMP) && it.name !in keep }
            .forEach { it.deleteRecursively() }
    }

    private companion object {
        const val TMP = ".tmp"

        /** O id do mapa é uma URI do SAF: vira um nome de pasta seguro. */
        fun dirName(chartId: String): String =
            MessageDigest.getInstance("SHA-1").digest(chartId.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** As músicas esperando a vez de separar, na ordem de chegada (a que está separando continua até terminar). */
interface SeparationQueue {
    val items: Flow<List<QueuedSeparation>>

    /** No fim da fila, na ordem dada; a que já está na fila mantém o lugar. */
    suspend fun add(songs: List<QueuedSeparation>)

    suspend fun next(): QueuedSeparation?

    suspend fun remove(chartId: String)

    suspend fun clear()

    /** Conta mais uma tentativa de separar [chartId] (antes de começar) e devolve quantas já são. */
    suspend fun startAttempt(chartId: String): Int
}

class RoomSeparationQueue @Inject constructor(private val dao: SeparationQueueDao) : SeparationQueue {
    override val items = dao.observe().map { rows -> rows.map { QueuedSeparation(it.chartId, it.title) } }

    override suspend fun add(songs: List<QueuedSeparation>) = dao.append(songs.map { it.chartId to it.title })

    override suspend fun next() = dao.first()?.let { QueuedSeparation(it.chartId, it.title) }

    override suspend fun remove(chartId: String) = dao.delete(chartId)

    override suspend fun clear() = dao.clear()

    override suspend fun startAttempt(chartId: String) = dao.startAttempt(chartId)
}
