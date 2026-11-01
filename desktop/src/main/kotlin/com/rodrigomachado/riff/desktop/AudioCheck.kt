package com.rodrigomachado.riff.desktop

import com.rodrigomachado.riff.core.audio.DesktopPlayers
import com.rodrigomachado.riff.core.audio.GuitarTrackControl
import com.rodrigomachado.riff.core.audio.JavaSoundSink
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.chart.sng.SngRef
import com.rodrigomachado.riff.core.data.folder.FileFolderSource
import com.rodrigomachado.riff.core.data.folder.FileRandomReadOpener
import com.rodrigomachado.riff.core.data.folder.SngFolderSource
import com.rodrigomachado.riff.core.data.folder.pcUri
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartAudioFinder
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs

/**
 * Mede o áudio no PC: `./scripts/gw :desktop:runAudioCheck --args="<pasta do mapa ou .sng>"`. Toca 20 s cortando e
 * devolvendo a guitarra a cada 2 s (faixas separadas) e imprime, a cada segundo, a deriva entre o relógio e a
 * posição da música (o relógio da partida), além do tempo até o som sair.
 */
fun main(args: Array<String>) = runBlocking {
    // O Gradle separa o --args nos espaços: o caminho volta inteiro.
    val path = args.joinToString(" ").ifBlank { error("passe a pasta do mapa (ou o .sng)") }
    val target = File(path)
    val folder = if (target.isFile) SngRef.packageUri(pcUri(target)) else pcUri(target)
    val randomRead = FileRandomReadOpener()
    val audio = ChartAudioFinder.find(SngFolderSource(FileFolderSource(), randomRead).listChildren(folder))
        ?: error("sem áudio em $path")
    val player: SongPlayer = when (audio) {
        is ChartAudio.Stems -> DesktopPlayers.stemPlayers(randomRead).create(audio.guitarUri, audio.backingUris)
        is ChartAudio.Single -> DesktopPlayers.songPlayers(randomRead).create()
    }
    println("Áudio: $audio")
    println("Buffer da saída: ${JavaSoundSink.BUFFER_FRAMES * MS_PER_S / SAMPLE_RATE} ms")
    player.prepare((audio as? ChartAudio.Single)?.uri ?: "")
    measure(player)
    player.release()
}

private suspend fun measure(player: SongPlayer) {
    val guitar = player as? GuitarTrackControl
    val asked = System.nanoTime()
    player.play()
    waitForSound(player)
    val startedNs = System.nanoTime()
    println("Até o som sair: ${(startedNs - asked) / NS_PER_MS} ms")
    val startPosition = player.positionMs
    var worst = 0L
    var audible = true
    for (second in 1..SECONDS) {
        kotlinx.coroutines.delay(MS_PER_S.toLong())
        if (guitar != null && second % 2 == 0) {
            audible = !audible
            guitar.setGuitarAudible(audible)
        }
        val wall = (System.nanoTime() - startedNs) / NS_PER_MS
        val drift = wall - (player.positionMs - startPosition)
        worst = maxOf(worst, abs(drift))
        println(
            "t=${second}s  posição=${player.positionMs} ms  deriva=$drift ms" +
                (guitar?.let { "  guitarra=${if (audible) "ligada" else "cortada"}" } ?: ""),
        )
    }
    println("Deriva máxima em $SECONDS s: $worst ms")
    player.pause()
    kotlinx.coroutines.delay(MS_PER_S.toLong())
    val resumed = System.nanoTime()
    player.play()
    waitForSound(player)
    println("Ao continuar, até o som sair: ${(System.nanoTime() - resumed) / NS_PER_MS} ms")
}

private const val SECONDS = 20
private const val SAMPLE_RATE = 48_000
private const val MS_PER_S = 1_000
private const val NS_PER_MS = 1_000_000L

/** Espera o som sair lendo a posição a cada 1 ms, como a partida faz a cada quadro (é a leitura que atualiza). */
private suspend fun waitForSound(player: SongPlayer) {
    while (true) {
        player.positionMs
        if (!player.isStarting) return
        kotlinx.coroutines.delay(1)
    }
}
