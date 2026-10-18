package com.rodrigomachado.riff.core.spotify.session

import android.service.notification.NotificationListenerService

/**
 * Existe só para o Android liberar a leitura das sessões de mídia ("Acesso às notificações"). O Riff não lê nem guarda
 * notificações: nenhum método é sobrescrito.
 */
class RiffNotificationListener : NotificationListenerService()
