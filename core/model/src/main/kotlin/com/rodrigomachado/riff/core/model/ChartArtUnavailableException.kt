package com.rodrigomachado.riff.core.model

/**
 * Não deu para procurar a imagem do mapa agora: o mapa ainda não está na biblioteca (o convidado da sala vai baixar a
 * música) ou a pasta dele perdeu o acesso. É diferente de "o mapa não tem imagem": quem guarda as capas não guarda
 * isto, e o próximo pedido procura de novo.
 */
class ChartArtUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
