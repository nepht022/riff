// Testes das regras do Realtime Database da sala (firebase/database.rules.json) no emulador do Firebase.
// Rodar com `scripts/testar_regras_sala.sh` (ver firebase/README.md). Projeto `demo-riff`: só emulador, nada real.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';

const AGORA = { '.sv': 'timestamp' };
const MINUTO = 60_000;
const HORA = 60 * MINUTO;

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-riff',
    database: { rules: readFileSync(new URL('./database.rules.json', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearDatabase();
});

const db = (uid) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).database();

function jogador(nome, { vaga = 'v1', entrouEm = AGORA, vistoEm = AGORA, aparelho = 'celular' } = {}) {
  return { nome, aparelho, vaga, entrouEm, vistoEm };
}

// Entrar como o app faz: numa só escrita, o próprio jogador e a vaga que ele ocupa.
function entrar(uid, codigo, vaga, opcoes = {}) {
  const { nome = uid, ...resto } = opcoes;
  return db(uid).ref(`salas/${codigo}`).update({
    [`jogadores/${uid}`]: jogador(nome, { vaga, ...resto }),
    [`vagas/${vaga}`]: uid,
  });
}

// A sala como o app cria: dono, estado "sala", tempos do servidor e o próprio dono como primeiro jogador.
function novaSala(dono, nome = 'Ana') {
  return {
    dono,
    estado: 'sala',
    criadaEm: AGORA,
    atividadeEm: AGORA,
    jogadores: { [dono]: jogador(nome) },
    vagas: { v1: dono },
  };
}

// Grava uma sala sem passar pelas regras (estado inicial dos testes), com tempos escolhidos pelo teste.
async function semear(codigo, { dono = 'ana', jogadores = { ana: {} }, atividadeEm = Date.now() } = {}) {
  const agora = Date.now();
  const lista = {};
  const vagas = {};
  Object.entries(jogadores).forEach(([uid, opcoes], i) => {
    const vaga = opcoes.vaga ?? `v${i + 1}`;
    vagas[vaga] = uid;
    lista[uid] = {
      nome: opcoes.nome ?? uid,
      aparelho: 'celular',
      vaga,
      entrouEm: opcoes.entrouEm ?? agora,
      vistoEm: opcoes.vistoEm ?? agora,
    };
  });
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref(`salas/${codigo}`).set({
      dono,
      estado: 'sala',
      criadaEm: atividadeEm,
      atividadeEm,
      jogadores: lista,
      vagas,
    });
  });
}

async function lerSemRegras(caminho) {
  let valor;
  await env.withSecurityRulesDisabled(async (ctx) => {
    valor = (await ctx.database().ref(caminho).once('value')).val();
  });
  return valor;
}

// --- leitura ---

test('anonymousCannotReadOutsideRooms', async () => {
  await semear('ABCD');
  await assertFails(db('ana').ref('/').once('value'));
  await assertFails(db('ana').ref('outra').once('value'));
  await assertFails(db('ana').ref('salas').once('value'));
  await assertFails(db('ana').ref('salas').orderByChild('atividadeEm').limitToFirst(5).once('value'));
  await assertFails(db('ana').ref('outra/coisa').set(1));
  await assertSucceeds(db('bia').ref('salas/ABCD').once('value'));
});

test('unauthenticatedCannotReadARoom', async () => {
  await semear('ABCD');
  await assertFails(db(null).ref('salas/ABCD').once('value'));
  await assertFails(db(null).ref('salas/ABCD').set(novaSala('ana')));
});

// --- criar ---

test('creatorBecomesOwner', async () => {
  await assertSucceeds(db('ana').ref('salas/ABCD').set(novaSala('ana')));
  const sala = await lerSemRegras('salas/ABCD');
  assert.equal(sala.dono, 'ana');
  assert.equal(sala.estado, 'sala');
  assert.equal(typeof sala.criadaEm, 'number');
  assert.equal(sala.jogadores.ana.nome, 'Ana');
});

