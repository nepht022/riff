package com.rodrigomachado.riff.core.data.queue

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Se a fila pode pesquisar e baixar agora: rede, espaço e pasta da biblioteca com escrita. */
interface DownloadConditions {
    suspend fun canDownload(useMobileData: Boolean): Boolean
}

class AndroidDownloadConditions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folders: LibraryFolderStore,
    private val access: FolderAccess,
) : DownloadConditions {
    override suspend fun canDownload(useMobileData: Boolean): Boolean =
        allowedNetwork(useMobileData) && context.filesDir.usableSpace >= MIN_FREE_BYTES && writableFolder()

    /** Sem "dados móveis", só rede sem cobrança por uso (Wi-Fi). */
    private fun allowedNetwork(useMobileData: Boolean): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return useMobileData || network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private suspend fun writableFolder(): Boolean = folders.rootUri.first()?.let(access::canWrite) ?: false

    private companion object {
        const val MIN_FREE_BYTES = 200L * 1024 * 1024
    }
}
