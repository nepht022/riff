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

## Riff no Windows, etapa 4d: ranking com três tabelas e o mesmo nome em vários aparelhos (2026-11-04)

- Scripts do ranking e do Reportar republicados pelo usuário em 2026-11-04. **O script do ranking mudou de novo na revisão final** (`seuNome` nas respostas, limites gerais do vínculo): falta republicar. Compatível com o app 0.5.0 (só campos novos no topo das respostas do `enviar`/`enviarVarios`, que ele ignora; no `placar`/`musicas` só com `tabelas: true`).
- App novo com o script antigo (de antes da 4d): as partidas do PC vão sem tabela reconhecida e o script as grava como Celular. Quem republicar o script depois de alguém jogar no PC fica com essas pontuações na tabela Celular.
- Teste real (PC: nome, jogar com e sem palheta, as três tabelas; celular: o ranking de hoje em "Celular", o código de vínculo e o PC usando o mesmo nome): o usuário seguiu sem relatar. O celular precisa do APK de **release** por cima (o banco vai para a v14; o de debug exigiria desinstalar e apagaria os recordes).
- Vínculo: 5 tentativas por aparelho por hora, mais 100 códigos errados e 100 aparelhos sem nome vinculados por hora somando todos; sem limite de aparelhos por jogador; código de 6 caracteres (31⁶) com validade de 10 min.
- `placar`/`musicas` com `tabelas: true` leem as Pontuações inteiras e classificam por tabela e dificuldade: com milhares de linhas pode ficar lento (cache ou índice).
- Nome trocado em outro aparelho: o app novo atualiza o nome guardado pelo `seuNome` no próximo envio, placar ou lista de músicas (até lá mostra o antigo); o celular com o app 0.5.0 (não lê o campo) guarda o nome antigo até o próximo `reservar`.
- `deleteScores` (vincular um aparelho que era o único do jogador, `removerJogador`) apaga linha a linha dentro do lock: com muitas pontuações pode demorar e segurar os outros pedidos.
- Voltar/Esc durante "Vincular" zera o `saving` com o pedido ainda em andamento: a resposta chega depois e pode fechar o diálogo (vinculado) ou mostrar o erro no passo do nome.
- Com o PC totalmente ocupado, a separação por IA (prioridade mínima) fica parada: intencional (a partida e o resto do PC vêm primeiro).
- Vincular com pontuações ainda na fila pode reenviar algumas (inofensivo: o script guarda o melhor).
- O texto de ajuda do código diz "toque em…" também no PC; o destaque de foco dos chips de tabela no PC não tem screenshot.

## Riff no Windows, etapa 4c: guitarra por IA no PC (2026-11-04)

- Teste no Windows (separar uma música de verdade, jogar errando, fechar no meio e reabrir): o usuário seguiu sem relatar o resultado.
- "Separar só carregando" escondida no PC: a spec queria mostrar num notebook; saber se há bateria no Windows exige código nativo novo.
- Textos da IA que falam de "celular" no PC: "pode esquentar o celular", "Pode não funcionar neste celular.", "Pouco espaço no celular".
- As threads nativas do ONNX Runtime ficam em prioridade normal (só a thread que chama é baixa): conferir se a partida engasga com uma separação rodando.
- O Java do instalador (4e) precisa carregar o ONNX Runtime 1.18 (msvcp140); hoje só conferido no Java do Android Studio.
- O modelo (130 MB) é lido inteiro para a heap a cada separação (o ORT 1.18 não abre de `ByteBuffer`); agora uma cópia só, direto num array do tamanho do recurso.
- A conta de threads (`processadores lógicos / 2 - 1`) supõe hyper-threading: num PC sem ele usa metade dos núcleos menos um (num de 4 núcleos, 1 thread).
- Lacunas de teste do PC: a partida não abre os `.m4a` separados nem confere que errar corta só a guitarra (o teste para no `trackLoader`); "Sem separar" na pergunta sem teste no PC; "apagar o mapa apaga a separação" sem teste no PC (depende do `ON DELETE CASCADE` no driver SQLite do PC).
- Um Riff só por pasta de dados (`riff.lock`, `FileChannel.tryLock`): o segundo mostra "O Riff já está aberto" e sai, sem trazer a janela do primeiro para a frente; nos ~2 s em que o primeiro fecha, o segundo também é recusado. Dois processos de verdade sem teste (o teste usa dois `DesktopGraph` no mesmo processo).
- O leitor do PC entrega o último bloco AAC inteiro: até 23 ms a mais no fim das faixas separadas (o começo fica alinhado).
- No diálogo de dificuldade, a primeira seta foca "Cancelar".
- `DesktopSettingsScreenshotTest` sem a aba da IA.