test('cannotCreateWithSomeoneElseAsOwner', async () => {
  await assertFails(db('ana').ref('salas/ABCD').set({ ...novaSala('bia'), jogadores: { ana: jogador('Ana') } }));
  await assertFails(db('ana').ref('salas/ABCD').set(novaSala('bia')));
});

test('cannotCreateWithClientTimesOrOtherPlayers', async () => {
  const agora = Date.now();
  await assertFails(db('ana').ref('salas/ABCD').set({ ...novaSala('ana'), criadaEm: agora }));
  await assertFails(db('ana').ref('salas/ABCD').set({ ...novaSala('ana'), atividadeEm: agora }));
  await assertFails(db('ana').ref('salas/ABCD').set({ ...novaSala('ana'), estado: 'partida' }));
  await assertFails(db('ana').ref('salas/ABCD').set({ ...novaSala('ana'), jogadores: {} }));
  await assertFails(
    db('ana').ref('salas/ABCD').set({
      ...novaSala('ana'),
      jogadores: { ana: jogador('Ana'), bia: jogador('Bia', { vaga: 'v2' }) },
      vagas: { v1: 'ana', v2: 'bia' },
    }),
  );
  // Ocupando mais de uma vaga ao criar.
  await assertFails(db('ana').ref('salas/ABCD').set({ ...novaSala('ana'), vagas: { v1: 'ana', v2: 'ana' } }));
  // Sem vaga.
  await assertFails(db('ana').ref('salas/ABCD').set({ ...novaSala('ana'), vagas: null }));
});

test('cannotOverwriteAnExistingRoom', async () => {
  await semear('ABCD', { dono: 'bia', jogadores: { bia: {} } });
  await assertFails(db('ana').ref('salas/ABCD').set(novaSala('ana')));
  assert.equal((await lerSemRegras('salas/ABCD')).dono, 'bia');
});

test('codeMustBeFourAllowedLetters', async () => {
  await assertSucceeds(db('ana').ref('salas/ABCD').set(novaSala('ana')));
  for (const codigo of ['ABCO', 'ABCI', 'ABCL', 'abcd', 'ABC', 'ABCDE', 'AB1D']) {
    await assertFails(db('ana').ref(`salas/${codigo}`).set(novaSala('ana')));
  }
});

// --- jogadores ---

test('playerWritesOnlyItself', async () => {
  await semear('ABCD');
  await assertSucceeds(entrar('bia', 'ABCD', 'v2', { aparelho: 'pc' }));
  await assertFails(
    db('bia').ref('salas/ABCD').update({ 'jogadores/caio': jogador('Caio', { vaga: 'v3' }), 'vagas/v3': 'caio' }),
  );
  await assertFails(db('bia').ref('salas/ABCD/jogadores/ana/nome').set('Outro'));
  await assertFails(db('bia').ref('salas/ABCD/jogadores/ana').remove());
});

test('joinTwiceKeepsOneEntry', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: { entrouEm: 1000 } } });
  await assertSucceeds(entrar('bia', 'ABCD', 'v2', { entrouEm: 1000 }));
  const sala = await lerSemRegras('salas/ABCD');
  assert.deepEqual(Object.keys(sala.jogadores).sort(), ['ana', 'bia']);
  assert.equal(sala.jogadores.bia.entrouEm, 1000);
});

test('cannotJoinAMissingRoom', async () => {
  await assertFails(entrar('bia', 'ZZZZ', 'v1'));
  await assertFails(db('bia').ref('salas/ZZZZ/jogadores/bia').set(jogador('Bia')));
});

