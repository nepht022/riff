/**
 * Riff — ranking online: nomes de jogador e as melhores pontuações por mapa, dificuldade e tabela, numa planilha da
 * conta do dono deste script.
 *
 * Como publicar (uma vez, na sua conta Google):
 * 1. Abra https://script.google.com → Novo projeto, apague o conteúdo e cole este arquivo. Em Configurações do
 *    projeto, marque "Mostrar arquivo de manifesto appsscript.json" e troque o conteúdo dele pelo de
 *    scripts/ranking_appsscript.json.
 * 2. Rode "prepararPlanilha" no editor e autorize: a planilha "Riff — Ranking" aparece no seu Google Drive.
 * 3. Implantar → Nova implantação → tipo "App da Web".
 *    - Executar como: Eu
 *    - Quem pode acessar: Qualquer pessoa
 * 4. Copie o "URL do app da Web" (termina em /exec) e coloque no local.properties do Riff:
 *    ranking.url=https://script.google.com/macros/s/.../exec
 * Ao mudar este arquivo, cole a versão nova no editor e republique: Implantar → Gerenciar implantações → editar
 * (lápis) → Versão: Nova versão → Implantar. O URL continua o mesmo (não crie uma implantação nova).
 *
 * A planilha (as colunas novas ficam no fim: as linhas de antes continuam valendo sem migrar nada):
 * - Jogadores: uma linha por aparelho — nome, chaveNome (o nome em minúsculas), hash (da chave do aparelho), criado,
 *   atualizado, jogador (o id do jogador; vazio = o próprio hash, como nas linhas de antes da 4d). Vários aparelhos
 *   com o mesmo jogador = o mesmo nome em vários aparelhos (todas as linhas dele têm o mesmo nome).
 * - Pontuações: o melhor de cada número por jogador, mapa, dificuldade e tabela — hash (o id do jogador; nas linhas
 *   de antes, o hash do aparelho, que é o id), mapa, titulo, artista, dificuldade, pontos, precisao, combo, perfeitos,
 *   semErrar, atualizado, notas, tabela (celular | pc_palheta | pc_sem_palheta; vazio = celular).
 * - Removidos: hash do aparelho tirado do ranking pelo dono, e quando. Não volta sozinho.
 * O código de vínculo fica só no CacheService (10 minutos), nunca na planilha.
 *
 * Para tirar alguém do ranking (pedido pelo Reportar, nome ofensivo), rode removerJogador("Nome") no editor: apaga o
 * jogador (todos os aparelhos) e as pontuações, e os aparelhos não voltam sozinhos (aba Removidos). Apagar só a linha
 * do jogador à mão faz o aparelho reservar o mesmo nome de novo no próximo envio.
 *
 * Protocolo (POST com JSON; resposta {ok:true, ...} ou {ok:false, motivo}). "chave" é o código aleatório do aparelho
 * (UUID, ≥ 16 caracteres nas ações que gravam); só o hash dela fica na planilha.
 * - reservar {chave, nome} → {ok, nome}. Põe ou troca o nome do jogador do aparelho (em todos os aparelhos dele; o
 *   antigo fica livre). Erros: nome_invalido, nome_em_uso (de outro jogador), limite, removido, pedido.
 * - enviar {chave, mapa, titulo, artista, dificuldade, pontos, precisao, combo, perfeitos, semErrar, notas, tabela?}
 *   → {ok, posicao, total, seuNome}: posição em pontos naquele mapa, dificuldade e tabela. tabela ausente/vazia =
 *   celular; desconhecida = pedido. Erros: sem_nome, limite, removido, pedido.
 * - enviarVarios {chave, envios: [até 50 como no enviar, sem acao/chave]} → {ok, resultados: [{ok, posicao, total} |
 *   {ok:false, motivo:'pedido'}], seuNome}. Erros do pedido: lote_invalido (lista vazia, > 50 ou chave curta),
 *   sem_nome, limite, removido.
 * - seuNome: o nome atual do jogador deste aparelho (trocado em qualquer aparelho dele), para o app atualizar o nome
 *   guardado. Vem no enviar e no enviarVarios (o app 0.5.0 lê só os campos que conhece) e, só com tabelas:true, no
 *   musicas e no placar; não vem quando o aparelho não tem nome.
 * - musicas {chave, busca, tabelas?} → {ok, musicas: [{mapa, titulo, artista, jogadores, suaPosicao, tabelas?}]}.
 *   Sem tabelas:true (app 0.5.0): só a tabela celular, sem o campo tabelas. Com tabelas:true: as músicas com alguém
 *   em qualquer tabela; jogadores = jogadores distintos somando as tabelas; suaPosicao = a melhor posição sua (em
 *   pontos) em qualquer tabela e dificuldade, ou null; tabelas = {celular|pc_palheta|pc_sem_palheta: {jogadores,
 *   suaPosicao}} só com as tabelas que têm alguém naquela música; mais seuNome.
 * - placar {chave, mapa, tabelas?} → {ok, linhas: [{nome, voce, dificuldade, pontos, precisao, combo, perfeitos,
 *   semErrar, notas, tabela?}]}. Sem tabelas:true: só as linhas celular, sem o campo tabela. Com tabelas:true: todas,
 *   cada uma com a tabela, mais seuNome. voce = a linha é do seu jogador (de qualquer aparelho dele).
 * - codigo {chave} → {ok, codigo, validoAte, validoPor}: código de vínculo do jogador deste aparelho — 6 caracteres
 *   de ABCDEFGHJKMNPQRSTUVWXYZ23456789, uso único, vale validoPor = 600 segundos (validoAte em ISO 8601, UTC; use o
 *   validoPor para a contagem, o relógio do aparelho pode estar errado). Um ativo por jogador: pedir de novo (de
 *   qualquer aparelho dele) invalida o anterior. Erros: sem_nome, limite (5 por aparelho por hora), removido, pedido.
 * - vincular {chave, codigo} → {ok, nome}: este aparelho passa a ser do jogador do código (minúsculas, espaços e "-"
 *   no código são aceitos). Se o aparelho já era de outro jogador e era o único aparelho dele, esse jogador sai
 *   inteiro (o nome fica livre e as pontuações dele saem da planilha; o app reenvia o histórico local depois); se o
 *   jogador antigo tinha outros aparelhos, só este aparelho sai dele (nome e pontuações ficam com os outros).
 *   Vincular ao próprio jogador só confirma (gasta o código). Erros: codigo_invalido (errado, vencido, já usado ou de
 *   jogador removido — a mesma resposta), limite (5 tentativas por aparelho por hora, certas ou erradas; 100 códigos
 *   errados por hora somando todos os aparelhos; 100 aparelhos sem nome vinculados por hora somando todos — a recusa
 *   por limite não gasta o código), removido, pedido.
 * - Script de antes da 4d: codigo e vincular respondem pedido; placar/musicas ignoram tabelas (só existe celular).
 */

