# Riff: calibração do atraso de áudio e ajuste fino por música

Data: 2026-10-09 · Status: aprovado em conversa, aguardando revisão do documento
Complementa: `2026-10-05-riff-design.md` (§2 item 4 Calibração, §4 Sincronização, §11 passo 7)

## 1. Objetivo

Fazer as notas baterem com a música no aparelho do jogador. Duas peças:

1. **Calibração geral:** medir uma vez o atraso entre o som e o toque do jogador e aplicá-lo em todas as partidas (Spotify e biblioteca).
2. **Ajuste fino por música:** corrigir, durante a partida, uma música ou mapa que ainda fique adiantado ou atrasado, e guardar o ajuste.

Decisões do usuário (2026-10-09): **um atraso só** (sem perfil por saída de som; quem trocar de fone recalibra); ajuste fino **no menu de pausa**; medição **tocando junto com o clique**.

## 2. Comportamento

### Calibração

- Entrada: nova linha "Calibrar atraso do áudio" nas Configurações, com o valor atual (por exemplo, "42 ms"; "Não calibrado" quando for 0).
- Tela "Calibração":
  1. Instrução: "Toque na tela junto com cada clique." e botão "Começar".
  2. Metrônomo a 100 BPM (um clique a cada 600 ms): 4 cliques de entrada, que não contam, e 20 cliques medidos. A tela inteira recebe os toques; cada toque dá um retorno visual curto.
  3. Resultado: "Atraso medido: 42 ms", botões −5 ms e +5 ms para retoque à mão, "Salvar" e "Repetir".
  4. Medição instável (menos de 12 toques válidos): "Medição instável. Tente de novo, tocando junto com o clique." e "Repetir".
- "Salvar" grava e volta para as Configurações. Sair sem salvar não muda nada.

### Ajuste fino por música

- No menu de pausa da partida: "Sincronia desta música: +20 ms" com botões "−10 ms" e "+10 ms".
- Cada toque muda o ajuste na hora (vale ao continuar) e grava.
- Limites: −500 ms a +500 ms.
- Onde fica salvo:
  - modo Spotify: no casamento faixa ↔ mapa (`track_matches.trackOffsetMs`); se o casamento era automático e ainda não estava salvo, ajustar o salva (com o mesmo mapa);
  - modo biblioteca: por mapa, numa tabela nova.
- "Jogar de novo" e as próximas partidas usam o ajuste salvo.

## 3. Medição

- **Sinal:** o relógio do jogo soma o ajuste à posição da música (`SongClock`: posição + `offsetMs`). Se o jogador toca, em média, `L` ms depois do clique que ouve, o atraso geral salvo é `audioOffsetMs = −L`. Na partida vale `audioOffsetMs + ajuste da música`.
- **Cliques:** gerados por nós numa saída de áudio própria, a mesma `AudioTrackSink` das faixas separadas (PCM float, `USAGE_GAME`), que informa quando cada quadro sai no alto-falante. Batida: uma nota de guitarra (Mi3, 0,25 s) no mesmo timbre "crunch" do som de acerto, sintetizada em código, com ataque imediato (pedido do usuário em 2026-10-09: o clique de senoide não agradou).
- **Instante de cada clique:** o quadro em que ele começa, convertido para o relógio do sistema (`SystemClock.uptimeMillis`) pela posição apresentada da saída.
- **Instante de cada toque:** `MotionEvent.eventTime` (mesmo relógio), no momento em que o dedo encosta.
- **`CalibrationSession` (Kotlin puro):** recebe os instantes dos cliques e dos toques; associa cada toque ao clique mais próximo (cada clique aceita no máximo um toque, o mais próximo); calcula a diferença de cada par; descarta as diferenças a mais de 250 ms da mediana; com 12 ou mais restantes, o atraso é a mediana delas, arredondada para ms; com menos, "instável". Toques nos cliques de entrada não contam.
- **Valor salvo:** limitado a −500..+500 ms. O retoque à mão (±5 ms) muda o valor antes de salvar.

## 4. Arquitetura

- **`:feature:calibration`:** `CalibrationRoute`, `CalibrationViewModel`, `CalibrationScreen`, `CalibrationSession`, `ClickTrack` (gera o metrônomo e informa os instantes). Depende de `:core:audio` (saída de áudio) além do padrão das features.
- **`:feature:settings`:** linha "Calibrar atraso do áudio" que navega para a calibração.
- **`:core:game`:** `SongClock.offsetMs` passa a poder mudar no meio da partida.
- **`:feature:gameplay`:** `GameSession.adjustOffset(deltaMs)`; o `GameplayViewModel` guarda o ajuste da música e grava pelo repositório certo; o menu de pausa ganha a linha de sincronia.
- **`:core:database`:** tabela `chart_offsets(chartId PK, offsetMs)`, chave estrangeira para `charts` com `ON DELETE CASCADE`; banco da versão 1 para a 2 com `AutoMigration`.
- **`:core:data`:** `ChartOffsetRepository` (ler e gravar o ajuste de um mapa); `TrackMatchRepository.saveOffset(trackUri, chartId, offsetMs)`.
- O `GameplayRoute` já tem `trackOffsetMs` para o Spotify; no modo biblioteca o ViewModel lê o ajuste do mapa.

## 5. Erros

- Saída de áudio que não abre na calibração: "Não deu para tocar o metrônomo." com "Tentar de novo".
- App vai para segundo plano durante a medição: a medição é cancelada e a tela volta ao início.
- Valor salvo fora do intervalo (arquivo antigo ou corrompido): limitado a −500..+500 na leitura.

## 6. Testes

- `CalibrationSession`: medição limpa (toques 40 ms depois → 40); toques fora do ritmo descartados; menos de 12 válidos → instável; clique sem toque; dois toques no mesmo clique (fica o mais próximo); toques nos cliques de entrada ignorados.
- Conversão para o valor salvo (sinal e limites) e o retoque ±5.
- `SongClock`: mudar o ajuste no meio da partida desloca a posição na hora.
- Banco: migração 1 → 2 e ajuste apagado junto com o mapa.
- Repositórios: gravar e ler o ajuste de mapa; `saveOffset` cria ou atualiza o casamento.
- `GameplayViewModel`: lê o ajuste do mapa na biblioteca; −10/+10 muda a sessão, respeita os limites e grava no lugar certo (Spotify ou biblioteca).
- Telas: estados da calibração (instrução, medindo, resultado, instável) com screenshot; linha nas Configurações; linha de sincronia no menu de pausa.
- No aparelho: o usuário calibra e confere de ouvido se as notas ficaram mais certas.
