# Riff: design do MVP

Data: 2026-10-05 · Status: aprovado em conversa, aguardando revisão do documento

> **Identidade visual:** definida em `2026-10-07-identidade-visual-design.md` (tema escuro único "Pôster de turnê", sem Dynamic Color). Ela prevalece sobre qualquer menção a tema claro ou Dynamic Color neste documento.

## 1. Objetivo

App Android de ritmo, no estilo Guitar Hero, em que o jogador toca junto a música que está tocando no Spotify. As notas vêm de mapas do Clone Hero que o próprio usuário importa.

- **Público:** uso pessoal, com Spotify Premium e o app do Spotify instalado. O app do Spotify fica em modo de desenvolvimento (até 25 usuários cadastrados).
- **Critério de sucesso:** com uma música tocando no Spotify e um mapa correspondente importado, o jogador aperta "Jogar" e as notas descem sincronizadas com o áudio, com erro percebido menor que cerca de 30 ms depois da calibração. Ele acerta notas, vê combo e pontuação, e o resultado fica salvo.

## 2. Escopo

### MVP
1. **Conexão com o Spotify** via App Remote SDK e tela "Tocando agora" com a faixa atual.
2. **Importação de mapas do Clone Hero:** o usuário escolhe uma pasta pelo seletor do Android (Storage Access Framework). Cada subpasta com `song.ini` e `notes.chart` ou `notes.mid` vira um mapa. O casamento com a faixa do Spotify é automático, com confirmação ou escolha manual quando a confiança é baixa.
3. **Gameplay:** 5 trilhas, o jogador toca a trilha na linha de acerto. O jogo julga cada toque como Perfeito, Bom ou Erro, conta combo, aplica multiplicador (1x a 4x, sobe a cada 10 acertos seguidos) e suporta notas longas. Segue pausa e troca de faixa do Spotify. Visual de palco com gemas e botões 3D, chama no acerto e sons de acerto e erro (veja `2026-10-07-identidade-visual-design.md`).
4. **Calibração:** um offset global de áudio, medido com uma batida de referência tocada pelo próprio app, mais um ajuste fino opcional por música.
5. **Resultado e histórico:** a melhor pontuação por música e dificuldade.
6. **Configurações:** velocidade das notas, modo canhoto (trilhas espelhadas), dificuldade padrão e volume dos efeitos sonoros.

### Fora do MVP
Star power, multiplayer, download de mapas da internet, geração automática de notas, input de deslizar ou palhetar, iOS, conta e nuvem, Crashlytics e publicação na Play Store.

## 3. Restrições externas

- **Spotify App Remote SDK:** exige o app do Spotify instalado e o app registrado no Developer Dashboard (package name, SHA-1 e redirect URI). As credenciais ficam em `local.properties` e nunca no repositório.
- **O Spotify não expõe o áudio nem os dados de batida** (Audio Analysis foi bloqueado para apps novos em 2024). O ritmo vem exclusivamente dos mapas.
- **A política de desenvolvedor do Spotify restringe jogos e sincronização visual com as músicas.** Por isso o projeto é de uso pessoal e não será publicado.

## 4. Arquitetura

Clean Architecture (data / domain / ui) com MVVM e estado unidirecional: cada tela tem um `UiState` imutável e recebe eventos.

### Módulos

| Módulo | Tipo | Responsabilidade |
|---|---|---|
| `:app` | Android app | Activity, NavHost com rotas type-safe, Hilt |
| `:feature:nowplaying` | Android lib | Faixa atual, status do mapa casado, botão Jogar |
| `:feature:gameplay` | Android lib | Pista em Compose Canvas, input por toque, HUD |
| `:feature:results` | Android lib | Resultado da partida e recorde |
| `:feature:library` | Android lib | Importar pasta, listar mapas, casar manualmente |
| `:feature:calibration` | Android lib | Medição do offset global |
| `:feature:settings` | Android lib | Preferências |
| `:core:model` | Kotlin/JVM | Modelos de domínio |
| `:core:chart` | Kotlin/JVM | Parsers de `.chart`, `.mid` e `song.ini`, conversão de tick para ms |
| `:core:game` | Kotlin/JVM | Relógio sincronizado, julgamento e pontuação |
| `:core:spotify` | Android lib | Wrapper do App Remote, expõe `Flow<PlaybackState>` |
| `:core:data` | Android lib | Repositórios, importação e casamento entre faixa e mapa |
| `:core:database` | Android lib | Room |
| `:core:datastore` | Android lib | DataStore de preferências |
| `:core:ui` | Android lib | Design system "Pôster de turnê" (tema escuro único, tipografia, componentes e peças de desenho da pista) |
| `build-logic/` | Gradle | Convention plugins |

`:core:chart` e `:core:game` são Kotlin puro: a lógica crítica de tempo roda em testes JVM rápidos, sem emulador.

