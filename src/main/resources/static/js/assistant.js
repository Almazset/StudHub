(() => {
  'use strict';
  const root = document.querySelector('[data-study-assistant]');
  if (!root) return;
  const t = key => window.StudHubTutorCopy.t(key);
  const preview = location.pathname.endsWith('studhub-preview.html');
  const modes = ['summary','flashcards','quiz','exam','coach','explain','tasks','guide'];
  const icon = {summary:'file-earmark-text',flashcards:'cards',quiz:'question-circle',exam:'calendar-check',coach:'person-workspace',explain:'lightbulb',tasks:'list-check',guide:'compass'};
  root.dataset.userContent = '';
  root.innerHTML = `<div class="assistant-panel tutor-panel" id="studhubTutor" data-assistant-panel role="dialog" aria-modal="false" aria-labelledby="tutorHeading" hidden>
    <header class="assistant-header"><span class="assistant-avatar"><i class="bi bi-stars" aria-hidden="true"></i></span><div><strong id="tutorHeading" data-ai-label="title"></strong><small data-ai-label="subtitle"></small></div><button type="button" class="assistant-close" data-ai-action="clear" data-ai-title="newChat"><i class="bi bi-arrow-counterclockwise"></i></button><button type="button" class="assistant-close tutor-expand" data-ai-action="expand" data-ai-title="expand"><i class="bi bi-arrows-fullscreen"></i></button><button type="button" class="assistant-close" data-ai-action="close" data-ai-title="close"><i class="bi bi-x-lg"></i></button></header>
    <div class="tutor-modes" role="tablist" data-ai-title="title">${modes.map(mode => `<button type="button" role="tab" id="tutorTab-${mode}" aria-controls="tutorContent" data-ai-mode="${mode}"><i class="bi bi-${icon[mode]}" aria-hidden="true"></i><span data-ai-label="${mode}"></span></button>`).join('')}</div>
    <div class="tutor-connection" data-ai-status role="status"></div>
    <div class="tutor-context"><p data-ai-hint></p><div class="tutor-upload" data-ai-drop><label for="tutorFiles" class="tutor-upload-button"><i class="bi bi-paperclip"></i><span data-ai-label="attach"></span></label><span class="tutor-drop-copy" data-ai-label="drop"></span><input id="tutorFiles" type="file" multiple accept=".pdf,.docx,.pptx,.txt,.md,.jpg,.jpeg,.png" class="visually-hidden" data-ai-files><small data-ai-label="formats"></small><small data-ai-label="limits"></small></div><div class="tutor-files" data-ai-selected-files></div></div>
    <div id="tutorContent" class="assistant-messages" role="log" aria-live="polite" aria-relevant="additions" data-assistant-messages></div>
    <div class="tutor-error" data-ai-error role="alert" hidden><span></span><button type="button" class="tutor-text-button" data-ai-action="retry" data-ai-label="retry" hidden></button></div>
    <form class="assistant-form tutor-composer" data-assistant-form><label class="visually-hidden" for="tutorInput" data-ai-label="input"></label><textarea id="tutorInput" name="message" rows="2" maxlength="6000" data-ai-input></textarea><div class="tutor-composer-actions"><small data-ai-count>0 / 6000</small><button type="button" class="tutor-stop" data-ai-action="stop" data-ai-label="stop" hidden></button><button type="submit" class="tutor-send" data-ai-title="send"><i class="bi bi-arrow-up"></i><span data-ai-label="send"></span></button></div><small class="tutor-disclosure" data-ai-label="disclosure"></small></form>
  </div><button type="button" class="assistant-launcher" data-assistant-open aria-controls="studhubTutor" aria-expanded="false" data-ai-title="open"><i class="bi bi-stars" aria-hidden="true"></i><span data-ai-label="title"></span></button>`;
  const panel = root.querySelector('[data-assistant-panel]');
  const launcher = root.querySelector('[data-assistant-open]');
  const input = root.querySelector('[data-ai-input]');
  const fileInput = root.querySelector('[data-ai-files]');
  const messages = root.querySelector('[data-assistant-messages]');
  const form = root.querySelector('form');
  const errorBox = root.querySelector('[data-ai-error]');
  let mode = 'summary', files = [], history = [], configured = false, connection = 'checking', busy = false;
  let controller, sequence = 0, lastFailure, statusPromise, guideShown = false;
  const node = (tag,className,text) => { const result = document.createElement(tag); if (className) result.className = className; if (text !== undefined) result.textContent = text; return result; };
  const errorCopy = {NOT_CONFIGURED:'notConfigured',UNAUTHORIZED:'login',FILE_LIMIT:'fileLimit',FILE_FORMAT:'fileFormat',TEXT_LIMIT:'textLimit',EMPTY_MESSAGE:'empty',RATE_LIMIT:'rate',BUSY:'busy',PROVIDER_LIMIT:'rate',PROVIDER_AUTH:'providerAuth',PROVIDER_ERROR:'providerError',TIMEOUT:'timeout',BLOCKED:'blocked',TRUNCATED:'truncated',INVALID_RESPONSE:'invalid',VALIDATION:'invalid'};
  function showError(key,retry = false) { errorBox.hidden = false; errorBox.querySelector('span').textContent = t(errorCopy[key] || key || 'providerError'); errorBox.querySelector('button').hidden = !retry; }
  function clearError() { errorBox.hidden = true; lastFailure = null; }
  function updateLabels() {
    root.querySelectorAll('[data-ai-label]').forEach(element => element.textContent = t(element.dataset.aiLabel));
    root.querySelectorAll('[data-ai-title]').forEach(element => { element.setAttribute('aria-label',t(element.dataset.aiTitle)); element.setAttribute('title',t(element.dataset.aiTitle)); });
    input.placeholder = t('placeholder'); root.setAttribute('aria-label',t('title'));
    root.querySelector('[data-ai-hint]').textContent = t(`${mode}Hint`);
    root.querySelector('[data-ai-status]').textContent = t(connection);
    updateFiles();
  }
  async function status() {
    if (preview) { connection = 'preview'; configured = false; updateLabels(); return; }
    if (!statusPromise) statusPromise = fetch('/api/assistant/status',{credentials:'same-origin',cache:'no-store'}).then(async response => {
      if (response.redirected || response.headers.get('content-type')?.includes('text/html') || response.status === 401) { connection = 'login'; return; }
      if (!response.ok) throw new Error('connection');
      const result = await response.json(); configured = result.configured === true; connection = configured ? 'ready' : 'notConfigured';
    }).catch(() => { connection = 'providerError'; statusPromise = null; }).finally(updateLabels);
    await statusPromise;
  }
  function open() { panel.hidden = false; root.classList.add('is-open'); launcher.setAttribute('aria-expanded','true'); input.focus(); status(); }
  function close(restore = true) { panel.hidden = true; root.classList.remove('is-open'); launcher.setAttribute('aria-expanded','false'); if (restore) launcher.focus(); }
  function chooseMode(next) {
    if (!modes.includes(next) || busy) return;
    mode = next; clearError();
    root.querySelectorAll('[data-ai-mode]').forEach(button => { const active = button.dataset.aiMode === mode; button.setAttribute('aria-selected',String(active)); button.tabIndex = active ? 0 : -1; });
    messages.setAttribute('aria-labelledby',`tutorTab-${mode}`);
    root.querySelector('[data-ai-hint]').textContent = t(`${mode}Hint`);
    root.querySelector('[data-ai-drop]').hidden = mode === 'guide'; root.querySelector('[data-ai-selected-files]').hidden = mode === 'guide'; root.querySelector('.tutor-disclosure').hidden = mode === 'guide';
    if (mode === 'guide' && !guideShown) { appendGuide(''); guideShown = true; }
  }
  function updateFiles() {
    const region = root.querySelector('[data-ai-selected-files]'); region.replaceChildren();
    files.forEach((file,index) => {
      const size = file.size < 1024*1024 ? `${(file.size/1024).toFixed(1)} KB` : `${(file.size/1024/1024).toFixed(2)} MB`;
      const chip = node('div','tutor-file'); chip.append(node('i','bi bi-file-earmark-text'),node('span','',file.name),node('small','',size));
      const remove = node('button','','×'); remove.type = 'button'; remove.disabled = busy; remove.setAttribute('aria-label',`${t('remove')}: ${file.name}`);
      remove.addEventListener('click',() => { if (!busy) { files.splice(index,1); clearError(); updateFiles(); } }); chip.append(remove); region.append(chip);
    });
  }
  function attach(incoming) {
    if (busy || mode === 'guide') return;
    const merged = [...files];
    for (const file of incoming) {
      if (!/\.(pdf|docx|pptx|txt|md|jpe?g|png)$/i.test(file.name)) { showError('FILE_FORMAT'); return; }
      if (!merged.some(existing => existing.name === file.name && existing.size === file.size && existing.lastModified === file.lastModified)) merged.push(file);
    }
    if (merged.length > 3 || merged.some(file => file.size === 0 || file.size > 8*1024*1024) || merged.reduce((sum,file) => sum+file.size,0) > 12*1024*1024) { showError('FILE_LIMIT'); return; }
    files = merged; clearError(); updateFiles();
  }
  function emptyState() {
    const empty = node('div','tutor-empty'); empty.dataset.aiEmpty = '';
    const title = node('strong','',t('emptyTitle')); title.dataset.aiLabel = 'emptyTitle';
    const help = node('p','',t('emptyHelp')); help.dataset.aiLabel = 'emptyHelp';
    empty.append(node('i','bi bi-journal-bookmark'),title,help); messages.append(empty);
  }
  function appendMessage(text,kind) {
    messages.querySelector('[data-ai-empty]')?.remove();
    const article = node('article',`assistant-message ${kind === 'user' ? 'user-message' : 'bot-message'}`);
    const label = node('small','tutor-message-label',t(kind === 'user' ? 'you' : 'assistant')); label.dataset.aiLabel = kind === 'user' ? 'you' : 'assistant';
    article.append(label,node('div','tutor-message-copy',text));
    messages.append(article); messages.scrollTop = messages.scrollHeight; return article;
  }
  function appendGuide(question) {
    const result = window.StudHubGuide.answer(question);
    const article = appendMessage(result.text,'assistant');
    const actions = node('div','tutor-guide-links');
    result.links.forEach(key => { const link = node('button','',t(key)); link.type = 'button'; link.dataset.aiLabel = key; link.addEventListener('click',() => window.StudHubGuide.navigate(key)); actions.append(link); });
    const tour = node('button','',t('tour')); tour.type = 'button'; tour.dataset.aiLabel = 'tour'; tour.addEventListener('click',() => window.StudHubGuide.tour()); actions.append(tour); article.append(actions);
  }
  function answerActions(article,answer) {
    const actions = node('div','tutor-answer-actions');
    const copy = node('button','',t('copy')); copy.type = 'button'; copy.dataset.aiLabel = 'copy';
    copy.addEventListener('click',async () => { try { await navigator.clipboard.writeText(answer); copy.textContent = t('copied'); setTimeout(() => copy.textContent = t('copy'),1600); } catch { showError('copyError'); } });
    const download = node('button','',t('download')); download.type = 'button'; download.dataset.aiLabel = 'download';
    download.addEventListener('click',() => {
      const url = URL.createObjectURL(new Blob([answer],{type:'text/plain;charset=utf-8'})); const link = node('a'); link.href = url; link.download = `StudHub-${mode}.txt`; link.click(); setTimeout(() => URL.revokeObjectURL(url),1000);
    }); actions.append(copy,download); article.append(actions);
  }
  function appendAnswer(answer) {
    const article = appendMessage(answer.text,'assistant');
    answer.tasks.forEach(task => {
      const card = node('section','tutor-task'); card.append(node('h3','',task.title),node('p','',task.description));
      const add = node('button','',t('addTask')); add.type = 'button'; add.dataset.aiLabel = 'addTask';
      add.addEventListener('click',() => {
        close(false);
        if (preview) window.previewApp.openAssignmentDraft(task);
        else { sessionStorage.setItem('studhub-assistant-draft',JSON.stringify(task)); location.assign('/assignments/new?assistantDraft=1'); }
      }); card.append(add); article.append(card);
    });
    const plain = answer.text + (answer.tasks.length ? '\n\n' + answer.tasks.map(task => `${task.title}\n${task.description}`).join('\n\n') : '');
    answerActions(article,plain); messages.scrollTop = messages.scrollHeight;
  }
  function setBusy(value) {
    busy = value; messages.setAttribute('aria-busy',String(value));
    form.querySelector('[type="submit"]').disabled = value;
    root.querySelector('[data-ai-action="stop"]').hidden = !value;
    fileInput.disabled = value; root.querySelectorAll('[data-ai-mode]').forEach(button => button.disabled = value); updateFiles();
  }
  function validateAnswer(answer) {
    if (!answer || typeof answer.text !== 'string' || !answer.text.trim() || answer.text.length > 20000 || !Array.isArray(answer.tasks) || answer.tasks.length > 5 || answer.tasks.some(task => typeof task.title !== 'string' || task.title.length < 2 || task.title.length > 150 || typeof task.description !== 'string' || task.description.length > 1400 || !task.description.trim())) throw new Error('INVALID_RESPONSE');
    return answer;
  }
  async function sendAttempt(attempt) {
    if (busy) return;
    const id = ++sequence; controller = new AbortController(); clearError(); setBusy(true);
    const pending = appendMessage(t('thinking'),'assistant'); pending.classList.add('tutor-pending');
    const timeout = setTimeout(() => controller.abort('timeout'),90000);
    try {
      const data = new FormData(); data.append('request',new Blob([JSON.stringify({mode:attempt.mode,message:attempt.message,language:window.StudHubI18n?.language || 'ru',history:attempt.history})],{type:'application/json'}));
      attempt.files.forEach(file => data.append('files',file));
      const token = document.querySelector('meta[name="_csrf"]')?.content, header = document.querySelector('meta[name="_csrf_header"]')?.content;
      const response = await fetch('/api/assistant/messages',{method:'POST',credentials:'same-origin',headers:token && header ? {[header]:token} : {},body:data,signal:controller.signal});
      if (response.redirected || response.status === 401 || response.headers.get('content-type')?.includes('text/html')) throw new Error('UNAUTHORIZED');
      const answer = await response.json().catch(() => { throw new Error('INVALID_RESPONSE'); });
      if (!response.ok) throw new Error(response.status === 413 ? 'FILE_LIMIT' : answer.error || 'PROVIDER_ERROR');
      if (id !== sequence) return;
      validateAnswer(answer); appendAnswer(answer);
      history.push({role:'user',text:attempt.message || t(attempt.mode)},{role:'assistant',text:answer.text + answer.tasks.map(task=>`\n${task.title}\n${task.description}`).join('')});
      history = history.slice(-8).map(turn => ({role:turn.role,text:turn.text.slice(0,6000)}));
    } catch (error) {
      if (id !== sequence) return;
      lastFailure = attempt;
      showError(controller.signal.aborted ? controller.signal.reason === 'timeout' ? 'TIMEOUT' : 'stopped' : errorCopy[error.message] ? error.message : 'PROVIDER_ERROR',true);
    } finally { clearTimeout(timeout); pending.remove(); if (id === sequence) { setBusy(false); input.focus({preventScroll:true}); } }
  }
  async function submit(event) {
    event?.preventDefault(); if (busy) return;
    const message = input.value.trim();
    if (mode === 'guide') { if (message) appendMessage(message,'user'); input.value = ''; root.querySelector('[data-ai-count]').textContent = '0 / 6000'; appendGuide(message); return; }
    if (!message && !files.length) { showError('EMPTY_MESSAGE'); return; }
    await status();
    if (!configured) { showError(preview ? 'preview' : connection === 'login' ? 'UNAUTHORIZED' : 'NOT_CONFIGURED'); return; }
    appendMessage((message || t(mode)) + (files.length ? `\n${files.map(file=>file.name).join(', ')}` : ''),'user');
    const attempt = {mode,message,files:[...files],history:[...history]}; input.value = ''; root.querySelector('[data-ai-count]').textContent = '0 / 6000'; await sendAttempt(attempt);
  }
  launcher.addEventListener('click',() => panel.hidden ? open() : close());
  form.addEventListener('submit',submit);
  input.addEventListener('input',() => { clearError(); root.querySelector('[data-ai-count]').textContent = `${input.value.length} / 6000`; });
  input.addEventListener('keydown',event => { if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) submit(event); });
  fileInput.addEventListener('change',() => { attach([...fileInput.files]); fileInput.value = ''; });
  const drop = root.querySelector('[data-ai-drop]');
  drop.addEventListener('dragover',event => { event.preventDefault(); if (!busy) drop.classList.add('is-dragging'); });
  drop.addEventListener('dragleave',() => drop.classList.remove('is-dragging'));
  drop.addEventListener('drop',event => { event.preventDefault(); drop.classList.remove('is-dragging'); attach([...event.dataTransfer.files]); });
  root.addEventListener('click',event => {
    const tab = event.target.closest('[data-ai-mode]'); if (tab) chooseMode(tab.dataset.aiMode);
    const action = event.target.closest('[data-ai-action]')?.dataset.aiAction;
    if (action === 'close') close();
    if (action === 'expand') panel.classList.toggle('tutor-expanded');
    if (action === 'stop') controller?.abort('user');
    if (action === 'retry' && lastFailure && !busy) sendAttempt(lastFailure);
    if (action === 'clear') {
      sequence++; controller?.abort('clear'); history = []; files = []; lastFailure = null; messages.replaceChildren(); guideShown = false; input.value = ''; root.querySelector('[data-ai-count]').textContent = '0 / 6000'; clearError(); setBusy(false); emptyState(); chooseMode(mode); input.focus();
    }
  });
  root.querySelector('[role="tablist"]').addEventListener('keydown',event => {
    if (!['ArrowRight','ArrowLeft','Home','End'].includes(event.key) || busy) return;
    event.preventDefault(); let index = modes.indexOf(mode);
    index = event.key === 'Home' ? 0 : event.key === 'End' ? modes.length-1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + modes.length) % modes.length;
    chooseMode(modes[index]); root.querySelector(`[data-ai-mode="${modes[index]}"]`).focus();
  });
  document.addEventListener('keydown',event => { if (event.key === 'Escape' && !panel.hidden) close(); });
  document.addEventListener('studhub:assistant-hide',() => close(false));
  document.addEventListener('studhub:language',updateLabels);
  updateLabels(); emptyState(); chooseMode(mode);
  if (!preview && location.pathname === '/assignments/new' && new URLSearchParams(location.search).has('assistantDraft')) {
    try {
      const task = JSON.parse(sessionStorage.getItem('studhub-assistant-draft') || 'null');
      sessionStorage.removeItem('studhub-assistant-draft');
      if (task && typeof task.title === 'string' && task.title.length <= 150 && typeof task.description === 'string' && task.description.length <= 1400) {
        const title = document.querySelector('input[name="title"]'), description = document.querySelector('textarea[name="description"]');
        if (title && description) { title.value = task.title; description.value = task.description; description.dispatchEvent(new Event('input',{bubbles:true})); document.querySelector('[name="courseId"]')?.focus(); }
      }
    } catch { sessionStorage.removeItem('studhub-assistant-draft'); }
  }
})();