var MAX_BODY = 4000;
var MAX_PER_HOUR = 60;    // pedidos de envio por aparelho: o endereço está no APK
var MAX_BATCH = 50;       // pontuações num pedido "enviarVarios" (o histórico de quem acabou de pôr o nome)
var MAX_BODY_BATCH = 40000;
var MIN_KEY = 16;         // o app manda um UUID; chave vazia ou curta é pedido forjado
var NAMES_PER_HOUR = 5;   // reservas (e trocas) de nome por aparelho
var NAMES_PER_HOUR_ALL = 100; // nomes novos por hora somando todos: o endereço está no APK
var SCORES_PER_HOUR = 300; // pontuações por aparelho (um lote conta cada uma)
var CODES_PER_HOUR = 5;   // códigos de vínculo pedidos por aparelho
var LINKS_PER_HOUR = 5;   // tentativas de vínculo por aparelho (erradas contam): 31^6 códigos, 5 chutes por hora
var WRONG_CODES_PER_HOUR_ALL = 100; // códigos errados por hora somando todos: muitos aparelhos não somam chutes
var LINKED_PER_HOUR_ALL = 100; // aparelhos sem nome que entram num jogador por vínculo, por hora somando todos
var CODE_SECONDS = 600;
var CODE_LENGTH = 6;
var CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; // sem I, L, O, 0 e 1 (fáceis de confundir)
var NAME = /^[\p{L}\p{N} _-]{3,20}$/u;
var METRICS = ['pontos', 'precisao', 'combo', 'perfeitos', 'semErrar'];
var STORED = METRICS.concat(['notas']); // notas: o total da partida, para "perfeitos de quantos"
var PHONE = 'celular';
var TABLES = [PHONE, 'pc_palheta', 'pc_sem_palheta'];
var PLAYERS = 'Jogadores';
var SCORES = 'Pontuações';
var REMOVED = 'Removidos'; // aparelhos (hash) tirados do ranking pelo dono: não voltam sozinhos
// Colunas das abas (base 0). As novas sempre no fim, para não deslocar as linhas que já existem.
var P = { nome: 0, chaveNome: 1, hash: 2, criado: 3, atualizado: 4, jogador: 5 };
var S = { hash: 0, mapa: 1, titulo: 2, artista: 3, dificuldade: 4, pontos: 5, precisao: 6, combo: 7, perfeitos: 8,
  semErrar: 9, atualizado: 10, notas: 11, tabela: 12 };

