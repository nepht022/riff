# Riff: integração com o Spotify — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jogar junto da música que está tocando no Spotify: conectar pelo App Remote SDK, mostrar a faixa atual com o mapa casado na tela "Tocando agora", confirmar ou escolher o mapa, e rodar a partida sincronizada com o Spotify, mantendo o modo com o áudio da pasta na biblioteca.

**Architecture:** `:core:spotify` embrulha o SDK (`SpotifyRemote`, com `StateFlow` de conexão e de estado do player) e implementa `SongPlayer` (`SpotifySongPlayer`), de modo que a `GameSession` atual roda sem mudança com o Spotify no lugar do `MediaPlayer`. `:core:data` casa a faixa com os mapas (`TrackMatchRepository`) e expõe `NowPlayingRepository`. `:feature:nowplaying` é a nova tela inicial; a biblioteca ganha o modo "escolher mapa para esta música"; o gameplay escolhe o player pela rota e acompanha pausas feitas fora do jogo.

**Tech Stack:** Spotify App Remote SDK 0.8.0 (AAR num repositório Maven local em `third_party/maven`), Gson 2.14.0, Kotlin coroutines/Flow, Hilt (qualificadores `@LocalSong` e `@SpotifySong`), Room, Compose, Navigation type-safe, JUnit 4 + Robolectric + Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-05-riff-design.md` (§2 MVP item 1, §3, §4 Sincronização, §5 Casamento, §6, §7, §8, §11 passo 6) e `docs/superpowers/specs/2026-10-07-identidade-visual-design.md` (componentes da tela nova).

## Global Constraints

- minSdk 26; compileSdk 37; targetSdk 36; Kotlin 2.4.20; AGP 9.4.1; Gradle via `./scripts/gw` (Windows a partir do WSL).
- Client ID e redirect URI em `local.properties` (`spotify.clientId`, `spotify.redirectUri`), lidos via `BuildConfig`, nunca no repositório. Sem `spotify.clientId` o build funciona e a tela mostra "Spotify não configurado" (CI sem credenciais, spec §10).
- Redirect URI: `riff://callback`. Package: `com.rodrigomachado.riff`.
- Dependências permitidas: `feature → core:data, core:ui, core:model`; `core:data → core:database, core:datastore, core:spotify, core:chart, core:model`; `:feature:gameplay` também depende de `:core:game` e `:core:audio`. Uma feature nunca depende de outra feature.
- Sincronização (spec §4): releitura do estado a cada 2 s; desvio < 80 ms corrigido aos poucos, maior aplicado de uma vez (`SongClock` já faz isso).
- Casamento (spec §5): ≥ 0,92 automático; 0,75 a 0,92 sugere e pede confirmação; abaixo disso escolha manual. Casamento manual ou confirmado é salvo em `track_matches` e tem prioridade.
- Spotify desconectado no meio da partida: o jogo pausa e reconecta com backoff (spec §7).
- Strings em resources, pt-BR padrão e en segunda língua. Alvos de toque de 48dp.
- Identidade "Pôster de turnê": só componentes de `:core:ui` (`PosterTitle`, `PosterButton`, `TrackCard`, `StatusBadge`, estados), nada de assets de terceiros.
- Decisão do usuário (2026-10-08, opção a): o modo com o áudio da pasta continua na biblioteca ("Jogar"), além do modo Spotify.
- Detekt e Lint sem erros; testes Robolectric com `sdk=35` (`robolectric.properties`).

## Review Focus

1. **Spotify cai no meio da partida (app do Spotify fechado, conexão perdida):** o jogo pausa sem crash, guarda a posição e a reconexão com backoff retoma sem o jogador perder a partida. Testes: `SpotifySongPlayerTest.losingTheConnectionPausesAndKeepsThePosition` e `ReconnectPolicyTest` (Tasks 2 e 3).
2. **O jogador pausa, retoma ou pula para outro ponto pela notificação do Spotify:** a tela do jogo mostra "Pausado" e libera as trilhas; ao retomar fora do jogo, a partida continua; um salto na barra é aplicado de uma vez. Testes: `GameplayViewModelTest.pausedOutsideTheGameShowsThePauseScreenAndReleasesTheLanes`, `GameStagePlaybackTest` e `SpotifySongPlayerTest.positionIsExtrapolatedAndFrozenWhenPaused` (Tasks 3 e 7).
3. **A faixa muda (fim natural ou pulo):** a partida termina, o resultado é salvo e "Jogar de novo" volta a usar o Spotify. Testes: `SpotifySongPlayerTest.trackChangeAfterOurTrackCompletesTheSong` (Task 3) e a navegação na Task 11.
4. **Spotify não instalado, não configurado ou sem login:** a tela explica o motivo e a biblioteca (modo áudio da pasta) continua acessível. Teste: `NowPlayingContentTest` (Task 10).
5. **Mapa apagado depois de um casamento manual (reimportação):** o casamento salvo some junto (cascade) e o app volta ao casamento automático, sem crash. Teste: `RoomTrackMatchRepositoryTest.removedChartDropsItsSavedMatch` (Task 4).

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
|---|---|
| `third_party/maven/com/spotify/android/spotify-app-remote/0.8.0/*` | AAR oficial do Spotify e POM mínimo |
| `third_party/README.md` | Origem, versão, SHA-256 e licença do AAR |
| `core/model/.../SpotifyConnection.kt` | `SpotifyConnection`, `SpotifyError` |
| `core/model/.../NowPlaying.kt` | `NowPlaying`, `MatchStatus` |
| `core/spotify/.../SpotifyMappers.kt` | `PlayerState → PlaybackState`, `Throwable → SpotifyError` |
| `core/spotify/.../SpotifyRemote.kt` | Interface do Spotify usada pelo resto do app |
| `core/spotify/.../SpotifyConfig.kt` | Client ID e redirect URI |
| `core/spotify/.../ReconnectPolicy.kt` | Backoff da reconexão |
| `core/spotify/.../AppRemoteSpotify.kt` | Implementação com o SDK (testada à mão na Task 12) |
| `core/spotify/.../SpotifySongPlayer.kt` | `SongPlayer` sobre o Spotify |
| `core/spotify/.../SpotifyModule.kt` | Hilt |
| `core/audio/.../SongSources.kt` | Qualificadores `@LocalSong`, `@SpotifySong` |
| `core/data/.../TrackMatchRepository.kt` | Casamento salvo ou automático |
| `core/data/.../NowPlayingRepository.kt` | Faixa atual + casamento |
| `core/ui/.../component/DifficultyDialog.kt` | Diálogo de dificuldade (biblioteca e "Tocando agora") |
| `feature/library/...` | Modo "escolher mapa para esta música" |
| `feature/gameplay/...` | Rota com faixa do Spotify, player por rota, pausa vinda de fora |
| `feature/results/...` | "Voltar" para quem abriu a partida |
| `feature/nowplaying/...` | Tela "Tocando agora" |
| `app/.../RiffNavHost.kt` | Navegação nova, começando em "Tocando agora" |

---

### Task 1: SDK do Spotify, módulo `:core:spotify` e tradução de estados e erros

**Files:**
- Create: `third_party/maven/com/spotify/android/spotify-app-remote/0.8.0/spotify-app-remote-0.8.0.aar` (download)
- Create: `third_party/maven/com/spotify/android/spotify-app-remote/0.8.0/spotify-app-remote-0.8.0.pom`
- Create: `third_party/README.md`
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/SpotifyConnection.kt`
- Create: `core/spotify/build.gradle.kts`, `core/spotify/src/main/AndroidManifest.xml`
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/SpotifyMappers.kt`
- Test: `core/spotify/src/test/kotlin/com/rodrigomachado/riff/core/spotify/SpotifyMappersTest.kt`

**Interfaces:**
- Consumes: `PlaybackState(trackUri, title, artist, durationMs, positionMs, isPaused, capturedAtElapsedMs)` (`:core:model`).
- Produces: `enum class SpotifyError { NOT_CONFIGURED, NOT_INSTALLED, NOT_LOGGED_IN, NOT_AUTHORIZED, OFFLINE, DISCONNECTED, UNKNOWN }`; `sealed interface SpotifyConnection { Idle; Connecting; Connected; Failed(error: SpotifyError) }`; `internal fun PlayerState.toPlaybackState(capturedAtMs: Long): PlaybackState?`; `internal fun Throwable.toSpotifyError(): SpotifyError`; módulo `:core:spotify` com `BuildConfig.SPOTIFY_CLIENT_ID` e `BuildConfig.SPOTIFY_REDIRECT_URI`; aliases `libs.spotify.app.remote` e `libs.gson`.

- [ ] **Step 1: Baixar o AAR e conferir o hash**

```bash
D=third_party/maven/com/spotify/android/spotify-app-remote/0.8.0
mkdir -p "$D"
curl -sL -o "$D/spotify-app-remote-0.8.0.aar" \
  https://github.com/spotify/android-sdk/releases/download/v0.8.0-appremote_v2.1.0-auth/spotify-app-remote-release-0.8.0.aar
sha256sum "$D/spotify-app-remote-0.8.0.aar"
```
Expected: `b5a6dd880eaf01f63a871cba9ef7af77c341f8a94ffc8fdf2e9021f9a9d4c198`. Hash diferente: pare e avise (o arquivo não é o que foi inspecionado no planejamento).

`$D/spotify-app-remote-0.8.0.pom`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.spotify.android</groupId>
  <artifactId>spotify-app-remote</artifactId>
  <version>0.8.0</version>
  <packaging>aar</packaging>
</project>
```

`third_party/README.md`:
```markdown
# Dependências de terceiros versionadas

## Spotify App Remote SDK 0.8.0

- Origem: https://github.com/spotify/android-sdk/releases/tag/v0.8.0-appremote_v2.1.0-auth (`spotify-app-remote-release-0.8.0.aar`)
- SHA-256: `b5a6dd880eaf01f63a871cba9ef7af77c341f8a94ffc8fdf2e9021f9a9d4c198`
- Licença: Apache 2.0 (cabeçalho do `AndroidManifest.xml` do AAR)
- Por que aqui: o Spotify publica o App Remote só no GitHub, fora do Maven. Um repositório Maven local evita a dependência direta de `.aar`, que o AGP não aceita em módulos de biblioteca.
- O SDK usa o Gson em tempo de execução; a versão fica no version catalog (`gson`).
```

- [ ] **Step 2: Repositório local, catálogo e modelo**

Em `settings.gradle.kts`, dentro de `dependencyResolutionManagement { repositories { ... } }`, depois de `mavenCentral()`:
```kotlin
        // AAR do Spotify App Remote (não está no Maven): veja third_party/README.md
        maven {
            url = uri("third_party/maven")
            content { includeGroup("com.spotify.android") }
        }
```
E acrescente `include(":core:spotify")` depois de `include(":core:audio")`.

Em `gradle/libs.versions.toml`, em `[versions]`:
```toml
spotifyAppRemote = "0.8.0"
gson = "2.14.0"
```
Em `[libraries]`:
```toml
spotify-app-remote = { module = "com.spotify.android:spotify-app-remote", version.ref = "spotifyAppRemote" }
gson = { module = "com.google.code.gson:gson", version.ref = "gson" }
```

`core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/SpotifyConnection.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

/** Por que o Spotify não está disponível. Cada valor vira uma explicação na tela. */
enum class SpotifyError { NOT_CONFIGURED, NOT_INSTALLED, NOT_LOGGED_IN, NOT_AUTHORIZED, OFFLINE, DISCONNECTED, UNKNOWN }

/** Estado da conexão com o app do Spotify. */
sealed interface SpotifyConnection {
    data object Idle : SpotifyConnection

    data object Connecting : SpotifyConnection

    data object Connected : SpotifyConnection

    data class Failed(val error: SpotifyError) : SpotifyConnection
}
```

- [ ] **Step 3: Módulo `:core:spotify`**

`core/spotify/build.gradle.kts`:
```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
}

// Credenciais do Spotify ficam no local.properties (fora do git). Sem elas o build segue e a tela avisa.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.rodrigomachado.riff.core.spotify"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${localProperties.getProperty("spotify.clientId", "")}\"")
        buildConfigField(
            "String",
            "SPOTIFY_REDIRECT_URI",
            "\"${localProperties.getProperty("spotify.redirectUri", "riff://callback")}\"",
        )
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:audio"))
    implementation(libs.spotify.app.remote)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`core/spotify/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 4: Escrever o teste que falha**

`core/spotify/src/test/kotlin/com/rodrigomachado/riff/core/spotify/SpotifyMappersTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyError
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.OfflineModeException
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.types.Artist
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyMappersTest {
    private fun track(isEpisode: Boolean = false) = Track(
        Artist("DragonForce", "spotify:artist:1"),
        listOf(Artist("DragonForce", "spotify:artist:1")),
        null,
        441_000,
        "Through the Fire and Flames",
        "spotify:track:ttfaf",
        null,
        isEpisode,
        false,
    )

    private fun state(track: Track?, isPaused: Boolean = false) = PlayerState(track, isPaused, 1f, 12_000, null, null)

    @Test
    fun playerStateBecomesPlaybackStateWithTheCaptureTime() {
        assertEquals(
            PlaybackState(
                trackUri = "spotify:track:ttfaf",
                title = "Through the Fire and Flames",
                artist = "DragonForce",
                durationMs = 441_000,
                positionMs = 12_000,
                isPaused = true,
                capturedAtElapsedMs = 500,
            ),
            state(track(), isPaused = true).toPlaybackState(capturedAtMs = 500),
        )
    }

    @Test
    fun nothingPlayingOrAPodcastIsNotATrackToPlay() {
        assertNull(state(null).toPlaybackState(0))
        assertNull(state(track(isEpisode = true)).toPlaybackState(0))
    }

    @Test
    fun sdkErrorsBecomeReasonsTheScreenCanExplain() {
        val cause = RuntimeException()
        assertEquals(SpotifyError.NOT_INSTALLED, CouldNotFindSpotifyApp().toSpotifyError())
        assertEquals(SpotifyError.NOT_LOGGED_IN, NotLoggedInException("", cause).toSpotifyError())
        assertEquals(SpotifyError.NOT_AUTHORIZED, UserNotAuthorizedException("", cause).toSpotifyError())
        assertEquals(SpotifyError.NOT_AUTHORIZED, AuthenticationFailedException("", cause).toSpotifyError())
        assertEquals(SpotifyError.OFFLINE, OfflineModeException("", cause).toSpotifyError())
        assertEquals(SpotifyError.DISCONNECTED, SpotifyDisconnectedException().toSpotifyError())
        assertEquals(SpotifyError.DISCONNECTED, SpotifyConnectionTerminatedException().toSpotifyError())
        assertEquals(SpotifyError.UNKNOWN, IllegalStateException().toSpotifyError())
    }
}
```

- [ ] **Step 5: Rodar e ver falhar**

Run: `./scripts/gw :core:spotify:testDebugUnitTest --console=plain`
Expected: FAIL na compilação com `Unresolved reference 'toPlaybackState'` (e `toSpotifyError`). Se falhar antes, ao resolver `com.spotify.android:spotify-app-remote`, o repositório local está errado: corrija antes de seguir.

- [ ] **Step 6: Implementar**

`core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/SpotifyMappers.kt`:
```kotlin
package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyError
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.OfflineModeException
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.types.PlayerState

