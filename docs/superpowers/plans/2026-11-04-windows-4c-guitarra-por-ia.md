# Riff no Windows — Etapa 4c: guitarra separada por IA no PC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No PC, como no celular: a Biblioteca pergunta antes de jogar uma música sem guitarra separada; "Separar" (ou "+ Fila") põe na fila; a IA (Demucs v4 em ONNX) separa a guitarra do resto, com o progresso na palheta do topo; depois, errar corta só a guitarra. A aba "Guitarra por IA" das Configurações aparece no PC. O celular fica igual.

**Architecture:** `:core:separation` vira multiplataforma. Comum (`commonMain`, JVM nos dois alvos): o modelo (`OrtDemucsModel`, a API `ai.onnxruntime` é a mesma no Android e no PC), STFT, `GuitarSeparator`, `StemAccumulator`, `ResamplingPcmSource`, `SongPcmLoader`, `EncoderDelay`, `GuitarSeparationRunner`, `SeparationQueueLoop`, `SeparationProgress`. Android (`androidMain`): `M4aStemSink` (MediaCodec), WorkManager, notificações, Hilt, `DeviceInfo`. PC (`desktopMain`): gravação das faixas pelo FFmpeg (bytedeco, já usado na 2b), um agendador que roda a mesma `SeparationQueueLoop` num escopo do app (só com o Riff aberto), o modelo lido dos recursos, `DeviceInfo` do PC. O `DesktopGraph` troca `NoAiSeparations`/`NoSeparationScheduler` pelos de verdade (`RoomAiSeparationRepository`, `RoomSeparationQueue`, pasta `%APPDATA%\Riff\separacoes`).

**Tech Stack:** ONNX Runtime para JVM (`com.microsoft.onnxruntime:onnxruntime`, traz o nativo do Windows), JTransforms, FFmpeg (bytedeco) para decodificar e para codificar AAC em `.m4a`.

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§2 "guitarra separada por IA também no PC"; §3 "Separar por IA com o Riff fechado: no PC a fila fica salva e roda só com o Riff aberto, continuando na próxima abertura; sem bandeja nem serviço"; riscos: "o ONNX Runtime para PC teve problema com o Java do Android Studio (msvcp140)").

## Decisões (minhas; o usuário pediu para decidir)

1. **Os mesmos arquivos do celular:** `guitar.m4a` e `backing.m4a` (AAC), gravados pelo FFmpeg no PC. O resto do Riff (tocar, apagar, contar espaço) não muda. O atraso do codificador AAC do FFmpeg é compensado como o do celular (`EncoderDelay`): a guitarra separada fica alinhada ao mapa (teste de ida e volta, ±1 ms).
2. **O modelo (130 MB, Git LFS):** um arquivo só no repositório, `core/separation/src/main/assets/htdemucs_6s_seg4.onnx` (o APK continua igual). O `:desktop` o empacota como recurso (cópia no build, sem duplicar no git) e o PC o lê na primeira separação; se faltar, a separação falha com a mensagem de erro do celular (sem travar).
3. **Versão do ONNX Runtime no PC:** a mais nova que carrega no Java que roda o Riff no PC do usuário (o `:desktop:run`), começando pela do teste (1.18.0, `onnxruntime-jvm` do catálogo). O teste dourado (`GuitarSeparatorGoldenTest`) roda também no PC. O Java do instalador fica para a 4e.
4. **Só com o Riff aberto:** a fila fica no banco do PC; ao abrir o Riff, ela continua; ao fechar, a música da vez é cancelada sem deixar arquivo pela metade e volta para a fila. Sem bandeja, sem serviço, sem notificação do Windows (o progresso fica na palheta do topo da Biblioteca e no painel da fila, que já são comuns).
5. **"Separar só carregando":** no PC fica escondida (sempre separa). A spec queria mostrar num notebook; saber se há bateria no Windows exige código nativo novo — vai para as pendências. Sem `ThermalGate` no PC.
6. **Memória pouca:** `lowMemoryDevice` no PC = memória física total < 4 GB (`OperatingSystemMXBean`), como o aviso do celular.
7. **Threads:** o ONNX Runtime do PC usa os núcleos físicos menos um (a janela e o áudio seguem fluidos); a separação roda fora da thread da janela, em prioridade baixa.
8. **Licenças:** Demucs, demucs.onnx e ONNX Runtime entram na lista manual do PC (os textos já estão em `composeResources/files/licencas`).

