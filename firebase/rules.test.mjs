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
// `sala` acrescenta campos da sala (estado, rodada, musica...); `extra` num jogador acrescenta campos dele.
async function semear(codigo, { dono = 'ana', jogadores = { ana: {} }, atividadeEm = Date.now(), sala = {} } = {}) {
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
      ...opcoes.extra,
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
      ...sala,
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

test('repositoryOwnerRemovesVanishedPlayersIsAccepted', async () => {
  const agora = Date.now();
  // O dono, no batimento, tira de uma vez os que sumiram há mais de 5 min, com as vagas que ainda são deles.
  await semear('ABCD', {
    jogadores: { ana: {}, bia: { vistoEm: agora - 10 * MINUTO }, caio: { vistoEm: agora - 6 * MINUTO }, davi: {} },
  });
  const limpeza = { 'jogadores/bia': null, 'vagas/v2': null, 'jogadores/caio': null, 'vagas/v3': null };
  await assertFails(db('davi').ref('salas/ABCD').update(limpeza));
  await assertSucceeds(db('ana').ref('salas/ABCD').update(limpeza));
  const sala = await lerSemRegras('salas/ABCD');
  assert.deepEqual(Object.keys(sala.jogadores).sort(), ['ana', 'davi']);
  assert.deepEqual(sala.vagas, { v1: 'ana', v4: 'davi' });
  // A vaga do sumido já ocupada por outro: só o jogador sai.
  await semear('EFGH', { jogadores: { ana: {}, bia: { vistoEm: agora - 10 * MINUTO }, eva: { vaga: 'v2' } } });
  await assertSucceeds(db('ana').ref('salas/EFGH').update({ 'jogadores/bia': null }));
  assert.deepEqual((await lerSemRegras('salas/EFGH')).vagas, { v1: 'ana', v2: 'eva' });
});

// --- M2: a música, a rodada e o começo ---

// A música como o RoomRepository grava (chartId = SHA-1 das notas, em hexadecimal minúsculo).
const MUSICA = {
  chartId: '0123456789abcdef0123456789abcdef01234567',
  titulo: 'Teen Spirit',
  artista: 'Nirvana',
  dificuldades: 'EASY,MEDIUM,HARD,EXPERT',
  duracaoMs: 300_000,
  encore: { md5: 'a07d209baccd2d58042d81b869a0077e', video: false, charter: 'highfine' },
};

test('ownerChoosesTheSong', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} } });
  await assertSucceeds(db('ana').ref('salas/ABCD/musica').set(MUSICA));
  assert.deepEqual((await lerSemRegras('salas/ABCD')).musica, MUSICA);
  // Trocar de música; sem Encore e sem duração também vale.
  const { encore, duracaoMs, ...local } = MUSICA;
  await assertSucceeds(db('ana').ref('salas/ABCD/musica').set({ ...local, chartId: 'f'.repeat(40) }));
  // Tirar a música (nenhuma escolhida).
  await assertSucceeds(db('ana').ref('salas/ABCD/musica').remove());
  // Só na sala: durante a contagem ou a partida a música não muda.
  await semear('EFGH', { sala: { estado: 'contagem', rodada: 1, musica: MUSICA } });
  await assertFails(db('ana').ref('salas/EFGH/musica').set({ ...MUSICA, titulo: 'Outra' }));
});

test('guestCannotChooseTheSong', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} }, sala: { musica: MUSICA } });
  await assertFails(db('bia').ref('salas/ABCD/musica').set({ ...MUSICA, titulo: 'Outra' }));
  await assertFails(db('bia').ref('salas/ABCD/musica/titulo').set('Outra'));
  await assertFails(db('bia').ref('salas/ABCD/musica').remove());
  await assertFails(db('zeca').ref('salas/ABCD/musica').set(MUSICA));
  await assertFails(db(null).ref('salas/ABCD/musica').set(MUSICA));
});

