package com.rodrigomachado.riff.core.encore

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object EncoreModule {
    @Provides
    fun encoreClient(): EncoreClient = EncoreClient(UrlConnectionTransport())
}
