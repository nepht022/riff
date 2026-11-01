# Riff no Windows — Etapa 2b: Áudio no PC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O PC toca a música de um mapa (arquivo único ou faixas separadas, com a guitarra cortando e voltando), os sons de erro e os sons do menu; a latência e a sincronia do áudio do PC ficam medidas. O celular fica igual.

**Architecture:** `:core:audio` vira multiplataforma. Ficam em comum as interfaces (`SongPlayer`, `SfxPlayer`, `PcmSource`, `AudioSink`, `StemPlayer`, `MenuSoundPlayer`, `PlaybackInterruptions`), o mixer, o leitor em blocos, o relógio de quadros apresentados e o `StemSongPlayer` (os três valores padrão que hoje usam o Android viram funções por plataforma). No PC: decodificação com **FFmpeg** (bytedeco, 8.1.2-1.5.14 — escolha minha: um caminho só para ogg/opus/mp3/wav, dentro ou fora do `.sng`, com reamostragem) sempre para **48 kHz estéreo float**; saída com **Java Sound** (`SourceDataLine`, 16 bits); a música de arquivo único toca pelo mesmo `StemSongPlayer` (uma faixa só); os WAV dos sons (hoje em `core/audio/src/main/res/raw`) servem o PC como recursos, sem cópia.

**Tech Stack:** `org.bytedeco:ffmpeg:8.1.2-1.5.14` + `org.bytedeco:javacpp:1.5.14` com o classificador do sistema em que se compila (`windows-x86_64` no PC do usuário, `linux-x86_64` no CI); `javax.sound.sampled`.

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§6 etapa 2; risco "Áudio no PC: … a escolha precisa ser medida (latência e sincronia) cedo, na etapa 2").

## Global Constraints