test('songFieldsAreValidated', async () => {
  await semear('ABCD');
  const musica = db('ana').ref('salas/ABCD/musica');
  await assertSucceeds(musica.set({ ...MUSICA, titulo: 'T'.repeat(200), artista: 'A'.repeat(200), dificuldades: 'HARD' }));
  await assertSucceeds(musica.set({ ...MUSICA, encore: { md5: MUSICA.encore.md5, video: true } }));
  const ruins = [
    { ...MUSICA, chartId: 'a'.repeat(39) },
    { ...MUSICA, chartId: 'a'.repeat(41) },
    { ...MUSICA, chartId: 'A'.repeat(40) },
    { ...MUSICA, chartId: 'g'.repeat(40) },
    { ...MUSICA, chartId: 42 },
    { ...MUSICA, titulo: 'T'.repeat(201) },
    { ...MUSICA, titulo: 7 },
    { ...MUSICA, artista: 'A'.repeat(201) },
    { ...MUSICA, dificuldades: '' },
    { ...MUSICA, dificuldades: 'EASY,' },
    { ...MUSICA, dificuldades: 'easy' },
    { ...MUSICA, dificuldades: 'EASY;HARD' },
    { ...MUSICA, dificuldades: 'EASY,INSANE' },
    { ...MUSICA, dificuldades: 'EASYHARD' },
    { ...MUSICA, dificuldades: 'EASY, HARD' },
    { ...MUSICA, duracaoMs: -1 },
    { ...MUSICA, duracaoMs: '300000' },
    { ...MUSICA, encore: { ...MUSICA.encore, md5: 'a'.repeat(31) } },
    { ...MUSICA, encore: { ...MUSICA.encore, md5: 'a'.repeat(33) } },
    { ...MUSICA, encore: { ...MUSICA.encore, md5: 'z'.repeat(32) } },
    { ...MUSICA, encore: { ...MUSICA.encore, video: 'nao' } },
    { ...MUSICA, encore: { ...MUSICA.encore, charter: 'C'.repeat(201) } },
    { ...MUSICA, encore: { video: false } },
    { ...MUSICA, encore: { ...MUSICA.encore, lixo: 1 } },
    { ...MUSICA, lixo: 1 },
    { titulo: 'Sem chartId', artista: 'X', dificuldades: 'EASY' },
    { chartId: MUSICA.chartId, artista: 'X', dificuldades: 'EASY' },
    { chartId: MUSICA.chartId, titulo: 'X', artista: 'X' },
  ];
  for (const ruim of ruins) {
    await assertFails(musica.set(ruim));
  }
});

test('ownerStartsARoundByOne', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} }, sala: { musica: MUSICA } });
  const sala = db('ana').ref('salas/ABCD');
  // A primeira rodada é 1 (sem rodada antes = 0).
  await assertFails(sala.update({ estado: 'contagem', rodada: 2 }));
  await assertFails(sala.update({ estado: 'contagem', rodada: 0 }));
  await assertFails(sala.update({ estado: 'contagem', rodada: '1' }));
  // Contagem sem subir a rodada, ou a rodada sem a contagem: recusado.
  await assertFails(sala.update({ estado: 'contagem' }));
  await assertFails(sala.update({ rodada: 1 }));
  await assertSucceeds(sala.update({ estado: 'contagem', rodada: 1 }));
  // Repetir a contagem da mesma rodada (o app tentando de novo) vale; a rodada não volta nem pula.
  await assertSucceeds(sala.update({ estado: 'contagem' }));
  await assertFails(sala.update({ estado: 'contagem', rodada: 1 }));
  await assertFails(sala.update({ estado: 'contagem', rodada: 3 }));
  await assertFails(db('ana').ref('salas/ABCD/rodada').remove());
  // Volta à sala e começa a próxima.
  await assertSucceeds(sala.update({ estado: 'sala' }));
  await assertFails(sala.update({ estado: 'contagem', rodada: 3 }));
  await assertSucceeds(sala.update({ estado: 'contagem', rodada: 2 }));
  const depois = await lerSemRegras('salas/ABCD');
  assert.equal(depois.rodada, 2);
  assert.equal(depois.estado, 'contagem');
  // O convidado não começa.
  await assertSucceeds(sala.update({ estado: 'sala' }));
  await assertFails(db('bia').ref('salas/ABCD').update({ estado: 'contagem', rodada: 3 }));
  await assertFails(db('bia').ref('salas/ABCD/rodada').set(3));
});

