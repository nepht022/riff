package com.rodrigomachado.riff

import com.rodrigomachado.riff.core.data.game.ChartArtLoader
import com.rodrigomachado.riff.core.data.importer.ChartImportListeners
import com.rodrigomachado.riff.core.ui.art.ChartThumbImages
import com.rodrigomachado.riff.feature.gameplay.DecodedChartArtImages
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * As capas das listas no celular (Biblioteca, Resultado, sala, tela inicial): a mesma leitura da imagem do mapa da
 * partida, decodificada pequena, e uma memória só para o app inteiro (voltar a uma lista não relê as capas). O fundo
 * da pista continua sendo o `ChartArtImages` do `:feature:gameplay`. Ao fim de cada importação (Reimportar, Trocar
 * pasta, download), a memória se esquece: a pasta pode ter ganhado a capa.
 */
@Module
@InstallIn(SingletonComponent::class)
object ChartArtModule {
    @Provides
    @Singleton
    fun chartThumbs(loader: ChartArtLoader, imports: ChartImportListeners): ChartThumbImages =
        ChartThumbImages(DecodedChartArtImages.thumbs(loader)).also { thumbs ->
            imports.add(thumbs::clear)
        }
}