/** Estado do player como o app usa. Sem faixa, ou com podcast, não há o que jogar: `null`. */
internal fun PlayerState.toPlaybackState(capturedAtMs: Long): PlaybackState? {
    val current = track ?: return null
    if (current.isEpisode || current.isPodcast) return null
    return PlaybackState(
        trackUri = current.uri,
        title = current.name.orEmpty(),
        artist = current.artist?.name.orEmpty(),
        durationMs = current.duration,
        positionMs = playbackPosition,
        isPaused = isPaused,
        capturedAtElapsedMs = capturedAtMs,
    )
}

internal fun Throwable.toSpotifyError(): SpotifyError = when (this) {
    is CouldNotFindSpotifyApp -> SpotifyError.NOT_INSTALLED
    is NotLoggedInException -> SpotifyError.NOT_LOGGED_IN
    is UserNotAuthorizedException, is AuthenticationFailedException -> SpotifyError.NOT_AUTHORIZED
    is OfflineModeException -> SpotifyError.OFFLINE
    is SpotifyDisconnectedException, is SpotifyConnectionTerminatedException -> SpotifyError.DISCONNECTED
    else -> SpotifyError.UNKNOWN
}
```

- [ ] **Step 7: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :core:spotify:testDebugUnitTest :core:model:test --console=plain`
Expected: `BUILD SUCCESSFUL`, `SpotifyMappersTest` 3/3.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(spotify): SDK App Remote em repositório local e tradução de estados e erros" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `SpotifyRemote`, configuração, reconexão com backoff e a implementação com o SDK

**Files:**
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/SpotifyRemote.kt`
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/SpotifyConfig.kt`
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/ReconnectPolicy.kt`
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/AppRemoteSpotify.kt`
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/SpotifyModule.kt`
- Test: `core/spotify/src/test/kotlin/com/rodrigomachado/riff/core/spotify/ReconnectPolicyTest.kt`

**Interfaces:**
- Consumes: `toPlaybackState`, `toSpotifyError`, `SpotifyConnection`, `SpotifyError` (Task 1).
- Produces:
  ```kotlin
  interface SpotifyRemote {
      val connection: StateFlow<SpotifyConnection>
      val playback: StateFlow<PlaybackState?>
      fun connect()
      suspend fun refresh(): PlaybackState?
      fun play(trackUri: String)
      fun pause()
      fun resume()
  }
  class SpotifyConfig(val clientId: String, val redirectUri: String) { val isConfigured: Boolean }
  object ReconnectPolicy { fun shouldRetry(error: SpotifyError): Boolean; fun delayMs(attempt: Int): Long }
  ```
  Binding Hilt `SpotifyRemote → AppRemoteSpotify` (singleton).

- [ ] **Step 1: Escrever o teste que falha**

`core/spotify/src/test/kotlin/com/rodrigomachado/riff/core/spotify/ReconnectPolicyTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.SpotifyError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun onlyALostConnectionIsRetriedAutomatically() {
        assertTrue(ReconnectPolicy.shouldRetry(SpotifyError.DISCONNECTED))
        SpotifyError.entries.filter { it != SpotifyError.DISCONNECTED }.forEach {
            assertFalse("$it precisa do usuário", ReconnectPolicy.shouldRetry(it))
        }
    }

    @Test
    fun waitDoublesUpToThirtySeconds() {
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            (0..6).map(ReconnectPolicy::delayMs),
        )
    }

    @Test
    fun configWithoutClientIdIsNotConfigured() {
        assertFalse(SpotifyConfig(clientId = " ", redirectUri = "riff://callback").isConfigured)
        assertTrue(SpotifyConfig(clientId = "abc", redirectUri = "riff://callback").isConfigured)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:spotify:testDebugUnitTest --console=plain`
Expected: FAIL na compilação com `Unresolved reference 'ReconnectPolicy'` e `'SpotifyConfig'`.

- [ ] **Step 3: Implementar**

`SpotifyRemote.kt`:
```kotlin
package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import kotlinx.coroutines.flow.StateFlow

/** O app do Spotify, visto pelo Riff. Comandos são assíncronos; o efeito chega em [playback]. */
interface SpotifyRemote {
    val connection: StateFlow<SpotifyConnection>

    /** Último estado do player; `null` sem conexão ou sem uma faixa de música. */
    val playback: StateFlow<PlaybackState?>

    /** Conecta. Sem efeito se já estiver conectado ou conectando. */
    fun connect()

    /** Relê o estado agora (spec §4: releitura a cada 2 s). */
    suspend fun refresh(): PlaybackState?

    /** Toca [trackUri] do começo. */
    fun play(trackUri: String)

    fun pause()

    fun resume()
}
```

`SpotifyConfig.kt`:
```kotlin
package com.rodrigomachado.riff.core.spotify

/** Credenciais do app no Developer Dashboard, vindas do local.properties via BuildConfig. */
class SpotifyConfig(val clientId: String, val redirectUri: String) {
    val isConfigured: Boolean get() = clientId.isNotBlank()
}
```

`ReconnectPolicy.kt`:
```kotlin
package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.SpotifyError

/** Reconexão com backoff (spec §7). Erros de configuração, login ou autorização dependem do usuário. */
object ReconnectPolicy {
    private val DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)

    fun shouldRetry(error: SpotifyError): Boolean = error == SpotifyError.DISCONNECTED

    fun delayMs(attempt: Int): Long = DELAYS_MS[attempt.coerceIn(0, DELAYS_MS.lastIndex)]
}
```

`AppRemoteSpotify.kt` (cola com o SDK; validada à mão na Task 12):
```kotlin
package com.rodrigomachado.riff.core.spotify

import android.content.Context
import android.os.SystemClock
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** [SpotifyRemote] com o App Remote SDK. Uma conexão por processo; reconecta sozinho se ela cair. */
@Singleton
class AppRemoteSpotify @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: SpotifyConfig,
) : SpotifyRemote {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectionState = MutableStateFlow<SpotifyConnection>(SpotifyConnection.Idle)
    private val playbackState = MutableStateFlow<PlaybackState?>(null)
    private var remote: SpotifyAppRemote? = null
    private var subscription: Subscription<PlayerState>? = null
    private var reconnectAttempt = 0

    override val connection: StateFlow<SpotifyConnection> = connectionState.asStateFlow()
    override val playback: StateFlow<PlaybackState?> = playbackState.asStateFlow()

    override fun connect() {
        val current = connectionState.value
        if (current == SpotifyConnection.Connecting || current == SpotifyConnection.Connected) return
        when {
            !config.isConfigured -> connectionState.value = SpotifyConnection.Failed(SpotifyError.NOT_CONFIGURED)
            !SpotifyAppRemote.isSpotifyInstalled(context) ->
                connectionState.value = SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED)
            else -> startConnecting()
        }
    }

    private fun startConnecting() {
        connectionState.value = SpotifyConnection.Connecting
        val params = ConnectionParams.Builder(config.clientId)
            .setRedirectUri(config.redirectUri)
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(
            context,
            params,
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    remote = appRemote
                    reconnectAttempt = 0
                    connectionState.value = SpotifyConnection.Connected
                    subscribe(appRemote)
                }

                override fun onFailure(error: Throwable) = onLost(error.toSpotifyError())
            },
        )
    }

    private fun subscribe(appRemote: SpotifyAppRemote) {
        subscription?.cancel()
        subscription = appRemote.playerApi.subscribeToPlayerState()
            .setEventCallback { state -> playbackState.value = state.toPlaybackState(SystemClock.elapsedRealtime()) }
            .apply { setErrorCallback { error -> onLost(error.toSpotifyError()) } }
    }

    private fun onLost(error: SpotifyError) {
        subscription?.cancel()
        subscription = null
        remote?.let(SpotifyAppRemote::disconnect)
        remote = null
        playbackState.value = null
        connectionState.value = SpotifyConnection.Failed(error)
        if (ReconnectPolicy.shouldRetry(error)) {
            val wait = ReconnectPolicy.delayMs(reconnectAttempt++)
            scope.launch {
                delay(wait)
                if (connectionState.value is SpotifyConnection.Failed) connect()
            }
        }
    }

    override suspend fun refresh(): PlaybackState? {
        val appRemote = remote ?: return null
        return suspendCancellableCoroutine { continuation ->
            appRemote.playerApi.playerState
                .setResultCallback { state ->
                    val playback = state.toPlaybackState(SystemClock.elapsedRealtime())
                    playbackState.value = playback
                    if (continuation.isActive) continuation.resume(playback)
                }
                .setErrorCallback { if (continuation.isActive) continuation.resume(null) }
        }
    }

    override fun play(trackUri: String) {
        remote?.playerApi?.play(trackUri)
    }

    override fun pause() {
        remote?.playerApi?.pause()
    }

    override fun resume() {
        remote?.playerApi?.resume()
    }
}
```

`SpotifyModule.kt`:
```kotlin
package com.rodrigomachado.riff.core.spotify

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SpotifyBindsModule {
    @Binds
    abstract fun spotifyRemote(impl: AppRemoteSpotify): SpotifyRemote
}