test('fifthPlayerIsRefused', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {}, caio: {} } });
  // Dois entrando juntos com 3 na sala: só um fica.
  const resultados = await Promise.allSettled([entrar('davi', 'ABCD', 'v4'), entrar('eva', 'ABCD', 'v4')]);
  assert.equal(resultados.filter((r) => r.status === 'fulfilled').length, 1);
  assert.equal(Object.keys((await lerSemRegras('salas/ABCD')).jogadores).length, 4);
  // O 5º: sem vaga, numa vaga ocupada, numa vaga inexistente ou fingindo a vaga de outro.
  for (const vaga of ['v1', 'v2', 'v3', 'v4', 'v5', 'v0']) {
    await assertFails(entrar('fabi', 'ABCD', vaga));
  }
  await assertFails(db('fabi').ref('salas/ABCD/jogadores/fabi').set(jogador('Fabi', { vaga: 'v1' })));
  assert.equal(Object.keys((await lerSemRegras('salas/ABCD')).jogadores).length, 4);
  // Quem já está na sala continua podendo se regravar com 4.
  await assertSucceeds(db('ana').ref('salas/ABCD/jogadores/ana/vistoEm').set(AGORA));
});

test('slotsCannotBeFreedWhileInUseButAbandonedOnesAreReused', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {}, caio: {}, davi: {} } });
  // Liberar a própria vaga sem sair (abriria espaço para um 5º) ou a vaga de outro: recusado.
  await assertFails(db('bia').ref('salas/ABCD/vagas/v2').remove());
  await assertFails(db('bia').ref('salas/ABCD/vagas/v3').remove());
  await assertFails(db('bia').ref('salas/ABCD/vagas/v3').set('bia'));
  // Sair sem liberar a vaga (ou o dono removendo alguém só em jogadores/): a vaga fica abandonada e pode ser ocupada.
  await assertSucceeds(db('caio').ref('salas/ABCD/jogadores/caio').remove());
  await assertSucceeds(entrar('eva', 'ABCD', 'v3'));
  assert.deepEqual((await lerSemRegras('salas/ABCD')).vagas, { v1: 'ana', v2: 'bia', v3: 'eva', v4: 'davi' });
  // Trocar de vaga não segura duas: a antiga fica abandonada.
  await semear('EFGH', { jogadores: { ana: {}, bia: {} } });
  await assertSucceeds(
    db('bia').ref('salas/EFGH').update({ 'jogadores/bia/vaga': 'v3', 'vagas/v3': 'bia', 'vagas/v2': null }),
  );
  assert.deepEqual((await lerSemRegras('salas/EFGH')).vagas, { v1: 'ana', v3: 'bia' });
});

test('nameLongerThan20IsRefused', async () => {
  await semear('ABCD');
  await assertSucceeds(entrar('bia', 'ABCD', 'v2', { nome: 'B'.repeat(20) }));
  await assertFails(entrar('caio', 'ABCD', 'v3', { nome: 'C'.repeat(21) }));
  await assertFails(entrar('caio', 'ABCD', 'v3', { nome: '' }));
  await assertFails(entrar('caio', 'ABCD', 'v3', { nome: 42 }));
  await assertFails(entrar('caio', 'ABCD', 'v3', { aparelho: 'tablet' }));
});

test('extraFieldsAreRefused', async () => {
  await semear('ABCD');
  await assertFails(
    db('bia').ref('salas/ABCD').update({
      'jogadores/bia': { ...jogador('Bia', { vaga: 'v2' }), pontos: 999 },
      'vagas/v2': 'bia',
    }),
  );
  await assertFails(db('ana').ref('salas/ABCD/jogadores/ana/lixo').set('x'));
  await assertFails(db('ana').ref('salas/ABCD/lixo').set('x'));
  await assertFails(db('ana').ref('salas/ABCD').set({ ...novaSala('ana'), lixo: 'x' }));
  await assertFails(db('ana').ref('salas/EFGH').set({ ...novaSala('ana'), lixo: 'x' }));
  await assertFails(
    db('ana').ref('salas/EFGH').set({ ...novaSala('ana'), jogadores: { ana: { ...jogador('Ana'), x: 1 } } }),
  );
  // Faltando campo do jogador.
  await assertFails(
    db('bia').ref('salas/ABCD').update({ 'jogadores/bia': { nome: 'Bia', aparelho: 'pc', vaga: 'v2' }, 'vagas/v2': 'bia' }),
  );
});

