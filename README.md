# Riff

Jogo de ritmo para Android no estilo Guitar Hero, feito para uso pessoal (não é publicado na Play Store; veja a seção 3 da especificação). As notas vêm de mapas do Clone Hero que você mesmo importa. Há dois jeitos de jogar:

- **Biblioteca, para jogar:** a música vem da pasta do mapa. Se a pasta traz a guitarra separada, ela some quando você erra e volta quando acerta, como no Guitar Hero. Sem ela, a música toca inteira e o acerto faz um som curto de guitarra. Ao continuar depois de uma pausa, a pista volta 0,5 s, como uma fita, para você retomar no ritmo.
- **Tocando agora (Spotify), para ouvir e tocar junto:** o Riff acompanha a faixa que está tocando no Spotify e procura o mapa dela na biblioteca. A sincronia é ajustada automaticamente pelo começo da música no áudio do mapa, e você refina na pausa. Aqui a música segue de onde parou ao continuar e o acerto não faz som. Os botões **⏮ ⏯ ⏭** comandam o próprio Spotify (anterior, tocar/pausar, próxima), antes e depois de escolher o mapa e também na pausa da partida; ali, trocar de música encerra a partida sem salvar o placar e volta ao Tocando agora com a música nova.

- Especificação: `docs/superpowers/specs/2026-10-05-riff-design.md` (e as especificações de cada etapa na mesma pasta)
- Planos: `docs/superpowers/plans/`
- Pendências conhecidas: `docs/pendencias.md`

## Instalar e configurar

### Requisitos

- Android Studio (o JBR 21 é usado como JDK) e Android SDK com a plataforma 37
- Um celular com Android 8 ou mais novo (o desempenho foi medido num Galaxy S25)
- No WSL, o Gradle roda pelo `./scripts/gw`, que chama o `gradlew.bat` do Windows; no Linux e no macOS, use `./gradlew`

### Spotify

1. Crie um app em https://developer.spotify.com/dashboard com o redirect URI `riff://callback` e a plataforma **Android**, com o package `com.rodrigomachado.riff` e o SHA-1 da sua chave de debug (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`). Em modo de desenvolvimento, a conta do Spotify usada no aparelho precisa estar em "User Management".
2. No `local.properties` (fora do git):
   ```
   spotify.clientId=<Client ID do Dashboard>
   spotify.redirectUri=riff://callback
   ```
3. O aparelho precisa do app do Spotify instalado e logado numa conta Premium. Na primeira conexão o Riff abre o login do Spotify pedindo o controle remoto; aceite.

Sem `spotify.clientId`, o app compila e mostra "Spotify não configurado"; a biblioteca continua jogando com o áudio das pastas. O AAR do SDK está em `third_party/maven` (veja `third_party/README.md`). Falhas de conexão aparecem no logcat com a tag `RiffSpotify`.

## Importar mapas

1. Na **Biblioteca**, toque em **Escolher pasta** e escolha a pasta que guarda os seus mapas. **Reimportar** lê a pasta de novo; **Trocar pasta** escolhe outra.
2. Cada música é uma subpasta no formato do Clone Hero:
   - `notes.chart` ou `notes.mid` (as notas) e `song.ini` (nome, artista, atraso);
   - o áudio: `song.ogg`, `.opus`, `.mp3` ou `.wav`.
3. **Guitarra separada:** se a subpasta tiver `guitar.*` e pelo menos uma faixa de acompanhamento (`song`, `rhythm`, `bass`, `drums`, `drums_1` a `drums_4`, `vocals`, `vocals_1`, `vocals_2`, `keys`, `crowd`), o Riff toca todas juntas e corta só a guitarra no erro.
4. Mapas `.sng` (o pacote do Encore e do YARG, um arquivo por música) são lidos direto: é só colocar o arquivo na pasta.
5. **Excluir:** o **X** vermelho de cada mapa apaga a pasta dele (ou o `.sng`) do celular, junto com a guitarra separada pela IA e o recorde; **Selecionar** marca vários para excluir de uma vez. Sempre pede confirmação e não dá para desfazer. Mapas soltos direto na pasta da biblioteca não têm o X (apagar a pasta deles apagaria a biblioteca inteira).
6. Não use mapas da Harmonix/Neversoft (rips de Rock Band ou Guitar Hero): são conteúdo protegido. Prefira mapas feitos pela comunidade.