function doPost(e) {
  try {
    if (!e || !e.postData || e.postData.contents.length > MAX_BODY_BATCH) return answer({ ok: false, motivo: 'pedido' });
    var data = JSON.parse(e.postData.contents);
    var hash = digest(String(data.chave || ''));
    var writes = ['reservar', 'enviar', 'enviarVarios', 'codigo', 'vincular'].indexOf(data.acao) >= 0;
    if (writes && String(data.chave || '').length < MIN_KEY) {
      return answer({ ok: false, motivo: data.acao === 'enviarVarios' ? 'lote_invalido' : 'pedido' });
    }
    if (data.acao !== 'enviarVarios' && e.postData.contents.length > MAX_BODY) {
      return answer({ ok: false, motivo: 'pedido' });
    }
    var all = data.tabelas === true;
    switch (data.acao) {
      case 'reservar': return answer(withLock(function () { return reserve(String(data.nome || ''), hash); }));
      case 'enviar': return answer(withLock(function () { return submit(data, hash); }));
      case 'enviarVarios': return answer(withLock(function () { return submitMany(data.envios, hash); }));
      case 'codigo': return answer(withLock(function () { return linkCode(hash); }));
      case 'vincular': return answer(withLock(function () { return link(String(data.codigo || ''), hash); }));
      case 'musicas': return answer(songs(String(data.busca || ''), hash, all));
      case 'placar': return answer(board(String(data.mapa || ''), hash, all));
      default: return answer({ ok: false, motivo: 'pedido' });
    }
  } catch (err) {
    console.error(err);
    return answer({ ok: false, motivo: 'erro interno' });
  }
}

/** Rode uma vez no editor: cria a planilha e pede as permissões. */
function prepararPlanilha() {
  book();
}

/**
 * Reserva o nome para o jogador deste aparelho; com outro nome já dele, troca em todos os aparelhos do jogador (o
 * antigo fica livre). Só contam no limite as reservas que mudam algo e os nomes em uso (procurar nomes); o limite de
 * todos só conta nomes novos.
 */
function reserve(rawName, hash) {
  var name = rawName.trim();
  if (!NAME.test(name)) return { ok: false, motivo: 'nome_invalido' };
  if (isRemoved(hash)) return { ok: false, motivo: 'removido' };
  var sheet = tab(PLAYERS);
  var rows = sheet.getDataRange().getValues(); // sem filtrar: os índices são os números das linhas
  var key = name.toLowerCase();
  var mine = deviceRow(rows, hash);
  var player = mine >= 0 ? playerIdOf(rows[mine]) : null;
  var taken = rows.some(function (row) { return String(row[P.chaveNome]) === key && playerIdOf(row) !== player; });
  if (!taken && mine >= 0 && String(rows[mine][P.nome]) === name) return { ok: true, nome: name };
  if (overLimit('nomes:' + hash, NAMES_PER_HOUR)) return { ok: false, motivo: 'limite' };
  if (taken) {
    countUse('nomes:' + hash, 1);
    return { ok: false, motivo: 'nome_em_uso' };
  }
  if (mine < 0 && overLimit('nomes', NAMES_PER_HOUR_ALL)) return { ok: false, motivo: 'limite' };
  var now = new Date().toISOString();
  if (mine < 0) {
    sheet.appendRow([safe(name), safe(key), hash, now, now, newPlayerId(rows, hash)]);
    countUse('nomes', 1);
  } else {
    rows.forEach(function (row, i) {
      if (playerIdOf(row) !== player) return;
      sheet.getRange(i + 1, P.nome + 1, 1, 2).setValues([[safe(name), safe(key)]]);
      sheet.getRange(i + 1, P.atualizado + 1, 1, 1).setValues([[now]]);
    });
  }
  countUse('nomes:' + hash, 1);
  return { ok: true, nome: name };
}

