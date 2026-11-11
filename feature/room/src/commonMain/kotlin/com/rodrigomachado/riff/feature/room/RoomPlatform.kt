package com.rodrigomachado.riff.feature.room

import androidx.compose.runtime.Composable

/** O Voltar do sistema (celular); no PC, nada: o Esc vem pelo `menuKeys` da tela. */
@Composable
internal expect fun RoomBackHandler(onBack: () -> Unit)

/** O log de erros da sala (só o que não tem outro tratamento); nunca lança. */
internal expect fun roomLog(message: String, error: Throwable)

/** O botão do código da sala: no celular, compartilha o convite; no PC, copia o código. */
internal fun interface CodeShare {
    /**
     * Compartilha o [invite] ("Entra na minha sala do Riff: KQZX") ou copia o [code]; devolve true quando copiou (a
     * tela diz "Código copiado"). Nunca lança.
     */
    suspend fun share(code: String, invite: String): Boolean
}

/** O compartilhar desta plataforma: no celular, o "Compartilhar" do sistema; no PC, a área de transferência. */
@Composable
internal expect fun rememberCodeShare(): CodeShare

/** No PC o botão copia (a descrição diz "Copiar o código"); no celular, compartilha. */
internal expect val codeShareCopies: Boolean