test('playerSeenAtMustBeServerTime', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: { vistoEm: Date.now() - 2 * MINUTO } } });
  await assertSucceeds(db('bia').ref('salas/ABCD/jogadores/bia/vistoEm').set(AGORA));
  await assertFails(db('bia').ref('salas/ABCD/jogadores/bia/vistoEm').set(Date.now() + HORA));
  await assertFails(db('bia').ref('salas/ABCD/jogadores/bia/entrouEm').set(1));
  await assertFails(entrar('caio', 'ABCD', 'v3', { entrouEm: 1 }));
});

test('activityIsTouchedOnlyByPlayersWithServerTime', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} }, atividadeEm: Date.now() - HORA });
  await assertSucceeds(db('bia').ref('salas/ABCD/atividadeEm').set(AGORA));
  await assertFails(db('bia').ref('salas/ABCD/atividadeEm').set(Date.now() + HORA));
  await assertFails(db('caio').ref('salas/ABCD/atividadeEm').set(AGORA));
  await assertFails(db('bia').ref('salas/ABCD/atividadeEm').remove());
  // Quem entra pode tocar a atividade na mesma escrita.
  await assertSucceeds(
    db('caio').ref('salas/ABCD').update({
      'jogadores/caio': jogador('Caio', { vaga: 'v3' }),
      'vagas/v3': 'caio',
      atividadeEm: AGORA,
    }),
  );
});

test('ownerRemovesPlayersAndLeavesPassingTheOwner', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {}, caio: {} } });
  await assertFails(db('bia').ref('salas/ABCD').update({ 'jogadores/caio': null, 'vagas/v3': null }));
  await assertSucceeds(db('ana').ref('salas/ABCD').update({ 'jogadores/caio': null, 'vagas/v3': null }));
  await assertFails(db('ana').ref('salas/ABCD/jogadores/bia/nome').set('Outro'));
  // Sair como dono: passa o dono para outro jogador e se remove (com a vaga), na mesma escrita.
  await assertSucceeds(
    db('ana').ref('salas/ABCD').update({ dono: 'bia', 'jogadores/ana': null, 'vagas/v1': null }),
  );
  const sala = await lerSemRegras('salas/ABCD');
  assert.equal(sala.dono, 'bia');
  assert.deepEqual(Object.keys(sala.jogadores), ['bia']);
  assert.deepEqual(sala.vagas, { v2: 'bia' });
  // Quem não é jogador da sala não vira dono.
  await assertFails(db('bia').ref('salas/ABCD/dono').set('zeca'));
  await assertFails(db('bia').ref('salas/ABCD/dono').remove());
});

// --- dono, estado ---

test('onlyOwnerChangesState', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} } });
  await assertFails(db('bia').ref('salas/ABCD/estado').set('partida'));
  await assertFails(db('bia').ref('salas/ABCD/criadaEm').set(AGORA));
  await assertFails(db('ana').ref('salas/ABCD/estado').set('qualquer'));
  await assertFails(db('ana').ref('salas/ABCD/estado').remove());
  await assertSucceeds(db('ana').ref('salas/ABCD/estado').set('sala'));
  await assertSucceeds(db('ana').ref('salas/ABCD/criadaEm').set(AGORA));
});

test('ownerCanBePassedOnlyWhenGoneFor60s', async () => {
  const agora = Date.now();
  await semear('ABCD', { jogadores: { ana: { vistoEm: agora - 30_000 }, bia: {} } });
  await assertFails(db('bia').ref('salas/ABCD/dono').set('bia'));
  await assertFails(db('caio').ref('salas/ABCD/dono').set('caio'));

  await semear('ABCD', { jogadores: { ana: { vistoEm: agora - 90_000 }, bia: {} } });
  await assertFails(db('caio').ref('salas/ABCD/dono').set('caio'));
  await assertFails(db('bia').ref('salas/ABCD/dono').set('zeca'));
  await assertSucceeds(db('bia').ref('salas/ABCD/dono').set('bia'));
  assert.equal((await lerSemRegras('salas/ABCD')).dono, 'bia');
});

