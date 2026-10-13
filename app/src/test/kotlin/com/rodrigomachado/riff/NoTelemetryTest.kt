package com.rodrigomachado.riff

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoTelemetryTest {
    /** O ONNX Runtime traz um provedor que liga telemetria (rede) toda vez que o app abre: o Riff não usa. */
    @Test
    fun theOnnxRuntimeTelemetryDoesNotStartWithTheApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providers = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS)
            .providers.orEmpty()
            .map { it.name }

        assertFalse(providers.toString(), "ai.onnxruntime.TelemetryInitializer" in providers)
    }
}
