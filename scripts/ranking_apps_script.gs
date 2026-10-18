/**
 * Riff — ranking online: nomes de jogador e as melhores pontuações por mapa e dificuldade, numa planilha da conta
 * do dono deste script.
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
 *
 * A planilha tem duas abas: Jogadores (nome e o hash da chave do celular dono dele) e Pontuações (o melhor de cada
 * número por jogador, mapa e dificuldade). Para liberar um nome ou tirar uma pontuação, apague a linha.
 * Ao mudar este arquivo, publique de novo em Implantar → Gerenciar implantações → editar → Nova versão.
 */

var MAX_BODY = 4000;
var MAX_PER_HOUR = 60;    // pedidos de envio por celular: o endereço está no APK
var MAX_BATCH = 50;       // pontuações num pedido "enviarVarios" (o histórico de quem acabou de pôr o nome)
var MAX_BODY_BATCH = 40000;
var MIN_KEY = 16;         // o app manda um UUID; chave vazia ou curta é pedido forjado
var NAMES_PER_HOUR = 5;   // reservas (e trocas) de nome por celular
var NAMES_PER_HOUR_ALL = 100; // reservas por hora somando todos: o endereço está no APK
var NAME = /^[\p{L}\p{N} _-]{3,20}$/u;
var METRICS = ['pontos', 'precisao', 'combo', 'perfeitos', 'semErrar'];
var STORED = METRICS.concat(['notas']); // notas: o total da partida, para "perfeitos de quantos"
var PLAYERS = 'Jogadores';
var SCORES = 'Pontuações';
// Colunas das abas (base 0).
var P = { nome: 0, chaveNome: 1, hash: 2, criado: 3, atualizado: 4 };
var S = { hash: 0, mapa: 1, titulo: 2, artista: 3, dificuldade: 4, pontos: 5, precisao: 6, combo: 7, perfeitos: 8,
  semErrar: 9, atualizado: 10, notas: 11 };

