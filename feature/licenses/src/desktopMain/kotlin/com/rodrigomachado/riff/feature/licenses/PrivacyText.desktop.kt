package com.rodrigomachado.riff.feature.licenses

import com.rodrigomachado.riff.feature.licenses.resources.Res
import com.rodrigomachado.riff.feature.licenses.resources.licenses_privacy_text_pc
import org.jetbrains.compose.resources.StringResource

// Sem o ranking (chega na etapa 4d, que atualiza este texto).
internal actual val privacyText: StringResource get() = Res.string.licenses_privacy_text_pc