@Module
@InstallIn(SingletonComponent::class)
object SpotifyProvidesModule {
    @Provides
    fun config(): SpotifyConfig = SpotifyConfig(BuildConfig.SPOTIFY_CLIENT_ID, BuildConfig.SPOTIFY_REDIRECT_URI)
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :core:spotify:testDebugUnitTest :core:spotify:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`, `ReconnectPolicyTest` 3/3 e `SpotifyMappersTest` 3/3.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(spotify): conexão com o App Remote e reconexão com backoff" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `SpotifySongPlayer` e qualificadores de player

**Files:**
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/SongSources.kt`
- Modify: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/AudioModule.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModel.kt` (só o qualificador)
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/SpotifySongPlayer.kt`
- Modify: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/SpotifyModule.kt`
- Test: `core/spotify/src/test/kotlin/com/rodrigomachado/riff/core/spotify/FakeSpotifyRemote.kt`
- Test: `core/spotify/src/test/kotlin/com/rodrigomachado/riff/core/spotify/SpotifySongPlayerTest.kt`

**Interfaces:**
- Consumes: `SongPlayer`, `SongPlayerFactory` (`:core:audio`); `SpotifyRemote` (Task 2).
- Produces: `@LocalSong` e `@SpotifySong` (`com.rodrigomachado.riff.core.audio`); `class SpotifySongPlayer(remote: SpotifyRemote, scope: CoroutineScope, nowMs: () -> Long) : SongPlayer` com `companion object { REFRESH_INTERVAL_MS = 2_000L; CONNECT_TIMEOUT_MS = 15_000L; END_TOLERANCE_MS = 1_500L }`; bindings Hilt `@LocalSong SongPlayerFactory` (MediaPlayer) e `@SpotifySong SongPlayerFactory` (Spotify).

**Comportamento do `SpotifySongPlayer`:**
- `prepare(uri)`: conecta e espera `Connected` (até 15 s); se não conectar, `IOException`. Guarda a faixa e começa a ouvir `playback` e a reler o estado a cada 2 s.
- Primeiro `play()`: toca a faixa do começo (`remote.play(uri)`); os seguintes: `remote.resume()`. `pause()` antes de começar não mexe no Spotify (a música que o usuário ouvia não é pausada à toa).
- `play`/`pause` valem na hora (estado otimista): a tela não pode ver "pausado" no instante seguinte a "Continuar" só porque o evento do Spotify ainda não chegou.
- Estados recebidos antes do último comando são ignorados (são a foto de antes do comando).
- Posição: extrapolada do último estado (`positionMs + (agora − capturedAt)`), congelada quando pausado.
- Sem estado (`null`, conexão caiu): fica pausado na posição atual.
- Fim: a faixa trocou depois de a nossa ter tocado, ou pausou a menos de 1,5 s do fim.
- `release()`: para de ouvir; se a partida não terminou e a música está tocando, pausa o Spotify (sair no meio para a música, como no modo local).

- [ ] **Step 1: Escrever o teste que falha**

`core/spotify/src/test/kotlin/com/rodrigomachado/riff/core/spotify/FakeSpotifyRemote.kt`:
```kotlin
package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSpotifyRemote : SpotifyRemote {
    var connectTo: SpotifyConnection = SpotifyConnection.Connected
    val commands = mutableListOf<String>()
    var refreshes = 0
    override val connection = MutableStateFlow<SpotifyConnection>(SpotifyConnection.Idle)
    override val playback = MutableStateFlow<PlaybackState?>(null)

    override fun connect() {
        connection.value = connectTo
    }

    override suspend fun refresh(): PlaybackState? {
        refreshes++
        return playback.value
    }

    override fun play(trackUri: String) {
        commands += "play:$trackUri"
    }

    override fun pause() {
        commands += "pause"
    }

    override fun resume() {
        commands += "resume"
    }
}
```

`core/spotify/src/test/kotlin/com/rodrigomachado/riff/core/spotify/SpotifySongPlayerTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SpotifySongPlayerTest {
    private val remote = FakeSpotifyRemote()
    private var now = 1_000L
    private val ours = "spotify:track:nossa"

    private fun state(position: Long, isPaused: Boolean = false, uri: String = ours, duration: Long = 200_000) =
        PlaybackState(uri, "Música", "Banda", duration, position, isPaused, capturedAtElapsedMs = now)

    private suspend fun TestScope.prepared(): SpotifySongPlayer {
        val player = SpotifySongPlayer(remote, backgroundScope) { now }
        player.prepare(ours)
        runCurrent()
        return player
    }

    private fun TestScope.emit(state: PlaybackState?) {
        remote.playback.value = state
        runCurrent()
    }

    @Test
    fun prepareFailsWhenSpotifyIsUnavailable() = runTest {
        remote.connectTo = SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED)
        val player = SpotifySongPlayer(remote, backgroundScope) { now }

        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { player.prepare(ours) } }
    }

    @Test
    fun firstPlayStartsTheTrackFromTheBeginningLaterOnesResume() = runTest {
        val player = prepared()

        player.pause() // antes de começar: não mexe na música do usuário
        player.play()
        player.pause()
        player.play()

        assertEquals(listOf("play:$ours", "pause", "resume"), remote.commands)
    }

    @Test
    fun playAndPauseApplyAtOnceBeforeSpotifyConfirms() = runTest {
        val player = prepared()

        player.play()
        assertTrue(player.isPlaying)
        now += 300
        assertEquals(300L, player.positionMs)

        player.pause()
        assertFalse(player.isPlaying)
        now += 5_000
        assertEquals(300L, player.positionMs)
    }

    @Test
    fun positionIsExtrapolatedAndFrozenWhenPaused() = runTest {
        val player = prepared()
        player.play()

        now += 100
        emit(state(position = 10_000))
        now += 500
        assertEquals(10_500L, player.positionMs)

        emit(state(position = 60_000)) // o jogador pulou na barra: vale na hora
        assertEquals(60_000L, player.positionMs)

        emit(state(position = 61_000, isPaused = true))
        now += 2_000
        assertEquals(61_000L, player.positionMs)
        assertFalse(player.isPlaying)
    }

    @Test
    fun statesFromBeforeTheLastCommandAreIgnored() = runTest {
        val player = prepared()
        val beforePlay = state(position = 90_000) // a música estava no meio quando o jogador apertou Jogar
        now += 100
        player.play()

        emit(beforePlay)

        assertEquals(0L, player.positionMs)
    }

    @Test
    fun trackChangeAfterOurTrackCompletesTheSong() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 1_000, uri = "spotify:track:outra")) // ainda não trocou para a nossa
        assertFalse(player.isCompleted)

        emit(state(position = 2_000))
        emit(state(position = 0, uri = "spotify:track:proxima"))

        assertTrue(player.isCompleted)
    }

    @Test
    fun pausingAtTheEndCompletesTheSong() = runTest {
        val player = prepared()
        player.play()
        now += 100

        emit(state(position = 199_000, isPaused = true, duration = 200_000))

        assertTrue(player.isCompleted)
    }

    @Test
    fun losingTheConnectionPausesAndKeepsThePosition() = runTest {
        val player = prepared()
        player.play()
        now += 100
        emit(state(position = 30_000))
        now += 400

        emit(null)
        now += 10_000

        assertFalse(player.isPlaying)
        assertEquals(30_400L, player.positionMs)
    }

    @Test
    fun rereadsTheStateEveryTwoSeconds() = runTest {
        prepared()

        advanceTimeBy(SpotifySongPlayer.REFRESH_INTERVAL_MS * 2 + 1)

        assertEquals(2, remote.refreshes)
    }

    @Test
    fun leavingMidSongPausesSpotifyButFinishingDoesNot() = runTest {
        val leaving = prepared()
        leaving.play()
        leaving.release()
        assertEquals(listOf("play:$ours", "pause"), remote.commands)

        remote.commands.clear()
        val finished = prepared()
        finished.play()
        now += 100
        emit(state(position = 2_000))
        emit(state(position = 0, uri = "spotify:track:proxima"))
        finished.release()
        assertEquals(listOf("play:$ours"), remote.commands)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:spotify:testDebugUnitTest --console=plain`
Expected: FAIL na compilação com `Unresolved reference 'SpotifySongPlayer'`.

- [ ] **Step 3: Implementar**

`core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/SongSources.kt`:
```kotlin
package com.rodrigomachado.riff.core.audio

import javax.inject.Qualifier

/** Música da pasta do mapa (MediaPlayer). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalSong

/** Música tocando no Spotify. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SpotifySong
```

Em `AudioModule.kt`, anote o provider de música: `@Provides @LocalSong fun songPlayers(...)`.

Em `GameplayViewModel.kt`, o parâmetro do construtor passa a ser `@LocalSong private val songPlayers: SongPlayerFactory,` (import `com.rodrigomachado.riff.core.audio.LocalSong`). Sem isso o Hilt não acha um `SongPlayerFactory` sem qualificador.

`core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/SpotifySongPlayer.kt`:
```kotlin
package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * A música da partida tocando no Spotify. O primeiro [play] toca a faixa do começo; os seguintes retomam.
 * Comandos valem na hora (estado otimista) e os eventos do Spotify confirmam ou corrigem; estados de
 * antes do último comando são descartados. A posição é extrapolada do último estado.
 */
class SpotifySongPlayer(
    private val remote: SpotifyRemote,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) : SongPlayer {
    private var trackUri: String? = null
    private var started = false
    private var sawOurTrack = false
    private var completed = false
    private var lastCommandAtMs = Long.MIN_VALUE
    private var last: PlaybackState? = null
    private var listening: Job? = null

    override suspend fun prepare(uri: String) {
        remote.connect()
        val connection = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            remote.connection.first { it == SpotifyConnection.Connected || it is SpotifyConnection.Failed }
        }
        if (connection != SpotifyConnection.Connected) throw IOException("Spotify indisponível: $connection")
        trackUri = uri
        listening = scope.launch {
            launch { remote.playback.collect(::onState) }
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                remote.refresh()
            }
        }
    }

    private fun onState(state: PlaybackState?) {
        if (!started) return
        if (state == null) {
            last = last?.frozenAt(positionMs)
            return
        }
        if (state.capturedAtElapsedMs < lastCommandAtMs) return
        if (state.trackUri != trackUri) {
            if (sawOurTrack) completed = true
            return
        }
        sawOurTrack = true
        last = state
        if (state.isPaused && state.positionMs >= state.durationMs - END_TOLERANCE_MS) completed = true
    }

    override fun play() {
        val uri = trackUri ?: return
        val now = nowMs()
        lastCommandAtMs = now
        if (!started) {
            started = true
            remote.play(uri)
            last = PlaybackState(uri, "", "", 0, 0, isPaused = false, capturedAtElapsedMs = now)
        } else {
            remote.resume()
            last = last?.copy(isPaused = false, capturedAtElapsedMs = now)
        }
    }

    override fun pause() {
        if (!started) return
        val position = positionMs
        lastCommandAtMs = nowMs()
        remote.pause()
        last = last?.frozenAt(position)
    }

    override fun release() {
        listening?.cancel()
        listening = null
        if (started && !completed && isPlaying) remote.pause()
    }

    override val positionMs: Long
        get() {
            val state = last ?: return 0
            return if (state.isPaused) state.positionMs else state.positionMs + (nowMs() - state.capturedAtElapsedMs)
        }

    override val isPlaying: Boolean get() = last?.isPaused == false

    override val isCompleted: Boolean get() = completed

    private fun PlaybackState.frozenAt(position: Long) =
        copy(positionMs = position, isPaused = true, capturedAtElapsedMs = nowMs())

    companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val END_TOLERANCE_MS = 1_500L
    }
}
```

Em `SpotifyModule.kt`, no `SpotifyProvidesModule`:
```kotlin
    @Provides
    @SpotifySong
    fun spotifySongPlayers(remote: SpotifyRemote): SongPlayerFactory = SongPlayerFactory {
        SpotifySongPlayer(remote, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), SystemClock::elapsedRealtime)
    }
```
(imports: `android.os.SystemClock`, `com.rodrigomachado.riff.core.audio.SongPlayerFactory`, `com.rodrigomachado.riff.core.audio.SpotifySong`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.SupervisorJob`.)

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :core:spotify:testDebugUnitTest :feature:gameplay:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; `SpotifySongPlayerTest` 10/10; gameplay sem regressão.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(spotify): player da partida sobre o Spotify" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Casamento salvo ou automático e "tocando agora" em `:core:data`

**Files:**
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/NowPlaying.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/TrackMatchRepository.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/NowPlayingRepository.kt`
- Modify: `core/data/build.gradle.kts`, `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/DataModule.kt`
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/RoomTrackMatchRepositoryTest.kt`
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/SpotifyNowPlayingRepositoryTest.kt`

**Interfaces:**
- Consumes: `TrackMatchDao.get/upsert`, `TrackMatchEntity`, `ChartRepository`, `TrackMatcher.bestMatch(TrackQuery, List<Chart>)`, `MatchKind`, `SpotifyRemote` (Task 2).
- Produces:
  ```kotlin
  sealed interface MatchStatus {
      data class Matched(val chart: Chart, val trackOffsetMs: Int) : MatchStatus
      data class Suggested(val chart: Chart, val confidence: Float) : MatchStatus
      data object NoMatch : MatchStatus
  }
  data class NowPlaying(val playback: PlaybackState, val match: MatchStatus)
  interface TrackMatchRepository {
      val changes: StateFlow<Int>
      suspend fun matchFor(track: PlaybackState): MatchStatus
      suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean)
  }
  interface NowPlayingRepository {
      val connection: StateFlow<SpotifyConnection>
      val nowPlaying: Flow<NowPlaying?>
      fun connect()
  }
  ```
  Bindings Hilt `TrackMatchRepository → RoomTrackMatchRepository`, `NowPlayingRepository → SpotifyNowPlayingRepository` (ambos singleton).

- [ ] **Step 1: Escrever os testes que falham**

Em `core/data/build.gradle.kts`, acrescente `implementation(project(":core:spotify"))` e `testImplementation(libs.kotlinx.coroutines.test)` (se ainda não estiver).

`core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/NowPlaying.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

/** Como a faixa do Spotify casa com a biblioteca (spec §5). */
sealed interface MatchStatus {
    /** Casamento automático (confiança alta) ou salvo pelo usuário. */
    data class Matched(val chart: Chart, val trackOffsetMs: Int) : MatchStatus

    /** Confiança média: o app sugere e pede confirmação. */
    data class Suggested(val chart: Chart, val confidence: Float) : MatchStatus

    data object NoMatch : MatchStatus
}