## Riff no Windows, etapa 4b: Reportar e Licenças no PC (2026-11-04)

- Script do Reportar corrigido (linha do sistema no PC) e republicado pelo usuário em 2026-11-04; falta ver um email de relato do PC com "Windows …".
- **Antes de distribuir o PC (4e), avisos de licença do código nativo embutido:** o FFmpeg do bytedeco (8.1.2, javacpp-presets 1.5.14) liga lame, speex, opencore-amr, vo-amrwbenc, openssl, openh264, libvpx, freetype, harfbuzz, opus, libxml2, srt, libwebp, aom, svt-av1, zimg, libmfx e traz `libwinpthread-1.dll`; o Skiko (`skiko-windows-x64.dll`) embute o Skia (BSD-3) e o ICU (`icudtl.dat`, licença Unicode), e o AboutLibraries só mostra "Apache-2.0". Nenhum desses avisos está na lista do PC. Apontar também o código-fonte exato do FFmpeg (8.1.2 e a tag 1.5.14 do javacpp-presets) no `ffmpeg-lgpl3.txt`. Validação humana recomendada (conformidade).
- O URL do `local.properties` entra cru no `ReportConfig`/`RankingConfig` gerado: um `"` ou `$` quebraria o build (URLs do Apps Script não têm).
- Testes do envio no PC que passariam com defeito: o falso não anota a thread nem suspende (o "janela segue respondendo" não prova nada); a espera única por app no PC sem teste; ↓/↑ entre o interruptor dos sons e "Licenças e privacidade" sem teste de teclado.
- `aboutlibraries.json` na raiz do classpath do PC (outro jar com o mesmo nome venceria); o PC leva textos só do celular (`onnxruntime.txt` ~335 KB, Demucs, JTransforms, JLargeArrays) até a 4c.
- Os textos de licença novos (LWJGL, GLFW, libffi, FFmpeg/LGPL/GPL) ficam no `composeResources` comum e vão também no APK do celular (~47 KB).
- `DesktopControlsFlowTest.aKeyChosenInTheSettingsPlaysTheMatch` falhou uma vez numa rodada cheia e passou sozinho: observar.
- `DeviceInfo.systemField` não acompanha um `copy(system = …)`.

## Riff no Windows, etapa 4a: pesquisa no Encore (2026-11-03)

