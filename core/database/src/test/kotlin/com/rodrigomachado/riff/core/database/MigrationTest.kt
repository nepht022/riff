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

    private companion object {
        const val DB = "migration-test"
    }
}