data class NowPlaying(val playback: PlaybackState, val match: MatchStatus)
```

`core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/RoomTrackMatchRepositoryTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.database.TrackMatchEntity
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.PlaybackState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomTrackMatchRepositoryTest {
    private lateinit var db: RiffDatabase
    private lateinit var charts: RoomChartRepository
    private lateinit var matches: RoomTrackMatchRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        charts = RoomChartRepository(db.chartDao())
        matches = RoomTrackMatchRepository(db.trackMatchDao(), charts)
    }

    @After
    fun tearDown() = db.close()

    private fun chart(id: String, title: String, artist: String, durationMs: Long? = null) =
        Chart(id, title, artist, null, null, durationMs, "content://$id", ChartFormat.CHART, setOf(Difficulty.EXPERT))

    private fun track(title: String, artist: String, durationMs: Long = 355_000) =
        PlaybackState("spotify:track:x", title, artist, durationMs, 0, false, 0)

    @Test
    fun closeTitleAndArtistMatchAutomatically() = runTest {
        val queen = chart("a", "Bohemian Rhapsody", "Queen")
        charts.saveScanned(queen, 1)

        assertEquals(MatchStatus.Matched(queen, 0), matches.matchFor(track("Bohemian Rhapsody - Remastered 2011", "Queen")))
    }

    @Test
    fun mediumConfidenceIsOnlySuggested() = runTest {
        val shortVersion = chart("a", "Bohemian Rhapsody", "Queen", durationMs = 240_000)
        charts.saveScanned(shortVersion, 1)

        val status = matches.matchFor(track("Bohemian Rhapsody", "Queen", durationMs = 355_000))

        assertEquals(shortVersion, (status as MatchStatus.Suggested).chart)
    }

    @Test
    fun unrelatedTrackHasNoMatch() = runTest {
        charts.saveScanned(chart("a", "Hurt", "Nine Inch Nails"), 1)

        assertEquals(MatchStatus.NoMatch, matches.matchFor(track("Smells Like Teen Spirit", "Nirvana")))
    }

    @Test
    fun savedChoiceWinsAndKeepsItsOffset() = runTest {
        val queen = chart("a", "Bohemian Rhapsody", "Queen")
        val other = chart("b", "Outra", "Banda")
        charts.saveScanned(queen, 1)
        charts.saveScanned(other, 1)
        db.trackMatchDao().upsert(TrackMatchEntity("spotify:track:x", "b", 1f, isManual = true, trackOffsetMs = 25))

        assertEquals(MatchStatus.Matched(other, 25), matches.matchFor(track("Bohemian Rhapsody", "Queen")))
    }

    @Test
    fun savingAChoiceIsAnnouncedAndKeepsTheOffsetOfTheSameChart() = runTest {
        charts.saveScanned(chart("a", "Música", "Banda"), 1)
        db.trackMatchDao().upsert(TrackMatchEntity("spotify:track:x", "a", 0.8f, isManual = false, trackOffsetMs = 40))
        val before = matches.changes.value

        matches.save("spotify:track:x", "a", confidence = 1f, isManual = true)

        assertEquals(before + 1, matches.changes.value)
        assertEquals(40, db.trackMatchDao().get("spotify:track:x")?.trackOffsetMs)
    }

    @Test
    fun removedChartDropsItsSavedMatch() = runTest {
        charts.saveScanned(chart("a", "Bohemian Rhapsody", "Queen"), 1)
        matches.save("spotify:track:x", "a", confidence = 1f, isManual = true)

        charts.removeNotSeenIn(2) // reimportação em que a pasta sumiu

        assertEquals(MatchStatus.NoMatch, matches.matchFor(track("Bohemian Rhapsody", "Queen")))
    }
}
```

`core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/SpotifyNowPlayingRepositoryTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.spotify.SpotifyRemote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyNowPlayingRepositoryTest {
    private val remote = FakeRemote()
    private val matches = FakeMatches()
    private val charts = FakeCharts()
    private val repository = SpotifyNowPlayingRepository(remote, matches, charts)
    private val track = PlaybackState("spotify:track:x", "Música", "Banda", 200_000, 0, false, 0)
    private val chart = Chart("a", "Música", "Banda", null, null, null, "u", ChartFormat.CHART, setOf(Difficulty.EXPERT))

    @Test
    fun followsTheTrackAndRecomputesTheMatchWhenAChoiceIsSaved() = runTest {
        val seen = mutableListOf<NowPlaying?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.nowPlaying.collect { seen += it } }
        assertNull(seen.last())

        remote.playback.value = track
        assertEquals(NowPlaying(track, MatchStatus.NoMatch), seen.last())

        matches.result = MatchStatus.Matched(chart, 0)
        matches.changes.value++
        assertEquals(NowPlaying(track, MatchStatus.Matched(chart, 0)), seen.last())
    }

    @Test
    fun connectIsForwardedToSpotify() {
        repository.connect()
        assertEquals(1, remote.connects)
        assertEquals(SpotifyConnection.Idle, repository.connection.value)
    }

    private class FakeRemote : SpotifyRemote {
        var connects = 0
        override val connection = MutableStateFlow<SpotifyConnection>(SpotifyConnection.Idle)
        override val playback = MutableStateFlow<PlaybackState?>(null)

        override fun connect() {
            connects++
        }

        override suspend fun refresh(): PlaybackState? = playback.value

        override fun play(trackUri: String) = Unit

        override fun pause() = Unit

        override fun resume() = Unit
    }

    private class FakeMatches : TrackMatchRepository {
        var result: MatchStatus = MatchStatus.NoMatch
        override val changes = MutableStateFlow(0)

        override suspend fun matchFor(track: PlaybackState) = result

        override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) = Unit
    }

    private class FakeCharts : ChartRepository {
        override fun observeCharts(): Flow<List<Chart>> = MutableStateFlow(emptyList())

        override suspend fun getChart(id: String): Chart? = null

        override suspend fun saveScanned(chart: Chart, scanId: Long) = Unit

        override suspend fun removeNotSeenIn(scanId: Long) = Unit

        override suspend fun keepFolder(folderUri: String, scanId: Long) = Unit
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:data:testDebugUnitTest --console=plain`
Expected: FAIL na compilação com `Unresolved reference 'RoomTrackMatchRepository'` e `'SpotifyNowPlayingRepository'`.

- [ ] **Step 3: Implementar**

`core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/TrackMatchRepository.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.chart.match.MatchKind
import com.rodrigomachado.riff.core.chart.match.TrackMatcher
import com.rodrigomachado.riff.core.chart.match.TrackQuery
import com.rodrigomachado.riff.core.database.TrackMatchDao
import com.rodrigomachado.riff.core.database.TrackMatchEntity
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

interface TrackMatchRepository {
    /** Muda a cada [save], para quem mostra o casamento recalcular. */
    val changes: StateFlow<Int>

    /** Casamento salvo (prioridade) ou automático (spec §5). */
    suspend fun matchFor(track: PlaybackState): MatchStatus

    /** Grava a confirmação ou a escolha manual; mantém o ajuste fino se o mapa for o mesmo. */
    suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean)
}

@Singleton
class RoomTrackMatchRepository @Inject constructor(
    private val dao: TrackMatchDao,
    private val charts: ChartRepository,
) : TrackMatchRepository {
    private val version = MutableStateFlow(0)
    override val changes: StateFlow<Int> = version.asStateFlow()

    override suspend fun matchFor(track: PlaybackState): MatchStatus {
        val saved = dao.get(track.trackUri)
        val savedChart = saved?.let { charts.getChart(it.chartId) }
        if (saved != null && savedChart != null) return MatchStatus.Matched(savedChart, saved.trackOffsetMs)
        val query = TrackQuery(track.title, track.artist, track.durationMs)
        val candidate = TrackMatcher.bestMatch(query, charts.observeCharts().first()) ?: return MatchStatus.NoMatch
        return when (candidate.kind) {
            MatchKind.AUTOMATIC -> MatchStatus.Matched(candidate.chart, 0)
            MatchKind.NEEDS_CONFIRMATION -> MatchStatus.Suggested(candidate.chart, candidate.confidence)
        }
    }

    override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) {
        val offset = dao.get(spotifyTrackUri)?.takeIf { it.chartId == chartId }?.trackOffsetMs ?: 0
        dao.upsert(TrackMatchEntity(spotifyTrackUri, chartId, confidence, isManual, offset))
        version.update { it + 1 }
    }
}
```

`core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/NowPlayingRepository.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.spotify.SpotifyRemote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

interface NowPlayingRepository {
    val connection: StateFlow<SpotifyConnection>

    /** A faixa tocando no Spotify com o mapa casado; `null` sem faixa. */
    val nowPlaying: Flow<NowPlaying?>

    fun connect()
}

@Singleton
class SpotifyNowPlayingRepository @Inject constructor(
    private val remote: SpotifyRemote,
    private val matches: TrackMatchRepository,
    charts: ChartRepository,
) : NowPlayingRepository {
    override val connection: StateFlow<SpotifyConnection> get() = remote.connection

    // Recalcula quando a faixa muda, quando a biblioteca muda (importação) e quando um casamento é salvo.
    @OptIn(ExperimentalCoroutinesApi::class)
    override val nowPlaying: Flow<NowPlaying?> =
        combine(remote.playback, charts.observeCharts(), matches.changes) { playback, _, _ -> playback }
            .mapLatest { playback -> playback?.let { NowPlaying(it, matches.matchFor(it)) } }

    override fun connect() = remote.connect()
}
```

Em `DataModule.kt`, no `DataBindsModule`:
```kotlin
    @Binds
    abstract fun trackMatchRepository(impl: RoomTrackMatchRepository): TrackMatchRepository

    @Binds
    abstract fun nowPlayingRepository(impl: SpotifyNowPlayingRepository): NowPlayingRepository
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :core:data:testDebugUnitTest :core:data:koverVerify :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; `RoomTrackMatchRepositoryTest` 6/6; `SpotifyNowPlayingRepositoryTest` 2/2.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(data): casamento salvo ou automático e faixa tocando agora" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Diálogo de dificuldade em `:core:ui`

**Files:**
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/DifficultyDialog.kt`
- Modify: `core/ui/src/main/res/values/strings.xml`, `core/ui/src/main/res/values-en/strings.xml`
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryScreen.kt`
- Modify: `feature/library/src/main/res/values/strings.xml`, `feature/library/src/main/res/values-en/strings.xml` (remover `library_choose_difficulty` e `library_cancel`)
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/component/DifficultyDialogTest.kt`

**Interfaces:**
- Produces: `@Composable fun <T> DifficultyDialog(title: String, options: List<T>, label: @Composable (T) -> String, onChoose: (T) -> Unit, onDismiss: () -> Unit)`; strings `ui_choose_difficulty` ("Escolha a dificuldade" / "Choose difficulty") e `ui_cancel` ("Cancelar" / "Cancel").

`:core:ui` não depende de `:core:model`, por isso o diálogo é genérico e a dificuldade vira texto pelo `label`.

- [ ] **Step 1: Escrever o teste que falha**

`core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/component/DifficultyDialogTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class DifficultyDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var chosen: Int? = null
    private var dismissed = false

    private fun show() = composeRule.setContent {
        RiffTheme {
            DifficultyDialog(
                title = "Música",
                options = listOf(0, 3),
                label = { if (it == 0) "Fácil" else "Expert" },
                onChoose = { chosen = it },
                onDismiss = { dismissed = true },
            )
        }
    }

    @Test
    fun choosingAnOptionReportsIt() {
        show()

        composeRule.onNodeWithText("Música").assertExists()
        composeRule.onNodeWithText("Escolha a dificuldade").assertExists()
        composeRule.onNodeWithText("Expert").performClick()

        assertEquals(3, chosen)
    }

    @Test
    fun cancelDismisses() {
        show()

        composeRule.onNodeWithText("Cancelar").performClick()

        assertTrue(dismissed)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:ui:testDebugUnitTest --tests '*DifficultyDialogTest*' --console=plain`
Expected: FAIL na compilação com `Unresolved reference 'DifficultyDialog'`.

- [ ] **Step 3: Implementar**

`core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/DifficultyDialog.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.rodrigomachado.riff.core.ui.R
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Escolha da dificuldade antes da partida. Usado na biblioteca e em "Tocando agora". */
@Composable
fun <T> DifficultyDialog(
    title: String,
    options: List<T>,
    label: @Composable (T) -> String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(RiffColors.StageBlack, MaterialTheme.shapes.large).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = RiffColors.Paper)
            Text(
                stringResource(R.string.ui_choose_difficulty),
                style = MaterialTheme.typography.bodyMedium,
                color = RiffColors.PaperDim,
            )
            options.forEach { option -> PosterButton(label(option), onClick = { onChoose(option) }) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel), color = RiffColors.Paper) }
        }
    }
}
```

Strings em `core/ui/src/main/res/values/strings.xml` (dentro de `<resources>`):
```xml
    <string name="ui_choose_difficulty">Escolha a dificuldade</string>
    <string name="ui_cancel">Cancelar</string>
```
E em `values-en/strings.xml`:
```xml
    <string name="ui_choose_difficulty">Choose difficulty</string>
    <string name="ui_cancel">Cancel</string>
```

Em `LibraryScreen.kt`: apague o `private fun DifficultyDialog(...)` e troque a chamada por
```kotlin
    choosing?.let { chart ->
        DifficultyDialog(
            title = chart.title,
            options = chart.difficulties.sorted(),
            label = { stringResource(difficultyLabel(it.ordinal)) },
            onChoose = { difficulty ->
                choosing = null
                onPlay(chart.id, difficulty)
            },
            onDismiss = { choosing = null },
        )
    }
```
(import `com.rodrigomachado.riff.core.ui.component.DifficultyDialog`; remova imports que ficarem sem uso, como `Dialog`). Apague `library_choose_difficulty` e `library_cancel` dos dois `strings.xml` da biblioteca.

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt lintDebug :core:ui:testDebugUnitTest :feature:library:testDebugUnitTest :feature:library:verifyRoborazziDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; `DifficultyDialogTest` 2/2; `LibraryContentTest.tappingAChartOffersItsDifficultiesAndPlaysTheChosenOne` continua passando (mesmos textos).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(ui): diálogo de dificuldade compartilhado no core:ui" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Biblioteca no modo "escolher o mapa desta música"

**Files:**
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryRoute.kt`
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryViewModel.kt`
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryScreen.kt`
- Modify: `feature/library/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Modify: `app/src/main/kotlin/com/rodrigomachado/riff/RiffNavHost.kt` (compilar com a rota nova)
- Test: `feature/library/src/test/kotlin/com/rodrigomachado/riff/feature/library/Fakes.kt`, `LibraryViewModelTest.kt`, `LibraryContentTest.kt`

**Interfaces:**
- Consumes: `TrackMatchRepository.save` (Task 4).
- Produces:
  ```kotlin
  @Serializable data class LibraryRoute(val matchTrackUri: String? = null, val matchTrackTitle: String? = null)
  data class TrackToMatch(val trackUri: String, val title: String)
  fun LibraryScreen(onPlay: (String, Difficulty) -> Unit, modifier: Modifier = Modifier, match: TrackToMatch? = null, onMatched: () -> Unit = {}, viewModel: LibraryViewModel = hiltViewModel())
  fun LibraryContent(state, onPickFolder, onRescan, onReportDismissed, onPlay, modifier = Modifier, matchingTitle: String? = null, onChartMatched: (String) -> Unit = {})
  LibraryViewModel.chooseChartFor(trackUri: String, chartId: String); LibraryUiState.matchSaved: Boolean
  ```

- [ ] **Step 1: Escrever os testes que falham**

Em `Fakes.kt` da biblioteca, acrescente:
```kotlin
class FakeTrackMatchRepository : com.rodrigomachado.riff.core.data.TrackMatchRepository {
    val saved = mutableListOf<Triple<String, String, Boolean>>()
    override val changes = MutableStateFlow(0)

    override suspend fun matchFor(track: com.rodrigomachado.riff.core.model.PlaybackState) =
        com.rodrigomachado.riff.core.model.MatchStatus.NoMatch