- Teste no Windows com uma pesquisa e um download de verdade: o usuário seguiu sem relatar o resultado.
- Download interrompido no PC (fechar a pesquisa no meio, rede caindo) sem teste próprio: o downloader comum apaga o `.part`; conferir que nenhum `.sng` pela metade fica na biblioteca.
- O seletor de pasta existe repetido na Biblioteca e na pesquisa (a pesquisa abre na pasta atual); juntar em `:core:ui` com um `startAt` opcional.
- Baseline Profile do celular ainda cita `SearchViewModel_HiltModules` (regenerar com os outros).
- `.sng.part` com defeito fica aberto (`SngPackage.open` não fecha a origem ao falhar): no Windows o `.part` não é apagado até o coletor de lixo agir.
- No PC, o aviso "Escolha a mesma pasta e permita" da pesquisa não faz sentido (sem SAF); um texto só do PC ("Escolha a pasta da biblioteca"). Faltam testes do PC sem pasta e com a pasta sumida.
- Título do seletor de pasta do PC fixo em português ("Pasta dos mapas"), na pesquisa e na Biblioteca.
- `DesktopSearchKeysTest` muda o `Locale` padrão sem restaurar.
- Nomes reservados do Windows incompletos (`CONIN$`, `CONOUT$`, `COM¹²³`/`LPT¹²³`).
- Progresso do download altera a lista da pesquisa na thread de IO (risco de `ConcurrentModificationException` com "Carregar mais"; já existia no celular).
- PC: o Java não usa o proxy do Windows (`-Djava.net.useSystemProxies=true`) nem os certificados do Windows (antivírus que inspeciona HTTPS quebra a pesquisa); no instalador (4e), incluir `jdk.crypto.ec` se usar `modules(...)`.
- No teste no Windows: baixar duas vezes o mesmo título (charters diferentes) e uma pasta bem funda (caminho > 260).

## Riff no Windows, etapa 3c: navegação sem mouse (2026-11-03)

- O controle nos menus (direcional, ✕, ○) só foi testado com controle falso e uma janela de teste: vai junto do teste com o controle (3a).
- O ○ cancelando o diálogo de escolha de **tecla** não tem teste próprio.
- Com a partida carregando, o controle nos menus não está suspenso: um ✕ manda Enter à tela de carregamento (sem nada focável, sem efeito hoje).
- Os diálogos da Biblioteca (apagar, separar, dificuldade) receberam a navegação por teclado, mas nenhum teste os percorre de ponta a ponta.
- O destaque do foco não tem screenshot dentro de diálogos, abas e sliders (só botão e cartão).
- Segurar o Esc volta duas vezes (a repetição automática fecha o diálogo ou para a medição e, ~500 ms depois, volta de tela).
- No teste com o controle (3a): diálogo da dificuldade, pausa (direcional, ✕ e ○), calibração com o controle e Start seguido de ✕ logo depois de continuar — o controle nos diálogos só foi testado entregando a tecla por dentro do Compose, não pelo `focusOwner` da janela.
- `editRetrying` (gravar com nova tentativa) está no código comum: o celular também tenta de novo (inofensivo). O `WriteRetryTest` não cobre "3 falhas, o erro sobe" e não cancela o escopo que cria.
- `opened` do `MenuKeys` é uma lista global da thread da janela (uma janela só hoje).
- Pausa continuada pelo Enter do teclado sem o intervalo de 1 quadro: sem teste próprio (o RED só aparecia continuando pelo Start do controle).

## Riff no Windows, etapa 3b: controles configuráveis (2026-11-03)

- Ranking por modo (celular, PC com palheta, PC sem palheta) é da etapa 4: hoje o recorde não guarda o modo em que foi feito.
- Escolher botões do controle na aba Controles: testado só com controle falso (vai junto do teste com o controle, acima).
- Com só o controle à mão, o diálogo de escolha de botão não cancela pelo controle (Esc ou mouse); a navegação sem mouse é da 3c.
- Nomes de tecla na aba: `getKeyText` descarta a posição (Shift esquerdo/direito e Enter/Enter numérico aparecem iguais) e as setas saem em inglês ("Up"/"Down").
- A leitura das preferências aceita qualquer `Long` como tecla (sem a posição, 0 ou o Esc): a aba mostra a tecla e a partida não a reconhece; aceitar só códigos com posição 1..4 e diferentes do Esc.
- Hipótese a conferir no Windows (teclado ABNT2): Ç, ´ e ~ podem chegar como `VK_UNDEFINED` (0) e valer todas pela mesma ação; recusar o código 0 no diálogo se confirmar. Conferir também se o AWT consome o Tab antes do Compose quando ele é escolhido.
- O `PadMapper` interpreta o texto das escolhas a cada leitura (~1 ms): converter uma vez em `setBindings`.
- `remember` dentro do `?:` em `SettingsScreen.kt` (funciona porque `LocalGamepad` é estático).

