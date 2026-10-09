# Riff: polimento (desempenho, Baseline Profile e README)

Data: 2026-10-09 · Status: implementado em 2026-10-09 (ver §9)
Complementa: `2026-10-05-riff-design.md` (§ Desempenho: "60 fps no gameplay em um aparelho intermediário, sem alocar memória no loop de frame. Baseline Profile entra na fase final."; §11 passo 8: "Polimento: Baseline Profile e README final.") e `2026-10-07-identidade-visual-design.md` (chama "sem alocação por frame")

## 1. Objetivo

Fechar o roteiro da especificação principal:

1. **Medir** o desempenho no aparelho do usuário (Galaxy S25), corrigir as alocações por quadro na pista e medir de novo.
2. **Baseline Profile** para o app abrir e rodar mais rápido desde a primeira execução.
3. **README final** como guia de uso, além de desenvolvimento.

Decisões do usuário (2026-10-09): escopo "desempenho + README" (os pontos menores das revisões ficam para um plano próprio); abordagem "medir, corrigir, medir de novo" com Macrobenchmark. O usuário ainda vai mudar coisas no app: README e Baseline Profile são **documentos vivos**, com o caminho de atualização descrito (§5 e §6).

## 2. Medição e Baseline Profile

- **Módulo `:baselineprofile`** (`com.android.test`, alvo `:app`), com os plugins oficiais `androidx.baselineprofile` (no app e no módulo) e as bibliotecas de Macrobenchmark e UiAutomator. O app ganha `androidx.profileinstaller`, que aplica o perfil na instalação.
- **Percursos** (UiAutomator), compartilhados entre o gerador e as medições:
  1. **Abertura:** abrir o app a frio até a tela inicial.
  2. **Biblioteca:** abrir a biblioteca e rolar a lista até o fim e de volta.
  3. **Partida:** abrir a música de nome dado (argumento de instrumentação `riff.song`, padrão "Smells Like Teen Spirit"), escolher a primeira dificuldade disponível, jogar 20 s tocando nas trilhas em ritmo fixo (uma trilha diferente a cada 250 ms, para gerar acertos, erros, chamas e cortes da guitarra) e sair pelo botão Voltar e "Sair" da pausa.
- **Gerador:** `BaselineProfileGenerator` roda os três percursos; o perfil gerado vai para `app/src/main/generated/baselineProfiles/` (local padrão do plugin) e fica no repositório.
- **Medições** (Macrobenchmark), cada uma em dois modos de compilação: sem perfil (`CompilationMode.None`) e com perfil (`CompilationMode.Partial(BaselineProfileMode.Require)`):
  - `StartupBenchmark`: `StartupTimingMetric`, abertura a frio, 5 repetições;
  - `GameplayBenchmark`: `FrameTimingMetric` (`frameDurationCpuMs` e `frameOverrunMs` em p50, p90 e p99) durante o percurso da partida, 3 repetições.
- **Build medido:** o variant que o plugin cria para benchmark (release sem minificação), assinado com a chave de debug, mesmo `applicationId`. Ele substitui o app instalado no celular sem apagar os dados (biblioteca, recordes, ajustes); depois da medição, a versão de debug é reinstalada.
- **Spotify fora da medição:** depende de outro app e da rede; o modo Spotify não tem percurso.

## 3. Correções no desenho da pista

Fonte das alocações por quadro (hoje, em `StageRenderer.kt` e `StageDrawing.kt`):

| Hoje, a cada quadro | Depois |
|---|---|
| `HighwayGeometry(size)` (objeto novo) | criada quando o tamanho muda |
| contorno da pista (`Path`) e degradê do fundo | prontos; recriados quando o tamanho muda |
| `fretDistances(...)` e `.filter` (listas novas) | `FloatArray` reaproveitado, preenchido no lugar, com a contagem válida |
| em cada gema e alvo: degradê cromado e degradê do brilho | degradês prontos em tamanho 1 por cor de trilha (e alvo apertado/solto); a gema é desenhada com `withTransform` (translação e escala), sem alocar |
| em cada chama: 3 `Path` e 3 degradês; faíscas numa `List<Pair>` | formato e degradês prontos em tamanho 1, desenhados com transformação; faíscas num `FloatArray` |
| placar recomposto a cada quadro | recomposto só quando pontuação, multiplicador, combo ou julgamento mudam |

- **`StageCache`** em `:core:ui` (`stage/`): guarda geometria, `Path` e `Brush` de um tamanho de tela; `update(size)` recria só quando o tamanho muda. A tela da partida o guarda com `remember`. As funções de desenho recebem o cache; o catálogo da identidade visual e os screenshots continuam funcionando com um cache próprio.
- **Aparência igual:** os screenshots `Gameplay_stage` e os do catálogo da identidade visual continuam passando no `verifyRoborazziDebug` (diferença dentro da tolerância configurada). Se algum precisar ser regravado, a diferença é conferida à mão e justificada.

