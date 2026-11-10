package com.rodrigomachado.riff

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.rodrigomachado.riff.core.data.LegacyAppLanguage
import com.rodrigomachado.riff.core.data.SystemAppLanguageStore
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.room.RoomPresence
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

    // O que ficou para o ranking sem internet vai na abertura (o repositório só nasce fora da thread da tela).
    @Inject lateinit var ranking: dagger.Lazy<RankingRepository>

    // A presença na sala (o batimento entre as telas), parada com o app em segundo plano.
    @Inject lateinit var roomPresence: dagger.Lazy<RoomPresence>

    // Vive enquanto o processo vive (o envio ao ranking e a limpeza da abertura). Um erro inesperado fica no log
    // em vez de derrubar o app.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w("RiffApp", "erro em segundo plano: ${error.javaClass.simpleName}")
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
        appScope.launch { ranking.get().flush() }
        // Na sala, o batimento segue em qualquer tela do app e para em segundo plano (os outros me veem sumir em
        // 60 s, como antes); criada na primeira volta ao primeiro plano.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = roomPresence.get().setForeground(true)

                override fun onStop(owner: LifecycleOwner) = roomPresence.get().setForeground(false)
            },
        )
    }
}

private const val OLD_SPOTIFY_TOKEN = "riff_spotify_web"
