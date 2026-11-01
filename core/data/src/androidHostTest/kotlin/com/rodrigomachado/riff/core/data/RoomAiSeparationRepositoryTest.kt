package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RoomAiSeparationRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()
    private lateinit var db: RiffDatabase
    private lateinit var repo: RoomAiSeparationRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomAiSeparationRepository(db.aiSeparationDao(), tmp.root, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun chart(id: String) =
        db.chartDao().replace(ChartEntity(id, "M", "B", null, null, null, "content://$id", "CHART", 1), emptyList())

    private suspend fun separate(id: String): File {
        chart(id)
        val work = repo.workDir(id)
        File(work, SEPARATED_GUITAR).writeBytes(ByteArray(10))
        File(work, SEPARATED_BACKING).writeBytes(ByteArray(5))
        repo.commit(id, work)
        return work
    }

    @Test
    fun aCommittedSeparationHasItsFilesAndCounts() = runTest {
        val work = separate("content://pasta/Música#sng")

        val files = repo.separatedFiles("content://pasta/Música#sng")!!
        assertTrue(files.guitar.isFile && files.backing.isFile)
        assertFalse(work.exists()) // a pasta temporária virou a definitiva
        assertEquals(AiSeparationState.SEPARATED, repo.stateOf("content://pasta/Música#sng"))
        assertEquals(setOf("content://pasta/Música#sng"), repo.separatedIds.first())
        assertEquals(SeparationUsage(songs = 1, bytes = 15), repo.usage.first())
    }

    @Test
    fun aDeclinedSongIsRememberedAndCanBeAskedAgain() = runTest {
        chart("a")
        repo.decline("a")
        assertEquals(AiSeparationState.DECLINED, repo.stateOf("a"))
        assertNull(repo.separatedFiles("a"))

        repo.resetDeclines()
        assertNull(repo.stateOf("a"))
    }

    @Test
    fun deletingAllKeepsTheDeclines() = runTest {
        separate("a")
        chart("b")
        repo.decline("b")

        repo.deleteAll()

        assertNull(repo.stateOf("a"))
        assertEquals(AiSeparationState.DECLINED, repo.stateOf("b"))
        assertEquals(0, tmp.root.listFiles()!!.size)
        assertEquals(SeparationUsage(0, 0), repo.usage.first())
    }

    @Test
    fun aSongThatLeftTheLibraryLosesItsFiles() = runTest {
        separate("a")
        separate("b")

        db.chartDao().deleteNotSeenIn(2) // a reimportação não achou os dois
        chart("b")
        repo.commit(
            "b",
            repo.workDir("b").also {
                File(it, SEPARATED_GUITAR).writeBytes(ByteArray(1))
                File(it, SEPARATED_BACKING).writeBytes(ByteArray(1))
            },
        )
        repo.pruneOrphans()

        assertNull(repo.separatedFiles("a"))
        assertEquals(1, tmp.root.listFiles()!!.size)
    }

    @Test
    fun aWorkDirStartsEmptyEvenAfterACrash() = runTest {
        val first = repo.workDir("a")
        File(first, "lixo").writeText("x")

        assertEquals(0, repo.workDir("a").listFiles()!!.size)
    }

    @Test
    fun committingForAChartThatLeftTheLibraryFailsWithoutLeavingFiles() = runTest {
        val work = repo.workDir("sumiu")
        File(work, SEPARATED_GUITAR).writeBytes(ByteArray(1))

        val error = runCatching { repo.commit("sumiu", work) }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
        assertEquals(0, tmp.root.listFiles()!!.size)
    }
}