## 4. Critérios de sucesso

- O Baseline Profile é gerado no S25 e incluído no app; o build de debug e o de release continuam funcionando.
- No percurso da partida, **com perfil**, depois das correções: `frameDurationCpuMs` p90 **abaixo de 8,3 ms** (metade do quadro de 60 fps, folga para aparelho intermediário) e `frameOverrunMs` p99 **igual ou menor** que antes das correções.
- Se o "antes" já cumprir a meta, as correções entram mesmo assim (a especificação proíbe alocação no loop de quadro) e os números ficam registrados como estão.
- Os números de antes e depois (abertura e partida, sem e com perfil) ficam no README, com data e aparelho.

## 5. README final (documento vivo)

Seções:

1. **O que é** o Riff e os dois modos: biblioteca (para jogar, com guitarra separada e retrocesso na retomada) e Spotify (para ouvir e tocar junto).
2. **Instalar e configurar:** requisitos, `local.properties`, Dashboard do Spotify (já existe; revisar).
3. **Importar mapas:** pastas do Clone Hero (`notes.chart`/`notes.mid`, `song.ini`, áudio), guitarra separada (`guitar.*` + acompanhamento), o que não é lido ainda (`.sng`) e de onde não pegar mapas (Harmonix/Neversoft).
4. **Jogar:** dificuldade, pausa, ajuste de sincronia (±1 s / ±10 ms, alinhamento automático no Spotify), calibração e Zerar, configurações.
5. **Desenvolvimento:** comandos, módulos, screenshots e **desempenho** (gerar o perfil, medir, números).
6. **Créditos e licenças:** sons CC0 do Freesound (`third_party/`), nada do Guitar Hero.
7. **Status:** roteiro da especificação concluído; pendências conhecidas (pontos menores das revisões; ideias: `.sng`, separação da guitarra no app).

Atualização: cada plano futuro que mudar o uso do app atualiza a seção correspondente do README no mesmo branch (vai no Global Constraints dos próximos planos).

## 6. Manter o perfil atualizado

- O README diz quando e como regenerar: depois de mudanças grandes em telas ou no gameplay, rodar o gerador com o celular conectado e desbloqueado e commitar o perfil novo.
- Um perfil desatualizado não quebra nada: o app só deixa de ganhar a otimização nas partes que mudaram.

## 7. Erros e casos de borda

- **Celular desconectado ou bloqueado:** a medição e o gerador falham com a mensagem do Gradle/Macrobenchmark; nenhuma outra task depende deles (o `check` normal não roda o `:baselineprofile`).
- **Música do percurso ausente na biblioteca:** o percurso da partida é pulado com aviso no log (`Log.w`), e o perfil sai só com abertura e biblioteca; o `GameplayBenchmark` falha dizendo qual música faltou.
- **Diálogo de dificuldade com uma só dificuldade:** o percurso toca na única opção.
- **Fora do escopo:** pontos menores das revisões, desempenho do modo Spotify, medição em outros aparelhos.

## 8. Testes

- **`StageCache`** (unitário/Robolectric): mesmo tamanho devolve os mesmos objetos; tamanho novo recria; degradês por trilha existem para as 5 cores.
- **Trastes:** o preenchimento do `FloatArray` dá os mesmos valores da lista de hoje, sem criar listas (mesma instância a cada quadro).
- **Aparência:** screenshots existentes passam (`verifyRoborazziDebug`).
- **Placar:** teste de composição conferindo que o placar não recompõe quando nada mudou (contador de recomposições) e recompõe quando a pontuação muda.
- **No aparelho:** gerador e medições rodam no S25; os números entram no README.

## 9. Resultado (2026-10-09)

Medido no Galaxy S25 (Android 16, 120 Hz), depois das correções da §3: abertura 185 ms sem perfil e 148 ms com perfil; a partida roda a 120 fps, com 1 a 3 quadros com atraso do app em cerca de 2.480 (thread principal ~2,4 ms por quadro).

A meta da §4 em `frameDurationCpuMs` (p90 < 8,3 ms) não se cumpriu (11,2 ms), mas a métrica soma a espera na fila da tela ("Buffer Stuffing": o app adiantado, ~5 ms por quadro no `queueBuffer`). Decisão do usuário: aceitar e trocar o critério por fps e quadros com atraso do app, lidos dos traces do Perfetto por `scripts/quadros_travados.py`.

