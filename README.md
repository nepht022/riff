<p align="center"><img src="docs/icone.png" width="160" alt="Ícone do Riff"></p>

# Riff

Jogo de ritmo para Android no estilo Guitar Hero, feito para uso pessoal (não é publicado na Play Store; veja a seção 3 da especificação). As notas vêm de mapas do Clone Hero que você mesmo importa. Há dois jeitos de jogar:

- **Biblioteca, para jogar:** a música vem da pasta do mapa. Se a pasta traz a guitarra separada, ela some quando você erra e volta quando acerta, como no Guitar Hero. Sem ela, a música toca inteira e o acerto fica em silêncio, como no Spotify. Ao continuar depois de uma pausa, a pista volta 0,5 s, como uma fita, para você retomar no ritmo.
- **Tocando agora (Spotify), para ouvir e tocar junto:** o Riff acompanha a faixa que está tocando no Spotify e procura o mapa dela na biblioteca. A sincronia é ajustada automaticamente na primeira vez: pelo começo da música no áudio do mapa, sem passar da sobra de duração do áudio do mapa em relação ao Spotify (numa mesma gravação, como os mapas oficiais, dá 0) nem da primeira nota. Você refina na pausa, e o valor fica salvo para a música. Aqui a música segue de onde parou ao continuar e o acerto não faz som. Os botões **⏮ ⏯ ⏭** comandam o próprio Spotify (anterior, tocar/pausar, próxima), antes e depois de escolher o mapa e também na pausa da partida; ali, trocar de música encerra a partida sem salvar o placar; se a música nova tiver **mapa encontrado**, a próxima partida começa direto, na mesma dificuldade (ou pede a dificuldade, se o mapa não tiver a mesma); com **sugestão**, a partida dela abre pausada com **Confirmar mapa e jogar · Escolher outro · Voltar ao menu** (o Spotify segue tocando; se ele passar de música antes, o Riff segue para a nova); sem mapa, volta ao Tocando agora. **Configurações → Spotify → Ao acabar a música:** **Ir direto para a próxima** (padrão) salva o placar sem mostrar o Resultado e segue para a próxima música do mesmo jeito (se as notas acabarem antes da música, espera o Spotify trocar até o fim dela + 10 s); **Mostrar o resultado** vai para o Resultado, como antes. O Tocando agora mostra também a barra de tempo da música, com os botões ⏮ ⏯ ⏭ logo abaixo, e o mapa com Jogar no pé da tela.

- Especificação: `docs/superpowers/specs/2026-10-05-riff-design.md` (e as especificações de cada etapa na mesma pasta)
- Planos: `docs/superpowers/plans/`
- Pendências conhecidas: `docs/pendencias.md`


**Instalar:** baixe o `Riff-<versão>.apk` em **Releases** (à direita na página do repositório), abra no celular Android (8 ou mais novo) e permita "instalar apps desconhecidos". Para o modo Spotify, ligue o Riff em "Acesso às notificações" quando o Tocando agora pedir.

## Instalar e configurar

### Requisitos

- Android Studio (o JBR 21 é usado como JDK) e Android SDK com a plataforma 37
- Um celular com Android 8 ou mais novo (o desempenho foi medido num Galaxy S25)
- No WSL, o Gradle roda pelo `./scripts/gw`, que chama o `gradlew.bat` do Windows; no Linux e no macOS, use `./gradlew`

### Spotify: permitir o acesso a notificações

O Riff acompanha o Spotify pela **sessão de mídia do Android** (o mesmo controle de música da tela de bloqueio): nada de SDK, login, Client ID ou cadastro no Dashboard do Spotify, e funciona com conta grátis.

1. Instale o app do Spotify no celular.
2. No **Tocando agora**, toque em **Permitir** e ligue o **Riff** em "Acesso às notificações". O Riff usa a permissão só para ver a música, a posição e a fila do Spotify e mandar tocar, pausar e pular; ele não lê as notificações.
3. Dê play numa música no Spotify: ela aparece no Tocando agora.

