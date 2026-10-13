# Riff: separar a guitarra com IA no celular (Projeto B)

Data: 2026-10-13 · Status: aprovado em conversa, aguardando revisão da especificação escrita
Complementa: `2026-10-13-filtros-e-sem-guitarra-design.md` (Projeto A) e `2026-10-09` (guitarra separada, mixer de faixas)

## 1. Objetivo

Músicas da Biblioteca sem guitarra separada (a maioria dos mapas do Encore traz só `song.opus`) passam a poder ter a guitarra separada **pelo próprio celular**, com IA, para jogar como no Teen Spirit: a guitarra some no erro e volta no acerto.

Restrições do usuário:

- Tudo roda no celular: sem servidor, sem PC, sem internet.
- Só no modo Biblioteca; o modo Spotify não muda.
- Separar é uma escolha do usuário, com aviso de tempo e calor.

## 2. Teste de viabilidade (2026-10-13)

Modelo `htdemucs_6s` (Demucs v4, MIT, o único aberto que separa guitarra com essa qualidade), exportado para ONNX com a STFT fora do modelo (método do `demucs.onnx`, MIT). Teen Spirit (3:33) no Galaxy S25, CPU, 4 threads:

| Execução | Tempo | Pico de RAM | Temp. máx. | Correlação com o PC |
|---|---|---|---|---|
| demucs.cpp (2026-10-12) | 9:08 | ~5,5 GB | 44,4 °C | 0,990 |
| ONNX Runtime, trechos de 7,8 s | 2:15 | 5,0 GB | 39,3 °C | 0,992 |
| ONNX Runtime, trechos de 7,8 s, sem arena | 2:58 | 3,0 GB | 41,0 °C | 0,992 |
| **ONNX Runtime, trechos de 4 s** | **1:59** | **2,8 GB** | **37,8 °C** | 0,982 |
| **ONNX Runtime, trechos de 4 s, sem arena** | 2:45 | **1,8 GB** | 40,2 °C | 0,982 |

- O usuário ouviu as versões de 7,8 s e 4 s e não notou diferença: **trechos de 4 s**.
- NPU (Hexagon, QNN): aceita 100% do modelo, mas compilar para ela estoura a memória no celular (> 7 GB) e no PC (> 13 GB, sem terminar). Descartada neste projeto.

## 3. Decisões do usuário

- **Quando:** perguntar ao jogar (não ao baixar).
- **Modelo:** dentro do app (~130 MB a mais no APK), sem download.
- **Onde guardar:** no espaço do app (não na pasta da biblioteca).
- **Como:** Kotlin + ONNX Runtime para Android (abordagem A), sem código nativo próprio.
- **Segundo plano sempre**, com um ícone clicável que mostra a porcentagem.
- Marca própria: **GUITARRA SEPARADA IA**.

## 4. O que o usuário vê

### Pergunta ao jogar

Na Biblioteca, ao tocar numa música sem guitarra separada (pasta, `.sng` ou baixada), sem separação pela IA e sem recusa guardada, depois de escolher a dificuldade:

> **Separar a guitarra com IA?**
> Separar a guitarra faz com que o som da mesma suma ao errar notas e só apareça ao acertá-las, aumentando a imersão. O processo leva em torno de 3 minutos e pode esquentar o celular. As músicas com separação se mantêm armazenadas no dispositivo.

(Texto definido pelo usuário em 2026-10-13, depois da implementação.)
> **[Separar]** · Jogar sem separar

- **Separar:** começa a separação em segundo plano e fecha a pergunta; o usuário continua na Biblioteca (não joga agora).
- **Jogar sem separar:** joga como hoje (música inteira, som de acerto e de erro) e guarda a recusa para a música: não pergunta de novo.
- Celular com menos de 4 GB de RAM: a pergunta acrescenta "Pode não funcionar neste celular." e deixa tentar.
- Sons do menu: Separar soa como confirmar; Jogar sem separar, como confirmar.

### Ícone e painel

- Enquanto há separação (rodando ou pausada), um ícone fica no topo das telas fora da partida: Tocando agora, Biblioteca, Pesquisa e Configurações. É uma palheta com um anel de progresso.
- Tocar no ícone abre um painel: nome da música, "Separando a guitarra… 45%", tempo restante estimado ("cerca de 1 min") e **Cancelar**. Pausado pelo calor: "Pausado para esfriar…".
- Ao terminar, o ícone some.

### Notificação

Notificação do Android durante a separação (obrigatória para continuar com o app fechado): nome da música e progresso. Ao fim: "Guitarra separada: <música>"; tocar abre a Biblioteca. Em falha: "Não deu para separar a guitarra de <música>".

### Biblioteca

- **GUITARRA SEPARADA IA:** separada pelo Riff, abaixo da marca da dificuldade. Jogar usa a guitarra separada.
- A marca "GUITARRA SEPARADA" (mapa que já veio com a guitarra à parte) existe só na pesquisa do Encore; a Biblioteca não sabe isso sem abrir a pasta e continua sem ela (corrigido em 2026-10-13, ao planejar).

### Configurações

Seção "Guitarra separada por IA":

- "12 músicas · 96 MB" e **Apagar separações** (com confirmação).
- **Perguntar de novo**: apaga as recusas ("Jogar sem separar").

### Uma por vez

Pedir outra separação com uma em andamento: "Já tem uma separação em andamento" e o painel abre.

## 5. Arquitetura

### Novo módulo `:core:separation`