- O celular fica igual: os testes do `:core:audio` (Robolectric) passam sem mudança de comportamento; o Android continua com MediaPlayer/MediaCodec/AudioTrack/SoundPool.
- Sem duplicar código entre plataformas (o `StemSongPlayer` é um só) nem os WAV dos sons.
- Nada do Guitar Hero (sons): os mesmos WAV do app.
- Lição da 2a: testes do PC que navegam clicam na thread da janela (`clickOnWindowThread`); `.preferences_pb` no nome do arquivo de preferências.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue`.

## Review Focus

1. **Faixas separadas com taxas ou canais diferentes** (uma 44,1 kHz mono, outra 48 kHz estéreo): no PC tocam juntas (tudo sai do decodificador em 48 kHz estéreo). Teste na Task 2.
2. **Faixa dentro de `.sng`** (mascarada): decodifica igual a um arquivo solto e não deixa arquivo temporário nem arquivo preso no Windows depois de fechar. Teste na Task 2.
3. **Arquivo de áudio corrompido ou de formato desconhecido:** `IOException` (a partida avisa como no celular), nunca travar a thread nem derrubar o app. Teste na Task 2.
4. **PC sem saída de som** (sem caixa/fone, dispositivo ocupado): abrir a saída lança `IOException`, que o `StemSongPlayer` já trata como falha de preparo. Teste na Task 3.
5. **Posição da música no PC** ao tocar, pausar, continuar e reposicionar: nunca volta para trás nem salta (é o relógio da partida). Teste na Task 3 com uma saída falsa e na medição real da Task 5.

---

### Task 1: `:core:audio` multiplataforma

**Files:**
- Modify: `core/audio/build.gradle.kts` (`riff.kmp.library` + `riff.kmp.hilt`; `commonMain`: `:core:model`, `:core:chart`, coroutines; o `res` do Android segue em `src/androidMain/res`)
- Move para `commonMain`: `Players.kt`, `StemAudio.kt`, `StemMixer.kt`, `PcmBlockReader.kt`, `PresentedFrames.kt`, `MissSoundPicker.kt`, `MenuSoundPicker.kt`, `LocalSong.kt` (qualificador), a interface `MenuSoundPlayer` e `PlaybackInterruptions`/`Factory` (interfaces), `StemSongPlayer.kt`.
- Fica em `androidMain`: `AudioModule.kt`, `MediaSongPlayer.kt`, `MediaCodecPcmSource.kt`, `AudioTrackSink.kt`, `SoundPoolSfxPlayer.kt`, `SoundPoolMenuSounds`, `AndroidPlaybackInterruptions`, `SngMediaDataSource.kt`, `res/raw`.
- Create: `commonMain/.../AudioPlatform.kt` — `internal expect fun audioLog(message: String, error: Throwable?)`, `internal expect fun uptimeMs(): Long`, `internal expect fun raiseAudioThreadPriority()`; `actual` Android = `Log.w(TAG, …)`, `SystemClock.uptimeMillis()`, `Process.setThreadPriority(THREAD_PRIORITY_AUDIO)`; `actual` PC = `System.err`/nada, `System.nanoTime() / 1_000_000`, `Thread.currentThread().priority = Thread.MAX_PRIORITY`.
- Testes → `androidHostTest`.

**Interfaces:**
- Produces: as mesmas APIs de hoje, agora em `commonMain`; os padrões do construtor do `StemSongPlayer` usam `audioLog`, `uptimeMs` e `raiseAudioThreadPriority`.

- [ ] **Step 1: Linha de base** — `./scripts/gw :core:audio:testDebugUnitTest` e anotar o número de testes no ledger.
- [ ] **Step 2: Converter** (sem mudar comportamento no Android).
- [ ] **Step 3: Rodar** — `:core:audio:testAndroidHostTest :core:audio:compileKotlinDesktop :app:assembleDebug :feature:gameplay:testDebugUnitTest` → mesmo número de testes, PASS.
- [ ] **Step 4: Commit** — `build: core:audio multiplataforma`

### Task 2: Decodificar com FFmpeg no PC

**Files:**
- Modify: `gradle/libs.versions.toml` (`ffmpeg = "8.1.2-1.5.14"`, `javacpp = "1.5.14"`); `core/audio/build.gradle.kts` (`desktopMain`: `ffmpeg` e `javacpp` com o classificador do sistema atual: `windows-x86_64`, `linux-x86_64` ou `macosx-*`, calculado de `os.name`/`os.arch`).
- Create: `core/audio/src/desktopMain/kotlin/.../FfmpegPcmSource.kt` — `class FfmpegPcmSource : PcmSource` com `companion fun open(uri: String, randomRead: RandomReadOpener): FfmpegPcmSource`.
  - `uri` é um caminho do PC (`pcUri`) ou uma entrada de `.sng` (`SngRef.entryOf(uri)`): a entrada é desmascarada para um arquivo temporário (`Files.createTempFile("riff-", ".<extensão>")`) apagado no `close()`.
  - Saída sempre `sampleRate = 48_000`, `channelCount = 2`, float intercalado (swresample); `durationMs` do contêiner; `seekTo(frame)` com `av_seek_frame` + descarte até o quadro exato.
  - Erros do FFmpeg (abrir, achar o fluxo de áudio, decodificar) viram `IOException` com a mensagem do FFmpeg.
- Create: `core/audio/src/desktopTest/resources/audio/` — tons de 1 s gerados uma vez com o `ffmpeg` do WSL e versionados (poucos KB): `tom_440_44k_mono.ogg`, `tom_440_48k_estereo.opus`, `tom_440_44k_estereo.mp3`, `tom_440_48k_estereo.wav`, `corrompido.ogg` (cabeçalho ogg + lixo).
- Test: `core/audio/src/desktopTest/kotlin/.../FfmpegPcmSourceTest.kt`

**Interfaces:**
- Consumes: `RandomReadOpener`, `SngRef` (`:core:chart`), `FileRandomReadOpener`/`pcUri` não (o `:core:audio` não depende do `:core:data`; o abridor vem por parâmetro).
- Produces: `FfmpegPcmSource.open(uri, randomRead)`; `object DesktopAudioFormat { const val SAMPLE_RATE = 48_000; const val CHANNELS = 2 }`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun everyFormatComesOutAt48kStereo()          // para os 4 tons: sampleRate 48000, channelCount 2, ~48000 quadros (±2%)
@Test fun theToneIsStill440Hz()                      // cruzamentos por zero do canal esquerdo do .ogg mono: 440 ± 5 Hz
@Test fun anEntryInsideASngDecodesAndLeavesNoTempFile() // .sng feito com SngWriter (opus dentro): decodifica; depois de close, o arquivo temporário não existe
@Test fun seekingStartsFromThatFrame()               // seekTo(24_000) no .wav: sobram ~24_000 quadros (±1%)
@Test fun aCorruptFileIsAnIOException()              // corrompido.ogg → IOException; formato inexistente ("x.xyz") → IOException
```