/**
 * O id de um jogador novo: o hash do aparelho (como sempre foi), a não ser que um jogador já use esse id — o aparelho
 * saiu do próprio jogador por um vínculo e a linha dele foi apagada depois; aí um id novo, para não juntar os dois.
 */
function newPlayerId(rows, hash) {
  var used = rows.some(function (row) { return playerIdOf(row) === hash; });
  return used ? 'j-' + Utilities.getUuid() : hash;
}

/** Código de vínculo do jogador deste aparelho, só no cache: um ativo por jogador, uso único, 10 minutos. */
function linkCode(hash) {
  if (isRemoved(hash)) return { ok: false, motivo: 'removido' };
  var player = playerOf(hash);
  if (!player) return { ok: false, motivo: 'sem_nome' };
  if (overLimit('codigos:' + hash, CODES_PER_HOUR)) return { ok: false, motivo: 'limite' };
  countUse('codigos:' + hash, 1);
  var cache = CacheService.getScriptCache();
  forgetCode(player);
  var code;
  do {
    code = randomCode();
  } while (cache.get('vinculo:' + code));
  var until = Date.now() + CODE_SECONDS * 1000;
  cache.put('vinculo:' + code, player, CODE_SECONDS);
  cache.put('vinculoDe:' + player, code, CODE_SECONDS);
  return { ok: true, codigo: code, validoAte: new Date(until).toISOString(), validoPor: CODE_SECONDS };
}

/** Invalida o código ativo do jogador, se houver. */
function forgetCode(player) {
  var cache = CacheService.getScriptCache();
  var old = cache.get('vinculoDe:' + player);
  if (old) cache.remove('vinculo:' + old);
  cache.remove('vinculoDe:' + player);
}

/**
 * 6 caracteres do alfabeto sem ambíguos. A fonte é o Utilities.getUuid() (UUID versão 4, do SecureRandom do Java)
 * em vez do Math.random, que não é feito para segredos: cada par de dígitos hexadecimais aleatórios é um byte
 * (0–255); bytes ≥ 248 (8 × 31) são descartados para cada caractere sair com a mesma chance.
 */
function randomCode() {
  var code = '';
  while (code.length < CODE_LENGTH) {
    // Sem os traços e sem os dígitos de versão (posição 12) e variante (16), que não são aleatórios.
    var hex = Utilities.getUuid().replace(/-/g, '');
    hex = hex.slice(0, 12) + hex.slice(13, 16) + hex.slice(17);
    for (var i = 0; i + 1 < hex.length && code.length < CODE_LENGTH; i += 2) {
      var byte = parseInt(hex.substr(i, 2), 16);
      if (byte < CODE_ALPHABET.length * 8) code += CODE_ALPHABET.charAt(byte % CODE_ALPHABET.length);
    }
  }
  return code;
}

/**
 * Este aparelho passa a ser do jogador do código. Se ele era o único aparelho de outro jogador, esse jogador sai
 * inteiro (nome livre, pontuações apagadas); se o jogador antigo tinha outros aparelhos, só este aparelho sai dele.
 */