## Riff no Windows, etapa 3a: o controle na partida (2026-11-03)

- **Teste com o controle de verdade, adiado pelo usuário** (sem o controle à mão): rodar `./scripts/gw :desktop:runPadCheck` (mapeado ou cru? ordem dos botões? intervalo de leitura) e jogar com o controle: cores, palheta no direcional e no analógico, Start, Alt+Tab (o controle não faz nada sem foco), controle ligado depois de abrir o jogo (o `glfwPollEvents` na thread `riff-pad` deve achá-lo) e cabo desconectado com uma cor segurada (pausa antes de soltar). Se a ordem crua do controle for outra, a 3b deixa configurar.

- No teste com o controle: medir o intervalo real da leitura **dentro do jogo** (o `runPadCheck` é outro processo, sem janela, e o `Thread.sleep(1)` no Windows pode dar ~1 ms ou ~15 ms); se passar de ~2 ms, espera híbrida (`parkNanos` + espera ativa curta). Conferir também a janela numa tela com escala de 150% (a leitura já só começa com a janela aberta).
- Leitura do controle: uma exceção inesperada na `riff-pad` encerra a leitura sem soltar as cores (capturar `Exception`, registrar e soltar no `finally`); eventos descartados sem aviso se o buffer de 64 encher; erros do GLFW sem registro no jogo (`glfwSetErrorCallback`).
- Cor do controle segurada durante o Alt+Tab precisa ser apertada de novo ao voltar (como o teclado).
- `unpluggingThePadPausesTheMatch` não prova a ordem pausa-antes-de-soltar (montar um pull-off que acertaria com a ordem trocada).
- `runPadCheck` não chama `glfwPollEvents` (não vê controle ligado durante os 20 s) e só acompanha o primeiro controle.
- Empacotamento (etapa 4): os nativos do LWJGL seguem o sistema do build (um instalador do Windows feito no CI Linux sairia sem eles) e um runtime do `jlink` precisa do `jdk.unsupported`. Base de mapeamentos do SDL fixa na do GLFW 3.4 (`glfwUpdateGamepadMappings` com um `gamecontrollerdb.txt` novo é opcional).

## Riff no Windows, etapa 2d: calibração e Configurações no PC (revisão final, 2026-11-01)

- Linux (X11): a repetição automática gera pares soltar/apertar, e segurar uma tecla na calibração conta vários toques; o alvo é o Windows (ignorar descidas da mesma tecla menos de ~30 ms depois da subida resolveria).
- Na medição do PC, toda tecla vira toque (inclusive Tab) e o Esc não sai: quem usa só o teclado espera o metrônomo acabar (alguns segundos). Etapa 3, com a navegação por teclado.
- `DesktopAppLanguageStore` grava `language.txt` na thread da tela e sem atomicidade (um corte no meio volta o jogo ao Português); gravar num temporário e renomear.
- O `key(language)` do app do PC descarta o estado lembrado das telas da pilha (rolagem etc.); hoje a pilha na troca é só Início → Configurações.
- `savingTheCalibrationAppliesTheOffset` confere só `settings.audioOffsetMs`, não a partida seguinte (a ligação é anterior e coberta pelos testes da partida).
- A escrita do `JavaSoundSink` espera para sempre por uma linha que não consome: a calibração já se protege (Task de correção da revisão); conferir a partida no PC com um fone USB desconectado no meio.

## Riff no Windows, etapa 2c-2: partida no PC (revisão final, 2026-11-01)

