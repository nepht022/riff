# Separação da guitarra com IA no celular — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** músicas da Biblioteca sem guitarra separada podem ter a guitarra separada pelo próprio celular (htdemucs_6s via ONNX Runtime na CPU), em segundo plano, e passam a jogar com a guitarra cortando no erro.

**Architecture:** módulo novo `:core:separation`. Ele traz:
- o modelo ONNX e o `OrtDemucsModel`;
- o port em Kotlin da STFT e da montagem por trechos do demucs.onnx (`DemucsStft`, `GuitarSeparator`, em fluxo, sem guardar a música inteira na heap);
- a decodificação para um arquivo temporário mapeado (`SongPcmLoader`), a gravação em `.m4a` e o `GuitarSeparationRunner`, que orquestra tudo dentro de um `Worker` do WorkManager.

`:core:data` guarda o estado (Room v5 + arquivos em `filesDir/separacoes`) e o `ChartTrackLoader` devolve `ChartAudio.Stems` com os arquivos da IA; o jogo não muda. As features só conhecem as interfaces de `:core:data` (`AiSeparationRepository`, `SeparationScheduler`) e o ícone vem de `:core:ui` por um `CompositionLocal`, como os sons do menu.

**Tech Stack:** Kotlin, ONNX Runtime 1.31.0 (`onnxruntime-android`; `onnxruntime` desktop só nos testes da JVM), JTransforms 3.2, WorkManager 2.12.0 + `androidx.hilt:hilt-work` 1.4.0, Room 2.8.5, Compose, Robolectric, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-13-separacao-guitarra-ia-design.md`

## Global Constraints

- Tudo roda no celular: sem servidor, sem PC, sem internet. Só no modo Biblioteca; o modo Spotify não muda.
- Modelo `htdemucs_6s` com **trechos de 4 s** (176400 amostras a 44,1 kHz; espectro `[1, 4, 2048, 173]`), sobreposição de 25 %, peso em triângulo, STFT nfft 4096 / hop 1024; dentro do app em `core/separation/src/main/assets/htdemucs_6s_seg4.onnx` (`noCompress`), no git.
- ONNX Runtime na CPU, 4 threads; arena da CPU ligada com 8 GB de RAM ou mais, desligada abaixo.
- Saída: **guitarra** (fonte 4) e **resto** (soma das outras 5), `.m4a` AAC, em `filesDir/separacoes/<sha1(chartId)>/guitar.m4a` e `backing.m4a`; gravação em `<…>.tmp/` e troca no fim.
- Room: tabela `ai_separations(chartId PK, state SEPARATED|DECLINED, bytes, createdAtEpochMs)`, versão 5, AutoMigration 4→5, apaga em cascata com o mapa.
- Textos (pt-BR):
  - pergunta: "Separar a guitarra com IA?" / "A guitarra some quando você erra, como no Teen Spirit. Leva uns 2 minutos, usa bastante o processador e pode esquentar o celular. Fica guardado: só precisa fazer uma vez por música." / "Separar" / "Jogar sem separar" / "Pode não funcionar neste celular.";
  - painel: "Separando a guitarra… %1$d%%", "cerca de %1$d min", "Pausado para esfriar…", "Cancelar";
  - erros: "Não deu para separar a guitarra de %1$s", "Pouco espaço no celular";
  - "Já tem uma separação em andamento";
  - marca "Guitarra separada IA";
  - Configurações: "Guitarra separada por IA", "%1$d músicas · %2$s", "Apagar separações", "Perguntar de novo";
  - notificação: "Guitarra separada: %1$s".
- Uma separação por vez; pausa entre trechos com `THERMAL_STATUS_SEVERE` ou mais, volta com `MODERATE` ou menos (API 29+); menos de 50 MB livres não começa; celular com menos de 4 GB de RAM: a pergunta avisa.
- Créditos: Demucs (MIT), demucs.onnx (MIT), ONNX Runtime (MIT), JTransforms (BSD-2).
- Fluxo do projeto: TDD, detekt (LongMethod 60, TooManyFunctions 11, ComplexCondition 3, CyclomaticComplex 14), suíte completa `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --console=plain`, `scripts/conferir_r8_spotify.sh`.

## Review Focus

1. Música em silêncio (ou quase): o desvio é 0 e a normalização não pode virar NaN; a separação termina com guitarra e resto em silêncio — teste no Task 3.
2. Música mais curta que um trecho (2 s): um trecho só, sem estourar índices — teste no Task 3.
3. Opus a 48 kHz (o caso comum do Encore) e áudio mono: reamostrado para 44,1 kHz e estéreo — teste no Task 4.
4. O mapa sai da biblioteca durante a separação: guardar falha (chave estrangeira) e vira "não deu para separar", sem deixar pasta temporária — teste no Task 6.
5. Cancelar com a separação pausada pelo calor: para na hora, limpa e volta a `Idle` — teste no Task 6.

---

### Task 1: Modelo, `OrtDemucsModel` e referências do Python

**Files:**
- Create: `core/separation/build.gradle.kts`, `core/separation/src/main/AndroidManifest.xml`, `core/separation/consumer-rules.pro`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/Demucs.kt`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/OrtDemucsModel.kt`
- Create: `core/separation/src/main/assets/htdemucs_6s_seg4.onnx` (gerado)
- Create: `scripts/exportar_modelo_ia.sh`, `scripts/exportar_modelo_ia.py`, `scripts/separacao_referencia.py`
- Create: `core/separation/src/test/resources/separacao/{stft_in,stft_spec,stft_inverse,mix,guitar,backing}.f32` (gerados)
- Create: `core/separation/src/test/resources/robolectric.properties`
- Create: `core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/Fixtures.kt`
- Test: `core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/OrtDemucsModelTest.kt`
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`

**Interfaces:**
- Produces:
  - `object Demucs { SAMPLE_RATE=44100; SEGMENT=176400; NFFT=4096; HOP=1024; BINS=2048; FRAMES=173; SOURCES=6; GUITAR=4; MODEL_ASSET="htdemucs_6s_seg4.onnx" }`;
  - `interface DemucsModel : Closeable { fun run(mix: FloatArray, spec: FloatArray): DemucsOutput }`;
  - `class DemucsOutput(val spec: FloatArray, val mix: FloatArray)`;
  - `class OrtDemucsModel(model: ByteBuffer, threads: Int = 4, useArena: Boolean = true) : DemucsModel`;
  - `fun interface DemucsModelFactory { fun create(useArena: Boolean): DemucsModel }`;
  - fixtures em `separacao/*.f32` (float32 little-endian, planar: canal 0 inteiro, depois canal 1);
  - `Fixtures.read(name): FloatArray` e `Fixtures.modelBuffer(): ByteBuffer` nos testes.

- [ ] **Step 1: Versões e módulo**

`gradle/libs.versions.toml`, em `[versions]`:
```toml
onnxruntime = "1.31.0"
jtransforms = "3.2"
work = "2.12.0"
```
Em `[libraries]`:
```toml
onnxruntime-android = { module = "com.microsoft.onnxruntime:onnxruntime-android", version.ref = "onnxruntime" }
onnxruntime-jvm = { module = "com.microsoft.onnxruntime:onnxruntime", version.ref = "onnxruntime" }
jtransforms = { module = "com.github.wendykierp:JTransforms", version.ref = "jtransforms" }
androidx-work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { module = "androidx.work:work-testing", version.ref = "work" }
androidx-hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "androidxHilt" }
androidx-hilt-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "androidxHilt" }
```
`settings.gradle.kts`: acrescentar `include(":core:separation")` junto dos outros `:core:*`.

`core/separation/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff.core.separation"
    // O modelo é lido direto do APK (mapeado em memória): não pode estar comprimido.
    androidResources { noCompress += "onnx" }
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// O onnxruntime-android não traz bibliotecas nativas para o PC: os testes da JVM usam o de desktop.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:audio"))
    implementation(project(":core:data"))
    implementation(libs.onnxruntime.android)
    implementation(libs.jtransforms)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.onnxruntime.jvm)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
```
`core/separation/consumer-rules.pro`:
```
# O ONNX Runtime chama as classes Java pelo JNI: o R8 não pode renomeá-las nem tirá-las.
-keep class ai.onnxruntime.** { *; }
```
`core/separation/src/main/AndroidManifest.xml`: `<manifest />`.
`core/separation/src/test/resources/robolectric.properties`: `sdk=35`.

- [ ] **Step 2: Exportar o modelo**

`scripts/exportar_modelo_ia.py`:
```python
"""Exporta o htdemucs_6s (Demucs v4, MIT) para ONNX com trechos de 4 s e a STFT fora do modelo.

Usa o Demucs adaptado do demucs.onnx (MIT, https://github.com/sevagh/demucs.onnx), que tira a STFT/iSTFT
de dentro da rede. Entradas: mix [1, 2, 176400] e spec [1, 4, 2048, 173]; saídas: spec_out [1, 6, 4, 2048, 173]
e mix_out [1, 6, 2, 176400].
"""
import sys
from fractions import Fraction

import torch
from demucs.htdemucs import standalone_magnitude, standalone_spec
from demucs.pretrained import get_model

model = get_model("htdemucs_6s").models[0].eval()
model.segment = Fraction(4)
wave = torch.randn(1, 2, 4 * 44100)
spec = standalone_magnitude(standalone_spec(wave))
torch.onnx.export(
    model, (wave, spec), sys.argv[1], export_params=True, opset_version=17, do_constant_folding=True,
    input_names=["mix", "spec"], output_names=["spec_out", "mix_out"],
)
```
`scripts/exportar_modelo_ia.sh`:
```bash
#!/usr/bin/env bash
# Gera core/separation/src/main/assets/htdemucs_6s_seg4.onnx e as referências dos testes (separacao_referencia.py).
# Precisa de python3 com torch 2.5, onnx, onnxscript, onnxslim, numpy e soundfile; e git.
set -euo pipefail
cd "$(dirname "$0")/.."
WORK="${WORK:-$(mktemp -d)}"
[ -d "$WORK/demucs.onnx" ] || git clone -q https://github.com/sevagh/demucs.onnx "$WORK/demucs.onnx"
git -C "$WORK/demucs.onnx" checkout -q 81fa192e6fcc88e35e887f6e6ccce91227f4e6f5
# O diffq só serve para modelos quantizados (o htdemucs_6s não é): sem ele a importação não quebra.
sed -i 's/^from diffq import DiffQuantizer, UniformQuantizer, restore_quantized_state$/DiffQuantizer = UniformQuantizer = restore_quantized_state = None/' \
  "$WORK/demucs.onnx/demucs-for-onnx/demucs/states.py"
export PYTHONPATH="$WORK/demucs.onnx/demucs-for-onnx"
python3 scripts/exportar_modelo_ia.py "$WORK/htdemucs_6s_seg4.onnx"
mkdir -p core/separation/src/main/assets
onnxslim "$WORK/htdemucs_6s_seg4.onnx" core/separation/src/main/assets/htdemucs_6s_seg4.onnx
python3 scripts/separacao_referencia.py core/separation/src/test/resources/separacao
```

- [ ] **Step 3: Referências do Python**

`scripts/separacao_referencia.py`:
```python
"""Referências dos testes de :core:separation, geradas pelo Demucs original (PyTorch), não pelo port.

stft_*: a STFT do Demucs num sinal aleatório de 5000 amostras; mix/guitar/backing: 6 s sintéticos separados
pelo apply_model do Demucs com trechos de 4 s e deslocamento 0 (o mesmo do port). Arquivos float32 little-endian,
planares (canal 0 inteiro, depois o 1); o espectro em planos [canal][real|imag][faixa][quadro].
"""
import sys
from fractions import Fraction
from pathlib import Path

import numpy as np
import torch
import demucs.apply as apply
from demucs.htdemucs import standalone_ispec, standalone_spec
from demucs.pretrained import get_model

out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
save = lambda name, a: np.ascontiguousarray(a, dtype="<f4").tofile(out / f"{name}.f32")

rng = np.random.default_rng(7)
x = torch.from_numpy(rng.uniform(-0.5, 0.5, (1, 2, 5000)).astype(np.float32))
z = standalone_spec(x)[0]                                   # [2, 2048, 5] complexo
save("stft_in", x[0].numpy())
save("stft_spec", torch.stack([z.real, z.imag], dim=1).numpy())  # [2, 2, 2048, 5]
save("stft_inverse", standalone_ispec(z[None], length=5000)[0].numpy())

sr, seconds = 44100, 6
t = np.arange(sr * seconds) / sr
saw = lambda f: 2 * ((t * f) % 1) - 1
guitar = 0.25 * (saw(110) + saw(165) + saw(220)) * (0.6 + 0.4 * np.sin(2 * np.pi * 2 * t))
bass = 0.3 * np.sin(2 * np.pi * 55 * t)
kick = 0.5 * np.exp(-30 * ((t % 0.5))) * np.sin(2 * np.pi * 60 * t)
noise = 0.05 * rng.standard_normal(t.size)
mix = np.stack([guitar + bass + kick + noise, 0.9 * guitar + bass + kick - noise]).astype(np.float32)
save("mix", mix)

model = get_model("htdemucs_6s").models[0].eval()
model.segment = Fraction(4)
apply.random.randint = lambda a, b: 0                     # deslocamento 0, como no port
wav = torch.from_numpy(mix)
ref = wav.mean(0)
norm = (wav - ref.mean()) / ref.std()
with torch.no_grad():
    sources = apply.apply_model(model, norm[None], shifts=1, split=True, overlap=0.25)[0]
sources = sources * ref.std() + ref.mean()
save("guitar", sources[4].numpy())
save("backing", (sources.sum(0) - sources[4]).numpy())
```
Rodar (precisa da venv com torch; na máquina de desenvolvimento ela está no scratchpad):
```bash
WORK=/tmp/claude-1000/riff-modelo PATH="<venv>/bin:$PATH" ./scripts/exportar_modelo_ia.sh
ls -la core/separation/src/main/assets core/separation/src/test/resources/separacao
```
Expected: `htdemucs_6s_seg4.onnx` com ~130 MB e os seis `.f32` (`mix`, `guitar` e `backing` com 2116800 bytes cada).

- [ ] **Step 4: Escrever o teste que falha**

`core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/Fixtures.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** As referências geradas por `scripts/separacao_referencia.py` e o modelo de verdade. */
object Fixtures {
    fun read(name: String): FloatArray {
        val bytes = checkNotNull(javaClass.getResource("/separacao/$name.f32")).readBytes()
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also(floats::get)
    }

    fun modelBuffer(): ByteBuffer = RandomAccessFile(File("src/main/assets/${Demucs.MODEL_ASSET}"), "r").use {
        it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length())
    }
}
```
`OrtDemucsModelTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrtDemucsModelTest {
    @Test
    fun oneSegmentGivesSixSourcesInBothBranches() {
        OrtDemucsModel(Fixtures.modelBuffer(), threads = 2).use { model ->
            val out = model.run(
                FloatArray(2 * Demucs.SEGMENT) { (it % 100) / 100f - 0.5f },
                FloatArray(4 * Demucs.BINS * Demucs.FRAMES),
            )

            assertEquals(Demucs.SOURCES * 4 * Demucs.BINS * Demucs.FRAMES, out.spec.size)
            assertEquals(Demucs.SOURCES * 2 * Demucs.SEGMENT, out.mix.size)
            assertTrue(out.mix.all { it.isFinite() })
        }
    }
}
```

- [ ] **Step 5: Rodar e ver falhar**

Run: `./scripts/gw :core:separation:testDebugUnitTest --console=plain`
Expected: FAIL na compilação ("Unresolved reference: OrtDemucsModel").

- [ ] **Step 6: Implementar**

