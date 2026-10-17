package com.rodrigomachado.riff.core.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AppLanguageStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locales = context.getSystemService(LocaleManager::class.java)
    private val store = SystemAppLanguageStore(context)

    @Test
    fun withNothingChosenTheGameIsInPortuguese() {
        locales.applicationLocales = LocaleList.getEmptyLocaleList()

        assertEquals(AppLanguage.PORTUGUESE, store.current)
    }

    @Test
    fun theFirstStartFixesPortugueseEvenOnAnEnglishPhone() {
        locales.applicationLocales = LocaleList.getEmptyLocaleList()

        store.applyDefault()

        assertEquals("pt-BR", locales.applicationLocales.toLanguageTags())
    }

    @Test
    fun theDefaultDoesNotUndoAChoice() {
        store.set(AppLanguage.ENGLISH)

        store.applyDefault()

        assertEquals(AppLanguage.ENGLISH, store.current)
    }

    @Test
    fun choosingEnglishChangesTheAppLanguage() {
        store.set(AppLanguage.ENGLISH)

        assertEquals("en", locales.applicationLocales.toLanguageTags())
        assertEquals(AppLanguage.ENGLISH, store.current)
    }

    @Test
    fun aLanguageChosenInTheSystemSettingsIsRecognized() {
        locales.applicationLocales = LocaleList.forLanguageTags("en-GB")

        assertEquals(AppLanguage.ENGLISH, store.current)
    }

    @Test
    fun aLanguageTheGameDoesNotHaveCountsAsPortuguese() {
        locales.applicationLocales = LocaleList.forLanguageTags("fr")

        assertEquals(AppLanguage.PORTUGUESE, store.current)
    }

    @Test
    fun beforeAndroid13TheChoiceIsKeptByTheApp() {
        val legacy = LegacyAppLanguage(context)

        legacy.set(AppLanguage.ENGLISH)

        assertEquals(AppLanguage.ENGLISH, legacy.current)
        assertEquals("en", legacy.wrap(context).resources.configuration.locales.toLanguageTags())
    }

    @Test
    fun beforeAndroid13NumbersFollowTheGameLanguage() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH) // celular em inglês
        val legacy = LegacyAppLanguage(context)
        legacy.set(AppLanguage.PORTUGUESE)
        try {
            legacy.wrap(context)

            assertEquals("pt", Locale.getDefault().language) // "1,25×", não "1.25×"
        } finally {
            Locale.setDefault(before)
        }
    }
}
