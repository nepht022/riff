# Pendências conhecidas

Decisões adiadas de propósito, com o motivo e quando retomar.

## Ideias para depois

- **Riff para Windows (PC):** é o projeto 2, decidido em 2026-10-30 (depois da 0.5.0 sem o Spotify, e antes do multiplayer por código de sala): app nativo com Compose Multiplatform Desktop, reaproveitando o motor do jogo, a leitura dos mapas, o desenho da pista, o ranking e a pesquisa no Encore (o projeto vira Kotlin Multiplatform). **Controles:** teclado (F1 a F5 e a palheta, teclas configuráveis) e controle de videogame, com a opção "Tocar sem palheta". Refazer: áudio (decodificação e saída sem MediaCodec), acesso a pastas e a ligação das peças (Hilt só existe no Android).

## Pontos menores em aberto (revisões de 2026-10-09 a 2026-10-13)

Os da calibração já resolvidos saíram da lista (retrocesso pausado, retomada depois de reconectar, pausa antes do primeiro início).

- `MediaCodec.getOutputBuffer` aloca um objeto por bloco decodificado (interno à plataforma, ~40 por segundo); sem efeito medido.
- Soltar um dedo também pode marcar um erro antes do quadro e religar a guitarra um pouco antes da hora.
- Relatório: o mapa repetido mostra só o nome da pasta ("Song: igual ao de Song" quando há duas pastas "Song" em pacotes diferentes); qual cópia vence depende da ordem de listagem do Android.
- Trocar para uma pasta cuja importação falha já libera o acesso à pasta antiga (os mapas dela continuam listados, mas não tocam).
- Com teclado ou controle, segurar Enter no botão em foco desloca o botão, mas o contorno de foco fica no lugar.
- O teste de altura do botão com fonte 2× não pega regressão (a altura mínima de 48 dp vale sempre); trocar por uma verificação de texto cortado.
- Decodificador que trava no meio da música: a leitura desiste em 2 a 4 s, mas segura a trava de áudio nesse tempo (pausar e continuar espera; sair fecha o decodificador com a leitura em andamento).
- Troca de canais ou de taxa no meio da faixa: a checagem compara com o início de cada leitura, não com o formato inicial; depois de voltar 0,5 s, a faixa pode tocar com ruído.
- Partida indisponível (UNEXPECTED, CHART_UNREADABLE) e ajuste não salvo por restrição do banco não deixam registro no logcat.
- Erro do banco ao ler o mapa e arquivo apagado (FileNotFoundException) aparecem como "não deu para ler este mapa".
- Se a saída de áudio nova não abrir ao continuar, a pista anima a volta de 0,5 s e depois pula de volta (a música segue de onde estava).
- A detecção do começo da música só cancela entre blocos: abrir e ler cada faixa ainda pode ocupar o IO por alguns segundos.
- A última repetição da medição da partida termina sem sair dela (a música segue até o script reinstalar o app).
- Sons do menu: o primeiro toque logo ao abrir o app pode sair mudo (os sons e as preferências ainda carregando, dezenas de ms).
- Sons do menu: o som de voltar tem volume médio uns 6 dB abaixo dos outros (picos iguais); com o volume geral baixo, quase some.
- Sons do menu: a lista de telas do detector de volta guarda ids de telas trocadas por "Jogar de novo" (custo desprezível).
- Pesquisa no Encore: a API não é documentada (vista no código do Bridge); se mudar, a pesquisa avisa "o Riff precisa ser atualizado".
- Pesquisa: dois downloads ao mesmo tempo com o mesmo "Artista - Nome" podem gerar um nome sem ".sng" (o mapa some no Reimportar); Reimportar enquanto um download termina pode apagar o mapa recém-baixado; sair da tela entre a importação e o registro deixa um mapa sem o arquivo.
- Pesquisa: escolher outra pasta em "Permitir" troca a raiz e libera a antiga sem reimportar (os mapas listados param de abrir até o Reimportar).
- Pesquisa: a consulta do começo do arquivo confia que o servidor respeita o Range.
- `.sng`: checagem de limites com overflow num pacote forjado (vira falha, sem crash); descritor não fechado quando a abertura falha; cada abertura lê 256 KB (importação de centenas de pacotes fica mais lenta); `.part` órfão se o sistema matar o app no meio do download; nome cortado em caracteres, não em bytes.
- Guitarra por IA: cada trecho copia ~85 MB na heap (saídas do modelo); em celulares com heap pequena pode faltar memória.
- Guitarra por IA: reimportar exatamente no fim de uma separação pode apagar os arquivos recém-gravados (a linha fica marcada, a música toca inteira com a marca IA).
- Biblioteca: um toque para jogar pode se perder se a navegação estiver bloqueada naquele instante (o pedido é descartado junto).
- O resto das revisões anteriores (biblioteca e importação, acessibilidade e robustez interna) está no histórico de cada plano; retomar pelo que incomodar no uso.

## Pontos menores do visual da pista (revisão de 2026-10-14)

