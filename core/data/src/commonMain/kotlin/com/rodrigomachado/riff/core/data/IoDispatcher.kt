package com.rodrigomachado.riff.core.data

import javax.inject.Qualifier

/** O despachante de E/S injetado nas classes que leem pastas e o banco (nos testes, o do teste). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
