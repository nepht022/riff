package com.rodrigomachado.riff.core.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.rodrigomachado.riff.core.model.AppLanguage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Inject

/**
 * Android 13 ou mais novo: o idioma por app do próprio sistema (vale também para notificações e aparece em
 * Configurações do celular → Apps → Riff → Idioma). Antes disso, o app guarda e aplica sozinho ([LegacyAppLanguage]).
 */
class SystemAppLanguageStore @Inject constructor(@ApplicationContext private val context: Context) : AppLanguageStore {
    private val legacy = LegacyAppLanguage(context)

    override val current: AppLanguage
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val chosen = localeManager().applicationLocales
            if (chosen.isEmpty) AppLanguage.PORTUGUESE else AppLanguage.fromTag(chosen[0].toLanguageTag())
        } else {
            legacy.current
        }

    override fun set(language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManager().applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            legacy.set(language)
        }
    }

    /** Na abertura do app: sem escolha, o jogo fica em Português mesmo num celular em outro idioma. */
    fun applyDefault() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && localeManager().applicationLocales.isEmpty) {
            set(AppLanguage.PORTUGUESE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(): LocaleManager = context.getSystemService(LocaleManager::class.java)
}

/** Antes do Android 13: a escolha fica nas preferências do app e cada tela é aberta já no idioma dela ([wrap]). */
class LegacyAppLanguage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val current: AppLanguage get() = AppLanguage.fromTag(prefs.getString(KEY, null))

    fun set(language: AppLanguage) {
        prefs.edit().putString(KEY, language.tag).apply()
    }

    /**
     * O contexto no idioma escolhido, para o `attachBaseContext` do app (notificações, separação) e da tela. Também
     * vira o idioma padrão do processo: números formatados ("1,25×") seguem o jogo, não o celular.
     */
    fun wrap(base: Context): Context {
        val locales = LocaleList.forLanguageTags(current.tag)
        Locale.setDefault(locales[0])
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(locales)
        return base.createConfigurationContext(configuration)
    }

    private companion object {
        const val PREFS = "riff_language"
        const val KEY = "language"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LanguageModule {
    @Binds
    abstract fun appLanguageStore(impl: SystemAppLanguageStore): AppLanguageStore
}
