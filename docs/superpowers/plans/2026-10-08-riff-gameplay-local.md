# Riff Gameplay com relógio local: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** jogar de verdade: escolher um mapa e a dificuldade na Biblioteca, tocar na pista em perspectiva com o áudio da própria pasta do mapa, ouvir os sons de acerto e erro, ver a chama e o julgamento, e terminar numa tela de Resultado com o recorde salvo.

**Architecture:** o áudio fica num módulo novo `:core:audio` atrás de interfaces (`SongPlayer` com MediaPlayer, `SfxPlayer` com SoundPool). `:core:data` ganha o carregamento das notas de um mapa e os repositórios de recordes e preferências. A lógica da partida fica em `GameSession` (Kotlin testável com fakes), que junta `GameEngine`, `SongClock`, o player e os sons; a tela só desenha e repassa toques. A navegação type-safe entra no `:app` ligando Biblioteca → Jogo → Resultado. O relógio é alimentado pela posição do player local exatamente como será pelo Spotify no plano seguinte.

**Tech Stack:** Navigation Compose 2.10.2 (rotas `@Serializable`), kotlinx-serialization 1.11.0, MediaPlayer e SoundPool do Android, Compose `Canvas` + `withFrameMillis`, `pointerInput` com vários dedos, Hilt, Room, Robolectric + Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-05-riff-design.md` (§2 item 3, §4 "Renderização e input" e "Julgamento", §6, §11 passo 5) e `docs/superpowers/specs/2026-10-07-identidade-visual-design.md` (§5 pista, §6 sons).

## Global Constraints

- Pacote base `com.rodrigomachado.riff`; namespace = pacote base + caminho do módulo (`com.rodrigomachado.riff.core.audio`, `com.rodrigomachado.riff.feature.gameplay`, `com.rodrigomachado.riff.feature.results`).
- Uma feature nunca depende de outra; a navegação entre features é feita no `:app`. Cada feature define a própria rota `@Serializable`.
- Julgamento e pontuação vêm do `GameEngine` existente (Perfeito ±50 ms, Bom ±100 ms, multiplicador como no GH). Nada disso muda aqui.
- Pista (identidade §5): perspectiva com `HighwayGeometry`, gemas `drawGem` (HOPO com centro claro), alvos deitados `drawFretButton` (acesos enquanto o dedo está na trilha, mesmo sem nota), chama `drawFlame` por `FLAME_DURATION_MS`, rastro de nota longa `drawSustainTrail` que brilha enquanto é segurada. Erro não mostra texto: só som e quebra de combo.
- Sons (identidade §6): acerto = estalo curto; erro = corda abafada; tocam no instante do toque (sem offset de calibração); volume `Settings.sfxVolume` (0 desliga). Sintetizados por um script nosso versionado; nada copiado do Guitar Hero.
- Animações reduzidas: com "remover animações" do Android ligado, a chama não é desenhada (o alvo aceso já indica o acerto) e o julgamento aparece sem salto.
- Canhoto (`Settings.leftHanded`): trilhas espelhadas no desenho e no toque.
- Texto visível em resources, `values/` pt-BR e `values-en/`.
- Testes Android em JUnit 4; Robolectric com SDK 35 (`robolectric.properties` por módulo).
- No WSL o Gradle roda por `./scripts/gw`. Antes de cada commit: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt`.
- Todo commit termina com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Decisões deste plano (a especificação não detalhava)

- **Áudio local como modo de jogo atual.** Enquanto o Spotify não existe, o jogo toca o áudio da pasta do mapa (`song.ogg`, `song.opus`, `song.mp3`, `song.wav`, ou `guitar.*`). A spec diz "só em modo de depuração": como só usamos build de debug, o modo fica disponível; o plano do Spotify o restringe a debug.
- **Entrada no jogo:** tocar num mapa da Biblioteca abre a escolha de dificuldade (só as que o mapa tem).
- **Fim da partida:** quando o áudio termina, ou 3 s depois da última nota se todas já foram julgadas.
- **Pausa:** botão "Pausar" na tela e pausa automática quando o app vai para segundo plano (ligação, botão Home). Pausar solta as trilhas seguradas.
- **Dependências novas permitidas:** `:feature:gameplay → :core:game, :core:audio`. A spec §4 é atualizada na Task 12.
- **Trastes da pista** rolam a cada 500 ms de música (o motor não conhece o BPM).
- **Velocidade das notas:** uma unidade de distância da pista vale `450 ms / Settings.noteSpeed`.

## Review Focus

1. **App vai para segundo plano no meio da música:** áudio e jogo pausam, trilhas seguradas são soltas, e "Continuar" retoma de onde parou. Testes nas Tasks 6 e 7.
2. **Dois dedos ao mesmo tempo (acorde):** as duas trilhas registram o toque e soltam separadamente. Teste na Task 8.
3. **Pasta sem áudio:** tela explicando o motivo, com "Voltar", sem crash e sem player criado. Teste na Task 7.
4. **Sair do jogo no meio (voltar):** o áudio para e o player e o SoundPool são liberados. Teste na Task 7.
5. **Áudio termina antes do mapa (ou o mapa tem notas além do áudio):** a partida termina no fim do áudio e o resultado é salvo. Testes nas Tasks 6 e 7.

---

### Task 1: Build: serialização e navegação

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `build-logic/convention/build.gradle.kts`, `build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt`, `app/build.gradle.kts`

**Interfaces:**
- Produces: o plugin `riff.android.feature` passa a aplicar `org.jetbrains.kotlin.plugin.serialization` e a incluir `navigation-compose` e `kotlinx-serialization-core`; o `:app` recebe `navigation-compose`.

- [ ] **Step 1: Catálogo e plugins**

`gradle/libs.versions.toml`, em `[versions]`:
```toml
navigation = "2.10.2"
kotlinxSerialization = "1.11.0"
```
Em `[libraries]`:
```toml
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
kotlinx-serialization-core = { module = "org.jetbrains.kotlinx:kotlinx-serialization-core", version.ref = "kotlinxSerialization" }
kotlin-serialization-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-serialization", version.ref = "kotlin" }
```
Em `[plugins]`:
```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`build.gradle.kts` (raiz), no bloco `plugins`: `alias(libs.plugins.kotlin.serialization) apply false`.

`build-logic/convention/build.gradle.kts`, em `dependencies`: `compileOnly(libs.kotlin.serialization.gradlePlugin)`.

`AndroidFeatureConventionPlugin.kt`: depois de `pluginManager.apply("riff.android.hilt")`, acrescente `pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")`; em `dependencies`, acrescente
```kotlin
                add("implementation", lib("androidx-navigation-compose"))
                add("implementation", lib("kotlinx-serialization-core"))
```

`app/build.gradle.kts`, em `dependencies`: `implementation(libs.androidx.navigation.compose)`.

- [ ] **Step 2: Build e commit**

Run: `./scripts/gw :app:assembleDebug :feature:library:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A
git commit -m "build: serialização e navegação type-safe" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Motor e linha do tempo das notas (`:core:game`)

**Files:**
- Modify: `core/game/src/main/kotlin/com/rodrigomachado/riff/core/game/GameEngine.kt`
- Create: `core/game/src/main/kotlin/com/rodrigomachado/riff/core/game/NoteTimeline.kt`
- Test: `core/game/src/test/kotlin/com/rodrigomachado/riff/core/game/GameEngineTest.kt` (acrescenta)
- Test: `core/game/src/test/kotlin/com/rodrigomachado/riff/core/game/NoteTimelineTest.kt`

**Interfaces:**
- Produces: `GameEngine.count(judgment: Judgment): Int` (sem alocar, para o loop de frame); `object NoteTimeline { fun firstAtOrAfter(notes: List<Note>, timeMs: Long): Int; fun visibleRange(notes: List<Note>, fromMs: Long, toMs: Long): IntRange }`.

- [ ] **Step 1: Testes que falham**

Em `GameEngineTest.kt`, antes do último `}`:
```kotlin
    @Test
    fun countReadsJudgmentsWithoutBuildingState() {
        val engine = GameEngine(listOf(note(1_000), note(2_000), note(3_000)))
        engine.onPress(0, 1_000)
        engine.update(2_200)
        assertEquals(1, engine.count(Judgment.PERFECT))
        assertEquals(1, engine.count(Judgment.MISS))
        assertEquals(0, engine.count(Judgment.GOOD))
    }
```

`NoteTimelineTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.game

import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteTimelineTest {
    private val notes = listOf(100L, 200L, 200L, 300L, 500L).mapIndexed { i, t -> Note(t, i % 5, 0, false) }

    @Test
    fun firstAtOrAfterFindsTheLowerBoundIncludingChords() {
        assertEquals(0, NoteTimeline.firstAtOrAfter(notes, 0))
        assertEquals(1, NoteTimeline.firstAtOrAfter(notes, 200))
        assertEquals(3, NoteTimeline.firstAtOrAfter(notes, 201))
        assertEquals(5, NoteTimeline.firstAtOrAfter(notes, 600))
    }

    @Test
    fun visibleRangeIsInclusiveOnBothEnds() {
        assertEquals(1..3, NoteTimeline.visibleRange(notes, 200, 300))
        assertEquals(0..4, NoteTimeline.visibleRange(notes, 0, 1_000))
        assertEquals(true, NoteTimeline.visibleRange(notes, 600, 900).isEmpty())
        assertEquals(true, NoteTimeline.visibleRange(emptyList(), 0, 100).isEmpty())
    }
}
```

Run: `./scripts/gw :core:game:test`
Expected: falha de compilação (`Unresolved reference 'count'`, `'NoteTimeline'`).

- [ ] **Step 2: Implementar**

Em `GameEngine.kt`, logo depois de `fun judgmentOf(index: Int): Judgment? = judgments[index]`:
```kotlin

    /** Quantas notas já receberam [judgment]. Não aloca: pode ser chamado a cada frame. */
    fun count(judgment: Judgment): Int = counts[judgment.ordinal]
```

`NoteTimeline.kt`:
```kotlin
package com.rodrigomachado.riff.core.game

import com.rodrigomachado.riff.core.model.Note

/** Busca binária nas notas ordenadas por tempo, para desenhar só o que está visível. */
object NoteTimeline {
    /** Índice da primeira nota com `timeMs >= [timeMs]` (ou `notes.size` se não houver). */
    fun firstAtOrAfter(notes: List<Note>, timeMs: Long): Int {
        var low = 0
        var high = notes.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (notes[mid].timeMs < timeMs) low = mid + 1 else high = mid
        }
        return low
    }

    /** Índices das notas com tempo entre [fromMs] e [toMs], inclusive. */
    fun visibleRange(notes: List<Note>, fromMs: Long, toMs: Long): IntRange =
        firstAtOrAfter(notes, fromMs) until firstAtOrAfter(notes, toMs + 1)
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:game:test koverVerify`
Expected: `BUILD SUCCESSFUL`, 3 testes novos.

```bash
git add -A
git commit -m "feat(core-game): contagem de julgamentos sem alocar e linha do tempo das notas" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Trilha sob o dedo (`HighwayGeometry.laneAt`)

