package com.rodrigomachado.riff.feature.report

import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** O que vai junto do relato quando a pessoa deixa "Incluir informações do aparelho" ligado. */
fun interface DeviceInfoSource {
    fun read(): DeviceInfo
}

class AndroidDeviceInfo @Inject constructor(@ApplicationContext private val context: Context) : DeviceInfoSource {
    override fun read() = DeviceInfo(
        appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
        android = Build.VERSION.RELEASE,
        model = "${Build.MANUFACTURER} ${Build.MODEL}",
        language = context.resources.configuration.locales[0].toLanguageTag(),
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReportModule {
    @Binds
    abstract fun reportSender(impl: AppsScriptReportSender): ReportSender

    @Binds
    abstract fun deviceInfo(impl: AndroidDeviceInfo): DeviceInfoSource
}