## Pesquisar e baixar mapas

- **Biblioteca → Pesquisar:** digite a música ou o artista. A lista mostra os mapas da comunidade no Encore (enchor.us), sem rips de jogos oficiais (Harmonix, Neversoft), com as dificuldades da guitarra e a marca **Guitarra separada** quando o pacote traz a guitarra à parte. **Baixar** grava o `.sng` em "Baixados pelo Riff", dentro da pasta da biblioteca, e o mapa já entra na lista (**Jogar**). Os que você já tem aparecem como "Já na biblioteca".
- **Originais | Versões** escolhe entre as gravações originais e covers, remixes, ao vivo e medleys (pelo título). **Só com guitarra separada** procura nas páginas seguintes sozinho até achar 10 mapas ou conferir 100 resultados; **Procurar mais** confere outros 100.
- **Tocando agora → Pesquisar no Encore** (faixa sem mapa ou só com sugestão): a pesquisa já vem com o artista e o título, os mais parecidos primeiro; o mapa baixado fica casado com a faixa e você volta com **Jogar**.
- Na primeira vez, o Riff pede para escolher a pasta de novo, agora com permissão de gravar nela (**Permitir**).
- O pacote vem com o áudio da música (uso pessoal). A API do Encore não é oficial: se ela mudar, a pesquisa avisa que o Riff precisa ser atualizado.

## Guitarra separada por IA

- Na Biblioteca, ao jogar uma música sem a guitarra separada (a maioria dos mapas do Encore), o Riff pergunta **Separar a guitarra com IA?** depois da dificuldade. **Separar** começa a separação em segundo plano; **Sem separar** joga com a música inteira e fica guardado (só pergunta de novo com **Perguntar sempre se separa** ligado, o padrão); **Voltar** fecha sem jogar.
- A separação roda no próprio celular, sem internet: o modelo Demucs (htdemucs_6s) com o ONNX Runtime na CPU. No Galaxy S25, uma música de 3:30 leva cerca de 2 minutos e usa até ~2,8 GB de RAM enquanto roda (celulares com menos de 8 GB usam um modo mais econômico e mais lento).
- Enquanto separa, aparece um ícone (palheta com anel de progresso) no topo das telas; tocar nele mostra a porcentagem, o tempo restante e **Cancelar**. A notificação do Android acompanha e avisa no fim. Se o celular esquentar demais, a separação pausa e continua quando esfriar.
- Mapas que já vêm com a guitarra à parte mostram **GUITARRA SEPARADA** na Biblioteca e não perguntam nada (a marca é anotada na importação: para mapas antigos, toque em **Reimportar** uma vez).
- Pronta, a música ganha a marca **GUITARRA SEPARADA IA** e passa a jogar como as que já vêm com a guitarra à parte: a guitarra some quando você erra.
- Cada música separada ocupa ~7 MB (guitarra e resto em `.m4a`, no espaço do app). Nas **Configurações**, a aba **Guitarra IA** mostra o espaço em **Apagar separações** e tem o interruptor **Perguntar sempre se separa** (ligado de fábrica): ligado, toda música sem guitarra separada pergunta ao jogar, mesmo as em que você escolheu "Sem separar".

## Reportar problemas

- Na tela inicial, **Reportar** (ao lado de Configurações) abre um formulário: tipo (Problema, Sugestão ou Outro), o que aconteceu, email para resposta (opcional) e **Incluir informações do aparelho** (versão do Riff, Android, modelo e idioma, mostrados na tela; ligado de fábrica). Depois de um envio, o próximo espera 5 minutos.
- O relato vai para um Google Apps Script na conta do desenvolvedor, que manda um email "[Riff] Problema: ..." para essa mesma conta (responder vai direto para quem relatou, se deixou o email). Nenhuma senha fica no app; o Google limita a ~100 emails por dia.
- **Configurar (uma vez):** publique `scripts/relatos_apps_script.gs` como app da Web (instruções no começo do arquivo) e coloque o endereço no `local.properties`: `report.url=https://script.google.com/macros/s/.../exec`. Sem ele, a tela avisa que o envio não está configurado.
- Antes de distribuir o app (Play Store), declarar na ficha de segurança dos dados que ele envia o texto do relato, o email opcional e as informações do aparelho.