**Files:**
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/HighwayGeometry.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/stage/HighwayGeometryTest.kt` (acrescenta)

**Interfaces:**
- Produces: `HighwayGeometry.laneAt(x: Float): Int` — trilha (na tela) sob o x na linha de acerto; fora da pista, a trilha mais próxima.

- [ ] **Step 1: Teste que falha**

Em `HighwayGeometryTest.kt`, antes do último `}`:
```kotlin
    @Test
    fun laneAtMapsTouchesToTheLaneUnderTheFingerAndClampsOutside() {
        (0 until 5).forEach { lane -> assertEquals(lane, geometry.laneAt(geometry.laneCenter(lane, 0f).x)) }
        assertEquals(0, geometry.laneAt(-50f))
        assertEquals(4, geometry.laneAt(5_000f))
    }
```

Run: `./scripts/gw :core:ui:testDebugUnitTest --tests '*HighwayGeometryTest'`
Expected: falha de compilação (`Unresolved reference 'laneAt'`).

- [ ] **Step 2: Implementar**

Em `HighwayGeometry.kt`, depois de `laneCenter`:
```kotlin

    /** Trilha (na tela) sob o [x] na linha de acerto; fora da pista vale a trilha mais próxima. */
    fun laneAt(x: Float): Int {
        val fraction = (x - leftAt(0f)) / widthAt(0f)
        return (fraction * laneCount).toInt().coerceIn(0, laneCount - 1)
    }
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:ui:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A
git commit -m "feat(core-ui): trilha sob o dedo na pista" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Áudio (`:core:audio`): player da música e efeitos sonoros

**Files:**
- Modify: `settings.gradle.kts` (`include(":core:audio")`)
- Create: `scripts/gerar_sfx.py`
- Create: `core/audio/build.gradle.kts`
- Create: `core/audio/src/main/res/raw/sfx_hit.wav`, `core/audio/src/main/res/raw/sfx_miss.wav` (gerados pelo script)
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/Players.kt`
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/MediaSongPlayer.kt`
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/SoundPoolSfxPlayer.kt`
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/AudioModule.kt`
- Test: `core/audio/src/test/kotlin/com/rodrigomachado/riff/core/audio/SfxAssetsTest.kt`

**Interfaces:**
- Produces (pacote `com.rodrigomachado.riff.core.audio`):
  - `interface SongPlayer { suspend fun prepare(uri: String); fun play(); fun pause(); fun release(); val positionMs: Long; val isPlaying: Boolean; val isCompleted: Boolean }`
  - `interface SfxPlayer { fun hit(); fun miss(); fun setVolume(volume: Float); fun release() }`
  - `fun interface SongPlayerFactory { fun create(): SongPlayer }` e `fun interface SfxPlayerFactory { fun create(): SfxPlayer }`, fornecidas pelo Hilt.

- [ ] **Step 1: Módulo e teste que falha**

`core/audio/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff.core.audio"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
}
```

`SfxAssetsTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.sound.sampled.AudioSystem

/** Os efeitos são gerados por scripts/gerar_sfx.py; este teste garante formato e duração curtos. */
class SfxAssetsTest {
    private fun check(name: String, maxSeconds: Double) {
        val format = AudioSystem.getAudioFileFormat(File("src/main/res/raw/$name"))
        assertEquals(44_100f, format.format.sampleRate)
        assertEquals(1, format.format.channels)
        assertEquals(16, format.format.sampleSizeInBits)
        val seconds = format.frameLength / format.format.sampleRate.toDouble()
        assertTrue("$name dura $seconds s", seconds in 0.01..maxSeconds)
    }

    @Test
    fun hitIsAShortClick() = check("sfx_hit.wav", maxSeconds = 0.08)

    @Test
    fun missIsAShortMutedString() = check("sfx_miss.wav", maxSeconds = 0.25)
}
```

Em `settings.gradle.kts`, acrescente `include(":core:audio")`.

Run: `./scripts/gw :core:audio:testDebugUnitTest`
Expected: os 2 testes falham com `FileNotFoundException` (os WAVs ainda não existem).

- [ ] **Step 2: Gerar os sons**

`scripts/gerar_sfx.py`:
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


def write(path, samples):
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 30_000)) for s in samples))


def hit():
    """Estalo curto: senoide aguda com um pouco de ruído, decaindo rápido."""
    rng = random.Random(1)
    count = int(RATE * 0.045)
    return [
        (0.55 * math.sin(2 * math.pi * 1_800 * i / RATE) + 0.45 * (rng.random() * 2 - 1)) * math.exp(-i / (RATE * 0.008))
        for i in range(count)
    ]


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
    write(f"{target}/sfx_hit.wav", hit())
    write(f"{target}/sfx_miss.wav", miss())
```

```bash
mkdir -p core/audio/src/main/res/raw
python3 scripts/gerar_sfx.py core/audio/src/main/res/raw
```

- [ ] **Step 3: Players**

`Players.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

/** Música da partida. Hoje é o áudio da pasta do mapa; o Spotify implementa a mesma ideia no plano seguinte. */
interface SongPlayer {
    /** @throws java.io.IOException se o arquivo não puder ser aberto ou decodificado. */
    suspend fun prepare(uri: String)

    fun play()

    fun pause()

    fun release()

    val positionMs: Long

    val isPlaying: Boolean

    val isCompleted: Boolean
}

/** Sons de acerto e erro, tocados no instante do toque. */
interface SfxPlayer {
    fun hit()

    fun miss()

    /** De 0 (desligado) a 1. */
    fun setVolume(volume: Float)

    fun release()
}

fun interface SongPlayerFactory {
    fun create(): SongPlayer
}

fun interface SfxPlayerFactory {
    fun create(): SfxPlayer
}
```

`MediaSongPlayer.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaSongPlayer(private val context: Context) : SongPlayer {
    private val player = MediaPlayer()

    @Volatile
    private var completed = false

    override suspend fun prepare(uri: String) = withContext(Dispatchers.IO) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        player.setDataSource(context, Uri.parse(uri))
        player.setOnCompletionListener { completed = true }
        player.prepare()
    }

    override fun play() = player.start()

    override fun pause() {
        if (player.isPlaying) player.pause()
    }

    override fun release() = player.release()

    override val positionMs: Long get() = player.currentPosition.toLong()

    override val isPlaying: Boolean get() = player.isPlaying

    override val isCompleted: Boolean get() = completed
}
```

`SoundPoolSfxPlayer.kt`:
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
    private val hitSound = pool.load(context, R.raw.sfx_hit, 1)
    private val missSound = pool.load(context, R.raw.sfx_miss, 1)
    private var volume = DEFAULT_VOLUME

    override fun hit() = play(hitSound)

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
    }
}
```

`AudioModule.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    @Provides
    fun songPlayers(@ApplicationContext context: Context): SongPlayerFactory = SongPlayerFactory { MediaSongPlayer(context) }

    @Provides
    fun sfxPlayers(@ApplicationContext context: Context): SfxPlayerFactory = SfxPlayerFactory { SoundPoolSfxPlayer(context) }
}
```

- [ ] **Step 4: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:audio:testDebugUnitTest :core:audio:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 2 testes passando.

```bash
git add -A
git commit -m "feat(core-audio): player da música, efeitos sonoros sintetizados e fábricas para injeção" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Dados da partida (`:core:data`)

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/importer/ChartFolderReader.kt`
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/importer/ChartImporter.kt` (usa o leitor compartilhado)
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/ChartRepository.kt`, `RoomChartRepository.kt` (`getChart`)
- Modify: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/ChartDao.kt` (`get`), `ScoreDao.kt` (`get`)
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/game/ChartTrackLoader.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/ScoreRepository.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/SettingsRepository.kt`
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/DataModule.kt`
- Modify: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/importer/Fakes.kt` (`getChart`), `feature/library/src/test/kotlin/com/rodrigomachado/riff/feature/library/Fakes.kt` (`getChart`)
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/game/FolderChartTrackLoaderTest.kt`
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/RoomScoreRepositoryTest.kt`

**Interfaces:**
- Consumes: `FolderSource`, `FileTooLargeException`, `ChartLoader`, `LoadedChart`, `ChartRepository`, `ScoreDao`, `RiffPreferences`.
- Produces:
  - `ChartRepository.getChart(id: String): Chart?`
  - pacote `.game`: `data class PlayableChart(val chart: Chart, val difficulty: Difficulty, val notes: List<Note>, val audioUri: String?)`, `class ChartNotFoundException(message: String) : Exception`, `interface ChartTrackLoader { suspend fun load(chartId: String, difficulty: Difficulty): PlayableChart }` e `FolderChartTrackLoader`
  - `interface ScoreRepository { suspend fun save(record: ScoreRecord): Long; suspend fun get(id: Long): ScoreRecord?; suspend fun best(chartId: String, difficulty: Difficulty): ScoreRecord? }` e `RoomScoreRepository`
  - `interface SettingsRepository { val settings: Flow<Settings> }` e `PreferencesSettingsRepository`

- [ ] **Step 1: Testes que falham**

`game/FolderChartTrackLoaderTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.importer.FakeChartRepository
import com.rodrigomachado.riff.core.data.importer.FakeFolderSource
import com.rodrigomachado.riff.core.data.importer.chartText
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderChartTrackLoaderTest {
    private val source = FakeFolderSource()
    private val charts = FakeChartRepository()

    private fun TestScope.loader() = FolderChartTrackLoader(source, charts, StandardTestDispatcher(testScheduler))

    private suspend fun registerChart(folder: String) = charts.saveScanned(
        Chart("id", "Música", "Banda", null, null, null, folder, ChartFormat.CHART, setOf(Difficulty.EXPERT)),
        scanId = 1,
    )

    @Test
    fun loadsTheNotesOfTheChosenDifficultyWithTheChartOffset() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda").replace("[Song]\n{\n", "[Song]\n{\n  Offset = 0.1\n"))
        source.file("raiz/M/song.ogg", "áudio")
        registerChart("raiz/M")

        val playable = loader().load("id", Difficulty.EXPERT)

        assertEquals(listOf(600L), playable.notes.map { it.timeMs })
        assertEquals("raiz/M/song.ogg", playable.audioUri)
    }

    @Test
    fun findsTheAudioIgnoringCaseAndPrefersTheFullSong() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/Guitar.ogg", "guitarra")
        source.file("raiz/M/SONG.MP3", "música")
        registerChart("raiz/M")

        assertEquals("raiz/M/SONG.MP3", loader().load("id", Difficulty.EXPERT).audioUri)
    }

    @Test
    fun folderWithoutAudioHasNoAudioUri() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        registerChart("raiz/M")

        assertNull(loader().load("id", Difficulty.EXPERT).audioUri)
    }

    @Test
    fun missingChartOrDifficultyIsChartNotFound() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        registerChart("raiz/M")

        assertTrue(runCatching { loader().load("outro", Difficulty.EXPERT) }.exceptionOrNull() is ChartNotFoundException)
        assertTrue(runCatching { loader().load("id", Difficulty.EASY) }.exceptionOrNull() is ChartNotFoundException)
    }
}
```

`RoomScoreRepositoryTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomScoreRepositoryTest {
    private lateinit var db: RiffDatabase
    private lateinit var scores: RoomScoreRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scores = RoomScoreRepository(db.scoreDao())
    }

    @After
    fun tearDown() = db.close()

    private fun record(score: Long) = ScoreRecord(0, "a", Difficulty.EXPERT, score, 0.875f, 12, 7, 2, 1, 1_000)

    @Test
    fun savedRecordComesBackWithItsNewId() = runTest {
        db.chartDao().replace(ChartEntity("a", "t", "b", null, null, null, "u", "CHART", 1), emptyList())

        val id = scores.save(record(1_200))

        assertEquals(record(1_200).copy(id = id), scores.get(id))
        assertNull(scores.get(id + 1))
    }

    @Test
    fun bestIsTheHighestScoreOfThatDifficulty() = runTest {
        db.chartDao().replace(ChartEntity("a", "t", "b", null, null, null, "u", "CHART", 1), emptyList())
        scores.save(record(500))
        val bestId = scores.save(record(900))

        assertEquals(bestId, scores.best("a", Difficulty.EXPERT)?.id)
        assertNull(scores.best("a", Difficulty.EASY))
    }
}
```

Run: `./scripts/gw :core:data:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'FolderChartTrackLoader'`, `'RoomScoreRepository'`, `'getChart'`).

- [ ] **Step 2: Leitor compartilhado e repositórios**

`importer/ChartFolderReader.kt`:
```kotlin
package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.chart.ChartLoader
import com.rodrigomachado.riff.core.chart.LoadedChart
import com.rodrigomachado.riff.core.data.folder.FileTooLargeException
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource

/** Lê os arquivos de mapa de uma pasta (com limite de tamanho) e monta o [LoadedChart]. */
internal object ChartFolderReader {
    private val READ_FILES = listOf("notes.chart", "notes.mid", "song.ini")

    suspend fun read(source: FolderSource, children: List<FolderEntry>, maxBytes: Long): LoadedChart {
        val contents = mutableMapOf<String, ByteArray>()
        for (name in READ_FILES) {
            val entry = children.firstOrNull { !it.isDirectory && it.name.equals(name, ignoreCase = true) } ?: continue
            if (entry.sizeBytes > maxBytes) throw FileTooLargeException(entry.name, maxBytes)
            contents[name] = source.read(entry, maxBytes)
        }
        return ChartLoader.load { name -> contents[name.lowercase()] }
    }
}
```

`importer/ChartImporter.kt`: no `load(folder, children)`, troque o laço que monta `contents` e a chamada `ChartLoader.load { ... }` por
```kotlin
        val loaded = ChartFolderReader.read(source, children, maxFileBytes)
```
e remova `READ_FILES` do `companion object` e os imports que ficarem sem uso.

`ChartDao.kt`: acrescente
```kotlin
    @Transaction
    @Query("SELECT * FROM charts WHERE id = :id")
    abstract suspend fun get(id: String): ChartWithDifficulties?
```

`ScoreDao.kt`: acrescente
```kotlin
    @Query("SELECT * FROM scores WHERE id = :id")
    suspend fun get(id: Long): ScoreEntity?
```

`ChartRepository.kt`: acrescente na interface
```kotlin
    suspend fun getChart(id: String): Chart?
```
`RoomChartRepository.kt`: acrescente
```kotlin
    override suspend fun getChart(id: String): Chart? = dao.get(id)?.toModel()
```

Nos dois `Fakes.kt` de teste (`core/data/.../importer/Fakes.kt` e `feature/library/.../Fakes.kt`), acrescente ao `FakeChartRepository`:
```kotlin
    override suspend fun getChart(id: String): Chart? = charts.firstOrNull { it.id == id }
```
(no fake da biblioteca, `charts` é o `MutableStateFlow`: use `charts.value.firstOrNull { it.id == id }`).

`game/ChartTrackLoader.kt`:
```kotlin
package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.data.importer.ChartFolderReader
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Mapa pronto para jogar: notas da dificuldade escolhida e o áudio da pasta, se houver. */
data class PlayableChart(val chart: Chart, val difficulty: Difficulty, val notes: List<Note>, val audioUri: String?)

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
        val audio = AUDIO_NAMES.firstNotNullOfOrNull { name ->
            children.firstOrNull { !it.isDirectory && it.name.equals(name, ignoreCase = true) }
        }
        PlayableChart(chart, difficulty, notes, audio?.uri)
    }

    private companion object {
        /** Música completa primeiro; a faixa da guitarra serve se não houver outra. */
        val AUDIO_NAMES = listOf("song.ogg", "song.opus", "song.mp3", "song.wav", "guitar.ogg", "guitar.opus", "guitar.mp3")
    }
}
```

`ScoreRepository.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.database.ScoreDao
import com.rodrigomachado.riff.core.database.ScoreEntity
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
import javax.inject.Inject

interface ScoreRepository {
    /** Grava e devolve o id gerado (o `id` do [record] é ignorado). */
    suspend fun save(record: ScoreRecord): Long

    suspend fun get(id: Long): ScoreRecord?

    suspend fun best(chartId: String, difficulty: Difficulty): ScoreRecord?
}

class RoomScoreRepository @Inject constructor(private val dao: ScoreDao) : ScoreRepository {
    override suspend fun save(record: ScoreRecord): Long = dao.insert(record.toEntity())

    override suspend fun get(id: Long): ScoreRecord? = dao.get(id)?.toModel()

    override suspend fun best(chartId: String, difficulty: Difficulty): ScoreRecord? = dao.best(chartId, difficulty.name)?.toModel()
}

private fun ScoreRecord.toEntity() =
    ScoreEntity(0, chartId, difficulty.name, score, accuracy, maxCombo, perfect, good, miss, playedAtEpochMs)

private fun ScoreEntity.toModel(): ScoreRecord? {
    val level = Difficulty.entries.firstOrNull { it.name == difficulty } ?: return null
    return ScoreRecord(id, chartId, level, score, accuracy, maxCombo, perfect, good, miss, playedAtEpochMs)
}
```

`SettingsRepository.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.datastore.RiffPreferences
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface SettingsRepository {
    val settings: Flow<Settings>
}

class PreferencesSettingsRepository @Inject constructor(preferences: RiffPreferences) : SettingsRepository {
    override val settings: Flow<Settings> = preferences.settings
}
```

`DataModule.kt`, em `DataBindsModule`:
```kotlin
    @Binds
    abstract fun chartTrackLoader(impl: FolderChartTrackLoader): ChartTrackLoader

    @Binds
    abstract fun scoreRepository(impl: RoomScoreRepository): ScoreRepository

    @Binds
    abstract fun settingsRepository(impl: PreferencesSettingsRepository): SettingsRepository
```
com os imports de `game.ChartTrackLoader` e `game.FolderChartTrackLoader`.

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:data:testDebugUnitTest :core:database:testDebugUnitTest :feature:library:testDebugUnitTest koverVerify`
Expected: `BUILD SUCCESSFUL`; 6 testes novos e os anteriores do importador continuam verdes.

```bash
git add -A
git commit -m "feat(core-data): carregar notas e áudio de um mapa, recordes e preferências" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Sessão de jogo (`GameSession`)

**Files:**
- Modify: `settings.gradle.kts` (`include(":feature:gameplay")`)
- Create: `feature/gameplay/build.gradle.kts`
- Create: `feature/gameplay/src/test/resources/robolectric.properties`
- Create: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameSession.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/Fakes.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/GameSessionTest.kt`

**Interfaces:**
- Consumes: `GameEngine`, `SongClock`, `TimeSource`, `NoteTimeline` (`:core:game`); `SongPlayer`, `SfxPlayer` (`:core:audio`); `FLAME_DURATION_MS` (`:core:ui`).
- Produces: `class GameSession(notes, player, sfx, timeSource, audioOffsetMs)` com `start()`, `pause()`, `resume()`, `tick(): Long`, `press(lane)`, `release(lane)`, `isPressed(lane)`, `flameProgress(lane): Float` (negativo se nunca acendeu), `judgmentAgeMs(): Long`, `val engine`, `val songTimeMs`, `val lastJudgment`, `val lookBehindMs`, `val isPaused`, `val isFinished`. Constantes `SYNC_INTERVAL_MS = 500`, `END_GRACE_MS = 3_000`.

- [ ] **Step 1: Módulo, fakes e testes que falham**

`feature/gameplay/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.gameplay"
}

dependencies {
    implementation(project(":core:game"))
    implementation(project(":core:audio"))
}
```

`feature/gameplay/src/test/resources/robolectric.properties`:
```properties
sdk=35
```

`Fakes.kt` (em `src/test`):
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Note

fun note(timeMs: Long, lane: Int = 0, sustainMs: Long = 0) = Note(timeMs, lane, sustainMs, isHopo = false)

class FakeTime(var now: Long = 0) : TimeSource {
    override fun nowMs() = now
}

class FakeSongPlayer : SongPlayer {
    var preparedUri: String? = null
    var prepareFailure: Exception? = null
    var released = false
    override var positionMs: Long = 0
    override var isPlaying: Boolean = false
    override var isCompleted: Boolean = false

    override suspend fun prepare(uri: String) {
        prepareFailure?.let { throw it }
        preparedUri = uri
    }

    override fun play() {
        isPlaying = true
    }

    override fun pause() {
        isPlaying = false
    }

    override fun release() {
        released = true
    }
}

class FakeSfxPlayer : SfxPlayer {
    var hits = 0
    var misses = 0
    var lastVolume = -1f
    var released = false

    override fun hit() {
        hits++
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

`GameSessionTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.model.Judgment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {
    private val time = FakeTime()
    private val player = FakeSongPlayer()
    private val sfx = FakeSfxPlayer()

    private fun session(vararg notes: com.rodrigomachado.riff.core.model.Note) =
        GameSession(notes.toList(), player, sfx, time, audioOffsetMs = 0).also { it.start() }

    @Test
    fun hitPlaysTheHitSoundLightsTheFlameAndShowsTheJudgment() {
        player.positionMs = 1_000
        val session = session(note(1_000, lane = 2))

        session.press(2)

        assertEquals(1, sfx.hits)
        assertEquals(Judgment.PERFECT, session.lastJudgment)
        assertEquals(0f, session.flameProgress(2), 0.001f)
        time.now = 125
        assertEquals(0.5f, session.flameProgress(2), 0.001f)
        assertTrue(session.flameProgress(0) < 0f)
    }

    @Test
    fun tapWithNoNoteNearPlaysTheMissSoundWithoutJudgmentText() {
        val session = session(note(5_000))

        session.press(0)

        assertEquals(1, sfx.misses)
        assertEquals(null, session.lastJudgment)
        assertTrue(session.isPressed(0))
    }

    @Test
    fun noteThatPassesUnplayedPlaysTheMissSoundOnce() {
        val session = session(note(1_000))
        player.positionMs = 1_500
        time.now = SYNC

        session.tick()
        time.now = SYNC + 16
        session.tick()

        assertEquals(1, sfx.misses)
        assertEquals(1, session.engine.count(Judgment.MISS))
    }

    @Test
    fun clockFollowsThePlayerAndResyncsPeriodically() {
        val session = session(note(10_000))
        time.now = 400
        assertEquals(400L, session.tick()) // extrapolado desde o início

        player.positionMs = 8_000 // o usuário avançou a música
        time.now = SYNC + 1
        assertEquals(8_000L, session.tick())
    }

    @Test
    fun pausingStopsTheSongReleasesHeldLanesAndIgnoresTaps() {
        player.positionMs = 1_000
        val session = session(note(1_000, lane = 1, sustainMs = 2_000))
        session.press(1)

        session.pause()

        assertFalse(player.isPlaying)
        assertTrue(session.isPaused)
        assertFalse(session.isPressed(1))
        assertTrue(session.engine.state.activeSustains.isEmpty())
        session.press(3)
        assertFalse(session.isPressed(3))

        session.resume()
        assertTrue(player.isPlaying)
    }

    @Test
    fun finishesWhenTheSongEnds() {
        val session = session(note(60_000))
        assertFalse(session.isFinished)

        player.isCompleted = true

        assertTrue(session.isFinished)
    }

    @Test
    fun finishesAfterTheGracePeriodOnceEveryNoteIsJudged() {
        player.positionMs = 1_000
        val session = session(note(1_000))
        session.press(0)
        assertFalse(session.isFinished)

        player.positionMs = 1_000 + GameSession.END_GRACE_MS + 1
        time.now = SYNC
        session.tick()

        assertTrue(session.isFinished)
    }

    private companion object {
        const val SYNC = GameSession.SYNC_INTERVAL_MS
    }
}
```

Em `settings.gradle.kts`, acrescente `include(":feature:gameplay")`.

Run: `./scripts/gw :feature:gameplay:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'GameSession'`).

- [ ] **Step 2: Implementar**

`GameSession.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.game.GameEngine
import com.rodrigomachado.riff.core.game.SongClock
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import com.rodrigomachado.riff.core.ui.stage.FLAME_DURATION_MS
import kotlin.math.max

