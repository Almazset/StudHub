(() => {
  'use strict';
  const root = document.querySelector('[data-friends-root]');
  if (!root) return;
  const safety = window.StudHubContactSafety;
  const copy = {
    heading: ["Друзья","Friends","Достар"],
    intro: ["Находите друзей и оставайтесь на связи.","Find friends and stay in touch.","Достар тауып, байланыста болыңыз."],
    myStatus: ["Мой статус","My status","Менің күйім"],
    ONLINE: ["В сети","Online","Желіде"],
    AWAY: ["Отошёл","Away","Орнында жоқ"],
    BUSY: ["Не беспокоить","Do not disturb","Мазаламаңыз"],
    INVISIBLE: ["Невидимый","Invisible","Көрінбейтін"],
    OFFLINE: ["Не в сети","Offline","Желіде емес"],
    statusHint: ["Друзья видят статус, если вы разрешили это в настройках. После закрытия или сворачивания последней вкладки статус может оставаться «В сети» до полутора минут. «Не беспокоить» не отключает уведомления.","Friends can see your status if you allow it in settings. After you close or hide your last tab, you may appear online for up to 90 seconds. Do not disturb leaves notifications on.","Рұқсат берсеңіз, достарыңыз күйіңізді көреді. Соңғы қойынды жабылғаннан немесе жасырылғаннан кейін күйіңіз бір жарым минутқа дейін «Желіде» болып көрінуі мүмкін. «Мазаламаңыз» хабарландыруларды өшірмейді."],
    privacy: ["Настройки приватности","Privacy settings","Құпиялық баптаулары"],
    add: ["Добавить друга","Add a friend","Дос қосу"],
    findHint: ["Введите имя, никнейм или ID, например SH-123.","Enter a name, nickname or ID, such as SH-123.","Атын, лақап атын немесе SH-123 сияқты ID енгізіңіз."],
    query: ["Имя, никнейм или SH-ID","Name, nickname or SH-ID","Аты, лақап аты немесе SH-ID"],
    find: ["Найти","Search","Іздеу"],
    sections: ["Разделы друзей","Friends sections","Достар бөлімдері"],
    accepted: ["Друзья","Friends","Достар"],
    incoming: ["Входящие","Incoming","Кіріс"],
    outgoing: ["Отправленные","Sent","Жіберілген"],
    blocked: ["Заблокированные","Blocked","Бұғатталған"],
    filter: ["Поиск в списке","Search this list","Тізімнен іздеу"],
    filterPlaceholder: ["Имя или SH-ID","Name or SH-ID","Аты немесе SH-ID"],
    onlyOnline: ["Только в сети","Online only","Тек желіде"],
    refresh: ["Обновить","Refresh","Жаңарту"],
    loading: ["Загрузка…","Loading…","Жүктелуде…"],
    failed: ["Не удалось выполнить действие. Попробуйте ещё раз.","Could not complete this action. Try again.","Әрекетті орындау мүмкін болмады. Қайталап көріңіз."],
    conflict: ["Заявка или дружба уже существует.","A request or friendship already exists.","Достық немесе өтінім бұрыннан бар."],
    unavailable: ["Сейчас это действие недоступно.","This action is currently unavailable.","Бұл әрекет қазір қолжетімсіз."],
    none: ["По выбранным условиям никого не найдено.","No matches for these filters.","Бұл сүзгілер бойынша ешкім табылмады."],
    emptyAccepted: ["Список друзей пока пуст.","You have no friends yet.","Достар тізімі әзірге бос."],
    emptyIncoming: ["Новых заявок нет.","No incoming requests.","Жаңа өтінімдер жоқ."],
    emptyOutgoing: ["Нет отправленных заявок.","No requests sent.","Жіберілген өтінімдер жоқ."],
    emptyBlocked: ["Вы пока никого не заблокировали.","You have not blocked anyone.","Әзірге ешкімді бұғаттаған жоқсыз."],
    accept: ["Принять","Accept","Қабылдау"],
    message: ["Написать","Message","Хабарлама жазу"],
    messageUnavailable: ["Личные сообщения недоступны","Direct messages unavailable","Жеке хабарламалар қолжетімсіз"],
    decline: ["Отклонить","Decline","Қабылдамау"],
    cancel: ["Отменить","Cancel","Бас тарту"],
    remove: ["Удалить","Remove","Жою"],
    confirmRemove: ["Удалить пользователя из друзей?","Remove this friend?","Пайдаланушыны достар тізімінен жою керек пе?"],
    updated: ["Список обновлён","List refreshed","Тізім жаңартылды"],
    offline: ["Нет связи. Повторим обновление автоматически.","Offline. We will retry automatically.","Байланыс жоқ. Автоматты түрде қайталаймыз."],
    notFound: ["Пользователь не найден.","No user found.","Пайдаланушы табылмады."],
    alreadyFriend: ["Уже в друзьях","Already friends","Достарыңызда бар"],
    requestExists: ["Заявка существует","Request pending","Өтінім жіберілген"],
    request: ["Добавить в друзья","Send friend request","Достық өтінімін жіберу"],
    requested: ["Заявка отправлена.","Request sent.","Өтінім жіберілді."],
    unblocked: ["Пользователь разблокирован.","User unblocked.","Пайдаланушы бұғаттан шығарылды."],
    saving: ["Сохраняем статус…","Saving status…","Күй сақталуда…"],
    done: ["Статус сохранён","Status saved","Күй сақталды"],
    saveFail: ["Не удалось сохранить статус","Could not save status","Күйді сақтау мүмкін болмады"],
  };
  const t = key => copy[key][Math.max(0, ['ru', 'en', 'kk'].indexOf(window.StudHubI18n?.language))];
  const $ = selector => root.querySelector(selector);
  const list = $('[data-friends-list]'), results = $('[data-friend-results]'), error = $('.friends-error');
  const search = $('[data-friends-filter]'), online = $('[data-friends-online]'), sync = $('[data-friends-sync]');
  const preference = $('[data-my-presence]');
  const tabs = [...root.querySelectorAll('[data-friend-tab]')];
  let tab = new URLSearchParams(location.search).get('tab') === 'blocked' ? 'blocked' : 'accepted';
  let entries = [], blocked = null, searchResults = null, sequence = 0, lastRendered = '', activeRequests = 0;
  let refreshing = false;

  const node = (tag, cls, text) => {
    const el = document.createElement(tag);
    if (cls) el.className = cls;
    if (text !== undefined) el.textContent = text;
    return el;
  };
  async function api(path, method = 'GET', body) {
    const headers = {};
    if (method !== 'GET') {
      const key = document.querySelector('meta[name="_csrf_header"]')?.content;
      const token = document.querySelector('meta[name="_csrf"]')?.content;
      if (key && token) headers[key] = token;
    }
    if (body) headers['Content-Type'] = 'application/json';
    let response;
    try {
      response = await fetch(path, {method, headers, credentials:'same-origin', cache:'no-store', body:body && JSON.stringify(body), signal:AbortSignal.timeout(12000)});
    } catch { throw new Error(t('failed')); }
    if (!response.ok || response.redirected) {
      const reason = response.redirected ? 'failed' : {403:'unavailable',404:'unavailable',409:'conflict'}[response.status] || 'failed';
      throw new Error(t(reason));
    }
    return response.status === 204 ? null : response.json();
  }

  function fail(problem) {
    error.hidden = false;
    error.textContent = problem.message;
  }
  function kind(entry) {
    return entry.status === 'ACCEPTED' ? 'accepted' : entry.incoming ? 'incoming' : 'outgoing';
  }
  function action(label, execute, primary = false) {
    const button = node('button', primary ? 'btn btn-primary' : 'btn btn-light', label);
    button.type = 'button';
    button.addEventListener('click', async () => {
      button.disabled = true;
      activeRequests++;
      error.hidden = true;
      try { await execute(); }
      catch (problem) { fail(problem); }
      finally { activeRequests--; button.disabled = false; }
    });
    return button;
  }

  function row(person, actions) {
    const {id, publicId, name, status, presence} = person;
    const label = name || publicId || safety.t('member');
    const el = node('div', 'friends-row');
    const avatar = node('span', 'friends-avatar', (name || safety.t('member')).slice(0, 1).toUpperCase());
    avatar.setAttribute('aria-hidden', 'true');
    el.dataset.friendId = id;
    const info = node('div', 'friends-info');
    info.append(node('strong', '', label));
    if (publicId && publicId !== label) info.append(node('small', '', publicId));
    if (status === 'ACCEPTED' && Object.hasOwn(copy, presence)) {
      info.append(node('span', 'friend-presence friend-presence-' + presence.toLowerCase(), t(presence)));
    }
    const buttons = node('div', 'friends-actions');
    buttons.append(...actions);
    el.append(avatar, info, buttons);
    return el;
  }

  function safetyAction(id, name) {
    const button = action(safety.t('more'), () => {
      safety.open({id,name}, {
        opener: button,
        fallbackFocus: () => tabs.find(tabButton => tabButton.dataset.friendTab === tab),
        onBlock: () => {
          blocked = null;
          searchResults = null;
          results.replaceChildren();
          refresh().catch(fail);
        }
      });
    });
    button.setAttribute('aria-label', safety.t('safety') + (name ? ' · ' + name : ''));
    return button;
  }

  function renderBlocked() {
    if (!blocked?.length) {
      list.append(node('p', 'muted-copy', t(blocked ? 'emptyBlocked' : 'loading')));
      return;
    }
    for (const person of blocked) {
      const unblock = action(safety.t('unblock'), async () => {
        await safety.unblock(person.id);
        await refresh();
        sync.textContent = t('unblocked');
        if (!list.contains(document.activeElement)) tabs.find(button => button.dataset.friendTab === 'blocked')?.focus();
      });
      list.append(row(person, [unblock]));
    }
  }

  function renderFriends() {
    const query = search.value.trim().toLocaleLowerCase();
    const onlineOnly = tab === 'accepted' && online.checked;
    const visibleStatuses = ['ONLINE', 'AWAY', 'BUSY'];
    const rows = entries.filter(entry => {
      const haystack = entry.profileVisible === false ? safety.t('member') : entry.name + ' SH-' + entry.userId;
      return kind(entry) === tab && (!query || haystack.toLocaleLowerCase().includes(query))
        && (!onlineOnly || visibleStatuses.includes(entry.presence));
    });
    rows.sort((a,b) => Number(visibleStatuses.includes(b.presence)) - Number(visibleStatuses.includes(a.presence))
      || (a.name || '').localeCompare(b.name || ''));
    const empty = {accepted:'emptyAccepted', incoming:'emptyIncoming', outgoing:'emptyOutgoing'};
    if (!rows.length) {
      list.append(node('p', 'muted-copy', t(query || onlineOnly ? 'none' : empty[tab])));
      return;
    }
    for (const person of rows) {
      const {id, userId, profileVisible} = person;
      const actions = [];
      if (tab === 'incoming') actions.push(action(t('accept'), async () => {
        await api('/api/friends/' + id + '/accept', 'POST');
        await refresh();
      }, true));
      if (tab === 'accepted') {
        if (person.canMessage !== false) actions.push(action(t('message'), async () => {
          const room = await api('/api/friends/' + id + '/chat', 'POST');
          location.assign('/chat?room=' + encodeURIComponent(room.id));
        }, true));
        else actions.push(node('small', 'muted-copy', t('messageUnavailable')));
      }
      const removeLabel = {accepted:'remove',incoming:'decline',outgoing:'cancel'}[tab];
      actions.push(action(t(removeLabel), async () => {
        if (tab === 'accepted' && !confirm(t('confirmRemove'))) return;
        await api('/api/friends/' + id, 'DELETE');
        await refresh();
      }));
      const name = profileVisible === false ? '' : person.name;
      actions.push(safetyAction(userId, name));
      const publicId = profileVisible === false ? '' : 'SH-' + userId;
      list.append(row({...person, name, publicId}, actions));
    }
  }

  function render() {
    const focused = list.contains(document.activeElement) ? {
      id:document.activeElement.closest('[data-friend-id]')?.dataset.friendId,
      label:document.activeElement.textContent
    } : null;
    tabs.forEach(button => {
      const name = button.dataset.friendTab;
      button.setAttribute('aria-pressed', String(name === tab));
      const count = button.querySelector('[data-count]');
      if (count) count.textContent = entries.filter(entry => kind(entry) === name).length;
    });
    $('[data-friend-filters]').hidden = tab === 'blocked';
    online.disabled = tab !== 'accepted';
    list.replaceChildren();
    if (tab === 'blocked') renderBlocked();
    else renderFriends();
    if (focused) {
      const row = [...list.children].find(item => item.dataset.friendId === focused.id);
      [...row?.querySelectorAll('button') || []].find(button => button.textContent === focused.label)?.focus({preventScroll:true});
    }
  }

  async function refresh() {
    const seq = ++sequence, requestingBlocks = tab === 'blocked';
    const [friends, blockList] = await Promise.all([
      api('/api/friends'), requestingBlocks ? safety.blockedUsers() : Promise.resolve(null)
    ]);
    if (seq !== sequence) return;
    entries = friends;
    if (requestingBlocks) blocked = blockList;
    const snapshot = JSON.stringify([entries, blocked, tab]);
    if (snapshot !== lastRendered) {
      lastRendered = snapshot;
      render();
    }
    sync.textContent = t('updated');
  }
  async function poll() {
    if (document.hidden || activeRequests || refreshing || document.querySelector('#contact-safety-dialog')) return;
    refreshing = true;
    try { await refresh(); }
    catch { sync.textContent = t('offline'); }
    finally { refreshing = false; }
  }

  function renderSearch() {
    results.replaceChildren();
    if (!searchResults) return;
    if (!searchResults.length) {
      results.append(node('p', 'muted-copy', t('notFound')));
      return;
    }
    for (const person of searchResults) {
      const existing = entries.find(friend => friend.userId === person.id);
      const sendRequest = existing
        ? node('small', 'muted-copy', t(kind(existing) === 'accepted' ? 'alreadyFriend' : 'requestExists'))
        : action(t('request'), async () => {
          await api('/api/friends', 'POST', {userId:person.id});
          await refresh();
          searchResults = null;
          results.replaceChildren(node('p', 'muted-copy', t('requested')));
        }, true);
      results.append(row({...person, publicId:person.publicId || 'SH-' + person.id},
        [sendRequest, safetyAction(person.id, person.name)]));
    }
  }

  tabs.forEach(button => button.addEventListener('click', () => {
    tab = button.dataset.friendTab;
    error.hidden = true;
    render();
    if (tab === 'blocked') refresh().catch(fail);
  }));
  search.addEventListener('input', render);
  online.addEventListener('change', render);
  $('[data-friends-refresh]').addEventListener('click', async event => {
    const button = event.currentTarget;
    button.disabled = true;
    error.hidden = true;
    try { await refresh(); }
    catch (problem) { fail(problem); }
    finally { button.disabled = false; }
  });
  $('[data-friend-search]').addEventListener('submit', async event => {
    event.preventDefault();
    const query = event.currentTarget.elements.query.value.trim();
    if (query.length < 2) return;
    const button = event.currentTarget.querySelector('button');
    button.disabled = true;
    error.hidden = true;
    searchResults = null;
    results.replaceChildren(node('p', 'muted-copy', t('loading')));
    try {
      searchResults = await api('/api/chat/users?q=' + encodeURIComponent(query));
      renderSearch();
    } catch (problem) {
      results.replaceChildren();
      fail(problem);
    } finally { button.disabled = false; }
  });

  let saved = 'ONLINE';
  api('/api/friends/presence').then(value => {
    saved = value.status;
    preference.value = saved;
    preference.disabled = false;
  }).catch(fail);
  preference.addEventListener('change', async () => {
    preference.disabled = true;
    const feedback = $('[data-presence-feedback]');
    feedback.textContent = t('saving');
    try {
      const value = await api('/api/friends/presence', 'PUT', {status:preference.value});
      saved = value.status;
      feedback.textContent = t('done');
    } catch (problem) {
      preference.value = saved;
      feedback.textContent = t('saveFail');
    } finally { preference.disabled = false; }
  });

  function localize() {
    root.querySelectorAll('[data-friends-copy]').forEach(el => el.textContent = t(el.dataset.friendsCopy));
    root.querySelectorAll('[data-friends-placeholder]').forEach(el => el.placeholder = t(el.dataset.friendsPlaceholder));
    $('.friends-tabs').setAttribute('aria-label', t('sections'));
  }
  localize();
  document.addEventListener('studhub:language', () => { localize(); render(); renderSearch(); });
  if (tab === 'blocked') render();
  refresh().catch(fail);
  setInterval(poll, 15000);
  document.addEventListener('visibilitychange', poll);
  window.addEventListener('online', poll);
})();