`Demucs.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import java.io.Closeable

/** O htdemucs_6s exportado com trechos de 4 s (ver `scripts/exportar_modelo_ia.py`). */
object Demucs {
    const val SAMPLE_RATE = 44_100
    const val SEGMENT = 176_400
    const val NFFT = 4096
    const val HOP = 1024
    const val BINS = 2048
    const val FRAMES = 173
    const val SOURCES = 6

    /** Ordem do htdemucs_6s: bateria, baixo, outros, voz, guitarra, piano. */
    const val GUITAR = 4
    const val MODEL_ASSET = "htdemucs_6s_seg4.onnx"
}

/** Saídas de um trecho: espectro `[6, 4, 2048, 173]` (CaC) e onda `[6, 2, 176400]`, achatados. */
class DemucsOutput(val spec: FloatArray, val mix: FloatArray)

/** Uma passada do modelo num trecho: onda `[2, 176400]` e espectro `[4, 2048, 173]` (CaC), achatados. */
interface DemucsModel : Closeable {
    fun run(mix: FloatArray, spec: FloatArray): DemucsOutput
}

fun interface DemucsModelFactory {
    fun create(useArena: Boolean): DemucsModel
}
```
`OrtDemucsModel.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/** O modelo no ONNX Runtime (CPU). [model] precisa ser um buffer direto (o asset mapeado em memória). */
class OrtDemucsModel(model: ByteBuffer, threads: Int = 4, useArena: Boolean = true) : DemucsModel {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(threads)
        options.setInterOpNumThreads(1)
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        // Sem a arena o pico de memória cai (~2,8 → ~1,8 GB no S25) e a separação fica ~40 % mais lenta.
        options.setCPUArenaAllocator(useArena)
        options.setMemoryPatternOptimization(useArena)
        env.createSession(model, options)
    }

    override fun run(mix: FloatArray, spec: FloatArray): DemucsOutput =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(mix), MIX_SHAPE).use { mixTensor ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(spec), SPEC_SHAPE).use { specTensor ->
                session.run(mapOf("mix" to mixTensor, "spec" to specTensor)).use { result ->
                    DemucsOutput(floats(result, "spec_out"), floats(result, "mix_out"))
                }
            }
        }

    private fun floats(result: OrtSession.Result, name: String): FloatArray {
        val buffer = (result.get(name).get() as OnnxTensor).floatBuffer
        return FloatArray(buffer.remaining()).also(buffer::get)
    }

    override fun close() = session.close()

    private companion object {
        val MIX_SHAPE = longArrayOf(1, 2, Demucs.SEGMENT.toLong())
        val SPEC_SHAPE = longArrayOf(1, 4, Demucs.BINS.toLong(), Demucs.FRAMES.toLong())
    }
}
```

- [ ] **Step 7: Rodar e ver passar**

Run: `./scripts/gw :core:separation:testDebugUnitTest detekt --console=plain`
Expected: PASS (1 teste) e detekt limpo.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts core/separation scripts/exportar_modelo_ia.sh scripts/exportar_modelo_ia.py scripts/separacao_referencia.py
git commit -m "feat: modelo htdemucs_6s (4 s) em ONNX e o OrtDemucsModel"
```

---

### Task 2: `DemucsStft`

**Files:**
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/DemucsStft.kt`
- Test: `core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/DemucsStftTest.kt`