## Jogar

- **Começar:** toque numa música da Biblioteca (ou em **Jogar** no "Tocando agora") e escolha a dificuldade. Toque nas trilhas na linha de acerto, com vários dedos se precisar; segure as notas longas.
- **Barra de progresso:** a linha fina no topo da partida mostra quanto da música já passou (até a última nota mais a folga do fim, quando vem o resultado), na Biblioteca e no Spotify.
- **Pausar:** botão **Pausar** ou o gesto de voltar. Na biblioteca, a partida também pausa sozinha numa ligação, quando outro app começa a tocar ou quando o fone é desconectado (no Spotify, o próprio Spotify pausa e o jogo acompanha). No menu de pausa, **Sincronia desta música** ajusta quando as notas chegam: **−1 s / +1 s** para diferenças grandes (por exemplo, um mapa com contagem de entrada) e **−10 ms / +10 ms** para o fino. O valor vale ao continuar e fica salvo para aquela música.
- **Calibração** (Configurações → Jogo → Calibrar atraso do áudio): toque junto com as batidas para medir o atraso do seu fone ou alto-falante. Útil com fone Bluetooth; com o alto-falante do celular costuma ser melhor deixar em 0 (**Zerar**).
- **Configurações**, em abas (abre sempre em **Geral**):
  - **Geral:** **Idioma** (Português, o padrão, ou English: troca na hora; no Android 13 ou mais novo também fica em Configurações do celular → Apps → Riff → Idioma) e sons do menu (liga e desliga).
  - **Sons:** volume geral, do acerto e do erro.
  - **Jogo:** velocidade das notas (0,5× a 2×), modo canhoto (trilhas espelhadas) e calibração.
  - **Visual da pista:** os quatro designs e a imagem do mapa (abaixo).
  - **Guitarra IA:** apagar separações e "Perguntar sempre se separa".
- **Visual da pista:** quatro escolhas nas Configurações, cada uma com miniaturas. **Fundo da pista**: Palco (o de sempre), Amplificador, Couro e rebites, Braço de guitarra, Chamas, Neon, Chapa de metal. **Design da pista** (desenho claro estampado na pista, rolando com a música): Sem desenho, Caveira alada, Raios, Estrelas, Guitarras, Palhetas, Tatuagem. **Design das notas**: Discos, Palhetas, Caveiras, Estrelas, Raios, Chamas. **Design dos botões**: Discos, Pedais de efeito, Knobs de amplificador, Alto-falantes, Palhetas. As cores das trilhas não mudam. Tudo é desenhado pelo app, em código.
- **Imagem do mapa** (ligada de fábrica): mostra atrás da pista, escurecida, a imagem de fundo do mapa (`background.jpg`/`.png`) ou, sem ela, a capa (`album.jpg`/`.png`).
- **Sons do menu:** os botões fora da partida tocam guitarra, no volume geral: abrir ou escolher algo dá um toque abafado, começar ou salvar dá um power chord (cada um sorteia entre duas gravações) e voltar, pelo botão ou pelo gesto do celular, arrasta a palheta na corda. Durante a partida os botões ficam em silêncio.

## Desenvolvimento

### Comandos

```bash
./scripts/gw :app:assembleDebug                # APK de debug
./scripts/gw test                              # testes unitários
./scripts/gw detekt -PdetektAutoCorrect        # formata (ktlint) e corrige
./scripts/gw detekt                            # análise estática
./scripts/gw lintDebug                         # Android Lint
```

O Robolectric não funciona quando o caminho do `~/.m2` tem espaço (ele codifica o espaço como `%20`). Nesse caso, passe `-Priff.robolectric.mavenRepoLocal=<pasta sem espaço>`, como o `scripts/gw` já faz, ou coloque a propriedade no `gradle.properties` do usuário para o Android Studio.

### Módulos