## Global Constraints

- O celular fica igual: testes de `:core:separation` (incluindo o dourado) com as mesmas contagens, APK com o mesmo modelo, WorkManager, notificações e "Separar só carregando" como hoje.
- Testes do PC sem o modelo de verdade quando não for o teste dourado (modelo falso, `DemucsModel` é interface); o dourado usa o modelo do LFS (pular com mensagem clara se o arquivo for só o ponteiro do LFS).
- Nada de arquivo pela metade em `separacoes`: grava em pasta temporária e troca no fim (`workDir`/`commit`, já comum).
- Navegação sem mouse (3c) na pergunta da separação, no painel da fila e na aba da IA.
- Lições: `composeResources` com `%1$d`/`%1$s` e sem `\'`/`\"`; testes do PC teclam/clicam na thread da janela; screenshots do PC com 1%; detekt sem autoCorrect; `git grep`; Gradle por `./scripts/gw`, saídas limitadas (`| tail -c 300000`); memória curta.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue` e `node scripts/*.test.js`.

## Review Focus

1. **Sincronia:** a guitarra separada no PC fica alinhada à música (o atraso do AAC do FFmpeg compensado; tocar as faixas separadas não desloca as notas). Teste de ida e volta.
2. **Fechar o Riff no meio:** nenhum arquivo pela metade, a música volta para a fila, a janela fecha na hora (sem esperar o modelo terminar um segmento longo demais), e na próxima abertura a fila segue.
3. **A janela não trava:** com a separação rodando, a Biblioteca e a partida seguem fluidas (fora da thread da UI; threads do ORT limitadas).
4. **ONNX Runtime no Windows:** carrega no Java do `:desktop:run` (msvcp140); falha de carga vira mensagem de erro da separação, não fecha o app.
5. **Celular igual** e o modelo não duplicado no git.

---

### Task 1: `:core:separation` multiplataforma (sem mudança no celular)

**Files:**
- `core/separation/build.gradle.kts` → `riff.kmp.library` (+ Hilt KMP como o `:core:data`); `commonMain` com JTransforms e o ONNX Runtime só para compilar (`compileOnly` do de JVM), `androidMain` com `onnxruntime-android` e WorkManager, `desktopMain` com `onnxruntime-jvm`.
- Move para `commonMain` os arquivos sem Android (lista na Architecture); o `Log` por função de plataforma; o que hoje usa `Context` fica no `androidMain`.
- Os testes de hoje para `androidHostTest`; os que não dependem de Android (STFT, `GuitarSeparator`, `ResamplingPcmSource`, `SongPcmLoader`, `SeparationQueueLoop`, `GuitarSeparationRunner`, o dourado) para `commonTest`/`desktopTest`, rodando nos dois.
- O modelo continua em `src/main/assets` (ou o equivalente do KMP Android) — o APK confere igual (tamanho do asset).

- [ ] **Step 1: Linha de base** — `:core:separation:testDebugUnitTest` (anotar o número e o tempo do dourado).
- [ ] **Step 2: Converter.** **Step 3: Rodar** `:core:separation:testAndroidHostTest :core:separation:desktopTest :app:assembleDebug :app:testDebugUnitTest detekt` → os mesmos testes (e os comuns também no PC), o dourado passando no PC.
- [ ] **Step 4: Commit** — `refactor: a separação por IA multiplataforma`

### Task 2: O motor no PC (modelo, ONNX Runtime e as faixas .m4a)

**Files:**
- `core/separation/src/desktopMain/.../FfmpegStemSink.kt`: grava guitarra e acompanhamento em AAC/`.m4a` (bytedeco), com o atraso do codificador medido e compensado como no `M4aStemSink`.
- `core/separation/src/desktopMain/.../DesktopSeparation.kt`: abrir o modelo dos recursos (`OrtDemucsModel` com as opções de threads da decisão 7), `DeviceInfo` do PC (decisão 6), o `GuitarSeparationRunner` montado com o `PcmSourceOpener` do PC (2b).
- `desktop/build.gradle.kts`: o modelo como recurso (cópia do asset no build).
- Tests (`core/separation/src/desktopTest`): `FfmpegStemSinkTest` (grava um seno conhecido, decodifica pelo leitor do PC, confere duração e alinhamento ±1 ms e que o arquivo abre no Riff), `DesktopSeparationRunnerTest` (modelo falso: uma música de 10 s em `.sng` e em pasta vira `guitar.m4a`/`backing.m4a` em `separacoes/<id>` com a soma ≈ original; falha do modelo → `SeparationFailure`, nenhuma pasta definitiva), o dourado no PC.

- [ ] **Step 1: Testes (RED)** → **Step 2–4:** implementar → `./scripts/gw :core:separation:desktopTest :core:separation:testAndroidHostTest detekt` PASS.
- [ ] **Step 5: Medir no PC do usuário (Windows):** tempo para separar uma música de ~3 min e o uso de CPU — anotar no README (é o número que a pessoa vê no PC).
- [ ] **Step 6: Commit** — `feat: no PC, a IA separa a guitarra`

### Task 3: A separação no app do PC

**Files:**
- `core/separation/src/desktopMain/.../DesktopSeparationScheduler.kt`: `SeparationScheduler` com a `SeparationQueueLoop` comum num escopo do app; continua a fila ao abrir; `close()` cancela a da vez e espera a limpeza (sem arquivo pela metade); `waitingForCharger` sempre `false`; `lowMemoryDevice` (decisão 6).
- `desktop/.../DesktopGraph.kt`: `RoomAiSeparationRepository` + `RoomSeparationQueue` com o banco do PC e `%APPDATA%\Riff\separacoes`; o agendador; a Biblioteca, o carregador da partida, o apagador e as Configurações com eles (no lugar de `NoAiSeparations`/`NoSeparationScheduler`, que podem sair se ninguém mais usar).
- `desktop/.../Main.kt`/`RiffDesktopApp.kt`: fechar a janela chama o `close()` do agendador.
- Configurações do PC: a aba "Guitarra por IA" volta (`tabs`), sem a linha "Separar só carregando" (parâmetro/plataforma).
- Licenças do PC: Demucs, demucs.onnx, ONNX Runtime.
- Tests (`desktop/src/test`): `DesktopSeparationFlowTest` (modelo falso e rápido): Biblioteca → música sem separação → a pergunta → Separar → a palheta mostra o progresso → depois de separada, a partida usa as faixas separadas (errar corta a guitarra); "+ Fila" com duas músicas; cancelar no painel; fechar o app no meio → pasta temporária some, a música volta à fila, reabrir (novo `DesktopGraph` com os mesmos dados) continua; aba da IA com o uso de espaço e "Apagar separações"; tudo só com o teclado na pergunta.

- [ ] **Step 1: Testes (RED)** → **Step 2–4:** implementar → `./scripts/gw :desktop:test :core:separation:desktopTest :feature:settings:desktopTest :feature:library:desktopTest detekt` PASS.
- [ ] **Step 5: No Windows (usuário):** separar uma música de verdade, jogar errando de propósito (só a guitarra some), fechar o Riff no meio de outra separação e reabrir.
- [ ] **Step 6: Commit** — `feat: no PC, a guitarra separada por IA`

### Task 4: Fechamento

- [ ] **Step 1:** Suíte completa → BUILD SUCCESSFUL.
- [ ] **Step 2:** README ("Riff no PC": IA, o tempo medido, onde ficam as separações) e `docs/pendencias.md` ("Separar só carregando" num notebook; o Java do instalador para o ONNX Runtime). Commit.
