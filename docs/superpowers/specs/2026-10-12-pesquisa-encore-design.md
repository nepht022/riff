# Riff: pesquisar e baixar mapas do Encore (e ler `.sng`)

Data: 2026-10-12 · Status: aprovado em conversa, aguardando revisão da especificação escrita
Complementa: `2026-10-05-riff-design.md` (biblioteca e modo Spotify) e `2026-10-08`/`2026-10-09` (guitarra separada, sincronia)

## 1. Objetivo

Hoje os mapas entram no Riff só pela pasta que o jogador escolhe, e quem baixa do Encore (enchor.us) precisa desembrulhar o `.sng` à mão. Este plano:

1. **Pesquisar no Encore de dentro do app** e baixar o mapa para a biblioteca, no modo Biblioteca e no modo Spotify.
2. **Ler `.sng` direto**, inclusive os baixados fora do app.

Decisões do usuário (2026-10-12):

- **Pacote completo:** o download guarda o `.sng` como vem do Encore, com as notas **e o áudio** (uso pessoal, sem distribuir). É uma exceção explícita, pedida pelo usuário, à regra de não propor baixar música; continua valendo nunca extrair áudio do Spotify.
- **Onde salvar:** numa subpasta **"Baixados pelo Riff"** dentro da pasta da biblioteca (visível no celular e no PC; o Reimportar acha).
- **Guardar o `.sng`** (não desembrulhar): um arquivo por música; os `.sng` baixados à mão passam a funcionar.
- **Guitarra separada** depende do pacote: se o `.sng` traz `guitar.*` e acompanhamento, vale o modo de faixas de hoje. Separar a guitarra dentro do app continua fora.
- **Fora desta etapa:** pesquisa ou download automáticos, vários downloads de uma vez, atualizar mapas já baixados.

## 2. Comportamento

### Biblioteca

- Botão **Pesquisar** ao lado de **Reimportar**; abre a tela de pesquisa com o campo vazio.
- O jogador digita e confirma (teclado ou botão); só então o app consulta o Encore.
- Cada resultado mostra: nome, artista, charter, duração, dificuldades da guitarra (Fácil a Expert presentes) e a marca **"Guitarra separada"** quando o pacote traz `guitar.*` com acompanhamento.
- Só aparecem mapas com guitarra de 5 trilhas (`diff_guitar` presente). Rips de jogos oficiais (Harmonix/Neversoft) ficam escondidos (§4.4).
- **Baixar** mostra o progresso no próprio item; ao terminar, o mapa entra na biblioteca (sem Reimportar) e o item vira **Jogar** (abre a escolha de dificuldade, como na lista).
- Resultado que já está na biblioteca (mesmo md5 do Encore, ou o arquivo já existe em "Baixados pelo Riff") aparece como **"Já na biblioteca"**, sem Baixar.
- Mais resultados: **Carregar mais** no fim da lista (página seguinte).

### Tocando agora (Spotify)

- **Sem mapa:** além de **Escolher mapa**, aparece **Pesquisar no Encore**.
- **Sugestão (75 a 92%):** **Pesquisar no Encore** ao lado de **Confirmar mapa** e **Escolher outro**.
- **Mapa encontrado:** nada muda; **Escolher outro** leva à Biblioteca no modo de escolha, que ganha também **Pesquisar**.
- A pesquisa abre preenchida com "artista título" da faixa e já pesquisa. Os resultados são ordenados pela mesma nota do casamento automático (título, artista e duração); a marca "Guitarra separada" aparece, mas não pesa.
- Ao **Baixar** um resultado vindo do Spotify, o mapa entra na biblioteca **já casado com a faixa** (casamento manual) e o app volta ao Tocando agora com "Mapa encontrado" e **Jogar**. A sincronia da primeira partida é detectada sozinha, como hoje.

### Sons do menu

Pesquisar e Carregar mais: navegar. Baixar: confirmar. Jogar: confirmar. Voltar da tela: o som de voltar de sempre (pela navegação).

## 3. Leitura do `.sng` (`:core:chart`)

Formato (SngFileFormat, mdsitton; tudo little-endian):