**Interfaces:**
- Consumes: `Demucs`, `Fixtures.read`.
- Produces:
  - `class DemucsStft(length: Int)` com `val frames: Int`;
  - `fun forward(signal: FloatArray, out: FloatArray, offset: Int)`: grava o plano real em `out[offset + f*frames + t]` e o imaginário em `out[offset + BINS*frames + f*frames + t]`;
  - `fun inverse(spec: FloatArray, offset: Int, out: FloatArray)`: lê o mesmo arranjo e grava `length` amostras.

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.rodrigomachado.riff.core.separation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DemucsStftTest {
    private val input = Fixtures.read("stft_in") // [2][5000]
    private val stft = DemucsStft(5000)

    private fun channel(c: Int) = input.copyOfRange(c * 5000, (c + 1) * 5000)

    @Test
    fun theSpectrumMatchesTheDemucsOne() {
        val expected = Fixtures.read("stft_spec") // [2 canais][real|imag][2048][5]
        val plane = Demucs.BINS * stft.frames
        val out = FloatArray(2 * 2 * plane)
        stft.forward(channel(0), out, offset = 0)
        stft.forward(channel(1), out, offset = 2 * plane)

        assertEquals(5, stft.frames)
        assertArrayEquals(expected, out, 1e-4f)
    }

    @Test
    fun theInverseMatchesTheDemucsOne() {
        val expected = Fixtures.read("stft_inverse")
        val spec = FloatArray(2 * Demucs.BINS * stft.frames)
        val back = FloatArray(5000)
        stft.forward(channel(1), spec, offset = 0)
        stft.inverse(spec, offset = 0, out = back)

        assertArrayEquals(expected.copyOfRange(5000, 10_000), back, 1e-4f)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:separation:testDebugUnitTest --tests '*DemucsStftTest*' --console=plain`
Expected: FAIL ("Unresolved reference: DemucsStft").

- [ ] **Step 3: Implementar**

```kotlin
package com.rodrigomachado.riff.core.separation

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A STFT do Demucs (`standalone_spec` / `standalone_ispec` do demucs.onnx, que chamam `torch.stft`): Hann periódica
 * de 4096, passo 1024, `normalized=True`, `center=True` com reflexão, mais o recheio de 1536 amostras que alinha os
 * quadros com o ramo do tempo. Guarda 2048 faixas (a última sai) e [frames] quadros (os 2 de cada ponta saem).
 */
class DemucsStft(private val length: Int) {
    val frames = (length + Demucs.HOP - 1) / Demucs.HOP
    private val padded = frames * Demucs.HOP + 2 * EDGE
    private val window = FloatArray(Demucs.NFFT) { (0.5 - 0.5 * cos(2 * PI * it / Demucs.NFFT)).toFloat() }
    private val fft = FloatFFT_1D(Demucs.NFFT.toLong())
    private val frame = FloatArray(Demucs.NFFT)
    private val plane = Demucs.BINS * frames

    fun forward(signal: FloatArray, out: FloatArray, offset: Int) {
        val x = reflect(reflect(signal, length, EDGE, padded - length - EDGE), padded, CENTER, CENTER)
        for (t in 0 until frames) {
            val start = (t + 2) * Demucs.HOP
            for (i in 0 until Demucs.NFFT) frame[i] = x[start + i] * window[i]
            fft.realForward(frame)
            out[offset + t] = frame[0] * SCALE
            out[offset + plane + t] = 0f
            for (f in 1 until Demucs.BINS) {
                out[offset + f * frames + t] = frame[2 * f] * SCALE
                out[offset + plane + f * frames + t] = frame[2 * f + 1] * SCALE
            }
        }
    }

    fun inverse(spec: FloatArray, offset: Int, out: FloatArray) {
        val total = Demucs.NFFT + Demucs.HOP * (frames + 3)
        val y = FloatArray(total)
        val envelope = FloatArray(total)
        for (t in 0 until frames + 4) {
            val start = t * Demucs.HOP
            if (t in 2 until frames + 2) addFrame(spec, offset, t - 2, y, start)
            for (i in 0 until Demucs.NFFT) envelope[start + i] += window[i] * window[i]
        }
        for (k in 0 until length) {
            val p = CENTER + EDGE + k
            out[k] = if (envelope[p] > MIN_ENVELOPE) y[p] / envelope[p] else y[p]
        }
    }

    private fun addFrame(spec: FloatArray, offset: Int, column: Int, y: FloatArray, start: Int) {
        frame[0] = spec[offset + column] * UNSCALE
        frame[1] = 0f // a faixa de Nyquist (a 2049ª) é zero
        for (f in 1 until Demucs.BINS) {
            frame[2 * f] = spec[offset + f * frames + column] * UNSCALE
            frame[2 * f + 1] = spec[offset + plane + f * frames + column] * UNSCALE
        }
        fft.realInverse(frame, true)
        for (i in 0 until Demucs.NFFT) y[start + i] += frame[i] * window[i]
    }

    private companion object {
        const val EDGE = Demucs.HOP / 2 * 3
        const val CENTER = Demucs.NFFT / 2
        const val MIN_ENVELOPE = 1e-11f
        val SCALE = 1f / sqrt(Demucs.NFFT.toFloat())
        val UNSCALE = sqrt(Demucs.NFFT.toFloat())

        /** `F.pad(mode="reflect")`: espelha sem repetir a amostra da borda. */
        fun reflect(x: FloatArray, n: Int, left: Int, right: Int) = FloatArray(n + left + right) { i ->
            var j = i - left
            if (j < 0) j = -j
            if (j >= n) j = 2 * (n - 1) - j
            x[j]
        }
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :core:separation:testDebugUnitTest --tests '*DemucsStftTest*' detekt --console=plain`
Expected: PASS (2 testes). Se o imaginário sair com o sinal trocado, o JTransforms está usando a convenção oposta: inverter o sinal das partes imaginárias em `forward` e `addFrame` e anotar o Ruling.

- [ ] **Step 5: Commit**

```bash
git add core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/DemucsStft.kt core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/DemucsStftTest.kt
git commit -m "feat: STFT do Demucs em Kotlin"
```

---

### Task 3: `GuitarSeparator` (trechos, em fluxo)

**Files:**
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/GuitarSeparator.kt`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/StemAccumulator.kt`
- Test: `core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/GuitarSeparatorTest.kt`
- Test: `core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/GuitarSeparatorGoldenTest.kt`

**Interfaces:**
- Consumes: `DemucsModel`, `DemucsOutput`, `DemucsStft`, `Demucs`.
- Produces:
  - `interface SongPcm { val frames: Int; val mean: Float; val std: Float; fun sample(channel: Int, frame: Int): Float }`;
  - `interface StemSink { fun write(guitar: FloatArray, backing: FloatArray, frames: Int) }` (estéreo intercalado, em ordem);
  - `class GuitarSeparator(model: DemucsModel)` com `suspend fun separate(song: SongPcm, sink: StemSink, onChunk: (done: Int, total: Int) -> Unit = { _, _ -> }, checkpoint: suspend () -> Unit = {})`;
  - `fun chunkCount(frames: Int): Int`.

- [ ] **Step 1: Escrever os testes que falham**

`GuitarSeparatorTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/** Música em memória, como o SongPcmLoader entrega. */
class ArraySong(private val left: FloatArray, private val right: FloatArray) : SongPcm {
    override val frames = left.size
    private val mono = FloatArray(frames) { (left[it] + right[it]) / 2 }
    override val mean = mono.average().toFloat()
    override val std = kotlin.math.sqrt(mono.sumOf { ((it - mean) * (it - mean)).toDouble() } / (frames - 1)).toFloat()
    override fun sample(channel: Int, frame: Int) = if (channel == 0) left[frame] else right[frame]
}

class CollectingSink : StemSink {
    val guitar = ArrayList<Float>()
    val backing = ArrayList<Float>()
    override fun write(guitar: FloatArray, backing: FloatArray, frames: Int) {
        for (i in 0 until 2 * frames) {
            this.guitar += guitar[i]
            this.backing += backing[i]
        }
    }
    fun channel(values: List<Float>, c: Int) = FloatArray(values.size / 2) { values[2 * it + c] }
}

/** Devolve a entrada como guitarra pelo ramo do tempo ([viaSpectrum] = pelo espectro); as outras fontes zeradas. */
class EchoModel(private val viaSpectrum: Boolean = false) : DemucsModel {
    var calls = 0
    override fun run(mix: FloatArray, spec: FloatArray): DemucsOutput {
        calls++
        val outSpec = FloatArray(Demucs.SOURCES * spec.size)
        val outMix = FloatArray(Demucs.SOURCES * mix.size)
        if (viaSpectrum) {
            spec.copyInto(outSpec, Demucs.GUITAR * spec.size)
        } else {
            mix.copyInto(outMix, Demucs.GUITAR * mix.size)
        }
        return DemucsOutput(outSpec, outMix)
    }
    override fun close() = Unit
}

class GuitarSeparatorTest {
    private fun tone(frames: Int, f: Double, phase: Double = 0.0) =
        FloatArray(frames) { (0.3 * sin(2 * Math.PI * f * it / Demucs.SAMPLE_RATE + phase) + 0.01).toFloat() }

    @Test
    fun aGuitarOnlyModelGivesTheSongBackAcrossSegments() = runTest {
        val left = tone(400_000, 220.0)
        val right = tone(400_000, 330.0, 1.0)
        val sink = CollectingSink()

        GuitarSeparator(EchoModel()).separate(ArraySong(left, right), sink)

        assertArrayEquals(left, sink.channel(sink.guitar, 0), 1e-4f)
        assertArrayEquals(right, sink.channel(sink.guitar, 1), 1e-4f)
    }

    @Test
    fun theSpectrumBranchAlsoRoundTrips() = runTest {
        val left = tone(200_000, 440.0)
        val sink = CollectingSink()

        GuitarSeparator(EchoModel(viaSpectrum = true)).separate(ArraySong(left, left.copyOf()), sink)

        assertArrayEquals(left, sink.channel(sink.guitar, 0), 1e-3f)
    }

    @Test
    fun theRestGetsTheMeanOfTheOtherFiveSources() = runTest {
        val left = tone(50_000, 220.0)
        val song = ArraySong(left, left.copyOf())
        val sink = CollectingSink()

        GuitarSeparator(EchoModel()).separate(song, sink)

        assertTrue(sink.backing.all { kotlin.math.abs(it - 5 * song.mean) < 1e-4f })
    }

    @Test
    fun aSongShorterThanOneSegmentIsOneChunk() = runTest {
        val left = tone(2 * Demucs.SAMPLE_RATE, 220.0)
        val model = EchoModel()
        val sink = CollectingSink()

        GuitarSeparator(model).separate(ArraySong(left, left.copyOf()), sink)

        assertEquals(1, model.calls)
        assertArrayEquals(left, sink.channel(sink.guitar, 0), 1e-4f)
    }

    @Test
    fun silenceDoesNotTurnIntoNaN() = runTest {
        val sink = CollectingSink()

        GuitarSeparator(EchoModel()).separate(ArraySong(FloatArray(100_000), FloatArray(100_000)), sink)

        assertEquals(200_000, sink.guitar.size)
        assertTrue(sink.guitar.all { it == 0f } && sink.backing.all { it == 0f })
    }

    @Test
    fun reportsEachChunkAndStopsAtTheCheckpoint() = runTest {
        val left = tone(400_000, 220.0)
        val reported = mutableListOf<Pair<Int, Int>>()
        val separator = GuitarSeparator(EchoModel())

        val error = runCatching {
            separator.separate(
                ArraySong(left, left.copyOf()),
                CollectingSink(),
                onChunk = { done, total -> reported += done to total },
                checkpoint = { if (reported.size == 2) throw kotlinx.coroutines.CancellationException("parou") },
            )
        }.exceptionOrNull()

        assertEquals(listOf(1 to 4, 2 to 4), reported)
        assertTrue(error is kotlinx.coroutines.CancellationException)
        assertEquals(4, chunkCount(400_000))
    }
}
```
`GuitarSeparatorGoldenTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** O port inteiro com o modelo de verdade contra o Demucs original (scripts/separacao_referencia.py). */
class GuitarSeparatorGoldenTest {
    @Test
    fun guitarAndRestMatchTheOriginalDemucs() = runTest(timeout = kotlin.time.Duration.parse("5m")) {
        val mix = Fixtures.read("mix")
        val n = mix.size / 2
        val sink = CollectingSink()

        OrtDemucsModel(Fixtures.modelBuffer()).use { model ->
            GuitarSeparator(model).separate(ArraySong(mix.copyOfRange(0, n), mix.copyOfRange(n, 2 * n)), sink)
        }

        for ((name, values) in listOf("guitar" to sink.guitar, "backing" to sink.backing)) {
            val expected = Fixtures.read(name)
            for (c in 0..1) {
                val got = sink.channel(values, c)
                val corr = correlation(got, expected.copyOfRange(c * n, (c + 1) * n))
                assertTrue("$name canal $c: correlação $corr", corr > 0.99)
            }
        }
    }

    private fun correlation(a: FloatArray, b: FloatArray): Double {
        val ma = a.average()
        val mb = b.average()
        var sab = 0.0
        var saa = 0.0
        var sbb = 0.0
        for (i in a.indices) {
            sab += (a[i] - ma) * (b[i] - mb)
            saa += (a[i] - ma) * (a[i] - ma)
            sbb += (b[i] - mb) * (b[i] - mb)
        }
        return sab / sqrt(saa * sbb)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:separation:testDebugUnitTest --tests '*GuitarSeparator*' --console=plain`
Expected: FAIL ("Unresolved reference: GuitarSeparator").

- [ ] **Step 3: Implementar**

`StemAccumulator.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

/**
 * Soma ponderada dos trechos (o `apply_model` do Demucs), em fluxo. A posição `base + i` vive no índice `i`; o que
 * fica antes do próximo trecho já está pronto e sai para o [sink] (dividido pelo peso e desnormalizado). O espaço é o
 * da música deslocada: [Demucs.SAMPLE_RATE]/2 de silêncio antes dela (`max_shift`, com deslocamento 0).
 */
internal class StemAccumulator(private val song: SongPcm, private val sink: StemSink) {
    private val guitar = Array(2) { FloatArray(Demucs.SEGMENT) }
    private val backing = Array(2) { FloatArray(Demucs.SEGMENT) }
    private val weightSum = FloatArray(Demucs.SEGMENT)
    private val outGuitar = FloatArray(2 * Demucs.SEGMENT)
    private val outBacking = FloatArray(2 * Demucs.SEGMENT)
    private var base = 0

    /** Soma o trecho que começa em [offset] (= base), já cortado no centro: [length] amostras de cada onda. */
    fun add(length: Int, chunkGuitar: Array<FloatArray>, chunkBacking: Array<FloatArray>, weight: FloatArray) {
        for (c in 0..1) {
            for (k in 0 until length) {
                guitar[c][k] += weight[k] * chunkGuitar[c][k]
                backing[c][k] += weight[k] * chunkBacking[c][k]
            }
        }
        for (k in 0 until length) weightSum[k] += weight[k]
    }

    /** Entrega as posições de `base` até [until] e anda a janela. */
    fun flush(until: Int) {
        val count = until - base
        var frames = 0
        for (i in 0 until count) {
            val songFrame = base + i - MAX_SHIFT
            if (songFrame !in 0 until song.frames) continue
            for (c in 0..1) {
                outGuitar[2 * frames + c] = guitar[c][i] / weightSum[i] * song.std + song.mean
                outBacking[2 * frames + c] = backing[c][i] / weightSum[i] * song.std + OTHER_SOURCES * song.mean
            }
            frames++
        }
        if (frames > 0) sink.write(outGuitar, outBacking, frames)
        shift(count)
        base = until
    }

    private fun shift(count: Int) {
        for (array in guitar + backing + arrayOf(weightSum)) {
            array.copyInto(array, 0, count, Demucs.SEGMENT)
            array.fill(0f, Demucs.SEGMENT - count, Demucs.SEGMENT)
        }
    }

    companion object {
        const val MAX_SHIFT = Demucs.SAMPLE_RATE / 2
        private const val OTHER_SOURCES = Demucs.SOURCES - 1
    }
}
```
`GuitarSeparator.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import kotlin.math.min

/** O áudio a separar, em 44,1 kHz estéreo, com a média e o desvio do mono (a normalização do Demucs). */
interface SongPcm {
    val frames: Int
    val mean: Float
    val std: Float

    fun sample(channel: Int, frame: Int): Float
}

/** Recebe a guitarra e o resto em blocos, em ordem, estéreo intercalado. */
fun interface StemSink {
    fun write(guitar: FloatArray, backing: FloatArray, frames: Int)
}

private const val STRIDE = Demucs.SEGMENT * 3 / 4
private const val HALF = Demucs.SEGMENT / 2

/** Quantos trechos a separação de uma música de [frames] amostras roda (o progresso conta por eles). */
fun chunkCount(frames: Int): Int = (frames + StemAccumulator.MAX_SHIFT + STRIDE - 1) / STRIDE

/**
 * A separação do demucs.onnx (`apply_model` do Demucs, MIT) com trechos de 4 s: normaliza pelo mono, desloca 0,5 s,
 * roda trechos com 25 % de sobreposição e soma com peso em triângulo. Guarda só a guitarra e o resto (soma das outras
 * cinco fontes). [checkpoint] roda antes de cada trecho: é onde a separação pausa ou é cancelada.
 */
class GuitarSeparator(private val model: DemucsModel) {
    private val stft = DemucsStft(Demucs.SEGMENT)
    private val plane = Demucs.BINS * Demucs.FRAMES
    private val weight = FloatArray(Demucs.SEGMENT) { k -> (if (k < HALF) k + 1 else Demucs.SEGMENT - k).toFloat() / HALF }
    private val mixIn = FloatArray(2 * Demucs.SEGMENT)
    private val specIn = FloatArray(4 * plane)
    private val restSpec = FloatArray(4 * plane)
    private val channel = FloatArray(Demucs.SEGMENT)
    private val wave = FloatArray(Demucs.SEGMENT)
    private val chunkGuitar = Array(2) { FloatArray(Demucs.SEGMENT) }
    private val chunkBacking = Array(2) { FloatArray(Demucs.SEGMENT) }

    suspend fun separate(
        song: SongPcm,
        sink: StemSink,
        onChunk: (done: Int, total: Int) -> Unit = { _, _ -> },
        checkpoint: suspend () -> Unit = {},
    ) {
        val shiftedLength = song.frames + StemAccumulator.MAX_SHIFT
        val total = chunkCount(song.frames)
        val accumulator = StemAccumulator(song, sink)
        for (index in 0 until total) {
            checkpoint()
            val offset = index * STRIDE
            val length = min(Demucs.SEGMENT, shiftedLength - offset)
            runChunk(song, offset, length)
            accumulator.add(length, chunkGuitar, chunkBacking, weight)
            accumulator.flush(if (index + 1 < total) offset + STRIDE else shiftedLength)
            onChunk(index + 1, total)
        }
    }

    /** Roda o modelo no trecho e deixa em [chunkGuitar]/[chunkBacking] as [length] amostras do centro. */
    private fun runChunk(song: SongPcm, offset: Int, length: Int) {
        val delta = Demucs.SEGMENT - length
        val start = offset - delta / 2 // o trecho curto do fim pega áudio de antes, como o TensorChunk.padded
        val scale = if (song.std > 0f) 1f / song.std else 1f
        for (c in 0..1) {
            for (k in 0 until Demucs.SEGMENT) {
                val frame = start + k - StemAccumulator.MAX_SHIFT
                channel[k] = if (frame in 0 until song.frames) (song.sample(c, frame) - song.mean) * scale else 0f
            }
            channel.copyInto(mixIn, c * Demucs.SEGMENT)
            stft.forward(channel, specIn, offset = c * 2 * plane)
        }
        val out = model.run(mixIn, specIn)
        sumOtherSources(out.spec)
        for (c in 0..1) {
            waveOf(out.spec, (Demucs.GUITAR * 4 + c * 2) * plane, out.mix, Demucs.GUITAR, c)
            wave.copyInto(chunkGuitar[c], 0, delta / 2, delta / 2 + length)
            waveOf(restSpec, c * 2 * plane, out.mix, source = -1, c)
            wave.copyInto(chunkBacking[c], 0, delta / 2, delta / 2 + length)
        }
    }

    private fun sumOtherSources(spec: FloatArray) {
        restSpec.fill(0f)
        for (s in 0 until Demucs.SOURCES) {
            if (s == Demucs.GUITAR) continue
            val from = s * 4 * plane
            for (i in restSpec.indices) restSpec[i] += spec[from + i]
        }
    }

    /** Onda de um canal: iSTFT do espectro em [specOffset] mais o ramo do tempo ([source] -1 = as outras cinco). */
    private fun waveOf(spec: FloatArray, specOffset: Int, mix: FloatArray, source: Int, c: Int) {
        stft.inverse(spec, specOffset, wave)
        for (s in 0 until Demucs.SOURCES) {
            if (if (source < 0) s == Demucs.GUITAR else s != source) continue
            val from = (s * 2 + c) * Demucs.SEGMENT
            for (k in 0 until Demucs.SEGMENT) wave[k] += mix[from + k]
        }
    }
}
```
Se o detekt reclamar de `CyclomaticComplexMethod`/`ComplexCondition` em `waveOf`, extrair `private fun includes(source: Int, s: Int)`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :core:separation:testDebugUnitTest detekt --console=plain`
Expected: PASS (7 testes novos; o de ouro leva ~20 s).

- [ ] **Step 5: Commit**

```bash
git add core/separation/src
git commit -m "feat: separação por trechos do Demucs em fluxo, comparada ao Demucs original"
```

---

### Task 4: Decodificar a música (`SongPcmLoader`)

**Files:**
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/ResamplingPcmSource.kt`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/SongPcmLoader.kt`
- Test: `core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/ResamplingPcmSourceTest.kt`
- Test: `core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/SongPcmLoaderTest.kt`

**Interfaces:**
- Consumes: `PcmSource`, `PcmSourceOpener` (`:core:audio`), `SongPcm`.
- Produces:
  - `class ResamplingPcmSource(source: PcmSource, targetRate: Int) : PcmSource`;
  - `class SongPcmLoader(opener: PcmSourceOpener)` com `fun load(uri: String, scratch: File): MappedSong`;
  - `class MappedSong : SongPcm, Closeable` (o temporário é apagado no `close`).

- [ ] **Step 1: Escrever os testes que falham**

`ResamplingPcmSourceTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.audio.PcmSource
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** Fonte em memória, entregue em blocos pequenos (como o MediaCodec). */
class ArrayPcmSource(
    private val interleaved: FloatArray,
    override val sampleRate: Int,
    override val channelCount: Int,
) : PcmSource {
    private var position = 0
    override fun read(buffer: FloatArray, maxFrames: Int): Int {
        val frames = minOf(maxFrames, 1000, (interleaved.size - position) / channelCount)
        interleaved.copyInto(buffer, 0, position, position + frames * channelCount)
        position += frames * channelCount
        return frames
    }
    override fun close() = Unit
}

class ResamplingPcmSourceTest {
    @Test
    fun a48kToneComesOutAt44kWithTheSamePitch() {
        val input = FloatArray(48_000) { (0.5 * sin(2 * Math.PI * 1000 * it / 48_000)).toFloat() }
        val source = ResamplingPcmSource(ArrayPcmSource(input, 48_000, 1), 44_100)
        val out = ArrayList<Float>()
        val buffer = FloatArray(4096)
        while (true) {
            val n = source.read(buffer, 4096)
            if (n == 0) break
            for (i in 0 until n) out += buffer[i]
        }

        assertEquals(44_100, source.sampleRate)
        assertEquals(44_100.0, out.size.toDouble(), 50.0)
        // 1 kHz a 44,1 kHz: compara o meio do sinal com a senoide esperada.
        for (i in 1000 until 43_000) {
            assertEquals(0.5 * sin(2 * Math.PI * 1000 * i / 44_100), out[i].toDouble(), 0.01)
        }
    }

    @Test
    fun theSameRatePassesThrough() {
        val input = FloatArray(2000) { it / 2000f }
        val source = ResamplingPcmSource(ArrayPcmSource(input, 44_100, 2), 44_100)
        val buffer = FloatArray(2000)

        assertEquals(1000, source.read(buffer, 1000))
        assertEquals(0f, abs(buffer[1999] - input[1999]), 0f)
    }
}
```
`SongPcmLoaderTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SongPcmLoaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aStereoSongKeepsItsChannelsAndStats() {
        val interleaved = FloatArray(2 * 10_000) { if (it % 2 == 0) 0.5f else -0.25f }
        val loader = SongPcmLoader { ArrayPcmSource(interleaved, 44_100, 2) }

        loader.load("x", tmp.root).use { song ->
            assertEquals(10_000, song.frames)
            assertEquals(0.5f, song.sample(0, 1234))
            assertEquals(-0.25f, song.sample(1, 1234))
            assertEquals(0.125f, song.mean, 1e-6f)
            assertEquals(0f, song.std, 1e-6f)
        }
        assertEquals(0, tmp.root.listFiles()!!.size) // o temporário sai no close
    }

    @Test
    fun aMono48kSongBecomesStereoAt44k() {
        val mono = FloatArray(48_000) { (it % 48) / 48f }
        val loader = SongPcmLoader { ArrayPcmSource(mono, 48_000, 1) }

        loader.load("x", tmp.root).use { song ->
            assertEquals(44_100.0, song.frames.toDouble(), 50.0)
            assertEquals(song.sample(0, 500), song.sample(1, 500))
        }
    }

    @Test
    fun moreThanTwoChannelsKeepTheFirstTwo() {
        val six = FloatArray(6 * 100) { (it % 6).toFloat() }
        SongPcmLoader { ArrayPcmSource(six, 44_100, 6) }.load("x", tmp.root).use { song ->
            assertEquals(0f, song.sample(0, 50))
            assertEquals(1f, song.sample(1, 50))
            assertFalse(song.std.isNaN())
        }
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:separation:testDebugUnitTest --tests '*Resampling*' --tests '*SongPcmLoader*' --console=plain`
Expected: FAIL ("Unresolved reference: ResamplingPcmSource").

- [ ] **Step 3: Implementar**

`ResamplingPcmSource.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.audio.PcmSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Converte a taxa de [source] para [targetRate] com sinc janelado (Blackman, [TAPS] de cada lado, corte em 90 % do
 * Nyquist menor). O Opus dos mapas decodifica a 48 kHz e o modelo pede 44,1 kHz. Na mesma taxa, só repassa.
 */
class ResamplingPcmSource(private val source: PcmSource, targetRate: Int) : PcmSource {
    override val sampleRate = targetRate
    override val channelCount = source.channelCount
    private val step = source.sampleRate.toDouble() / targetRate
    private val cutoff = 0.9 * min(1.0, 1.0 / step)
    private val history = Array(channelCount) { FloatArray(0) }
    private var historyStart = 0L // quadro de entrada de history[c][0]
    private var available = 0
    private var ended = false
    private var nextOut = 0L
    private val block = FloatArray(BLOCK * channelCount)

    override fun read(buffer: FloatArray, maxFrames: Int): Int {
        if (source.sampleRate == sampleRate) return source.read(buffer, maxFrames)
        var produced = 0
        while (produced < maxFrames) {
            val center = nextOut * step
            if (!ensure(floor(center).toLong() + TAPS + 1)) break
            for (c in 0 until channelCount) buffer[produced * channelCount + c] = interpolate(c, center)
            produced++
            nextOut++
        }
        return produced
    }

    private fun interpolate(c: Int, center: Double): Float {
        val first = floor(center).toLong() - TAPS + 1
        var sum = 0.0
        for (j in first..first + 2 * TAPS - 1) {
            val i = (j - historyStart).toInt()
            if (j < 0 || i >= available) continue
            sum += history[c][i] * kernel(center - j)
        }
        return sum.toFloat()
    }

    private fun kernel(x: Double): Double {
        if (x == 0.0) return cutoff
        val window = 0.42 + 0.5 * cos(PI * x / TAPS) + 0.08 * cos(2 * PI * x / TAPS)
        return sin(PI * cutoff * x) / (PI * x) * window
    }

    /** Garante entrada até o quadro [frame] (exclusive); falso se a música acabou antes de chegar perto. */
    private fun ensure(frame: Long): Boolean {
        dropOld()
        while (!ended && historyStart + available < frame) {
            val n = source.read(block, BLOCK)
            if (n == 0) ended = true else append(n)
        }
        return historyStart + available > floor(nextOut * step).toLong()
    }

    private fun append(frames: Int) {
        for (c in 0 until channelCount) {
            val grown = history[c].copyOf(available + frames)
            for (k in 0 until frames) grown[available + k] = block[k * channelCount + c]
            history[c] = grown
        }
        available += frames
    }

    private fun dropOld() {
        val keepFrom = floor(nextOut * step).toLong() - TAPS
        val drop = (keepFrom - historyStart).toInt()
        if (drop <= BLOCK) return
        for (c in 0 until channelCount) history[c] = history[c].copyOfRange(drop, available)
        available -= drop
        historyStart += drop
    }

    override fun close() = source.close()

    private companion object {
        const val TAPS = 32
        const val BLOCK = 4096
    }
}
```
`SongPcmLoader.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.audio.PcmSourceOpener
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/** A música decodificada num arquivo temporário mapeado em memória (fora da heap do app). */
class MappedSong internal constructor(
    private val file: File,
    private val data: FloatBuffer,
    override val frames: Int,
    override val mean: Float,
    override val std: Float,
) : SongPcm, Closeable {
    override fun sample(channel: Int, frame: Int): Float = data.get(2 * frame + channel)

    override fun close() {
        file.delete()
    }
}

/** Decodifica a música inteira para 44,1 kHz estéreo e calcula a média e o desvio do mono (Welford). */
class SongPcmLoader(private val opener: PcmSourceOpener) {
    fun load(uri: String, scratch: File): MappedSong {
        val file = File.createTempFile("musica", ".f32", scratch)
        val stats = MonoStats()
        ResamplingPcmSource(opener.open(uri), Demucs.SAMPLE_RATE).use { source ->
            DataOutputStream(BufferedOutputStream(file.outputStream(), BUFFER)).use { out ->
                copyStereo(source, out, stats)
            }
        }
        val data = RandomAccessFile(file, "r").use {
            it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()).order(ByteOrder.LITTLE_ENDIAN)
        }.asFloatBuffer()
        return MappedSong(file, data, stats.count.toInt(), stats.mean.toFloat(), stats.std().toFloat())
    }

    private fun copyStereo(source: com.rodrigomachado.riff.core.audio.PcmSource, out: DataOutputStream, stats: MonoStats) {
        val channels = source.channelCount
        val buffer = FloatArray(BLOCK * channels)
        while (true) {
            val n = source.read(buffer, BLOCK)
            if (n == 0) break
            for (k in 0 until n) {
                val left = buffer[k * channels]
                val right = if (channels > 1) buffer[k * channels + 1] else left
                out.writeInt(Integer.reverseBytes(left.toRawBits()))
                out.writeInt(Integer.reverseBytes(right.toRawBits()))
                stats.add((left + right) / 2.0)
            }
        }
    }

    private class MonoStats {
        var count = 0L
        var mean = 0.0
        private var m2 = 0.0

        fun add(x: Double) {
            count++
            val delta = x - mean
            mean += delta / count
            m2 += delta * (x - mean)
        }

        fun std() = if (count > 1) sqrt(m2 / (count - 1)) else 0.0
    }

    private companion object {
        const val BLOCK = 4096
        const val BUFFER = 1 shl 16
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :core:separation:testDebugUnitTest detekt --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/separation/src
git commit -m "feat: decodificar a música para 44,1 kHz estéreo num arquivo mapeado"
```

---

### Task 5: Guardar as separações (Room v5, repositório, loader)

**Files:**
- Modify: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/Entities.kt`, `RiffDatabase.kt`, `DatabaseModule.kt`
- Create: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/AiSeparationDao.kt`
- Test: `core/database/src/test/kotlin/com/rodrigomachado/riff/core/database/MigrationTest.kt` (novo teste)
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/SeparationStatus.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/AiSeparations.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/game/ChartAudioProbe.kt`
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/game/ChartTrackLoader.kt`, `DataModule.kt`
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/RoomAiSeparationRepositoryTest.kt`
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/game/FolderChartTrackLoaderTest.kt` (novos testes)
- Create: `core/data/src/testFixtures/...` não: o fake fica em `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/FakeAiSeparations.kt`

**Interfaces:**
- Consumes: `ChartEntity`, `FolderSource`, `ChartRepository`, `ChartAudioFiles`.
- Produces:
  - `:core:model`: `sealed interface SeparationStatus { Idle; Running(chartId, title, progress: Float, remainingMs: Long?, pausedForHeat: Boolean = false); Failed(title, reason: SeparationFailure) }`, `enum class SeparationFailure { NO_SPACE, NO_AUDIO, ERROR }` e `data class SeparationUsage(val songs: Int, val bytes: Long)`.
  - `:core:data`:
    - `enum class AiSeparationState { SEPARATED, DECLINED }`;
    - `data class SeparatedFiles(val guitar: File, val backing: File)`;
    - `interface AiSeparationRepository` com `separatedIds: Flow<Set<String>>`, `usage: Flow<SeparationUsage>`, `stateOf`, `separatedFiles`, `workDir(chartId): File`, `commit(chartId, workDir)`, `decline`, `resetDeclines`, `deleteAll`, `pruneOrphans`;
    - `interface SeparationScheduler` com `status: StateFlow<SeparationStatus>`, `lowMemoryDevice: Boolean`, `start(chartId, title): Boolean`, `cancel()` e `dismissFailure()`;
    - `interface ChartAudioProbe { suspend fun audioOf(chartId: String): ChartAudio? }`;
    - `object ChartAudioFinder { fun find(files: List<FolderEntry>): ChartAudio? }`;
    - `@SeparationsDir File`;
    - `const val SEPARATED_GUITAR = "guitar.m4a"` e `SEPARATED_BACKING = "backing.m4a"`.

- [ ] **Step 1: Escrever os testes que falham**

`MigrationTest.kt`, acrescentar:
```kotlin
    @Test
    fun version4BecomesVersion5AddingTheAiSeparations() {
        helper.createDatabase(4).apply {
            execSQL(
                "INSERT INTO charts (id, title, artist, album, charter, durationMs, folderUri, format, " +
                    "lastSeenScanId) VALUES ('a', 'Música', 'Banda', NULL, NULL, NULL, 'content://a', 'CHART', 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(5).apply {
            execSQL("INSERT INTO ai_separations (chartId, state, bytes, createdAtEpochMs) VALUES ('a', 'SEPARATED', 9, 1)")
            prepare("SELECT COUNT(*) FROM charts").use {
                it.step()
                assertEquals(1L, it.getLong(0))
            }
            close()
        }
    }
```
`RoomAiSeparationRepositoryTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RoomAiSeparationRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()
    private lateinit var db: RiffDatabase
    private lateinit var repo: RoomAiSeparationRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomAiSeparationRepository(db.aiSeparationDao(), tmp.root, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun chart(id: String) =
        db.chartDao().replace(ChartEntity(id, "M", "B", null, null, null, "content://$id", "CHART", 1), emptyList())

    private suspend fun separate(id: String): File {
        chart(id)
        val work = repo.workDir(id)
        File(work, SEPARATED_GUITAR).writeBytes(ByteArray(10))
        File(work, SEPARATED_BACKING).writeBytes(ByteArray(5))
        repo.commit(id, work)
        return work
    }

    @Test
    fun aCommittedSeparationHasItsFilesAndCounts() = runTest {
        val work = separate("content://pasta/Música#sng")

        val files = repo.separatedFiles("content://pasta/Música#sng")!!
        assertTrue(files.guitar.isFile && files.backing.isFile)
        assertFalse(work.exists()) // a pasta temporária virou a definitiva
        assertEquals(AiSeparationState.SEPARATED, repo.stateOf("content://pasta/Música#sng"))
        assertEquals(setOf("content://pasta/Música#sng"), repo.separatedIds.first())
        assertEquals(SeparationUsage(songs = 1, bytes = 15), repo.usage.first())
    }

    @Test
    fun aDeclinedSongIsRememberedAndCanBeAskedAgain() = runTest {
        chart("a")
        repo.decline("a")
        assertEquals(AiSeparationState.DECLINED, repo.stateOf("a"))
        assertNull(repo.separatedFiles("a"))

        repo.resetDeclines()
        assertNull(repo.stateOf("a"))
    }

    @Test
    fun deletingAllKeepsTheDeclines() = runTest {
        separate("a")
        chart("b")
        repo.decline("b")

        repo.deleteAll()

        assertNull(repo.stateOf("a"))
        assertEquals(AiSeparationState.DECLINED, repo.stateOf("b"))
        assertEquals(0, tmp.root.listFiles()!!.size)
        assertEquals(SeparationUsage(0, 0), repo.usage.first())
    }

    @Test
    fun aSongThatLeftTheLibraryLosesItsFiles() = runTest {
        separate("a")
        separate("b")

        db.chartDao().deleteNotSeenIn(2) // a reimportação não achou os dois
        chart("b")
        repo.commit("b", repo.workDir("b").also {
            File(it, SEPARATED_GUITAR).writeBytes(ByteArray(1))
            File(it, SEPARATED_BACKING).writeBytes(ByteArray(1))
        })
        repo.pruneOrphans()

        assertNull(repo.separatedFiles("a"))
        assertEquals(1, tmp.root.listFiles()!!.size)
    }

    @Test
    fun aWorkDirStartsEmptyEvenAfterACrash() = runTest {
        val first = repo.workDir("a")
        File(first, "lixo").writeText("x")

        assertEquals(0, repo.workDir("a").listFiles()!!.size)
    }
}
```
`FakeAiSeparations.kt` (testes de `:core:data`):
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class FakeAiSeparationRepository : AiSeparationRepository {
    val files = mutableMapOf<String, SeparatedFiles>()
    val states = mutableMapOf<String, AiSeparationState>()
    var pruned = 0
    override val separatedIds = MutableStateFlow(emptySet<String>())
    override val usage = MutableStateFlow(SeparationUsage(0, 0))
    override suspend fun stateOf(chartId: String) = states[chartId]
    override suspend fun separatedFiles(chartId: String) = files[chartId]
    override fun workDir(chartId: String) = File("tmp/$chartId")
    override suspend fun commit(chartId: String, workDir: File) {
        states[chartId] = AiSeparationState.SEPARATED
    }
    override suspend fun decline(chartId: String) {
        states[chartId] = AiSeparationState.DECLINED
    }
    override suspend fun resetDeclines() {
        states.values.removeAll { it == AiSeparationState.DECLINED }
    }
    override suspend fun deleteAll() {
        states.values.removeAll { it == AiSeparationState.SEPARATED }
    }
    override suspend fun pruneOrphans() {
        pruned++
    }
}
```
`FolderChartTrackLoaderTest.kt`: o construtor passa a receber o repositório (`FolderChartTrackLoader(source, charts, separations, dispatcher)`), com `private val separations = FakeAiSeparationRepository()`. Novos testes:
```kotlin
    @Test
    fun anAiSeparationBecomesStemsWithTheSongAsFallback() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/song.opus", "música")
        registerChart("raiz/M")
        separations.files["id"] = SeparatedFiles(File("/sep/x/guitar.m4a"), File("/sep/x/backing.m4a"))

        assertEquals(
            ChartAudio.Stems("file:///sep/x/guitar.m4a", listOf("file:///sep/x/backing.m4a"), "raiz/M/song.opus"),
            loader().load("id", Difficulty.EXPERT).audio,
        )
    }

    @Test
    fun theChartsOwnStemsWinOverTheAiOnes() = runTest {
        source.file("raiz/M/notes.chart", chartText("Música", "Banda"))
        source.file("raiz/M/guitar.ogg", "guitarra")
        source.file("raiz/M/song.ogg", "música")
        registerChart("raiz/M")
        separations.files["id"] = SeparatedFiles(File("/sep/x/guitar.m4a"), File("/sep/x/backing.m4a"))

        assertEquals("raiz/M/guitar.ogg", (loader().load("id", Difficulty.EXPERT).audio as ChartAudio.Stems).guitarUri)
    }
```
E um teste da sonda, em `ChartAudioProbeTest.kt` (mesmo pacote `game`):
```kotlin
package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.importer.FakeChartRepository
import com.rodrigomachado.riff.core.data.importer.FakeFolderSource
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartAudioProbeTest {
    private val source = FakeFolderSource()
    private val charts = FakeChartRepository()

    @Test
    fun findsTheFolderAudioWithoutReadingTheNotes() = runTest {
        source.file("raiz/M/song.opus", "música")
        charts.saveScanned(
            Chart("id", "M", "B", null, null, null, "raiz/M", ChartFormat.CHART, setOf(Difficulty.EXPERT)),
            scanId = 1,
        )
        val probe = FolderChartAudioProbe(source, charts, StandardTestDispatcher(testScheduler))

        assertEquals(ChartAudio.Single("raiz/M/song.opus"), probe.audioOf("id"))
        assertNull(probe.audioOf("outro"))
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:database:testDebugUnitTest :core:data:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (`aiSeparationDao`, `RoomAiSeparationRepository`, `FolderChartAudioProbe`, `SeparatedFiles`).

- [ ] **Step 3: Implementar**

`Entities.kt`, acrescentar:
```kotlin
/** Separação da guitarra pela IA: SEPARATED (arquivos em filesDir/separacoes) ou DECLINED ("Jogar sem separar"). */
@Entity(
    tableName = "ai_separations",
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
)
data class AiSeparationEntity(@PrimaryKey val chartId: String, val state: String, val bytes: Long, val createdAtEpochMs: Long)
```
`AiSeparationDao.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class AiSeparationUsageRow(val songs: Int, val bytes: Long)

@Dao
interface AiSeparationDao {
    @Upsert
    suspend fun upsert(entity: AiSeparationEntity)

    @Query("SELECT state FROM ai_separations WHERE chartId = :chartId")
    suspend fun stateOf(chartId: String): String?

    @Query("SELECT chartId FROM ai_separations WHERE state = :state")
    fun observeIds(state: String): Flow<List<String>>

    @Query("SELECT chartId FROM ai_separations WHERE state = :state")
    suspend fun idsWith(state: String): List<String>

    @Query("SELECT COUNT(*) AS songs, COALESCE(SUM(bytes), 0) AS bytes FROM ai_separations WHERE state = 'SEPARATED'")
    fun observeUsage(): Flow<AiSeparationUsageRow>

    @Query("DELETE FROM ai_separations WHERE state = :state")
    suspend fun deleteState(state: String)
}
```
`RiffDatabase.kt`: `AiSeparationEntity::class` em `entities`, `version = 5`, `AutoMigration(from = 4, to = 5)` e `abstract fun aiSeparationDao(): AiSeparationDao`. `DatabaseModule.kt`: `@Provides fun aiSeparationDao(database: RiffDatabase): AiSeparationDao = database.aiSeparationDao()`. Rodar `./scripts/gw :core:database:kspDebugKotlin` uma vez para gerar `schemas/.../5.json` e versioná-lo.

`core/model/.../SeparationStatus.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

/** A separação da guitarra pela IA em segundo plano: o ícone, o painel e a notificação leem isto. */
sealed interface SeparationStatus {
    data object Idle : SeparationStatus

    /** [progress] de 0 a 1; [remainingMs] nulo enquanto não dá para estimar. */
    data class Running(
        val chartId: String,
        val title: String,
        val progress: Float,
        val remainingMs: Long?,
        val pausedForHeat: Boolean = false,
    ) : SeparationStatus

    data class Failed(val title: String, val reason: SeparationFailure) : SeparationStatus
}

enum class SeparationFailure { NO_SPACE, NO_AUDIO, ERROR }

/** Músicas separadas pela IA e o espaço que ocupam. */
data class SeparationUsage(val songs: Int, val bytes: Long)
```
`core/data/.../AiSeparations.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.database.AiSeparationDao
import com.rodrigomachado.riff.core.database.AiSeparationEntity
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Qualifier

const val SEPARATED_GUITAR = "guitar.m4a"
const val SEPARATED_BACKING = "backing.m4a"

/** `filesDir/separacoes`: uma pasta por música separada pela IA. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SeparationsDir

enum class AiSeparationState { SEPARATED, DECLINED }

/** Os dois arquivos que a IA gerou: a guitarra e o resto da música. */
data class SeparatedFiles(val guitar: File, val backing: File)

interface AiSeparationRepository {
    val separatedIds: Flow<Set<String>>
    val usage: Flow<SeparationUsage>

    suspend fun stateOf(chartId: String): AiSeparationState?

    suspend fun separatedFiles(chartId: String): SeparatedFiles?

    /** Pasta temporária vazia para gravar a separação de [chartId]. */
    fun workDir(chartId: String): File

    /** Troca a pasta temporária pela definitiva e marca a música como separada. */
    suspend fun commit(chartId: String, workDir: File)

    suspend fun decline(chartId: String)

    suspend fun resetDeclines()

    /** Apaga todas as separações (as recusas ficam). */
    suspend fun deleteAll()

    /** Apaga os arquivos de músicas que saíram da biblioteca (a linha já saiu em cascata). */
    suspend fun pruneOrphans()
}

/** Começa, cancela e acompanha a separação, uma por vez. */
interface SeparationScheduler {
    val status: StateFlow<SeparationStatus>

    /** Menos de 4 GB de RAM: a pergunta avisa que pode não funcionar. */
    val lowMemoryDevice: Boolean

    /** Falso se já há uma separação em andamento. */
    fun start(chartId: String, title: String): Boolean

    fun cancel()

    /** Some com o aviso de falha. */
    fun dismissFailure()
}

class RoomAiSeparationRepository @Inject constructor(
    private val dao: AiSeparationDao,
    @SeparationsDir private val root: File,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AiSeparationRepository {
    override val separatedIds = dao.observeIds(AiSeparationState.SEPARATED.name).map { it.toSet() }
    override val usage = dao.observeUsage().map { SeparationUsage(it.songs, it.bytes) }

    override suspend fun stateOf(chartId: String) = dao.stateOf(chartId)?.let(AiSeparationState::valueOf)

    override suspend fun separatedFiles(chartId: String): SeparatedFiles? = withContext(io) {
        if (stateOf(chartId) != AiSeparationState.SEPARATED) return@withContext null
        val dir = File(root, dirName(chartId))
        SeparatedFiles(File(dir, SEPARATED_GUITAR), File(dir, SEPARATED_BACKING))
            .takeIf { it.guitar.isFile && it.backing.isFile }
    }

    override fun workDir(chartId: String): File = File(root, dirName(chartId) + TMP).apply {
        deleteRecursively()
        mkdirs()
    }

    override suspend fun commit(chartId: String, workDir: File) = withContext(io) {
        val target = File(root, dirName(chartId))
        target.deleteRecursively()
        if (!workDir.renameTo(target)) throw IOException("não deu para guardar a separação de $chartId")
        val bytes = target.listFiles().orEmpty().sumOf { it.length() }
        try {
            dao.upsert(AiSeparationEntity(chartId, AiSeparationState.SEPARATED.name, bytes, System.currentTimeMillis()))
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            target.deleteRecursively() // o mapa saiu da biblioteca durante a separação
            throw IOException("o mapa $chartId saiu da biblioteca", e)
        }
    }

    override suspend fun decline(chartId: String) =
        dao.upsert(AiSeparationEntity(chartId, AiSeparationState.DECLINED.name, 0, System.currentTimeMillis()))

    override suspend fun resetDeclines() = dao.deleteState(AiSeparationState.DECLINED.name)

    override suspend fun deleteAll() = withContext(io) {
        dao.deleteState(AiSeparationState.SEPARATED.name)
        root.listFiles().orEmpty().filterNot { it.name.endsWith(TMP) }.forEach { it.deleteRecursively() }
    }

    override suspend fun pruneOrphans() = withContext(io) {
        val keep = dao.idsWith(AiSeparationState.SEPARATED.name).map(::dirName).toSet()
        root.listFiles().orEmpty()
            .filter { !it.name.endsWith(TMP) && it.name !in keep }
            .forEach { it.deleteRecursively() }
    }

    private companion object {
        const val TMP = ".tmp"

        /** O id do mapa é uma URI do SAF: vira um nome de pasta seguro. */
        fun dirName(chartId: String): String =
            MessageDigest.getInstance("SHA-1").digest(chartId.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
```
Se o Room embrulhar a violação da chave estrangeira em outra exceção (ex.: `android.database.SQLException` ou `androidx.sqlite.SQLiteException`), capturar essa e anotar o Ruling; o teste do Task 6 cobre o caso.

`ChartAudioProbe.kt`:
```kotlin
package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.chart.ChartAudioFiles
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** O áudio da pasta de um mapa (sem a separação da IA), sem ler as notas. */
interface ChartAudioProbe {
    suspend fun audioOf(chartId: String): ChartAudio?
}

class FolderChartAudioProbe @Inject constructor(
    private val source: FolderSource,
    private val charts: ChartRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ChartAudioProbe {
    override suspend fun audioOf(chartId: String): ChartAudio? = withContext(dispatcher) {
        val chart = charts.getChart(chartId) ?: return@withContext null
        ChartAudioFinder.find(source.listChildren(chart.folderUri).filter { !it.isDirectory })
    }
}

/** Um arquivo só, ou a guitarra separada do resto da banda. */
object ChartAudioFinder {
    fun find(files: List<FolderEntry>): ChartAudio? {
        val names = files.map { it.name }
        fun find(name: String) = ChartAudioFiles.find(names, name)?.let { found -> files.first { it.name == found } }
        val song = find("song")
        val guitar = find("guitar")
        val backing = ChartAudioFiles.BACKING_NAMES.mapNotNull(::find)
        return when {
            guitar != null && backing.isNotEmpty() ->
                ChartAudio.Stems(guitar.uri, backing.map { it.uri }, fallbackUri = song?.uri ?: guitar.uri)

            song != null -> ChartAudio.Single(song.uri)

            guitar != null -> ChartAudio.Single(guitar.uri)

            else -> null
        }
    }
}
```
`ChartTrackLoader.kt`: tirar `audioOf`; construtor `FolderChartTrackLoader(source, charts, private val separations: AiSeparationRepository, dispatcher)`; em `load`:
```kotlin
        val audio = ChartAudioFinder.find(children.filter { !it.isDirectory })
        PlayableChart(chart, difficulty, notes, withAiSeparation(chartId, audio))
```
e:
```kotlin
    /** Sem guitarra separada na pasta, usa a da IA (se houver), com o áudio original de reserva. */
    private suspend fun withAiSeparation(chartId: String, audio: ChartAudio?): ChartAudio? {
        if (audio is ChartAudio.Stems) return audio
        val files = separations.separatedFiles(chartId) ?: return audio
        val guitar = "file://" + files.guitar.path
        return ChartAudio.Stems(guitar, listOf("file://" + files.backing.path), fallbackUri = audio?.singleUri ?: guitar)
    }
```
`DataModule.kt`: em `DataBindsModule`, `@Binds abstract fun aiSeparations(impl: RoomAiSeparationRepository): AiSeparationRepository` e `@Binds abstract fun chartAudioProbe(impl: FolderChartAudioProbe): ChartAudioProbe`; num `@Module object` existente (o do `IoDispatcher`), `@Provides @SeparationsDir fun separationsDir(@ApplicationContext context: Context): File = File(context.filesDir, "separacoes")`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :core:database:testDebugUnitTest :core:data:testDebugUnitTest :core:separation:compileDebugKotlin detekt --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/model core/database core/data
git commit -m "feat: guardar as separações da IA (Room v5) e jogar com elas como guitarra separada"
```

---

### Task 6: `GuitarSeparationRunner` (orquestração, calor, falhas)

**Files:**
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/GuitarSeparationRunner.kt`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/SeparationProgress.kt`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/ThermalGate.kt`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/M4aStemSink.kt`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/DeviceInfo.kt`
- Test: `core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/GuitarSeparationRunnerTest.kt`
- Test: `core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/ThermalGateTest.kt`

**Interfaces:**
- Consumes: `AiSeparationRepository`, `ChartAudioProbe`, `SongPcmLoader`/`SongPcm`, `GuitarSeparator`, `chunkCount`, `DemucsModelFactory`, `StemSink`, `SeparationStatus`, `SeparationFailure`.
- Produces:
  - `@Singleton class SeparationProgress @Inject constructor()` com `val status: StateFlow<SeparationStatus>` e `fun set(status: SeparationStatus)`;
  - `interface DeviceInfo { val totalRamBytes: Long; fun freeBytes(): Long }`;
  - `class ThermalGate(hot: () -> Boolean, cool: () -> Boolean, pollMs: Long = 2000)` com `suspend fun awaitCool(onPause: (Boolean) -> Unit)`;
  - `fun interface StemSinkFactory { fun create(dir: File): ClosableStemSink }`;
  - `interface ClosableStemSink : StemSink { fun finish() }`;
  - `fun interface SongLoader { fun load(uri: String, scratch: File): SongPcmHandle }`;
  - `class GuitarSeparationRunner(...)` com `suspend fun run(chartId: String, title: String): Boolean` (verdadeiro se terminou e guardou).

- [ ] **Step 1: Escrever os testes que falham**

`ThermalGateTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalGateTest {
    @Test
    fun coolPassesStraightThrough() = runTest {
        val pauses = mutableListOf<Boolean>()
        ThermalGate(hot = { false }, cool = { true }).awaitCool { pauses += it }
        assertEquals(emptyList<Boolean>(), pauses)
    }

    @Test
    fun hotPausesUntilItCoolsDown() = runTest {
        var reads = 0
        val pauses = mutableListOf<Boolean>()
        ThermalGate(hot = { true }, cool = { ++reads >= 3 }, pollMs = 1000).awaitCool { pauses += it }

        assertEquals(listOf(true, false), pauses)
        assertEquals(3, reads)
        assertEquals(2000L, testScheduler.currentTime)
    }
}
```
`GuitarSeparationRunnerTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.SeparatedFiles
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class GuitarSeparationRunnerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val progress = SeparationProgress()
    private val repo = RunnerRepository()
    private val probe = object : ChartAudioProbe {
        var audio: ChartAudio? = ChartAudio.Single("content://m/song.opus")
        override suspend fun audioOf(chartId: String) = audio
    }
    private var free = 1L shl 30
    private val device = object : DeviceInfo {
        override val totalRamBytes = 12L shl 30
        override fun freeBytes() = free
    }
    private var hot = false
    private var cooled = CompletableDeferred<Unit>()
    private val sinks = mutableListOf<RecordingSink>()

    inner class RunnerRepository : AiSeparationRepository {
        val committed = mutableListOf<String>()
        var commitFails = false
        override val separatedIds = MutableStateFlow(emptySet<String>())
        override val usage = MutableStateFlow(SeparationUsage(0, 0))
        override suspend fun stateOf(chartId: String): AiSeparationState? = null
        override suspend fun separatedFiles(chartId: String): SeparatedFiles? = null
        override fun workDir(chartId: String) = File(tmp.root, "$chartId.tmp").apply { mkdirs() }
        override suspend fun commit(chartId: String, workDir: File) {
            if (commitFails) throw IOException("o mapa saiu da biblioteca")
            committed += chartId
        }
        override suspend fun decline(chartId: String) = Unit
        override suspend fun resetDeclines() = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun pruneOrphans() = Unit
    }

    class RecordingSink : ClosableStemSink {
        var frames = 0
        var finished = false
        override fun write(guitar: FloatArray, backing: FloatArray, frames: Int) {
            this.frames += frames
        }
        override fun finish() {
            finished = true
        }
    }

    private fun runner(frames: Int = 400_000) = GuitarSeparationRunner(
        repo = repo,
        probe = probe,
        loader = { _, _ -> songHandle(frames) },
        models = { EchoModel() },
        sinks = { RecordingSink().also(sinks::add) },
        progress = progress,
        device = device,
        thermal = ThermalGate(hot = { hot }, cool = { cooled.isCompleted }, pollMs = 1000),
        scratch = tmp.root,
    )

    private fun songHandle(frames: Int) = object : SongPcmHandle {
        override val frames = frames
        override val mean = 0f
        override val std = 1f
        override fun sample(channel: Int, frame: Int) = 0.1f
        override fun close() = Unit
    }

    @Test
    fun aSeparationWritesCommitsAndGoesBackToIdle() = runTest {
        val seen = mutableListOf<SeparationStatus>()
        val watch = launch { progress.status.collect { seen += it } }

        assertTrue(runner().run("m", "Música"))
        runCurrent()
        watch.cancel()

        assertEquals(listOf("m"), repo.committed)
        assertTrue(sinks.single().finished)
        assertEquals(400_000, sinks.single().frames)
        assertTrue(seen.any { it is SeparationStatus.Running && it.progress == 0.5f })
        assertEquals(SeparationStatus.Idle, progress.status.value)
    }

    @Test
    fun withoutSpaceItDoesNotStart() = runTest {
        free = 10L shl 20

        assertFalse(runner().run("m", "Música"))

        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.NO_SPACE), progress.status.value)
        assertTrue(sinks.isEmpty())
    }

    @Test
    fun aChartWithoutAudioFails() = runTest {
        probe.audio = null

        assertFalse(runner().run("m", "Música"))

        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.NO_AUDIO), progress.status.value)
    }

    @Test
    fun aChartThatLeftTheLibraryFailsAndLeavesNoTempDir() = runTest {
        repo.commitFails = true

        assertFalse(runner().run("m", "Música"))

        assertEquals(SeparationStatus.Failed("Música", SeparationFailure.ERROR), progress.status.value)
        assertFalse(File(tmp.root, "m.tmp").exists())
    }

    @Test
    fun heatPausesAndCancellingWhilePausedCleansUp() = runTest {
        hot = true
        val job = launch { runner().run("m", "Música") }
        runCurrent()
        assertEquals(true, (progress.status.value as SeparationStatus.Running).pausedForHeat)

        job.cancel()
        runCurrent()

        assertEquals(SeparationStatus.Idle, progress.status.value)
        assertFalse(File(tmp.root, "m.tmp").exists())
        assertTrue(repo.committed.isEmpty())
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:separation:testDebugUnitTest --tests '*Runner*' --tests '*ThermalGate*' --console=plain`
Expected: FAIL ("Unresolved reference: GuitarSeparationRunner").

- [ ] **Step 3: Implementar**

`SeparationProgress.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** O estado da separação para o app inteiro (o Worker roda no mesmo processo). */
@Singleton
class SeparationProgress @Inject constructor() {
    private val state = MutableStateFlow<SeparationStatus>(SeparationStatus.Idle)
    val status: StateFlow<SeparationStatus> = state.asStateFlow()

    fun set(status: SeparationStatus) {
        state.value = status
    }
}
```
`ThermalGate.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import kotlinx.coroutines.delay

/** Pausa entre trechos enquanto o celular está quente ([hot]) e volta quando esfria ([cool]). */
class ThermalGate(private val hot: () -> Boolean, private val cool: () -> Boolean, private val pollMs: Long = 2000) {
    suspend fun awaitCool(onPause: (Boolean) -> Unit) {
        if (!hot()) return
        onPause(true)
        while (!cool()) delay(pollMs)
        onPause(false)
    }
}
```
`DeviceInfo.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import android.app.ActivityManager
import android.content.Context
import java.io.File

/** Memória total do celular (para a arena e o aviso) e espaço livre onde as separações ficam. */
interface DeviceInfo {
    val totalRamBytes: Long

    fun freeBytes(): Long
}

class AndroidDeviceInfo(context: Context, private val dir: File) : DeviceInfo {
    override val totalRamBytes: Long = ActivityManager.MemoryInfo().also {
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
    }.totalMem

    override fun freeBytes(): Long = dir.apply { mkdirs() }.usableSpace
}
```
`M4aStemSink.kt` (só roda no aparelho; conferido no Task 11):
```kotlin
package com.rodrigomachado.riff.core.separation

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.rodrigomachado.riff.core.data.SEPARATED_BACKING
import com.rodrigomachado.riff.core.data.SEPARATED_GUITAR
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface ClosableStemSink : StemSink {
    /** Fecha os arquivos; sem isto eles ficam inválidos. */
    fun finish()
}

fun interface StemSinkFactory {
    fun create(dir: File): ClosableStemSink
}

/** Grava a guitarra e o resto em `.m4a` (AAC 160 kb/s, 44,1 kHz estéreo) na pasta da separação. */
class M4aStemSink(dir: File) : ClosableStemSink {
    private val guitar = AacFile(File(dir, SEPARATED_GUITAR))
    private val backing = AacFile(File(dir, SEPARATED_BACKING))

    override fun write(guitar: FloatArray, backing: FloatArray, frames: Int) {
        this.guitar.write(guitar, frames)
        this.backing.write(backing, frames)
    }

    override fun finish() {
        guitar.finish()
        backing.finish()
    }
}

private class AacFile(file: File) {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var framesWritten = 0L

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, Demucs.SAMPLE_RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    fun write(interleaved: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                drain(false)
                continue
            }
            val input = checkNotNull(codec.getInputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
            val count = minOf(frames - done, input.remaining() / BYTES_PER_FRAME)
            for (i in 2 * done until 2 * (done + count)) input.putShort(toPcm16(interleaved[i]))
            codec.queueInputBuffer(index, 0, count * BYTES_PER_FRAME, presentationUs(), 0)
            framesWritten += count
            done += count
            drain(false)
        }
    }

    fun finish() {
        var index = codec.dequeueInputBuffer(TIMEOUT_US)
        while (index < 0) {
            drain(false)
            index = codec.dequeueInputBuffer(TIMEOUT_US)
        }
        codec.queueInputBuffer(index, 0, 0, presentationUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
    }

    private fun drain(untilEnd: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }

                index >= 0 -> {
                    val data: ByteBuffer = checkNotNull(codec.getOutputBuffer(index))
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!config && info.size > 0) muxer.writeSampleData(track, data, info)
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }

                !untilEnd -> return
            }
        }
    }

    private fun presentationUs() = framesWritten * 1_000_000L / Demucs.SAMPLE_RATE

    private companion object {
        const val BIT_RATE = 160_000
        const val MAX_INPUT = 64 * 1024
        const val BYTES_PER_FRAME = 4
        const val TIMEOUT_US = 10_000L

        fun toPcm16(x: Float): Short = (x.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
    }
}
```
`GuitarSeparationRunner.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import android.util.Log
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File

/** A música decodificada; fechar apaga o temporário. */
interface SongPcmHandle : SongPcm, Closeable

fun interface SongLoader {
    fun load(uri: String, scratch: File): SongPcmHandle
}

/**
 * Uma separação do começo ao fim: confere o espaço, decodifica, separa trecho a trecho (com pausa pelo calor),
 * grava os `.m4a` numa pasta temporária e só então guarda. Qualquer falha deixa a música como estava.
 */
@Suppress("LongParameterList") // as dependências de cada etapa, trocadas por falsas nos testes
class GuitarSeparationRunner(
    private val repo: AiSeparationRepository,
    private val probe: ChartAudioProbe,
    private val loader: SongLoader,
    private val models: DemucsModelFactory,
    private val sinks: StemSinkFactory,
    private val progress: SeparationProgress,
    private val device: DeviceInfo,
    private val thermal: ThermalGate,
    private val scratch: File,
) {
    /** Verdadeiro se a separação terminou e foi guardada. */
    @Suppress("TooGenericExceptionCaught") // falha do modelo, do decodificador ou de memória: a música fica como estava
    suspend fun run(chartId: String, title: String): Boolean {
        if (device.freeBytes() < MIN_FREE_BYTES) return failed(title, SeparationFailure.NO_SPACE)
        val uri = probe.audioOf(chartId)?.singleUri ?: return failed(title, SeparationFailure.NO_AUDIO)
        val work = repo.workDir(chartId)
        val running = SeparationStatus.Running(chartId, title, progress = 0f, remainingMs = null)
        progress.set(running)
        return try {
            separate(uri, work, running)
            repo.commit(chartId, work)
            progress.set(SeparationStatus.Idle)
            true
        } catch (e: CancellationException) {
            progress.set(SeparationStatus.Idle)
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "não deu para separar $chartId", e)
            failed(title, SeparationFailure.ERROR)
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun separate(uri: String, work: File, running: SeparationStatus.Running) {
        loader.load(uri, scratch).use { song ->
            models.create(useArena = device.totalRamBytes >= ARENA_RAM_BYTES).use { model ->
                val sink = sinks.create(work)
                val started = System.nanoTime()
                var current = running
                GuitarSeparator(model).separate(
                    song,
                    sink,
                    onChunk = { done, total ->
                        val elapsedMs = (System.nanoTime() - started) / NANOS_PER_MS
                        current = current.copy(progress = done.toFloat() / total, remainingMs = elapsedMs / done * (total - done))
                        progress.set(current)
                    },
                    checkpoint = {
                        currentCoroutineContext().ensureActive()
                        thermal.awaitCool { paused -> progress.set(current.copy(pausedForHeat = paused)) }
                    },
                )
                sink.finish()
            }
        }
    }

    private fun failed(title: String, reason: SeparationFailure): Boolean {
        progress.set(SeparationStatus.Failed(title, reason))
        return false
    }

    private companion object {
        const val TAG = "GuitarSeparation"
        const val MIN_FREE_BYTES = 50L shl 20
        const val ARENA_RAM_BYTES = 7L shl 30 // "8 GB" de fábrica aparece como ~7,4 GB de totalMem
        const val NANOS_PER_MS = 1_000_000L
    }
}
```
`MappedSong` (Task 4) passa a implementar `SongPcmHandle` (troca `SongPcm, Closeable` por `SongPcmHandle`).

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :core:separation:testDebugUnitTest detekt --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/separation/src
git commit -m "feat: orquestrar a separação com pausa pelo calor, espaço e falhas"
```

---

### Task 7: Segundo plano (WorkManager, notificação, agendador)

**Files:**
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/SeparationWorker.kt`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/SeparationNotifications.kt`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/WorkSeparationScheduler.kt`
- Create: `core/separation/src/main/kotlin/com/rodrigomachado/riff/core/separation/SeparationModule.kt`
- Create: `core/separation/src/main/res/values/strings.xml`, `core/separation/src/main/res/values-en/strings.xml`
- Modify: `core/separation/src/main/AndroidManifest.xml`, `core/separation/build.gradle.kts`
- Modify: `app/build.gradle.kts`, `app/src/main/kotlin/com/rodrigomachado/riff/RiffApplication.kt`, `app/src/main/AndroidManifest.xml`
- Test: `core/separation/src/test/kotlin/com/rodrigomachado/riff/core/separation/WorkSeparationSchedulerTest.kt`

**Interfaces:**
- Consumes: `GuitarSeparationRunner`, `SeparationProgress`, `DeviceInfo`, `SeparationScheduler`.
- Produces:
  - `class WorkSeparationScheduler : SeparationScheduler` (`@Singleton`);
  - `SeparationWorker` (`@HiltWorker`, chaves `KEY_CHART_ID` e `KEY_TITLE`, trabalho único `"separacao-guitarra"`);
  - `SeparationNotifications.OPEN_LIBRARY_EXTRA = "riff.abrir_biblioteca"`.

- [ ] **Step 1: Escrever o teste que falha**

```kotlin
package com.rodrigomachado.riff.core.separation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkSeparationSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val progress = SeparationProgress()
    private val device = object : DeviceInfo {
        override val totalRamBytes = 3L shl 30
        override fun freeBytes() = 1L shl 30
    }
    private lateinit var scheduler: WorkSeparationScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        scheduler = WorkSeparationScheduler(context, progress, device)
    }

    private fun work() = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK).get()

    @Test
    fun startingQueuesOneWorkAndShowsItRightAway() {
        assertTrue(scheduler.start("m", "Música"))

        assertEquals(1, work().size)
        assertEquals(SeparationStatus.Running("m", "Música", 0f, null), progress.status.value)
    }

    @Test
    fun aSecondSongWaitsItsTurnByBeingRefused() {
        scheduler.start("m", "Música")

        assertFalse(scheduler.start("n", "Outra"))
        assertEquals(1, work().size)
    }

    @Test
    fun cancellingStopsTheWork() {
        scheduler.start("m", "Música")

        scheduler.cancel()

        assertEquals(WorkInfo.State.CANCELLED, work().single().state)
    }

    @Test
    fun aFailureCanBeDismissedAndThePhoneIsLowOnMemory() {
        progress.set(SeparationStatus.Failed("Música", SeparationFailure.ERROR))

        scheduler.dismissFailure()

        assertEquals(SeparationStatus.Idle, progress.status.value)
        assertTrue(scheduler.lowMemoryDevice)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:separation:testDebugUnitTest --tests '*Scheduler*' --console=plain`
Expected: FAIL ("Unresolved reference: WorkSeparationScheduler").

- [ ] **Step 3: Implementar**

`core/separation/build.gradle.kts`, dependências:
```kotlin
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.androidx.work.testing)
```
`core/separation/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Separar a guitarra continua com o app fechado: serviço em primeiro plano com notificação. -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync|mediaProcessing"
            tools:node="merge" />
    </application>
</manifest>
```
`strings.xml` (pt-BR; `values-en` com o mesmo conteúdo em inglês):
```xml
<resources>
    <string name="separation_channel">Guitarra separada por IA</string>
    <string name="separation_running">Separando a guitarra… %1$d%%</string>
    <string name="separation_paused">Pausado para esfriar…</string>
    <string name="separation_done">Guitarra separada: %1$s</string>
    <string name="separation_failed">Não deu para separar a guitarra de %1$s</string>
    <string name="separation_no_space">Pouco espaço no celular</string>
</resources>
```
`values-en/strings.xml`:
```xml
<resources>
    <string name="separation_channel">AI guitar separation</string>
    <string name="separation_running">Separating the guitar… %1$d%%</string>
    <string name="separation_paused">Paused to cool down…</string>
    <string name="separation_done">Guitar separated: %1$s</string>
    <string name="separation_failed">Couldn\'t separate the guitar of %1$s</string>
    <string name="separation_no_space">Not enough space on the phone</string>
</resources>
```
`SeparationNotifications.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlin.math.roundToInt

/** A notificação do Android: progresso enquanto separa, aviso de fim ou de falha. Tocar abre a Biblioteca. */
class SeparationNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun foregroundInfo(status: SeparationStatus.Running): ForegroundInfo {
        val text = if (status.pausedForHeat) {
            context.getString(R.string.separation_paused)
        } else {
            context.getString(R.string.separation_running, (status.progress * PERCENT).roundToInt())
        }
        val notification = builder().setContentTitle(status.title).setContentText(text).setOngoing(true)
            .setProgress(PERCENT, (status.progress * PERCENT).roundToInt(), false).build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return ForegroundInfo(PROGRESS_ID, notification, type)
    }

    fun done(title: String) = notify(context.getString(R.string.separation_done, title))

    fun failed(failure: SeparationStatus.Failed) = notify(
        if (failure.reason == SeparationFailure.NO_SPACE) {
            context.getString(R.string.separation_no_space)
        } else {
            context.getString(R.string.separation_failed, failure.title)
        },
    )

    private fun notify(text: String) = manager.notify(RESULT_ID, builder().setContentTitle(text).setAutoCancel(true).build())

    private fun builder(): NotificationCompat.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.separation_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(OPEN_LIBRARY_EXTRA, true)
        val pending = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
    }

    companion object {
        const val OPEN_LIBRARY_EXTRA = "riff.abrir_biblioteca"
        private const val CHANNEL = "separacao"
        private const val PROGRESS_ID = 41
        private const val RESULT_ID = 42
        private const val PERCENT = 100
    }
}
```
(`androidx.core:core-ktx` entra em `:core:separation`: `implementation(libs.androidx.core.ktx)`.)

`SeparationWorker.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.rodrigomachado.riff.core.model.SeparationStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Roda o [GuitarSeparationRunner] em primeiro plano, com a notificação acompanhando o progresso. */
@HiltWorker
class SeparationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: GuitarSeparationRunner,
    private val progress: SeparationProgress,
) : CoroutineWorker(context, params) {
    private val notifications = SeparationNotifications(context)
    private val chartId get() = checkNotNull(inputData.getString(KEY_CHART_ID))
    private val title get() = inputData.getString(KEY_TITLE).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifications.foregroundInfo(SeparationStatus.Running(chartId, title, 0f, null))

    override suspend fun doWork(): Result = coroutineScope {
        setForeground(getForegroundInfo())
        val updates = launch {
            progress.status.collect { status ->
                if (status is SeparationStatus.Running) setForeground(notifications.foregroundInfo(status))
            }
        }
        val ok = runner.run(chartId, title)
        updates.cancel()
        val status = progress.status.value
        if (ok) notifications.done(title) else if (status is SeparationStatus.Failed) notifications.failed(status)
        if (ok) Result.success() else Result.failure()
    }

    companion object {
        const val KEY_CHART_ID = "chartId"
        const val KEY_TITLE = "title"
    }
}

const val UNIQUE_WORK = "separacao-guitarra"
```
`WorkSeparationScheduler.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.model.SeparationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkSeparationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progress: SeparationProgress,
    device: DeviceInfo,
) : SeparationScheduler {
    override val status: StateFlow<SeparationStatus> = progress.status
    override val lowMemoryDevice = device.totalRamBytes < LOW_RAM_BYTES

    override fun start(chartId: String, title: String): Boolean {
        if (progress.status.value is SeparationStatus.Running) return false
        val request = OneTimeWorkRequestBuilder<SeparationWorker>()
            .setInputData(workDataOf(SeparationWorker.KEY_CHART_ID to chartId, SeparationWorker.KEY_TITLE to title))
            .build()
        // O ícone aparece na hora, antes de o WorkManager começar.
        progress.set(SeparationStatus.Running(chartId, title, progress = 0f, remainingMs = null))
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        return true
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    override fun dismissFailure() {
        if (progress.status.value is SeparationStatus.Failed) progress.set(SeparationStatus.Idle)
    }

    private companion object {
        const val LOW_RAM_BYTES = 4L shl 30
    }
}
```
`SeparationModule.kt`:
```kotlin
package com.rodrigomachado.riff.core.separation

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.rodrigomachado.riff.core.audio.MediaCodecPcmSource
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.data.SeparationsDir
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.FileInputStream
import java.io.File
import java.nio.channels.FileChannel
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SeparationBindsModule {
    @Binds
    abstract fun scheduler(impl: WorkSeparationScheduler): SeparationScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object SeparationModule {
    @Provides
    @Singleton
    fun deviceInfo(@ApplicationContext context: Context, @SeparationsDir dir: File): DeviceInfo =
        AndroidDeviceInfo(context, dir)

    @Provides
    @Suppress("LongParameterList") // as dependências do GuitarSeparationRunner
    fun runner(
        @ApplicationContext context: Context,
        repo: AiSeparationRepository,
        probe: ChartAudioProbe,
        opener: RandomReadOpener,
        progress: SeparationProgress,
        device: DeviceInfo,
    ): GuitarSeparationRunner = GuitarSeparationRunner(
        repo = repo,
        probe = probe,
        loader = { uri, scratch ->
            SongPcmLoader { MediaCodecPcmSource.open(context, it, opener) }.load(uri, scratch)
        },
        models = { useArena -> OrtDemucsModel(modelBuffer(context), useArena = useArena) },
        sinks = { dir -> M4aStemSink(dir) },
        progress = progress,
        device = device,
        thermal = thermalGate(context),
        scratch = context.cacheDir,
    )

    /** O modelo mapeado direto do APK (asset sem compressão): não ocupa a heap nem é copiado. */
    private fun modelBuffer(context: Context) = context.assets.openFd(Demucs.MODEL_ASSET).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use {
            it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    private fun thermalGate(context: Context): ThermalGate {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalGate(hot = { false }, cool = { true })
        val power = context.getSystemService(PowerManager::class.java)
        return ThermalGate(
            hot = { power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE },
            cool = { power.currentThermalStatus <= PowerManager.THERMAL_STATUS_MODERATE },
        )
    }
}
```
`MappedSong` implementa `SongPcmHandle` (Task 6), então `loader` devolve o tipo certo.

App: `app/build.gradle.kts` ganha `implementation(project(":core:separation"))` e `implementation(libs.androidx.work.runtime)`, `implementation(libs.androidx.hilt.work)`. `RiffApplication`:
```kotlin
@HiltAndroidApp
class RiffApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // O WorkManager cria os Workers pelo Hilt (o SeparationWorker recebe o runner injetado).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() { /* como hoje */ }
}
```
`app/src/main/AndroidManifest.xml`, dentro de `<application>` (com `xmlns:tools`):
```xml
        <!-- O WorkManager é iniciado pelo RiffApplication (com a fábrica do Hilt), não pelo androidx.startup. -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :core:separation:testDebugUnitTest :app:assembleDebug detekt --console=plain`
Expected: PASS e o APK monta.

- [ ] **Step 5: Commit**

```bash
git add core/separation app
git commit -m "feat: separação em segundo plano com WorkManager e notificação"
```

---

### Task 8: Ícone e painel (`SeparationIndicator`) nas telas

**Files:**
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/SeparationIndicator.kt`
- Modify: `core/ui/src/main/res/values/strings.xml`, `core/ui/src/main/res/values-en/strings.xml`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/component/SeparationIndicatorTest.kt`
- Modify: `feature/library/.../LibraryScreen.kt` (`LibraryHeader`), `feature/nowplaying/.../NowPlayingScreen.kt` (linha Biblioteca/Configurações), `feature/search/.../SearchScreen.kt` (linha do Voltar), `feature/settings/.../SettingsScreen.kt` (linha do Voltar)
- Modify: `app/src/main/kotlin/com/rodrigomachado/riff/MainActivity.kt`, `RiffNavHost.kt`
- Create: `app/src/main/kotlin/com/rodrigomachado/riff/OpenLibrary.kt`
- Test: `app/src/test/kotlin/com/rodrigomachado/riff/OpenLibraryTest.kt`

**Interfaces:**
- Consumes: `SeparationStatus`, `SeparationScheduler`, `SeparationNotifications.OPEN_LIBRARY_EXTRA`.
- Produces:
  - `interface SeparationControls { val status: StateFlow<SeparationStatus>; fun cancel(); fun dismissFailure() }`;
  - `val LocalSeparation = staticCompositionLocalOf<SeparationControls?> { null }`;
  - `@Composable fun SeparationIndicator(modifier: Modifier = Modifier)`, que lê o local;
  - `@Composable fun SeparationIndicatorContent(status: SeparationStatus, onCancel: () -> Unit, onDismissFailure: () -> Unit, modifier: Modifier = Modifier)`;
  - `object SeparationIndicatorTags { const val ICON = "separation_icon" }`;
  - `fun opensLibrary(intent: Intent?): Boolean`.

- [ ] **Step 1: Escrever os testes que falham**

`SeparationIndicatorTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class SeparationIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val events = mutableListOf<String>()

    private fun show(status: SeparationStatus) = composeRule.setContent {
        RiffTheme { SeparationIndicatorContent(status, onCancel = { events += "cancel" }, onDismissFailure = { events += "ok" }) }
    }

    @Test
    fun idleShowsNothing() {
        show(SeparationStatus.Idle)
        composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).assertDoesNotExist()
    }

    @Test
    fun theIconOpensThePanelWithProgressTimeAndCancel() {
        show(SeparationStatus.Running("m", "Smells Like Teen Spirit", 0.45f, remainingMs = 70_000))

        composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).performClick()

        composeRule.onNodeWithText("Smells Like Teen Spirit").assertExists()
        composeRule.onNodeWithText("Separando a guitarra… 45%").assertExists()
        composeRule.onNodeWithText("cerca de 2 min").assertExists()
        composeRule.onNodeWithText("Cancelar").performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun pausedForHeatSaysSo() {
        show(SeparationStatus.Running("m", "M", 0.2f, null, pausedForHeat = true))

        composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).performClick()

        composeRule.onNodeWithText("Pausado para esfriar…").assertExists()
    }

    @Test
    fun aFailureIsShownUntilDismissed() {
        show(SeparationStatus.Failed("M", SeparationFailure.ERROR))

        composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).performClick()
        composeRule.onNodeWithText("Não deu para separar a guitarra de M").assertExists()
        composeRule.onNodeWithText("OK").performClick()

        assertEquals(listOf("ok"), events)
    }
}
```
`OpenLibraryTest.kt`:
```kotlin
package com.rodrigomachado.riff

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenLibraryTest {
    @Test
    fun theSeparationNotificationOpensTheLibrary() {
        assertTrue(opensLibrary(Intent().putExtra("riff.abrir_biblioteca", true)))
        assertFalse(opensLibrary(Intent()))
        assertFalse(opensLibrary(null))
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:ui:testDebugUnitTest :app:testDebugUnitTest --console=plain`
Expected: FAIL ("Unresolved reference: SeparationIndicatorContent", "opensLibrary").

- [ ] **Step 3: Implementar**

Strings de `:core:ui` (`values`; `values-en` em inglês):
```xml
    <string name="ui_separation_open">Separação da guitarra</string>
    <string name="ui_separation_running">Separando a guitarra… %1$d%%</string>
    <string name="ui_separation_remaining">cerca de %1$d min</string>
    <string name="ui_separation_paused">Pausado para esfriar…</string>
    <string name="ui_separation_cancel">Cancelar</string>
    <string name="ui_separation_failed">Não deu para separar a guitarra de %1$s</string>
    <string name="ui_separation_no_space">Pouco espaço no celular</string>
    <string name="ui_separation_ok">OK</string>
```
`SeparationIndicator.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.ui.R
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A separação da guitarra em segundo plano, fornecida pelo app (as telas só põem o ícone). */
interface SeparationControls {
    val status: StateFlow<SeparationStatus>

    fun cancel()

    fun dismissFailure()
}

val LocalSeparation = staticCompositionLocalOf<SeparationControls?> { null }

object SeparationIndicatorTags {
    const val ICON = "separation_icon"
}

/** O ícone da separação no topo da tela; some quando não há separação. */
@Composable
fun SeparationIndicator(modifier: Modifier = Modifier) {
    val controls = LocalSeparation.current ?: return
    val status by controls.status.collectAsState()
    SeparationIndicatorContent(status, controls::cancel, controls::dismissFailure, modifier)
}

@Composable
fun SeparationIndicatorContent(
    status: SeparationStatus,
    onCancel: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == SeparationStatus.Idle) return
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.ui_separation_open)
    IconButton(
        onClick = withMenuSound(MenuSound.NAVIGATE) { open = true },
        modifier = modifier.testTag(SeparationIndicatorTags.ICON).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (status is SeparationStatus.Running) {
                CircularProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.size(RING),
                    color = RiffColors.Paper,
                    trackColor = RiffColors.PaperDim,
                )
            }
            Pick(failed = status is SeparationStatus.Failed)
        }
    }
    if (open) SeparationPanel(status, onCancel, onDismissFailure) { open = false }
}

@Composable
private fun SeparationPanel(
    status: SeparationStatus,
    onCancel: () -> Unit,
    onDismissFailure: () -> Unit,
    onClose: () -> Unit,
) {
    when (status) {
        is SeparationStatus.Running -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(status.title) },
            text = { Column { runningText(status).forEach { Text(it) } } },
            confirmButton = {
                TextButton(onClick = withMenuSound(MenuSound.BACK) {
                    onClose()
                    onCancel()
                }) { Text(stringResource(R.string.ui_separation_cancel)) }
            },
        )

        is SeparationStatus.Failed -> AlertDialog(
            onDismissRequest = onClose,
            text = { Text(failedText(status)) },
            confirmButton = {
                TextButton(onClick = withMenuSound(MenuSound.CONFIRM) {
                    onClose()
                    onDismissFailure()
                }) { Text(stringResource(R.string.ui_separation_ok)) }
            },
        )

        SeparationStatus.Idle -> onClose()
    }
}

@Composable
private fun runningText(status: SeparationStatus.Running): List<String> {
    if (status.pausedForHeat) return listOf(stringResource(R.string.ui_separation_paused))
    val percent = stringResource(R.string.ui_separation_running, (status.progress * PERCENT).roundToInt())
    val minutes = status.remainingMs?.let { ceil(it / MS_PER_MIN).toInt().coerceAtLeast(1) } ?: return listOf(percent)
    return listOf(percent, stringResource(R.string.ui_separation_remaining, minutes))
}

@Composable
private fun failedText(status: SeparationStatus.Failed) = when (status.reason) {
    SeparationFailure.NO_SPACE -> stringResource(R.string.ui_separation_no_space)
    else -> stringResource(R.string.ui_separation_failed, status.title)
}

/** A palheta vermelha do ícone do app (desenhada em código); com falha, fica no tom de erro. */
@Composable
private fun Pick(failed: Boolean) {
    val color = if (failed) RiffColors.StampError else RiffColors.TourRed
    Canvas(Modifier.size(PICK)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h)
            cubicTo(w * 0.1f, h * 0.7f, 0f, h * 0.35f, w * 0.1f, h * 0.12f)
            cubicTo(w * 0.35f, 0f, w * 0.65f, 0f, w * 0.9f, h * 0.12f)
            cubicTo(w, h * 0.35f, w * 0.9f, h * 0.7f, w * 0.5f, h)
            close()
        }
        drawPath(path, color)
    }
}

private val RING = 34.dp
private val PICK = 18.dp
private const val PERCENT = 100
private const val MS_PER_MIN = 60_000.0
```
(`import androidx.compose.foundation.layout.Column` no arquivo.)

Nas telas:
- `LibraryHeader` (`LibraryScreen.kt`): `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically)` com Voltar, `SeparationIndicator()`, Pesquisar.
- `NowPlayingScreen.kt`: na `Row` de Biblioteca/Configurações, depois de Configurações: `Spacer(Modifier.weight(1f))` e `SeparationIndicator()`. A `Row` ganha `Modifier.fillMaxWidth()` e `verticalAlignment = Alignment.CenterVertically`.
- `SearchScreen.kt` e `SettingsScreen.kt`: o `TextButton` do Voltar vai para uma `Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)`, seguido de `Spacer(Modifier.weight(1f))` e `SeparationIndicator()`.

Sem `LocalSeparation`, nada muda; os screenshots continuam iguais.

App:
- `OpenLibrary.kt`:
```kotlin
package com.rodrigomachado.riff

import android.content.Intent

/** A notificação da separação abre a Biblioteca. */
fun opensLibrary(intent: Intent?): Boolean = intent?.getBooleanExtra("riff.abrir_biblioteca", false) == true
```
- `MainActivity`:
  - injeta `@Inject lateinit var separations: SeparationScheduler`;
  - mantém `private val openLibrary = mutableStateOf(false)`: no `onCreate`, `openLibrary.value = opensLibrary(intent)`; em `override fun onNewIntent(intent: Intent)`, `super.onNewIntent(intent); if (opensLibrary(intent)) openLibrary.value = true`;
  - fornece `LocalSeparation provides remember { object : SeparationControls { override val status = separations.status; override fun cancel() = separations.cancel(); override fun dismissFailure() = separations.dismissFailure() } }` junto do `LocalMenuSound`;
  - passa `RiffApp(openLibrary.value) { openLibrary.value = false }` → `RiffNavHost(modifier, openLibrary, onLibraryOpened)`.
- `RiffNavHost`: parâmetros `openLibrary: Boolean = false, onLibraryOpened: () -> Unit = {}` e:
```kotlin
    LaunchedEffect(openLibrary) {
        if (openLibrary) {
            navController.navigate(LibraryRoute()) { launchSingleTop = true }
            onLibraryOpened()
        }
    }
```
- `app/src/main/AndroidManifest.xml`: a `MainActivity` ganha `android:launchMode="singleTop"`. Assim, tocar na notificação com o app aberto chama `onNewIntent` em vez de criar outra tela.

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :core:ui:testDebugUnitTest :app:testDebugUnitTest testDebugUnitTest verifyRoborazziDebug detekt --console=plain`
Expected: PASS; screenshots sem diferença.

- [ ] **Step 5: Commit**

```bash
git add core/ui feature app
git commit -m "feat: ícone da separação com o painel de progresso nas telas e a notificação abrindo a Biblioteca"
```

---

### Task 9: Biblioteca — pergunta ao jogar e marca GUITARRA SEPARADA IA

**Files:**
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryViewModel.kt`
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryScreen.kt`
- Create: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/SeparationPrompt.kt`
- Modify: `feature/library/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Modify: `feature/library/src/test/kotlin/com/rodrigomachado/riff/feature/library/Fakes.kt`, `LibraryViewModelTest.kt`, `LibraryContentTest.kt`, `LibraryScreenshotTest.kt`

**Interfaces:**
- Consumes: `AiSeparationRepository`, `SeparationScheduler`, `ChartAudioProbe`, `ChartAudio`.
- Produces:
  - `LibraryUiState` ganha `aiSeparated: Set<String>`, `prompt: SeparationAsk?` e `play: PlayRequestLibrary?`;
  - `data class SeparationAsk(val chartId: String, val title: String, val difficulty: Difficulty, val lowMemory: Boolean, val busy: Boolean = false)`;
  - `data class PlayRequestLibrary(val chartId: String, val difficulty: Difficulty)`;
  - `LibraryViewModel` ganha `onDifficultyChosen(chart: Chart, difficulty: Difficulty)`, `onSeparate()`, `onPlayWithoutSeparating()`, `onPromptDismissed()` e `onPlayHandled()`;
  - `LibraryContent` ganha `onDifficultyChosen`, `onSeparate`, `onPlayWithoutSeparating`, `onPromptDismissed` (com padrões `{}`).

- [ ] **Step 1: Escrever os testes que falham**

`Fakes.kt` (library), acrescentar:
```kotlin
class FakeAiSeparations : AiSeparationRepository {
    val states = mutableMapOf<String, AiSeparationState>()
    var pruned = 0
    override val separatedIds = MutableStateFlow(emptySet<String>())
    override val usage = MutableStateFlow(SeparationUsage(0, 0))
    override suspend fun stateOf(chartId: String) = states[chartId]
    override suspend fun separatedFiles(chartId: String): SeparatedFiles? = null
    override fun workDir(chartId: String) = File("x")
    override suspend fun commit(chartId: String, workDir: File) = Unit
    override suspend fun decline(chartId: String) {
        states[chartId] = AiSeparationState.DECLINED
    }
    override suspend fun resetDeclines() = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun pruneOrphans() {
        pruned++
    }
}

class FakeScheduler : SeparationScheduler {
    override val status = MutableStateFlow<SeparationStatus>(SeparationStatus.Idle)
    override var lowMemoryDevice = false
    val started = mutableListOf<String>()
    override fun start(chartId: String, title: String): Boolean {
        if (status.value is SeparationStatus.Running) return false
        started += chartId
        status.value = SeparationStatus.Running(chartId, title, 0f, null)
        return true
    }
    override fun cancel() = Unit
    override fun dismissFailure() = Unit
}

class FakeAudioProbe : ChartAudioProbe {
    val audio = mutableMapOf<String, ChartAudio>()
    override suspend fun audioOf(chartId: String) = audio[chartId]
}
```
`LibraryViewModelTest.kt` (o construtor do ViewModel ganha `separations`, `scheduler` e `probe`; ajustar o `viewModel()` existente). Testes:
```kotlin
    private val separations = FakeAiSeparations()
    private val scheduler = FakeScheduler()
    private val probe = FakeAudioProbe()
    private val song = Chart("m", "Música", "Banda", null, null, null, "u", ChartFormat.CHART, setOf(Difficulty.EXPERT))

    @Test
    fun aSongWithoutSeparateGuitarAsksBeforePlaying() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(SeparationAsk("m", "Música", Difficulty.EXPERT, lowMemory = false), vm.uiState.value.prompt)
        assertNull(vm.uiState.value.play)
    }

    @Test
    fun aSongWithItsOwnGuitarPlaysRightAway() = runTest {
        probe.audio["m"] = ChartAudio.Stems("g", listOf("s"), "s")
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
    }

    @Test
    fun aDeclinedOrSeparatedSongPlaysRightAway() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        separations.states["m"] = AiSeparationState.DECLINED
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
    }

    @Test
    fun playingWithoutSeparatingIsRememberedAndPlays() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        val vm = viewModel()
        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        vm.onPlayWithoutSeparating()
        advanceUntilIdle()

        assertEquals(AiSeparationState.DECLINED, separations.states["m"])
        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
        assertNull(vm.uiState.value.prompt)
    }

    @Test
    fun separatingStartsInTheBackgroundAndDoesNotPlay() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        val vm = viewModel()
        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        vm.onSeparate()

        assertEquals(listOf("m"), scheduler.started)
        assertNull(vm.uiState.value.prompt)
        assertNull(vm.uiState.value.play)
    }

    @Test
    fun aSecondSeparationSaysOneIsRunning() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        scheduler.start("outra", "Outra")
        val vm = viewModel()
        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        vm.onSeparate()

        assertEquals(true, vm.uiState.value.prompt?.busy)
    }

    @Test
    fun theSongBeingSeparatedPlaysWholeWithoutAsking() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        scheduler.start("m", "Música")
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
    }

    @Test
    fun aRescanPrunesSeparationsOfSongsThatLeft() = runTest {
        val vm = viewModel()
        vm.onRescan()
        advanceUntilIdle()

        assertEquals(1, separations.pruned)
    }
```
`onRescan` só importa com pasta escolhida: no teste, preparar o `folderStore` como os testes de reimportação existentes já fazem.

`LibraryContentTest.kt`:
```kotlin
    @Test
    fun theAiBadgeShowsOnSeparatedSongs() {
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(song), aiSeparated = setOf(song.id)))

        composeRule.onNodeWithText("GUITARRA SEPARADA IA").assertExists()
    }

    @Test
    fun thePromptOffersToSeparateOrPlay() {
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(song),
                prompt = SeparationAsk(song.id, song.title, Difficulty.EXPERT, lowMemory = true),
            ),
        )

        composeRule.onNodeWithText("Separar a guitarra com IA?").assertExists()
        composeRule.onNodeWithText("Pode não funcionar neste celular.", substring = true).assertExists()
        composeRule.onNodeWithText("Separar").performClick()
        composeRule.onNodeWithText("Jogar sem separar").performClick()

        assertEquals(listOf("separate", "playWithout"), events)
        assertEquals(listOf(MenuSound.CONFIRM, MenuSound.CONFIRM), sounds)
    }

    @Test
    fun choosingADifficultyGoesThroughTheViewModel() {
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(song)))

        composeRule.onNodeWithText(song.title).performClick()
        composeRule.onNodeWithText("Expert").performClick()

        assertEquals(listOf("difficulty:${song.id}:EXPERT"), events)
    }
