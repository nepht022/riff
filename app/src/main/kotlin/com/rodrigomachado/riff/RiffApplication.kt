package com.rodrigomachado.riff

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rodrigomachado.riff.core.data.LegacyAppLanguage
import com.rodrigomachado.riff.core.data.SystemAppLanguageStore
import com.rodrigomachado.riff.core.data.queue.QueuePrefetcher
import com.rodrigomachado.riff.core.spotify.ForegroundActivity
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class RiffApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var language: SystemAppLanguageStore

    @Inject lateinit var queuePrefetcher: QueuePrefetcher

    // Vive enquanto o processo vive: a fila do Spotify segue em qualquer tela do Riff.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // O WorkManager cria os Workers pelo Hilt (o SeparationWorker recebe o runner injetado).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // Antes do Android 13: o idioma do jogo vale também fora da tela (notificações da separação).
    override fun attachBaseContext(base: Context) {
        val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        super.attachBaseContext(if (legacy) LegacyAppLanguage(base).wrap(base) else base)
    }

    override fun onCreate() {
        super.onCreate()
        // Antes da primeira tela: a conexão com o Spotify precisa saber qual tela está aberta.
        registerActivityLifecycleCallbacks(ForegroundActivity)
        // Sem escolha guardada, Português (decisão do usuário, mesmo num celular em outro idioma). Escolher
        // "Padrão do sistema" nas configurações do celular também volta a Português na próxima abertura.
        language.applyDefault()
        // Espera a opção e a conexão com o Spotify; nunca conecta sozinho.
        queuePrefetcher.start(appScope)
    }
}