- [ ] **Step 2: Rodar** — `./scripts/gw :core:audio:desktopTest` → FAIL (classe inexistente).
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar** → PASS. Medir e anotar no ledger o tempo de decodificar 1 minuto (gerar um tom de 60 s no teste).
- [ ] **Step 5: Commit** — `feat: no PC, o áudio dos mapas é decodificado com FFmpeg`

### Task 3: Saída de som do PC e a música tocando

**Files:**
- Create: `core/audio/src/desktopMain/kotlin/.../JavaSoundSink.kt` — `class JavaSoundSink(sampleRate: Int, openLine: (AudioFormat) -> SourceDataLine = ::defaultLine) : AudioSink`: 16 bits estéreo little-endian, buffer de 2.048 quadros (≈ 43 ms a 48 kHz); `playedFrames` = `line.longFramePosition`; `isPresenting` verdadeiro quando a posição passou de 0 depois do `start`; `LineUnavailableException`/sem dispositivo → `IOException("sem saída de som: …")`.
- Create: `fun floatToPcm16(buffer: FloatArray, offset: Int, samples: Int, out: ByteArray)` (com corte em ±1).
- Create: `core/audio/src/desktopMain/kotlin/.../DesktopPlayers.kt` — `object DesktopPlayers { fun songPlayers(randomRead): SongPlayerFactory; fun stemPlayers(randomRead): StemSongPlayerFactory; val interruptions: PlaybackInterruptionsFactory }`: a música de arquivo único é um `StemSongPlayer(uri, emptyList(), …)` **embrulhado** em `class SingleSongPlayer(stems: StemSongPlayer) : SongPlayer by stems`, que não expõe `GuitarTrackControl` (o `GameSession` corta a guitarra de qualquer player que seja `GuitarTrackControl`: sem o embrulho, um erro silenciaria a música inteira); interrupções no PC = nenhuma.
- Test: `JavaSoundSinkTest.kt` (com um `SourceDataLine` falso mínimo), `DesktopStemPlaybackTest.kt` (FFmpeg + `StemSongPlayer` + saída falsa que só conta quadros).