test('inicioMustBeSoon', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} }, sala: { estado: 'contagem', rodada: 1, musica: MUSICA } });
  const sala = db('ana').ref('salas/ABCD');
  await assertFails(sala.update({ estado: 'partida', inicio: Date.now() - 5000 }));
  await assertFails(sala.update({ estado: 'partida', inicio: Date.now() + 60_000 }));
  await assertFails(sala.update({ estado: 'partida', inicio: 'logo' }));
  // A partida sempre com o inicio.
  await assertFails(sala.update({ estado: 'partida' }));
  await assertSucceeds(sala.update({ estado: 'partida', inicio: Date.now() + 3000 }));
  await assertSucceeds(sala.update({ estado: 'partida', inicio: AGORA }));
  await assertSucceeds(sala.update({ inicio: Date.now() + 15_000 }));
  // O inicio só existe na partida: na sala (ou começando da sala direto na partida) é recusado.
  await semear('EFGH', { jogadores: { ana: {}, bia: {} }, sala: { rodada: 1, musica: MUSICA } });
  await assertFails(db('ana').ref('salas/EFGH/inicio').set(Date.now() + 3000));
  await assertFails(db('ana').ref('salas/EFGH').update({ estado: 'partida', inicio: Date.now() + 3000 }));
});

test('onlyOwnerSetsInicio', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} }, sala: { estado: 'contagem', rodada: 1, musica: MUSICA } });
  await assertFails(db('bia').ref('salas/ABCD').update({ estado: 'partida', inicio: Date.now() + 3000 }));
  await assertFails(db('bia').ref('salas/ABCD/inicio').set(Date.now() + 3000));
  await assertSucceeds(db('ana').ref('salas/ABCD').update({ estado: 'partida', inicio: Date.now() + 3000 }));
  await assertFails(db('bia').ref('salas/ABCD/inicio').remove());
  // Tirar o inicio sem sair da partida, ou voltar à sala deixando o inicio: recusado.
  await assertFails(db('ana').ref('salas/ABCD/inicio').remove());
  await assertFails(db('ana').ref('salas/ABCD').update({ estado: 'sala' }));
  await assertSucceeds(db('ana').ref('salas/ABCD').update({ estado: 'sala', inicio: null }));
  const sala = await lerSemRegras('salas/ABCD');
  assert.equal(sala.estado, 'sala');
  assert.equal(sala.inicio, undefined);
});

test('playerSetsItsDifficultyReadyAndSong', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} }, sala: { musica: MUSICA } });
  const bia = db('bia').ref('salas/ABCD');
  for (const dificuldade of ['EASY', 'MEDIUM', 'HARD', 'EXPERT']) {
    await assertSucceeds(bia.update({ 'jogadores/bia/dificuldade': dificuldade }));
  }
  for (const musica of ['tem', 'baixando', 'sem']) {
    await assertSucceeds(bia.update({ 'jogadores/bia/musica': musica, 'jogadores/bia/pronto': musica === 'tem' }));
  }
  await assertSucceeds(bia.update({ 'jogadores/bia/dificuldade': null, 'jogadores/bia/pronto': false }));
  await assertFails(bia.update({ 'jogadores/bia/dificuldade': 'hard' }));
  await assertFails(bia.update({ 'jogadores/bia/dificuldade': 'INSANE' }));
  await assertFails(bia.update({ 'jogadores/bia/pronto': 'sim' }));
  await assertFails(bia.update({ 'jogadores/bia/musica': 'talvez' }));
  await assertFails(bia.update({ 'jogadores/bia/musica': true }));
  // Só o próprio jogador (nem o dono mexe no estado dos outros).
  await assertFails(bia.update({ 'jogadores/ana/pronto': true }));
  await assertFails(db('ana').ref('salas/ABCD').update({ 'jogadores/bia/pronto': true }));
  const sala = await lerSemRegras('salas/ABCD');
  assert.equal(sala.jogadores.bia.musica, 'sem');
  assert.equal(sala.jogadores.bia.pronto, false);
});

test('loadedMustBeTheCurrentRound', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} }, sala: { estado: 'contagem', rodada: 2, musica: MUSICA } });
  const carregou = db('bia').ref('salas/ABCD/jogadores/bia/carregou');
  await assertFails(carregou.set(1));
  await assertFails(carregou.set(3));
  await assertFails(carregou.set('2'));
  await assertSucceeds(carregou.set(2));
  await assertFails(db('ana').ref('salas/ABCD/jogadores/bia/carregou').set(2));
  // Sem rodada nenhuma, nada carregou.
  await semear('EFGH', { jogadores: { ana: {}, bia: {} } });
  await assertFails(db('bia').ref('salas/EFGH/jogadores/bia/carregou').set(0));
  await assertFails(db('bia').ref('salas/EFGH/jogadores/bia/carregou').set(1));
});