- No Android, as teclas novas da partida valem para qualquer teclado: o direcional de um controle Bluetooth ou do controle da TV palheta (sem nota perto, é erro e cala a guitarra), e Esc pausa/continua em vez de virar "Voltar". Rever junto com o controle (etapa 3).
- Conferir se o `when` do AWT no Windows é o instante da tecla (`GetMessageTime`) ou o do processamento (o limite de 0 a 200 ms de `KeyEvent.lateByMs()` já tem teste).
- `SingleSongPlayer.prepare` (PC): `stems` só é guardado depois do `prepare`; cancelado no meio (depois do `setUp`), a thread `riff-audio` (não-daemon) e a saída ficam órfãs. Hoje não há como sair durante o Carregando no PC.
- Fechar a janela no meio da música: `graph.close()` fecha o banco antes de a composição sair (`Main.kt`); se a música terminar nesse instante, o `scores.save` cai num banco fechado (exceção no log, sem travar). A saída depende do `exitProcessOnExit` padrão (a thread de áudio é não-daemon): vale um comentário em `Main.kt`.
- `DesktopGameplayFlowTest` depende de tempo real: palheta 15 ms antes da nota, espera de 4 ms por quadro e sincronização de 500 ms; num CI carregado pode sair da janela. Centralizar a palhetada (≈ 0 ms) e citar `windows.goodMs`.
- Baseline Profile cita a assinatura antiga de `GameplayScreen` (`baseline-prof.txt:19779`): regenerar (já anotado acima para a base comum).
- Esc não volta da tela "Não dá para jogar" no PC (só Tab+Enter ou mouse); a spec §5 pede "Esc/○ volta" — etapa 3, com a navegação por teclado do Resultado.
- Sem screenshot da janela quase quadrada (~800×790), onde cada lateral do placar tem ~175 dp.

## Riff no Windows, etapa 2c-1: pista e regras da palheta (revisão final, 2026-11-01)

- Notas longas com a palheta: palhetada errada ou troca de cor durante a nota longa não a interrompe; num acorde longo, soltar uma cor só encerra aquela (no Clone Hero o acorde inteiro cai). Decidir antes de ligar o teclado (2c-2).
- Palhetada que chega alguns ms antes da cor (troca de acorde no teclado) conta como erro; a camada de entrada da 2c-2 pode segurar a palhetada uns ms esperando a cor, e precisa ignorar a repetição automática da tecla.
- `quadToQuad` com quadrilátero degenerado (tamanho zero antes do layout, janela minimizada) dá NaN; manter a matriz anterior e deixar a função `internal`.
- Faltam testes de HOPO em acorde e de palhetada errada durante nota longa.
- No PC, a conversão da matriz para o Skia a cada quadro e a cópia do ladrilho ao trocar de visual (pequenas).

## Riff no Windows, etapa 2b: áudio no PC (revisão final, 2026-11-01)

- Arquivos temporários `riff-*` das faixas de `.sng` podem sobrar em `%TEMP%` (disco cheio ao extrair, processo encerrado no meio); limpar os antigos ao iniciar, numa pasta própria.
- Falha ao carregar as bibliotecas nativas do FFmpeg (antivírus, jar do sistema errado) vira `Error`, não `IOException`: converter no abridor.
- O FFmpeg abre os arquivos sem lista de protocolos/formatos permitidos (defesa extra para mapas baixados).
- Alocações nativas por quadro decodificado (`pointerTo`, `AVRational`); retornos nulos de alocação sem checagem.
- `JavaSoundSink`: `Thread.sleep(2)` pode dormir ~15 ms no Windows (margem curta com 42 ms de buffer); a posição da linha não desconta a latência real da saída (fica para a calibração do PC); o README traz o tamanho do buffer, não a latência medida.
- Sons do menu do PC: 5 Clips abertos na montagem do grafo e nunca fechados.
- Empacotamento: o classificador nativo vem da máquina de build (o instalador precisa ser feito no Windows); excluir os executáveis `ffmpeg`/`ffprobe` do jar; avisos de licença da LGPL do FFmpeg.
- `thePositionNeverGoesBack` não continua depois da pausa; `playMenuSound` local sombreia a função no `RiffDesktopApp`; `@Suppress` redundante no `StemSongPlayer`.

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