/**
 * Uma partida: junta o motor, o relógio da música, o player e os sons.
 * Não conhece a tela; a tela chama [tick] a cada frame e repassa os toques.
 */
class GameSession(
    notes: List<Note>,
    private val player: SongPlayer,
    private val sfx: SfxPlayer,
    private val timeSource: TimeSource,
    audioOffsetMs: Long,
) {
    val engine = GameEngine(notes)
    private val clock = SongClock(timeSource, audioOffsetMs)
    private val pressed = BooleanArray(Note.LANE_COUNT)
    private val flameStartedAt = LongArray(Note.LANE_COUNT) { NEVER }
    private val lastNoteEndMs = engine.notes.maxOfOrNull { it.timeMs + it.sustainMs } ?: 0L
    private var lastSyncAt = NEVER
    private var missesHeard = 0
    private var lastJudgmentAt = NEVER

    /** Quanto tempo atrás do relógio ainda desenhar (notas perdidas passando e rastros longos). */
    val lookBehindMs: Long = max(MISS_TAIL_MS, engine.notes.maxOfOrNull { it.sustainMs } ?: 0L)

    var songTimeMs: Long = 0
        private set

    var lastJudgment: Judgment? = null
        private set

    val isPaused: Boolean get() = !player.isPlaying

    val isFinished: Boolean
        get() = player.isCompleted || (engine.isFinished && songTimeMs > lastNoteEndMs + END_GRACE_MS)

    fun start() {
        player.play()
        syncClock()
    }

    fun pause() {
        player.pause()
        syncClock()
        for (lane in pressed.indices) release(lane)
    }

    fun resume() {
        player.play()
        syncClock()
    }

    /** Avança a partida até o instante atual. Chamado a cada frame. */
    fun tick(): Long {
        if (timeSource.nowMs() - lastSyncAt >= SYNC_INTERVAL_MS) syncClock()
        songTimeMs = clock.positionMs()
        engine.update(songTimeMs)
        val misses = engine.count(Judgment.MISS)
        if (misses > missesHeard) {
            missesHeard = misses
            sfx.miss()
        }
        return songTimeMs
    }

    fun press(lane: Int) {
        if (isPaused) return
        pressed[lane] = true
        val judgment = engine.onPress(lane, clock.positionMs())
        if (judgment == null) {
            sfx.miss()
            return
        }
        sfx.hit()
        val now = timeSource.nowMs()
        flameStartedAt[lane] = now
        lastJudgment = judgment
        lastJudgmentAt = now
    }

    fun release(lane: Int) {
        if (!pressed[lane]) return
        pressed[lane] = false
        engine.onRelease(lane, clock.positionMs())
    }

    fun isPressed(lane: Int): Boolean = pressed[lane]

    /** Progresso da chama da [lane] de 0 a 1 (acima de 1 já apagou); negativo se nunca acendeu. */
    fun flameProgress(lane: Int): Float {
        val start = flameStartedAt[lane]
        if (start == NEVER) return -1f
        return (timeSource.nowMs() - start).toFloat() / FLAME_DURATION_MS
    }

    /** Há quanto tempo apareceu o último julgamento. */
    fun judgmentAgeMs(): Long = if (lastJudgmentAt == NEVER) Long.MAX_VALUE else timeSource.nowMs() - lastJudgmentAt

    private fun syncClock() {
        val now = timeSource.nowMs()
        clock.sync(player.positionMs, !player.isPlaying, now)
        lastSyncAt = now
    }

    companion object {
        const val SYNC_INTERVAL_MS = 500L
        const val END_GRACE_MS = 3_000L
        private const val MISS_TAIL_MS = 300L
        private const val NEVER = Long.MIN_VALUE
    }
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :feature:gameplay:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 7 testes passando.

```bash
git add -A
git commit -m "feat(gameplay): sessão de jogo com relógio, sons, chama, pausa e fim da partida" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: ViewModel do jogo

**Files:**
- Create: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModel.kt`
- Create: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayModule.kt`
- Create: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayRoute.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModelTest.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/Fakes.kt` (acrescenta)

**Interfaces:**
- Consumes: `ChartTrackLoader`, `PlayableChart`, `ChartNotFoundException`, `ScoreRepository`, `SettingsRepository`, `FolderAccessLostException` (`:core:data`); `SongPlayerFactory`, `SfxPlayerFactory`; `GameSession`.
- Produces:
  - `@Serializable data class GameplayRoute(val chartId: String, val difficulty: String)`
  - `sealed interface GameplayUiState { Loading; Ready(title, artist, difficulty, session, settings, isPaused); Unavailable(reason: UnavailableReason); Finished(scoreId: Long) }` com `enum class UnavailableReason { NO_AUDIO, CHART_MISSING, FOLDER_ACCESS_LOST, AUDIO_FAILED }`
  - `GameplayViewModel` com `uiState`, `pause()`, `resume()`, `onSessionFinished()`.
  - `GameplayModule` (Hilt) fornecendo `TimeSource` com `SystemClock.elapsedRealtime()`.

- [ ] **Step 1: Fakes e testes que falham**

Em `Fakes.kt`, acrescente:
```kotlin
class FakeChartTrackLoader : com.rodrigomachado.riff.core.data.game.ChartTrackLoader {
    var result: () -> com.rodrigomachado.riff.core.data.game.PlayableChart = { error("defina o resultado") }

    override suspend fun load(chartId: String, difficulty: com.rodrigomachado.riff.core.model.Difficulty) = result()
}

class FakeSettingsRepository(settings: com.rodrigomachado.riff.core.model.Settings) : com.rodrigomachado.riff.core.data.SettingsRepository {
    override val settings = kotlinx.coroutines.flow.MutableStateFlow(settings)
}

class FakeScoreRepository : com.rodrigomachado.riff.core.data.ScoreRepository {
    val saved = mutableListOf<com.rodrigomachado.riff.core.model.ScoreRecord>()

    override suspend fun save(record: com.rodrigomachado.riff.core.model.ScoreRecord): Long {
        saved += record
        return saved.size.toLong()
    }

    override suspend fun get(id: Long) = saved.getOrNull(id.toInt() - 1)?.copy(id = id)

    override suspend fun best(chartId: String, difficulty: com.rodrigomachado.riff.core.model.Difficulty) = null
}
```

`GameplayViewModelTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartNotFoundException
import com.rodrigomachado.riff.core.data.game.PlayableChart
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameplayViewModelTest {
    private val loader = FakeChartTrackLoader()
    private val settings = FakeSettingsRepository(Settings.DEFAULT.copy(sfxVolume = 0.3f, audioOffsetMs = -40))
    private val scores = FakeScoreRepository()
    private val player = FakeSongPlayer()
    private val sfx = FakeSfxPlayer()
    private var playersCreated = 0
    private val time = FakeTime()

    private val chart = Chart("a", "Música", "Banda", null, null, null, "raiz/a", ChartFormat.CHART, setOf(Difficulty.EXPERT))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = GameplayViewModel(
        SavedStateHandle(mapOf("chartId" to "a", "difficulty" to "EXPERT")),
        loader,
        settings,
        scores,
        { playersCreated++; player },
        { sfx },
        time,
    )

    private fun playable(audio: String? = "raiz/a/song.ogg") =
        PlayableChart(chart, Difficulty.EXPERT, listOf(note(1_000), note(2_000)), audio)

    @Test
    fun loadsPreparesTheSongAndStartsPlaying() = runTest {
        loader.result = { playable() }

        val state = viewModel().uiState.value

        assertTrue(state is GameplayUiState.Ready)
        assertEquals("raiz/a/song.ogg", player.preparedUri)
        assertTrue(player.isPlaying)
        assertEquals(0.3f, sfx.lastVolume)
    }

    @Test
    fun folderWithoutAudioExplainsWhyAndCreatesNoPlayer() = runTest {
        loader.result = { playable(audio = null) }

        assertEquals(GameplayUiState.Unavailable(UnavailableReason.NO_AUDIO), viewModel().uiState.value)
        assertEquals(0, playersCreated)
    }

    @Test
    fun missingChartAndLostFolderAreExplained() = runTest {
        loader.result = { throw ChartNotFoundException("sumiu") }
        assertEquals(GameplayUiState.Unavailable(UnavailableReason.CHART_MISSING), viewModel().uiState.value)

        loader.result = { throw FolderAccessLostException("revogada") }
        assertEquals(GameplayUiState.Unavailable(UnavailableReason.FOLDER_ACCESS_LOST), viewModel().uiState.value)
    }

    @Test
    fun audioThatCannotBePlayedIsExplainedAndReleased() = runTest {
        loader.result = { playable() }
        player.prepareFailure = java.io.IOException("formato desconhecido")

        assertEquals(GameplayUiState.Unavailable(UnavailableReason.AUDIO_FAILED), viewModel().uiState.value)
        assertTrue(player.released)
    }

    @Test
    fun pauseAndResumeAreReflectedInTheState() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()

        viewModel.pause()
        assertTrue((viewModel.uiState.value as GameplayUiState.Ready).isPaused)
        viewModel.resume()
        assertEquals(false, (viewModel.uiState.value as GameplayUiState.Ready).isPaused)
    }

    @Test
    fun finishingSavesTheScoreAndReleasesAudio() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()
        val session = (viewModel.uiState.value as GameplayUiState.Ready).session
        player.positionMs = 1_000
        time.now = GameSession.SYNC_INTERVAL_MS
        session.tick()
        session.press(0)

        viewModel.onSessionFinished()
        viewModel.onSessionFinished() // chamada repetida não grava de novo

        assertEquals(GameplayUiState.Finished(1), viewModel.uiState.value)
        val record = scores.saved.single()
        assertEquals("a", record.chartId)
        assertEquals(Difficulty.EXPERT, record.difficulty)
        assertEquals(100L, record.score)
        assertEquals(1, record.perfect)
        assertTrue(player.released)
        assertTrue(sfx.released)
    }

    @Test
    fun leavingMidSongReleasesTheAudio() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()

        viewModel.release()

        assertTrue(player.released)
        assertTrue(sfx.released)
        assertNull(scores.saved.firstOrNull())
    }
}
```

Run: `./scripts/gw :feature:gameplay:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'GameplayViewModel'`, `'GameplayUiState'`).

