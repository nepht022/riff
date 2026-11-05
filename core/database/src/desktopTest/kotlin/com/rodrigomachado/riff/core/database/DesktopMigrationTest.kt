package com.rodrigomachado.riff.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Um banco do PC de antes da 4d (versão 13), migrado pelo [riffDatabase] do próprio app, com o SQLite embutido: as
 * partidas e a fila continuam e ganham a tabela "celular" (no PC o app lê essa marca como "com palheta").
 */
class DesktopMigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aVersion13DatabaseOpensOnThePcWithTheRankingTable() = runTest {
        val file = File(folder.root, "riff.db")
        // O diretório de trabalho dos testes é o do módulo: os esquemas exportados ficam em schemas/.
        val helper = MigrationTestHelper(
            schemaDirectoryPath = File("schemas").toPath(),
            databasePath = file.toPath(),
            driver = BundledSQLiteDriver(),
            databaseClass = RiffDatabase::class,
        )
        helper.createDatabase(13).apply {
            execSQL(
                "INSERT INTO charts (id, title, artist, album, charter, durationMs, folderUri, format, " +
                    "lastSeenScanId, separateGuitar) VALUES ('a', 'T', 'B', NULL, NULL, NULL, 'C:\\m', 'CHART', 1, 0)",
            )
            execSQL(
                "INSERT INTO scores (chartId, difficulty, score, accuracy, maxCombo, perfect, good, miss, " +
                    "playedAtEpochMs, cleanStreakMs) VALUES ('a', 'HARD', 100, 1.0, 1, 1, 0, 0, 1, 500)",
            )
            execSQL(
                "INSERT INTO ranking_pending (scoreId, chartId, difficulty, title, artist, points, accuracy, combo, " +
                    "perfect, cleanMs, createdAtEpochMs, notes) " +
                    "VALUES (1, 'a', 'HARD', 'T', 'A', 100, 0.9, 5, 4, NULL, 1, 9)",
            )
            close()
        }

        val database = riffDatabase(file)
        val scores = database.scoreDao().all()
        val queued = database.rankingPendingDao().all()
        database.close()

        assertEquals(
            listOf(Triple(100L, 500L, "celular")),
            scores.map { Triple(it.score, it.cleanStreakMs, it.rankingTable) },
        )
        assertEquals(listOf(Triple(100L, 9, "celular")), queued.map { Triple(it.points, it.notes, it.rankingTable) })
        BundledSQLiteDriver().open(file.absolutePath).apply {
            prepare("SELECT dflt_value FROM pragma_table_info('scores') WHERE name = 'rankingTable'").use {
                it.step()
                assertEquals("'celular'", it.getText(0))
            }
            close()
        }
    }
}
