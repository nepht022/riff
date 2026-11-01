package com.rodrigomachado.riff.desktop

import com.rodrigomachado.riff.core.model.AppLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

/** O idioma do jogo no PC: guardado num arquivo da pasta de dados, Português sem escolha. */
class DesktopAppLanguageStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "language.txt")

    @After
    fun restoreLocale() = DesktopLocale.applyDefault()

    @Test
    fun withoutAFileTheLanguageIsPortuguese() {
        assertEquals(AppLanguage.PORTUGUESE, DesktopAppLanguageStore(file).current)
    }

    @Test
    fun theChosenLanguageIsKeptForTheNextOpening() {
        DesktopAppLanguageStore(file).set(AppLanguage.ENGLISH)

        val reopened = DesktopAppLanguageStore(file)

        assertEquals(AppLanguage.ENGLISH, reopened.current)
        assertEquals(AppLanguage.ENGLISH, reopened.language.value)
    }

    @Test
    fun aBrokenFileFallsBackToPortuguese() {
        file.writeText("xx-??")

        assertEquals(AppLanguage.PORTUGUESE, DesktopAppLanguageStore(file).current)
    }

    @Test
    fun choosingTheLanguageChangesTheDefaultLocale() {
        val store = DesktopAppLanguageStore(file)

        store.set(AppLanguage.ENGLISH)

        assertEquals("en", Locale.getDefault().language)
        assertEquals(AppLanguage.ENGLISH, store.language.value)
    }

    @Test
    fun openingAppliesTheSavedLanguage() {
        file.writeText("en")

        DesktopAppLanguageStore(file).apply()

        assertEquals("en", Locale.getDefault().language)
    }
}
