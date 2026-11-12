package com.rodrigomachado.riff.feature.room

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun RoomBackHandler(onBack: () -> Unit) = BackHandler(onBack = onBack)

internal actual fun roomLog(message: String, error: Throwable) {
    // Fora do Android (nos testes da JVM), o Log lança: o log nunca derruba nada.
    runCatching { Log.w("RiffRoom", message, error) }
}

/** O "Compartilhar" do sistema com o convite em texto (WhatsApp, mensagem…); nada é copiado. */
@Composable
internal actual fun rememberCodeShare(): CodeShare {
    val context = LocalContext.current
    return remember(context) {
        CodeShare { _, invite ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, invite)
            val chooser = Intent.createChooser(send, null)
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
            } catch (e: ActivityNotFoundException) {
                roomLog("sem app para compartilhar", e)
            }
            false
        }
    }
}

internal actual val codeShareCopies: Boolean = false