Sem a permissão, o Tocando agora explica e oferece o modo Biblioteca. Numa conta grátis, um anúncio pausa a partida (ou adia a próxima, no modo contínuo) até acabar; ⏮, ⏭ e "Jogar de novo" seguem os limites do Spotify grátis. Até a versão 0.2.0 o Riff usava o App Remote SDK; os mapas escolhidos e os ajustes por música continuam valendo (a música é reconhecida pelo mesmo `spotify:track:…`).

### Chave de release

O debug e o release são assinados com a mesma chave própria, fora do git: assim um substitui o outro no celular sem apagar os dados, e quem recebeu o APK consegue atualizar. A chave fica em `~/.android/riff-release.jks` (no Windows, `C:\Users\<você>\.android\`) e a senha no `local.properties`:

```
riff.keystore.password=<senha>
riff.key.alias=riff
```

Crie a chave uma vez com `keytool -genkeypair -keystore ~/.android/riff-release.jks -alias riff -keyalg RSA -keysize 4096 -validity 10000`. **Guarde o `.jks` e a senha fora do PC**: sem eles, nenhuma atualização instala por cima e todo mundo precisa desinstalar (perdendo recordes e ajustes). Sem a chave, o debug usa a chave de debug e o release **para com erro** (um APK com outra chave não instala por cima do de quem já tem o Riff). **Trocar a chave** obriga quem tem a versão antiga a desinstalar, perdendo recordes, ajustes e a chave do ranking (o nome fica preso até ser liberado na planilha); no próprio celular dá para salvar os dados antes com o app de debug (`run-as`, como em [Desempenho](#desempenho)) e devolvê-los depois.

### Enviar o APK para outras pessoas

1. Suba `versionCode` e `versionName` em `app/build.gradle.kts` (a versão nova só instala por cima de uma mais antiga).
2. `./scripts/gw :app:assembleRelease` e envie `app/build/outputs/apk/release/app-release.apk` (~190 MB, só celulares ARM, Android 8+). A pessoa precisa permitir "instalar apps desconhecidos" para o app que abrir o arquivo.
3. Funciona sem nada além do APK: biblioteca, pesquisa no Encore, guitarra separada por IA, ranking e Reportar (os endereços dos scripts vão dentro do APK).
4. **Spotify:** basta a pessoa ter o app do Spotify (Premium ou grátis) e ligar o Riff em "Acesso às notificações" quando o Tocando agora pedir. Ninguém precisa ser cadastrado.


## Importar mapas

1. Na **Biblioteca**, toque em **Escolher pasta** e escolha a pasta que guarda os seus mapas. **Reimportar** lê a pasta de novo; **Trocar pasta** escolhe outra.
2. Cada música é uma subpasta no formato do Clone Hero:
   - `notes.chart` ou `notes.mid` (as notas) e `song.ini` (nome, artista, atraso);
   - o áudio: `song.ogg`, `.opus`, `.mp3` ou `.wav`.
3. **Guitarra separada:** se a subpasta tiver `guitar.*` e pelo menos uma faixa de acompanhamento (`song`, `rhythm`, `bass`, `drums`, `drums_1` a `drums_4`, `vocals`, `vocals_1`, `vocals_2`, `keys`, `crowd`), o Riff toca todas juntas e corta só a guitarra no erro.
4. Mapas `.sng` (o pacote do Encore e do YARG, um arquivo por música) são lidos direto: é só colocar o arquivo na pasta.
5. **Excluir:** o **X** vermelho de cada mapa apaga a pasta dele (ou o `.sng`) do celular, junto com a guitarra separada pela IA e o recorde; **Selecionar** marca vários para excluir de uma vez, e o ícone de caixas na mesma linha marca (ou desmarca) todos. Sempre pede confirmação e não dá para desfazer. Mapas soltos direto na pasta da biblioteca não têm o X (apagar a pasta deles apagaria a biblioteca inteira).
6. Não use mapas da Harmonix/Neversoft (rips de Rock Band ou Guitar Hero): são conteúdo protegido. Prefira mapas feitos pela comunidade.

## Pesquisar e baixar mapas

- **Biblioteca → Pesquisar:** digite a música ou o artista. A lista mostra os mapas do Encore (enchor.us), inclusive os oficiais de Rock Band e Guitar Hero, com as dificuldades da guitarra e a marca **Guitarra separada** quando o pacote traz a guitarra à parte. **Baixar** grava o `.sng` em "Baixados pelo Riff", dentro da pasta da biblioteca, e o mapa já entra na lista (**Jogar**). Os que você já tem aparecem como "Já na biblioteca".
- **Originais | Versões** escolhe entre as gravações originais e covers, remixes, ao vivo e medleys (pelo título). **Só com guitarra separada** procura nas páginas seguintes sozinho até achar 10 mapas ou conferir 100 resultados; **Procurar mais** confere outros 100.
- **Tocando agora → Pesquisar no Encore** (faixa sem mapa ou só com sugestão): a pesquisa já vem com o artista e o título, os mais parecidos primeiro; o mapa baixado fica casado com a faixa e você volta com **Jogar**.
- Na primeira vez, o Riff pede para escolher a pasta de novo, agora com permissão de gravar nela (**Permitir**).
- O pacote vem com o áudio da música (uso pessoal). A API do Encore não é oficial: se ela mudar, a pesquisa avisa que o Riff precisa ser atualizado.

## Mapas da fila do Spotify

- **Configurações → Spotify → Baixar mapas da fila** (desligada de fábrica): enquanto você ouve, o Riff lê a fila do Spotify (pela sessão de mídia, relida a cada mudança) e baixa do Encore os mapas das **duas próximas músicas** e, se faltar, o da **música que está tocando** (inclusive aberta direto no Spotify). A fila da sessão não traz a duração: o mapa baixado vira **MAPA ENCONTRADO** quando a música começa e a duração bate (ou **CONFIRMAR MAPA**, se for outra). A fila baixa também **durante a partida**, um mapa por vez. Ao passar de música, ela chega como **MAPA ENCONTRADO** e a partida dela começa direto; se o mapa ainda estiver baixando, o Tocando agora mostra "Baixando o mapa desta música…" e espera.
- **Escolha do mapa:** só resultados com semelhança alta (a mesma do "mapa encontrado" automático) e com guitarra; originais antes de covers, remixes e ao vivo; desempate pela guitarra separada e por mais dificuldades. Sem resultado confiável, não baixa nada.
- **Apagar os que eu não joguei** (ligada): o mapa baixado pela fila some quando a música passa (ou sai da fila) sem você jogar. Jogado, ou baixado na mão, nunca é apagado.
- **Usar dados móveis** (desligada): sem ela, só baixa no Wi-Fi. **Músicas com mapas já baixados** (quando o mapa da biblioteca é só sugestão): **Baixar nova sugestão** (padrão) ou **Manter o que já tenho**. **Sem mapa com a mesma duração** (o Encore só tem mapas com duração diferente da faixa do Spotify): **Baixar o de duração mais próxima** (padrão; até 1 minuto de diferença ou, sem nenhum assim, um mapa sem duração no Encore; o mapa chega como sugestão, com **CONFIRMAR MAPA**, e substitui uma sugestão da biblioteca menos confiável) ou **Não baixar nenhum**. Quando a fila não consegue um mapa, o **Tocando agora** conta o motivo embaixo de "Nenhum mapa da biblioteca…": não achou no Encore, só com outra duração, a pesquisa falhou ou o download falhou. O motivo também vai para o registro do Android (`adb logcat -s RiffQueue`).
- Funciona com o Riff à vista e com o "Acesso às notificações" ligado (qualquer tela; em segundo plano, a fila para). **Sem a duração** (a fila não traz), a opção **Baixar o de duração mais próxima** só vale quando a duração já é conhecida. Em rádio ou aleatório, o Spotify pode não mostrar a fila: aí nada é adiantado. Falhas aparecem no logcat com a tag `RiffQueue`.
- **Privacidade:** o Riff lê a fila do Spotify só para isso; nada é enviado a outro lugar além das pesquisas no Encore.

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
- A tela diz, abaixo do email, que o relato vai para o desenvolvedor e que o email só serve para a resposta.
- Antes de distribuir o app (Play Store), declarar na ficha de segurança dos dados que ele envia o texto do relato, o email opcional e as informações do aparelho.

## Ranking online

- **Nome de jogador:** na primeira vez que o Riff abre, ele pede o seu nome no ranking (3 a 20 letras, números, espaço, `_` ou `-`), avisando que o nome e as melhores pontuações ficam visíveis para todos. O nome fica com este celular: ninguém mais pode usar ("Rodrigo" e "rodrigo" contam como o mesmo). **Agora não** adia até a próxima abertura. Para trocar: **Configurações → Geral → Nome no ranking** (o antigo fica livre; as suas pontuações acompanham o nome novo).
- **Envio automático:** ao acabar uma partida que seja a sua melhor naquele mapa e dificuldade em algum número, ela vai sozinha para o ranking — também quando o Resultado é pulado ("Ir direto para a próxima"). O Resultado mostra "Ranking: 3º de 27" e **Ver ranking**. Sem internet, a partida fica na fila e vai quando a conexão voltar. No primeiro nome, os seus melhores já guardados no celular vão uma vez, em lotes de até 50 por pedido.
- **Tela Ranking** (no topo do Tocando agora e da Biblioteca): as músicas com pontuação (busca por música ou artista) e o placar de cada uma, por dificuldade, ordenado por **Pontos**, **Precisão**, **Combo**, **Perfeitos** ou **Tempo sem errar** (a maior sequência sem erro, em m:ss), do maior para o menor ou ao contrário, com busca por nome e a sua linha em destaque.
- **"Mesma música" = mesmo mapa** (as mesmas notas): mapas diferentes da mesma música têm rankings separados.
- **Onde fica:** um Google Apps Script com uma planilha na conta do dono do app (`scripts/ranking_apps_script.gs`; o passo a passo de publicar está no começo do arquivo). O endereço vai no `local.properties` como `ranking.url=…/exec`; sem ele, o ranking fica desligado. Só o apelido e as pontuações ficam guardados; a chave do celular vai como hash. Para tirar alguém do ranking (pedido pelo Reportar, nome ofensivo), rode `removerJogador("Nome")` no editor do script: apaga o jogador e as pontuações, e o celular dele esquece o nome e não volta sozinho. Apagar só a linha do jogador à mão faz o celular reservar o mesmo nome de novo no próximo envio. Testes do script sem o Google: `node scripts/ranking_apps_script.test.js`.
- **Jogador apagado da planilha:** no próximo envio, o celular reserva o mesmo nome de novo e manda a partida; se outra pessoa já tiver pego o nome, o app pede um nome novo (a partida espera na fila).
- **Limites do script** (o endereço vai dentro do APK): 60 pedidos de envio e 300 pontuações por hora por celular (um lote é um pedido), 5 reservas de nome por hora por celular (tentar um nome em uso conta; salvar o mesmo nome não) e 100 nomes novos por hora no total; chaves curtas ou vazias são recusadas. O app continua funcionando com uma versão antiga do script (manda uma pontuação por vez) até ela ser republicada.

## Licenças e privacidade

**Configurações → Geral → Licenças e privacidade**: o que o app guarda e envia (ranking, Reportar, Spotify, Encore), como pedir para sair do ranking e as licenças de tudo que vem de fora. A lista das bibliotecas é gerada no build pelo plugin AboutLibraries (`app/build.gradle.kts`); o que não vem do Gradle (modelo Demucs, demucs.onnx, SDK do Spotify, fontes, sons do Freesound, mapas do Encore) está em `feature/licenses/.../AndroidLicenseSource.kt`, com os textos em `feature/licenses/src/main/assets/licencas/`. Ao adicionar algo de fora do Gradle, acrescente nessa lista.

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

## Qualidade e testes (QA)

O Riff é feito com **TDD**: cada mudança começa por um teste que falha pelo motivo certo, depois vem o código mínimo que o faz passar, e no fim roda a suíte inteira. Números de 2026-10-20: **~1.100 testes** em 163 arquivos, **55 screenshots** de referência e 26 testes do script do ranking.

### Camadas de teste

| Camada | O que cobre | Onde |
|---|---|---|
| Unidade (JVM) | motor do jogo (julgamento, combo, pontuação, relógio), leitura de mapas (`.chart`, `.mid`, `.sng`, `song.ini`), casamento faixa × mapa, fila do Spotify, ranking | `core/*/src/test` |
| Robolectric | ViewModels, repositórios, players de áudio, sessão de mídia do Spotify (com uma sessão falsa), Room | `core/*/src/test`, `feature/*/src/test` |
| Interface (Compose) | cada tela e cada estado (sem permissão, sem mapa, anúncio, pausa, erros), cliques, sons do menu, textos em pt e en | `feature/*/src/test/*ContentTest.kt` |
| Screenshots (Roborazzi) | o visual de cada tela comparado pixel a pixel com a referência | `*/src/test/screenshots` |
| Migração do banco | cada versão do esquema do Room migra sem perder dados (casamentos, ajustes, recordes) | `core/database/src/test/.../MigrationTest.kt` |
| Golden da IA | a separação da guitarra (Demucs em ONNX) reproduz a saída de referência | `core/separation/src/test` |
| Script do ranking | o Apps Script roda em Node com uma planilha falsa que imita as conversões do Google Planilhas | `node scripts/ranking_apps_script.test.js` |
| Aparelho | medição de sincronia (registro temporário `RiffSync` no `adb logcat`), quadros atrasados e abertura (Macrobenchmark + Baseline Profile) | `scripts/desempenho.sh`, `scripts/quadros_travados.py` |

### Portões de qualidade

- **Detekt + ktlint** (`config/detekt/detekt.yml`): limites de tamanho de função (60 linhas), de funções por classe e arquivo (11), de complexidade (14) e de linha (120 colunas).
- **Android Lint** com erros quebrando o build.
- **Kover:** cobertura mínima de **70%** em `:core:chart`, `:core:game` e `:core:data` (`koverVerify`).
- **Suíte completa antes de cada merge:**

  ```bash
  ./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --continue
  node scripts/ranking_apps_script.test.js
  ```

- **CI** (GitHub Actions, `.github/workflows/ci.yml`): detekt, lint, testes, screenshots, Kover e o APK de debug a cada push na `main` e em pull requests. O modelo da IA vem do Git LFS, com cache.

### Processo de cada mudança

1. **Especificação e plano** para mudanças grandes (`docs/superpowers/specs` e `docs/superpowers/plans`). As mudanças pequenas são combinadas na conversa.
2. **TDD** tarefa a tarefa, com um registro das decisões tomadas no caminho.
3. **Revisão final** da branch inteira por um revisor independente (outro modelo, sem o contexto de quem implementou). Problemas críticos e importantes são corrigidos com um teste que falha primeiro; os menores vão para `docs/pendencias.md`.
4. **Aceite no aparelho** (Galaxy S25) antes do merge, com registro temporário quando o problema é de tempo (sincronia, fila).
5. O **README** e o **Baseline Profile** são atualizados junto com a mudança.

### Testes instáveis conhecidos

Passam ao rodar de novo sozinhos (efeito de carga ou do Windows):

- `lintAnalyzeDebugUnitTest` (crash do analisador do lint);
- `ResamplingPcmSourceTest` (tempo, sob carga);
- `SettingsContentTest` (espera ociosa do Compose, sob carga);
- `MigrationTest` (FileNotFound no esquema JSON recém-gerado na primeira compilação).

Pendências e pontos menores das revisões ficam em `docs/pendencias.md`.

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
| `:core:spotify` | Spotify pela sessão de mídia do Android ("Acesso às notificações"): estado do player, comandos, fila e o player da partida sobre o Spotify |
| `:core:data` | Repositório de mapas, acesso a pastas por SAF (leitura, `.sng` como pasta e gravação), importador de mapas e download de mapas do Encore |
| `:core:encore` | Cliente do Encore (enchor.us): pesquisa, marca de guitarra separada e download |
| `:core:separation` | Guitarra separada por IA: o modelo htdemucs_6s em ONNX (`scripts/exportar_modelo_ia.sh`), a STFT e a separação por trechos portadas do demucs.onnx, a decodificação, a gravação em `.m4a` e o trabalho em segundo plano (WorkManager) |
| `:feature:library` | Tela da Biblioteca: escolher pasta, importar, lista e relatório |
| `:feature:search` | Pesquisa no Encore: resultados, download com progresso e Jogar |
| `:feature:gameplay` | A partida: pista em movimento, toque com vários dedos, chama, sons, pausa, sincronia e fim |
| `:feature:results` | Resultado da partida e recorde |
| `:feature:calibration` | Calibração do atraso do áudio: metrônomo, toques, resultado e Zerar |
| `:feature:settings` | Configurações em abas: idioma, nome no ranking, sons, jogo, visual da pista, guitarra IA e Spotify |
| `:feature:ranking` | Pedido do nome, músicas com pontuação e placar do ranking online |
| `:feature:report` | Reportar problema ou sugestão |
| `:feature:licenses` | Licenças e privacidade (lista das bibliotecas pelo AboutLibraries) |
| `:feature:nowplaying` | Tela inicial "Tocando agora": faixa atual, mapa casado, confirmar ou escolher o mapa e jogar |
| `:baselineprofile` | Percursos automáticos (UiAutomator) que geram o Baseline Profile e medem abertura e partida (Macrobenchmark) |

### Screenshots

```bash
./scripts/gw recordRoborazziDebug   # regrava as referências (todos os módulos) depois de uma mudança visual intencional
./scripts/gw verifyRoborazziDebug   # compara com as referências
```

Rode o `verifyRoborazziDebug` antes de commitar mudanças de UI; o CI compara de novo a cada push. A comparação é exata: no Linux do CI o Robolectric desenha as telas iguais às gravadas no Windows (as fontes do app vêm junto com ele).

### Desempenho

O **Baseline Profile** diz ao Android quais partes do app compilar antes da primeira execução, para a abertura e a partida já começarem rápidas. Ele é gerado no celular por percursos automáticos (abrir o app, rolar a biblioteca, jogar 20 s de uma música e sair).

**Vale só para o build release** (`./scripts/gw :app:assembleRelease`, assinado com a mesma chave do debug): o Android ignora o perfil em apps de debug. Os números abaixo são do release. As otimizações do desenho da pista (sem alocar memória a cada quadro) valem nos dois.

```bash
scripts/desempenho.sh perfil    # gera o perfil de novo
scripts/desempenho.sh medir     # mede abertura e partida, sem e com o perfil
scripts/desempenho.sh tudo      # os dois
uv run --with perfetto python3 scripts/quadros_travados.py   # fps e quadros com atraso, a partir dos traces
```

- Celular conectado, desbloqueado, com a tela ligada (tempo de tela longo) e sem economia de bateria; não mexa nele durante a execução (10 a 15 minutos).
- A música do percurso precisa estar na biblioteca (o percurso rola a lista até achá-la): por padrão "Smells Like Teen Spirit"; outra com `scripts/desempenho.sh tudo "-Pandroid.testInstrumentationRunnerArguments.riff.song=<nome>"`.
- O perfil de abertura (`startup-prof.txt`) leva só a abertura do app; a biblioteca e a partida entram no perfil geral. A medição da partida conta só os 20 s de jogo (abrir a música e sair ficam de fora).
- O app medido substitui o de debug no celular sem apagar os dados (mesma chave, veja [Chave de release](#chave-de-release)). Antes, o script para o app e copia os dados para uma pasta nova em `.copias-dados/<data>/` (fora do git); no fim, reinstala o debug, mesmo se algo falhar.
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
