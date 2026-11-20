(() => {
  'use strict';
  const words = {
    safety: ['Действия с пользователем', 'User actions', 'Пайдаланушы әрекеттері'],
    more: ['Ещё', 'More', 'Тағы'],
    block: ['Заблокировать', 'Block', 'Бұғаттау'],
    unblock: ['Разблокировать', 'Unblock', 'Бұғаттан шығару'],
    blocked: ['Пользователь заблокирован', 'User blocked', 'Пайдаланушы бұғатталды'],
    blockHint: ['Вы не сможете писать друг другу лично или отправлять заявки в друзья. Текущая дружба или заявка будет удалена.', 'This stops direct messages and friend requests between you. Any existing friendship or request will be removed.', 'Бір-біріңізге жеке хабарлама немесе достық өтінімін жібере алмайсыздар. Ағымдағы достық немесе өтінім жойылады.'],
    report: ['Пожаловаться', 'Report', 'Шағымдану'],
    reportHint: ['Укажите причину и при необходимости опишите, что произошло.', 'Choose a reason and describe what happened if needed.', 'Себепті таңдаңыз және қажет болса, не болғанын сипаттаңыз.'],
    reportSent: ['Жалоба отправлена', 'Report submitted', 'Шағым жіберілді'],
    reason: ['Причина', 'Reason', 'Себеп'],
    details: ['Описание (необязательно)', 'Details (optional)', 'Сипаттама (міндетті емес)'],
    selectReason: ['Выберите причину', 'Select a reason', 'Себепті таңдаңыз'],
    SPAM: ['Спам', 'Spam', 'Спам'],
    HARASSMENT: ['Домогательства или травля', 'Harassment or bullying', 'Қудалау немесе буллинг'],
    INAPPROPRIATE: ['Неприемлемое поведение', 'Inappropriate behavior', 'Орынсыз әрекет'],
    OTHER: ['Другая причина', 'Other reason', 'Басқа себеп'],
    submit: ['Отправить жалобу', 'Submit report', 'Шағымды жіберу'],
    close: ['Закрыть', 'Close', 'Жабу'],
    back: ['Назад', 'Back', 'Артқа'],
    wait: ['Сохраняем…', 'Saving…', 'Сақталуда…'],
    rateLimit: ['Лимит жалоб исчерпан. Попробуйте позже.', 'The report limit has been reached. Try again later.', 'Шағымдар шегіне жеттіңіз. Кейінірек қайталаңыз.'],
    failed: ['Не удалось выполнить действие. Попробуйте ещё раз.', 'Could not complete this action. Try again.', 'Әрекетті орындау мүмкін болмады. Қайталап көріңіз.'],
    member: ['Участник', 'Member', 'Қатысушы']
  };
  function t(key) {
    const language = ['ru', 'en', 'kk'].indexOf(window.StudHubI18n?.language);
    return words[key][Math.max(0, language)];
  }
  function element(tag, text, className) {
    const result = document.createElement(tag);
    if (text) result.textContent = text;
    if (className) result.className = className;
    return result;
  }
  function button(label, action, className = 'btn btn-light') {
    const result = element('button', t(label), className);
    result.type = 'button';
    result.addEventListener('click', action);
    return result;
  }
  async function request(path, method = 'GET', body) {
    const headers = {};
    const key = document.querySelector('meta[name="_csrf_header"]')?.content;
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    if (key && token) headers[key] = token;
    if (body) headers['Content-Type'] = 'application/json';
    let response;
    try {
      response = await fetch('/api/friends/' + path, {method, headers, body: body && JSON.stringify(body), cache: 'no-store', signal: AbortSignal.timeout(12000)});
    } catch {
      throw new Error(t('failed'));
    }
    if (!response.ok || response.redirected) throw new Error(t(response.status === 429 && path.startsWith('reports/') ? 'rateLimit' : 'failed'));
    if (response.status === 204) return null;
    return response.json().catch(() => null);
  }
  function field(id, label, control) {
    const field = element('div', '', 'field-group');
    const caption = element('label', t(label));
    caption.htmlFor = id;
    control.id = id;
    field.append(caption, control);
    return field;
  }

  function open(person, {messageId, onBlock, fallbackFocus, opener = document.activeElement} = {}) {
    if (!Number.isSafeInteger(person?.id) || person.id <= 0 || document.getElementById('contact-safety-dialog')) return;
    const context = Number.isSafeInteger(messageId) && messageId > 0 ? {messageId} : {};
    const dialog = element('dialog', '', 'chat-dialog');
    dialog.id = 'contact-safety-dialog';
    dialog.setAttribute('aria-labelledby', 'contactSafetyTitle');
    dialog.dataset.userContent = '';
    const header = element('header', '', 'chat-dialog-heading');
    const title = element('h2', t('safety'), 'contact-safety-title');
    title.id = 'contactSafetyTitle';
    const close = button('close', () => dialog.close());
    header.replaceChildren(title, close);
    const body = element('div');
    const notice = element('p', '', 'chat-error');
    notice.setAttribute('role', 'alert');
    notice.hidden = true;
    dialog.append(header, notice, body);
    document.body.append(dialog);

    let busy = false;
    dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
    dialog.addEventListener('close', () => {
      dialog.remove();
      const returnTo = opener?.isConnected && opener.getClientRects().length ? opener : fallbackFocus?.();
      returnTo?.focus({preventScroll: true});
    });

    function actions(...buttons) {
      const row = element('div', '', 'form-actions');
      row.append(...buttons);
      return row;
    }
    function success(status) {
      notice.hidden = true;
      const feedback = element('p', t(status));
      feedback.setAttribute('role', 'status');
      body.replaceChildren(feedback);
      close.focus();
    }
    async function save(control, operation, status, onSaved) {
      busy = true;
      const controls = [...dialog.querySelectorAll('button,select,textarea')];
      controls.forEach(input => input.disabled = true);
      dialog.setAttribute('aria-busy', 'true');
      notice.hidden = true;
      const oldLabel = control.textContent;
      control.textContent = t('wait');
      let saved = false;
      try {
        await operation();
        saved = true;
      } catch (error) {
        notice.textContent = error.message;
        notice.hidden = false;
      } finally {
        dialog.removeAttribute('aria-busy');
        busy = false;
        controls.forEach(input => input.disabled = false);
        control.textContent = oldLabel;
      }
      if (saved) {
        success(status);
        onSaved?.();
      } else control.focus();
    }
    function home() {
      notice.hidden = true;
      title.textContent = t('safety');
      const block = button('block', blockForm);
      const report = button('report', reportForm);
      body.replaceChildren(element('p', person.name || t('member')), actions(block, report));
      block.focus();
    }
    function blockForm() {
      title.textContent = t('block');
      const query = context.messageId ? '?' + new URLSearchParams(context) : '';
      const confirm = button('block', () => save(confirm, () => request('blocked/' + person.id + query, 'PUT'), 'blocked', onBlock), 'btn btn-primary');
      body.replaceChildren(element('p', t('blockHint')), actions(button('back', home), confirm));
      confirm.focus();
    }
    function reportForm() {
      title.textContent = t('report');
      const form = element('form');
      const reason = element('select');
      reason.required = true;
      for (const value of ['', 'SPAM', 'HARASSMENT', 'INAPPROPRIATE', 'OTHER']) {
        const option = element('option', t(value || 'selectReason'));
        option.value = value;
        reason.append(option);
      }
      const details = element('textarea');
      details.maxLength = 1000;
      details.rows = 4;
      const submit = element('button', t('submit'), 'btn btn-primary');
      submit.type = 'submit';
      form.append(field('contactReportReason', 'reason', reason), field('contactReportDetails', 'details', details),
        actions(button('back', home), submit));
      form.addEventListener('submit', event => {
        event.preventDefault();
        if (busy) return;
        const payload = {...context, reason: reason.value, details: details.value.trim()};
        save(submit, () => request('reports/' + person.id, 'POST', payload), 'reportSent');
      });
      body.replaceChildren(element('p', t('reportHint'), 'muted-copy'), form);
      reason.focus();
    }

    dialog.showModal();
    home();
  }

  window.StudHubContactSafety = {
    t, open,
    blockedUsers: async () => (await request('blocked')).users,
    async unblock(id) { return request('blocked/' + id, 'DELETE'); }
  };
})();