```
O `show(...)` do teste passa a ligar `onDifficultyChosen = { c, d -> events += "difficulty:${c.id}:$d" }`, `onSeparate = { events += "separate" }` e `onPlayWithoutSeparating = { events += "playWithout" }`. O teste existente que esperava `onPlay` direto ao escolher a dificuldade passa a esperar esse evento.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :feature:library:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (`onDifficultyChosen`, `SeparationAsk`).

- [ ] **Step 3: Implementar**

`LibraryViewModel.kt`:
- o construtor ganha `private val separations: AiSeparationRepository`, `private val scheduler: SeparationScheduler` e `private val probe: ChartAudioProbe`;
- `LibraryUiState` ganha `aiSeparated: Set<String> = emptySet()`, `prompt: SeparationAsk? = null` e `play: PlayRequestLibrary? = null`;
- `private val promptState = MutableStateFlow(PromptState())` e `private val separation = combine(promptState, separations.separatedIds) { prompt, ids -> prompt to ids }`. O `combine` principal fica com cinco fluxos (`charts`, `rootUri`, `importState`, `matchSaved`, `separation`) e preenche `aiSeparated = separation.second`, `prompt = separation.first.prompt` e `play = separation.first.play`.
```kotlin
data class SeparationAsk(
    val chartId: String,
    val title: String,
    val difficulty: Difficulty,
    val lowMemory: Boolean,
    val busy: Boolean = false,
)