    override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) {
        saved += Triple(spotifyTrackUri, chartId, isManual)
    }
}
```

Em `LibraryViewModelTest.kt`: crie `private val matches = FakeTrackMatchRepository()`, passe-o como último argumento do construtor (`LibraryViewModel(repository, folderStore, importer, folderAccess, matches)`) e acrescente:
```kotlin
    @Test
    fun choosingAChartForATrackSavesAManualMatch() = runTest {
        val viewModel = viewModel()

        viewModel.chooseChartFor("spotify:track:x", "a")

        assertEquals(listOf(Triple("spotify:track:x", "a", true)), matches.saved)
        assertTrue(viewModel.uiState.value.matchSaved)
    }
```

Em `LibraryContentTest.kt`, acrescente (o `show` existente continua igual; este teste monta o conteúdo direto):
```kotlin
    @Test
    fun inMatchModeTappingAChartPicksItWithoutAskingTheDifficulty() {
        var picked: String? = null
        composeRule.setContent {
            RiffTheme {
                LibraryContent(
                    state = LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(chart("a", title = "Música"))),
                    onPickFolder = {},
                    onRescan = {},
                    onReportDismissed = {},
                    onPlay = { _, _ -> error("não deveria jogar") },
                    matchingTitle = "Bohemian Rhapsody",
                    onChartMatched = { picked = it },
                )
            }
        }

        composeRule.onNodeWithText("Qual mapa é esta música?").assertExists()
        composeRule.onNodeWithText("Bohemian Rhapsody").assertExists()
        composeRule.onNodeWithText("Música").performClick()

        composeRule.onNodeWithText("Escolha a dificuldade").assertDoesNotExist()
        assertEquals("a", picked)
    }
```
(Use os imports que o arquivo já tem; acrescente `RiffTheme` se faltar.)

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :feature:library:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (`chooseChartFor`, `matchSaved`, `matchingTitle` e `onChartMatched` não existem; construtor com 5 argumentos).

- [ ] **Step 3: Implementar**

`LibraryRoute.kt`:
```kotlin
package com.rodrigomachado.riff.feature.library

import kotlinx.serialization.Serializable

/** Biblioteca. Com [matchTrackUri], serve para escolher o mapa da música que está tocando no Spotify. */
@Serializable
data class LibraryRoute(val matchTrackUri: String? = null, val matchTrackTitle: String? = null)

/** Música do Spotify esperando o jogador escolher o mapa. */
data class TrackToMatch(val trackUri: String, val title: String)
```

`LibraryViewModel.kt`:
- `LibraryUiState` ganha `val matchSaved: Boolean = false`.
- O construtor ganha `private val matches: TrackMatchRepository` (import `com.rodrigomachado.riff.core.data.TrackMatchRepository`).
- Novo estado e combinação:
```kotlin
    private val matchSaved = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> =
        combine(chartRepository.observeCharts(), folderStore.rootUri, importState, matchSaved) { charts, rootUri, import, saved ->
            LibraryUiState(
                isLoading = false,
                hasFolder = rootUri != null,
                charts = charts,
                isImporting = import.isImporting,
                scannedFolders = import.scannedFolders,
                report = import.report,
                folderAccessLost = import.folderAccessLost,
                importFailed = import.importFailed,
                matchSaved = saved,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    /** Escolha manual do mapa da música do Spotify (spec §5: salva e tem prioridade). */
    fun chooseChartFor(trackUri: String, chartId: String) {
        viewModelScope.launch {
            matches.save(trackUri, chartId, confidence = 1f, isManual = true)
            matchSaved.value = true
        }
    }
```

`LibraryScreen.kt`:
```kotlin
@Composable
fun LibraryScreen(
    onPlay: (chartId: String, difficulty: Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    match: TrackToMatch? = null,
    onMatched: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onFolderPicked(it.toString()) }
    }
    if (state.matchSaved) LaunchedEffect(Unit) { onMatched() }
    LibraryContent(
        state = state,
        onPickFolder = { pickFolder.launch(null) },
        onRescan = viewModel::onRescan,
        onReportDismissed = viewModel::onReportDismissed,
        onPlay = onPlay,
        modifier = modifier,
        matchingTitle = match?.title,
        onChartMatched = { chartId -> match?.let { viewModel.chooseChartFor(it.trackUri, chartId) } },
    )
}
```
`LibraryContent` ganha `matchingTitle: String? = null` e `onChartMatched: (String) -> Unit = {}` depois de `modifier`. Logo abaixo do `PosterTitle`, quando `matchingTitle != null`:
```kotlin
        matchingTitle?.let { title ->
            Text(stringResource(R.string.library_match_title), style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
            Text(title, style = MaterialTheme.typography.bodyLarge, color = RiffColors.PaperDim)
        }
```
E o clique na lista passa a ser `{ chart -> if (matchingTitle != null) onChartMatched(chart.id) else choosing = chart }`.

Strings da biblioteca:
```xml
    <string name="library_match_title">Qual mapa é esta música?</string>
```
```xml
    <string name="library_match_title">Which chart is this song?</string>
```

`RiffNavHost.kt` (só para compilar; a navegação nova é a Task 11): `startDestination = LibraryRoute()` e `onBackToLibrary = dropUnlessResumed { navController.popBackStack<LibraryRoute>(inclusive = false) }`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt lintDebug :feature:library:testDebugUnitTest :feature:library:verifyRoborazziDebug :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; novos testes passando; screenshots da biblioteca sem mudança.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(library): escolher o mapa da música que está tocando" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Partida com o Spotify

**Files:**
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayRoute.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModel.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayScreen.kt`
- Modify: `feature/gameplay/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModelTest.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/GameStagePlaybackTest.kt`

**Interfaces:**
- Consumes: `@LocalSong`, `@SpotifySong` (Task 3).
- Produces: `GameplayRoute(chartId: String, difficulty: String, spotifyTrackUri: String? = null, trackOffsetMs: Int = 0)`; `UnavailableReason.SPOTIFY_UNAVAILABLE`; `GameplayViewModel.onPlaybackChanged(paused: Boolean)`; `GameStage(..., onPlaybackChanged: (Boolean) -> Unit = {})`.

**Regras:**
- Com `spotifyTrackUri`, o player vem de `@SpotifySong`, prepara a faixa do Spotify, a pasta não precisa ter áudio e o offset é `settings.audioOffsetMs + trackOffsetMs`.
- Falha ao preparar no modo Spotify → `SPOTIFY_UNAVAILABLE` ("Não deu para tocar pelo Spotify…").
- Pausa ou retomada fora do jogo (notificação do Spotify, conexão caiu e voltou): o laço de frames compara `session.isPaused` com o estado da tela e chama `onPlaybackChanged`. Pausado fora → `session.pause()` (solta as trilhas) e a tela mostra "Pausado"; retomado fora → a tela volta ao jogo.

- [ ] **Step 1: Escrever os testes que falham**

Em `GameplayViewModelTest.kt`:
- Acrescente `private val spotifyPlayer = FakeSongPlayer()` e `private var spotifyPlayersCreated = 0`.
- Troque o helper por:
```kotlin
    private fun viewModel(spotifyTrackUri: String? = null, trackOffsetMs: Int = 0) = GameplayViewModel(
        SavedStateHandle(
            buildMap {
                put("chartId", "a")
                put("difficulty", "EXPERT")
                spotifyTrackUri?.let { put("spotifyTrackUri", it) }
                put("trackOffsetMs", trackOffsetMs)
            },
        ),
        loader,
        settings,
        scores,
        {
            playersCreated++
            player
        },
        {
            spotifyPlayersCreated++
            spotifyPlayer
        },
        { sfx },
        time,
    )
```
- Acrescente os testes:
```kotlin
    @Test
    fun spotifyModePlaysTheTrackWithItsOffsetAndNeedsNoAudioInTheFolder() = runTest {
        loader.result = { playable(audio = null) }

        val state = viewModel(spotifyTrackUri = "spotify:track:x", trackOffsetMs = 25).uiState.value as GameplayUiState.Ready

        assertEquals("spotify:track:x", spotifyPlayer.preparedUri)
        assertEquals(0, playersCreated)
        assertTrue(spotifyPlayer.isPlaying)
        spotifyPlayer.positionMs = 1_000
        time.now = GameSession.SYNC_INTERVAL_MS
        assertEquals(985L, state.session.tick()) // 1000 − 40 (calibração global) + 25 (ajuste da música)
    }

    @Test
    fun spotifyThatCannotPlayIsExplained() = runTest {
        loader.result = { playable() }
        spotifyPlayer.prepareFailure = java.io.IOException("Spotify indisponível")

        assertEquals(
            GameplayUiState.Unavailable(UnavailableReason.SPOTIFY_UNAVAILABLE),
            viewModel(spotifyTrackUri = "spotify:track:x").uiState.value,
        )
        assertTrue(spotifyPlayer.released)
    }

    @Test
    fun pausedOutsideTheGameShowsThePauseScreenAndReleasesTheLanes() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()
        val session = (viewModel.uiState.value as GameplayUiState.Ready).session
        session.press(0)

        viewModel.onPlaybackChanged(paused = true)

        assertTrue((viewModel.uiState.value as GameplayUiState.Ready).isPaused)
        assertEquals(false, session.isPressed(0))

        viewModel.onPlaybackChanged(paused = false)
        assertEquals(false, (viewModel.uiState.value as GameplayUiState.Ready).isPaused)
    }
```

`feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/GameStagePlaybackTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.test.junit4.createComposeRule
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameStagePlaybackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun whenTheMusicStopsOutsideTheGameTheStageReportsIt() {
        val player = FakeSongPlayer()
        val session = GameSession(listOf(note(60_000)), player, FakeSfxPlayer(), FakeTime(), audioOffsetMs = 0)
        session.start()
        val reported = mutableListOf<Boolean>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, Settings.DEFAULT),
                    reducedMotion = true,
                    onPause = {},
                    onResume = {},
                    onExit = {},
                    onSessionFinished = {},
                    onPlaybackChanged = { reported += it },
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(emptyList<Boolean>(), reported)

        player.pause() // pausado pela notificação do Spotify
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(true, reported.firstOrNull())
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :feature:gameplay:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (construtor com a factory do Spotify, `SPOTIFY_UNAVAILABLE`, `onPlaybackChanged`).

- [ ] **Step 3: Implementar**

`GameplayRoute.kt`:
```kotlin
/** Partida. Com [spotifyTrackUri], a música vem do Spotify e [trackOffsetMs] é o ajuste fino desta música. */
@Serializable
data class GameplayRoute(
    val chartId: String,
    val difficulty: String,
    val spotifyTrackUri: String? = null,
    val trackOffsetMs: Int = 0,
)
```

`GameplayViewModel.kt`:
- `enum class UnavailableReason { NO_AUDIO, CHART_MISSING, FOLDER_ACCESS_LOST, AUDIO_FAILED, SPOTIFY_UNAVAILABLE }`.
- Construtor: depois de `@LocalSong private val songPlayers: SongPlayerFactory,` acrescente `@SpotifySong private val spotifyPlayers: SongPlayerFactory,`.
- Campos:
```kotlin
    private val spotifyTrackUri: String? = savedStateHandle["spotifyTrackUri"]
    private val trackOffsetMs: Int = savedStateHandle["trackOffsetMs"] ?: 0
```
- `load()`:
```kotlin
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // cada falha vira uma explicação na tela
    private suspend fun load(): GameplayUiState = try {
        val playable = loader.load(chartId, difficulty)
        val songUri = spotifyTrackUri ?: playable.audioUri ?: throw NoAudioException()
        val settings = settingsRepository.settings.first()
        val factory = if (spotifyTrackUri != null) spotifyPlayers else songPlayers
        val songPlayer = factory.create().also { player = it }
        try {
            songPlayer.prepare(songUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw if (spotifyTrackUri != null) SpotifyFailedException(e) else AudioFailedException(e)
        }
        val effects = sfxPlayers.create().also {
            sfx = it
            it.setVolume(settings.sfxVolume)
        }
        val offset = settings.audioOffsetMs.toLong() + trackOffsetMs
        val session = GameSession(playable.notes, songPlayer, effects, timeSource, offset)
        if (!pauseRequested) session.start()
        GameplayUiState.Ready(playable.chart.title, playable.chart.artist, difficulty, session, settings, pauseRequested)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        release()
        GameplayUiState.Unavailable(reasonFor(e))
    }
```
- Em `reasonFor`: `is SpotifyFailedException -> UnavailableReason.SPOTIFY_UNAVAILABLE`; e `private class SpotifyFailedException(cause: Throwable) : Exception("Spotify não pôde tocar", cause)`.
- Novo método:
```kotlin
    /** A música pausou ou voltou fora do jogo (notificação do Spotify, conexão caiu e voltou). */
    fun onPlaybackChanged(paused: Boolean) {
        if (player == null) return
        val ready = state.value as? GameplayUiState.Ready ?: return
        if (ready.isPaused == paused) return
        if (paused) ready.session.pause()
        state.update { ready.copy(isPaused = paused) }
    }
```

`GameplayScreen.kt`:
- `GameplayScreen` passa `onPlaybackChanged = viewModel::onPlaybackChanged` ao `GameStage`.
- `GameStage` ganha `onPlaybackChanged: (Boolean) -> Unit = {}` (depois de `onSessionFinished`) e o laço passa a ser:
```kotlin
    val currentPaused by rememberUpdatedState(state.isPaused)
    val playbackChanged by rememberUpdatedState(onPlaybackChanged)
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
                if (session.isPaused != currentPaused) playbackChanged(session.isPaused)
            }
        }
    }