- Cabeçalho: `"SNGPKG"` (6 bytes), versão (`uint32`), máscara (16 bytes).
- Metadados: `uint64` tamanho, `uint64` quantidade, pares (`int32` tamanho + chave UTF-8, `int32` tamanho + valor UTF-8). São as chaves do `song.ini` (o `.sng` não traz `song.ini`).
- Índice: `uint64` tamanho, `uint64` quantidade, entradas (`byte` tamanho + nome UTF-8 com `/`, `uint64` tamanho do conteúdo, `uint64` posição do primeiro byte no arquivo).
- Dados: `uint64` tamanho e o conteúdo mascarado de cada arquivo.
- Desmascarar o byte `i` de um arquivo (i começa em 0 em cada arquivo): `byte XOR máscara[i % 16] XOR (i AND 0xFF)`.

Peças:

- **`SngPackage`** (Kotlin puro, sobre uma interface de leitura por posição `RandomRead { size; read(position, buffer, offset, length) }`): valida o cabeçalho, lê metadados e índice, e entrega `open(name): RandomRead` de cada arquivo já desmascarado. Limites de sanidade: até 64 arquivos, nomes até 255 bytes, metadados até 64 KB, e cada arquivo dentro do tamanho do pacote; fora disso, `InvalidSngException`.
- **Metadados → `SongInfo`:** as mesmas regras do `SongIniParser` (nome, artista, álbum, charter, duração, `delay`/`delay_seconds`).
- **Índice parcial:** `SngIndex.parse(bytes)` lê cabeçalho, metadados e índice a partir só do começo do arquivo (usado na marca "Guitarra separada", §4.2); se os bytes não bastam, diz quantos faltam.

## 4. Encore (`:core:encore`, módulo novo)

Fonte: o código aberto do Bridge (app de desktop do próprio Encore, GPL-3; só lido para entender as chamadas, nada copiado). A API não é documentada nem tem termos de uso públicos: pode mudar sem aviso (§6).

### 4.1 Pesquisa

- `POST https://api.enchor.us/search` com JSON `{ search, per_page: 25, page, instrument: "guitar", difficulty: null, drumType: null, drumsReviewed: false, sort: null, source: "riff" }`.
- Da resposta, por resultado: `name`, `artist`, `charter`, `song_length`, `diff_guitar`, `md5`, `hasVideoBackground`, `icon` e as dificuldades presentes em `notesData` (quando houver). Total encontrado para o **Carregar mais**.
- JSON com `org.json` (já no Android) e conexão `HttpURLConnection` (sem biblioteca nova). Tempo-limite: 10 s para conectar e 20 s para ler.

### 4.2 Marca "Guitarra separada"

- Para os resultados na tela: `GET https://files.enchor.us/<md5>[_novideo].sng` com `Range: bytes=0-65535`; o índice diz se há `guitar.*` e outra faixa de acompanhamento (as mesmas regras da importação).
- No máximo 3 pedidos ao mesmo tempo; cada md5 consultado uma vez por sessão (cache em memória). Falha ou índice maior que o pedaço: a marca fica de fora (sem erro na tela).

### 4.3 Download

- `GET https://files.enchor.us/<md5>_novideo.sng` quando `hasVideoBackground` (sem o vídeo de fundo, que o Riff não usa), senão `<md5>.sng`.
- Grava em "Baixados pelo Riff/`<Artista> - <Nome>.sng`" (caracteres inválidos trocados por `_`; nome repetido ganha ` (2)`) com o nome provisório `….sng.part`; progresso pelo `Content-Length`.
- Terminou: lê o pacote com o `SngPackage` (cabeçalho, índice e um mapa legível); só então renomeia para `.sng` e importa **só esse arquivo** (sem varrer a pasta toda). Qualquer falha apaga o `.part`.
- O md5 do Encore fica guardado no mapa importado (coluna nova `encoreMd5` na tabela de mapas, migração automática 3 → 4) para o "Já na biblioteca".

### 4.4 Filtro dos jogos oficiais

- Lista própria de ícones de setlist dos jogos oficiais (Guitar Hero, Rock Band e afins), montada a partir de respostas reais da API e guardada em `:core:encore`; resultados com esses ícones não aparecem.
- Se o filtro errar (um rip passar), o jogador não baixa; a lista é atualizada no código.

## 5. Importação, áudio e permissões

