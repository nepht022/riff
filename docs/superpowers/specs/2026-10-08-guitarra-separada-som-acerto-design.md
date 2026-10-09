# Riff: guitarra separada e som de acerto

Data: 2026-10-08 · Status: aprovado em conversa, aguardando revisão do documento
Complementa: `2026-10-05-riff-design.md` (§4 Renderização e input, áudio) e `2026-10-07-identidade-visual-design.md` (§6 Sons: o "estalo curto" de acerto é substituído pelo som desta especificação)

## 1. Objetivo

O jogador não gostou do estalo de acerto. Duas mudanças:

1. **Guitarra separada, como no Guitar Hero:** no modo biblioteca, quando a pasta do mapa trouxer a faixa da guitarra separada, todas as faixas tocam juntas; a guitarra some no erro e volta no acerto seguinte.
2. **Som de acerto novo:** na pasta sem faixa separada, o estalo vira uma nota de guitarra elétrica com distorção leve, uma nota por trilha, num volume que fica atrás da música.

O Spotify não expõe o áudio nem faixas separadas (spec principal §3). No modo Spotify o acerto fica em silêncio (decisão do usuário em 2026-10-09: a música inteira já está tocando).

## 2. Comportamento

### Som de acerto (modo de um arquivo)

- Timbre "crunch leve" (candidato D3 escolhido pelo usuário): corda sintetizada (Karplus-Strong), distorção leve (`tanh` com ganho 2,5) e filtro de caixa de amplificador (passa-baixas em ~4 kHz e ~5,5 kHz, passa-altas em 120 Hz e 70 Hz).
- Uma nota por trilha, pentatônica de Mi menor: verde Mi3 (164,81 Hz), vermelha Sol3 (196,00 Hz), amarela Lá3 (220,00 Hz), azul Si3 (246,94 Hz), laranja Ré4 (293,66 Hz).
- Duração 0,25 s, com fade-out de 60 ms.
- Pico do arquivo em −12 dBFS (o estalo atual tem pico de ~−0,8 dBFS), para ficar atrás da música no volume padrão `sfxVolume = 0,6`. O ajuste fica na tela de Configurações (plano seguinte); 0 desliga.
- Gerados por `scripts/gerar_sfx.py` (síntese nossa, sem amostras de terceiros): `sfx_hit_1.wav` a `sfx_hit_5.wav`. O `sfx_hit.wav` atual é removido.

### Som de erro (todos os modos)

Decidido com o usuário em 2026-10-08, depois de ouvir candidatos sintetizados (não soavam naturais) e gravações reais.

- **Material:** gravações reais de guitarra, licença CC0, do Freesound, guardadas em `third_party/sons/` com origem e licença: 353605 "Muted Guitar Strum" (chiller345), 12006 "Dist_Chr_Gmin_pm" e 12042 "Dist_sng_G_3rd_str_pm" (SpeedY), 576575 "guit scratch" (gazbooth), 483882 "Electric Guitar String Pluck Noise 1_11" (Joao_Janz). Nada do Guitar Hero: o som original do jogo foi descartado pela regra do projeto e por direito autoral.
- **Seis variações curtas** (0,3 a 0,4 s) montadas com trechos dessas gravações (batida abafada, acorde distorcido abafado, nota fora do tom, raspada, ruído de corda), por `scripts/montar_sfx_erro.py`: `sfx_miss_1.wav` a `sfx_miss_6.wav`, pico em −9 dBFS (um pouco acima do acerto, para o erro ser percebido). O `sfx_miss.wav` sintetizado é removido.
- **Um som por vez (simulação S3 escolhida pelo usuário):** cada erro sorteia uma variação diferente da anterior, com força entre 75% e 100%; um erro novo encerra o som anterior num fade de 25 ms; erros a menos de 250 ms do último som não disparam outro (no máximo 4 por segundo). A guitarra separada continua sendo cortada em todos os erros.

### Guitarra separada (modo biblioteca)

- **Faixas reconhecidas** na pasta do mapa, pelos nomes do Clone Hero, com as extensões `ogg`, `opus`, `mp3` e `wav`:
  - guitarra: `guitar`;
  - acompanhamento: `song`, `rhythm`, `bass`, `drums`, `drums_1` a `drums_4`, `vocals`, `vocals_1`, `vocals_2`, `keys`, `crowd`.
- **Quando vale o modo de faixas:** existe `guitar.*` e pelo menos uma faixa de acompanhamento. Sem `guitar.*`, ou com `guitar.*` sozinha, fica o modo de um arquivo de hoje (`song.*` primeiro; `guitar.*` se for a única).
- **Erro** (nota que passou sem ser tocada, ou toque sem nota): a guitarra cai a zero numa rampa de 30 ms e toca o som de erro.
- **Nota atrasada** (2026-10-09, depois do teste no aparelho): 50 ms depois do tempo de uma nota ainda não tocada, a guitarra já cala, sem som de erro; o erro em si continua no fim da janela (100 ms), e um acerto atrasado a traz de volta. Antes, o começo da nota perdida chegava a tocar.
- **Acerto:** a guitarra volta a 100% numa rampa de 30 ms. Nesse modo não há som de acerto.
- **Começo da partida:** a guitarra começa audível.
- **Fim:** a partida acaba quando a faixa mais longa termina (ou pela regra de hoje: 3 s depois da última nota julgada). Uma faixa mais curta que as outras termina em silêncio.
- **Fora de escopo (YAGNI):** volume por faixa, cortar a guitarra ao soltar uma nota longa antes da hora, modo para baixo ou bateria.