- [ ] **Step 2: Implementar**

`GameplayRoute.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import kotlinx.serialization.Serializable

@Serializable
data class GameplayRoute(val chartId: String, val difficulty: String)
```

`GameplayModule.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import android.os.SystemClock
import com.rodrigomachado.riff.core.game.TimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object GameplayModule {
    @Provides
    fun timeSource(): TimeSource = TimeSource { SystemClock.elapsedRealtime() }
}
```

`GameplayViewModel.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.audio.SongPlayerFactory
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartNotFoundException
import com.rodrigomachado.riff.core.data.game.ChartTrackLoader
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.ScoreRecord
import com.rodrigomachado.riff.core.model.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UnavailableReason { NO_AUDIO, CHART_MISSING, FOLDER_ACCESS_LOST, AUDIO_FAILED }

sealed interface GameplayUiState {
    data object Loading : GameplayUiState

    data class Ready(
        val title: String,
        val artist: String,
        val difficulty: Difficulty,
        val session: GameSession,
        val settings: Settings,
        val isPaused: Boolean = false,
    ) : GameplayUiState

    data class Unavailable(val reason: UnavailableReason) : GameplayUiState

    data class Finished(val scoreId: Long) : GameplayUiState
}

@HiltViewModel
class GameplayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loader: ChartTrackLoader,
    private val settingsRepository: SettingsRepository,
    private val scores: ScoreRepository,
    private val songPlayers: SongPlayerFactory,
    private val sfxPlayers: SfxPlayerFactory,
    private val timeSource: TimeSource,
) : ViewModel() {
    private val chartId: String = checkNotNull(savedStateHandle["chartId"])
    private val difficulty: Difficulty = Difficulty.valueOf(checkNotNull(savedStateHandle["difficulty"]))
    private val state = MutableStateFlow<GameplayUiState>(GameplayUiState.Loading)
    val uiState: StateFlow<GameplayUiState> = state.asStateFlow()

    private var player: SongPlayer? = null
    private var sfx: SfxPlayer? = null
    private var finishing = false

    init {
        viewModelScope.launch { state.value = load() }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // cada falha vira uma explicação na tela
    private suspend fun load(): GameplayUiState {
        val playable = try {
            loader.load(chartId, difficulty)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChartNotFoundException) {
            return GameplayUiState.Unavailable(UnavailableReason.CHART_MISSING)
        } catch (e: FolderAccessLostException) {
            return GameplayUiState.Unavailable(UnavailableReason.FOLDER_ACCESS_LOST)
        } catch (e: Exception) {
            return GameplayUiState.Unavailable(UnavailableReason.CHART_MISSING)
        }
        val audioUri = playable.audioUri ?: return GameplayUiState.Unavailable(UnavailableReason.NO_AUDIO)
        val settings = settingsRepository.settings.first()
        val songPlayer = songPlayers.create().also { player = it }
        try {
            songPlayer.prepare(audioUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            release()
            return GameplayUiState.Unavailable(UnavailableReason.AUDIO_FAILED)
        }
        val effects = sfxPlayers.create().also {
            sfx = it
            it.setVolume(settings.sfxVolume)
        }
        val session = GameSession(playable.notes, songPlayer, effects, timeSource, settings.audioOffsetMs.toLong())
        session.start()
        return GameplayUiState.Ready(playable.chart.title, playable.chart.artist, difficulty, session, settings)
    }

    fun pause() {
        val ready = state.value as? GameplayUiState.Ready ?: return
        ready.session.pause()
        state.update { ready.copy(isPaused = true) }
    }

    fun resume() {
        val ready = state.value as? GameplayUiState.Ready ?: return
        ready.session.resume()
        state.update { ready.copy(isPaused = false) }
    }

    fun onSessionFinished() {
        val ready = state.value as? GameplayUiState.Ready ?: return
        if (finishing) return
        finishing = true
        release()
        val engine = ready.session.engine
        val record = ScoreRecord(
            id = 0,
            chartId = chartId,
            difficulty = difficulty,
            score = engine.score,
            accuracy = engine.accuracy(),
            maxCombo = engine.state.maxCombo,
            perfect = engine.count(Judgment.PERFECT),
            good = engine.count(Judgment.GOOD),
            miss = engine.count(Judgment.MISS),
            playedAtEpochMs = System.currentTimeMillis(),
        )
        viewModelScope.launch { state.value = GameplayUiState.Finished(scores.save(record)) }
    }

    /** Para e libera o áudio. Também chamado ao sair do jogo. */
    fun release() {
        player?.release()
        sfx?.release()
        player = null
        sfx = null
    }

    override fun onCleared() = release()
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :feature:gameplay:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 7 testes novos.

```bash
git add -A
git commit -m "feat(gameplay): ViewModel do jogo com carregamento, pausa, fim e liberação do áudio" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Tela do jogo (pista em movimento, toque e HUD)

**Files:**
- Create: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/StageRenderer.kt`
- Create: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayScreen.kt`
- Create: `feature/gameplay/src/main/res/values/strings.xml`, `feature/gameplay/src/main/res/values-en/strings.xml`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/GameStageTest.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayScreenshotTest.kt`

**Interfaces:**
- Consumes: `GameSession`, `GameplayUiState`, `GameplayViewModel`; `HighwayGeometry`, `drawHighway`, `drawGem`, `drawFretButton`, `drawFlame`, `drawSustainTrail`, `ScoreText`, `MultiplierStamp`, `JudgmentText`, `LaneColors` (`:core:ui`); `NoteTimeline` (`:core:game`).
- Produces: `@Composable fun GameplayScreen(onFinished: (scoreId: Long) -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier, viewModel: GameplayViewModel = hiltViewModel())`; `internal fun DrawScope.drawStage(session, msPerUnit, leftHanded, reducedMotion)`; `internal fun screenLane(lane: Int, leftHanded: Boolean): Int`; `@Composable internal fun GameStage(state: GameplayUiState.Ready, reducedMotion: Boolean, onPause, onResume, onExit, onSessionFinished, modifier)`; tag `GameplayTestTags.STAGE = "gameplay_stage"`.

- [ ] **Step 1: Strings**

`feature/gameplay/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="gameplay_judgment_perfect">PERFEITO!</string>
    <string name="gameplay_judgment_good">BOM!</string>
    <string name="gameplay_combo">COMBO %1$d</string>
    <string name="gameplay_pause">Pausar</string>
    <string name="gameplay_paused">Pausado</string>
    <string name="gameplay_resume">Continuar</string>
    <string name="gameplay_exit">Sair</string>
    <string name="gameplay_back">Voltar</string>
    <string name="gameplay_unavailable_title">Não dá para jogar</string>
    <string name="gameplay_no_audio">Esta pasta não tem áudio (song.ogg, song.mp3…). Por enquanto o jogo toca o áudio da própria pasta; com o Spotify, isso muda.</string>
    <string name="gameplay_chart_missing">Este mapa não está mais na biblioteca ou não tem esta dificuldade.</string>
    <string name="gameplay_folder_lost">Perdi o acesso à pasta deste mapa. Escolha a pasta de novo na biblioteca.</string>
    <string name="gameplay_audio_failed">Não consegui tocar o áudio desta pasta.</string>
</resources>
```

`feature/gameplay/src/main/res/values-en/strings.xml`:
```xml
<resources>
    <string name="gameplay_judgment_perfect">PERFECT!</string>
    <string name="gameplay_judgment_good">GOOD!</string>
    <string name="gameplay_combo">COMBO %1$d</string>
    <string name="gameplay_pause">Pause</string>
    <string name="gameplay_paused">Paused</string>
    <string name="gameplay_resume">Resume</string>
    <string name="gameplay_exit">Quit</string>
    <string name="gameplay_back">Back</string>
    <string name="gameplay_unavailable_title">Can\'t play</string>
    <string name="gameplay_no_audio">This folder has no audio (song.ogg, song.mp3…). For now the game plays the folder\'s own audio; that changes with Spotify.</string>
    <string name="gameplay_chart_missing">This chart is no longer in the library or doesn\'t have this difficulty.</string>
    <string name="gameplay_folder_lost">Lost access to this chart\'s folder. Choose the folder again in the library.</string>
    <string name="gameplay_audio_failed">Couldn\'t play this folder\'s audio.</string>
</resources>
```

- [ ] **Step 2: Testes que falham**

`GameStageTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.stage.HighwayGeometry
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameStageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val time = FakeTime()
    private val player = FakeSongPlayer()
    private val sfx = FakeSfxPlayer()

    private fun show(leftHanded: Boolean = false): GameSession {
        val session = GameSession(listOf(note(60_000)), player, sfx, time, audioOffsetMs = 0).also { it.start() }
        val state = GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, Settings.DEFAULT.copy(leftHanded = leftHanded))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RiffTheme { GameStage(state, reducedMotion = true, onPause = {}, onResume = {}, onExit = {}, onSessionFinished = {}) }
        }
        return session
    }

    private fun laneX(lane: Int, width: Float, height: Float) = HighwayGeometry(Size(width, height)).laneCenter(lane, 0f).x

    @Test
    fun twoFingersPressAndReleaseTwoLanesIndependently() {
        val session = show()

        composeRule.onNodeWithTag(GameplayTestTags.STAGE).performTouchInput {
            val y = height * 0.8f
            down(0, Offset(laneX(1, width.toFloat(), height.toFloat()), y))
            down(1, Offset(laneX(3, width.toFloat(), height.toFloat()), y))
        }
        assertTrue(session.isPressed(1))
        assertTrue(session.isPressed(3))

        composeRule.onNodeWithTag(GameplayTestTags.STAGE).performTouchInput { up(0) }
        assertFalse(session.isPressed(1))
        assertTrue(session.isPressed(3))
        assertEquals(2, sfx.misses) // nenhuma nota perto: dois toques fantasmas
    }

    @Test
    fun leftHandedMirrorsTheLanes() {
        val session = show(leftHanded = true)

        composeRule.onNodeWithTag(GameplayTestTags.STAGE).performTouchInput {
            down(0, Offset(laneX(0, width.toFloat(), height.toFloat()), height * 0.8f))
        }

        assertTrue(session.isPressed(4))
    }
}
```

`GameplayScreenshotTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class GameplayScreenshotTest {
    @Test
    fun stageMidSong() {
        val time = FakeTime()
        val player = FakeSongPlayer().apply { positionMs = 4_000 }
        val notes = listOf(
            Note(4_000, 2, 0, false),
            Note(4_450, 0, 0, false),
            Note(4_900, 1, 0, true),
            Note(5_350, 3, 900, false),
            Note(5_800, 4, 0, false),
            Note(6_250, 2, 0, false),
            Note(6_700, 0, 0, false),
        )
        val session = GameSession(notes, player, FakeSfxPlayer(), time, audioOffsetMs = 0).also {
            it.start()
            it.tick()
            it.press(2) // acerta a nota amarela agora: chama e "PERFEITO!"
        }
        time.now = 40
        val state = GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, Settings.DEFAULT)
        captureRoboImage("src/test/screenshots/Gameplay_stage.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    GameStage(state, reducedMotion = false, onPause = {}, onResume = {}, onExit = {}, onSessionFinished = {}, animate = false)
                }
            }
        }
    }
}
```

Run: `./scripts/gw :feature:gameplay:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'GameStage'`, `'GameplayTestTags'`).

- [ ] **Step 3: Desenho da pista**

`StageRenderer.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.rodrigomachado.riff.core.game.NoteTimeline
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import com.rodrigomachado.riff.core.ui.stage.HighwayGeometry
import com.rodrigomachado.riff.core.ui.stage.drawFlame
import com.rodrigomachado.riff.core.ui.stage.drawFretButton
import com.rodrigomachado.riff.core.ui.stage.drawGem
import com.rodrigomachado.riff.core.ui.stage.drawHighway
import com.rodrigomachado.riff.core.ui.stage.drawSustainTrail
import com.rodrigomachado.riff.core.ui.theme.LaneColors
import kotlin.math.min

