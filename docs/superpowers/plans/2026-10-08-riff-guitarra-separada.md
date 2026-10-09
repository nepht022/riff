# Riff: guitarra separada e som de acerto — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No modo biblioteca, quando a pasta do mapa trouxer a guitarra separada, tocar todas as faixas juntas e cortar a guitarra no erro (como no Guitar Hero); nos outros casos, trocar o estalo de acerto por uma nota de guitarra elétrica, uma por trilha, num volume que fica atrás da música.

**Architecture:** O `ChartTrackLoader` passa a devolver `ChartAudio` (`Single` ou `Stems`). Em `:core:audio`, um `StemMixer` em Kotlin puro mistura as faixas com rampa de ganho; o `StemSongPlayer` decodifica cada faixa (`PcmSource`, MediaCodec), mistura numa thread de áudio e escreve num único `AudioSink` (AudioTrack), cuja contagem de quadros é o relógio. A `GameSession` usa `GuitarTrackControl` quando o player o tiver; senão toca `sfx.hit(lane)`.

**Tech Stack:** Kotlin, coroutines, Android MediaExtractor/MediaCodec/AudioTrack (PCM float), SoundPool, Hilt, Compose, JUnit 4 + Robolectric, Python 3 (síntese dos sons).

**Spec:** `docs/superpowers/specs/2026-10-08-guitarra-separada-som-acerto-design.md` (complementa `2026-10-05-riff-design.md` §4 e `2026-10-07-identidade-visual-design.md` §6).

## Global Constraints

- minSdk 26; compileSdk 37; targetSdk 36; Kotlin 2.4.20; AGP 9.4.1; Gradle via `./scripts/gw` (Windows a partir do WSL).
- Sons sintetizados por `scripts/gerar_sfx.py`, sem amostras de terceiros; nada de assets do Guitar Hero ou de projetos que os redesenham.
- Som de acerto: timbre "crunch leve" (Karplus-Strong, `tanh` com ganho 2,5, passa-baixas ~4 kHz e ~5,5 kHz, passa-altas 120 Hz e 70 Hz); uma nota por trilha: Mi3 164,81 Hz, Sol3 196,00 Hz, Lá3 220,00 Hz, Si3 246,94 Hz, Ré4 293,66 Hz; 0,25 s com fade-out de 60 ms; pico ≤ −12 dBFS; arquivos `sfx_hit_1.wav` a `sfx_hit_5.wav`; o `sfx_hit.wav` atual é removido; o som de erro não muda.
- Faixas reconhecidas (extensões `ogg`, `opus`, `mp3`, `wav`): guitarra `guitar`; acompanhamento `song`, `rhythm`, `bass`, `drums`, `drums_1` a `drums_4`, `vocals`, `vocals_1`, `vocals_2`, `keys`, `crowd`. Modo de faixas só com `guitar.*` + pelo menos uma de acompanhamento.
- Rampa de ganho da guitarra: 30 ms. Guitarra começa audível. No modo de faixas não há som de acerto.
- Falha nas faixas: log com tag `RiffAudio`, cai no modo de um arquivo e mostra uma vez "Tocando sem a guitarra separada" (pt) / "Playing without the separate guitar" (en).
- Sem alocação por bloco de áudio; depois de `release` nenhuma thread continua rodando.
- Strings em resources, pt-BR padrão e en segunda língua. Detekt e Lint sem erros; Robolectric com `sdk=35`.

## Review Focus

1. **Pasta com faixas em taxas diferentes (por exemplo, `song.opus` a 48 kHz e `guitar.ogg` a 44,1 kHz):** o jogo cai no modo de um arquivo com o aviso, sem silêncio nem crash. Testes: `StemSongPlayerTest.differentSampleRatesAreRejectedAndEveryTrackIsClosed` (Task 4) e `GameplayViewModelTest.stemsThatFailToPrepareFallBackToTheSingleFileWithANotice` (Task 5).
2. **Faixa que quebra no meio da música (arquivo corrompido):** ela fica em silêncio e as outras seguem. Teste: `StemSongPlayerTest.aTrackThatFailsMidSongGoesSilentAndTheRestKeepsPlaying` (Task 4).
3. **Sair com a partida pausada (a thread de áudio está esperando):** a thread termina e tudo é liberado. Teste: `StemSongPlayerTest.releaseWhilePausedStopsTheAudioThread` (Task 4).
4. **Guitarra estéreo com acompanhamento mono:** os dois canais saem certos. Teste: `StemMixerTest.monoIsCopiedToBothChannelsAndStereoIsKept` (Task 3).
5. **Muitas faixas altas ao mesmo tempo:** a soma é limitada a [−1, 1] em vez de estourar. Teste: `StemMixerTest.theSumIsLimitedToTheUnitRange` (Task 3).

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
|---|---|
| `scripts/gerar_sfx.py` | Síntese dos cinco sons de acerto e do som de erro |
| `core/audio/src/main/res/raw/sfx_hit_1..5.wav` | Sons de acerto por trilha (gerados) |
| `core/audio/.../Players.kt` | `SfxPlayer.hit(lane)` |
| `core/audio/.../SoundPoolSfxPlayer.kt` | Carrega e toca os cinco sons |
| `core/data/.../game/ChartTrackLoader.kt` | `ChartAudio` e a escolha das faixas |
| `core/audio/.../StemMixer.kt` | Mistura com rampa de ganho (Kotlin puro) |
| `core/audio/.../StemAudio.kt` | `PcmSource`, `PcmSourceOpener`, `AudioSink`, `AudioSinkFactory`, `GuitarTrackControl`, `StemPlayer`, `StemSongPlayerFactory` |
| `core/audio/.../StemSongPlayer.kt` | Thread de áudio: decodifica, mistura e escreve; relógio pelos quadros tocados |
| `core/audio/.../MediaCodecPcmSource.kt` | Decodificação com MediaExtractor + MediaCodec |
| `core/audio/.../AudioTrackSink.kt` | Saída com AudioTrack (PCM float, estéreo) |
| `core/audio/.../AudioModule.kt` | Binding do `StemSongPlayerFactory` |
| `feature/gameplay/.../GameSession.kt` | Guitarra no acerto e no erro; `hit(lane)` |
| `feature/gameplay/.../GameplayViewModel.kt` | Escolha do player por `ChartAudio`, queda para um arquivo |
| `feature/gameplay/.../GameplayScreen.kt` | Aviso "Tocando sem a guitarra separada" |

---

### Task 1: Sons de acerto por trilha

**Files:**
- Modify: `scripts/gerar_sfx.py`
- Create (gerados): `core/audio/src/main/res/raw/sfx_hit_1.wav` … `sfx_hit_5.wav`
- Delete: `core/audio/src/main/res/raw/sfx_hit.wav`
- Modify: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/Players.kt`
- Modify: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/SoundPoolSfxPlayer.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameSession.kt`
- Test: `core/audio/src/test/kotlin/com/rodrigomachado/riff/core/audio/SfxAssetsTest.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/Fakes.kt`, `GameSessionTest.kt`

**Interfaces:**
- Produces: `SfxPlayer.hit(lane: Int)`; `FakeSfxPlayer.hitLanes: MutableList<Int>` e `FakeSfxPlayer.hits: Int` (tamanho de `hitLanes`).

- [ ] **Step 1: Escrever os testes que falham**

