(() => {
  'use strict';
  const preview = location.pathname.endsWith('studhub-preview.html');
  const key = 'studhub-preview-projects-v1';
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let root, projects = [], invitations = [];
  const projectUrl = mode => preview ? `#projects${mode ? '-' + mode : ''}` : `/projects${mode ? '#' + mode : ''}`;
  const link = (mode, label, icon) => `<a class="quick-action" href="${projectUrl(mode)}" ${preview ? `data-preview-route="projects-${mode}"` : ''}><i class="bi bi-${icon}"></i><span>${label}</span><i class="bi bi-chevron-right"></i></a>`;
  function dashboard() {
    const main=document.querySelector('#main-content');
    if(document.body.dataset.page !== 'dashboard' || main.querySelector('.quick-actions')) return;
    const section=document.createElement('section'); section.className='surface quick-actions';
    section.innerHTML=`<h3>Быстрые действия</h3><div class="quick-actions-list">${link('new','Создать проект','folder-plus')}<a class="quick-action" href="${preview ? '#assignment-new' : '/assignments/new'}" ${preview ? 'data-preview-route="assignment-new"' : ''}><i class="bi bi-check2-square"></i><span>Создать задачу</span><i class="bi bi-chevron-right"></i></a>${link('members','Добавить участника','person-plus')}</div>`;
    main.querySelector('.stats-grid')?.insertAdjacentElement('afterend',section);
  }
  const demoLoad = () => { try { const value=JSON.parse(localStorage.getItem(key) || '[]'); return Array.isArray(value) ? value : []; } catch { return []; } };
  const demoSave = rows => localStorage.setItem(key,JSON.stringify(rows));
  async function api(path='', method='GET', body) {
    if(preview) {
      let rows=demoLoad();
      if(path==='/users') return [{id:2,name:'Мария Садыкова'},{id:3,name:'Данияр Омаров'},{id:4,name:'Алия Садыкова'}].filter(p=>p.name.toLowerCase().includes(body.query.toLowerCase()));
      if(method==='GET') return rows;
      if(path==='') { const p={id:Date.now(),title:body.title.trim(),description:body.description.trim(),owner:true,members:[{id:1,name:localStorage.getItem('studhub-preview-name') || 'Алмаз'}]}; rows.unshift(p); demoSave(rows); return p; }
      const p=rows.find(p=>p.id===Number(path)); if(!p) throw Error('Проект не найден.');
      if(!p.members.some(m=>m.id===body.userId)) p.members.push(body.person); demoSave(rows); return p;
    }
    const headers={}; if(body) headers['Content-Type']='application/json';
    const token=document.querySelector('meta[name="_csrf"]')?.content, name=document.querySelector('meta[name="_csrf_header"]')?.content;
    if(token && name) headers[name]=token;
    const url=path==='/users' ? `/api/chat/users?q=${encodeURIComponent(body.query)}` : `/api/projects${path}`;
    try {
      const response = await fetch(url, {
        method: path === '/users' ? 'GET' : method,
        credentials: 'same-origin',
        headers,
        body: body && path !== '/users' ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(15000)
      });
      if (!response.ok || response.redirected) throw new Error();
      return await response.json();
    } catch {
      throw new Error('Не удалось выполнить действие. Проверьте соединение и повторите попытку.');
    }
  }
  function draw(mode='') {
    if(!root?.isConnected) return;
    root.innerHTML=`<div class="project-page"><div class="project-heading"><div><p class="eyebrow">STUDHUB</p><h2>Проекты</h2><p class="muted-copy">Создавайте проекты и собирайте участников.</p></div><button class="btn btn-primary" type="button" data-project-new><i class="bi bi-plus-lg"></i> Создать проект</button></div>${preview ? '<p class="settings-demo-note">Предпросмотр: проекты сохраняются только в этом браузере.</p>' : ''}<p class="project-error" data-project-error role="alert" hidden></p><p class="project-feedback muted-copy" role="status" data-project-feedback></p><section class="surface project-form" ${mode==='new' ? '' : 'hidden'}><h3>Новый проект</h3><form data-project-form><label>Название проекта<input name="title" required maxlength="120"></label><label>Описание<textarea name="description" maxlength="1000" rows="3"></textarea></label><div><button class="btn btn-primary" type="submit">Создать проект</button> <button class="btn btn-light" type="button" data-project-cancel>Отмена</button></div></form></section>${invitations.length?`<section class="surface project-card"><h3>Приглашения в проекты</h3>${invitations.map(p=>`<div class="project-invitation"><strong>${esc(p.title)}</strong><span>Владелец · SH-${esc(p.ownerId)}${p.closed?' · Проект закрыт':''}</span><button type="button" class="btn btn-primary" data-invitation-accept="${p.id}" ${p.closed?'disabled':''}>Принять</button><button type="button" class="btn btn-light" data-invitation-decline="${p.id}">Отклонить</button></div>`).join('')}</section>`:''}<div data-project-list>${projects.length ? projects.map(p=>`<section class="surface project-card"><h3>${esc(p.title)}</h3><span class="status-badge">${p.closed?'Закрыт':'Открыт'}</span>${preview ? '' : `<a class="project-open" href="/projects/${encodeURIComponent(p.id)}">Открыть проект <span aria-hidden="true">→</span></a>`}<p>${esc(p.description || 'Описание не добавлено')}</p><strong>Участники · ${p.members.length}</strong><div class="project-members">${p.members.map(m=>`<span>${esc(m.name)}${m.id===p.ownerId?' · Владелец':' · Участник'}</span>`).join('')}</div>${p.owner && !p.closed ? `<details class="project-add" ${mode==='members' ? 'open' : ''}><summary>Пригласить участника</summary><p>Приглашённый увидит запрос в разделе «Проекты».</p><form data-project-search="${p.id}"><input name="query" minlength="2" maxlength="80" required placeholder="Имя, никнейм или SH-ID" aria-label="Имя, никнейм или SH-ID"><button class="btn btn-light" type="submit">Найти</button></form><div data-project-results="${p.id}" aria-live="polite"></div></details>` : ''}</section>`).join('') : '<section class="surface project-card"><p>Проектов пока нет. Создайте первый проект, чтобы добавить участников.</p></section>'}</div></div>`;
  }
  function showError(error) {
    const box = root.querySelector('[data-project-error]');
    box.textContent = error.message;
    box.hidden = false;
  }

  async function mount(mode = '', restoreFocus = false) {
    root = document.querySelector('[data-project-root]');
    if (!root) return;
    root.innerHTML = '<p class="list-feedback muted-copy" role="status">Загружаем проекты…</p>';
    try {
      [projects, invitations] = await Promise.all([api(), preview ? [] : api('/invitations')]);
      draw(mode);
      if (restoreFocus && document.activeElement === document.body) {
        root.querySelector(mode === 'new' ? '[name="title"]' : '[data-project-new]')?.focus();
      }
    } catch {
      root.innerHTML = '<div class="surface project-card"><p role="alert">Не удалось загрузить проекты. Проверьте подключение и попробуйте ещё раз.</p><button class="btn btn-light" type="button" data-project-retry>Повторить</button></div>';
      const retry = root.querySelector('[data-project-retry]');
      retry.addEventListener('click', () => mount(mode, true));
      if (restoreFocus && document.activeElement === document.body) retry.focus();
    }
  }
  document.addEventListener('click',async event=>{
    if(!root?.isConnected) return;
    if(event.target.closest('[data-project-new]')) { draw('new'); root.querySelector('[name="title"]')?.focus(); }
    if (event.target.closest('[data-project-cancel]')) {
      draw();
      root.querySelector('[data-project-new]')?.focus();
    }
    const invitation = event.target.closest('[data-invitation-accept],[data-invitation-decline]');
    if (invitation) {
      const hadFocus = document.activeElement === invitation;
      invitation.disabled = true;
      try {
        const accept = invitation.hasAttribute('data-invitation-accept');
        const id = accept ? invitation.dataset.invitationAccept : invitation.dataset.invitationDecline;
        const project = await api(`/${id}/invitation/${accept ? 'accept' : 'decline'}`, 'POST');
        if (accept) projects.unshift(project);
        invitations = invitations.filter(item => String(item.id) !== id);
        draw();
        root.querySelector('[data-project-feedback]').textContent = accept ? 'Приглашение принято.' : 'Приглашение отклонено.';
        if (hadFocus && document.activeElement === document.body) root.querySelector('[data-project-new]')?.focus();
      } catch (error) {
        showError(error);
      } finally {
        invitation.disabled = false;
        if (hadFocus && invitation.isConnected && document.activeElement === document.body) invitation.focus();
      }
      return;
    }
    const button=event.target.closest('[data-project-add]');
    if(button) {
      const hadFocus = document.activeElement === button;
      button.disabled = true;
      try {
        const person = JSON.parse(button.dataset.person);
        const id = button.dataset.project;
        const project = await api(preview ? id : `/${id}/members`, 'POST', {userId: person.id, person});
        projects = projects.map(item => String(item.id) === id ? project : item);
        draw('members');
        root.querySelector('[data-project-feedback]').textContent = 'Приглашение отправлено. Ожидаем принятия.';
        if (hadFocus && document.activeElement === document.body) {
          root.querySelector(`[data-project-search="${CSS.escape(id)}"] input`)?.focus();
        }
      } catch (error) {
        showError(error);
      } finally {
        button.disabled = false;
        if (hadFocus && button.isConnected && document.activeElement === document.body) button.focus();
      }
    }
  });
  document.addEventListener('submit',async event=>{
    const form=event.target.closest('[data-project-form],[data-project-search]'); if(!form || !root?.contains(form)) return;
    event.preventDefault();
    const button = form.querySelector('button[type=submit]');
    const hadFocus = form.contains(document.activeElement);
    const results = form.hasAttribute('data-project-search') ? form.nextElementSibling : null;
    root.querySelector('[data-project-error]').hidden = true;
    root.querySelector('[data-project-feedback]').textContent = '';
    form.setAttribute('aria-busy', 'true');
    if (results) {
      results.textContent = 'Ищем участников…';
    }
    button.disabled = true;
    try {
      if (!results) {
        const values = Object.fromEntries(new FormData(form));
        if (!values.title.trim()) throw new Error('Укажите название проекта.');
        const project = await api('', 'POST', values);
        projects.unshift(project);
        draw();
        root.querySelector('[data-project-feedback]').textContent = 'Проект создан.';
        if (hadFocus && document.activeElement === document.body) root.querySelector('[data-project-new]')?.focus();
      } else {
        const people = await api('/users', 'GET', {query: form.elements.query.value.trim()});
        const project = projects.find(item => String(item.id) === form.dataset.projectSearch);
        const matches = people.filter(person => !project.members.some(member => member.id === person.id));
        results.innerHTML = matches.length
          ? matches.map(person => `<button class="btn btn-light" type="button" data-project-add data-project="${project.id}" data-person="${esc(JSON.stringify(person))}">${esc(person.name)} · SH-${esc(person.id)} · Пригласить</button>`).join(' ')
          : '<p class="muted-copy">Участники не найдены. Поиск доступен по открытым профилям.</p>';
      }
    } catch (error) {
      if (results) results.replaceChildren();
      showError(error);
    } finally {
      form.removeAttribute('aria-busy');
      button.disabled = false;
      if (hadFocus && button.isConnected && document.activeElement === document.body) button.focus();
    }
  });
  window.StudHubProjects={mount,dashboard}; dashboard();
  document.addEventListener('studhub:preview-reset', () => { if(preview) localStorage.removeItem(key); });
  if(preview && /^#projects(?:-(new|members))?$/.test(location.hash)) window.previewBaseRender(location.hash.slice(1));
  if(document.querySelector('[data-project-root]')) mount(location.hash.slice(1));
})();