internal const val BASE_MS_PER_DISTANCE = 450f
private const val GEM_RADIUS_FRACTION = 0.42f
private const val TRAIL_WIDTH_FRACTION = 0.35f
private const val MISSED_ALPHA = 0.4f
private const val FRET_SPACING_MS = 500L
private const val FLAME_WIDTH_FRACTION = 0.62f
private const val FLAME_HEIGHT_FRACTION = 1.7f

/** Trilha na tela para a trilha lógica [lane] (canhoto espelha). Vale nos dois sentidos. */
internal fun screenLane(lane: Int, leftHanded: Boolean): Int = if (leftHanded) Note.LANE_COUNT - 1 - lane else lane

/** Desenha a pista no instante atual da [session]. */
internal fun DrawScope.drawStage(session: GameSession, msPerUnit: Float, leftHanded: Boolean, reducedMotion: Boolean) {
    val geometry = HighwayGeometry(size)
    val t = session.songTimeMs
    val visibleMs = (geometry.visibleDistance * msPerUnit).toLong()
    drawHighway(geometry, fretDistances(t, visibleMs, msPerUnit))

    val radius = geometry.laneWidthAtStrike * GEM_RADIUS_FRACTION
    val notes = session.engine.notes
    val range = NoteTimeline.visibleRange(notes, t - session.lookBehindMs, t + visibleMs)
    for (i in range.last downTo range.first) {
        val note = notes[i]
        val judgment = session.engine.judgmentOf(i)
        val lane = screenLane(note.lane, leftHanded)
        val color = LaneColors.forLane(note.lane)
        val head = (note.timeMs - t) / msPerUnit
        val wasHit = judgment == Judgment.PERFECT || judgment == Judgment.GOOD
        if (note.sustainMs > 0) {
            val tail = min((note.timeMs + note.sustainMs - t) / msPerUnit, geometry.visibleDistance)
            val start = if (wasHit) maxOf(head, 0f) else maxOf(head, geometry.nearDistance)
            if (tail > start) {
                val holding = wasHit && session.isPressed(note.lane)
                drawSustainTrail(
                    geometry.laneCenter(lane, start),
                    geometry.laneCenter(lane, tail),
                    radius * TRAIL_WIDTH_FRACTION * geometry.scaleAt(start),
                    color,
                    active = holding,
                )
            }
        }
        if (wasHit || head < geometry.nearDistance || head > geometry.visibleDistance) continue
        val gemColor = if (judgment == Judgment.MISS) color.copy(alpha = MISSED_ALPHA) else color
        drawGem(geometry.laneCenter(lane, head), radius * geometry.scaleAt(head), gemColor, note.isHopo)
    }

    for (lane in 0 until Note.LANE_COUNT) {
        val center = geometry.laneCenter(screenLane(lane, leftHanded), 0f)
        drawFretButton(center, radius, LaneColors.forLane(lane), session.isPressed(lane))
    }
    if (reducedMotion) return
    for (lane in 0 until Note.LANE_COUNT) {
        val progress = session.flameProgress(lane)
        if (progress !in 0f..1f) continue
        drawFlame(
            geometry.laneCenter(screenLane(lane, leftHanded), 0f),
            geometry.laneWidthAtStrike * FLAME_WIDTH_FRACTION,
            geometry.laneWidthAtStrike * FLAME_HEIGHT_FRACTION,
            progress,
        )
    }
}

/** Trastes a cada [FRET_SPACING_MS] de música, rolando junto com as notas. */
private fun fretDistances(t: Long, visibleMs: Long, msPerUnit: Float): List<Float> {
    val first = (t / FRET_SPACING_MS + 1) * FRET_SPACING_MS
    return generateSequence(first) { it + FRET_SPACING_MS }
        .takeWhile { it - t <= visibleMs }
        .map { (it - t) / msPerUnit }
        .toList()
}
```

- [ ] **Step 4: Tela**

`GameplayScreen.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import android.provider.Settings.Global
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.stage.HighwayGeometry
import com.rodrigomachado.riff.core.ui.stage.JudgmentText
import com.rodrigomachado.riff.core.ui.stage.MultiplierStamp
import com.rodrigomachado.riff.core.ui.stage.ScoreText
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameMillis

object GameplayTestTags {
    const val STAGE = "gameplay_stage"
}

private const val JUDGMENT_VISIBLE_MS = 500L
private const val JUDGMENT_BOUNCE_MS = 120f
private const val JUDGMENT_BOUNCE_SCALE = 0.25f
private const val COMBO_MIN_TO_SHOW = 10
private const val SCRIM_ALPHA = 0.8f

@Composable
fun GameplayScreen(onFinished: (scoreId: Long) -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier, viewModel: GameplayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pause() }
    KeepScreenOn()
    when (val current = state) {
        GameplayUiState.Loading -> LoadingState(modifier)
        is GameplayUiState.Unavailable -> EmptyState(
            title = stringResource(R.string.gameplay_unavailable_title),
            message = stringResource(current.reason.message()),
            actionLabel = stringResource(R.string.gameplay_back),
            onAction = onExit,
            modifier = modifier,
        )
        is GameplayUiState.Ready -> GameStage(
            state = current,
            reducedMotion = rememberReducedMotion(),
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onExit = {
                viewModel.release()
                onExit()
            },
            onSessionFinished = viewModel::onSessionFinished,
            modifier = modifier,
        )
        is GameplayUiState.Finished -> LaunchedEffect(current.scoreId) { onFinished(current.scoreId) }
    }
}

/**
 * Palco da partida. [animate] liga o loop de frames (os screenshots desligam para congelar um instante).
 */
@Composable
internal fun GameStage(
    state: GameplayUiState.Ready,
    reducedMotion: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
    onSessionFinished: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val session = state.session
    val leftHanded = state.settings.leftHanded
    val msPerUnit = BASE_MS_PER_DISTANCE / state.settings.noteSpeed
    var frame by remember { mutableLongStateOf(0L) }
    if (animate) {
        LaunchedEffect(session) {
            while (isActive) {
                withFrameMillis {
                    session.tick()
                    frame++
                }
                if (session.isFinished) {
                    onSessionFinished()
                    break
                }
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag(GameplayTestTags.STAGE)
                .pointerInput(session, leftHanded) { handleLanes(session, leftHanded) },
        ) {
            frame // lê o contador para redesenhar a cada frame
            drawStage(session, msPerUnit, leftHanded, reducedMotion)
        }
        Hud(session, frame, reducedMotion, onPause, Modifier.fillMaxSize())
        if (state.isPaused) PauseOverlay(onResume, onExit)
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.handleLanes(session: GameSession, leftHanded: Boolean) {
    val lanes = mutableMapOf<PointerId, Int>()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val geometry = HighwayGeometry(Size(size.width.toFloat(), size.height.toFloat()))
            for (change in event.changes) {
                if (change.changedToDown()) {
                    val lane = screenLane(geometry.laneAt(change.position.x), leftHanded)
                    lanes[change.id] = lane
                    session.press(lane)
                    change.consume()
                } else if (change.changedToUp()) {
                    lanes.remove(change.id)?.let(session::release)
                    change.consume()
                }
            }
        }
    }
}

@Composable
private fun Hud(session: GameSession, frame: Long, reducedMotion: Boolean, onPause: () -> Unit, modifier: Modifier) {
    frame // recompõe o placar a cada frame
    val engine = session.engine
    Box(modifier.padding(Spacing.md)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            ScoreText(engine.score)
            TextButton(onClick = onPause) { Text(stringResource(R.string.gameplay_pause), color = RiffColors.Paper) }
            MultiplierStamp(engine.multiplier)
        }
        val combo = engine.state.combo
        if (combo >= COMBO_MIN_TO_SHOW) {
            Text(
                stringResource(R.string.gameplay_combo, combo),
                style = MaterialTheme.typography.labelLarge,
                color = RiffColors.Paper,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.xl * 2),
            )
        }
        val age = session.judgmentAgeMs()
        val judgment = session.lastJudgment
        if (judgment != null && age < JUDGMENT_VISIBLE_MS) {
            val bounce = if (reducedMotion) 1f else 1f + JUDGMENT_BOUNCE_SCALE * (1f - (age / JUDGMENT_BOUNCE_MS).coerceAtMost(1f))
            JudgmentText(
                stringResource(if (judgment == Judgment.PERFECT) R.string.gameplay_judgment_perfect else R.string.gameplay_judgment_good),
                Modifier.align(Alignment.Center).graphicsLayer {
                    scaleX = bounce
                    scaleY = bounce
                },
            )
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(RiffColors.StageBlack.copy(alpha = SCRIM_ALPHA)),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PosterTitle(stringResource(R.string.gameplay_paused))
        PosterButton(stringResource(R.string.gameplay_resume), onClick = onResume)
        TextButton(onClick = onExit) { Text(stringResource(R.string.gameplay_exit), color = RiffColors.Paper) }
    }
}