function link(rawCode, hash) {
  if (isRemoved(hash)) return { ok: false, motivo: 'removido' };
  if (overLimit('vincular:' + hash, LINKS_PER_HOUR) || overLimit('vincularErrados', WRONG_CODES_PER_HOUR_ALL)) {
    return { ok: false, motivo: 'limite' };
  }
  var sheet = tab(PLAYERS);
  var rows = sheet.getDataRange().getValues(); // sem filtrar: os índices são os números das linhas
  var mine = deviceRow(rows, hash);
  // Antes de olhar o código: a recusa não gasta o código certo.
  if (mine < 0 && overLimit('vinculados', LINKED_PER_HOUR_ALL)) return { ok: false, motivo: 'limite' };
  countUse('vincular:' + hash, 1);
  var code = rawCode.toUpperCase().replace(/[\s-]/g, '');
  var cache = CacheService.getScriptCache();
  var target = code.length === CODE_LENGTH ? cache.get('vinculo:' + code) : null;
  if (!target) {
    countUse('vincularErrados', 1);
    return { ok: false, motivo: 'codigo_invalido' };
  }
  var targetRow = null;
  rows.forEach(function (row) { if (playerIdOf(row) === target) targetRow = row; });
  forgetCode(target); // uso único (e o jogador removido no meio do caminho também gasta)
  if (!targetRow) return { ok: false, motivo: 'codigo_invalido' };
  var name = String(targetRow[P.nome]);
  if (mine >= 0 && playerIdOf(rows[mine]) === target) return { ok: true, nome: name };
  var now = new Date().toISOString();
  var created = now;
  if (mine >= 0) {
    var old = playerIdOf(rows[mine]);
    created = rows[mine][P.criado] || now;
    var others = rows.some(function (row, i) { return i !== mine && playerIdOf(row) === old; });
    sheet.deleteRow(mine + 1);
    if (!others) {
      forgetCode(old);
      deleteScores([old]);
    }
  }
  sheet.appendRow([safe(name), safe(name.toLowerCase()), hash, created, now, target]);
  if (mine < 0) countUse('vinculados', 1);
  return { ok: true, nome: name };
}

/** Apaga as pontuações destes jogadores (de baixo para cima, para os números das linhas não mudarem); quantas. */
function deleteScores(players) {
  var scores = tab(SCORES);
  var rows = scores.getDataRange().getValues();
  var removed = 0;
  for (var j = rows.length - 1; j >= 0; j--) {
    if (players.indexOf(String(rows[j][S.hash])) >= 0) {
      scores.deleteRow(j + 1);
      removed++;
    }
  }
  return removed;
}

/** Uma pontuação: guarda o melhor de cada número e devolve a posição em pontos naquele mapa, dificuldade e tabela. */
function submit(data, hash) {
  var who = refuseSubmit(hash, 1);
  if (!who.player) return who;
  var result = storeAll([data], who.player)[0];
  if (result.ok) result.seuNome = who.name;
  return result;
}

/** Várias pontuações num pedido: um resultado por envio, na mesma ordem. */
function submitMany(list, hash) {
  // Motivo próprio: o app volta a mandar uma por vez só quando ouve "pedido" (o script antigo não conhece o lote).
  if (!Array.isArray(list) || list.length === 0 || list.length > MAX_BATCH) return { ok: false, motivo: 'lote_invalido' };
  var who = refuseSubmit(hash, list.length);
  if (!who.player) return who;
  return { ok: true, resultados: storeAll(list, who.player), seuNome: who.name };
}

/**
 * Removido, sem nome ou passou do limite (pedidos e pontuações por hora, por aparelho): devolve a recusa. Senão conta
 * o uso e devolve {player, name}: o id do jogador do aparelho e o nome atual dele.
 */
function refuseSubmit(hash, scores) {
  if (isRemoved(hash)) return { ok: false, motivo: 'removido' };
  var rows = tab(PLAYERS).getDataRange().getValues();
  var mine = deviceRow(rows, hash);
  if (mine < 0) return { ok: false, motivo: 'sem_nome' };
  var player = playerIdOf(rows[mine]);
  if (overLimit('envios:' + hash, MAX_PER_HOUR) || overLimit('pontuacoes:' + hash, SCORES_PER_HOUR, scores)) {
    return { ok: false, motivo: 'limite' };
  }
  countUse('envios:' + hash, 1);
  countUse('pontuacoes:' + hash, scores);
  return { player: player, name: String(rows[mine][P.nome]) };
}

/**
 * Lê a aba uma vez, junta o melhor de cada número na memória e grava só as linhas que mudaram. Na memória os textos
 * ficam como vieram (o apóstrofo do [safe] é só para gravar), iguais ao que a planilha devolve na leitura.
 */
