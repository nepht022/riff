package com.rodrigomachado.riff.core.separation

import android.app.ActivityManager
import android.content.Context
import java.io.File

class AndroidDeviceInfo(context: Context, private val dir: File) : DeviceInfo {
    override val totalRamBytes: Long = ActivityManager.MemoryInfo().also {
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
    }.totalMem

    override fun freeBytes(): Long = dir.apply { mkdirs() }.usableSpace
}
