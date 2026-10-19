package com.rodrigomachado.riff.feature.nowplaying

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings

/**
 * Abre a tela "Acesso às notificações". Celulares sem ela (Android Go) abrem as Configurações; sem nenhuma das duas,
 * nada acontece (em vez de fechar o app).
 */
internal fun openNotificationAccess(start: (Intent) -> Unit) {
    for (action in listOf(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, Settings.ACTION_SETTINGS)) {
        try {
            start(Intent(action))
            return
        } catch (_: ActivityNotFoundException) {
            // tenta a próxima
        }
    }
}