function storeAll(list, player) {
  var sheet = tab(SCORES);
  var rows = sheet.getLastRow() ? sheet.getDataRange().getValues() : [];
  var changed = {};
  var results = list.map(function (data) {
    var index = merge(rows, data || {}, player);
    if (index < 0) return { ok: false, motivo: 'pedido' };
    changed[index] = true;
    return index;
  });
  var existing = sheet.getLastRow();
  Object.keys(changed).map(Number).sort(function (a, b) { return a - b; }).forEach(function (index) {
    var cells = forSheet(rows[index]);
    if (index < existing) {
      sheet.getRange(index + 1, 1, 1, cells.length).setValues([cells]);
    } else {
      sheet.appendRow(cells);
    }
  });
  return results.map(function (result) {
    if (typeof result !== 'number') return result;
    var row = rows[result];
    var ranked = rank(rows, row[S.mapa], row[S.dificuldade], tableOf(row), 'pontos', true);
    for (var j = 0; j < ranked.length; j++) {
      if (ranked[j].player === player) return { ok: true, posicao: j + 1, total: ranked.length };
    }
    return { ok: true, posicao: null, total: ranked.length };
  });
}

/** Junta a pontuação na linha do jogador (ou numa nova) e devolve o índice dela; -1 se o envio não serve. */
function merge(rows, data, player) {
  var map = text(data.mapa, 100);
  var difficulty = text(data.dificuldade, 20);
  var table = text(data.tabela, 20) || PHONE; // o app 0.5.0 não manda a tabela: é o celular
  if (!map || !difficulty || TABLES.indexOf(table) < 0) return -1;
  var index = -1;
  for (var i = 0; i < rows.length; i++) {
    if (String(rows[i][S.hash]) === player && String(rows[i][S.mapa]) === map &&
      String(rows[i][S.dificuldade]) === difficulty && tableOf(rows[i]) === table) {
      index = i;
    }
  }
  if (index < 0) {
    rows.push([player, map, '', '', difficulty, '', '', '', '', '', '', '', table]);
    index = rows.length - 1;
  }
  var row = rows[index];
  while (row.length <= S.tabela) row.push(''); // linhas de antes das colunas das notas e da tabela
  row[S.titulo] = text(data.titulo, 200);
  row[S.artista] = text(data.artista, 200);
  STORED.forEach(function (metric) { row[S[metric]] = best(row[S[metric]], data[metric]); });
  row[S.atualizado] = new Date().toISOString();
  row[S.tabela] = table;
  return index;
}

/** A linha como vai para a planilha: os textos que ela leria como fórmula ganham o apóstrofo. */
function forSheet(row) {
  var cells = row.slice();
  [S.mapa, S.titulo, S.artista, S.dificuldade].forEach(function (column) { cells[column] = safe(cells[column]); });
  return cells;
}

/**
 * Para o dono (rode no editor, ex.: removerJogador("Nome")): apaga o jogador (as linhas de todos os aparelhos dele) e
 * as pontuações, e cada aparelho fica marcado para não voltar sozinho. O nome fica livre para outra pessoa. Use
 * quando alguém pedir para sair do ranking.
 */
function removerJogador(nome) {
  return withLock(function () {
    var key = String(nome === undefined || nome === null ? '' : nome).trim().toLowerCase();
    if (!key) return 'Jogador não encontrado: ' + nome;
    var players = tab(PLAYERS);
    var rows = players.getDataRange().getValues();
    var ids = [];
    rows.forEach(function (row) {
      if (String(row[P.chaveNome]) === key && ids.indexOf(playerIdOf(row)) < 0) ids.push(playerIdOf(row));
    });
    if (!ids.length) return 'Jogador não encontrado: ' + nome;
    var devices = [];
    for (var i = rows.length - 1; i >= 0; i--) {
      if (String(rows[i][P.hash]) !== '' && ids.indexOf(playerIdOf(rows[i])) >= 0) {
        devices.push(String(rows[i][P.hash]));
        players.deleteRow(i + 1);
      }
    }
    var removed = deleteScores(ids);
    ids.forEach(forgetCode);
    var marks = tab(REMOVED);
    var now = new Date().toISOString();
    devices.forEach(function (device) { marks.appendRow([device, now]); });
    return 'Removido: ' + nome + ' (' + devices.length + ' aparelhos, ' + removed + ' pontuações)';
  });
}

function isRemoved(hash) {
  return tab(REMOVED).getDataRange().getValues().some(function (row) { return String(row[0]) === hash; });
}