/** "Remover animações" do Android: escala de animação zerada. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { Global.getFloat(context.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private fun UnavailableReason.message(): Int = when (this) {
    UnavailableReason.NO_AUDIO -> R.string.gameplay_no_audio
    UnavailableReason.CHART_MISSING -> R.string.gameplay_chart_missing
    UnavailableReason.FOLDER_ACCESS_LOST -> R.string.gameplay_folder_lost
    UnavailableReason.AUDIO_FAILED -> R.string.gameplay_audio_failed
}
```

- [ ] **Step 5: Testes, screenshot e verificação visual**

Run: `./scripts/gw detekt -PdetektAutoCorrect`, depois `./scripts/gw :feature:gameplay:recordRoborazziDebug` e `./scripts/gw detekt :feature:gameplay:testDebugUnitTest :feature:gameplay:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`; `GameStageTest` (2) e o screenshot.

Abra `feature/gameplay/src/test/screenshots/Gameplay_stage.png` com a ferramenta Read e compare com o mockup aprovado da pista em perspectiva: notas nascendo pequenas no fundo, alvo amarelo aceso com a chama, rastro azul da nota longa, placar "100", carimbo "x1", "PERFEITO!" no centro e "Pausar" no topo. Ajuste e regrave se algo destoar.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(gameplay): tela do jogo com pista em movimento, toque com vários dedos, HUD e pausa" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Resultado (`:feature:results`) e dificuldades no `:core:ui`

**Files:**
- Modify: `settings.gradle.kts` (`include(":feature:results")`)
- Create: `core/ui/src/main/res/values/difficulties.xml`, `core/ui/src/main/res/values-en/difficulties.xml`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/DifficultyLabels.kt`
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryScreen.kt` e as strings da biblioteca (usa os rótulos do `:core:ui`)
- Create: `feature/results/build.gradle.kts`, `feature/results/src/test/resources/robolectric.properties`
- Create: `feature/results/src/main/kotlin/com/rodrigomachado/riff/feature/results/ResultsRoute.kt`
- Create: `feature/results/src/main/kotlin/com/rodrigomachado/riff/feature/results/ResultsViewModel.kt`
- Create: `feature/results/src/main/kotlin/com/rodrigomachado/riff/feature/results/ResultsScreen.kt`
- Create: `feature/results/src/main/res/values/strings.xml`, `feature/results/src/main/res/values-en/strings.xml`
- Test: `feature/results/src/test/kotlin/com/rodrigomachado/riff/feature/results/ResultsViewModelTest.kt`
- Test: `feature/results/src/test/kotlin/com/rodrigomachado/riff/feature/results/ResultsContentTest.kt`
- Test: `feature/results/src/test/kotlin/com/rodrigomachado/riff/feature/results/ResultsScreenshotTest.kt`

**Interfaces:**
- Produces:
  - `:core:ui`: strings `difficulty_easy/medium/hard/expert` e `@StringRes fun difficultyLabel(index: Int): Int` (0 = Fácil … 3 = Expert; o `:core:ui` não conhece o `Difficulty` do modelo).
  - `@Serializable data class ResultsRoute(val scoreId: Long)`
  - `sealed interface ResultsUiState { Loading; Missing; Ready(record: ScoreRecord, title: String, artist: String, isNewBest: Boolean) }`
  - `ResultsViewModel` e `@Composable fun ResultsScreen(onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit, onBackToLibrary: () -> Unit, modifier, viewModel)`; `ResultsContent(state, onPlayAgain, onBackToLibrary, modifier)`.

- [ ] **Step 1: Rótulos de dificuldade no `:core:ui`**

`core/ui/src/main/res/values/difficulties.xml`:
```xml
<resources>
    <string name="difficulty_easy">Fácil</string>
    <string name="difficulty_medium">Médio</string>
    <string name="difficulty_hard">Difícil</string>
    <string name="difficulty_expert">Expert</string>
</resources>
```
`values-en/difficulties.xml`: `Easy`, `Medium`, `Hard`, `Expert`.

`DifficultyLabels.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.annotation.StringRes
import com.rodrigomachado.riff.core.ui.R

/** Rótulo da dificuldade de índice [index] (0 = Fácil … 3 = Expert, a ordem de `Difficulty`). */
@StringRes
fun difficultyLabel(index: Int): Int = when (index) {
    0 -> R.string.difficulty_easy
    1 -> R.string.difficulty_medium
    2 -> R.string.difficulty_hard
    else -> R.string.difficulty_expert
}
```

Na biblioteca, troque a função `Difficulty.label()` por `difficultyLabel(difficulty.ordinal)` (import de `com.rodrigomachado.riff.core.ui.component.difficultyLabel`) e remova `library_difficulty_*` dos dois `strings.xml` da biblioteca.

- [ ] **Step 2: Módulo de resultado e testes que falham**

`feature/results/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.results"
}
```
`feature/results/src/test/resources/robolectric.properties`: `sdk=35`.

`feature/results/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="results_new_best">NOVO RECORDE</string>
    <string name="results_accuracy">Precisão</string>
    <string name="results_max_combo">Maior combo</string>
    <string name="results_perfect">Perfeitos</string>
    <string name="results_good">Bons</string>
    <string name="results_miss">Erros</string>
    <string name="results_play_again">Jogar de novo</string>
    <string name="results_library">Biblioteca</string>
    <string name="results_missing">Este resultado não existe mais.</string>
</resources>
```
`values-en/strings.xml`: `NEW BEST`, `Accuracy`, `Best combo`, `Perfect`, `Good`, `Misses`, `Play again`, `Library`, `This result no longer exists.`

`ResultsViewModelTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.results

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.ScoreRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

fun record(id: Long, score: Long) = ScoreRecord(id, "a", Difficulty.HARD, score, 0.875f, 42, 70, 10, 5, 0)

class FakeScores(private val records: List<ScoreRecord>) : ScoreRepository {
    override suspend fun save(record: ScoreRecord) = error("não usado")

    override suspend fun get(id: Long) = records.firstOrNull { it.id == id }

    override suspend fun best(chartId: String, difficulty: Difficulty) = records.maxByOrNull { it.score }
}

class FakeCharts : ChartRepository {
    override fun observeCharts(): Flow<List<Chart>> = flowOf(emptyList())

    override suspend fun getChart(id: String) =
        Chart(id, "Música", "Banda", null, null, null, "raiz/$id", ChartFormat.CHART, setOf(Difficulty.HARD))

    override suspend fun saveScanned(chart: Chart, scanId: Long) = Unit

    override suspend fun removeNotSeenIn(scanId: Long) = Unit

    override suspend fun keepFolder(folderUri: String, scanId: Long) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(scoreId: Long, records: List<ScoreRecord>) =
        ResultsViewModel(SavedStateHandle(mapOf("scoreId" to scoreId)), FakeScores(records), FakeCharts())

    @Test
    fun highestScoreIsANewBest() = runTest {
        val state = viewModel(2, listOf(record(1, 500), record(2, 900))).uiState.value
        assertEquals(ResultsUiState.Ready(record(2, 900), "Música", "Banda", isNewBest = true), state)
    }

    @Test
    fun lowerScoreIsNotANewBest() = runTest {
        val state = viewModel(1, listOf(record(1, 500), record(2, 900))).uiState.value as ResultsUiState.Ready
        assertEquals(false, state.isNewBest)
    }

    @Test
    fun unknownResultIsMissing() = runTest {
        assertEquals(ResultsUiState.Missing, viewModel(7, emptyList()).uiState.value)
    }
}
```

`ResultsContentTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.results

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class ResultsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheStatsAndPlaysAgainWithTheSameChartAndDifficulty() {
        var playAgain: Pair<String, Difficulty>? = null
        composeRule.setContent {
            RiffTheme {
                ResultsContent(
                    ResultsUiState.Ready(record(2, 48_300), "Música", "Banda", isNewBest = true),
                    onPlayAgain = { id, difficulty -> playAgain = id to difficulty },
                    onBackToLibrary = {},
                )
            }
        }

        composeRule.onNodeWithText("48.300").assertExists()
        composeRule.onNodeWithText("NOVO RECORDE").assertExists()
        composeRule.onNodeWithText("88%").assertExists()
        composeRule.onNodeWithText("DIFÍCIL").assertExists()
        composeRule.onNodeWithText("Jogar de novo").performClick()
        assertEquals("a" to Difficulty.HARD, playAgain)
    }
}
```

`ResultsScreenshotTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.results

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class ResultsScreenshotTest {
    @Test
    fun newBest() {
        captureRoboImage("src/test/screenshots/Results_new_best.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    ResultsContent(
                        ResultsUiState.Ready(record(2, 48_300), "Through the Fire and Flames", "DragonForce", isNewBest = true),
                        onPlayAgain = { _, _ -> },
                        onBackToLibrary = {},
                    )
                }
            }
        }
    }
}
```

Em `settings.gradle.kts`, acrescente `include(":feature:results")`.

Run: `./scripts/gw :feature:results:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'ResultsViewModel'`, `'ResultsContent'`).

- [ ] **Step 3: Implementar**

`ResultsRoute.kt`:
```kotlin
package com.rodrigomachado.riff.feature.results

import kotlinx.serialization.Serializable

@Serializable
data class ResultsRoute(val scoreId: Long)
```

`ResultsViewModel.kt`:
```kotlin
package com.rodrigomachado.riff.feature.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.model.ScoreRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ResultsUiState {
    data object Loading : ResultsUiState

    data object Missing : ResultsUiState

    data class Ready(val record: ScoreRecord, val title: String, val artist: String, val isNewBest: Boolean) : ResultsUiState
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scores: ScoreRepository,
    private val charts: ChartRepository,
) : ViewModel() {
    private val scoreId: Long = checkNotNull(savedStateHandle["scoreId"])
    private val state = MutableStateFlow<ResultsUiState>(ResultsUiState.Loading)
    val uiState: StateFlow<ResultsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val record = scores.get(scoreId)
            state.value = if (record == null) {
                ResultsUiState.Missing
            } else {
                val chart = charts.getChart(record.chartId)
                val best = scores.best(record.chartId, record.difficulty)
                ResultsUiState.Ready(record, chart?.title.orEmpty(), chart?.artist.orEmpty(), isNewBest = best?.id == record.id)
            }
        }
    }
}
```

`ResultsScreen.kt`:
```kotlin
package com.rodrigomachado.riff.feature.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.difficultyLabel
import com.rodrigomachado.riff.core.ui.stage.ScoreText
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import java.text.NumberFormat

@Composable
fun ResultsScreen(
    onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResultsContent(state, onPlayAgain, onBackToLibrary, modifier)
}

@Composable
fun ResultsContent(
    state: ResultsUiState,
    onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ResultsUiState.Loading -> LoadingState(modifier)
        ResultsUiState.Missing -> EmptyState(
            title = stringResource(R.string.results_missing),
            message = "",
            actionLabel = stringResource(R.string.results_library),
            onAction = onBackToLibrary,
            modifier = modifier,
        )
        is ResultsUiState.Ready -> ReadyResults(state, onPlayAgain, onBackToLibrary, modifier)
    }
}

@Composable
private fun ReadyResults(
    state: ResultsUiState.Ready,
    onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier,
) {
    val record = state.record
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        PosterTitle(state.title, style = MaterialTheme.typography.headlineMedium)
        Text(state.artist, style = MaterialTheme.typography.titleMedium, color = RiffColors.PaperDim)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(stringResource(difficultyLabel(record.difficulty.ordinal)), BadgeTone.NEUTRAL)
            if (state.isNewBest) StatusBadge(stringResource(R.string.results_new_best), BadgeTone.SUCCESS)
        }
        ScoreText(record.score)
        Column(
            Modifier.fillMaxWidth().background(RiffColors.Paper, MaterialTheme.shapes.small).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Stat(stringResource(R.string.results_accuracy), NumberFormat.getPercentInstance(locale).format(record.accuracy))
            Stat(stringResource(R.string.results_max_combo), record.maxCombo.toString())
            Stat(stringResource(R.string.results_perfect), record.perfect.toString())
            Stat(stringResource(R.string.results_good), record.good.toString())
            Stat(stringResource(R.string.results_miss), record.miss.toString())
        }
        PosterButton(stringResource(R.string.results_play_again), onClick = { onPlayAgain(record.chartId, record.difficulty) })
        TextButton(onClick = onBackToLibrary) { Text(stringResource(R.string.results_library), color = RiffColors.Paper) }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = RiffColors.Ink)
        Text(value, style = MaterialTheme.typography.titleMedium, color = RiffColors.Ink)
    }
}
```

- [ ] **Step 4: Testes, screenshots e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect`, depois `./scripts/gw :feature:results:recordRoborazziDebug :feature:library:recordRoborazziDebug` e `./scripts/gw detekt :core:ui:testDebugUnitTest :feature:library:testDebugUnitTest :feature:library:verifyRoborazziDebug :feature:results:testDebugUnitTest :feature:results:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. Abra `Results_new_best.png` e confira a hierarquia: título, artista, carimbos, placar grande, cartão de papel com os números e os botões.

```bash
git add -A
git commit -m "feat(results): tela de resultado com recorde e rótulos de dificuldade no core-ui" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Biblioteca: escolher dificuldade e jogar

