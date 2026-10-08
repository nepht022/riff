package com.rodrigomachado.riff

import android.app.Application
import com.rodrigomachado.riff.core.spotify.ForegroundActivity
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RiffApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Antes da primeira tela: a conexão com o Spotify precisa saber qual tela está aberta.
        registerActivityLifecycleCallbacks(ForegroundActivity)
    }
}