test('ownerMissingFromPlayersCanBeReplaced', async () => {
  await semear('ABCD', { dono: 'ana', jogadores: { bia: {}, caio: {} } });
  await assertFails(db('zeca').ref('salas/ABCD/dono').set('zeca'));
  await assertSucceeds(db('caio').ref('salas/ABCD/dono').set('bia'));
});

// --- apagar ---

test('staleRoomCanBeDeletedByAnyone', async () => {
  await semear('ABCD', { atividadeEm: Date.now() - 3 * HORA });
  await assertFails(db(null).ref('salas/ABCD').remove());
  await assertSucceeds(db('zeca').ref('salas/ABCD').remove());
  assert.equal(await lerSemRegras('salas/ABCD'), null);
});

test('staleRoomCodeCanBeTakenOver', async () => {
  // Criar e dar com um código ocupado por uma sala morta: apaga a morta e cria a nova com o mesmo código.
  await semear('ABCD', { atividadeEm: Date.now() - 3 * HORA });
  await assertFails(db('bia').ref('salas/ABCD').set(novaSala('bia')));
  await assertSucceeds(db('bia').ref('salas/ABCD').remove());
  await assertSucceeds(db('bia').ref('salas/ABCD').set(novaSala('bia')));
  assert.equal((await lerSemRegras('salas/ABCD')).dono, 'bia');
});

test('freshRoomCanBeDeletedOnlyByOwner', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} }, atividadeEm: Date.now() - HORA });
  await assertFails(db('bia').ref('salas/ABCD').remove());
  await assertFails(db('zeca').ref('salas/ABCD').remove());
  await assertSucceeds(db('ana').ref('salas/ABCD').remove());
  assert.equal(await lerSemRegras('salas/ABCD'), null);
});

// --- as escritas do RoomRepository (core/room), exatamente como o app as faz ---

test('repositoryJoinLeaveAndHeartbeatWritesAreAccepted', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: { entrouEm: 1000 } } });
  // Batimento: vistoEm e atividadeEm numa escrita só.
  await assertSucceeds(db('bia').ref('salas/ABCD').update({ 'jogadores/bia/vistoEm': AGORA, atividadeEm: AGORA }));
  // Fora da sala o batimento é recusado (não cria um jogador pela metade); o app nem tenta.
  await assertFails(db('zeca').ref('salas/ABCD').update({ 'jogadores/zeca/vistoEm': AGORA, atividadeEm: AGORA }));
  // Entrar de novo: mesma vaga, o mesmo entrouEm, e a atividade.
  await assertSucceeds(
    db('bia').ref('salas/ABCD').update({
      'jogadores/bia': jogador('Bia', { vaga: 'v2', entrouEm: 1000 }),
      'vagas/v2': 'bia',
      atividadeEm: AGORA,
    }),
  );
  // Convidado saindo: o jogador e a vaga.
  await assertSucceeds(db('bia').ref('salas/ABCD').update({ 'jogadores/bia': null, 'vagas/v2': null }));
  const sala = await lerSemRegras('salas/ABCD');
  assert.deepEqual(Object.keys(sala.jogadores), ['ana']);
  assert.deepEqual(sala.vagas, { v1: 'ana' });
});

test('repositoryOwnerTakeoverAndLastLeaveAreAccepted', async () => {
  const agora = Date.now();
  // O dono sumiu: o jogador vivo mais antigo grava o dono num PATCH da sala.
  await semear('ABCD', { jogadores: { ana: { vistoEm: agora - 90_000 }, bia: {} } });
  await assertSucceeds(db('bia').ref('salas/ABCD').update({ dono: 'bia' }));
  // O último a sair, com o dono fora da sala: fica com o dono e apaga a sala.
  await semear('EFGH', { dono: 'ana', jogadores: { bia: {} } });
  await assertFails(db('bia').ref('salas/EFGH').remove());
  await assertSucceeds(db('bia').ref('salas/EFGH').update({ dono: 'bia' }));
  await assertSucceeds(db('bia').ref('salas/EFGH').remove());
  assert.equal(await lerSemRegras('salas/EFGH'), null);
});