- **Imagem do mapa grande na memória:** o lado maior fica entre 1280 e 2559 px (até ~26 MB). Decodificar pelo tamanho da tela ou em `RGB_565` (fica sob o véu).
- **Imagem e véu redesenhados a cada quadro:** dividem a camada com a pista. `graphicsLayer` no Canvas da pista e `ColorFilter` na imagem no lugar da caixa do véu.
- **Cache de texturas:** guarda até ~6 MB durante o processo; rever se a memória apertar.

## Pontos menores das Configurações em abas e do idioma (revisão de 2026-10-16)

- **Testes:** o desvio para Android antigo do `SystemAppLanguageStore` não roda com `sdk` abaixo de 33; não há teste da aba sobrevivendo à tela refeita.
- **"Padrão do sistema":** escolhido nas configurações do celular, volta a Português na próxima abertura (o app não distingue de "nunca escolheu"). Na primeira abertura num celular em outro idioma, a primeira tela pode piscar.

## Pontos menores de "Reportar problema" (revisão de 2026-10-17)

- **API antiga:** `getPackageInfo(String, Int)` está obsoleto no Android 13+ (só aviso do compilador).
- **Revisão das categorias (2026-10-17):**
  - O script registra em Execuções o assunto do email, que traz o começo do texto do relato; registrar só tipo e categoria (minimização de dados, LGPD).
  - O logcat do app guarda o começo da resposta do servidor; melhor só o `motivo` (ou "resposta não é do script"). Uma falha que não seja de rede passa sem linha no log.
  - Permissão `script.scriptapp` no manifesto do script provavelmente sobra (o script não usa `ScriptApp`); tirar, rodar `testarEnvio` e publicar de novo.
  - Campo de categoria no leitor de tela: escolhido, lê só o nome ("Encore, lista"), sem "Categoria" nem que é obrigatório; a seta "▾" é lida; nada diz por que o Enviar está desligado.
  - O auxiliar dos testes do ViewModel escolhe uma categoria sempre (esconde a exigência); preferir parâmetro explícito.

## Pontos menores da barra de progresso (revisão de 2026-10-17)

- **Idiomas da direita para a esquerda:** a barra sempre enche da esquerda (irrelevante em pt/en).

## Ranking online (revisão final, 2026-10-17)

- Depois de o Android matar o app em segundo plano, o pedido do nome pode aparecer sobre a partida restaurada.
- Corrida estreita no status do Resultado ("Ranking: Nº" sobrescrito por "Enviando…/Sem internet").
- Posições com buracos quando um jogador é apagado da planilha; dificuldades do placar na ordem de inserção.
- O log de erro do script (do dono) pode conter um trecho da chave num JSON inválido.
- "Tempo sem errar" subconta quando notas de trilhas diferentes são acertadas fora de ordem.

## Pronto para enviar: licenças, privacidade e ranking (revisão final, 2026-10-18)

- O debug é assinado com a mesma chave do release: um APK de debug instala por cima do de um amigo e permite `run-as` nos dados dele (escolhido para o desempenho.sh trocar debug e release sem apagar dados).
- `register()` roda fora da trava do envio: ao mesmo tempo que uma nova reserva automática, pode salvar um nome local diferente do do script (raro).
- Depois de esquecer o nome (nome pego por outra pessoa), o próximo nome reenvia o histórico (o script guarda o maior; só gasta pedidos).
- Os limites por hora usam janelas fixas e o cache do Apps Script: perto da virada da hora passam até o dobro; o cache pode esquecer (melhor esforço).
- Quem foi tirado do ranking vê de novo o pedido do nome (como numa instalação nova; "Agora não" vale até reabrir o app).
- Leitura do ranking (`musicas`, `placar`) continua aberta e sem limite, e o script não confere se uma pontuação é plausível (vale para quem forjar pedidos).

## Fila de separações (revisão final, 2026-10-30)

- **`REPLACE` com estado atrasado:** trocar "Separar só carregando" no instante em que o trabalho passa de esperando para rodando cancela o que começou e começa outro (dois modelos na memória por alguns segundos). Raro.
- **Janela de "Começando…":** entre sair da fila e marcar "separando", a música ainda aparece na fila com ✕ e Excluir não a cancela (termina numa falha "não deu para separar").
- **"Esperando o carregador"** pode aparecer por instantes com o celular na tomada (o trabalho fica esperando também logo depois do pedido ou na espera de nova tentativa).
- **Painel:** "Cancelar todas" não pede confirmação; o número do ícone tem 18 dp fixos (três dígitos ou fonte grande vazam do círculo).
- **Atualização:** uma separação em andamento da versão anterior (sem fila) some na atualização (uso pessoal; aceito).
- **Limite do Android 15:** o serviço em primeiro plano de processamento de mídia tem 6 h por dia; uma fila muito longa à noite pode chegar nele (não medido).
- **Testes:** falta o caso do trabalho que acaba com a fila ainda cheia (o scheduler pede de novo) e o de uma exceção comum dentro de `runCurrent`.

## Sem o Spotify (revisão final, 2026-10-30)