data class PlayRequestLibrary(val chartId: String, val difficulty: Difficulty)

private data class PromptState(val prompt: SeparationAsk? = null, val play: PlayRequestLibrary? = null)
```
```kotlin
    /** Depois da dificuldade: pergunta se a música não tem guitarra separada (nem da IA, nem recusa guardada). */
    fun onDifficultyChosen(chart: Chart, difficulty: Difficulty) {
        viewModelScope.launch {
            val ask = needsAsking(chart.id)
            promptState.value = if (ask) {
                PromptState(prompt = SeparationAsk(chart.id, chart.title, difficulty, scheduler.lowMemoryDevice))
            } else {
                PromptState(play = PlayRequestLibrary(chart.id, difficulty))
            }
        }
    }

    private suspend fun needsAsking(chartId: String): Boolean {
        val running = scheduler.status.value
        if (running is SeparationStatus.Running && running.chartId == chartId) return false
        if (separations.stateOf(chartId) != null) return false
        return probe.audioOf(chartId)?.let { it !is ChartAudio.Stems } ?: false
    }

    fun onSeparate() {
        val ask = promptState.value.prompt ?: return
        promptState.value = if (scheduler.start(ask.chartId, ask.title)) PromptState() else PromptState(ask.copy(busy = true))
    }

    fun onPlayWithoutSeparating() {
        val ask = promptState.value.prompt ?: return
        viewModelScope.launch {
            separations.decline(ask.chartId)
            promptState.value = PromptState(play = PlayRequestLibrary(ask.chartId, ask.difficulty))
        }
    }

    fun onPromptDismissed() {
        promptState.value = PromptState()
    }

    fun onPlayHandled() {
        promptState.update { it.copy(play = null) }
    }