| Módulo | Conteúdo |
|---|---|
| `:app` | Activity, Application com Hilt e navegação Tocando agora → Jogo → Resultado (e a Biblioteca); Baseline Profile em `src/release/generated/baselineProfiles` |
| `:core:ui` | Identidade "Pôster de turnê": tema escuro único, fontes New Rocker e Oswald (licenças OFL em `core/ui/licenses/`), componentes e peças de desenho da pista (com `StageCache`, sem alocar por quadro), com screenshots em `src/test/screenshots` |
| `:core:model` | Modelos de domínio |
| `:core:chart` | Leitura de mapas do Clone Hero (`notes.chart`, `notes.mid`, `song.ini` e pacotes `.sng`), e o casamento entre faixa do Spotify e mapa |
| `:core:game` | Relógio da música e motor do jogo (julgamento, combo e pontuação) |
| `:core:audio` | Player da música (MediaPlayer, ou faixas separadas misturadas com a guitarra cortada no erro), detecção do começo da música e sons de acerto, erro e menu (SoundPool), gerados por `scripts/gerar_sfx.py`, `scripts/montar_sfx_erro.py` e `scripts/montar_sfx_menu.py` |
| `:core:database` | Room: mapas, dificuldades, casamentos com faixas do Spotify, ajustes de sincronia e recordes (esquemas em `schemas/`) |
| `:core:datastore` | Preferências do jogador e pasta da biblioteca |
| `:core:spotify` | Conexão com o app do Spotify (App Remote SDK), autorização, estado do player e o player da partida sobre o Spotify |
| `:core:data` | Repositório de mapas, acesso a pastas por SAF (leitura, `.sng` como pasta e gravação), importador de mapas e download de mapas do Encore |
| `:core:encore` | Cliente do Encore (enchor.us): pesquisa, marca de guitarra separada e download |
| `:core:separation` | Guitarra separada por IA: o modelo htdemucs_6s em ONNX (`scripts/exportar_modelo_ia.sh`), a STFT e a separação por trechos portadas do demucs.onnx, a decodificação, a gravação em `.m4a` e o trabalho em segundo plano (WorkManager) |
| `:feature:library` | Tela da Biblioteca: escolher pasta, importar, lista e relatório |
| `:feature:search` | Pesquisa no Encore: resultados, download com progresso e Jogar |
| `:feature:gameplay` | A partida: pista em movimento, toque com vários dedos, chama, sons, pausa, sincronia e fim |
| `:feature:results` | Resultado da partida e recorde |
| `:feature:calibration` | Calibração do atraso do áudio: metrônomo, toques, resultado e Zerar |
| `:feature:settings` | Configurações: volume geral, de acerto e de erro, sons do menu, velocidade das notas e modo canhoto |
| `:feature:nowplaying` | Tela inicial "Tocando agora": faixa atual, mapa casado, confirmar ou escolher o mapa e jogar |
| `:baselineprofile` | Percursos automáticos (UiAutomator) que geram o Baseline Profile e medem abertura e partida (Macrobenchmark) |

### Screenshots

```bash
./scripts/gw recordRoborazziDebug   # regrava as referências (todos os módulos) depois de uma mudança visual intencional
./scripts/gw verifyRoborazziDebug   # compara com as referências
```

A comparação de screenshots é local: rode o `verifyRoborazziDebug` antes de commitar mudanças de UI. O CI não compara as imagens, porque o Robolectric desenha as fontes de forma diferente no Linux e no Windows (veja `docs/pendencias.md`).

### Desempenho

O **Baseline Profile** diz ao Android quais partes do app compilar antes da primeira execução, para a abertura e a partida já começarem rápidas. Ele é gerado no celular por percursos automáticos (abrir o app, rolar a biblioteca, jogar 20 s de uma música e sair).

**Vale só para o build release** (`./scripts/gw :app:assembleRelease`, assinado com a chave de debug): o Android ignora o perfil em apps de debug. Os números abaixo são do release. As otimizações do desenho da pista (sem alocar memória a cada quadro) valem nos dois. Depois de mexer nas regras do R8 (`app/proguard-rules.pro`), rode `scripts/conferir_r8_spotify.sh`: ele confere que o SDK do Spotify continua achando o app no release.

```bash
scripts/desempenho.sh perfil    # gera o perfil de novo
scripts/desempenho.sh medir     # mede abertura e partida, sem e com o perfil
scripts/desempenho.sh tudo      # os dois
uv run --with perfetto python3 scripts/quadros_travados.py   # fps e quadros com atraso, a partir dos traces
```