- O "★ Ranking" da tela inicial é lido pelo TalkBack com o nome da estrela; dar a ele uma descrição só "Ranking".
- Dependência `lifecycle-process` sem uso no `app/build.gradle.kts`; comentários que ainda citam o Spotify em `RiffNavHost.kt`, `RiffApplication.kt`, `DifficultyDialog.kt` e `PlayerName.kt`; "⏭ na pausa" na triagem abaixo.
- O `MigrationTest` da versão 13 não preenche `chart_difficulties` nem `encore_charts` antes de migrar.

## Riff no Windows, etapa 2a: Biblioteca no PC (revisão final, 2026-11-01)

- `.sng` corrompido no PC: o arquivo fica aberto até o coletor de lixo, e o Windows não deixa apagá-lo nesse meio tempo (fechar o `RandomRead` quando `SngPackage.open` falhar; o mesmo no `ChartDownloader`).
- `FileRandomReadOpener` (PC) lê com `seek` + `read` num arquivo compartilhado: trocar pela leitura posicional do canal antes de ler faixas em paralelo (etapa do áudio).
- O marcador `#sng` pode confundir uma pasta chamada `x#sng` no PC (inofensivo: vira mapa ilegível).
- `RankingConfig` gerado sem escapar `$`, `"` e `\` da URL (as do Apps Script não têm).
- Baseline Profile anterior à Biblioteca multiplataforma (cita `R` da Biblioteca e não tem `HiltLibraryViewModel`): refazer antes da próxima release.
- Seletor de pasta do PC sem janela-mãe (pode abrir atrás do Riff), com o visual Metal do Swing e título fixo em português.
- Ao fechar a janela, o banco fecha antes das coroutines dos ViewModels (uma importação em andamento deixa exceções no log).
- Navegação do PC sem `dropUnlessResumed`: hoje um segundo clique rápido cai na tela que entra (sem efeito); quando Reportar e Configurações funcionarem no PC, proteger como no celular, com teste.
- O código só do PC (`desktopMain`) não passa por lint (o lint do alvo JVM acusa o código gerado pelo Room).

## Riff no Windows, etapa 1: base comum (revisão final, 2026-10-31)

- O placar e o "PERFEITO!" da partida remontam a fonte (`newRocker()`) a cada mudança de pontos: só alocação, sem novo layout; montar a família uma vez no tema.
- Baseline Profile anterior à base comum (cita `difficultyLabel`, não tem as classes de recursos do Compose Multiplatform): refazer com o celular ligado antes da próxima release.
- Antes do Android 13, uma mudança de configuração que não recria a tela (multi-janela) pode voltar o `Locale` padrão ao do sistema, e os textos comuns sairiam no idioma do celular até a tela ser recriada (hipótese, sem reprodução).
- O comentário do CI e o README ainda dizem que toda comparação de screenshot é exata (a do PC tem 1% de tolerância).
- O teste da janela pequena do PC usa 800×500 de conteúdo; a janela mínima real tem ~800×470 de área útil.
- `@Preview` do `:core:ui` pode não renderizar no Android Studio (saiu o `ui-tooling` de debug).

## Triagem de 2026-10-30: o que fica de propósito

Os pontos menores restantes foram revistos. Os resolvidos saíram da lista; os que ficam acima, ficam por um destes motivos. Ainda abertos, sem motivo especial (pequenos, para quando mexer perto):

- `tools:targetApi` na tag `<application>` inteira; nomes completos no `Fakes.kt` de settings.
- Área de toque das abas das Configurações perto de 47 dp (a aba "Guitarra IA" com texto curto é a mais estreita).
- Teste da música atual × limite de 3 mapas da fila.
- "Espere alguns minutos" (Reportar) continua na tela depois dos 5 minutos se nada for editado.

Os que ficam de propósito:

- **Sem efeito medido ou raros:** alocação do `MediaCodec`, soltar o dedo marcando erro um pouco antes, decodificador que trava, troca de canais no meio da faixa, saída de áudio que não abre ao continuar, corridas estreitas da fila e do ranking, imagem do mapa grande na memória e o véu redesenhado (a partida mede 120 fps com 0 a 1 quadro atrasado).
- **Fora do controle do Riff:** a API do Encore não documentada e o servidor que respeita o Range; o Android 15 e o limite de primeiro plano.
- **Exigem publicar o Apps Script de novo** (ação do usuário): o assunto do email e a chave no log de erro do script, a permissão `script.scriptapp`, leitura aberta do ranking, plausibilidade das pontuações.
- **Escolhas combinadas:** debug assinado com a chave do release, ⏭ na pausa sem confirmação, "Padrão do sistema" voltando a Português.
- **Logs da partida indisponível:** os testes do ViewModel da partida rodam sem Android; registrar no logcat exigiria trocá-los por Robolectric (custo maior que o ganho).

As pendências que eram só do modo Spotify (controles, fila de mapas, Spotify contínuo, sessão de mídia, ajuste inicial de sincronia) saíram junto com ele na 0.5.0.