```
Em `runImport`, depois do `importer.import(...)` dar certo: `separations.pruneOrphans()`.

`SeparationPrompt.kt`:
```kotlin
package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors

/** "Separar a guitarra com IA?" — depois da dificuldade, numa música sem guitarra separada. */
@Composable
internal fun SeparationPrompt(
    ask: SeparationAsk,
    onSeparate: () -> Unit,
    onPlayWithout: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_separate_title)) },
        text = {
            Column {
                Text(
                    stringResource(if (ask.busy) R.string.library_separate_busy else R.string.library_separate_message),
                )
                if (ask.lowMemory && !ask.busy) Text(stringResource(R.string.library_separate_low_memory))
            }
        },
        confirmButton = {
            if (!ask.busy) {
                TextButton(onClick = withMenuSound(MenuSound.CONFIRM, onSeparate)) {
                    Text(stringResource(R.string.library_separate), color = RiffColors.Paper)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = withMenuSound(MenuSound.CONFIRM, onPlayWithout)) {
                Text(stringResource(R.string.library_play_without_separating), color = RiffColors.Paper)
            }
        },
    )
}
```
Strings da Biblioteca (`values`; `values-en` em inglês):
```xml
    <string name="library_separate_title">Separar a guitarra com IA?</string>
    <string name="library_separate_message">A guitarra some quando você erra, como no Teen Spirit. Leva uns 2 minutos, usa bastante o processador e pode esquentar o celular. Fica guardado: só precisa fazer uma vez por música.</string>
    <string name="library_separate_low_memory">Pode não funcionar neste celular.</string>
    <string name="library_separate_busy">Já tem uma separação em andamento</string>
    <string name="library_separate">Separar</string>
    <string name="library_play_without_separating">Jogar sem separar</string>
    <string name="library_ai_separated">Guitarra separada IA</string>