test('leftMustBeTheCurrentRound', async () => {
  await semear('ABCD', {
    jogadores: { ana: {}, bia: {} },
    sala: { estado: 'partida', rodada: 2, inicio: Date.now(), musica: MUSICA },
  });
  const saiu = db('bia').ref('salas/ABCD/jogadores/bia/saiu');
  await assertFails(saiu.set(1));
  await assertFails(saiu.set(3));
  await assertFails(saiu.set(true));
  await assertSucceeds(saiu.set(2));
  await assertFails(db('ana').ref('salas/ABCD/jogadores/bia/saiu').set(2));
  await semear('EFGH', { jogadores: { ana: {}, bia: {} } });
  await assertFails(db('bia').ref('salas/EFGH/jogadores/bia/saiu').set(0));
});

test('extraPlayerFieldsStillRefused', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} }, sala: { estado: 'contagem', rodada: 1, musica: MUSICA } });
  await assertFails(db('bia').ref('salas/ABCD/jogadores/bia/pontos').set(999));
  await assertFails(db('bia').ref('salas/ABCD/jogadores/bia/temMusica').set(true));
  await assertFails(db('bia').ref('salas/ABCD').update({ 'jogadores/bia/pronto': true, 'jogadores/bia/lixo': 1 }));
  await assertFails(db('ana').ref('salas/ABCD/placar').set({ 1: {} }));
  // Os campos novos não dispensam os da M1.
  await assertFails(
    db('caio').ref('salas/ABCD').update({ 'jogadores/caio': { dificuldade: 'EASY', pronto: true }, 'vagas/v3': 'caio' }),
  );
});

// --- as escritas novas do RoomRepository (Task 2 da M2), exatamente como o app as fará ---

test('repositoryChooseSongAndSetMeAreAccepted', async () => {
  await semear('ABCD', { jogadores: { ana: {}, bia: {} } });
  // chooseSong (dono): a música, a dificuldade dele, "tem" e não pronto, e a atividade.
  await assertSucceeds(
    db('ana').ref('salas/ABCD').update({
      musica: MUSICA,
      'jogadores/ana/dificuldade': 'EXPERT',
      'jogadores/ana/musica': 'tem',
      'jogadores/ana/pronto': false,
      atividadeEm: AGORA,
    }),
  );
  // setMe (qualquer jogador): dificuldade (ou null), pronto, musica e a atividade.
  await assertSucceeds(
    db('bia').ref('salas/ABCD').update({
      'jogadores/bia/dificuldade': null,
      'jogadores/bia/pronto': false,
      'jogadores/bia/musica': 'baixando',
      atividadeEm: AGORA,
    }),
  );
  await assertSucceeds(
    db('bia').ref('salas/ABCD').update({
      'jogadores/bia/dificuldade': 'HARD',
      'jogadores/bia/pronto': true,
      'jogadores/bia/musica': 'tem',
      atividadeEm: AGORA,
    }),
  );
  // O batimento da M1 continua aceito com os campos novos.
  await assertSucceeds(db('bia').ref('salas/ABCD').update({ 'jogadores/bia/vistoEm': AGORA, atividadeEm: AGORA }));
  const sala = await lerSemRegras('salas/ABCD');
  assert.equal(sala.musica.chartId, MUSICA.chartId);
  assert.equal(sala.jogadores.ana.dificuldade, 'EXPERT');
  assert.deepEqual(
    { d: sala.jogadores.bia.dificuldade, p: sala.jogadores.bia.pronto, m: sala.jogadores.bia.musica },
    { d: 'HARD', p: true, m: 'tem' },
  );
});

// Entrar de novo (RoomFormat.join): o jogador inteiro regravado com a dificuldade, o Pronto e a música de antes;
// o carregou/saiu ficam de fora (de uma rodada que pode já ter passado).
test('repositoryJoinAgainKeepsTheM2Fields', async () => {
  await semear('ABCD', {
    jogadores: { ana: {}, bia: { entrouEm: 1000, extra: { dificuldade: 'HARD', pronto: true, musica: 'tem', carregou: 1 } } },
    sala: { estado: 'partida', rodada: 1, inicio: Date.now(), musica: MUSICA },
  });
  await assertSucceeds(
    db('bia').ref('salas/ABCD').update({
      'jogadores/bia': { ...jogador('Bia', { vaga: 'v2', entrouEm: 1000 }), dificuldade: 'HARD', pronto: true, musica: 'tem' },
      'vagas/v2': 'bia',
      atividadeEm: AGORA,
    }),
  );
  const bia = (await lerSemRegras('salas/ABCD/jogadores/bia'));
  assert.deepEqual({ d: bia.dificuldade, p: bia.pronto, m: bia.musica, c: bia.carregou }, { d: 'HARD', p: true, m: 'tem', c: undefined });
});