- **Importação (`:core:data`):** a varredura reconhece arquivos `.sng` além das subpastas. Cada `.sng` vira um mapa com as regras de hoje (dificuldades, guitarra separada, repetidos por SHA-1 das notas, relatório de falhas: `.sng` inválido é "mapa inválido").
- **Referência de áudio dentro do pacote:** texto `sng:<uri do .sng>!<nome do arquivo>`; a de arquivo comum continua a URI.
- **Tocar de dentro do pacote (`:core:audio`):** uma `MediaDataSource` que lê o trecho do arquivo no `.sng` (pelo `SngPackage`, desmascarando) alimenta o `MediaPlayer` (um arquivo só) e o `MediaExtractor` (faixas separadas). Reposicionar (voltar 0,5 s) funciona por leitura em qualquer posição.
- **Permissão de escrita:** a pasta passa a ser guardada com leitura **e escrita**. Quem escolheu a pasta antes só tem leitura: na primeira pesquisa, o app explica ("Para baixar, o Riff precisa gravar na pasta da biblioteca") e abre o seletor já na pasta atual; sem a escrita, a pesquisa mostra os resultados, mas Baixar fica indisponível com essa explicação.
- **Manifesto:** permissão `INTERNET`.

## 6. Erros

| Situação | O que acontece |
|---|---|
| Sem internet, tempo esgotado, Encore fora do ar (5xx) | "Não deu para falar com o Encore." e **Tentar de novo** |
| Resposta fora do formato esperado (a API mudou) | "A pesquisa do Encore mudou; o Riff precisa ser atualizado." (registrado no logcat) |
| Nenhum resultado | "Nada encontrado para "…"." |
| Download interrompido, sem espaço, acesso à pasta perdido | Mensagem com o motivo; o `.part` é apagado; o item volta a **Baixar** |
| Pacote baixado inválido | "O arquivo baixado veio com defeito." e o `.part` é apagado |
| Sair da tela durante o download | O download é cancelado e o `.part` apagado |

## 7. Arquitetura

- **`:core:chart`:** `SngPackage`, `SngIndex`, `RandomRead`, metadados → `SongInfo`.
- **`:core:encore` (novo):** `EncoreClient` (pesquisa, começo do arquivo, download por um `HttpTransport` injetável), `EncoreResult`, filtro dos oficiais.
- **`:core:data`:** importação de `.sng`, `ChartDownloader` (grava o `.part`, valida, renomeia, importa um arquivo e casa com a faixa do Spotify quando pedido), coluna `encoreMd5`, escrita na pasta.
- **`:core:audio`:** `SngMediaDataSource` e a abertura de áudio por referência `sng:`.
- **`:feature:search` (novo):** `SearchRoute(query?, matchTrackUri?, matchTrackTitle?)`, `SearchViewModel`, `SearchScreen`.
- **`:feature:library` e `:feature:nowplaying`:** os botões **Pesquisar** / **Pesquisar no Encore**; navegação no `RiffNavHost`.

## 8. Testes

- **`SngPackage`/`SngIndex`:** pacotes montados no teste (máscara conhecida): desmascarar, metadados, índice, leitura em qualquer posição, cabeçalho errado, arquivo truncado, limites de sanidade, índice parcial.
- **Importação:** pasta com `.sng` e subpastas juntos; `.sng` com faixas separadas; `.sng` inválido no relatório.
- **Áudio:** `SngMediaDataSource` entrega os bytes desmascarados nas posições pedidas e o tamanho certo.
- **`EncoreClient`:** respostas de exemplo guardadas (pesquisa real, vazia, formato mudado), transporte falso para falhas, `Range` e download interrompido; filtro dos oficiais; montagem da URL com e sem vídeo.
- **`ChartDownloader`:** `.part` → validação → `.sng` → importado; falha apaga o `.part`; nome repetido; casamento com a faixa do Spotify.
- **`SearchViewModel`/tela:** pesquisar, carregar mais, baixar com progresso, "Já na biblioteca", sem escrita na pasta, erros; vindo do Spotify (pré-preenchido, ordenação, volta casado).
- **No aparelho:** pesquisar "smells like teen spirit", baixar, jogar na Biblioteca e no Spotify; um `.sng` baixado à mão na pasta.
- **README:** seção "Pesquisar e baixar mapas", `.sng` como formato lido, crédito ao Encore (enchor.us) e aviso de que a API não é oficial.