/**
 * As músicas com pontuação. Sem [all] (o app 0.5.0) só conta a tabela celular; com [all], todas, mais o resumo de
 * cada tabela (jogadores e a sua posição) para o app trocar de tabela sem voltar aqui.
 */
function songs(query, hash, all) {
  var rows = filled(tab(SCORES).getDataRange().getValues(), S.mapa).filter(function (row) {
    return all || tableOf(row) === PHONE;
  });
  var players = tab(PLAYERS).getDataRange().getValues();
  var mineRow = deviceRow(players, hash);
  var player = mineRow < 0 ? null : playerIdOf(players[mineRow]);
  var bySong = {};
  rows.forEach(function (row) {
    var song = bySong[row[S.mapa]] || (bySong[row[S.mapa]] = { mapa: row[S.mapa], titulo: row[S.titulo],
      artista: row[S.artista], jogadores: {}, tabelas: {} });
    var table = tableOf(row);
    var perTable = song.tabelas[table] || (song.tabelas[table] = { jogadores: {}, dificuldades: {} });
    song.jogadores[row[S.hash]] = true;
    perTable.jogadores[row[S.hash]] = true;
    perTable.dificuldades[row[S.dificuldade]] = true;
  });
  var wanted = query.trim().toLowerCase();
  var list = Object.keys(bySong).map(function (map) {
    var song = bySong[map];
    var mine = null;
    var tables = {};
    Object.keys(song.tabelas).forEach(function (table) {
      var perTable = song.tabelas[table];
      var position = null;
      Object.keys(perTable.dificuldades).forEach(function (difficulty) {
        var ranked = rank(rows, map, difficulty, table, 'pontos', true);
        for (var i = 0; i < ranked.length; i++) {
          if (player && ranked[i].player === player && (position === null || i + 1 < position)) position = i + 1;
        }
      });
      if (position !== null && (mine === null || position < mine)) mine = position;
      tables[table] = { jogadores: Object.keys(perTable.jogadores).length, suaPosicao: position };
    });
    var item = { mapa: map, titulo: song.titulo, artista: song.artista, jogadores: Object.keys(song.jogadores).length,
      suaPosicao: mine };
    if (all) item.tabelas = tables;
    return item;
  }).filter(function (song) {
    return !wanted || (song.titulo + ' ' + song.artista).toLowerCase().indexOf(wanted) >= 0;
  });
  list.sort(function (a, b) { return b.jogadores - a.jogadores || String(a.titulo).localeCompare(String(b.titulo)); });
  var result = { ok: true, musicas: list };
  if (all && mineRow >= 0) result.seuNome = String(players[mineRow][P.nome]);
  return result;
}

/**
 * Todas as linhas de um mapa (todas as dificuldades e números; com [all], de todas as tabelas, cada uma com a sua):
 * o app ordena, troca de número e de tabela e busca nome na hora, sem voltar aqui a cada filtro.
 */
function board(map, hash, all) {
  var rows = filled(tab(SCORES).getDataRange().getValues(), S.mapa);
  var players = filled(tab(PLAYERS).getDataRange().getValues(), P.hash);
  var mine = deviceRow(players, hash);
  var player = mine >= 0 ? playerIdOf(players[mine]) : null;
  var names = {};
  players.forEach(function (row) { names[playerIdOf(row)] = String(row[P.nome]); });
  var lines = [];
  rows.forEach(function (row) {
    if (row[S.mapa] !== map) return;
    var table = tableOf(row);
    if (!all && table !== PHONE) return;
    var name = names[String(row[S.hash])];
    if (!name) return; // jogador apagado da planilha
    var line = {
      nome: name,
      voce: String(row[S.hash]) === player,
      dificuldade: row[S.dificuldade],
      pontos: number(row[S.pontos]),
      precisao: number(row[S.precisao]),
      combo: number(row[S.combo]),
      perfeitos: number(row[S.perfeitos]),
      semErrar: number(row[S.semErrar]),
      notas: number(row[S.notas]),
    };
    if (all) line.tabela = table;
    lines.push(line);
  });
  var result = { ok: true, linhas: lines };
  if (all && mine >= 0) result.seuNome = String(players[mine][P.nome]);
  return result;
}

function number(value) {
  return value === '' || value === null || value === undefined ? null : Number(value);
}