test('repositoryRoundWritesAreAccepted', async () => {
  await semear('ABCD', {
    jogadores: { ana: {}, bia: { extra: { dificuldade: 'HARD', pronto: true, musica: 'tem' } } },
    sala: { musica: MUSICA },
  });
  const ana = db('ana').ref('salas/ABCD');
  const bia = db('bia').ref('salas/ABCD');
  // startRound (dono): contagem, rodada + 1, sem inicio, e a atividade.
  await assertFails(bia.update({ estado: 'contagem', rodada: 1, inicio: null, atividadeEm: AGORA }));
  await assertSucceeds(ana.update({ estado: 'contagem', rodada: 1, inicio: null, atividadeEm: AGORA }));
  // markLoaded (cada um): a rodada que carregou.
  await assertSucceeds(bia.update({ 'jogadores/bia/carregou': 1 }));
  await assertSucceeds(ana.update({ 'jogadores/ana/carregou': 1 }));
  // scheduleStartIfDue (dono): partida e inicio = agora do servidor + 3 s (calculado pelo relógio medido).
  await assertSucceeds(ana.update({ estado: 'partida', inicio: Date.now() + 3000, atividadeEm: AGORA }));
  // markLeft (cada um): a rodada que abandonou.
  await assertSucceeds(bia.update({ 'jogadores/bia/saiu': 1 }));
  // backToLobby (dono): sala e sem inicio.
  await assertSucceeds(ana.update({ estado: 'sala', inicio: null, atividadeEm: AGORA }));
  // A próxima rodada com o incremento do servidor também vale.
  await assertSucceeds(ana.update({ estado: 'contagem', rodada: { '.sv': { increment: 1 } }, inicio: null }));
  const sala = await lerSemRegras('salas/ABCD');
  assert.equal(sala.rodada, 2);
  assert.equal(sala.jogadores.bia.carregou, 1);
  assert.equal(sala.jogadores.bia.saiu, 1);
});

test('repositoryNewOwnerFinishesTheCountdown', async () => {
  const agora = Date.now();
  // O dono some na contagem: o herdeiro assume (PATCH só do dono, como na M1) e depois marca o começo.
  await semear('ABCD', {
    jogadores: { ana: { vistoEm: agora - 90_000 }, bia: { extra: { pronto: true, musica: 'tem', carregou: 1 } } },
    sala: { estado: 'contagem', rodada: 1, musica: MUSICA },
  });
  const bia = db('bia').ref('salas/ABCD');
  await assertFails(bia.update({ estado: 'partida', inicio: Date.now() + 3000 }));
  // Assumir e começar na mesma escrita não vale: o inicio confere o dono de antes da escrita.
  await assertFails(bia.update({ dono: 'bia', estado: 'partida', inicio: Date.now() + 3000 }));
  await assertSucceeds(bia.update({ dono: 'bia' }));
  await assertSucceeds(bia.update({ estado: 'partida', inicio: Date.now() + 3000, atividadeEm: AGORA }));
  assert.equal((await lerSemRegras('salas/ABCD')).estado, 'partida');
});

// --- as escritas novas da revisão final da M2 (RoomPresence e a rodada órfã) ---

test('repositoryClearReadyIsAccepted', async () => {
  await semear('ABCD', {
    jogadores: { ana: {}, bia: { extra: { dificuldade: 'HARD', pronto: true, musica: 'tem', carregou: 1 } } },
    sala: { estado: 'sala', rodada: 1, musica: MUSICA },
  });
  // clearReady (cada um, para si): só o pronto e a atividade.
  await assertSucceeds(db('bia').ref('salas/ABCD').update({ 'jogadores/bia/pronto': false, atividadeEm: AGORA }));
  // O de outro jogador, não.
  await assertFails(db('ana').ref('salas/ABCD').update({ 'jogadores/bia/pronto': false, atividadeEm: AGORA }));
  const bia = await lerSemRegras('salas/ABCD/jogadores/bia');
  assert.deepEqual({ p: bia.pronto, d: bia.dificuldade, m: bia.musica }, { p: false, d: 'HARD', m: 'tem' });
});