`SfxAssetsTest.kt` inteiro:
```kotlin
package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.sound.sampled.AudioSystem
import kotlin.math.abs
import kotlin.math.pow

/** Os efeitos são gerados por scripts/gerar_sfx.py; este teste garante formato, duração e volume. */
class SfxAssetsTest {
    private fun file(name: String) = File("src/main/res/raw/$name")

    private fun check(name: String, maxSeconds: Double) {
        val format = AudioSystem.getAudioFileFormat(file(name))
        assertEquals(44_100f, format.format.sampleRate)
        assertEquals(1, format.format.channels)
        assertEquals(16, format.format.sampleSizeInBits)
        val seconds = format.frameLength / format.format.sampleRate.toDouble()
        assertTrue("$name dura $seconds s", seconds in 0.01..maxSeconds)
    }

    /** Maior amostra em valor absoluto (16 bits, little-endian). */
    private fun peak(name: String): Int = AudioSystem.getAudioInputStream(file(name)).use { stream ->
        val bytes = stream.readAllBytes()
        var max = 0
        for (i in 0 until bytes.size - 1 step 2) {
            val sample = ((bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)).toShort().toInt()
            max = maxOf(max, abs(sample))
        }
        max
    }

    @Test
    fun eachLaneHasAShortQuietGuitarNote() {
        val limit = (Short.MAX_VALUE * 10.0.pow(-12.0 / 20)).toInt() // −12 dBFS
        for (lane in 1..5) {
            check("sfx_hit_$lane.wav", maxSeconds = 0.3)
            assertTrue("sfx_hit_$lane.wav alto demais", peak("sfx_hit_$lane.wav") <= limit)
        }
    }

    @Test
    fun eachLaneSoundsDifferent() {
        val sounds = (1..5).map { file("sfx_hit_$it.wav").readBytes().toList() }
        assertEquals(5, sounds.toSet().size)
        assertNotEquals(sounds[0], sounds[4])
    }

    @Test
    fun theOldClickIsGone() = assertFalse(file("sfx_hit.wav").exists())

    @Test
    fun missIsAShortMutedString() = check("sfx_miss.wav", maxSeconds = 0.25)
}
```

Em `feature/gameplay/src/test/.../Fakes.kt`, troque o `FakeSfxPlayer` por:
```kotlin
class FakeSfxPlayer : SfxPlayer {
    val hitLanes = mutableListOf<Int>()
    val hits: Int get() = hitLanes.size
    var misses = 0
    var lastVolume = -1f
    var released = false

    override fun hit(lane: Int) {
        hitLanes += lane
    }

    override fun miss() {
        misses++
    }

    override fun setVolume(volume: Float) {
        lastVolume = volume
    }

    override fun release() {
        released = true
    }
}
```

Em `GameSessionTest.hitPlaysTheHitSoundLightsTheFlameAndShowsTheJudgment`, troque `assertEquals(1, sfx.hits)` por `assertEquals(listOf(2), sfx.hitLanes) // o som da trilha tocada`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:audio:testDebugUnitTest :feature:gameplay:testDebugUnitTest --console=plain`
Expected: `:core:audio` FAIL (`sfx_hit_1.wav` não existe; `sfx_hit.wav` ainda existe) e `:feature:gameplay` FAIL na compilação (`'hit' overrides nothing`).

- [ ] **Step 3: Implementar**

`scripts/gerar_sfx.py` inteiro:
```python
#!/usr/bin/env python3
"""Gera os efeitos sonoros do Riff. Tudo sintetizado aqui, sem samples de terceiros.

Uso: python3 scripts/gerar_sfx.py core/audio/src/main/res/raw
"""
import math
import random
import struct
import sys
import wave

RATE = 44_100

# Uma nota por trilha, pentatônica de Mi menor: Mi3, Sol3, Lá3, Si3, Ré4.
LANE_NOTES_HZ = [164.81, 196.00, 220.00, 246.94, 293.66]
HIT_SECONDS = 0.25
HIT_FADE_SECONDS = 0.06
HIT_PEAK = 10 ** (-12 / 20)  # −12 dBFS: fica atrás da música


def write(path, samples):
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 30_000)) for s in samples))


def lowpass(samples, cutoff):
    alpha = 1 - math.exp(-2 * math.pi * cutoff / RATE)
    y = 0.0
    out = []
    for x in samples:
        y += alpha * (x - y)
        out.append(y)
    return out


def highpass(samples, cutoff):
    return [x - low for x, low in zip(samples, lowpass(samples, cutoff))]


def pluck(freq, seconds, seed):
    """Corda dedilhada (Karplus-Strong): ruído num atraso do tamanho do período, suavizado a cada volta."""
    rng = random.Random(seed)
    period = int(RATE / freq)
    delay = [rng.random() * 2 - 1 for _ in range(period)]
    out = []
    for i in range(int(RATE * seconds)):
        j = i % period
        value = delay[j]
        delay[j] = 0.998 * 0.5 * (value + delay[(j + 1) % period])
        out.append(value)
    return out


def hit(lane):
    """Guitarra com distorção leve ("crunch") e caixa de amplificador, na nota da trilha."""
    samples = pluck(LANE_NOTES_HZ[lane], HIT_SECONDS, seed=60 + lane)
    samples = highpass(samples, 120)
    samples = [math.tanh(2.5 * x) for x in samples]
    samples = highpass(lowpass(lowpass(samples, 4_000), 5_500), 70)
    attack = int(RATE * 0.001)
    fade = int(RATE * HIT_FADE_SECONDS)
    count = len(samples)
    shaped = [x * min(1, i / attack) * min(1, (count - i) / fade) for i, x in enumerate(samples)]
    peak = max(abs(x) for x in shaped)
    return [x / peak * HIT_PEAK for x in shaped]


def miss():
    """Corda abafada: onda quadrada grave misturada a ruído filtrado, decaindo."""
    rng = random.Random(2)
    count = int(RATE * 0.18)
    filtered = 0.0
    samples = []
    for i in range(count):
        t = i / RATE
        square = 1.0 if math.sin(2 * math.pi * 98 * t) >= 0 else -1.0
        filtered = 0.85 * filtered + 0.15 * (rng.random() * 2 - 1)
        samples.append((0.5 * square + 0.8 * filtered) * math.exp(-t / 0.06) * 0.8)
    return samples


if __name__ == "__main__":
    target = sys.argv[1]
    for lane in range(len(LANE_NOTES_HZ)):
        write(f"{target}/sfx_hit_{lane + 1}.wav", hit(lane))
    write(f"{target}/sfx_miss.wav", miss())
```

Gere os arquivos e apague o antigo:
```bash
python3 scripts/gerar_sfx.py core/audio/src/main/res/raw
git rm -q core/audio/src/main/res/raw/sfx_hit.wav
```

Em `Players.kt`, troque `fun hit()` por:
```kotlin
    /** Acerto na [lane] (0 a 4): cada trilha tem a sua nota. */
    fun hit(lane: Int)
```

`SoundPoolSfxPlayer.kt` inteiro:
```kotlin
package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundPoolSfxPlayer(context: Context) : SfxPlayer {
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val hitSounds = HIT_SOUNDS.map { pool.load(context, it, 1) }
    private val missSound = pool.load(context, R.raw.sfx_miss, 1)
    private var volume = DEFAULT_VOLUME

    override fun hit(lane: Int) = play(hitSounds[lane.coerceIn(hitSounds.indices)])

    override fun miss() = play(missSound)

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    override fun release() = pool.release()

    private fun play(sound: Int) {
        if (volume > 0f) pool.play(sound, volume, volume, 1, 0, 1f)
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val DEFAULT_VOLUME = 0.6f
        val HIT_SOUNDS = listOf(R.raw.sfx_hit_1, R.raw.sfx_hit_2, R.raw.sfx_hit_3, R.raw.sfx_hit_4, R.raw.sfx_hit_5)
    }
}
```

Em `GameSession.press`, troque `sfx.hit()` por `sfx.hit(lane)`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :core:audio:testDebugUnitTest :feature:gameplay:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; `SfxAssetsTest` 4/4; gameplay sem regressão.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(audio): som de acerto de guitarra com uma nota por trilha" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `ChartAudio` no carregamento do mapa

**Files:**
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/game/ChartTrackLoader.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModel.kt` (só a leitura do áudio)
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/game/FolderChartTrackLoaderTest.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModelTest.kt` (helper)

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface ChartAudio {
      /** O que tocar sem as faixas separadas: song.* (ou a guitarra, se for a única). */
      val singleUri: String
      data class Single(val uri: String) : ChartAudio
      data class Stems(val guitarUri: String, val backingUris: List<String>, val fallbackUri: String) : ChartAudio
  }
  data class PlayableChart(val chart: Chart, val difficulty: Difficulty, val notes: List<Note>, val audio: ChartAudio?)
  ```

`fallbackUri` não está na spec: é o arquivo do modo de um arquivo quando as faixas falham (spec §4), resolvido aqui para a queda não depender de nova leitura da pasta.