/** Quem tem o número (vazio não entra) na tabela, do melhor para o pior ([descending]) ou ao contrário. */
function rank(rows, map, difficulty, table, metric, descending) {
  var list = [];
  rows.forEach(function (row) {
    var value = row[S[metric]];
    if (row[S.mapa] === map && row[S.dificuldade] === difficulty && tableOf(row) === table && value !== '' &&
      value !== null && value !== undefined) {
      list.push({ player: String(row[S.hash]), value: Number(value) });
    }
  });
  list.sort(function (a, b) { return descending ? b.value - a.value : a.value - b.value; });
  return list;
}

/** Sem a linha vazia que o Google devolve numa aba vazia. */
function filled(rows, column) {
  return rows.filter(function (row) { return row[column] !== '' && row[column] !== undefined; });
}

function best(current, incoming) {
  if (incoming === null || incoming === undefined || incoming === '' || isNaN(Number(incoming))) return current;
  if (current === '' || current === null || current === undefined) return Number(incoming);
  return Math.max(Number(current), Number(incoming));
}

/** O id do jogador deste aparelho, ou null se o aparelho não tem nome. */
function playerOf(hash) {
  var rows = tab(PLAYERS).getDataRange().getValues();
  var i = deviceRow(rows, hash);
  return i < 0 ? null : playerIdOf(rows[i]);
}

/** A linha (índice) deste aparelho na aba Jogadores, ou -1. */
function deviceRow(rows, hash) {
  for (var i = 0; i < rows.length; i++) if (String(rows[i][P.hash]) === hash) return i;
  return -1;
}

/** O jogador da linha: a coluna jogador, ou o próprio hash do aparelho nas linhas de antes da 4d. */
function playerIdOf(row) {
  var id = row[P.jogador];
  return id === '' || id === null || id === undefined ? String(row[P.hash]) : String(id);
}

/** A tabela da linha de pontuação: vazia (as linhas de antes da 4d) é o celular. */
function tableOf(row) {
  var table = row[S.tabela];
  return table === '' || table === null || table === undefined ? PHONE : String(table);
}

/** Já chegou a [max] nesta hora contando mais [extra] (1 se omitido)? */
function overLimit(name, max, extra) {
  var used = Number(CacheService.getScriptCache().get(hourKey(name)) || 0);
  return used + (extra || 1) > max;
}

function countUse(name, amount) {
  var cache = CacheService.getScriptCache();
  var key = hourKey(name);
  cache.put(key, String(Number(cache.get(key) || 0) + amount), 3600);
}

function hourKey(name) {
  return name + ':' + Math.floor(Date.now() / 3600000);
}

/**
 * Tudo que grava (reservar, enviar, código, vínculo, remover) lê e grava a planilha ou o cache: um de cada vez, para
 * dois aparelhos não pegarem o mesmo nome nem gastarem o mesmo código.
 */
function withLock(block) {
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    return block();
  } finally {
    lock.releaseLock();
  }
}

function book() {
  var props = PropertiesService.getScriptProperties();
  var id = props.getProperty('planilha');
  if (id) return SpreadsheetApp.openById(id);
  var created = SpreadsheetApp.create('Riff — Ranking');
  props.setProperty('planilha', created.getId());
  return created;
}

/**
 * A aba, criada em formato de texto: sem ele a planilha converte sozinha ("007" vira 7, "true" vira verdadeiro) e
 * dois celulares conseguiriam o mesmo nome.
 */
function tab(name) {
  var spreadsheet = book();
  var existing = spreadsheet.getSheetByName(name);
  if (existing) return existing;
  var created = spreadsheet.insertSheet(name);
  created.getRange('A:Z').setNumberFormat('@');
  return created;
}

/** Texto que a planilha leria como fórmula (=, +, -, @) vai com apóstrofo: fica texto e o apóstrofo não aparece. */
function safe(value) {
  var v = String(value);
  return /^[=+\-@]/.test(v) ? "'" + v : v;
}

/** A chave do aparelho nunca fica na planilha: só o hash dela. */
function digest(value) {
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, value);
  return bytes.map(function (b) { return ((b + 256) % 256).toString(16).padStart(2, '0'); }).join('');
}

function text(value, max) {
  return String(value === null || value === undefined ? '' : value).slice(0, max).trim();
}

function answer(body) {
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON);
}