```
(import `androidx.compose.runtime.rememberUpdatedState`.)
- Em `UnavailableReason.message()`, `SPOTIFY_UNAVAILABLE -> R.string.gameplay_unavailable_spotify`.

Strings do gameplay:
```xml
    <string name="gameplay_unavailable_spotify">Não deu para tocar pelo Spotify. Confira se o app do Spotify está aberto e conectado.</string>
```
```xml
    <string name="gameplay_unavailable_spotify">Couldn\'t play through Spotify. Check that the Spotify app is open and connected.</string>
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt lintDebug :feature:gameplay:testDebugUnitTest :feature:gameplay:verifyRoborazziDebug :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; testes novos e antigos do gameplay passando; screenshot `Gameplay_stage.png` sem mudança.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(gameplay): partida com a música do Spotify e pausa vinda de fora" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Resultado volta para quem abriu a partida

**Files:**
- Modify: `feature/results/src/main/kotlin/com/rodrigomachado/riff/feature/results/ResultsScreen.kt`
- Modify: `feature/results/src/main/kotlin/com/rodrigomachado/riff/feature/results/ResultsRoute.kt`
- Modify: `feature/results/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Modify: `app/src/main/kotlin/com/rodrigomachado/riff/RiffNavHost.kt`
- Test: `feature/results/src/test/kotlin/com/rodrigomachado/riff/feature/results/ResultsContentTest.kt`, `ResultsScreenshotTest.kt`

**Interfaces:**
- Produces: `ResultsScreen(onPlayAgain: (String, Difficulty) -> Unit, onDone: () -> Unit, ...)`; `ResultsRoute(scoreId: Long, spotifyTrackUri: String? = null, trackOffsetMs: Int = 0)`; string `results_back` ("Voltar" / "Back") no lugar de `results_library`.

Agora a partida pode ter vindo de "Tocando agora" ou da biblioteca; "Voltar" volta para quem abriu. A rota carrega a faixa do Spotify para "Jogar de novo" continuar no mesmo modo.

- [ ] **Step 1: Escrever o teste que falha**

Em `ResultsContentTest.kt` e `ResultsScreenshotTest.kt`, troque `onBackToLibrary = {}` por `onDone = {}`. Em `ResultsContentTest.kt`, acrescente:
```kotlin
    @Test
    fun backReturnsToWhereTheGameStarted() {
        var done = false
        composeRule.setContent {
            RiffTheme {
                ResultsContent(state = ready(), onPlayAgain = { _, _ -> }, onDone = { done = true })
            }
        }

        composeRule.onNodeWithText("Voltar").performScrollTo().performClick()

        assertTrue(done)
    }
```
Se o arquivo não tiver um helper `ready()`, use o mesmo `ResultsUiState.Ready(...)` que os outros testes do arquivo montam.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :feature:results:testDebugUnitTest --console=plain`
Expected: FAIL na compilação com `No parameter with name 'onDone'`.

- [ ] **Step 3: Implementar**

- Em `ResultsScreen.kt`, renomeie o parâmetro `onBackToLibrary` para `onDone` em `ResultsScreen`, `ResultsContent` e `ReadyResults`, e troque `R.string.results_library` por `R.string.results_back` (dois usos).
- `strings.xml`: troque `results_library` por `<string name="results_back">Voltar</string>`; em `values-en`: `<string name="results_back">Back</string>`.
- `ResultsRoute.kt`:
```kotlin
/** Resultado. [spotifyTrackUri] e [trackOffsetMs] fazem "Jogar de novo" continuar no modo Spotify. */
@Serializable
data class ResultsRoute(val scoreId: Long, val spotifyTrackUri: String? = null, val trackOffsetMs: Int = 0)
```
- Em `RiffNavHost.kt`, no `ResultsScreen`: `onDone = dropUnlessResumed { navController.popBackStack() }` (o jogo já saiu da pilha, então volta para quem o abriu).

- [ ] **Step 4: Rodar, regravar o screenshot e conferir**

Run: `./scripts/gw :feature:results:recordRoborazziDebug --console=plain` e leia `feature/results/src/test/screenshots/Results_new_best.png`: só o rótulo do botão de baixo muda para "Voltar".
Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt lintDebug :feature:results:testDebugUnitTest :feature:results:verifyRoborazziDebug :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(results): voltar para quem abriu a partida" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: `:feature:nowplaying` — estado da tela

**Files:**
- Modify: `settings.gradle.kts` (`include(":feature:nowplaying")`)
- Create: `feature/nowplaying/build.gradle.kts`, `feature/nowplaying/src/main/AndroidManifest.xml`, `feature/nowplaying/src/test/resources/robolectric.properties`
- Create: `feature/nowplaying/src/main/kotlin/com/rodrigomachado/riff/feature/nowplaying/NowPlayingRoute.kt`
- Create: `feature/nowplaying/src/main/kotlin/com/rodrigomachado/riff/feature/nowplaying/NowPlayingViewModel.kt`
- Test: `feature/nowplaying/src/test/kotlin/com/rodrigomachado/riff/feature/nowplaying/Fakes.kt`
- Test: `feature/nowplaying/src/test/kotlin/com/rodrigomachado/riff/feature/nowplaying/NowPlayingViewModelTest.kt`

**Interfaces:**
- Consumes: `NowPlayingRepository`, `TrackMatchRepository` (Task 4).
- Produces:
  ```kotlin
  @Serializable data object NowPlayingRoute
  sealed interface NowPlayingUiState { Connecting; Unavailable(error: SpotifyError); NothingPlaying; Playing(nowPlaying: NowPlaying) }
  class NowPlayingViewModel { val uiState: StateFlow<NowPlayingUiState>; fun retry(); fun confirmSuggestion(trackUri: String, chartId: String, confidence: Float) }
  ```

- [ ] **Step 1: Módulo**

`feature/nowplaying/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.nowplaying"
}
```
`feature/nowplaying/src/main/AndroidManifest.xml`: `<?xml version="1.0" encoding="utf-8"?>` + `<manifest />`.
`feature/nowplaying/src/test/resources/robolectric.properties`: copie de `feature/gameplay/src/test/resources/robolectric.properties` (`sdk=35`).

`NowPlayingRoute.kt`:
```kotlin
package com.rodrigomachado.riff.feature.nowplaying

import kotlinx.serialization.Serializable

/** Tela inicial: a música tocando no Spotify e o mapa casado. */
@Serializable
data object NowPlayingRoute
```

- [ ] **Step 2: Escrever o teste que falha**

`feature/nowplaying/src/test/kotlin/com/rodrigomachado/riff/feature/nowplaying/Fakes.kt`:
```kotlin
package com.rodrigomachado.riff.feature.nowplaying

import com.rodrigomachado.riff.core.data.NowPlayingRepository
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import kotlinx.coroutines.flow.MutableStateFlow

fun chart(id: String = "a", title: String = "Bohemian Rhapsody", artist: String = "Queen") =
    Chart(id, title, artist, null, null, null, "content://$id", ChartFormat.CHART, setOf(Difficulty.MEDIUM, Difficulty.EXPERT))

fun track(isPaused: Boolean = false) =
    PlaybackState("spotify:track:x", "Bohemian Rhapsody - Remastered 2011", "Queen", 355_000, 61_000, isPaused, 0)

fun playing(match: MatchStatus, isPaused: Boolean = false) = NowPlaying(track(isPaused), match)

class FakeNowPlayingRepository : NowPlayingRepository {
    var connects = 0
    override val connection = MutableStateFlow<SpotifyConnection>(SpotifyConnection.Idle)
    override val nowPlaying = MutableStateFlow<NowPlaying?>(null)

    override fun connect() {
        connects++
    }
}

class FakeTrackMatchRepository : TrackMatchRepository {
    val saved = mutableListOf<Triple<String, String, Boolean>>()
    override val changes = MutableStateFlow(0)

    override suspend fun matchFor(track: PlaybackState): MatchStatus = MatchStatus.NoMatch

    override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) {
        saved += Triple(spotifyTrackUri, chartId, isManual)
    }
}
```

`NowPlayingViewModelTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.nowplaying

import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {
    private val repository = FakeNowPlayingRepository()
    private val matches = FakeTrackMatchRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): NowPlayingViewModel {
        val viewModel = NowPlayingViewModel(repository, matches)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun opensConnectingToSpotify() = runTest {
        val viewModel = viewModel()

        assertEquals(1, repository.connects)
        assertEquals(NowPlayingUiState.Connecting, viewModel.uiState.value)
    }

    @Test
    fun failureExplainsWhyAndRetryConnectsAgain() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED)

        assertEquals(NowPlayingUiState.Unavailable(SpotifyError.NOT_INSTALLED), viewModel.uiState.value)
        viewModel.retry()
        assertEquals(2, repository.connects)
    }

    @Test
    fun connectedShowsTheTrackOrThatNothingIsPlaying() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Connected
        assertEquals(NowPlayingUiState.NothingPlaying, viewModel.uiState.value)

        val now = playing(MatchStatus.Matched(chart(), 0))
        repository.nowPlaying.value = now
        assertEquals(NowPlayingUiState.Playing(now), viewModel.uiState.value)
    }

    @Test
    fun confirmingASuggestionSavesIt() = runTest {
        val viewModel = viewModel()

        viewModel.confirmSuggestion("spotify:track:x", "a", 0.9f)

        assertEquals(listOf(Triple("spotify:track:x", "a", false)), matches.saved)
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `./scripts/gw :feature:nowplaying:testDebugUnitTest --console=plain`
Expected: FAIL na compilação com `Unresolved reference 'NowPlayingViewModel'`.

- [ ] **Step 4: Implementar**

`NowPlayingViewModel.kt`:
```kotlin
package com.rodrigomachado.riff.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.NowPlayingRepository
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NowPlayingUiState {
    data object Connecting : NowPlayingUiState

    data class Unavailable(val error: SpotifyError) : NowPlayingUiState

    data object NothingPlaying : NowPlayingUiState

    data class Playing(val nowPlaying: NowPlaying) : NowPlayingUiState
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val repository: NowPlayingRepository,
    private val matches: TrackMatchRepository,
) : ViewModel() {
    val uiState: StateFlow<NowPlayingUiState> =
        combine(repository.connection, repository.nowPlaying) { connection, nowPlaying ->
            when (connection) {
                SpotifyConnection.Idle, SpotifyConnection.Connecting -> NowPlayingUiState.Connecting
                is SpotifyConnection.Failed -> NowPlayingUiState.Unavailable(connection.error)
                SpotifyConnection.Connected ->
                    nowPlaying?.let { NowPlayingUiState.Playing(it) } ?: NowPlayingUiState.NothingPlaying
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NowPlayingUiState.Connecting)

    init {
        repository.connect()
    }

    fun retry() = repository.connect()

    /** O jogador confirmou o mapa sugerido (confiança média, spec §5). */
    fun confirmSuggestion(trackUri: String, chartId: String, confidence: Float) {
        viewModelScope.launch { matches.save(trackUri, chartId, confidence, isManual = false) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :feature:nowplaying:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `NowPlayingViewModelTest` 4/4.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(nowplaying): estado da tela Tocando agora" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Tela "Tocando agora"

**Files:**
- Create: `feature/nowplaying/src/main/kotlin/com/rodrigomachado/riff/feature/nowplaying/NowPlayingScreen.kt`
- Create: `feature/nowplaying/src/main/res/values/strings.xml`, `feature/nowplaying/src/main/res/values-en/strings.xml`
- Test: `feature/nowplaying/src/test/kotlin/com/rodrigomachado/riff/feature/nowplaying/NowPlayingContentTest.kt`
- Test: `feature/nowplaying/src/test/kotlin/com/rodrigomachado/riff/feature/nowplaying/NowPlayingScreenshotTest.kt`

**Interfaces:**
- Consumes: `NowPlayingUiState`, `NowPlayingViewModel` (Task 9); `DifficultyDialog`, `difficultyLabel`, `PosterTitle`, `PosterButton`, `TrackCard`, `StatusBadge`, `BadgeTone`, `LoadingState`, `EmptyState`, `ErrorState` (`:core:ui`).
- Produces:
  ```kotlin
  data class PlayRequest(val chartId: String, val difficulty: Difficulty, val trackUri: String, val trackOffsetMs: Int)
  fun NowPlayingScreen(onPlay: (PlayRequest) -> Unit, onChooseChart: (trackUri: String, title: String) -> Unit, onOpenLibrary: () -> Unit, modifier: Modifier = Modifier, viewModel: NowPlayingViewModel = hiltViewModel())
  fun NowPlayingContent(state: NowPlayingUiState, onRetry: () -> Unit, onPlay: (PlayRequest) -> Unit, onConfirm: (trackUri: String, chartId: String, confidence: Float) -> Unit, onChooseChart: (String, String) -> Unit, onOpenLibrary: () -> Unit, modifier: Modifier = Modifier)
  ```

**Conteúdo por estado:**
- Sempre: `PosterTitle` "Tocando agora" e, logo abaixo, `TextButton` "Biblioteca" (`onOpenLibrary`), para o modo com o áudio da pasta continuar acessível.
- `Connecting`: `LoadingState(message = "Conectando ao Spotify…")`.
- `Unavailable(NOT_INSTALLED)`: `EmptyState("Spotify não instalado", "Instale o app do Spotify para jogar com a música que está tocando. Enquanto isso, a biblioteca joga com o áudio das pastas.", actionLabel = "Abrir biblioteca", onAction = onOpenLibrary)`.
- `Unavailable(NOT_CONFIGURED)`: `EmptyState("Spotify não configurado", "Este build não tem o spotify.clientId no local.properties.", "Abrir biblioteca", onOpenLibrary)`.
- `Unavailable(outros)`: `ErrorState(mensagem do erro, onRetry)`.
- `NothingPlaying`: `EmptyState("Nada tocando", "Toque uma música no Spotify e ela aparece aqui.")`.
- `Playing`: `TrackCard(title, artist, tilted = true, badge = carimbo)`; se pausado, texto "Pausado no Spotify"; e conforme o casamento:
  - `Matched`: "Mapa: <título> — <artista>" e `PosterButton("Jogar")` → `DifficultyDialog` com as dificuldades do mapa → `onPlay(PlayRequest(chart.id, dificuldade, trackUri, trackOffsetMs))`.
  - `Suggested`: "Este mapa parece ser desta música: <título> — <artista>", `PosterButton("Confirmar mapa")` → `onConfirm(trackUri, chart.id, confidence)` e `TextButton("Escolher outro")` → `onChooseChart(trackUri, título da faixa)`.
  - `NoMatch`: "Nenhum mapa da biblioteca casa com esta música." e `PosterButton("Escolher mapa")` → `onChooseChart`.
- Carimbos: `Matched` → "MAPA ENCONTRADO" (`SUCCESS`); `Suggested` → "CONFIRMAR MAPA" (`WARNING`); `NoMatch` → "SEM MAPA" (`NEUTRAL`).

- [ ] **Step 1: Escrever os testes que falham**

`NowPlayingContentTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.nowplaying

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.SpotifyError
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class NowPlayingContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var played: PlayRequest? = null
    private var confirmed: Triple<String, String, Float>? = null
    private var chooseFor: Pair<String, String>? = null
    private var retries = 0
    private var libraryOpened = false

    private fun show(state: NowPlayingUiState) = composeRule.setContent {
        RiffTheme {
            NowPlayingContent(
                state = state,
                onRetry = { retries++ },
                onPlay = { played = it },
                onConfirm = { uri, id, confidence -> confirmed = Triple(uri, id, confidence) },
                onChooseChart = { uri, title -> chooseFor = uri to title },
                onOpenLibrary = { libraryOpened = true },
            )
        }
    }

    @Test
    fun matchedTrackPlaysWithTheChosenDifficultyAndItsOffset() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), trackOffsetMs = 25))))

        composeRule.onNodeWithText("Bohemian Rhapsody - Remastered 2011").assertExists()
        composeRule.onNodeWithText("MAPA ENCONTRADO").assertExists()
        composeRule.onNodeWithText("Jogar").performClick()
        composeRule.onNodeWithText("Expert").performClick()

        assertEquals(PlayRequest("a", Difficulty.EXPERT, "spotify:track:x", 25), played)
    }

    @Test
    fun suggestionCanBeConfirmedOrReplaced() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.Suggested(chart(), confidence = 0.9f))))

        composeRule.onNodeWithText("CONFIRMAR MAPA").assertExists()
        composeRule.onNodeWithText("Confirmar mapa").performClick()
        assertEquals(Triple("spotify:track:x", "a", 0.9f), confirmed)

        composeRule.onNodeWithText("Escolher outro").performClick()
        assertEquals("spotify:track:x" to "Bohemian Rhapsody - Remastered 2011", chooseFor)
    }

    @Test
    fun withoutAMatchAsksToChooseOne() {
        show(NowPlayingUiState.Playing(playing(MatchStatus.NoMatch, isPaused = true)))

        composeRule.onNodeWithText("SEM MAPA").assertExists()
        composeRule.onNodeWithText("Pausado no Spotify").assertExists()
        composeRule.onNodeWithText("Escolher mapa").performClick()

        assertEquals("spotify:track:x" to "Bohemian Rhapsody - Remastered 2011", chooseFor)
    }

    @Test
    fun spotifyNotInstalledStillOffersTheLibrary() {
        show(NowPlayingUiState.Unavailable(SpotifyError.NOT_INSTALLED))

        composeRule.onNodeWithText("Spotify não instalado").assertExists()
        composeRule.onNodeWithText("Abrir biblioteca").performClick()

        assertTrue(libraryOpened)
    }

    @Test
    fun otherFailuresOfferToTryAgain() {
        show(NowPlayingUiState.Unavailable(SpotifyError.NOT_LOGGED_IN))

        composeRule.onNodeWithText("Entre na sua conta no app do Spotify e tente de novo.").assertExists()
        composeRule.onNodeWithText("Tentar de novo").performClick()

        assertEquals(1, retries)
    }
}
```
Antes de escrever, confira o rótulo do botão do `ErrorState` em `core/ui/src/main/res/values/strings.xml`; se não for "Tentar de novo", use o rótulo real no teste.

`NowPlayingScreenshotTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.nowplaying

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.model.MatchStatus
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
class NowPlayingScreenshotTest {
    @Test
    fun matched() = snapshot("NowPlaying_matched") {
        content(NowPlayingUiState.Playing(playing(MatchStatus.Matched(chart(), 0))))
    }

    @Test
    fun suggested() = snapshot("NowPlaying_suggested") {
        content(NowPlayingUiState.Playing(playing(MatchStatus.Suggested(chart(), 0.9f))))
    }

    @Composable
    private fun content(state: NowPlayingUiState) = NowPlayingContent(
        state = state,
        onRetry = {},
        onPlay = {},
        onConfirm = { _, _, _ -> },
        onChooseChart = { _, _ -> },
        onOpenLibrary = {},
    )

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme { StageBackground(Modifier.fillMaxSize()) { content() } }
        }
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :feature:nowplaying:testDebugUnitTest --console=plain`
Expected: FAIL na compilação com `Unresolved reference 'NowPlayingContent'` e `'PlayRequest'`.

- [ ] **Step 3: Implementar**

`feature/nowplaying/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="nowplaying_title">Tocando agora</string>
    <string name="nowplaying_library">Biblioteca</string>
    <string name="nowplaying_connecting">Conectando ao Spotify…</string>
    <string name="nowplaying_not_installed_title">Spotify não instalado</string>
    <string name="nowplaying_not_installed_message">Instale o app do Spotify para jogar com a música que está tocando. Enquanto isso, a biblioteca joga com o áudio das pastas.</string>
    <string name="nowplaying_not_configured_title">Spotify não configurado</string>
    <string name="nowplaying_not_configured_message">Este build não tem o spotify.clientId no local.properties.</string>
    <string name="nowplaying_open_library">Abrir biblioteca</string>
    <string name="nowplaying_error_not_logged_in">Entre na sua conta no app do Spotify e tente de novo.</string>
    <string name="nowplaying_error_not_authorized">O Riff não foi autorizado no Spotify. Tente de novo e aceite o pedido de acesso.</string>
    <string name="nowplaying_error_offline">O Spotify está no modo offline.</string>
    <string name="nowplaying_error_disconnected">A conexão com o Spotify caiu. Tentando reconectar…</string>
    <string name="nowplaying_error_unknown">Não deu para conectar ao Spotify.</string>
    <string name="nowplaying_nothing_title">Nada tocando</string>
    <string name="nowplaying_nothing_message">Toque uma música no Spotify e ela aparece aqui.</string>
    <string name="nowplaying_paused">Pausado no Spotify</string>
    <string name="nowplaying_chart">Mapa: %1$s — %2$s</string>
    <string name="nowplaying_play">Jogar</string>
    <string name="nowplaying_suggestion">Este mapa parece ser desta música: %1$s — %2$s</string>
    <string name="nowplaying_confirm">Confirmar mapa</string>
    <string name="nowplaying_choose_other">Escolher outro</string>
    <string name="nowplaying_no_match">Nenhum mapa da biblioteca casa com esta música.</string>
    <string name="nowplaying_choose">Escolher mapa</string>
    <string name="nowplaying_badge_matched">Mapa encontrado</string>
    <string name="nowplaying_badge_suggested">Confirmar mapa</string>
    <string name="nowplaying_badge_none">Sem mapa</string>
</resources>
```
`values-en/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="nowplaying_title">Now playing</string>
    <string name="nowplaying_library">Library</string>
    <string name="nowplaying_connecting">Connecting to Spotify…</string>
    <string name="nowplaying_not_installed_title">Spotify not installed</string>
    <string name="nowplaying_not_installed_message">Install the Spotify app to play along with what\'s playing. Meanwhile, the library plays with the folders\' audio.</string>
    <string name="nowplaying_not_configured_title">Spotify not configured</string>
    <string name="nowplaying_not_configured_message">This build has no spotify.clientId in local.properties.</string>
    <string name="nowplaying_open_library">Open library</string>
    <string name="nowplaying_error_not_logged_in">Log in to the Spotify app and try again.</string>
    <string name="nowplaying_error_not_authorized">Riff wasn\'t authorized on Spotify. Try again and accept the access request.</string>
    <string name="nowplaying_error_offline">Spotify is in offline mode.</string>
    <string name="nowplaying_error_disconnected">The connection to Spotify dropped. Reconnecting…</string>
    <string name="nowplaying_error_unknown">Couldn\'t connect to Spotify.</string>
    <string name="nowplaying_nothing_title">Nothing playing</string>
    <string name="nowplaying_nothing_message">Play a song on Spotify and it shows up here.</string>
    <string name="nowplaying_paused">Paused on Spotify</string>
    <string name="nowplaying_chart">Chart: %1$s — %2$s</string>
    <string name="nowplaying_play">Play</string>
    <string name="nowplaying_suggestion">This chart looks like this song: %1$s — %2$s</string>
    <string name="nowplaying_confirm">Confirm chart</string>
    <string name="nowplaying_choose_other">Choose another</string>
    <string name="nowplaying_no_match">No chart in the library matches this song.</string>
    <string name="nowplaying_choose">Choose chart</string>
    <string name="nowplaying_badge_matched">Chart found</string>
    <string name="nowplaying_badge_suggested">Confirm chart</string>
    <string name="nowplaying_badge_none">No chart</string>
</resources>
```
O `StatusBadge` já escreve em maiúsculas (spec visual §4); por isso os testes procuram "MAPA ENCONTRADO". Confira em `StatusBadge.kt`; se ele não transformar o texto, a string do carimbo deve vir em maiúsculas.

`NowPlayingScreen.kt`:
```kotlin
package com.rodrigomachado.riff.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.SpotifyError
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.DifficultyDialog
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.component.difficultyLabel
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** O que "Jogar" leva para a partida. */
data class PlayRequest(val chartId: String, val difficulty: Difficulty, val trackUri: String, val trackOffsetMs: Int)

@Composable
fun NowPlayingScreen(
    onPlay: (PlayRequest) -> Unit,
    onChooseChart: (trackUri: String, title: String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NowPlayingContent(
        state = state,
        onRetry = viewModel::retry,
        onPlay = onPlay,
        onConfirm = viewModel::confirmSuggestion,
        onChooseChart = onChooseChart,
        onOpenLibrary = onOpenLibrary,
        modifier = modifier,
    )
}

@Composable
fun NowPlayingContent(
    state: NowPlayingUiState,
    onRetry: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onConfirm: (trackUri: String, chartId: String, confidence: Float) -> Unit,
    onChooseChart: (trackUri: String, title: String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        PosterTitle(
            stringResource(R.string.nowplaying_title),
            Modifier.padding(top = Spacing.md),
            style = MaterialTheme.typography.displaySmall,
        )
        TextButton(onClick = onOpenLibrary) {
            Text(stringResource(R.string.nowplaying_library), color = RiffColors.Paper)
        }
        when (state) {
            NowPlayingUiState.Connecting -> LoadingState(message = stringResource(R.string.nowplaying_connecting))
            is NowPlayingUiState.Unavailable -> Unavailable(state.error, onRetry, onOpenLibrary)
            NowPlayingUiState.NothingPlaying -> EmptyState(
                title = stringResource(R.string.nowplaying_nothing_title),
                message = stringResource(R.string.nowplaying_nothing_message),
            )
            is NowPlayingUiState.Playing -> Playing(state.nowPlaying, onPlay, onConfirm, onChooseChart)
        }
    }
}

@Composable
private fun Unavailable(error: SpotifyError, onRetry: () -> Unit, onOpenLibrary: () -> Unit) {
    when (error) {
        SpotifyError.NOT_INSTALLED -> EmptyState(
            title = stringResource(R.string.nowplaying_not_installed_title),
            message = stringResource(R.string.nowplaying_not_installed_message),
            actionLabel = stringResource(R.string.nowplaying_open_library),
            onAction = onOpenLibrary,
        )
        SpotifyError.NOT_CONFIGURED -> EmptyState(
            title = stringResource(R.string.nowplaying_not_configured_title),
            message = stringResource(R.string.nowplaying_not_configured_message),
            actionLabel = stringResource(R.string.nowplaying_open_library),
            onAction = onOpenLibrary,
        )
        else -> ErrorState(stringResource(error.message()), onRetry = onRetry)
    }
}

private fun SpotifyError.message(): Int = when (this) {
    SpotifyError.NOT_LOGGED_IN -> R.string.nowplaying_error_not_logged_in
    SpotifyError.NOT_AUTHORIZED -> R.string.nowplaying_error_not_authorized
    SpotifyError.OFFLINE -> R.string.nowplaying_error_offline
    SpotifyError.DISCONNECTED -> R.string.nowplaying_error_disconnected
    else -> R.string.nowplaying_error_unknown
}

@Composable
private fun Playing(
    nowPlaying: NowPlaying,
    onPlay: (PlayRequest) -> Unit,
    onConfirm: (trackUri: String, chartId: String, confidence: Float) -> Unit,
    onChooseChart: (trackUri: String, title: String) -> Unit,
) {
    val track = nowPlaying.playback
    var choosing by remember { mutableStateOf<MatchStatus.Matched?>(null) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        TrackCard(title = track.title, artist = track.artist, tilted = true, badge = { MatchBadge(nowPlaying.match) })
        if (track.isPaused) {
            Text(stringResource(R.string.nowplaying_paused), color = RiffColors.PaperDim)
        }
        when (val match = nowPlaying.match) {
            is MatchStatus.Matched -> {
                Text(stringResource(R.string.nowplaying_chart, match.chart.title, match.chart.artist), color = RiffColors.Paper)
                PosterButton(stringResource(R.string.nowplaying_play), onClick = { choosing = match })
            }
            is MatchStatus.Suggested -> {
                Text(stringResource(R.string.nowplaying_suggestion, match.chart.title, match.chart.artist), color = RiffColors.Paper)
                PosterButton(
                    stringResource(R.string.nowplaying_confirm),
                    onClick = { onConfirm(track.trackUri, match.chart.id, match.confidence) },
                )
                TextButton(onClick = { onChooseChart(track.trackUri, track.title) }) {
                    Text(stringResource(R.string.nowplaying_choose_other), color = RiffColors.Paper)
                }
            }
            MatchStatus.NoMatch -> {
                Text(stringResource(R.string.nowplaying_no_match), color = RiffColors.Paper)
                PosterButton(stringResource(R.string.nowplaying_choose), onClick = { onChooseChart(track.trackUri, track.title) })
            }
        }
    }
    choosing?.let { match ->
        DifficultyDialog(
            title = match.chart.title,
            options = match.chart.difficulties.sorted(),
            label = { stringResource(difficultyLabel(it.ordinal)) },
            onChoose = { difficulty ->
                choosing = null
                onPlay(PlayRequest(match.chart.id, difficulty, track.trackUri, match.trackOffsetMs))
            },
            onDismiss = { choosing = null },
        )
    }
}

@Composable
private fun MatchBadge(match: MatchStatus) {
    when (match) {
        is MatchStatus.Matched -> StatusBadge(stringResource(R.string.nowplaying_badge_matched), BadgeTone.SUCCESS)
        is MatchStatus.Suggested -> StatusBadge(stringResource(R.string.nowplaying_badge_suggested), BadgeTone.WARNING)
        MatchStatus.NoMatch -> StatusBadge(stringResource(R.string.nowplaying_badge_none), BadgeTone.NEUTRAL)
    }
}
```
(Se o import `Chart` ficar sem uso, remova-o; o Detekt avisa.)

- [ ] **Step 4: Rodar, gravar os screenshots e conferir**

Run: `./scripts/gw :feature:nowplaying:recordRoborazziDebug --console=plain` e leia `NowPlaying_matched.png` e `NowPlaying_suggested.png`: título "Tocando agora" em letreiro, cartão inclinado com o carimbo, texto do mapa e botão vermelho; nenhum texto cortado.
Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt lintDebug :feature:nowplaying:testDebugUnitTest :feature:nowplaying:verifyRoborazziDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; `NowPlayingContentTest` 5/5.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(nowplaying): tela Tocando agora com o mapa casado" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Navegação começando em "Tocando agora"

**Files:**
- Modify: `app/build.gradle.kts` (`implementation(project(":feature:nowplaying"))`)
- Modify: `app/src/main/kotlin/com/rodrigomachado/riff/RiffNavHost.kt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/navigation/DropUnlessResumed.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/navigation/DropUnlessResumedTest.kt`

**Interfaces:**
- Consumes: `NowPlayingRoute`, `NowPlayingScreen`, `PlayRequest` (Tasks 9 e 10); `LibraryRoute`, `TrackToMatch` (Task 6); `GameplayRoute` (Task 7); `ResultsRoute` (Task 8).
- Produces: `@Composable fun <A> dropUnlessResumed(block: (A) -> Unit): (A) -> Unit` (um parâmetro); NavHost com início em `NowPlayingRoute`.

**Fluxo:**
```
NowPlaying ─[Jogar]→ Gameplay(spotify) ─→ Results ─[Voltar]→ NowPlaying
    ├─[Escolher mapa]→ Library(match) ─[mapa]→ NowPlaying (casamento salvo)
    └─[Biblioteca]→ Library ─[Jogar]→ Gameplay(local) ─→ Results ─[Voltar]→ Library
```

- [ ] **Step 1: Escrever o teste que falha**

Em `DropUnlessResumedTest.kt`, acrescente:
```kotlin
    @Test
    fun singleParameterActionsAreDroppedToo() {
        val calls = mutableListOf<String>()
        lateinit var action: (String) -> Unit
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                action = dropUnlessResumed { a: String -> calls += a }
            }
        }

        composeRule.runOnIdle { action("a") }
        owner.registry.currentState = Lifecycle.State.STARTED
        composeRule.runOnIdle { action("b") }

        assertEquals(listOf("a"), calls)
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:ui:testDebugUnitTest --tests '*DropUnlessResumed*' --console=plain`
Expected: FAIL na compilação (sobrecarga de um parâmetro não existe).

- [ ] **Step 3: Implementar**

Em `DropUnlessResumed.kt`, acrescente:
```kotlin
/** Igual ao de dois parâmetros, para ações com um parâmetro. */
@Composable
fun <A> dropUnlessResumed(block: (A) -> Unit): (A) -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return { a -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(a) }
}
```

`RiffNavHost.kt`:
```kotlin
package com.rodrigomachado.riff

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rodrigomachado.riff.core.ui.navigation.dropUnlessResumed
import com.rodrigomachado.riff.feature.gameplay.GameplayRoute
import com.rodrigomachado.riff.feature.gameplay.GameplayScreen
import com.rodrigomachado.riff.feature.library.LibraryRoute
import com.rodrigomachado.riff.feature.library.LibraryScreen
import com.rodrigomachado.riff.feature.library.TrackToMatch
import com.rodrigomachado.riff.feature.nowplaying.NowPlayingRoute
import com.rodrigomachado.riff.feature.nowplaying.NowPlayingScreen
import com.rodrigomachado.riff.feature.results.ResultsRoute
import com.rodrigomachado.riff.feature.results.ResultsScreen

/**
 * Tocando agora → Jogo → Resultado, e a biblioteca (jogar com o áudio da pasta ou escolher o mapa de
 * uma música). As features não se conhecem; a ligação é feita aqui. Cada ação só vale com a tela em
 * primeiro plano, para um toque duplo não navegar duas vezes.
 */
@Composable
fun RiffNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = NowPlayingRoute, modifier = modifier) {
        composable<NowPlayingRoute> {
            NowPlayingScreen(
                onPlay = dropUnlessResumed { request ->
                    navController.navigate(
                        GameplayRoute(request.chartId, request.difficulty.name, request.trackUri, request.trackOffsetMs),
                    )
                },
                onChooseChart = dropUnlessResumed { trackUri, title ->
                    navController.navigate(LibraryRoute(matchTrackUri = trackUri, matchTrackTitle = title))
                },
                onOpenLibrary = dropUnlessResumed { navController.navigate(LibraryRoute()) },
            )
        }
        composable<LibraryRoute> { entry ->
            val route = entry.toRoute<LibraryRoute>()
            LibraryScreen(
                onPlay = dropUnlessResumed { chartId, difficulty ->
                    navController.navigate(GameplayRoute(chartId, difficulty.name))
                },
                match = route.matchTrackUri?.let { TrackToMatch(it, route.matchTrackTitle.orEmpty()) },
                onMatched = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable<GameplayRoute> { entry ->
            val route = entry.toRoute<GameplayRoute>()
            GameplayScreen(
                onFinished = { scoreId ->
                    navController.navigate(ResultsRoute(scoreId, route.spotifyTrackUri, route.trackOffsetMs)) {
                        popUpTo<GameplayRoute> { inclusive = true }
                    }
                },
                onExit = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable<ResultsRoute> { entry ->
            val route = entry.toRoute<ResultsRoute>()
            ResultsScreen(
                onPlayAgain = dropUnlessResumed { chartId, difficulty ->
                    navController.navigate(
                        GameplayRoute(chartId, difficulty.name, route.spotifyTrackUri, route.trackOffsetMs),
                    ) { popUpTo<ResultsRoute> { inclusive = true } }
                },
                onDone = dropUnlessResumed { navController.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 4: Verificação completa**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt lintDebug test koverVerify :core:ui:verifyRoborazziDebug :feature:library:verifyRoborazziDebug :feature:gameplay:verifyRoborazziDebug :feature:results:verifyRoborazziDebug :feature:nowplaying:verifyRoborazziDebug assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(app): navegação começando em Tocando agora" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Partida real com o Spotify, especificação e README

**Files:**
- Modify: `docs/superpowers/specs/2026-10-05-riff-design.md`
- Modify: `README.md`
- Modify: `.github/workflows/*.yml` (só se o CI listar módulos de screenshot explicitamente: acrescente `:feature:nowplaying:verifyRoborazziDebug`)

- [ ] **Step 1: Preparar o aparelho (precisa do usuário)**

Este passo depende do usuário; peça e espere:
- Um aparelho com o app do Spotify instalado e logado numa conta Premium: o celular dele via `adb` (depuração USB) ou o emulador Pixel_4_2 com o Spotify instalado pela Play Store e logado.
- Um mapa do Clone Hero de uma música que exista no Spotify, na pasta `Download/RiffCharts` do aparelho (o mapa "Metronomo" não tem música equivalente no Spotify).
- Confirmar que o app no Developer Dashboard tem o package `com.rodrigomachado.riff`, o SHA-1 `2C:79:DC:4D:8B:F5:89:5C:62:A8:DC:24:1E:B9:EF:1F:62:32:D2:1C` e o redirect `riff://callback`.

- [ ] **Step 2: Jogar de verdade**

Instale o APK e, com `uiautomator dump`/`input tap` (ou pedindo ao usuário quando a ação exigir a conta), confira e registre no ledger:
1. O app abre em "Tocando agora" e conecta; na primeira vez o Spotify mostra o pedido de autorização (aceitar).
2. Sem música: "Nada tocando". Tocando a música do mapa: cartão com a faixa e o carimbo (automático ou "Confirmar mapa").
3. Se pedir confirmação, "Confirmar mapa" vira "Mapa encontrado". Com outra música, "Escolher mapa" abre a biblioteca em modo de escolha e, ao tocar no mapa, volta com "Mapa encontrado".
4. "Jogar" → dificuldade → a música recomeça do início no Spotify e as notas descem no ritmo (capture duas telas com 1 s de intervalo; o usuário confirma a sincronia de ouvido).
5. Pausar pela notificação do Spotify: o jogo mostra "Pausado"; retomar pela notificação: o jogo continua.
6. "Continuar" e "Sair" pelo jogo: sair pausa a música no Spotify.
7. Pular para a próxima faixa no Spotify no meio da partida: vai para o resultado; "Jogar de novo" recomeça a mesma música pelo Spotify; "Voltar" volta para "Tocando agora".
8. Fechar o app do Spotify (`am force-stop com.spotify.music`) no meio da partida: o jogo pausa sem crash; abrir o Spotify de novo e conferir que o Riff reconecta.
9. "Biblioteca" → "Jogar" com o mapa "Metronomo" continua funcionando com o áudio da pasta.
10. Nenhum `FATAL EXCEPTION` no `logcat`.

Problema encontrado aqui (por exemplo, a conexão exigir uma Activity em vez do `ApplicationContext`) é corrigido com TDD quando der para testar, ou com uma ruling no ledger explicando a validação manual.

- [ ] **Step 3: Especificação e README**

Na spec principal:
- §6, troque o diagrama por:
```
ConnectSpotify/NowPlaying ──[Jogar]──► Gameplay ──► Results ─[Voltar]─► NowPlaying
        ├─ (sem mapa ou confiança média) ─► Library (escolher o mapa) ─► NowPlaying
        ├─► Library ─[Jogar com o áudio da pasta]─► Gameplay ──► Results ─[Voltar]─► Library
        ├─► Calibration
        └─► Settings
```
- §4 Sincronização, item 4: "Se a faixa trocar, a partida termina e o resultado é salvo (vale o que foi jogado); \"Voltar\" leva para \"Tocando agora\"."
- §11, no passo 6, acrescente: "Implementado. A conexão é do App Remote SDK 0.8.0 (AAR em `third_party/maven`); \"Tocando agora\" é a tela inicial. Decisão de 2026-10-08: o modo com o áudio da pasta continua na biblioteca."

No `README.md`:
- Tabela de módulos:
```markdown
| `:core:spotify` | Conexão com o app do Spotify (App Remote SDK), estado do player e o player da partida sobre o Spotify |
| `:feature:nowplaying` | Tela inicial "Tocando agora": faixa atual, mapa casado, confirmar ou escolher o mapa e jogar |
```
- Linha do `:app`: `| \`:app\` | Activity, Application com Hilt e navegação Tocando agora → Jogo → Resultado (e a Biblioteca) |`.
- Nova seção antes de "Screenshots":
```markdown
## Spotify

1. Crie um app em https://developer.spotify.com/dashboard com o redirect URI `riff://callback`, a API Android, o package `com.rodrigomachado.riff` e o SHA-1 da sua chave de debug (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`).
2. No `local.properties` (fora do git):
   ```
   spotify.clientId=<Client ID do Dashboard>
   spotify.redirectUri=riff://callback
   ```
3. O aparelho precisa do app do Spotify instalado e logado numa conta Premium.

Sem `spotify.clientId`, o app compila e mostra "Spotify não configurado"; a biblioteca continua jogando com o áudio das pastas. O AAR do SDK está em `third_party/maven` (veja `third_party/README.md`).
```
- Status: `Fundação, identidade visual, biblioteca, gameplay e integração com o Spotify prontos. Próximos planos: calibração e configurações.`

- [ ] **Step 4: Verificação e commit**

Run: `./scripts/gw detekt lintDebug test koverVerify :core:ui:verifyRoborazziDebug :feature:library:verifyRoborazziDebug :feature:gameplay:verifyRoborazziDebug :feature:results:verifyRoborazziDebug :feature:nowplaying:verifyRoborazziDebug assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A
git commit -m "docs: integração com o Spotify na especificação e no README" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
