(() => {
  'use strict';

  const apiRoot = '/api/notifications';
  const categories = {
    CHAT_MESSAGE: {icon: 'chat-dots', label: ['Новые сообщения', 'New messages', 'Жаңа хабарлар'], title: ['Новое сообщение', 'New message', 'Жаңа хабар']},
    PROJECT_INVITATION: {icon: 'folder-plus', label: ['Приглашения в проекты', 'Project invitations', 'Жоба шақырулары'], title: ['Приглашение в проект', 'Project invitation', 'Жобаға шақыру']},
    FRIEND_REQUEST: {icon: 'person-plus', label: ['Заявки в друзья', 'Friend requests', 'Достық өтінімдері'], title: ['Заявка в друзья', 'Friend request', 'Достық өтінімі']},
    TASK_ASSIGNED: {icon: 'check2-square', label: ['Назначенные задачи', 'Task assignments', 'Тағайындалған тапсырмалар'], title: ['Назначена задача', 'Task assigned', 'Тапсырма тағайындалды']},
    DEADLINE: {icon: 'calendar-event', label: ['Ближайшие дедлайны', 'Upcoming deadlines', 'Жақын мерзімдер'], title: ['Приближается дедлайн', 'Upcoming deadline', 'Мерзім жақындап қалды']}
  };
  const copy = {
    title: ['Уведомления', 'Notifications', 'Хабарландырулар'],
    settings: ['Настроить', 'Customize', 'Баптау'],
    loading: ['Загружаем уведомления…', 'Loading notifications…', 'Хабарландырулар жүктелуде…'],
    empty: ['Уведомлений пока нет.', 'No notifications yet.', 'Әзірге хабарландыру жоқ.'],
    unread: ['Непрочитанных: {count}', 'Unread: {count}', 'Оқылмағаны: {count}'],
    noneUnread: ['Всё прочитано', 'All caught up', 'Барлығы оқылды'],
    readAll: ['Прочитать все', 'Mark all as read', 'Бәрін оқылды деп белгілеу'],
    read: ['Отметить прочитанным', 'Mark as read', 'Оқылды деп белгілеу'],
    new: ['Новое', 'New', 'Жаңа'],
    retry: ['Повторить', 'Try again', 'Қайталау'],
    loadError: ['Не удалось загрузить уведомления.', 'Could not load notifications.', 'Хабарландырулар жүктелмеді.'],
    readError: ['Не удалось отметить уведомления прочитанными. Повторите попытку.', 'Could not mark notifications as read. Try again.', 'Хабарландырулар оқылды деп белгіленбеді. Қайталап көріңіз.'],
    session: ['Сеанс завершён. Войдите снова.', 'Your session has ended. Sign in again.', 'Сеанс аяқталды. Қайта кіріңіз.'],
    inApp: ['В StudHub', 'In StudHub', 'StudHub ішінде'],
    prefsLoading: ['Загружаем типы уведомлений…', 'Loading notification types…', 'Хабарландыру түрлері жүктелуде…'],
    prefsError: ['Не удалось загрузить типы уведомлений.', 'Could not load notification types.', 'Хабарландыру түрлері жүктелмеді.'],
    saveError: ['Не удалось сохранить настройки. Повторите попытку.', 'Could not save the preferences. Try again.', 'Баптаулар сақталмады. Қайталап көріңіз.'],
    save: ['Сохранить', 'Save', 'Сақтау'],
    saving: ['Сохраняем…', 'Saving…', 'Сақталуда…'],
    saved: ['Настройки уведомлений сохранены.', 'Notification preferences saved.', 'Хабарландыру баптаулары сақталды.']
  };

  const language = () => window.StudHubI18n?.language || 'ru';
  const translate = words => words[['ru', 'en', 'kk'].indexOf(language())] || words[0];
  const typeNames = Object.keys(categories);
  const t = (key, count) => translate(copy[key]).replace('{count}', String(count));
  function node(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text != null) element.textContent = text;
    return element;
  }
  function symbol(name) {
    const icon = node('i', 'bi bi-' + name);
    icon.setAttribute('aria-hidden', 'true');
    return icon;
  }
  function button(label, className) {
    const element = node('button', className, label);
    element.type = 'button';
    return element;
  }
  async function api(path, {method = 'GET', body, signal, keepalive = false} = {}) {
    const headers = new Headers({Accept: 'application/json'});
    if (body) headers.set('Content-Type', 'application/json');
    if (method !== 'GET') {
      const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
      const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
      if (csrfHeader && csrfToken) headers.set(csrfHeader, csrfToken);
    }
    const requestSignal = signal ? AbortSignal.any([signal, AbortSignal.timeout(12000)]) : AbortSignal.timeout(12000);
    const response = await fetch(apiRoot + path, {
      method, headers, credentials: 'same-origin', cache: 'no-store', keepalive,
      signal: requestSignal, body: body ? JSON.stringify(body) : undefined
    });
    if (response.redirected || response.status === 401 || response.status === 403) throw new Error('session');
    if (!response.ok) throw new Error('request');
    return response.status === 204 ? null : response.json();
  }
  function linkTarget(href) {
    if (typeof href !== 'string' || !href.startsWith('/') || href.startsWith('//')) return null;
    let url;
    try { url = new URL(href, location.origin); } catch { return null; }
    if (url.origin !== location.origin) return null;
    return /^\/(?:chat|friends|projects|assignments|calendar)(?:\/|$)/.test(url.pathname)
      ? url.pathname + url.search + url.hash : null;
  }

  const inbox = document.querySelector('[data-notification-inbox]');
  let refreshInbox = () => {};
  if (inbox) {
    const bell = inbox.querySelector('[data-notification-trigger]');
    const badge = inbox.querySelector('[data-notification-badge]');
    const panel = inbox.querySelector('[data-panel="notifications"]');
    const list = panel.querySelector('[data-notification-list]');
    const readAll = panel.querySelector('[data-notification-read-all]');
    const summary = panel.querySelector('[data-notification-summary]');
    const status = panel.querySelector('[data-notification-status]');
    const error = panel.querySelector('[data-notification-error]');
    const retry = panel.querySelector('[data-notification-retry]');
    const settings = panel.querySelector('[data-notification-settings-link]');
    let feed = null;
    let poll = null;
    let writing = false;
    let failure = null;
    let displayKey = '';

    function markLocal(id) {
      if (!feed) return;
      const old = feed.items.find(item => item.id === id);
      feed = {
        unread: id == null ? 0 : Math.max(0, feed.unread - Number(old != null && !old.read)),
        items: feed.items.map(item => id == null || item.id === id ? {...item, read: true} : item)
      };
    }
    async function markRead(id = null, keepalive = false) {
      if (writing) return;
      const focused = document.activeElement;
      const hadFocus = panel.contains(focused);
      const currentPoll = poll;
      poll = null;
      currentPoll?.abort();
      writing = true;
      failure = null;
      render();
      try {
        await api(id == null ? '/read-all' : '/' + id + '/read', {method: 'POST', keepalive});
        markLocal(id);
      } catch (reason) {
        failure = reason.message === 'session' ? 'session' : 'readError';
      } finally {
        writing = false;
        render();
        if (!keepalive && !panel.hidden && document.activeElement === document.body && hadFocus) {
          const row = list.querySelector('[data-notification-id="' + id + '"]');
          const target = focused.isConnected && !focused.disabled ? focused : row?.querySelector('[data-notification-role="open"]');
          (target || settings).focus({preventScroll: true});
        }
        if (!failure && !keepalive) refreshInbox();
      }
    }
    function notificationRow(item) {
      const category = categories[item.type];
      const row = node('li', 'notification-row' + (item.read ? '' : ' is-unread'));
      row.dataset.notificationId = String(item.id);
      row.dataset.notificationReadState = item.read ? 'read' : 'unread';
      const href = linkTarget(item.href);
      const primary = node(href ? 'a' : 'div', 'notification-entry');
      if (href) {
        primary.href = href;
        primary.dataset.notificationRole = 'open';
        primary.addEventListener('click', event => {
          if (!item.read && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
            markRead(item.id, true);
          }
        });
      }
      const text = node('span', 'notification-entry-text');
      const title = node('strong', '', translate(category.title));
      text.append(title);
      if (!item.read) title.append(node('span', 'visually-hidden', ' · ' + t('new')));
      if (item.detail) text.append(node('span', 'notification-detail', item.detail));
      const instant = new Date(item.createdAt);
      if (Number.isFinite(instant.getTime())) {
        const timestamp = node('time', '', new Intl.DateTimeFormat(language(), {dateStyle: 'medium', timeStyle: 'short'}).format(instant));
        timestamp.dateTime = instant.toISOString();
        text.append(timestamp);
      }
      const icon = node('span', 'notification-entry-icon');
      icon.append(symbol(category.icon));
      primary.append(icon, text);
      row.append(primary);
      if (!item.read) {
        const read = button(null, 'notification-read-button');
        read.dataset.notificationRead = String(item.id);
        read.dataset.notificationRole = 'read';
        read.disabled = writing;
        read.title = t('read');
        read.setAttribute('aria-label', t('read') + ' · ' + translate(category.title));
        read.append(symbol('check2'));
        read.addEventListener('click', () => markRead(item.id));
        row.append(read);
      }
      return row;
    }
    function renderRows() {
      const items = feed?.items || [];
      const key = JSON.stringify([language(), items]);
      if (key === displayKey) {
        list.querySelectorAll('[data-notification-read]').forEach(control => { control.disabled = writing; });
        return;
      }
      displayKey = key;
      const focused = document.activeElement?.closest('[data-notification-id]');
      const id = focused?.dataset.notificationId;
      const action = document.activeElement?.dataset.notificationRole;
      const rows = items.map(notificationRow);
      list.replaceChildren(...rows);
      if (id && !panel.hidden) {
        const row = rows.find(item => item.dataset.notificationId === id);
        const next = row?.querySelector('[data-notification-role="' + action + '"]:not(:disabled)')
          || row?.querySelector('[data-notification-role="open"]') || settings;
        next.focus({preventScroll: true});
      }
    }
    function render() {
      const unread = feed?.unread || 0;
      badge.hidden = !unread;
      badge.textContent = unread > 99 ? '99+' : String(unread);
      const label = unread ? t('title') + ' · ' + t('unread', unread) : t('title');
      bell.setAttribute('aria-label', label);
      summary.textContent = feed ? (unread ? t('unread', unread) : t('noneUnread')) : '';
      readAll.disabled = !unread || writing;
      if (readAll.disabled && document.activeElement === readAll && !writing) settings.focus();
      status.hidden = Boolean(failure) || Boolean(feed?.items.length);
      status.textContent = feed ? t('empty') : t('loading');
      error.hidden = !failure;
      error.textContent = failure ? t(failure) : '';
      retry.hidden = !failure || failure === 'session' || failure === 'readError';
      renderRows();
    }

    refreshInbox = async function (replace = false) {
      if (writing || document.hidden || (poll && !replace)) return;
      poll?.abort();
      const controller = new AbortController();
      poll = controller;
      try {
        const zone = new Intl.DateTimeFormat().resolvedOptions().timeZone;
        const query = zone ? '?' + new URLSearchParams({zone}) : '';
        const result = await api(query, {signal: controller.signal});
        if (poll !== controller) return;
        if (!Number.isSafeInteger(result?.unread) || result.unread < 0 || !Array.isArray(result.items)) throw new Error('request');
        feed = {
          unread: result.unread,
          items: result.items.filter(item => item && Number.isSafeInteger(item.id) && item.id > 0 && Object.hasOwn(categories, item.type))
        };
        if (failure !== 'readError') failure = null;
      } catch (reason) {
        if (poll !== controller) return;
        failure = reason.message === 'session' ? 'session' : 'loadError';
      } finally {
        if (poll === controller) {
          poll = null;
          render();
        }
      }
    };
    function localize() {
      panel.querySelectorAll('[data-notification-copy]').forEach(element => { element.textContent = t(element.dataset.notificationCopy); });
      render();
    }
    bell.addEventListener('click', () => { if (!panel.hidden) refreshInbox(true); });
    retry.addEventListener('click', async () => {
      const hadFocus = document.activeElement === retry;
      await refreshInbox(true);
      if (hadFocus && retry.hidden && !panel.hidden
          && (document.activeElement === retry || document.activeElement === document.body)) {
        settings.focus({ preventScroll: true });
      }
    });
    readAll.addEventListener('click', () => markRead());
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) refreshInbox();
    });
    document.addEventListener('studhub:language', localize);
    window.addEventListener('pageshow', event => { if (event.persisted) refreshInbox(); });
    setInterval(refreshInbox, 30000);
    localize();
    refreshInbox();
  }

  let preferenceWrite;
  async function mountPreferences(container) {
    if (!container) return;
    const heading = node('h4', '', t('inApp'));
    const header = node('header');
    header.append(heading);
    const status = node('p', 'notification-prefs-status', t('prefsLoading'));
    status.setAttribute('role', 'status');
    container.replaceChildren(header, status);
    try {
      await preferenceWrite?.catch(() => {});
      if (!container.isConnected) return;
      const preferences = await api('/preferences');
      if (!typeNames.every(type => typeof preferences?.[type] === 'boolean')) throw new Error('request');
      if (!container.isConnected) return;
      status.textContent = '';
      const form = node('form', 'notification-preferences');
      const fields = node('fieldset', 'notification-pref-fields');
      fields.append(node('legend', 'visually-hidden', t('title')));
      for (const type of typeNames) {
        const label = node('label', 'notification-pref');
        const icon = node('span', 'notification-pref-icon');
        icon.append(symbol(categories[type].icon));
        const toggle = node('input');
        toggle.type = 'checkbox';
        toggle.checked = preferences[type];
        toggle.dataset.notificationPreference = type;
        label.append(icon, node('span', 'notification-pref-label', translate(categories[type].label)), toggle);
        fields.append(label);
      }
      const submit = node('button', 'btn btn-primary', t('save'));
      submit.type = 'submit';
      submit.dataset.notificationSave = '';
      submit.disabled = true;
      form.append(fields, submit);
      container.insertBefore(form, status);
      form.addEventListener('input', () => {
        submit.disabled = false;
        status.textContent = '';
      });
      form.addEventListener('submit', async event => {
        event.preventDefault();
        if (preferenceWrite || submit.disabled) return;
        const inputs = [...form.querySelectorAll('[data-notification-preference]')];
        const body = Object.fromEntries(inputs.map(input => [input.dataset.notificationPreference, input.checked]));
        fields.disabled = true;
        submit.disabled = true;
        submit.textContent = t('saving');
        const pending = api('/preferences', {method: 'PUT', body});
        preferenceWrite = pending;
        let saved = false;
        try {
          await pending;
          saved = true;
          status.setAttribute('role', 'status');
          status.textContent = t('saved');
          refreshInbox(true);
        } catch (reason) {
          status.setAttribute('role', 'alert');
          status.textContent = t(reason.message === 'session' ? 'session' : 'saveError');
        } finally {
          preferenceWrite = null;
          fields.disabled = false;
          submit.disabled = saved;
          submit.textContent = t('save');
        }
      });
    } catch (reason) {
      if (!container.isConnected) return;
      status.setAttribute('role', 'alert');
      status.textContent = t(reason.message === 'session' ? 'session' : 'prefsError');
      if (reason.message !== 'session') {
        const retry = button(t('retry'), 'btn btn-light');
        retry.addEventListener('click', () => mountPreferences(container));
        container.append(retry);
      }
    }
  }

  window.StudHubNotifications = {mountPreferences};
})();