**Interfaces:**
- Consumes: Task 2.
- Produces: `JavaSoundSink`, `floatToPcm16`, `DesktopPlayers`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun pcm16ClipsAndConverts()                // 1.5f → 32767, -1.5f → -32768, 0.5f → 16383/16384
@Test fun noOutputDeviceIsAnIOException()        // openLine lança LineUnavailableException → IOException na criação
@Test fun separatedStemsPlayTogetherAndTheGuitarCuts() // guitarra 44k mono + resto 48k estéreo (os tons): prepare ok; com a saída falsa, setGuitarAudible(false) zera a contribuição da guitarra (RMS do bloco cai)
@Test fun aSingleSongCannotBeSilencedByAMiss()   // songPlayers().create() não é GuitarTrackControl
@Test fun thePositionNeverGoesBack()             // tocar, pausar, continuar, seekTo(500): positionMs não diminui fora do seek e fica em 500 ± 25 logo depois do seek
```

- [ ] **Step 2: Rodar** → FAIL. **Step 3: Implementar.** **Step 4: Rodar** → PASS.
- [ ] **Step 5: Commit** — `feat: no PC, a música toca pela saída de som do Java`

### Task 4: Sons de erro e do menu no PC

**Files:**
- Modify: `core/audio/build.gradle.kts` — `desktopMain` usa `src/androidMain/res/raw` como pasta de recursos (os mesmos WAV).
- Create: `core/audio/src/desktopMain/kotlin/.../DesktopSfx.kt` — `class ClipSfxPlayer : SfxPlayer` (cada som carregado uma vez como `Clip`; sorteio com `MissSoundPicker`; volume por `FloatControl.Type.MASTER_GAIN`) e `class ClipMenuSounds : MenuSoundPlayer` (com `MenuSoundPicker`). Sem dispositivo: tocam em silêncio (não lançam).
- Modify: `desktop/.../DesktopGraph.kt` (menu e sfx) e `RiffDesktopApp.kt` — `CompositionLocalProvider(LocalMenuSound provides …)` com o volume geral e o interruptor "Sons do menu" das preferências, como o `:app` faz.
- Test: `core/audio/src/desktopTest/.../DesktopSfxTest.kt`; `desktop/src/test/.../DesktopMenuSoundTest.kt`

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun everySoundOfTheAppLoadsOnThePc()       // os 16 WAV de res/raw abrem como AudioInputStream pelo classpath do PC
@Test fun withoutAnOutputTheSoundsAreSilentNotCrashes() // ClipSfxPlayer/ClipMenuSounds com abridor de Clip que lança: hit/miss/play não lançam
@Test fun clickingSoloPlaysTheNavigateSound()    // desktop: LocalMenuSound do app recebe MenuSound.NAVIGATE ao clicar em Sozinho (com o interruptor ligado) e nada com ele desligado
```

- [ ] **Step 2: Rodar** → FAIL. **Step 3: Implementar.** **Step 4: Rodar** → PASS.
- [ ] **Step 5: Commit** — `feat: sons do menu e de erro no PC`

### Task 5: Medir latência e sincronia no PC do usuário

**Files:**
- Create: `desktop/src/main/kotlin/.../AudioCheck.kt` — `fun main(args)` (`./scripts/gw :desktop:runAudioCheck --args="<pasta do mapa>"`, tarefa `JavaExec` no `desktop/build.gradle.kts`): abre o mapa (faixas separadas se houver, senão a música), toca 20 s alternando a guitarra a cada 2 s, e a cada segundo imprime `relógio da parede − positionMs` (deriva), o tamanho do buffer da saída em ms e o tempo até `isPresenting`.
- Modify: `README.md` (seção "Riff no PC": a medição e como rodar).

- [ ] **Step 1:** Rodar no Windows com a pasta "Smells Like Teen Spirit" de `C:\Users\Rodrigo Machado\Music\RiffMapas` (faixas separadas) e com um `.sng`.
- [ ] **Step 2:** Pedir ao usuário que escute (guitarra cortando e voltando a cada 2 s, sem estalos) e anotar no README: latência da saída, tempo até o som sair, deriva máxima em 20 s.
- [ ] **Step 3:** Se a deriva passar de 20 ms em 20 s ou houver estalos, ajustar o buffer da saída (registrar `Ruling:` com os números) antes de seguir.
- [ ] **Step 4: Commit** — `docs: latência e sincronia do áudio no PC`

### Task 6: CI e fechamento

- [ ] **Step 1:** Suíte completa (Global Constraints) → BUILD SUCCESSFUL.
- [ ] **Step 2:** Linux (`~/riff-linux`, comando do CI) → verde (FFmpeg `linux-x86_64` nos testes; o CI não tem placa de som: os testes da saída usam a linha falsa).
- [ ] **Step 3:** `docs/pendencias.md` se algo ficar; commit.
