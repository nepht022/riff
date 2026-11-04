/**
 * Riff — recebe os relatos da tela "Reportar problema" e manda para o email do dono deste script.
 *
 * Como publicar (uma vez, na sua conta Google):
 * 1. Abra https://script.google.com → Novo projeto, apague o conteúdo e cole este arquivo. Em Configurações do
 *    projeto, marque "Mostrar arquivo de manifesto appsscript.json" e troque o conteúdo dele pelo de
 *    scripts/relatos_appsscript.json (declara a permissão de enviar email; sem ela o Google recusa o envio).
 *    Rode "testarEnvio" no editor e autorize: deve chegar um email de teste.
 * 2. Implantar → Nova implantação → tipo "App da Web".
 *    - Executar como: Eu
 *    - Quem pode acessar: Qualquer pessoa
 * 3. Autorize (o Google pede permissão para enviar email como você).
 * 4. Copie o "URL do app da Web" (termina em /exec) e coloque no local.properties do Riff:
 *    report.url=https://script.google.com/macros/s/.../exec
 *
 * O email vai para a conta que publicou o script (nenhum endereço fica no app). O Google limita a ~100 emails por dia;
 * o script aceita até 20 relatos por hora e ignora pedidos grandes demais.
 * Ao mudar este arquivo, publique de novo em Implantar → Gerenciar implantações → editar → Nova versão.
 */

var MAX_BODY = 12000;      // o relato inteiro, em caracteres
var MAX_TEXT = 4000;
var MAX_FIELD = 200;       // contato e cada informação do aparelho
var MAX_PER_HOUR = 20;     // o endereço está no APK: segura quem tentar lotar a caixa (e a cota diária do Google)
var TYPES = { problema: 'Problema', sugestao: 'Sugestão', outro: 'Outro' };

function doPost(e) {
  try {
    if (!e || !e.postData) return refuse('sem corpo');
    if (e.postData.contents.length > MAX_BODY) return refuse('grande demais');
    if (!withinHourlyLimit()) return refuse('limite por hora');
    var data = JSON.parse(e.postData.contents);
    var text = String(data.texto || '').slice(0, MAX_TEXT).trim();
    if (!text) return refuse('texto vazio');
    var type = TYPES.hasOwnProperty(data.tipo) ? TYPES[data.tipo] : 'Outro';
    var contact = clean(data.contato);
    var category = clean(data.categoria);
    var heading = category ? type + ' · ' + category : type;
    var lines = ['Tipo: ' + heading, '', text, ''];
    if (contact) lines.push('Contato: ' + contact);
    if (data.aparelho) {
      var d = data.aparelho;
      // O PC manda "sistema" (ex.: "Windows 11 10.0 (amd64)") e não manda modelo; o celular, "android" e "modelo".
      var system = d.sistema ? clean(d.sistema) : 'Android ' + clean(d.android);
      var parts = ['Riff ' + clean(d.versaoDoApp), system];
      if (clean(d.modelo)) parts.push(clean(d.modelo));
      parts.push(clean(d.idioma));
      lines.push(parts.join(' · '));
    }
    var message = {
      to: Session.getEffectiveUser().getEmail(),
      subject: '[Riff] ' + heading + ': ' + clean(text.split('\n')[0]).slice(0, 60),
      body: lines.join('\n'), // só texto: nada do relato vira HTML
      name: 'Riff',
    };
    // Responder ao email vai direto para quem relatou, se deixou o contato.
    if (/^[^@\s<>"(),;:]+@[^@\s<>"(),;:]+\.[^@\s<>"(),;:]+$/.test(contact)) message.replyTo = contact;
    try {
      MailApp.sendEmail(message);
    } catch (err) {
      // Um contato que o Gmail recuse não pode fazer o relato se perder: manda sem o "responder para".
      console.error(err);
      delete message.replyTo;
      MailApp.sendEmail(message);
    }
    console.log('relato enviado: ' + message.subject);
    return answer(true);
  } catch (err) {
    // O detalhe fica em Execuções, no editor; quem chamou só sabe que falhou (sem configuração, cota ou o que mandou).
    console.error(err);
    return answer(false, 'erro interno');
  }
}

/** Recusa registrando o motivo (aparece em Execuções e na resposta). */
function refuse(reason) {
  console.warn('recusado: ' + reason);
  return answer(false, reason);
}

/**
 * Para testar no editor: escolha "testarEnvio" e clique em Executar. Na primeira vez o Google pede autorização
 * para enviar email; depois chega um email "[Riff] Outro: Teste..." na sua caixa.
 */
function testarEnvio() {
  var resposta = doPost({ postData: { contents: JSON.stringify({ tipo: 'outro', texto: 'Teste pelo editor do script' }) } });
  console.log(resposta.getContent());
}

/** Uma linha só, sem caracteres de controle, com tamanho máximo. */
function clean(value) {
  return String(value || '').replace(/[\u0000-\u001f\u007f\u2028\u2029]/g, ' ').trim().slice(0, MAX_FIELD);
}

/** Conta os relatos da hora atual (para todo mundo junto); acima do limite, recusa. */
function withinHourlyLimit() {
  var lock = LockService.getScriptLock();
  lock.waitLock(5000);
  try {
    var cache = CacheService.getScriptCache();
    var key = 'relatos-' + new Date().toISOString().slice(0, 13);
    var count = Number(cache.get(key) || 0);
    if (count >= MAX_PER_HOUR) return false;
    cache.put(key, String(count + 1), 3600);
    return true;
  } finally {
    lock.releaseLock();
  }
}

function answer(ok, reason) {
  var result = { ok: ok };
  if (reason) result.motivo = reason;
  return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
}