Dependências permitidas: `feature → core:data, core:ui, core:model`. `core:data → core:database, core:datastore, core:spotify, core:chart, core:model`. Uma feature nunca depende de outra feature.

### Sincronização (a parte crítica)

1. `:core:spotify` assina `PlayerApi.subscribeToPlayerState()`. A cada evento, emite `PlaybackState(positionMs, isPaused, trackUri, capturedAtElapsedMs = SystemClock.elapsedRealtime())`.
2. O `SongClock` (em `:core:game`) calcula a cada frame: `songTimeMs = positionMs + (agora − capturedAt) + audioOffsetMs + trackOffsetMs`. Se a música estiver pausada, o tempo congela.
3. A cada 2 segundos, uma releitura do estado gera uma nova referência. Desvios menores que 80 ms são corrigidos aos poucos (no máximo 5 ms por frame), para as notas não pularem. Desvios maiores são aplicados de uma vez, porque indicam que o usuário mexeu na barra de progresso.
4. Se a faixa trocar, a partida termina e volta para "Tocando agora".
5. O relógio recebe a fonte de tempo como interface (`TimeSource`), o que permite testar com um relógio falso.

### Renderização e input

- **Desenho:** Compose `Canvas` dentro de um loop `withFrameNanos`. Só as notas na janela visível são desenhadas, encontradas por busca binária no tempo.
- **Input:** `pointerInput` detecta toques com vários dedos. Cada toque gera `Hit(lane, songTimeMs)`, entregue ao motor do jogo. Nota longa conta enquanto o dedo fica pressionado.

### Julgamento e pontuação (`:core:game`)

- **Janelas:** Perfeito se o erro for de até 50 ms, Bom até 100 ms, Erro acima disso. Uma nota que passa da janela sem ser tocada também conta como Erro. Um toque em trilha sem nota próxima quebra o combo.
- **Pontuação:** Perfeito vale 100 e Bom vale 50, multiplicados pelo multiplicador. Como no Guitar Hero, os 10 primeiros acertos seguidos valem x1, do 11º ao 20º valem x2, do 21º ao 30º x3 e daí em diante x4. Nota longa soma 25 pontos a cada 500 ms sustentados (o equivalente a um beat a 120 BPM; o motor trabalha em ms e não conhece o BPM).
- **Precisão:** `(perfeitos + 0,5 × bons) / total de notas`.

## 5. Modelo de dados

### Domínio (`:core:model`)

```kotlin
data class Chart(val id: String, val title: String, val artist: String, val album: String?,
                 val charter: String?, val durationMs: Long?, val folderUri: String,
                 val format: ChartFormat, val difficulties: Set<Difficulty>)
enum class ChartFormat { CHART, MIDI }
enum class Difficulty { EASY, MEDIUM, HARD, EXPERT }
data class Note(val timeMs: Long, val lane: Int /* 0..4 */, val sustainMs: Long, val isHopo: Boolean)
data class ChartTrack(val chartId: String, val difficulty: Difficulty, val notes: List<Note>) // em ordem de tempo
data class PlaybackState(val trackUri: String, val title: String, val artist: String,
                         val durationMs: Long, val positionMs: Long, val isPaused: Boolean,
                         val capturedAtElapsedMs: Long)
data class TrackMatch(val spotifyTrackUri: String, val chartId: String,
                      val confidence: Float, val isManual: Boolean, val trackOffsetMs: Int)
enum class Judgment { PERFECT, GOOD, MISS }
data class GameState(val songTimeMs: Long, val score: Long, val combo: Int, val maxCombo: Int,
                     val multiplier: Int, val counts: Map<Judgment, Int>, val activeSustains: Set<Int>)
data class ScoreRecord(val id: Long, val chartId: String, val difficulty: Difficulty, val score: Long,
                       val accuracy: Float, val maxCombo: Int, val perfect: Int, val good: Int,
                       val miss: Int, val playedAtEpochMs: Long)
data class Settings(val audioOffsetMs: Int, val noteSpeed: Float, val leftHanded: Boolean,
                    val defaultDifficulty: Difficulty, val sfxVolume: Float /* 0..1, padrão 0,6 */)
```

`Chart.id` é o hash SHA-1 do arquivo de notas, então reimportar o mesmo mapa não duplica nada.

### Persistência

- **Room:** as tabelas `charts`, `chart_difficulties`, `track_matches` (chave `spotifyTrackUri`) e `scores`.
- **DataStore:** `Settings`.
- **As notas não são persistidas.** São lidas e convertidas do arquivo, via SAF, quando a partida começa.

### Casamento entre faixa e mapa