## 3. Arquitetura

### `:core:data`: `ChartTrackLoader`

`PlayableChart.audioUri: String?` vira `audio: ChartAudio?`:

```kotlin
sealed interface ChartAudio {
    data class Single(val uri: String) : ChartAudio
    data class Stems(val guitarUri: String, val backingUris: List<String>) : ChartAudio
}
```

A escolha segue a seção 2. O gameplay usa `Single` como hoje e `Stems` com o player novo.

### `:core:audio`

- **`StemMixer`** (Kotlin puro, sem Android): recebe blocos PCM float por faixa e escreve um bloco estéreo misturado num buffer reaproveitado. Mono é duplicado para os dois canais. Cada faixa tem ganho alvo e ganho atual; a mudança de alvo é aplicada como rampa linear de 30 ms (1.323 quadros a 44,1 kHz). Faixa sem dados conta como silêncio. A saturação é limitada a [−1, 1].
- **`StemDecoder`** (Android): `MediaExtractor` + `MediaCodec` por faixa, entrega PCM em blocos para o mixer, sem carregar a música inteira na memória. Todas as faixas precisam da mesma taxa de amostragem; se alguma diferir, o preparo falha com `IOException`.
- **`StemSongPlayer : SongPlayer, GuitarTrackControl`**: uma thread de áudio decodifica, mistura e escreve num único `AudioTrack` (`USAGE_GAME`, `CONTENT_TYPE_MUSIC`, PCM float).
  - A posição vem dos quadros tocados (`AudioTrack.getPlaybackHeadPosition`), convertidos em ms: um relógio só.
  - `pause` pausa o `AudioTrack` e a thread; `release` para a thread, espera o término e libera codecs e `AudioTrack`, de forma idempotente, com as mesmas garantias do `MediaSongPlayer` (usar depois de liberado é inofensivo).
  - Sem alocação por bloco de áudio: buffers de decodificação e de mistura reaproveitados.
- **`GuitarTrackControl`**: `fun setGuitarAudible(audible: Boolean)`.
- **`SfxPlayer.hit()` vira `hit(lane: Int)`**: o `SoundPoolSfxPlayer` carrega os cinco sons e toca o da trilha. Para o erro, carrega as seis variações e usa o `MissSoundPicker`; o corte do som anterior é feito baixando o volume do stream em passos ao longo de 25 ms e parando-o.
- **Fábrica:** `SongPlayerFactory` ganha a criação do player de faixas (por exemplo, um `StemSongPlayerFactory` com `@LocalSong`), escolhida pelo `GameplayViewModel` conforme o `ChartAudio`.

### `:feature:gameplay`: `GameSession`

- Recebe o player e, se ele implementar `GuitarTrackControl`, entra no modo de faixas:
  - acerto: `setGuitarAudible(true)`, sem `sfx.hit`;
  - erro (nota perdida no `tick` ou toque sem nota): `setGuitarAudible(false)` e `sfx.miss()`.
- Sem `GuitarTrackControl` (um arquivo ou Spotify): `sfx.hit(lane)` no acerto, como hoje mas com a trilha.

## 4. Erros

- Faixa que não abre, não decodifica ou tem taxa diferente das outras: o motivo vai para o logcat (tag `RiffAudio`), o gameplay cai no modo de um arquivo (`song.*`) e a tela mostra uma vez "Tocando sem a guitarra separada" (pt) / "Playing without the separate guitar" (en). Se nem o modo de um arquivo abrir, vale o erro de hoje (`AUDIO_FAILED`).
- Segundo plano, pausa e saída: iguais a hoje. Depois de `release` nenhuma thread continua rodando.

## 5. Testes

- **`StemMixer`** (unitários): soma de faixas; mono para estéreo; rampa de 30 ms no corte e na volta, sem salto de amplitude entre quadros vizinhos maior que o da rampa; faixa que acaba antes; limite em [−1, 1]; nenhuma alocação no caminho de mistura (o mesmo buffer de saída a cada chamada).
- **`ChartTrackLoader`**: `Stems` com `guitar.ogg` + `song.ogg`; inclui `bass`, `drums_1` etc.; só `song.*` → `Single`; só `guitar.*` → `Single(guitar)`.
- **`GameSession`**: modo de faixas (acerto liga sem `hit`; nota perdida e toque sem nota cortam e tocam `miss`); modo de um arquivo (`hit(lane)` com a trilha certa).
- **`GameplayViewModel`**: escolhe o player de faixas para `Stems`; falha no preparo das faixas cai em `Single` com o aviso.
- **`StemSongPlayer`** (Robolectric): ciclo preparar, tocar, pausar, liberar; liberar duas vezes; nenhuma thread viva depois de liberar.
- **`gerar_sfx.py`**: o `SfxAssetsTest` existente passa a conferir `sfx_hit_1..5` (presentes, duração ≤ 0,3 s, pico ≤ −12 dBFS).
- **Som de erro:** `MissSoundPicker` (unitário): nunca repete a variação anterior, passa por todas, ignora erros a menos de 250 ms e a força fica entre 0,75 e 1; `SfxAssetsTest` confere `sfx_miss_1..6` (presentes, duração ≤ 0,4 s, pico ≤ −9 dBFS) e que o `sfx_miss.wav` antigo saiu.
- **No aparelho do usuário:** um mapa com a guitarra separada (do Encore, de charter da comunidade, nunca Harmonix/Neversoft; se não houver, o mapa do Nirvana separado pelo Demucs). O usuário confere de ouvido o corte no erro, a volta no acerto e o volume do som novo.