- **`DemucsModel`** (interface): `run(time: FloatArray, spec: FloatArray): DemucsOutput` para um trecho de 4 s (entrada tempo `[1, 2, 176400]`, espectro `[1, 4, 2048, 173]`; saída espectro `[1, 6, 4, 2048, 173]` e tempo `[1, 6, 2, 176400]`). Implementação `OrtDemucsModel` com `com.microsoft.onnxruntime:onnxruntime-android` (CPU), sessão criada a partir do asset mapeado em memória (`AssetFileDescriptor` → `FileChannel.map`), 4 threads, arena da CPU ligada se o aparelho tem 8 GB de RAM ou mais.
- **`GuitarSeparator`**: Kotlin puro, portado do `demucs.onnx` (`model_apply.cpp`, `model_inference.cpp`, `dsp.cpp`):
  - normalização pela média e desvio do mono;
  - trechos de 4 s, sobreposição de 25 %, peso em triângulo, preenchimento por reflexão;
  - STFT (nfft 4096, hop 1024, janela de Hann, "complex as channels"), via JTransforms (BSD);
  - iSTFT do ramo de frequência somada ao ramo de tempo;
  - saída: **guitarra** (fonte 4) e **resto** (soma das outras 5), estéreo 44,1 kHz.
  - Progresso por trecho; checagem de cancelamento e de pausa entre trechos.
- **`PcmDecoder` / `M4aEncoder`**: `MediaExtractor` + `MediaCodec` para PCM 44,1 kHz estéreo (reamostra se preciso), lendo pelo mesmo acesso a arquivos do loader (pastas e `.sng`); grava AAC em `.m4a` com `MediaMuxer`.
- **Modelo:** `src/main/assets/htdemucs_6s_seg4.onnx` (~130 MB, `noCompress`), no git. `scripts/exportar_modelo_ia.sh` refaz o arquivo (PyTorch + Demucs oficial + exportação do `demucs.onnx` + onnxslim) e é documentado no README.

### Execução

- **`SeparationWorker`** (WorkManager, `CoroutineWorker` em primeiro plano; tipo de serviço `mediaProcessing` no Android 15+, `dataSync` antes). Trabalho único (`enqueueUniqueWork`, `KEEP`): uma separação por vez.
- **Calor:** `PowerManager.addThermalStatusListener`; a partir de `THERMAL_STATUS_SEVERE` pausa entre trechos; volta quando o estado cai para `MODERATE` ou menos.
- **`SeparationStatus`** (`StateFlow`, singleton): `Idle` | `Running(chartId, title, progress, remainingMs)` | `Paused(...)`. O tempo restante vem da média por trecho até o momento.

### Dados

- Arquivos: `filesDir/separacoes/<chartId>/guitar.m4a` e `backing.m4a`. Gravados em `<chartId>.tmp/` e renomeados no fim (troca atômica).
- Room (`:core:database`, nova migração): tabela `ai_separation(chartId PK, state SEPARATED|DECLINED, bytes, createdAt)`.
- Separações órfãs (mapa fora da biblioteca) são apagadas ao fim de cada importação.

### Encaixe no que existe

- **`ChartTrackLoader`:** sem guitarra separada na pasta e com separação `SEPARATED` → `ChartAudio.Stems(guitarUri = guitar.m4a, backingUris = [backing.m4a], fallbackUri = áudio original)`. O jogo não muda.
- **Biblioteca:** pergunta depois da dificuldade; marca GUITARRA SEPARADA IA.
- **`:core:ui`:** `SeparationIndicator` (ícone + painel), usado no topo das quatro telas.
- **Configurações:** seção nova.
- **README:** seção "Guitarra separada por IA" (uso, tempo, calor, espaço), módulo novo e créditos (Demucs e demucs.onnx, MIT; ONNX Runtime, MIT; JTransforms, BSD).

## 6. Erros e casos de borda

- **Cancelar:** para entre trechos (menos de 1 s), apaga os temporários; a música volta a perguntar.
- **Falha** (memória, modelo, decodificação, mapa sem áudio): painel/notificação "Não deu para separar a guitarra de <música>"; nada é gravado; a música continua jogável inteira e pergunta de novo.
- **App fechado ou celular reiniciado no meio:** o WorkManager recomeça do zero.
- **Menos de 50 MB livres:** não começa; "Pouco espaço no celular".
- **Música longa:** funciona; o tempo cresce com a duração, a memória só nos ~40 MB do áudio decodificado.
- **Jogar a música que está sendo separada:** joga inteira, sem esperar.
- **Mapa que já tem guitarra separada:** não pergunta.

## 7. Testes

- `GuitarSeparator`: STFT e iSTFT comparadas a valores gerados no Python (tolerância 1e-4); reconstrução perfeita com um `DemucsModel` falso (identidade); trechos e sobreposição em áudio mais curto e mais longo que 4 s; cancelamento entre trechos; progresso.
- **Teste de ouro** com o modelo real na JVM (`onnxruntime` para desktop só em `testImplementation`): 8 s de áudio fixo, guitarra comparada à saída do Python (correlação > 0,99).
- `ChartTrackLoader`: separação `SEPARATED` vira `Stems`; sem ela, como hoje.
- Room: migração; recusa; espaço; órfãs.
- Worker: sucesso grava e marca; falha não marca; cancelamento limpa; pausa por calor (fonte térmica falsa).
- Telas: pergunta (sons, recusa guardada, aviso de pouca RAM); ícone e painel; marca GUITARRA SEPARADA IA; Configurações; screenshots.
- No aparelho: separar uma música baixada do Encore pelo app, medir o tempo, jogar com a guitarra cortando, apagar separações.