1. Normalização de título e artista: minúsculas, sem acento, sem `(feat. …)`, `- Remastered …`, `- Live …` e pontuação.
2. Pontuação = `0,6 × sim(título) + 0,4 × sim(artista)`, com similaridade Jaro-Winkler. Se a duração diferir mais de 5 segundos, a pontuação cai 0,1.
3. Com pontuação de pelo menos 0,92, o casamento é automático. Entre 0,75 e 0,92, o app sugere e pede confirmação. Abaixo de 0,75, o usuário escolhe manualmente na biblioteca.
4. Um casamento manual ou confirmado é salvo em `track_matches` e tem prioridade nas próximas vezes.

## 6. Telas e navegação

```
ConnectSpotify ─► NowPlaying ──[Jogar]──► Gameplay ──► Results ─► NowPlaying
                     ├─ (sem mapa ou confiança média) ─► Library (casar)
                     ├─► Library ─► Importar pasta (SAF)
                     ├─► Calibration
                     └─► Settings
```

Estados obrigatórios em cada tela: carregando, vazio (com orientação do que fazer) e erro (com botão de tentar de novo). Exemplos de erro: Spotify não instalado, autorização negada, pasta inacessível, mapa inválido.

## 7. Tratamento de erros

- Os repositórios retornam `Result<T>` ou uma `sealed interface` de erro de domínio: `SpotifyNotInstalled`, `SpotifyAuthFailed`, `SpotifyDisconnected`, `ChartParseError(path, line, reason)`, `FolderAccessLost`.
- Na importação, um mapa inválido não interrompe as outras pastas. O relatório final mostra quantos foram importados e quantos falharam, e por quê.
- Se o Spotify desconectar no meio da partida, o jogo pausa e tenta reconectar com backoff.

## 8. Requisitos não funcionais

- **Strings:** todas em resources, pt-BR como padrão e en como segunda língua.
- **Acessibilidade fora do gameplay:** contentDescription, alvos de toque de 48dp e fontes escaláveis. O gameplay é visual por natureza, mas avisa o resultado final via TalkBack.
- **Segurança:** client ID e redirect URI em `local.properties`, lidos via `BuildConfig`, e R8 ativo no release.
- **Desempenho:** 60 fps no gameplay em um aparelho intermediário, sem alocar memória no loop de frame. Baseline Profile entra na fase final.
- **Versões:** minSdk 26, targetSdk e compileSdk na versão mais recente estável.

## 9. Testes

- **`:core:chart`:** parsing de `.chart` (resolução, mudança de BPM no meio, notas longas, HOPO), `.mid` (pistas PART GUITAR por dificuldade) e `song.ini`, com arquivos de exemplo mínimos escritos à mão em `src/test/resources`.
- **`:core:game`:** `SongClock` (extrapolação, pausa, correção suave versus salto), julgamento nas bordas das janelas, combo e multiplicador, notas longas e precisão.
- **`:core:data`:** normalização e pontuação do casamento, importação com mapas válidos e inválidos (Room em memória).
- **ViewModels:** Turbine + MockK.
- **UI:** Compose UI Test com testTags nas telas principais. Roborazzi nos componentes de `:core:ui`.
- **E2E:** Maestro só para fluxos que não dependem do Spotify (biblioteca, configurações e calibração). O fluxo com Spotify é testado manualmente, porque exige o app e uma conta.
- **Ferramentas:** JUnit 5 (JUnit 4 só nos testes Robolectric e Roborazzi, que exigem o runner dele), MockK, Turbine, kotlinx-coroutines-test, Kover (meta de 70% em `:core:chart`, `:core:game` e `:core:data`), Detekt com as regras do ktlint (wrapper oficial) e Android Lint.

## 10. CI

GitHub Actions a cada PR: ktlint, Detekt, Lint, testes unitários, Kover e `assembleDebug`. O CI não precisa de credenciais do Spotify: o build usa valores vazios e o SDK só é exercitado em execução real. Não há pipeline de release na Play Store (ver seção 3).

## 11. Ordem de implementação

1. Setup: projeto Gradle, version catalog, `build-logic`, Detekt e ktlint, CI e `:core:ui` (design system).
2. `:core:model` e `:core:chart` (parsers), com testes.
3. `:core:game` (relógio, julgamento, pontuação), com testes.
4. `:core:database`, `:core:datastore`, importação e casamento em `:core:data`, e a tela `:feature:library`.
5. `:feature:gameplay` + `:feature:results`, jogáveis com um relógio local (sem Spotify), para validar a sensação do jogo.
6. `:core:spotify` + `:feature:nowplaying`: sincronização real.
7. `:feature:calibration` + `:feature:settings`.
8. Polimento: Baseline Profile e README final.

Os passos 5 e 6 ficam separados para validar o gameplay antes de depender do Spotify. Assim dá para testar com o áudio de qualquer mapa (o `song.ogg` da pasta) tocando localmente, só em modo de depuração.
