package com.rodrigomachado.riff

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rodrigomachado.riff.core.data.LegacyAppLanguage
import com.rodrigomachado.riff.core.data.SystemAppLanguageStore
import com.rodrigomachado.riff.core.data.queue.QueuePrefetcher
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.KeyStore
import javax.inject.Inject

@HiltAndroidApp
class RiffApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var language: SystemAppLanguageStore

    // Só criada fora da thread da tela (lê o banco e as preferências).
    @Inject lateinit var queuePrefetcher: dagger.Lazy<QueuePrefetcher>

    // O que ficou para o ranking sem internet vai na abertura (o repositório só nasce fora da thread da tela).
    @Inject lateinit var ranking: dagger.Lazy<RankingRepository>

    // Vive enquanto o processo vive: a fila do Spotify segue em qualquer tela do Riff. Um erro inesperado fica no
    // log em vez de derrubar o app.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w("RiffQueue", "erro na fila do Spotify: ${error.javaClass.simpleName}")
        },
    )

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
        // O login do Spotify (até a versão 0.2.0) guardava um token aqui, cifrado com uma chave do Keystore de mesmo
        // nome; agora o Spotify vem pela sessão de mídia: os dois saem.
        deleteSharedPreferences(OLD_SPOTIFY_TOKEN)
        appScope.launch(Dispatchers.IO) {
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(OLD_SPOTIFY_TOKEN) }
        }
        // Sem escolha guardada, Português (decisão do usuário, mesmo num celular em outro idioma). Escolher
        // "Padrão do sistema" nas configurações do celular também volta a Português na próxima abertura.
        language.applyDefault()
        // Espera a opção e a conexão com o Spotify; nunca conecta sozinho.
        appScope.launch { queuePrefetcher.get().start(appScope) }
        appScope.launch { ranking.get().flush() }
    }
}

private const val OLD_SPOTIFY_TOKEN = "riff_spotify_web"
