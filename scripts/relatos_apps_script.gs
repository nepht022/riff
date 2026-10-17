/**
 * Riff — recebe os relatos da tela "Reportar problema" e manda para o email do dono deste script.
 *
 * Como publicar (uma vez, na sua conta Google):
 * 1. Abra https://script.google.com → Novo projeto, apague o conteúdo e cole este arquivo.
 * 2. Implantar → Nova implantação → tipo "App da Web".
 *    - Executar como: Eu
 *    - Quem pode acessar: Qualquer pessoa
 * 3. Autorize (o Google pede permissão para enviar email como você).
 * 4. Copie o "URL do app da Web" (termina em /exec) e coloque no local.properties do Riff:
 *    report.url=https://script.google.com/macros/s/.../exec
 *
 * O email vai para a conta que publicou o script (nenhum endereço fica no app). O Google limita a ~100 emails por dia.
 * Ao mudar este arquivo, publique de novo em Implantar → Gerenciar implantações → editar → Nova versão.
 */

var MAX_TEXT = 4000;
var TYPES = { problema: 'Problema', sugestao: 'Sugestão', outro: 'Outro' };

function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var text = String(data.texto || '').slice(0, MAX_TEXT).trim();
    if (!text) return answer(false);
    var type = TYPES[data.tipo] || 'Outro';
    var contact = String(data.contato || '').trim();
    var lines = ['Tipo: ' + type, '', text, ''];
    if (contact) lines.push('Contato: ' + contact);
    if (data.aparelho) {
      var d = data.aparelho;
      lines.push('Riff ' + d.versaoDoApp + ' · Android ' + d.android + ' · ' + d.modelo + ' · ' + d.idioma);
    }
    var message = {
      to: Session.getEffectiveUser().getEmail(),
      subject: '[Riff] ' + type + ': ' + text.split('\n')[0].slice(0, 60),
      body: lines.join('\n'),
      name: 'Riff',
    };
    // Responder ao email vai direto para quem relatou, se deixou o contato.
    if (/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(contact)) message.replyTo = contact;
    MailApp.sendEmail(message);
    return answer(true);
  } catch (err) {
    return answer(false);
  }
}

function answer(ok) {
  return ContentService.createTextOutput(JSON.stringify({ ok: ok })).setMimeType(ContentService.MimeType.JSON);
}
