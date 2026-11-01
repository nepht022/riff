package com.rodrigomachado.riff.feature.library

import androidx.compose.runtime.Composable

/**
 * Escolher a pasta da biblioteca: no celular, o seletor do sistema (com a permissão guardada); no PC, a janela de
 * escolher pasta. [onPicked] recebe o endereço da pasta, ou null se a escolha foi cancelada.
 */
@Composable
expect fun rememberFolderPicker(onPicked: (String?) -> Unit): () -> Unit

/** Pede a permissão de notificação (Android 13+) e então roda [action], com ou sem ela; no PC só roda [action]. */
@Composable
expect fun rememberNotificationPermissionThen(action: () -> Unit): () -> Unit