```
`LibraryScreen.kt`:
- `LibraryScreen`:
  - liga os callbacks do ViewModel;
  - reage a `state.play` com `LaunchedEffect(state.play) { state.play?.let { onPlay(it.chartId, it.difficulty); viewModel.onPlayHandled() } }`;
  - antes de `onSeparate`, no Android 13+ pede `POST_NOTIFICATIONS` com `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` e chama `viewModel.onSeparate()` no retorno, qualquer que seja a resposta.
- `LibraryContent`:
  - o `onChoose` do `DifficultyDialog` passa a chamar `onDifficultyChosen(chart, difficulty)`;
  - com `state.prompt != null`, mostra o `SeparationPrompt`.
- `ChartList` recebe `aiSeparated`. O `badge` de cada cartão vira:
```kotlin
                badge = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        chart.difficulties.maxOrNull()?.let { difficultyBadge(it)() }
                        if (chart.id in aiSeparated) {
                            StatusBadge(stringResource(R.string.library_ai_separated), BadgeTone.SUCCESS)
                        }
                    }
                },
```
- `LibraryScreenshotTest`: acrescentar um screenshot com uma música separada pela IA e outro com a pergunta aberta. Gravar com `./scripts/gw :feature:library:recordRoborazziDebug`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :feature:library:testDebugUnitTest :feature:library:recordRoborazziDebug :app:assembleDebug detekt --console=plain`
Expected: PASS. Conferir as imagens novas (marca verde "GUITARRA SEPARADA IA" sob a dificuldade; pergunta com os dois botões).

