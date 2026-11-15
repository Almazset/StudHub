(() => {
  'use strict';
  const i18n = window.StudHubI18n;
  const t = (key, vars) => i18n.t(key, vars);
  const preview = location.pathname.endsWith('studhub-preview.html');
  const main = document.getElementById('main-content');
  const sections = ['appearance', 'profile', 'privacy', 'about'];
  const icons = {appearance:'palette', profile:'person-circle', privacy:'shield-check', about:'info-circle'};
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const demoKey = 'studhub-preview-settings-v1';
  let data, section = 'appearance', enrollment = null, recoveryCodes = null;
  let appearanceQueue = Promise.resolve();
  const errorKeys = {password:'errorPassword',emailUsed:'errorEmail',passwordMismatch:'errorMismatch',passwordLength:'errorLength',passwordSame:'errorSame',code:'errorCode',photoSize:'errorPhotoSize',photoFormat:'errorPhotoFormat',enrollmentExpired:'errorExpired',validation:'errorValidation',supportLimit:'errorSupportLimit',session:'sessionExpired',alreadyEnabled:'enabled'};
  function errorText(error) {
    if (error?.retryAfter) return t('errorRateLimit', {seconds: error.retryAfter});
    const key = error?.message || '';
    return key.startsWith('settings.error.') ? t(errorKeys[key.slice(15)] || 'failed') : t(key === 'sessionExpired' ? key : 'failed');
  }
  async function api(path = '', method = 'GET', payload) {
    const headers = {};
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (token && header) headers[header] = token;
    const multipart = payload instanceof FormData;
    if (payload && !multipart) headers['Content-Type'] = 'application/json';
    const response = await fetch(`/api/settings${path}`, {method, credentials:'same-origin', cache:'no-store', headers, body:payload ? multipart ? payload : JSON.stringify(payload) : undefined});
    if (response.redirected || response.status === 401 || response.headers.get('content-type')?.includes('text/html')) throw new Error('sessionExpired');
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(body.error || 'failed');
      if (response.status === 429) error.retryAfter = Math.max(1, Number(response.headers.get('Retry-After')) || 60);
      throw error;
    }
    return body;
  }
  function demoUsers() {
    try { return JSON.parse(localStorage.getItem('studhub-preview-data-v2') || 'null'); } catch { return null; }
  }
  function loadDemo() {
    let saved;
    try { saved = JSON.parse(localStorage.getItem(demoKey) || '{}'); } catch { saved = {}; }
    const users = demoUsers()?.users || [];
    const currentName = localStorage.getItem('studhub-preview-name') || 'Алмаз';
    const user = users.find(user => user.name === currentName) || users[0];
    return {
      profile:{id:user?.id || 1, name:currentName, nickname:saved.profile?.nickname || '', email:saved.profile?.email || user?.email || 'almaz@studhub.local', hasPhoto:Boolean(saved.photo), admin:false},
      appearance:saved.appearance || {theme:localStorage.getItem('studhub-theme') || 'light',fontSize:localStorage.getItem('studhub-font-size') || 'normal',language:i18n.language},
      privacy:saved.privacy || {visibility:'private'}, security:{twoFactorEnabled:false}, sessions:[], support:saved.support || [], version:'0.0.1', photo:saved.photo || null
    };
  }
  function saveDemo() {
    localStorage.setItem(demoKey, JSON.stringify({profile:data.profile, appearance:data.appearance,privacy:data.privacy,support:data.support,photo:data.photo}));
  }
  function applyAppearance(value) {
    const theme = value.theme === 'light' ? 'light' : 'dark';
    document.body.dataset.theme = theme; document.body.dataset.bsTheme = theme;
    document.documentElement.dataset.theme = theme;
    window.StudHubTheme?.set(theme);
    document.documentElement.dataset.fontSize = ['small','normal','large'].includes(value.fontSize) ? value.fontSize : 'normal';
    localStorage.setItem('studhub-theme', theme); localStorage.setItem('studhub-font-size', value.fontSize);
    if (i18n.language !== value.language) i18n.setLanguage(value.language);
  }
  function updateIdentity() {
    if (!data) return;
    if (preview) {
      const name = localStorage.getItem('studhub-preview-name') || 'Алмаз';
      const user = demoUsers()?.users.find(user => user.name === name);
      if (user) { data.profile.id = user.id; data.profile.email = user.email; }
      data.profile.name = name;
    }
    const displayName = data.profile.nickname || data.profile.name;
    document.querySelectorAll('[data-preview-name]').forEach(node => node.textContent = data.profile.name);
    document.querySelectorAll('[data-profile-name],.account-meta strong').forEach(node => node.textContent = displayName);
    const avatar = document.querySelector('[data-profile-avatar]');
    if (avatar) {
      avatar.replaceChildren();
      if (data.profile.hasPhoto) {
        const image = document.createElement('img'); image.src = preview ? data.photo : `/api/settings/photo?v=${Date.now()}`; image.alt = ''; avatar.append(image);
      } else avatar.textContent = displayName.trim().slice(0,1).toLocaleUpperCase(i18n.language);
    }
  }
  function setupMenu() {
    const menu = document.getElementById('topbarSettings');
    if (!menu) return;
    const reset = menu.querySelector('[data-preview-reset]');
    const logout = menu.querySelector('[data-settings-logout]');
    menu.innerHTML = `<h2>${t('settings')}</h2>${sections.map(key => `<a class="popover-action settings-menu-link" href="${preview ? `#settings-${key}` : `/settings#${key}`}" ${preview ? `data-preview-route="settings-${key}"` : ''}><i class="bi bi-${icons[key]}"></i><span><strong>${t(key)}</strong><small>${t(`${key}Hint`)}</small></span><i class="bi bi-chevron-right"></i></a>`).join('')}`;
    if (reset) menu.append(reset);
    if (logout) menu.append(logout);
    const trigger = document.querySelector('[data-panel-trigger="settings"]');
    trigger?.setAttribute('aria-label',t('settings')); trigger?.setAttribute('title',t('settings'));
  }
  let fieldSequence = 0;
  const field = (name, label, type = 'text', value = '', extra = '') => {
    const id = `settings-${name}-${fieldSequence++}`;
    return `<div class="field-group"><label for="${id}">${t(label)}</label><input id="${id}" name="${name}" type="${type}" value="${esc(value)}" ${extra}></div>`;
  };
  const formError = '<p class="settings-form-error" data-form-error role="alert" hidden></p>';
  const saveButton = label => `<button class="btn btn-primary" type="submit">${t(label || 'save')}</button>`;
  const card = (title, hint, content, extra = '') => `<section class="settings-card surface ${extra}"><header><h4>${t(title)}</h4>${hint ? `<p>${t(hint)}</p>` : ''}</header>${content}</section>`;
  const otpField = () => data.security.twoFactorEnabled ? field('code','otp','text','','autocomplete="one-time-code" maxlength="32" required') : '';
  function appearancePage() {
    const prefs = data.appearance;
    return `${card('theme','',`<fieldset class="settings-theme-options"><legend class="visually-hidden">${t('theme')}</legend>${['light','dark'].map(theme => `<label class="settings-theme-option ${prefs.theme === theme ? 'selected' : ''}"><input type="radio" name="theme" value="${theme}" data-preference="theme" ${prefs.theme === theme ? 'checked' : ''}><span class="theme-miniature theme-miniature-${theme}" aria-hidden="true"><span></span><span></span><span></span></span><strong><i class="bi bi-${theme === 'light' ? 'sun' : 'moon-stars'}"></i>${t(theme)}</strong><i class="bi bi-check-circle-fill selection-mark" aria-hidden="true"></i></label>`).join('')}</fieldset>`)}
      ${card('fontSize','',`<fieldset class="settings-font-options"><legend class="visually-hidden">${t('fontSize')}</legend>${['small','normal','large'].map(size => `<label><input type="radio" name="fontSize" value="${size}" data-preference="fontSize" ${prefs.fontSize === size ? 'checked' : ''}><span>${t(size)}</span></label>`).join('')}</fieldset><div class="settings-text-sample"><strong>${t('sample')}</strong><p>${t('sampleBody')}</p></div>`)}
      ${card('language','',`<label class="visually-hidden" for="settingsLanguage">${t('language')}</label><select id="settingsLanguage" data-preference="language"><option value="ru" ${prefs.language === 'ru' ? 'selected' : ''}>Русский</option><option value="kk" ${prefs.language === 'kk' ? 'selected' : ''}>Қазақша</option><option value="en" ${prefs.language === 'en' ? 'selected' : ''}>English</option></select>`)}<p class="settings-hint"><i class="bi bi-check-circle"></i>${t('liveHint')}</p>`;
  }
  function profilePage() {
    const profile = data.profile;
    const image = profile.hasPhoto ? `<img src="${esc(preview ? data.photo : `/api/settings/photo?v=${Date.now()}`)}" alt="${t('photo')}">` : esc((profile.nickname || profile.name).slice(0,1).toLocaleUpperCase(i18n.language));
    return `${card('photo','photoHint',`<div class="settings-photo-row"><span class="settings-avatar">${image}</span><div class="settings-photo-actions"><label class="btn btn-light settings-upload" for="settingsPhoto"><i class="bi bi-upload"></i>${t('upload')}</label><input id="settingsPhoto" type="file" accept="image/jpeg,image/png" class="visually-hidden" data-settings-photo>${profile.hasPhoto ? `<button type="button" class="text-link" data-settings-action="remove-photo">${t('removePhoto')}</button>` : ''}</div></div>`)}
      ${card('profile','',`<div class="settings-user-id"><strong>ID StudHub</strong><span>SH-${esc(profile.id)}</span><button type="button" class="btn btn-light" data-copy-user-id aria-label="Копировать ID">Копировать</button><small>Поделитесь ID, чтобы участники могли найти вас. Для поиска включите видимость профиля.</small><span data-user-id-copy-status role="status"></span></div><form data-settings-form="profile">${field('name','name','text',profile.name,'required minlength="2" maxlength="80" autocomplete="name"')}${field('nickname','nickname','text',profile.nickname || '', 'maxlength="32" placeholder="donk" autocomplete="nickname"')}<p class="settings-hint">${t('nicknameHint')}</p>${field('email','email','email',profile.email,'required maxlength="120" autocomplete="email"')}<p class="settings-hint">${preview ? t('demo') : t('emailHint')}</p>${preview ? '' : `<div data-email-proof hidden>${field('currentPassword','currentPassword','password','','autocomplete="current-password" maxlength="100"')}${otpField()}</div>`}${formError}${saveButton()}</form>`)}
      ${card('changePassword','passwordHint',preview ? `<p class="settings-demo-note"><i class="bi bi-shield-lock"></i>${t('demoSecurity')}</p>` : `<form data-settings-form="password">${field('currentPassword','currentPassword','password','','autocomplete="current-password" required maxlength="100"')}${field('newPassword','newPassword','password','','autocomplete="new-password" required minlength="8" maxlength="72"')}${field('confirmPassword','confirmPassword','password','','autocomplete="new-password" required minlength="8" maxlength="72"')}${otpField()}${formError}${saveButton('updatePassword')}</form>`)}`;
  }
  function factorContent() {
    if (preview) return `<p class="settings-demo-note"><i class="bi bi-info-circle"></i>${t('demoSecurity')}</p>`;
    if (recoveryCodes) return `<div class="settings-recovery"><h5>${t('recovery')}</h5><p>${t('recoveryHint')}</p><div class="settings-recovery-codes">${recoveryCodes.map(code => `<code>${esc(code)}</code>`).join('')}</div><div class="settings-actions"><button class="btn btn-light" type="button" data-settings-action="download-codes">${t('downloadCodes')}</button><button class="btn btn-primary" type="button" data-settings-action="finish-factor">${t('done')}</button></div></div>`;
    if (enrollment) return `<p>${t('setupHint')}</p><label class="settings-secret-label" for="settingsSecret">${t('secret')}</label><input class="settings-secret" id="settingsSecret" readonly value="${esc(enrollment)}" spellcheck="false"><form data-settings-form="enable-factor">${field('code','sixDigits','text','','required inputmode="numeric" pattern="[0-9]{6}" maxlength="6" autocomplete="one-time-code"')}${formError}<div class="settings-actions">${saveButton('confirmEnable')}<button type="button" class="btn btn-light" data-settings-action="cancel-factor">${t('cancel')}</button></div></form>`;
    return `<span class="settings-state ${data.security.twoFactorEnabled ? 'enabled' : ''}"><i class="bi bi-${data.security.twoFactorEnabled ? 'shield-check' : 'shield'}"></i>${t(data.security.twoFactorEnabled ? 'enabled' : 'disabled')}</span><details class="settings-factor-details"><summary>${t(data.security.twoFactorEnabled ? 'disable' : 'enable')}</summary><form data-settings-form="${data.security.twoFactorEnabled ? 'disable-factor' : 'start-factor'}">${field('password','currentPassword','password','','required autocomplete="current-password" maxlength="100"')}${otpField()}${formError}${saveButton(data.security.twoFactorEnabled ? 'disable' : 'authenticate')}</form></details>`;
  }
  function sessionList() {
    if (preview) return `<p class="settings-demo-note"><i class="bi bi-window"></i>${t('demoSecurity')}</p>`;
    const others = data.sessions.some(session => !session.current);
    return `<div class="settings-session-list">${data.sessions.map(session => `<article class="settings-session"><span class="settings-device-icon"><i class="bi bi-laptop"></i></span><div><strong>${esc(session.device)}</strong><small>${t('lastActive')}: ${formatDate(session.lastActive)}</small>${session.current ? `<span class="settings-current">${t('currentSession')}</span>` : ''}</div>${session.current ? '' : `<button type="button" class="btn btn-light" data-settings-action="revoke-session" data-reference="${esc(session.reference)}">${t('revoke')}</button>`}</article>`).join('')}</div>${others ? `<button class="btn btn-light" type="button" data-settings-action="revoke-others">${t('revokeOthers')}</button>` : `<p class="settings-hint">${t('noOtherSessions')}</p>`}`;
  }
  function privacyPage() {
    return `${card('visibility','visibilityHint',`<label for="settingsVisibility" class="visually-hidden">${t('visibility')}</label><select id="settingsVisibility" data-settings-visibility><option value="private" ${data.privacy.visibility === 'private' ? 'selected' : ''}>${t('private')}</option><option value="members" ${data.privacy.visibility === 'members' ? 'selected' : ''}>${t('members')}</option></select>`)}${card('twoFactor','twoFactorHint',factorContent())}${card('sessions','sessionsHint',sessionList())}`;
  }
  const formatDate = value => new Intl.DateTimeFormat(i18n.language,{dateStyle:'medium',timeStyle:'short'}).format(new Date(value));
  function tickets(items, inbox = false) {
    return items.length ? items.map(ticket => `<details class="settings-ticket"><summary><span><strong>#${esc(ticket.id)} · ${esc(ticket.subject)}</strong><small>${formatDate(ticket.createdAt)}</small></span><span class="settings-state">${t(ticket.closed ? 'closed' : 'open')}</span></summary><p class="settings-ticket-copy">${esc(ticket.message)}</p>${inbox && !ticket.closed ? `<button class="btn btn-light" data-settings-action="close-ticket" data-id="${ticket.id}" type="button">${t('closeRequest')}</button>` : ''}</details>`).join('') : `<p class="settings-hint">${t('noRequests')}</p>`;
  }
  function aboutPage() {
    return `${card('faq','', ['Course','Save','Factor'].map(key => `<details class="settings-faq"><summary>${t(`faq${key}Q`)}</summary><p>${t(`faq${key}A`)}</p></details>`).join(''))}
      ${card('support',preview ? 'demoSupport' : 'supportHint',`<form data-settings-form="support">${field('subject','subject','text','','required minlength="3" maxlength="120"')}<div class="field-group"><label for="supportMessage">${t('message')}</label><textarea id="supportMessage" name="message" required minlength="10" maxlength="2000" rows="5"></textarea></div>${formError}${saveButton('send')}</form><h5 class="settings-subheading">${t('myRequests')}</h5>${tickets(data.support)}${data.profile.admin ? `<div class="settings-admin-inbox"><button class="btn btn-light" type="button" data-settings-action="load-inbox">${t('adminInbox')}</button><div data-support-inbox></div></div>` : ''}`)}
      ${card('terms','',`<p>${t('termsIntro')}</p><p>${t('termsContent')}</p><p>${t('termsSecurity')}</p>`)}<div class="settings-version"><span class="brand-mark">S</span><span><strong>StudHub</strong><small>${t('version')} ${esc(data.version)}${preview ? ` · ${t('demo')}` : ''}</small></span></div>`;
  }
  function draw(focus = false) {
    const root = document.querySelector('[data-settings-root]');
    if (!root || !data) return;
    fieldSequence = 0;
    const render = {appearance:appearancePage,profile:profilePage,privacy:privacyPage,about:aboutPage};
    root.innerHTML = `<section class="page-intro settings-heading"><div><p class="eyebrow">STUDHUB</p><h2>${t('settings')}</h2><p class="muted-copy">${t('subtitle')}</p></div>${preview ? `<span class="settings-preview-badge">${t('demo')}</span>` : ''}</section><div class="settings-layout"><nav class="settings-nav" aria-label="${t('settings')}">${sections.map(key => `<button type="button" data-settings-section="${key}" ${section === key ? 'aria-current="page"' : ''}><i class="bi bi-${icons[key]}"></i><span>${t(key)}</span><i class="bi bi-chevron-right"></i></button>`).join('')}</nav><div class="settings-content"><header class="settings-section-heading"><h3 tabindex="-1">${t(section)}</h3><p>${t(`${section}Hint`)}</p></header><div class="settings-notice" data-settings-notice role="status" hidden></div>${render[section]()}</div></div>`;
    if (focus) root.querySelector('.settings-section-heading h3')?.focus({preventScroll:true});
  }
  function notice(message, error = false) {
    const region = document.querySelector('[data-settings-notice]');
    if (!region) return;
    region.hidden = false; region.textContent = message; region.classList.toggle('is-error',error); region.setAttribute('role',error ? 'alert' : 'status');
  }
  async function reload() { data = preview ? loadDemo() : await api(); applyAppearance(data.appearance); updateIdentity(); return data; }
  let initial = null;
  async function ensureLoaded() {
    if (!initial) initial = reload().catch(error => { initial = null; throw error; });
    return initial;
  }
  async function open(requested = 'appearance') {
    const next = requested.replace(/^settings-/, '');
    section = sections.includes(next) ? next : 'appearance';
    if (preview) {
      main.innerHTML = '<div data-settings-root></div>';
      document.body.dataset.page = 'settings';
      document.querySelectorAll('[data-preview-title]').forEach(node => node.textContent = t('settings'));
      document.querySelectorAll('.sidebar-link.active').forEach(node => node.classList.remove('active'));
      history.replaceState(null,'',`#settings-${section}`);
    } else history.replaceState(null,'',`#${section}`);
    const root = document.querySelector('[data-settings-root]');
    if (!root) return;
    root.innerHTML = `<p class="settings-loading" role="status">${t('loading')}</p>`;
    document.querySelectorAll('[data-panel]').forEach(panel => { panel.hidden = true; });
    document.querySelectorAll('[data-panel-trigger]').forEach(trigger => trigger.setAttribute('aria-expanded','false'));
    try { await ensureLoaded(); draw(true); } catch (error) {
      root.innerHTML = `<div class="settings-load-error" role="alert"><p>${errorText(error)}</p><button type="button" class="btn btn-light" data-settings-action="retry">${t('retry')}</button></div>`;
    }
  }
  window.StudHubSettings = {open, refreshIdentity:updateIdentity};

  document.addEventListener('input', event => {
    const form = event.target.closest('[data-settings-form="profile"]');
    if (!form) return;
    const proof = form.querySelector('[data-email-proof]');
    if (proof) {
      const changed = form.elements.email.value.trim().toLowerCase() !== data.profile.email.toLowerCase();
      proof.hidden = !changed;
      proof.querySelectorAll('input').forEach(input => input.required = changed);
    }
  });
  document.addEventListener('change', async event => {
    const preference = event.target.closest('[data-preference]');
    if (preference) {
      data.appearance = {...data.appearance,[preference.dataset.preference]:preference.value};
      const snapshot = {...data.appearance};
      applyAppearance(snapshot); setupMenu(); draw(); notice(t('saving'));
      document.querySelector(`[data-preference="${preference.dataset.preference}"]${preference.type === 'radio' ? `[value="${preference.value}"]` : ''}`)?.focus({preventScroll:true});
      appearanceQueue = appearanceQueue.catch(() => {}).then(async () => {
        if (preview) saveDemo(); else await api('/appearance','PUT',snapshot);
      }).then(() => notice(t('saved')),error => notice(errorText(error),true));
      return;
    }
    if (event.target.matches('[data-settings-visibility]')) {
      const previous = data.privacy.visibility;
      data.privacy.visibility = event.target.value;
      try { if (preview) saveDemo(); else await api('/privacy','PUT',data.privacy); notice(t('saved')); }
      catch (error) { data.privacy.visibility = previous; event.target.value = previous; notice(errorText(error),true); }
    }
    if (event.target.matches('[data-settings-photo]')) {
      const file = event.target.files[0];
      if (!file) return;
      try {
        if (file.size > 2 * 1024 * 1024) throw new Error('settings.error.photoSize');
        if (!['image/png','image/jpeg'].includes(file.type)) throw new Error('settings.error.photoFormat');
        notice(t('saving'));
        if (preview) { data.photo = await previewPhoto(file); data.profile.hasPhoto = true; saveDemo(); updateIdentity(); }
        else { const upload = new FormData(); upload.append('photo',file); await api('/photo','POST',upload); await reload(); }
        draw(); notice(t('saved'));
      } catch (error) { notice(errorText(error),true); }
    }
  });
  document.addEventListener('submit', async event => {
    const form = event.target.closest('[data-settings-form]');
    if (!form) return;
    event.preventDefault(); event.stopPropagation();
    if (!form.reportValidity()) return;
    const values = Object.fromEntries(new FormData(form));
    const type = form.dataset.settingsForm;
    const buttons = [...form.querySelectorAll('button')]; buttons.forEach(button => button.disabled = true);
    form.setAttribute('aria-busy','true'); form.querySelector('[data-form-error]').hidden = true;
    try {
      if (type === 'profile') {
        if (values.name.trim().length < 2) throw new Error('settings.error.validation');
        if (preview) {
          const demo = demoUsers();
          if (demo?.users.some(user => user.id !== data.profile.id && user.email.toLowerCase() === values.email.trim().toLowerCase())) throw new Error('settings.error.emailUsed');
          data.profile.name = values.name.trim(); data.profile.nickname = values.nickname.trim(); data.profile.email = values.email.trim();
          localStorage.setItem('studhub-preview-name',data.profile.name);
          window.previewApp?.updateProfile(data.profile);
          saveDemo(); updateIdentity();
        } else {
          const result = await api('/profile','PUT',values);
          if (result.reauthenticate) { location.assign('/login?updated'); return; }
          await reload();
        }
      } else if (type === 'password') {
        if (values.newPassword !== values.confirmPassword) throw new Error('settings.error.passwordMismatch');
        await api('/password','POST',values); await reload();
      } else if (type === 'start-factor') {
        const result = await api('/two-factor/start','POST',values); enrollment = result.secret;
      } else if (type === 'enable-factor') {
        const result = await api('/two-factor/enable','POST',values); enrollment = null; recoveryCodes = result.recoveryCodes; await reload();
      } else if (type === 'disable-factor') {
        await api('/two-factor','DELETE',values); await reload();
      } else if (type === 'support') {
        let result;
        if (preview) {
          if (values.subject.trim().length < 3 || values.message.trim().length < 10) throw new Error('settings.error.validation');
          result = {id:Math.max(0,...data.support.map(ticket => ticket.id)) + 1,subject:values.subject.trim(),message:values.message.trim(),createdAt:new Date().toISOString(),closed:false};
          data.support.unshift(result); data.support = data.support.slice(0,20); saveDemo();
        } else { result = await api('/support','POST',values); await reload(); }
        draw(); notice(t('ticketCreated',{id:result.id})); return;
      }
      draw();
      if (type === 'profile') notice(t('saved'));
      if (type === 'password') notice(t('passwordUpdated'));
      if (type === 'disable-factor') notice(t('saved'));
    } catch (error) {
      const region = form.querySelector('[data-form-error]'); region.hidden = false; region.textContent = errorText(error);
    } finally { form.removeAttribute('aria-busy'); buttons.forEach(button => button.disabled = false); }
  },true);
  document.addEventListener('click', async event => {
    const copyId = event.target.closest('[data-copy-user-id]');
    if (copyId) {
      const status = document.querySelector('[data-user-id-copy-status]');
      try { await navigator.clipboard.writeText(`SH-${data.profile.id}`); status.textContent = 'ID скопирован'; }
      catch { status.textContent = 'Не удалось скопировать. Выделите ID вручную.'; }
      return;
    }
    const nav = event.target.closest('[data-settings-section]');
    if (nav) { section = nav.dataset.settingsSection; history.replaceState(null,'',`#${preview ? 'settings-' : ''}${section}`); draw(true); return; }
    const action = event.target.closest('[data-settings-action]');
    if (!action) return;
    action.disabled = true;
    try {
      const kind = action.dataset.settingsAction;
      if (kind === 'retry') { await open(section); return; }
      if (kind === 'remove-photo') {
        if (preview) { data.photo = null; data.profile.hasPhoto = false; saveDemo(); updateIdentity(); } else { await api('/photo','DELETE'); await reload(); }
      }
      if (kind === 'cancel-factor') enrollment = null;
      if (kind === 'finish-factor') recoveryCodes = null;
      if (kind === 'download-codes') { download('studhub-recovery-codes.txt',`StudHub\n${data.profile.email}\n\n${recoveryCodes.join('\n')}`); return; }
      if (kind === 'revoke-session') { await api(`/sessions/${action.dataset.reference}`,'DELETE'); await reload(); }
      if (kind === 'revoke-others') { await api('/sessions','DELETE'); await reload(); }
      if (kind === 'load-inbox' || kind === 'close-ticket') {
        if (kind === 'close-ticket') await api(`/support/${action.dataset.id}/close`,'POST');
        const inbox = await api('/support/inbox');
        document.querySelector('[data-support-inbox]').innerHTML = tickets(inbox,true); return;
      }
      draw(); if (kind !== 'cancel-factor' && kind !== 'finish-factor') notice(t('saved'));
    } catch (error) { notice(errorText(error),true); }
    finally { action.disabled = false; }
  });
  function download(filename, value) {
    const url = URL.createObjectURL(new Blob([value],{type:'text/plain;charset=utf-8'}));
    const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); setTimeout(() => URL.revokeObjectURL(url),1000);
  }
  async function previewPhoto(file) {
    if (file.size > 2 * 1024 * 1024) throw new Error('settings.error.photoSize');
    if (!['image/png','image/jpeg'].includes(file.type)) throw new Error('settings.error.photoFormat');
    const image = await createImageBitmap(file).catch(() => { throw new Error('settings.error.photoFormat'); });
    if (image.width > 4096 || image.height > 4096 || image.width * image.height > 16_000_000) { image.close(); throw new Error('settings.error.photoSize'); }
    const canvas = document.createElement('canvas'); canvas.width = 256; canvas.height = 256;
    const side = Math.min(image.width,image.height);
    canvas.getContext('2d').drawImage(image,(image.width-side)/2,(image.height-side)/2,side,side,0,0,256,256); image.close();
    return canvas.toDataURL('image/png');
  }
  document.addEventListener('studhub:language',() => { setupMenu(); if (document.querySelector('[data-settings-root]') && data) draw(); });
  document.addEventListener('studhub:preview-reset',() => { localStorage.removeItem(demoKey); data = loadDemo(); initial = Promise.resolve(data); updateIdentity(); });
  setupMenu();
  applyAppearance({theme:localStorage.getItem('studhub-theme') || 'light',fontSize:localStorage.getItem('studhub-font-size') || 'normal',language:i18n.language});
  const requested = window.studhubPendingSettings || (preview ? location.hash.match(/^#settings-(\w+)/)?.[1] : document.querySelector('[data-settings-root]') ? location.hash.slice(1) || 'appearance' : null);
  if (requested) open(requested);
  else if (preview || document.body.classList.contains('app-shell')) ensureLoaded().catch(() => {});
})();