- [ ] **Step 1: Escrever os testes que falham**

Em `FolderChartTrackLoaderTest.kt`:
- No teste `loadsTheNotesOfTheChosenDifficultyWithTheChartOffset`, troque a última linha por `assertEquals(ChartAudio.Single("raiz/M/song.ogg"), playable.audio)`.
- Troque `findsTheAudioIgnoringCaseAndPrefersTheFullSong` e `folderWithoutAudioHasNoAudioUri` por:
```kotlin
    @Test
    fun guitarWithTheRestOfTheBandBecomesStemsIgnoringCase() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/Guitar.ogg", "guitarra")
        source.file("raiz/M/SONG.MP3", "música")
        source.file("raiz/M/drums_1.ogg", "bateria")
        source.file("raiz/M/bass.opus", "baixo")
        registerChart("raiz/M")

        assertEquals(
            ChartAudio.Stems(
                guitarUri = "raiz/M/Guitar.ogg",
                backingUris = listOf("raiz/M/SONG.MP3", "raiz/M/bass.opus", "raiz/M/drums_1.ogg"),
                fallbackUri = "raiz/M/SONG.MP3",
            ),
            loader().load("id", Difficulty.EXPERT).audio,
        )
    }

    @Test
    fun stemsWithoutSongFallBackToTheGuitar() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/guitar.ogg", "guitarra")
        source.file("raiz/M/rhythm.ogg", "base")
        registerChart("raiz/M")

        assertEquals("raiz/M/guitar.ogg", loader().load("id", Difficulty.EXPERT).audio?.singleUri)
    }

    @Test
    fun onlyTheSongIsASingleFile() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/song.wav", "música")
        registerChart("raiz/M")

        assertEquals(ChartAudio.Single("raiz/M/song.wav"), loader().load("id", Difficulty.EXPERT).audio)
    }

    @Test
    fun theGuitarAloneIsASingleFile() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/guitar.mp3", "guitarra")
        registerChart("raiz/M")

        assertEquals(ChartAudio.Single("raiz/M/guitar.mp3"), loader().load("id", Difficulty.EXPERT).audio)
    }

    @Test
    fun folderWithoutAudioHasNoAudio() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        registerChart("raiz/M")

        assertNull(loader().load("id", Difficulty.EXPERT).audio)
    }
```

Em `GameplayViewModelTest.kt`, troque o helper `playable` por:
```kotlin
    private fun playable(audio: String? = "raiz/a/song.ogg") =
        PlayableChart(chart, Difficulty.EXPERT, listOf(note(1_000), note(2_000)), audio?.let { ChartAudio.Single(it) })
```
(import `com.rodrigomachado.riff.core.data.game.ChartAudio`).

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:data:testDebugUnitTest --console=plain`
Expected: FAIL na compilação com `Unresolved reference 'ChartAudio'`.

- [ ] **Step 3: Implementar**

`ChartTrackLoader.kt` inteiro:
```kotlin
package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.data.importer.ChartFolderReader
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Áudio da pasta do mapa: um arquivo só, ou a guitarra separada do resto da banda. */
sealed interface ChartAudio {
    /** O que tocar sem as faixas separadas: song.* (ou a guitarra, se for a única). */
    val singleUri: String

    data class Single(val uri: String) : ChartAudio {
        override val singleUri: String get() = uri
    }

    data class Stems(val guitarUri: String, val backingUris: List<String>, val fallbackUri: String) : ChartAudio {
        override val singleUri: String get() = fallbackUri
    }
}

/** Mapa pronto para jogar: notas da dificuldade escolhida e o áudio da pasta, se houver. */
data class PlayableChart(val chart: Chart, val difficulty: Difficulty, val notes: List<Note>, val audio: ChartAudio?)

class ChartNotFoundException(message: String) : Exception(message)

interface ChartTrackLoader {
    /**
     * @throws ChartNotFoundException se o mapa não estiver na biblioteca ou não tiver a dificuldade.
     * @throws com.rodrigomachado.riff.core.data.folder.FolderAccessLostException se a pasta não puder ser lida.
     */
    suspend fun load(chartId: String, difficulty: Difficulty): PlayableChart
}