- [ ] **Step 5: Commit**

```bash
git add feature/library
git commit -m "feat: perguntar antes de jogar uma música sem guitarra separada e marcar as separadas pela IA"
```

---

### Task 10: Configurações — "Guitarra separada por IA"

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/rodrigomachado/riff/feature/settings/SettingsViewModel.kt`, `SettingsScreen.kt`
- Modify: `feature/settings/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Modify: `feature/settings/src/test/kotlin/com/rodrigomachado/riff/feature/settings/Fakes.kt`, `SettingsViewModelTest.kt`, `SettingsContentTest.kt`, `SettingsScreenshotTest.kt`

**Interfaces:**
- Consumes: `AiSeparationRepository` (`usage`, `deleteAll`, `resetDeclines`), `SeparationUsage`.
- Produces:
  - `SettingsViewModel.separationUsage: StateFlow<SeparationUsage>`, `deleteSeparations()` e `askAgain()`;
  - `SettingsContent` ganha `separationUsage: SeparationUsage = SeparationUsage(0, 0)`, `onDeleteSeparations: () -> Unit = {}` e `onAskAgain: () -> Unit = {}`.

- [ ] **Step 1: Escrever os testes que falham**

`SettingsViewModelTest.kt`:
```kotlin
    @Test
    fun theAiSectionShowsTheUsageAndDeletesOrAsksAgain() = runTest {
        separations.usage.value = SeparationUsage(songs = 12, bytes = 96L shl 20)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.separationUsage.collect {} }

        assertEquals(SeparationUsage(12, 96L shl 20), vm.separationUsage.value)
        vm.deleteSeparations()
        vm.askAgain()
        advanceUntilIdle()

        assertEquals(listOf("deleteAll", "resetDeclines"), separations.calls)
    }
```
O fake de `Fakes.kt` (settings) é como o da Biblioteca, mas registra `calls += "deleteAll"` e `calls += "resetDeclines"`. O `viewModel()` dos testes passa a receber `separations`.

`SettingsContentTest.kt`:
```kotlin
    @Test
    fun theAiSectionDeletesAfterConfirmingAndAsksAgain() {
        show(Settings.DEFAULT, separationUsage = SeparationUsage(12, 96L shl 20))

        composeRule.onNodeWithText("Guitarra separada por IA").performScrollTo().assertExists()
        composeRule.onNodeWithText("12 músicas · 96 MB").assertExists()
        composeRule.onNodeWithText("Apagar separações").performScrollTo().performClick()
        composeRule.onNodeWithText("Apagar").performClick() // confirmação
        composeRule.onNodeWithText("Perguntar de novo").performScrollTo().performClick()

        assertEquals(listOf("delete", "askAgain"), events)
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :feature:settings:testDebugUnitTest --console=plain`
Expected: FAIL ("Unresolved reference: separationUsage").

- [ ] **Step 3: Implementar**

Strings (`values`; `values-en` em inglês):
```xml
    <string name="settings_ai_section">Guitarra separada por IA</string>
    <string name="settings_ai_usage">%1$d músicas · %2$s</string>
    <string name="settings_ai_delete">Apagar separações</string>
    <string name="settings_ai_delete_confirm">Apagar as guitarras separadas pela IA? Dá para separar de novo depois.</string>
    <string name="settings_ai_delete_yes">Apagar</string>
    <string name="settings_ai_delete_no">Cancelar</string>
    <string name="settings_ai_ask_again">Perguntar de novo</string>
```
`SettingsViewModel`:
```kotlin
    val separationUsage: StateFlow<SeparationUsage> = separations.usage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SeparationUsage(0, 0))

    fun deleteSeparations() {
        viewModelScope.launch { separations.deleteAll() }
    }

    fun askAgain() {
        viewModelScope.launch { separations.resetDeclines() }
    }
```
O construtor ganha `private val separations: AiSeparationRepository`. Se o detekt contar mais de 11 funções, a supressão `TooManyFunctions` que já existe cobre; senão, atualizar o comentário dela.

`SettingsScreen.kt`: depois da linha de calibração, `Section(stringResource(R.string.settings_ai_section))` e um `AiSeparationRows(usage, onDeleteSeparations, onAskAgain)` com:
- o texto `stringResource(R.string.settings_ai_usage, usage.songs, Formatter.formatShortFileSize(LocalContext.current, usage.bytes))`;
- "Apagar separações" (`TextButton` com som de navegar), que abre um `AlertDialog` de confirmação ("Apagar" com som de confirmar chama `onDeleteSeparations`; "Cancelar" fecha);
- "Perguntar de novo" (`TextButton` com som de confirmar).

`Formatter.formatShortFileSize` usa unidades decimais na faixa de MB; se "96 MB" sair diferente no teste, ajustar o valor do teste para o que o Formatter imprime e anotar o Ruling. `SettingsScreen` liga os três parâmetros ao ViewModel. Regravar `SettingsScreenshotTest`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :feature:settings:testDebugUnitTest :feature:settings:recordRoborazziDebug detekt --console=plain`
Expected: PASS; a imagem nova mostra a seção no fim.

- [ ] **Step 5: Commit**

```bash
git add feature/settings
git commit -m "feat: Configurações com o espaço das separações, apagar e perguntar de novo"
```

---

### Task 11: README, suíte, aparelho

- [ ] **Step 1: README**
  - Nova seção "Guitarra separada por IA" (depois de "Pesquisar e baixar mapas"):
    - a pergunta ao jogar;
    - a separação roda em segundo plano, com o ícone e a notificação;
    - ~2 min por música no S25, até ~2,8 GB de RAM enquanto roda;
    - pausa pelo calor;
    - marca GUITARRA SEPARADA IA;
    - "Jogar sem separar" fica guardado;
    - Configurações (espaço, apagar, perguntar de novo);
    - ~7 MB por música.
  - Tabela de módulos: linha de `:core:separation`.
  - Créditos: Demucs (Meta, MIT), demucs.onnx (Sevag Hanssian, MIT), ONNX Runtime (Microsoft, MIT), JTransforms (BSD-2).
  - "Como gerar o modelo": `scripts/exportar_modelo_ia.sh`.
- [ ] **Step 2: suíte completa**
  - Rodar `./scripts/gw --stop; ./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --console=plain` e depois `scripts/conferir_r8_spotify.sh`.
  - Conferir o tamanho do APK de release: `ls -la app/build/outputs/apk/release/` (esperado ~+150 MB).
- [ ] **Step 3: no aparelho, com o release instalado**
  - Baixar pelo app uma música do Encore sem guitarra separada, tocar, escolher a dificuldade e tocar em **Separar**. Aceitar a notificação.
  - Conferir o ícone com progresso, o painel com % e tempo, e a notificação. Saber o tempo total (meta: ~2 min para 3:30).
  - Conferir que a temperatura não passa de ~42 °C: `dumpsys battery`.
  - Ao fim, conferir a marca na Biblioteca e jogar: a guitarra corta no erro.
  - Em outra música, testar **Cancelar** e **Jogar sem separar** (que não pergunta de novo).
  - Nas Configurações, conferir o espaço, **Apagar separações** e **Perguntar de novo**.
- [ ] **Step 4: Baseline Profile** — o caminho da Biblioteca mudou: regerar com `scripts/desempenho.sh perfil` e reinstalar o release.
- [ ] **Step 5: Commit** `docs: README da guitarra separada por IA`.
