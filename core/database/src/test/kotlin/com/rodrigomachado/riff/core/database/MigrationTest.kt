package com.rodrigomachado.riff.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    // Construtor com arquivo e driver: o de nome do banco não acha o caminho do Robolectric.
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(DB),
        driver = AndroidSQLiteDriver(),
        databaseClass = RiffDatabase::class,
    )

    @Test
    fun version1BecomesVersion2KeepingTheChartsAndAddingOffsets() {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO charts (id, title, artist, album, charter, durationMs, folderUri, format, " +
                    "lastSeenScanId) VALUES ('a', 'Música', 'Banda', NULL, NULL, NULL, 'content://a', 'CHART', 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(2).apply {
            execSQL("INSERT INTO chart_offsets (chartId, offsetMs) VALUES ('a', 15)")
            prepare("SELECT offsetMs FROM chart_offsets WHERE chartId = 'a'").use { statement ->
                statement.step()
                assertEquals(15L, statement.getLong(0))
            }
            close()
        }
    }

    @Test
    fun version2BecomesVersion3MarkingTheOffsetsAlreadyChosen() {
        helper.createDatabase(2).apply {
            execSQL(
                "INSERT INTO charts (id, title, artist, album, charter, durationMs, folderUri, format, " +
                    "lastSeenScanId) VALUES ('a', 'Música', 'Banda', NULL, NULL, NULL, 'content://a', 'CHART', 1)",
            )
            execSQL("INSERT INTO track_matches VALUES ('spotify:track:ajustada', 'a', 1.0, 0, 3000)")
            execSQL("INSERT INTO track_matches VALUES ('spotify:track:nova', 'a', 1.0, 1, 0)")
            close()
        }

        helper.runMigrationsAndValidate(3).apply {
            prepare("SELECT spotifyTrackUri, offsetChosen FROM track_matches ORDER BY spotifyTrackUri").use {
                it.step()
                assertEquals("spotify:track:ajustada", it.getText(0))
                assertEquals(1L, it.getLong(1)) // os +3000 ms do jogador continuam valendo
                it.step()
                assertEquals(0L, it.getLong(1)) // sem ajuste: o Spotify ainda vai alinhar pelo mapa
            }
            close()
        }
    }

    @Test
    fun version3BecomesVersion4KeepingTheLibraryAndAddingTheEncoreTable() {
        helper.createDatabase(3).apply {
            execSQL(
                "INSERT INTO charts (id, title, artist, album, charter, durationMs, folderUri, format, " +
                    "lastSeenScanId) VALUES ('a', 'Música', 'Banda', NULL, NULL, NULL, 'content://a', 'CHART', 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(4).apply {
            execSQL("INSERT INTO encore_charts (chartId, md5) VALUES ('a', 'm5')")
            prepare("SELECT COUNT(*) FROM charts").use {
                it.step()
                assertEquals(1L, it.getLong(0))
            }
            close()
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
