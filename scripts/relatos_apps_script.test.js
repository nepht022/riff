// Testes do scripts/relatos_apps_script.gs fora do Google: `node scripts/relatos_apps_script.test.js`.
// Roda o .gs num contexto com os serviços do Apps Script simulados e confere o email montado.
'use strict';
const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

function newContext() {
  const sent = [];
  const cache = {};
  const context = {
    console: { log() {}, warn() {}, error() {} },
    Session: { getEffectiveUser: () => ({ getEmail: () => 'dono@example.com' }) },
    MailApp: { sendEmail: (m) => sent.push(m) },
    LockService: { getScriptLock: () => ({ waitLock() {}, releaseLock() {} }) },
    CacheService: {
      getScriptCache: () => ({ get: (k) => cache[k] || null, put: (k, v) => { cache[k] = v; } }),
    },
    ContentService: {
      MimeType: { JSON: 'json' },
      createTextOutput: (text) => ({ setMimeType: () => ({ getContent: () => text }) }),
    },
  };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(__dirname + '/relatos_apps_script.gs', 'utf8'), context);
  return { context, sent };
}

function post(report) {
  const { context, sent } = newContext();
  const answer = JSON.parse(context.doPost({ postData: { contents: JSON.stringify(report) } }).getContent());
  return { answer, sent };
}

function deviceLine(body) {
  return body.split('\n').find((line) => line.startsWith('Riff '));
}

const tests = {
  'o relato do celular mostra Android e o modelo, como antes'() {
    const { answer, sent } = post({
      tipo: 'problema',
      categoria: 'Sons',
      texto: 'Travou',
      aparelho: { versaoDoApp: '0.5.0', android: '16', modelo: 'samsung SM-S931B', idioma: 'pt-BR' },
    });
    assert.strictEqual(answer.ok, true);
    assert.strictEqual(deviceLine(sent[0].body), 'Riff 0.5.0 · Android 16 · samsung SM-S931B · pt-BR');
  },
  'o relato do PC mostra o sistema, sem "Android" nem modelo vazio'() {
    const { answer, sent } = post({
      tipo: 'problema',
      categoria: 'Sons',
      texto: 'Travou',
      aparelho: { versaoDoApp: '0.5.0', sistema: 'Windows 11 10.0 (amd64)', idioma: 'pt-BR' },
    });
    assert.strictEqual(answer.ok, true);
    assert.strictEqual(deviceLine(sent[0].body), 'Riff 0.5.0 · Windows 11 10.0 (amd64) · pt-BR');
  },
  'sem informações do aparelho, nenhuma linha "Riff"'() {
    const { sent } = post({ tipo: 'outro', texto: 'Oi' });
    assert.strictEqual(deviceLine(sent[0].body), undefined);
  },
};

let failed = 0;
for (const [name, test] of Object.entries(tests)) {
  try {
    test();
    console.log('ok   ' + name);
  } catch (err) {
    failed++;
    console.log('FAIL ' + name + '\n     ' + err.message);
  }
}
if (failed) process.exit(1);
