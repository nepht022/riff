package com.rodrigomachado.riff

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.rodrigomachado.riff.core.data.queue.AppVisibility
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map

/** À vista = alguma tela do Riff começada (o processo pode seguir vivo por causa do "Acesso às notificações"). */
@Module
@InstallIn(SingletonComponent::class)
object AppVisibilityModule {
    @Provides
    fun appVisibility(): AppVisibility = AppVisibility {
        ProcessLifecycleOwner.get().lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
    }
}