class FolderChartTrackLoader @Inject constructor(
    private val source: FolderSource,
    private val charts: ChartRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ChartTrackLoader {
    override suspend fun load(chartId: String, difficulty: Difficulty): PlayableChart = withContext(dispatcher) {
        val chart = charts.getChart(chartId) ?: throw ChartNotFoundException("mapa $chartId não está na biblioteca")
        val children = source.listChildren(chart.folderUri)
        val loaded = ChartFolderReader.read(source, children, FolderChartImporter.MAX_FILE_BYTES)
        val notes = loaded.tracks[difficulty] ?: throw ChartNotFoundException("mapa $chartId sem $difficulty")
        PlayableChart(chart, difficulty, notes, audioOf(children.filter { !it.isDirectory }))
    }

    private fun audioOf(files: List<FolderEntry>): ChartAudio? {
        fun find(name: String) = EXTENSIONS.firstNotNullOfOrNull { extension ->
            files.firstOrNull { it.name.equals("$name.$extension", ignoreCase = true) }
        }
        val song = find("song")
        val guitar = find("guitar")
        val backing = BACKING_NAMES.mapNotNull(::find)
        return when {
            guitar != null && backing.isNotEmpty() ->
                ChartAudio.Stems(guitar.uri, backing.map { it.uri }, fallbackUri = song?.uri ?: guitar.uri)
            song != null -> ChartAudio.Single(song.uri)
            guitar != null -> ChartAudio.Single(guitar.uri)
            else -> null
        }
    }

    private companion object {
        val EXTENSIONS = listOf("ogg", "opus", "mp3", "wav")

        /** Nomes das faixas do Clone Hero, menos a guitarra; `song` primeiro. */
        val BACKING_NAMES = listOf(
            "song",
            "rhythm",
            "bass",
            "drums",
            "drums_1",
            "drums_2",
            "drums_3",
            "drums_4",
            "vocals",
            "vocals_1",
            "vocals_2",
            "keys",
            "crowd",
        )
    }
}
```
Em `GameplayViewModel.load()`, troque `playable.audioUri` por `playable.audio?.singleUri` (por enquanto o gameplay segue tocando um arquivo só; as faixas entram na Task 5).

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :core:data:testDebugUnitTest :feature:gameplay:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `FolderChartTrackLoaderTest` 7/7; gameplay sem regressão.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(data): faixas separadas da pasta do mapa (ChartAudio)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `StemMixer`

**Files:**
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/StemMixer.kt`
- Test: `core/audio/src/test/kotlin/com/rodrigomachado/riff/core/audio/StemMixerTest.kt`

**Interfaces:**
- Produces: `class StemMixer(trackCount: Int, rampFrames: Int)` com `fun setTargetGain(track: Int, gain: Float)`, `fun currentGain(track: Int): Float`, `fun mix(inputs: Array<FloatArray>, channels: IntArray, available: IntArray, frames: Int, out: FloatArray)`; `companion object { const val RAMP_MS = 30; fun rampFrames(sampleRate: Int): Int }`.

- [ ] **Step 1: Escrever o teste que falha**

`StemMixerTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StemMixerTest {
    private fun constant(value: Float, frames: Int, channels: Int = 1) = FloatArray(frames * channels) { value }

    @Test
    fun tracksAreSummed() {
        val mixer = StemMixer(trackCount = 2, rampFrames = 10)
        val out = FloatArray(8)

        mixer.mix(arrayOf(constant(0.2f, 4), constant(0.3f, 4)), intArrayOf(1, 1), intArrayOf(4, 4), 4, out)

        out.forEach { assertEquals(0.5f, it, 1e-6f) }
    }

    @Test
    fun monoIsCopiedToBothChannelsAndStereoIsKept() {
        val mixer = StemMixer(trackCount = 2, rampFrames = 10)
        val stereo = floatArrayOf(0.2f, -0.2f, 0.2f, -0.2f)
        val out = FloatArray(4)

        mixer.mix(arrayOf(constant(0.1f, 2), stereo), intArrayOf(1, 2), intArrayOf(2, 2), 2, out)

        assertArrayEquals(floatArrayOf(0.3f, -0.1f, 0.3f, -0.1f), out, 1e-6f)
    }

    @Test
    fun cuttingATrackRampsDownWithoutJumpsAndComesBack() {
        val mixer = StemMixer(trackCount = 1, rampFrames = 10)
        val out = FloatArray(40)
        mixer.setTargetGain(0, 0f)

        mixer.mix(arrayOf(constant(1f, 20)), intArrayOf(1), intArrayOf(20), 20, out)

        val left = (0 until 20).map { out[it * 2] }
        assertEquals(0.9f, left[0], 1e-5f)
        assertEquals(0f, left[9], 1e-5f)
        assertEquals(0f, left[19], 1e-5f)
        left.zipWithNext().forEach { (a, b) -> assertTrue("salto de $a para $b", abs(a - b) <= 0.1f + 1e-5f) }

        mixer.setTargetGain(0, 1f)
        mixer.mix(arrayOf(constant(1f, 20)), intArrayOf(1), intArrayOf(20), 20, out)
        assertEquals(0.1f, out[0], 1e-5f)
        assertEquals(1f, out[19 * 2], 1e-5f)
    }

    @Test
    fun theRampContinuesAcrossBlocks() {
        val mixer = StemMixer(trackCount = 1, rampFrames = 10)
        val out = FloatArray(8)
        mixer.setTargetGain(0, 0f)

        mixer.mix(arrayOf(constant(1f, 4)), intArrayOf(1), intArrayOf(4), 4, out)
        assertEquals(0.6f, mixer.currentGain(0), 1e-5f)
        mixer.mix(arrayOf(constant(1f, 4)), intArrayOf(1), intArrayOf(4), 4, out)
        assertEquals(0.2f, mixer.currentGain(0), 1e-5f)
    }

    @Test
    fun aTrackPastItsEndIsSilence() {
        val mixer = StemMixer(trackCount = 2, rampFrames = 10)
        val out = FloatArray(16)

        mixer.mix(arrayOf(constant(0.25f, 8), constant(0.25f, 8)), intArrayOf(1, 1), intArrayOf(8, 4), 8, out)

        assertEquals(0.5f, out[3 * 2], 1e-6f)
        assertEquals(0.25f, out[6 * 2], 1e-6f)
    }

    @Test
    fun theSumIsLimitedToTheUnitRange() {
        val mixer = StemMixer(trackCount = 3, rampFrames = 10)
        val out = FloatArray(4)

        mixer.mix(
            arrayOf(constant(0.6f, 2), constant(0.6f, 2), constant(-0.9f, 2, channels = 2).also { it[0] = 0.9f }),
            intArrayOf(1, 1, 2),
            intArrayOf(2, 2, 2),
            2,
            out,
        )

        assertEquals(1f, out[0], 1e-6f) // 0,6 + 0,6 + 0,9
        out.forEach { assertTrue(it in -1f..1f) }
    }

    @Test
    fun onlyTheRequestedFramesAreWrittenIntoTheGivenBuffer() {
        val mixer = StemMixer(trackCount = 1, rampFrames = 10)
        val out = FloatArray(10) { 7f }

        mixer.mix(arrayOf(constant(0.1f, 2)), intArrayOf(1), intArrayOf(2), 2, out)

        assertEquals(0.1f, out[3], 1e-6f)
        assertEquals(7f, out[4], 0f)
    }

    @Test
    fun thirtyMillisecondsOfRampAtCdQuality() = assertEquals(1_323, StemMixer.rampFrames(44_100))
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:audio:testDebugUnitTest --tests '*StemMixerTest*' --console=plain`
Expected: FAIL na compilação com `Unresolved reference 'StemMixer'`.

- [ ] **Step 3: Implementar**

`StemMixer.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

/**
 * Mistura faixas em estéreo intercalado. Cada faixa tem um ganho alvo; a mudança vira uma rampa linear de
 * [rampFrames] quadros, para o corte da guitarra não estalar. [mix] não aloca nada: roda a cada bloco de áudio.
 * Os ganhos podem mudar de outra thread (o toque do jogador); o pior caso é a rampa começar um bloco depois.
 */
class StemMixer(private val trackCount: Int, private val rampFrames: Int) {
    private val target = FloatArray(trackCount) { 1f }
    private val current = FloatArray(trackCount) { 1f }

    fun setTargetGain(track: Int, gain: Float) {
        target[track] = gain.coerceIn(0f, 1f)
    }

    fun currentGain(track: Int): Float = current[track]

    /**
     * @param inputs amostras intercaladas de cada faixa, com [channels] canais (1 ou 2)
     * @param available quadros válidos de cada faixa neste bloco; depois deles a faixa conta como silêncio
     * @param out saída estéreo intercalada; só as primeiras `frames * 2` posições são escritas
     */
    fun mix(inputs: Array<FloatArray>, channels: IntArray, available: IntArray, frames: Int, out: FloatArray) {
        java.util.Arrays.fill(out, 0, frames * 2, 0f)
        val step = 1f / rampFrames
        for (track in 0 until trackCount) {
            val input = inputs[track]
            val count = channels[track]
            val valid = minOf(available[track], frames)
            val goal = target[track]
            var gain = current[track]
            for (frame in 0 until frames) {
                gain = approach(gain, goal, step)
                if (frame < valid) {
                    val left = input[frame * count]
                    val right = if (count == 2) input[frame * count + 1] else left
                    out[frame * 2] += left * gain
                    out[frame * 2 + 1] += right * gain
                }
            }
            current[track] = gain
        }
        for (i in 0 until frames * 2) out[i] = out[i].coerceIn(-1f, 1f)
    }

    private fun approach(value: Float, goal: Float, step: Float): Float = when {
        value < goal -> minOf(goal, value + step)
        value > goal -> maxOf(goal, value - step)
        else -> value
    }

    companion object {
        const val RAMP_MS = 30

        fun rampFrames(sampleRate: Int): Int = sampleRate * RAMP_MS / 1_000
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :core:audio:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `StemMixerTest` 8/8.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(audio): mixer de faixas com rampa de ganho" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: `StemSongPlayer`, decodificação e saída de áudio

**Files:**
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/StemAudio.kt`
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/StemSongPlayer.kt`
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/MediaCodecPcmSource.kt`
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/AudioTrackSink.kt`
- Modify: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/AudioModule.kt`
- Test: `core/audio/src/test/kotlin/com/rodrigomachado/riff/core/audio/StemSongPlayerTest.kt`

**Interfaces:**
- Consumes: `StemMixer` (Task 3); `SongPlayer` (`Players.kt`).
- Produces (em `StemAudio.kt`):
  ```kotlin
  interface PcmSource : java.io.Closeable {
      val sampleRate: Int
      val channelCount: Int
      /** Preenche até [maxFrames] quadros intercalados; devolve menos só no fim da faixa (0 = acabou). */
      fun read(buffer: FloatArray, maxFrames: Int): Int
  }
  fun interface PcmSourceOpener { @Throws(java.io.IOException::class) fun open(uri: String): PcmSource }
  interface AudioSink {
      fun start(); fun pause(); fun stop()
      /** Bloqueia até caber; [buffer] é estéreo intercalado. */
      fun write(buffer: FloatArray, frames: Int)
      val playedFrames: Long
      fun release()
  }
  fun interface AudioSinkFactory { fun create(sampleRate: Int): AudioSink }
  interface GuitarTrackControl { fun setGuitarAudible(audible: Boolean) }
  interface StemPlayer : SongPlayer, GuitarTrackControl
  fun interface StemSongPlayerFactory { fun create(guitarUri: String, backingUris: List<String>): StemPlayer }
  ```
  `class StemSongPlayer(guitarUri, backingUris, opener, sinks, log = Log.w, startThread = true) : StemPlayer` com `internal fun renderOnce(): Boolean` e `internal val audioThread: Thread?`; `companion object { const val BLOCK_FRAMES = 1_024 }`. Binding Hilt de `StemSongPlayerFactory`.

**Comportamento do `StemSongPlayer`:**
- `prepare(uri)`: o `uri` é ignorado (as faixas vêm do construtor; o gameplay passa o da guitarra). Abre todas as faixas, exige a mesma taxa e 1 ou 2 canais; senão fecha as abertas, registra o motivo (`RiffAudio`) e lança `IOException`. Cria o mixer, os buffers e a saída; inicia a thread `riff-audio`.
- A thread espera enquanto pausado; tocando, chama `renderOnce()`: lê um bloco de cada faixa (uma faixa que lança exceção é registrada e vira silêncio), mistura e escreve.
- `positionMs` = quadros tocados pela saída em ms; `isCompleted` = todas as faixas acabaram e tudo o que foi escrito foi tocado; `isPlaying` = tocando, não liberado e não concluído.
- `release()`: idempotente; guarda a posição, para a saída (desbloqueia um `write`), acorda e espera a thread, fecha as faixas e libera a saída. Depois disso todas as chamadas são inofensivas.

- [ ] **Step 1: Escrever o teste que falha**

`StemSongPlayerTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StemSongPlayerTest {
    private class FakeSource(
        override val sampleRate: Int = 44_100,
        override val channelCount: Int = 1,
        private val value: Float = 0.25f,
        private val frames: Int = 4_000,
        private val failAfterFrames: Int = Int.MAX_VALUE,
    ) : PcmSource {
        var position = 0
        var closed = false

        override fun read(buffer: FloatArray, maxFrames: Int): Int {
            if (position >= failAfterFrames) throw IllegalStateException("arquivo corrompido")
            val count = minOf(maxFrames, frames - position)
            for (i in 0 until count * channelCount) buffer[i] = value
            position += count
            return count
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeSink : AudioSink {
        var started = false
        var paused = false
        var stopped = false
        var released = false
        var written = 0L
        var lastBlock = FloatArray(0)
        override var playedFrames = 0L

        override fun start() {
            started = true
            paused = false
        }

        override fun pause() {
            paused = true
        }

        override fun stop() {
            stopped = true
        }

        override fun write(buffer: FloatArray, frames: Int) {
            lastBlock = buffer.copyOf(frames * 2)
            written += frames
        }

        override fun release() {
            released = true
        }
    }

    private val sources = mutableMapOf<String, FakeSource>()
    private val sink = FakeSink()
    private val logged = mutableListOf<String>()

    private fun player(startThread: Boolean = false) = StemSongPlayer(
        guitarUri = "guitar.ogg",
        backingUris = listOf("song.ogg"),
        opener = { uri -> sources.getValue(uri) },
        sinks = { sink },
        log = { message, _ -> logged += message },
        startThread = startThread,
    )

    private fun prepared(startThread: Boolean = false) = player(startThread).also { runBlocking { it.prepare("guitar.ogg") } }

    @Test
    fun guitarAndBandAreMixedIntoOneOutput() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()

        player.play()
        assertTrue(player.renderOnce())

        assertEquals(StemSongPlayer.BLOCK_FRAMES.toLong(), sink.written)
        assertEquals(0.5f, sink.lastBlock[0], 1e-6f)
    }

    @Test
    fun cuttingTheGuitarLeavesTheBandAfterTheRamp() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        player.play()

        player.setGuitarAudible(false)
        player.renderOnce()
        player.renderOnce() // 2.048 quadros > rampa de 1.323

        assertEquals(0.25f, sink.lastBlock[sink.lastBlock.size - 2], 1e-6f)
    }

    @Test
    fun differentSampleRatesAreRejectedAndEveryTrackIsClosed() {
        sources["guitar.ogg"] = FakeSource(sampleRate = 44_100)
        sources["song.ogg"] = FakeSource(sampleRate = 48_000)
        val player = player()

        assertThrows(IOException::class.java) { runBlocking { player.prepare("guitar.ogg") } }
        assertTrue(sources.values.all { it.closed })
        assertEquals(1, logged.size)
    }

    @Test
    fun positionComesFromTheFramesThatWerePlayed() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()

        sink.playedFrames = 44_100

        assertEquals(1_000L, player.positionMs)
    }

    @Test
    fun aShorterTrackEndsInSilenceAndTheSongEndsWithTheLongest() {
        sources["guitar.ogg"] = FakeSource(frames = 100)
        sources["song.ogg"] = FakeSource(frames = 50)
        val player = prepared()
        player.play()

        assertTrue(player.renderOnce())
        assertEquals(100L, sink.written)
        assertEquals(0.5f, sink.lastBlock[10 * 2], 1e-6f)
        assertEquals(0.25f, sink.lastBlock[70 * 2], 1e-6f)
        assertFalse(player.renderOnce())
        assertFalse(player.isCompleted) // escrito, mas ainda não tocado

        sink.playedFrames = 100
        assertTrue(player.isCompleted)
        assertFalse(player.isPlaying)
    }

    @Test
    fun aTrackThatFailsMidSongGoesSilentAndTheRestKeepsPlaying() {
        sources["guitar.ogg"] = FakeSource(failAfterFrames = StemSongPlayer.BLOCK_FRAMES)
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        player.play()

        player.renderOnce()
        assertTrue(player.renderOnce())

        assertEquals(0.25f, sink.lastBlock[0], 1e-6f)
        assertEquals(1, logged.size)
    }

    @Test
    fun playAndPauseDriveTheOutput() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()

        player.play()
        assertTrue(sink.started)
        assertTrue(player.isPlaying)

        player.pause()
        assertTrue(sink.paused)
        assertFalse(player.isPlaying)
    }

    @Test
    fun releaseWhilePausedStopsTheAudioThread() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared(startThread = true)
        player.play()
        player.pause()
        val thread = checkNotNull(player.audioThread)

        player.release()

        assertFalse(thread.isAlive)
        assertTrue(sink.stopped)
        assertTrue(sink.released)
        assertTrue(sources.values.all { it.closed })
    }

    @Test
    fun everythingIsHarmlessAfterRelease() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        sink.playedFrames = 4_410

        player.release()
        player.release()
        player.play()
        player.pause()
        player.setGuitarAudible(false)

        assertEquals(100L, player.positionMs)
        assertFalse(player.isPlaying)
        assertFalse(player.renderOnce())
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:audio:testDebugUnitTest --tests '*StemSongPlayerTest*' --console=plain`
Expected: FAIL na compilação com `Unresolved reference 'StemSongPlayer'` (e `PcmSource`, `AudioSink`).

- [ ] **Step 3: Implementar**

`StemAudio.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

import java.io.Closeable
import java.io.IOException

/** Áudio decodificado de uma faixa, em blocos de amostras float intercaladas. */
interface PcmSource : Closeable {
    val sampleRate: Int
    val channelCount: Int

    /** Preenche até [maxFrames] quadros intercalados; devolve menos só no fim da faixa (0 = acabou). */
    fun read(buffer: FloatArray, maxFrames: Int): Int
}

fun interface PcmSourceOpener {
    @Throws(IOException::class)
    fun open(uri: String): PcmSource
}

/** Saída de áudio estéreo. Os quadros já tocados são o relógio da música. */
interface AudioSink {
    fun start()

    fun pause()

    /** Para de vez e desbloqueia um [write] em andamento. */
    fun stop()

    /** Bloqueia até caber; [buffer] é estéreo intercalado. */
    fun write(buffer: FloatArray, frames: Int)

    val playedFrames: Long

    fun release()
}

fun interface AudioSinkFactory {
    fun create(sampleRate: Int): AudioSink
}

/** Liga e desliga a faixa da guitarra (acerto e erro, como no Guitar Hero). */
interface GuitarTrackControl {
    fun setGuitarAudible(audible: Boolean)
}

interface StemPlayer : SongPlayer, GuitarTrackControl

fun interface StemSongPlayerFactory {
    fun create(guitarUri: String, backingUris: List<String>): StemPlayer
}
```

`StemSongPlayer.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Toca a guitarra e o resto da banda em faixas separadas, perfeitamente juntas: uma thread decodifica cada
 * faixa, mistura ([StemMixer]) e escreve numa saída só, cujos quadros tocados são o relógio. A guitarra é a
 * faixa 0 do mixer.
 */
class StemSongPlayer(
    private val guitarUri: String,
    private val backingUris: List<String>,
    private val opener: PcmSourceOpener,
    private val sinks: AudioSinkFactory,
    private val log: (String, Throwable?) -> Unit = { message, error -> Log.w(TAG, message, error) },
    private val startThread: Boolean = true,
) : StemPlayer {
    private val lock = Object()
    private var sources: List<PcmSource> = emptyList()
    private var sink: AudioSink? = null
    private var mixer: StemMixer? = null
    private var inputs: Array<FloatArray> = emptyArray()
    private var channels = IntArray(0)
    private var available = IntArray(0)
    private var ended = BooleanArray(0)
    private val out = FloatArray(BLOCK_FRAMES * 2)
    private var sampleRate = 0
    private var writtenFrames = 0L
    private var positionAtRelease = 0L

    @Volatile private var playing = false

    @Volatile private var released = false

    @Volatile private var allEnded = false

    internal var audioThread: Thread? = null
        private set

    /** O [uri] é ignorado: as faixas vêm do construtor. */
    @Suppress("TooGenericExceptionCaught") // qualquer falha ao abrir vira IOException e o modo de um arquivo
    override suspend fun prepare(uri: String) = withContext(Dispatchers.IO) {
        val opened = mutableListOf<PcmSource>()
        try {
            (listOf(guitarUri) + backingUris).forEach { opened += opener.open(it) }
            val rates = opened.map { it.sampleRate }.distinct()
            if (rates.size != 1) throw IOException("faixas com taxas diferentes: $rates")
            if (opened.any { it.channelCount !in 1..2 }) throw IOException("só faixas mono ou estéreo")
            setUp(opened, rates.single())
        } catch (e: CancellationException) {
            opened.forEach { runCatching { it.close() } }
            throw e
        } catch (e: IOException) {
            fail(opened, e)
        } catch (e: RuntimeException) {
            fail(opened, IOException("faixa não pôde ser aberta", e))
        }
    }

    private fun fail(opened: List<PcmSource>, error: IOException): Nothing {
        opened.forEach { runCatching { it.close() } }
        log("Faixas separadas indisponíveis", error)
        throw error
    }

    private fun setUp(opened: List<PcmSource>, rate: Int) {
        sampleRate = rate
        inputs = Array(opened.size) { FloatArray(BLOCK_FRAMES * opened[it].channelCount) }
        channels = IntArray(opened.size) { opened[it].channelCount }
        available = IntArray(opened.size)
        ended = BooleanArray(opened.size)
        mixer = StemMixer(opened.size, StemMixer.rampFrames(rate))
        val output = sinks.create(rate)
        synchronized(lock) {
            if (released) {
                opened.forEach { runCatching { it.close() } }
                output.release()
                return
            }
            sources = opened
            sink = output
        }
        if (startThread) audioThread = Thread(::loop, "riff-audio").apply { start() }
    }

    private fun loop() {
        while (!released) {
            synchronized(lock) {
                while (!released && (!playing || allEnded)) lock.wait()
            }
            if (!released) renderOnce()
        }
    }

    /** Lê um bloco de cada faixa, mistura e escreve. `false` quando todas as faixas acabaram. */
    internal fun renderOnce(): Boolean {
        val output = sink ?: return false
        val mix = mixer ?: return false
        if (released || allEnded) return false
        var frames = 0
        for (track in sources.indices) {
            available[track] = if (ended[track]) 0 else read(track)
            frames = maxOf(frames, available[track])
        }
        if (frames == 0) {
            allEnded = true
            return false
        }
        mix.mix(inputs, channels, available, frames, out)
        output.write(out, frames)
        writtenFrames += frames
        return true
    }

    @Suppress("TooGenericExceptionCaught") // decodificador quebrado no meio: a faixa vira silêncio
    private fun read(track: Int): Int {
        val count = try {
            sources[track].read(inputs[track], BLOCK_FRAMES)
        } catch (e: RuntimeException) {
            log("Faixa $track falhou no meio da música", e)
            0
        }
        if (count < BLOCK_FRAMES) ended[track] = true
        return count
    }

    override fun play() {
        val output = sink ?: return
        if (released) return
        output.start()
        synchronized(lock) {
            playing = true
            lock.notifyAll()
        }
    }

    override fun pause() {
        if (released) return
        playing = false
        sink?.pause()
    }

    override fun setGuitarAudible(audible: Boolean) {
        if (!released) mixer?.setTargetGain(GUITAR, if (audible) 1f else 0f)
    }

    override fun release() {
        synchronized(lock) {
            if (released) return
            positionAtRelease = positionMs
            released = true
            playing = false
            lock.notifyAll()
        }
        sink?.stop()
        audioThread?.join(JOIN_TIMEOUT_MS)
        sources.forEach { runCatching { it.close() } }
        sink?.release()
    }

    override val positionMs: Long
        get() {
            if (released) return positionAtRelease
            val output = sink ?: return 0
            return output.playedFrames * 1_000 / sampleRate
        }

    override val isPlaying: Boolean get() = playing && !released && !isCompleted

    override val isCompleted: Boolean
        get() = allEnded && (sink?.playedFrames ?: 0) >= writtenFrames

    companion object {
        const val BLOCK_FRAMES = 1_024
        private const val GUITAR = 0
        private const val JOIN_TIMEOUT_MS = 1_000L
        private const val TAG = "RiffAudio"
    }
}
```
Atenção: no teste `everythingIsHarmlessAfterRelease`, `positionMs` depois de `release` deve ser 100 (4.410 quadros a 44,1 kHz), por isso a posição é guardada antes de `released = true`.

`MediaCodecPcmSource.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodifica uma faixa aos poucos (MediaExtractor + MediaCodec), sem carregar a música inteira. */
class MediaCodecPcmSource private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
) : PcmSource {
    private val info = MediaCodec.BufferInfo()
    private var output: ByteBuffer? = null
    private var outputIndex = -1
    private var floatOutput = false
    private var inputDone = false
    private var outputDone = false

    override var sampleRate = 0
        private set

    override var channelCount = 0
        private set

    /** Lê até o primeiro bloco decodificado, para saber a taxa e os canais reais (o Opus decodifica a 48 kHz). */
    private fun prime() {
        repeat(PRIME_ATTEMPTS) {
            if (output != null || outputDone) return
            nextOutput()
        }
        if (sampleRate == 0) throw IOException("decodificador não entregou áudio")
    }

    override fun read(buffer: FloatArray, maxFrames: Int): Int {
        val wanted = maxFrames * channelCount
        var filled = 0
        while (filled < wanted) {
            val current = output ?: if (outputDone) break else nextOutput() ?: continue
            filled += copy(current, buffer, filled, wanted - filled)
            if (!current.hasRemaining()) {
                codec.releaseOutputBuffer(outputIndex, false)
                output = null
                outputIndex = -1
            }
        }
        return filled / channelCount
    }

    private fun copy(source: ByteBuffer, target: FloatArray, offset: Int, max: Int): Int {
        var count = 0
        if (floatOutput) {
            while (count < max && source.remaining() >= Float.SIZE_BYTES) target[offset + count++] = source.float
        } else {
            while (count < max && source.remaining() >= Short.SIZE_BYTES) {
                target[offset + count++] = source.short / SHORT_SCALE
            }
        }
        return count
    }

    private fun nextOutput(): ByteBuffer? {
        feedInput()
        val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) readFormat(codec.outputFormat)
        if (index < 0) return null
        if (sampleRate == 0) readFormat(codec.outputFormat)
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
        val buffer = checkNotNull(codec.getOutputBuffer(index))
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.nativeOrder())
        output = buffer
        outputIndex = index
        return buffer
    }

    private fun readFormat(format: MediaFormat) {
        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        floatOutput = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
            format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
    }

    private fun feedInput() {
        if (inputDone) return
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return
        val buffer = checkNotNull(codec.getInputBuffer(index))
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    override fun close() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { extractor.release() }
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val PRIME_ATTEMPTS = 200
        private const val SHORT_SCALE = 32_768f

        @Throws(IOException::class)
        @Suppress("TooGenericExceptionCaught") // erros do MediaCodec chegam como RuntimeException
        fun open(context: Context, uri: String): MediaCodecPcmSource {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, Uri.parse(uri), null)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: throw IOException("sem faixa de áudio: $uri")
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
                codec.configure(format, null, null, 0)
                codec.start()
                return MediaCodecPcmSource(extractor, codec).also {
                    try {
                        it.prime()
                    } catch (e: IOException) {
                        it.close()
                        throw e
                    }
                }
            } catch (e: IOException) {
                extractor.release()
                throw e
            } catch (e: RuntimeException) {
                extractor.release()
                throw IOException("não deu para decodificar $uri", e)
            }
        }
    }
}
```

`AudioTrackSink.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Saída estéreo em PCM float. A posição da cabeça de leitura é o relógio da música. */
class AudioTrackSink(sampleRate: Int) : AudioSink {
    private val track: AudioTrack

    init {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minimum * BUFFER_MULTIPLIER)
            .build()
    }

    override fun start() = track.play()

    override fun pause() = track.pause()

    override fun stop() {
        runCatching { track.stop() }
    }

    override fun write(buffer: FloatArray, frames: Int) {
        track.write(buffer, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
    }

    override val playedFrames: Long get() = track.playbackHeadPosition.toLong() and UINT_MASK

    override fun release() = track.release()

    private companion object {
        const val BUFFER_MULTIPLIER = 2
        const val UINT_MASK = 0xFFFF_FFFFL
    }
}
```

Em `AudioModule.kt`, acrescente:
```kotlin
    @Provides
    fun stemPlayers(@ApplicationContext context: Context): StemSongPlayerFactory =
        StemSongPlayerFactory { guitarUri, backingUris ->
            StemSongPlayer(
                guitarUri,
                backingUris,
                opener = { uri -> MediaCodecPcmSource.open(context, uri) },
                sinks = ::AudioTrackSink,
            )
        }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt lintDebug :core:audio:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; `StemSongPlayerTest` 9/9; `StemMixerTest` 8/8.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(audio): player de faixas separadas com uma saída e um relógio" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Partida com a guitarra separada

**Files:**
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameSession.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModel.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayScreen.kt`
- Modify: `feature/gameplay/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/Fakes.kt`, `GameSessionTest.kt`, `GameplayViewModelTest.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/GameStageNoticeTest.kt`

**Interfaces:**
- Consumes: `ChartAudio` (Task 2); `GuitarTrackControl`, `StemPlayer`, `StemSongPlayerFactory` (Task 4); `SfxPlayer.hit(lane)` (Task 1).
- Produces: `GameplayUiState.Ready(..., isPaused: Boolean = false, withoutSeparateGuitar: Boolean = false)`; construtor do `GameplayViewModel` com `stemPlayers: StemSongPlayerFactory` logo depois de `@SpotifySong spotifyPlayers`; string `gameplay_no_separate_guitar`.

- [ ] **Step 1: Escrever os testes que falham**

Em `Fakes.kt` do gameplay, acrescente:
```kotlin
class FakeStemPlayer(val song: FakeSongPlayer = FakeSongPlayer()) :
    com.rodrigomachado.riff.core.audio.StemPlayer,
    SongPlayer by song {
    val audible = mutableListOf<Boolean>()

    override fun setGuitarAudible(audible: Boolean) {
        this.audible += audible
    }
}
```

Em `GameSessionTest.kt`, acrescente:
```kotlin
    private val stems = FakeStemPlayer()

    private fun stemSession(vararg notes: com.rodrigomachado.riff.core.model.Note) =
        GameSession(notes.toList(), stems, sfx, time, audioOffsetMs = 0).also { it.start() }

    @Test
    fun withTheSeparateGuitarAHitBringsItBackWithoutTheHitSound() {
        stems.song.positionMs = 1_000
        val session = stemSession(note(1_000, lane = 1))

        session.press(1)

        assertEquals(listOf(true), stems.audible)
        assertEquals(0, sfx.hits)
    }

    @Test
    fun withTheSeparateGuitarATapWithoutANoteCutsItAndPlaysTheMissSound() {
        val session = stemSession(note(5_000))

        session.press(0)

        assertEquals(listOf(false), stems.audible)
        assertEquals(1, sfx.misses)
    }

    @Test
    fun withTheSeparateGuitarANoteThatPassesCutsIt() {
        val session = stemSession(note(1_000))
        stems.song.positionMs = 1_500
        time.now = SYNC

        session.tick()

        assertEquals(listOf(false), stems.audible)
        assertEquals(1, sfx.misses)
    }
```
(`SYNC` já existe no arquivo; se o nome for outro, use a mesma constante do teste `noteThatPassesUnplayedPlaysTheMissSoundOnce`.)

Em `GameplayViewModelTest.kt`:
- Acrescente `private val stemPlayer = FakeStemPlayer()` e `private var stemsCreated: Pair<String, List<String>>? = null`.
- No helper `viewModel`, depois do bloco do `spotifyPlayer`, acrescente o argumento:
```kotlin
        { guitar, backing ->
            stemsCreated = guitar to backing
            stemPlayer
        },
```
- Acrescente:
```kotlin
    private fun stems() = ChartAudio.Stems("raiz/a/guitar.ogg", listOf("raiz/a/song.ogg"), fallbackUri = "raiz/a/song.ogg")

    @Test
    fun separateGuitarPlaysEveryTrackWithTheStemPlayer() = runTest {
        loader.result = { playable().copy(audio = stems()) }

        val state = viewModel().uiState.value as GameplayUiState.Ready

        assertEquals("raiz/a/guitar.ogg" to listOf("raiz/a/song.ogg"), stemsCreated)
        assertEquals(0, playersCreated)
        assertTrue(stemPlayer.isPlaying)
        assertEquals(false, state.withoutSeparateGuitar)
    }

    @Test
    fun stemsThatFailToPrepareFallBackToTheSingleFileWithANotice() = runTest {
        loader.result = { playable().copy(audio = stems()) }
        stemPlayer.song.prepareFailure = java.io.IOException("faixas com taxas diferentes")

        val state = viewModel().uiState.value as GameplayUiState.Ready

        assertTrue(stemPlayer.song.released)
        assertEquals("raiz/a/song.ogg", player.preparedUri)
        assertTrue(player.isPlaying)
        assertTrue(state.withoutSeparateGuitar)
    }
```

`GameStageNoticeTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class GameStageNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playingWithoutTheSeparateGuitarIsAnnouncedAtTheStart() {
        val session = GameSession(listOf(note(60_000)), FakeSongPlayer(), FakeSfxPlayer(), FakeTime(), audioOffsetMs = 0)
        session.start()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    GameplayUiState.Ready(
                        "Música",
                        "Banda",
                        Difficulty.EXPERT,
                        session,
                        Settings.DEFAULT,
                        withoutSeparateGuitar = true,
                    ),
                    reducedMotion = true,
                    onPause = {},
                    onResume = {},
                    onExit = {},
                    onSessionFinished = {},
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText("Tocando sem a guitarra separada").assertExists()
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :feature:gameplay:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (construtor do `GameplayViewModel` com um argumento a mais, `withoutSeparateGuitar` não existe).

- [ ] **Step 3: Implementar**

`GameSession.kt`:
- Campo: `private val guitar = player as? GuitarTrackControl` (import `com.rodrigomachado.riff.core.audio.GuitarTrackControl`).
- Em `tick()`, troque o bloco de erros por:
```kotlin
        if (misses > missesHeard) {
            missesHeard = misses
            onMiss()
        }
```
- Em `press`, troque o trecho do julgamento por:
```kotlin
        val judgment = engine.onPress(lane, clock.positionMs())
        if (judgment == null) {
            onMiss()
            return
        }
        // Com a guitarra separada, ela mesma é a resposta do acerto (como no Guitar Hero).
        if (guitar != null) guitar.setGuitarAudible(true) else sfx.hit(lane)
```
- Novo método:
```kotlin
    private fun onMiss() {
        sfx.miss()
        guitar?.setGuitarAudible(false)
    }
```

`GameplayViewModel.kt`:
- `Ready` ganha, depois de `isPaused`, `val withoutSeparateGuitar: Boolean = false,`.
- Construtor: depois de `@SpotifySong private val spotifyPlayers: SongPlayerFactory,` acrescente `private val stemPlayers: StemSongPlayerFactory,` (import `com.rodrigomachado.riff.core.audio.StemSongPlayerFactory`; e `com.rodrigomachado.riff.core.data.game.ChartAudio`).
- `load()` inteiro:
```kotlin
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // cada falha vira uma explicação na tela
    private suspend fun load(): GameplayUiState = try {
        val playable = loader.load(chartId, difficulty)
        val settings = settingsRepository.settings.first()
        val (songPlayer, withoutSeparateGuitar) = prepareSong(playable.audio)
        val effects = sfxPlayers.create().also {
            sfx = it
            it.setVolume(settings.sfxVolume)
        }
        val offset = settings.audioOffsetMs.toLong() + trackOffsetMs
        val session = GameSession(playable.notes, songPlayer, effects, timeSource, offset)
        if (!pauseRequested) session.start()
        GameplayUiState.Ready(
            playable.chart.title,
            playable.chart.artist,
            difficulty,
            session,
            settings,
            pauseRequested,
            withoutSeparateGuitar,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        release()
        GameplayUiState.Unavailable(reasonFor(e))
    }

    /** Spotify, faixas separadas ou um arquivo só; se as faixas falharem, cai no arquivo só (com aviso). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // a falha das faixas vira o modo de um arquivo
    private suspend fun prepareSong(audio: ChartAudio?): Pair<SongPlayer, Boolean> {
        spotifyTrackUri?.let { return prepared(spotifyPlayers.create(), it, ::SpotifyFailedException) to false }
        val chartAudio = audio ?: throw NoAudioException()
        if (chartAudio !is ChartAudio.Stems) {
            return prepared(songPlayers.create(), chartAudio.singleUri, ::AudioFailedException) to false
        }
        val stems = stemPlayers.create(chartAudio.guitarUri, chartAudio.backingUris).also { player = it }
        try {
            stems.prepare(chartAudio.guitarUri)
            return stems to false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            stems.release()
            player = null
        }
        return prepared(songPlayers.create(), chartAudio.singleUri, ::AudioFailedException) to true
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun prepared(created: SongPlayer, uri: String, wrap: (Throwable) -> Exception): SongPlayer {
        player = created
        try {
            created.prepare(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw wrap(e)
        }
        return created
    }
```
(`SpotifyFailedException` e `AudioFailedException` já recebem `cause: Throwable`; a referência `::SpotifyFailedException` serve como `(Throwable) -> Exception`.)

`GameplayScreen.kt`:
- No `GameStage`, troque `Hud(session, frame, reducedMotion, onPause, Modifier.fillMaxSize())` por `Hud(session, frame, reducedMotion, state.withoutSeparateGuitar, onPause, Modifier.fillMaxSize())`.
- `Hud` ganha o parâmetro `withoutSeparateGuitar: Boolean` depois de `reducedMotion` e, no fim do `Box`, depois do bloco do julgamento:
```kotlin
        if (withoutSeparateGuitar && session.songTimeMs < NOTICE_MS) {
            Text(
                stringResource(R.string.gameplay_no_separate_guitar),
                style = MaterialTheme.typography.bodyMedium,
                color = RiffColors.PaperDim,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl * 3),
            )
        }
```
- Constante privada no arquivo, junto das outras: `private const val NOTICE_MS = 4_000L`.

Strings:
```xml
    <string name="gameplay_no_separate_guitar">Tocando sem a guitarra separada</string>
```
```xml
    <string name="gameplay_no_separate_guitar">Playing without the separate guitar</string>
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt lintDebug :feature:gameplay:testDebugUnitTest :feature:gameplay:verifyRoborazziDebug :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; testes novos e antigos do gameplay passando; `Gameplay_stage.png` sem mudança.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(gameplay): guitarra separada cortada no erro e volta no acerto" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Partida real, especificação e README

**Files:**
- Modify: `docs/superpowers/specs/2026-10-05-riff-design.md`, `docs/superpowers/specs/2026-10-07-identidade-visual-design.md`, `README.md`

- [ ] **Step 1: Mapa com a guitarra separada (precisa do usuário para ouvir)**

Procure no Encore (`https://api.enchor.us/search`, POST `{"search": "...", "page": 1}`) um mapa de charter da comunidade (nunca Harmonix/Neversoft) com guitarra; baixe `https://files.enchor.us/<md5>.sng`, extraia (o `.sng` é um pacote aberto: cabeçalho `SNGPKG`, versão, máscara XOR de 16 bytes, metadados, índice de arquivos e dados mascarados com `mask[i % 16] ^ (i & 0xFF)`) e confira se tem `guitar.*` e outra faixa. Se nenhum servir, separe o `song.opus` do mapa do Nirvana com o Demucs, no computador e fora do repositório (`pip install demucs`, depois `demucs -n htdemucs_6s --two-stems=guitar song.opus`): `guitar.wav` vira `guitar.ogg` e `no_guitar.wav` vira `song.ogg` (`ffmpeg -i entrada.wav -c:a libvorbis saida.ogg`), os dois a 44,1 kHz. Copie a pasta para `Download/RiffCharts` do aparelho e reimporte na biblioteca.

- [ ] **Step 2: Jogar de verdade**

Instale o APK no aparelho e confira (registrando no ledger):
1. Mapa com faixas: a música toca completa; errar corta a guitarra e toca o som de erro; acertar a traz de volta, sem estalo; o usuário confirma de ouvido.
2. Mapa sem faixas (Nirvana atual): o som de acerto novo, uma nota por trilha, fica atrás da música; o usuário confirma o volume.
3. Pausar, continuar e sair numa partida com faixas: sem travar, sem som depois de sair.
4. Modo Spotify: o som de acerto novo também toca.
5. Nenhum `FATAL EXCEPTION` no `logcat`; nada com a tag `RiffAudio` num mapa com faixas boas.

- [ ] **Step 3: Documentação**

- Spec principal, §4 "Renderização e input", acrescente: "Áudio da pasta: com a guitarra separada (`guitar.*` + outras faixas do Clone Hero), as faixas tocam juntas por um mixer próprio (`StemSongPlayer`, uma saída e um relógio) e a guitarra é cortada no erro, como no Guitar Hero; sem ela, o som de acerto é uma nota de guitarra por trilha (`docs/superpowers/specs/2026-10-08-guitarra-separada-som-acerto-design.md`)."
- Identidade visual, §6, troque "**Acerto:** estalo curto." por "**Acerto:** nota de guitarra com distorção leve, uma por trilha (veja `2026-10-08-guitarra-separada-som-acerto-design.md`); com a guitarra separada, a própria guitarra."
- README, linha do `:core:audio`: `| \`:core:audio\` | Player da música (MediaPlayer, ou faixas separadas misturadas com a guitarra cortada no erro) e sons de acerto e erro (SoundPool), gerados por \`scripts/gerar_sfx.py\` |`.

- [ ] **Step 4: Verificação e commit**

Run: `./scripts/gw detekt lintDebug test koverVerify :core:ui:verifyRoborazziDebug :feature:library:verifyRoborazziDebug :feature:gameplay:verifyRoborazziDebug :feature:results:verifyRoborazziDebug :feature:nowplaying:verifyRoborazziDebug assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A
git commit -m "docs: guitarra separada e som de acerto na especificação e no README" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7 (acrescentada durante a execução): som de erro com gravações CC0

Pedido do usuário depois do teste no aparelho: o som de erro sintetizado não soava natural. Decisão registrada na spec (§2 "Som de erro"): seis variações montadas por `scripts/montar_sfx_erro.py` com gravações CC0 de `third_party/sons` (origem em `third_party/README.md`), `MissSoundPicker` (sorteio sem repetir, força 0,75 a 1, nada a menos de 250 ms) e `SoundPoolSfxPlayer.miss()` encerrando o som anterior num fade de 25 ms. Testes: `MissSoundPickerTest` (5) e `SfxAssetsTest` (`sfx_miss_1..6` ≤ 0,4 s e ≤ −9 dBFS; `sfx_miss.wav` removido).