function doPost(e) {
  try {
    if (!e || !e.postData || e.postData.contents.length > MAX_BODY_BATCH) return answer({ ok: false, motivo: 'pedido' });
    var data = JSON.parse(e.postData.contents);
    var hash = digest(String(data.chave || ''));
    var writes = data.acao === 'reservar' || data.acao === 'enviar' || data.acao === 'enviarVarios';
    if (writes && String(data.chave || '').length < MIN_KEY) return answer({ ok: false, motivo: 'pedido' });
    if (data.acao !== 'enviarVarios' && e.postData.contents.length > MAX_BODY) {
      return answer({ ok: false, motivo: 'pedido' });
    }
    switch (data.acao) {
      case 'reservar': return answer(withLock(function () { return reserve(String(data.nome || ''), hash); }));
      case 'enviar': return answer(withLock(function () { return submit(data, hash); }));
      case 'enviarVarios': return answer(withLock(function () { return submitMany(data.envios, hash); }));
      case 'musicas': return answer(songs(String(data.busca || ''), hash));
      case 'placar': return answer(board(String(data.mapa || ''), hash));
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

/** Reserva o nome para este celular; com outro nome já dele, troca (o antigo fica livre). */
function reserve(rawName, hash) {
  var name = rawName.trim();
  if (!NAME.test(name)) return { ok: false, motivo: 'nome_invalido' };
  if (!withinLimit('nomes:' + hash, NAMES_PER_HOUR) || !withinLimit('nomes', NAMES_PER_HOUR_ALL)) {
    return { ok: false, motivo: 'limite' };
  }
  var sheet = tab(PLAYERS);
  var rows = sheet.getDataRange().getValues();
  var key = name.toLowerCase();
  var mine = -1;
  for (var i = 0; i < rows.length; i++) {
    var owner = String(rows[i][P.hash]);
    if (String(rows[i][P.chaveNome]) === key && owner !== hash) return { ok: false, motivo: 'nome_em_uso' };
    if (owner === hash) mine = i;
  }
  var now = new Date().toISOString();
  if (mine < 0) {
    sheet.appendRow([safe(name), safe(key), hash, now, now]);
  } else {
    sheet.getRange(mine + 1, P.nome + 1, 1, 2).setValues([[safe(name), safe(key)]]);
    sheet.getRange(mine + 1, P.atualizado + 1, 1, 1).setValues([[now]]);
  }
  return { ok: true, nome: name };
}

/** Guarda o melhor de cada número; devolve a posição em pontos naquele mapa e dificuldade. */
function submit(data, hash) {
  if (!playerOf(hash)) return { ok: false, motivo: 'sem_nome' };
  if (!withinLimit('envios:' + hash, MAX_PER_HOUR)) return { ok: false, motivo: 'limite' };
  return store(data, hash);
}

/** Várias pontuações num pedido (conta uma vez no limite): um resultado por envio, na mesma ordem. */
function submitMany(list, hash) {
  if (!Array.isArray(list) || list.length === 0 || list.length > MAX_BATCH) return { ok: false, motivo: 'pedido' };
  if (!playerOf(hash)) return { ok: false, motivo: 'sem_nome' };
  if (!withinLimit('envios:' + hash, MAX_PER_HOUR)) return { ok: false, motivo: 'limite' };
  return { ok: true, resultados: list.map(function (data) { return store(data || {}, hash); }) };
}

function store(data, hash) {
  var map = text(data.mapa, 100);
  var difficulty = text(data.dificuldade, 20);
  if (!map || !difficulty) return { ok: false, motivo: 'pedido' };
  var sheet = tab(SCORES);
  var rows = sheet.getDataRange().getValues();
  var index = -1;
  for (var i = 0; i < rows.length; i++) {
    if (rows[i][S.hash] === hash && rows[i][S.mapa] === map && rows[i][S.dificuldade] === difficulty) index = i;
  }
  var row = index >= 0 ? rows[index].slice() : [hash, map, '', '', difficulty, '', '', '', '', '', '', ''];
  while (row.length <= S.notas) row.push(''); // linhas de antes da coluna das notas
  row[S.mapa] = safe(map);
  row[S.dificuldade] = safe(difficulty);
  row[S.titulo] = safe(text(data.titulo, 200));
  row[S.artista] = safe(text(data.artista, 200));
  STORED.forEach(function (metric) { row[S[metric]] = best(row[S[metric]], data[metric]); });
  row[S.atualizado] = new Date().toISOString();
  if (index >= 0) {
    sheet.getRange(index + 1, 1, 1, row.length).setValues([row]);
  } else {
    sheet.appendRow(row);
  }
  rows = sheet.getDataRange().getValues(); // como a planilha guardou
  var ranked = rank(rows, map, difficulty, 'pontos', true);
  for (var j = 0; j < ranked.length; j++) {
    if (ranked[j].hash === hash) return { ok: true, posicao: j + 1, total: ranked.length };
  }
  return { ok: true, posicao: null, total: ranked.length };
}

function songs(query, hash) {
  var rows = filled(tab(SCORES).getDataRange().getValues(), S.mapa);
  var bySong = {};
  rows.forEach(function (row) {
    var song = bySong[row[S.mapa]] || (bySong[row[S.mapa]] = { mapa: row[S.mapa], titulo: row[S.titulo],
      artista: row[S.artista], jogadores: {}, dificuldades: {} });
    song.jogadores[row[S.hash]] = true;
    song.dificuldades[row[S.dificuldade]] = true;
  });
  var wanted = query.trim().toLowerCase();
  var list = Object.keys(bySong).map(function (map) {
    var song = bySong[map];
    var mine = null;
    Object.keys(song.dificuldades).forEach(function (difficulty) {
      var ranked = rank(rows, map, difficulty, 'pontos', true);
      for (var i = 0; i < ranked.length; i++) {
        if (ranked[i].hash === hash && (mine === null || i + 1 < mine)) mine = i + 1;
      }
    });
    return { mapa: map, titulo: song.titulo, artista: song.artista, jogadores: Object.keys(song.jogadores).length,
      suaPosicao: mine };
  }).filter(function (song) {
    return !wanted || (song.titulo + ' ' + song.artista).toLowerCase().indexOf(wanted) >= 0;
  });
  list.sort(function (a, b) { return b.jogadores - a.jogadores || String(a.titulo).localeCompare(String(b.titulo)); });
  return { ok: true, musicas: list };
}

/**
 * Todas as linhas de um mapa (todas as dificuldades e números): o celular ordena, troca de número e busca nome na
 * hora, sem voltar aqui a cada filtro.
 */
function board(map, hash) {
  var rows = filled(tab(SCORES).getDataRange().getValues(), S.mapa);
  var names = playerNames();
  var lines = [];
  rows.forEach(function (row) {
    if (row[S.mapa] !== map) return;
    var name = names[row[S.hash]];
    if (!name) return; // jogador apagado da planilha
    lines.push({
      nome: name,
      voce: row[S.hash] === hash,
      dificuldade: row[S.dificuldade],
      pontos: number(row[S.pontos]),
      precisao: number(row[S.precisao]),
      combo: number(row[S.combo]),
      perfeitos: number(row[S.perfeitos]),
      semErrar: number(row[S.semErrar]),
      notas: number(row[S.notas]),
    });
  });
  return { ok: true, linhas: lines };
}

function number(value) {
  return value === '' || value === null || value === undefined ? null : Number(value);
}

/** Quem tem o número (vazio não entra), do melhor para o pior ([descending]) ou ao contrário. */
function rank(rows, map, difficulty, metric, descending) {
  var list = [];
  rows.forEach(function (row) {
    var value = row[S[metric]];
    if (row[S.mapa] === map && row[S.dificuldade] === difficulty && value !== '' && value !== null) {
      list.push({ hash: row[S.hash], value: Number(value) });
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

function playerOf(hash) {
  var rows = tab(PLAYERS).getDataRange().getValues();
  for (var i = 0; i < rows.length; i++) if (rows[i][P.hash] === hash) return rows[i];
  return null;
}

function playerNames() {
  var names = {};
  tab(PLAYERS).getDataRange().getValues().forEach(function (row) { names[row[P.hash]] = String(row[P.nome]); });
  return names;
}

/** Conta [name] nesta hora; falso quando já chegou a [max]. */
function withinLimit(name, max) {
  var cache = CacheService.getScriptCache();
  var key = name + ':' + Math.floor(Date.now() / 3600000);
  var count = Number(cache.get(key) || 0);
  if (count >= max) return false;
  cache.put(key, String(count + 1), 3600);
  return true;
}

/** Reservar e enviar leem e gravam a planilha: um de cada vez, para dois celulares não pegarem o mesmo nome. */
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

/** A chave do celular nunca fica na planilha: só o hash dela. */
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
