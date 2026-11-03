package com.rodrigomachado.riff.feature.search

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

private const val TAG = "RiffSearch"

internal actual fun searchLog(message: String, error: Throwable) {
    Log.w(TAG, message, error)
}

@Composable
internal actual fun rememberFolderPicker(startAt: String?, onPicked: (String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        onPicked(uri?.toString())
    }
    // O seletor abre na pasta de hoje: basta confirmar a mesma.
    return { launcher.launch(startAt?.let(Uri::parse)) }
}