**Files:**
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryScreen.kt`
- Create: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryRoute.kt`
- Modify: `feature/library/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Modify: `feature/library/src/test/kotlin/com/rodrigomachado/riff/feature/library/LibraryContentTest.kt` e `LibraryScreenshotTest.kt` (novo parâmetro `onPlay`)

**Interfaces:**
- Produces: `@Serializable data object LibraryRoute`; `LibraryScreen(onPlay: (chartId: String, difficulty: Difficulty) -> Unit, modifier, viewModel)`; `LibraryContent(state, onPickFolder, onRescan, onReportDismissed, onPlay, modifier)`.

- [ ] **Step 1: Teste que falha**

Em `LibraryContentTest.kt`: acrescente `private var played: Pair<String, Difficulty>? = null`, passe `onPlay = { id, d -> played = id to d }` no `show`, e o teste:
```kotlin
    @Test
    fun tappingAChartOffersItsDifficultiesAndPlaysTheChosenOne() {
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(chart("a", title = "Música", difficulties = setOf(Difficulty.EASY, Difficulty.EXPERT))),
            ),
        )

        composeRule.onNodeWithText("Música").performClick()
        composeRule.onNodeWithText("Escolha a dificuldade").assertExists()
        composeRule.onNodeWithText("Médio").assertDoesNotExist()
        composeRule.onNodeWithText("Expert").performClick()

        assertEquals("a" to Difficulty.EXPERT, played)
    }
```
Em `LibraryScreenshotTest.kt`, acrescente `onPlay = { _, _ -> }` às duas chamadas de `LibraryContent`.

Run: `./scripts/gw :feature:library:testDebugUnitTest`
Expected: falha de compilação (`No parameter with name 'onPlay'`).

- [ ] **Step 2: Implementar**

Strings (pt e en): `library_choose_difficulty` = "Escolha a dificuldade" / "Choose difficulty"; `library_cancel` = "Cancelar" / "Cancel".

`LibraryRoute.kt`:
```kotlin
package com.rodrigomachado.riff.feature.library

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute
```

`LibraryScreen.kt`:
- `LibraryScreen` ganha o primeiro parâmetro `onPlay: (chartId: String, difficulty: Difficulty) -> Unit` e o repassa ao `LibraryContent`.
- `LibraryContent` ganha `onPlay: (String, Difficulty) -> Unit` antes do `modifier`; no início do corpo: `var choosing by remember { mutableStateOf<Chart?>(null) }`; passe `onChartClick = { choosing = it }` ao `ChartList`; e, ao final do corpo do `Column`:
```kotlin
        choosing?.let { chart ->
            DifficultyDialog(
                chart = chart,
                onChoose = { difficulty ->
                    choosing = null
                    onPlay(chart.id, difficulty)
                },
                onDismiss = { choosing = null },
            )
        }
```
- `ChartList` ganha `onChartClick: (Chart) -> Unit` e passa `onClick = { onChartClick(chart) }` ao `TrackCard`.
- Nova função:
```kotlin
@Composable
private fun DifficultyDialog(chart: Chart, onChoose: (Difficulty) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(RiffColors.StageBlack, MaterialTheme.shapes.large).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(chart.title, style = MaterialTheme.typography.titleLarge, color = RiffColors.Paper)
            Text(stringResource(R.string.library_choose_difficulty), style = MaterialTheme.typography.bodyMedium, color = RiffColors.PaperDim)
            chart.difficulties.sorted().forEach { difficulty ->
                PosterButton(stringResource(difficultyLabel(difficulty.ordinal)), onClick = { onChoose(difficulty) })
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel), color = RiffColors.Paper) }
        }
    }
}
```
(imports: `androidx.compose.ui.window.Dialog`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`.)

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :feature:library:testDebugUnitTest :feature:library:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A
git commit -m "feat(library): escolher a dificuldade e começar a partida" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Navegação no app

**Files:**
- Modify: `app/build.gradle.kts` (dependências de `:feature:gameplay` e `:feature:results`)
- Create: `app/src/main/kotlin/com/rodrigomachado/riff/RiffNavHost.kt`
- Modify: `app/src/main/kotlin/com/rodrigomachado/riff/MainActivity.kt`

**Interfaces:**
- Consumes: `LibraryRoute`/`LibraryScreen`, `GameplayRoute`/`GameplayScreen`, `ResultsRoute`/`ResultsScreen`.

- [ ] **Step 1: NavHost**

`app/build.gradle.kts`: acrescente `implementation(project(":feature:gameplay"))` e `implementation(project(":feature:results"))`.

`RiffNavHost.kt`:
```kotlin
package com.rodrigomachado.riff

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rodrigomachado.riff.feature.gameplay.GameplayRoute
import com.rodrigomachado.riff.feature.gameplay.GameplayScreen
import com.rodrigomachado.riff.feature.library.LibraryRoute
import com.rodrigomachado.riff.feature.library.LibraryScreen
import com.rodrigomachado.riff.feature.results.ResultsRoute
import com.rodrigomachado.riff.feature.results.ResultsScreen

/** Biblioteca → Jogo → Resultado. As features não se conhecem; a ligação é feita aqui. */
@Composable
fun RiffNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = LibraryRoute, modifier = modifier) {
        composable<LibraryRoute> {
            LibraryScreen(onPlay = { chartId, difficulty -> navController.navigate(GameplayRoute(chartId, difficulty.name)) })
        }
        composable<GameplayRoute> {
            GameplayScreen(
                onFinished = { scoreId ->
                    navController.navigate(ResultsRoute(scoreId)) { popUpTo<GameplayRoute> { inclusive = true } }
                },
                onExit = { navController.popBackStack() },
            )
        }
        composable<ResultsRoute> {
            ResultsScreen(
                onPlayAgain = { chartId, difficulty ->
                    navController.navigate(GameplayRoute(chartId, difficulty.name)) { popUpTo<ResultsRoute> { inclusive = true } }
                },
                onBackToLibrary = { navController.popBackStack(LibraryRoute, inclusive = false) },
            )
        }
    }
}
```

`MainActivity.kt`: troque `LibraryScreen(Modifier.safeDrawingPadding())` por `RiffNavHost(Modifier.safeDrawingPadding())` e ajuste os imports.

- [ ] **Step 2: Verificação completa e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt lintDebug test koverVerify :core:ui:verifyRoborazziDebug :feature:library:verifyRoborazziDebug :feature:gameplay:verifyRoborazziDebug :feature:results:verifyRoborazziDebug assembleDebug`
Expected: `BUILD SUCCESSFUL`, lint sem erros, grafo do Hilt completo.

```bash
git add -A
git commit -m "feat(app): navegação Biblioteca, Jogo e Resultado" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Partida de verdade no emulador, spec e README

**Files:**
- Modify: `docs/superpowers/specs/2026-10-05-riff-design.md` (§4 dependências e decisões deste plano)
- Modify: `README.md`

- [ ] **Step 1: Mapa de exemplo com áudio**

Gere um mapa de 12 s a 120 BPM com uma nota por batida (trilhas em ciclo) e um áudio de metrônomo do mesmo tamanho, e envie para a pasta já usada pela biblioteca no emulador:
```bash
SC=<scratchpad>/riff-sample/Metronomo
mkdir -p "$SC"
python3 - "$SC" <<'PY'
import math, struct, sys, wave
out = sys.argv[1]
rate = 44_100
samples = []
for i in range(rate * 12):
    t = i / rate
    beat = t % 0.5
    samples.append(0.6 * math.sin(2 * math.pi * 1_000 * t) * math.exp(-beat / 0.02) if beat < 0.06 else 0.0)
with wave.open(f"{out}/song.wav", "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
    w.writeframes(b"".join(struct.pack("<h", int(s * 30_000)) for s in samples))
notes = "\n".join(f"  {192 * (k + 1)} = N {k % 5} 0" for k in range(20))
with open(f"{out}/notes.chart", "w") as f:
    f.write('[Song]\n{\n  Name = "Metronomo"\n  Artist = "Riff"\n  Resolution = 192\n}\n[SyncTrack]\n{\n  0 = B 120000\n}\n[ExpertSingle]\n{\n' + notes + "\n}\n")
PY
ADB="/mnt/c/Users/Rodrigo Machado/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" push "$(wslpath -w "$SC")" /sdcard/Download/RiffCharts/
```

- [ ] **Step 2: Jogar no emulador**

Instale o APK, abra o app (a biblioteca já aponta para `Download/RiffCharts`), toque em "Rescan", toque no cartão "Metronomo", escolha "Expert". Use o `uiautomator dump` e `input tap` como no plano anterior. Durante a partida:
- capture a tela (`screencap`) duas vezes com 1 s de intervalo e confira que as notas andaram;
- toque algumas vezes nas trilhas (`input tap X Y` na altura de 80% da tela) e capture: alvo aceso, chama e "PERFECT!"/"GOOD!" quando acertar;
- espere o fim (cerca de 15 s) e confira a tela de Resultado com o placar e "NEW BEST";
- toque em "Play again" e depois volte à biblioteca;
- no meio de outra partida, aperte Home (`input keyevent KEYCODE_HOME`), volte ao app e confira o "Paused";
- sem `FATAL EXCEPTION` no `logcat`.

Registre no ledger o que foi visto em cada passo.

- [ ] **Step 3: Especificação e README**

Na spec principal (§4, parágrafo "Dependências permitidas"), acrescente: "`:feature:gameplay` também depende de `:core:game` (motor) e `:core:audio` (player e sons)." Na §11, ao passo 5, acrescente: "Implementado com o áudio da pasta do mapa (`song.*` ou `guitar.*`); a partida termina no fim do áudio ou 3 s depois da última nota julgada."

No `README.md`, na tabela de módulos, acrescente:
```markdown
| `:core:audio` | Player da música (MediaPlayer) e sons de acerto e erro (SoundPool), gerados por `scripts/gerar_sfx.py` |
| `:feature:gameplay` | A partida: pista em movimento, toque com vários dedos, chama, sons, pausa e fim |
| `:feature:results` | Resultado da partida e recorde |
```
Troque a linha do `:app` por `| `:app` | Activity, Application com Hilt e navegação Biblioteca → Jogo → Resultado |` e, em "Status": `Fundação, identidade visual, biblioteca e gameplay com o áudio da pasta prontos. Próximos planos: integração com o Spotify (inclui o casamento manual), calibração e configurações.`

- [ ] **Step 4: Verificação e commit**

Run: `./scripts/gw detekt lintDebug test koverVerify :core:ui:verifyRoborazziDebug :feature:library:verifyRoborazziDebug :feature:gameplay:verifyRoborazziDebug :feature:results:verifyRoborazziDebug assembleDebug`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A
git commit -m "docs: gameplay local na especificação e no README" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