test('repositoryOrphanRoundRepairIsAccepted', async () => {
  const agora = Date.now();
  // A contagem órfã sem ninguém carregado: o dono (o novo, depois de assumir) volta à sala direto da contagem.
  await semear('ABCD', {
    jogadores: { ana: { vistoEm: agora - 90_000 }, bia: { extra: { pronto: true, musica: 'tem' } } },
    sala: { estado: 'contagem', rodada: 1, musica: MUSICA },
  });
  const bia = db('bia').ref('salas/ABCD');
  await assertSucceeds(bia.update({ dono: 'bia' }));
  await assertSucceeds(bia.update({ estado: 'sala', inicio: null, atividadeEm: AGORA }));
  // A partida órfã (o inicio bem no passado): de volta à sala.
  await semear('EFGH', {
    dono: 'bia',
    jogadores: { bia: { extra: { pronto: true, musica: 'tem', carregou: 1 } } },
    sala: { estado: 'partida', rodada: 1, inicio: agora - HORA, musica: MUSICA },
  });
  await assertSucceeds(db('bia').ref('salas/EFGH').update({ estado: 'sala', inicio: null, atividadeEm: AGORA }));
  assert.equal((await lerSemRegras('salas/ABCD')).estado, 'sala');
  assert.equal((await lerSemRegras('salas/EFGH')).inicio, undefined);
});

// A escolha com os textos no limite (o RoundFormat corta título, artista e charter em 200).
test('repositoryChooseSongAt200CharactersIsAccepted', async () => {
  await semear('ABCD', { jogadores: { ana: {} } });
  const longa = {
    ...MUSICA,
    titulo: 't'.repeat(200),
    artista: 'a'.repeat(200),
    encore: { md5: '0123456789abcdef0123456789abcdef', video: false, charter: 'c'.repeat(200) },
  };
  await assertSucceeds(db('ana').ref('salas/ABCD').update({ musica: longa, atividadeEm: AGORA }));
  await assertFails(
    db('ana').ref('salas/ABCD').update({ musica: { ...longa, encore: { ...longa.encore, charter: 'c'.repeat(201) } } }),
  );
});

// --- a API REST, como o app fala com o banco (FirebaseDb) ---

const DB_HOST = process.env.FIREBASE_DATABASE_EMULATOR_HOST ?? '127.0.0.1:9000';
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9099';

// Login anônimo no emulador do Auth, como o AnonymousAuth faz (accounts:signUp).
async function loginAnonimo() {
  const resposta = await fetch(`http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=falsa`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ returnSecureToken: true }),
  });
  assert.equal(resposta.status, 200);
  const { idToken, localId } = await resposta.json();
  return { idToken, uid: localId };
}

// O PUT de criar do RoomRepository: só se o código está livre (if-match: null_etag).
function putSeLivre(codigo, { idToken, uid }, sala = novaSala(uid)) {
  return fetch(`http://${DB_HOST}/salas/${codigo}.json?ns=demo-riff&auth=${idToken}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-Firebase-ETag': 'true', 'if-match': 'null_etag' },
    body: JSON.stringify(sala),
  });
}

// No emulador (2026-11-09), o código ocupado dá 412: o if-match é conferido antes das regras (até uma sala que as
// regras recusariam de qualquer jeito dá 412). O servidor de verdade pode conferir as regras antes: por isso o
// RoomRepository trata 401/403 nesse PUT também como "ocupado" (relê e decide).
test('restCreateOnATakenCodeAnswers412', async () => {
  const eu = await loginAnonimo();
  await semear('ABCD', { dono: 'bia', jogadores: { bia: {} } });
  const ocupado = await putSeLivre('ABCD', eu);
  console.log(`PUT if-match null_etag num código ocupado por uma sala viva: HTTP ${ocupado.status}`);
  assert.equal(ocupado.status, 412);
  const recusavel = await putSeLivre('ABCD', eu, novaSala('zeca'));
  console.log(`O mesmo PUT com uma sala que as regras recusam: HTTP ${recusavel.status}`);
  assert.equal(recusavel.status, 412);
  assert.equal((await lerSemRegras('salas/ABCD')).dono, 'bia');
  // A mesma sala recusável num código livre: as regras recusam.
  const livreRecusado = await putSeLivre('JKMN', eu, novaSala('zeca'));
  console.log(`Sala recusável num código livre: HTTP ${livreRecusado.status}`);
  assert.ok([401, 403].includes(livreRecusado.status), `HTTP ${livreRecusado.status}`);
  // Código livre: cria.
  assert.equal((await putSeLivre('EFGH', eu)).status, 200);
  assert.equal((await lerSemRegras('salas/EFGH')).dono, eu.uid);
});
