package com.rodrigomachado.riff.feature.report

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReportModule {
    @Binds
    abstract fun deviceInfo(impl: AndroidDeviceInfo): DeviceInfoSource

    companion object {
        @Provides
        fun reportSender(): ReportSender = AppsScriptReportSender(ReportConfig.REPORT_URL, UrlConnectionJsonPost)

        /** Um só no app; no celular conta também o tempo com a tela desligada, como antes. */
        @Provides
        @Singleton
        fun reportCooldown(): ReportCooldown = ReportCooldown(SystemClock::elapsedRealtime)
    }
}
