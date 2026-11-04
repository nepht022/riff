package com.rodrigomachado.riff.feature.report

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidDeviceInfo @Inject constructor(@ApplicationContext private val context: Context) : DeviceInfoSource {
    override fun read() = DeviceInfo.android(
        appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
        release = Build.VERSION.RELEASE,
        model = "${Build.MANUFACTURER} ${Build.MODEL}",
        language = context.resources.configuration.locales[0].toLanguageTag(),
    )
}