- Celular conectado, desbloqueado, com a tela ligada (tempo de tela longo) e sem economia de bateria; não mexa nele durante a execução (10 a 15 minutos).
- A música do percurso precisa estar na biblioteca (o percurso rola a lista até achá-la): por padrão "Smells Like Teen Spirit"; outra com `scripts/desempenho.sh tudo "-Pandroid.testInstrumentationRunnerArguments.riff.song=<nome>"`.
- O perfil de abertura (`startup-prof.txt`) leva só a abertura do app; a biblioteca e a partida entram no perfil geral. A medição da partida conta só os 20 s de jogo (abrir a música e sair ficam de fora).
- O app medido substitui o de debug no celular sem apagar os dados (mesma chave). Antes, o script para o app e copia os dados para uma pasta nova em `.copias-dados/<data>/` (fora do git); no fim, reinstala o debug, mesmo se algo falhar.
- **Restaurar uma cópia** (só se os dados sumirem), com o app de debug instalado e parado (`adb shell am force-stop com.rodrigomachado.riff`): para cada arquivo da pasta, `adb push <arquivo> /data/local/tmp/` e `adb shell "run-as com.rodrigomachado.riff sh -c 'cat /data/local/tmp/<arquivo> > databases/<arquivo>'"` (o `riff_settings.preferences_pb` vai para `files/datastore/`). Apague do aparelho o `riff.db-wal`/`-shm` que não estiver na cópia.
- **Quando regerar o perfil:** depois de mudanças grandes em telas ou no gameplay. Um perfil desatualizado não quebra nada: só deixa de acelerar o que mudou.
- O `frameDurationCpuMs` do Macrobenchmark inclui a espera na fila da tela ("Buffer Stuffing", o app adiantado). Para saber se a partida trava, use o `quadros_travados.py`.

Medido em 2026-10-16 num Galaxy S25 (Android 16, tela a 120 Hz), com o visual mais pesado da pista (Chamas, Caveira alada, notas Caveiras, botões Alto-falantes e a imagem do mapa):

| Medida | Sem perfil | Com perfil |
|---|---|---|
| Abertura até a primeira tela | 176 ms | 151 ms |
| Partida: quadros por segundo | 120 | 120 |
| Partida: quadros com atraso do app (em ~2.430) | 1 a 4 | 0 a 1 |

Com o visual padrão (2026-10-12): abertura 188 / 163 ms; 120 fps; 0 a 2 / 1 quadro com atraso; `frameDurationCpuMs` p50 / p90 de 5,0 / 6,8 ms sem perfil e 4,3 / 6,0 ms com perfil.

Na medição anterior (2026-10-09, perfil de abertura igual ao geral e a partida contando a navegação), a abertura com perfil deu 148 ms; a variação entre execuções é de algumas dezenas de ms.

## Créditos e licenças

- Som de erro e sons do menu: montados com gravações de guitarra com licença Creative Commons 0 do Freesound (origem e autores em `third_party/README.md`).
- Som de acerto e metrônomo da calibração: sintetizados pelo próprio projeto.
- Separação da guitarra: modelo Demucs v4 htdemucs_6s (Meta, MIT), adaptado para ONNX pelo demucs.onnx (Sevag Hanssian, MIT); ONNX Runtime (Microsoft, MIT); JTransforms (Piotr Wendykier, BSD-2).
- Fontes New Rocker e Oswald: licença OFL (`core/ui/licenses/`).
- Mapas baixados pela pesquisa: Encore (enchor.us), feitos pela comunidade do Clone Hero.
- Nada do Guitar Hero nem de projetos que redesenham seus assets: a pista, as gemas e a chama são desenhadas em código.

## Status

O roteiro da especificação principal está concluído (2026-10-09): fundação, identidade visual, biblioteca, gameplay, integração com o Spotify, guitarra separada, configurações, calibração e polimento.

Pendências conhecidas (detalhes em `docs/pendencias.md`): pontos menores das revisões finais. Ideias para depois: separar a guitarra dentro do app.

Cada mudança no uso do app atualiza a seção correspondente deste README.
