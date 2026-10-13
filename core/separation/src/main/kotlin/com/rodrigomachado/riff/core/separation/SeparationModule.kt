package com.rodrigomachado.riff.core.separation

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.rodrigomachado.riff.core.audio.MediaCodecPcmSource
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.data.SeparationsDir
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SeparationBindsModule {
    @Binds
    abstract fun scheduler(impl: WorkSeparationScheduler): SeparationScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object SeparationModule {
    @Provides
    @Singleton
    fun deviceInfo(@ApplicationContext context: Context, @SeparationsDir dir: File): DeviceInfo =
        AndroidDeviceInfo(context, dir)

    @Provides
    @Suppress("LongParameterList") // as dependências do GuitarSeparationRunner
    fun runner(
        @ApplicationContext context: Context,
        repo: AiSeparationRepository,
        probe: ChartAudioProbe,
        opener: RandomReadOpener,
        progress: SeparationProgress,
        device: DeviceInfo,
    ): GuitarSeparationRunner = GuitarSeparationRunner(
        repo = repo,
        probe = probe,
        loader = { uri, scratch ->
            SongPcmLoader { MediaCodecPcmSource.open(context, it, opener) }.load(uri, scratch)
        },
        models = { useArena -> OrtDemucsModel(modelBuffer(context), useArena = useArena) },
        sinks = { dir -> M4aStemSink(dir) },
        progress = progress,
        device = device,
        thermal = thermalGate(context),
        scratch = context.cacheDir,
    )

    /** O modelo mapeado direto do APK (asset sem compressão): não ocupa a heap nem é copiado. */
    private fun modelBuffer(context: Context) = context.assets.openFd(Demucs.MODEL_ASSET).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use {
            it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    private fun thermalGate(context: Context): ThermalGate {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalGate(hot = { false }, cool = { true })
        val power = context.getSystemService(PowerManager::class.java)
        return ThermalGate(
            hot = { power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE },
            cool = { power.currentThermalStatus <= PowerManager.THERMAL_STATUS_MODERATE },
        )
    }
}
