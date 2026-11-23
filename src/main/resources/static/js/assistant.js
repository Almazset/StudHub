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
    <header class="assistant-header"><span class="assistant-avatar"><i class="bi bi-stars" aria-hidden="true"></i></span><div><strong id="tutorHeading" data-ai-label="title" tabindex="-1"></strong><small data-ai-label="subtitle"></small></div><button type="button" class="assistant-close" data-ai-action="clear" data-ai-title="newChat"><i class="bi bi-arrow-counterclockwise"></i></button><button type="button" class="assistant-close" data-ai-action="library" data-ai-title="library" aria-pressed="false" ${preview ? 'hidden' : ''}><i class="bi bi-bookmark" aria-hidden="true"></i></button><button type="button" class="assistant-close tutor-expand" data-ai-action="expand" data-ai-title="expand" aria-pressed="false"><i class="bi bi-arrows-fullscreen"></i></button><button type="button" class="assistant-close" data-ai-action="close" data-ai-title="close"><i class="bi bi-x-lg"></i></button></header>
    <div class="tutor-modes" role="radiogroup" data-ai-title="modes">${modes.map(mode => `<button type="button" role="radio" data-ai-mode="${mode}"><i class="bi bi-${icon[mode]}" aria-hidden="true"></i><span data-ai-label="${mode}"></span></button>`).join('')}</div>
    <div class="tutor-connection" data-ai-status role="status"></div>
    <div class="visually-hidden" data-ai-progress role="status"></div>
    <div class="tutor-context"><p data-ai-hint></p><div class="tutor-upload" data-ai-drop><button type="button" class="tutor-upload-button" data-ai-attach><i class="bi bi-paperclip" aria-hidden="true"></i><span data-ai-label="attach"></span></button><span class="tutor-drop-copy" data-ai-label="drop"></span><input id="tutorFiles" type="file" multiple accept=".pdf,.docx,.pptx,.txt,.md,.jpg,.jpeg,.png" hidden data-ai-files><small data-ai-label="formats"></small><small data-ai-label="limits"></small></div><div class="tutor-files" data-ai-selected-files></div></div>
    <section class="tutor-library" data-ai-library aria-labelledby="tutorLibraryHeading" hidden><div class="tutor-library-heading"><h3 id="tutorLibraryHeading" data-ai-label="library"></h3><button type="button" class="tutor-text-button" data-ai-action="backToChat" data-ai-label="backToChat"></button></div><div data-ai-library-content></div></section>
    <div id="tutorContent" class="assistant-messages" role="log" aria-live="polite" aria-relevant="additions" data-ai-title="transcript" tabindex="0" data-assistant-messages></div>
    <div class="tutor-error" data-ai-error role="alert" hidden><span></span><button type="button" class="tutor-text-button" data-ai-action="retry" data-ai-label="retry" hidden></button></div>
    <form class="assistant-form tutor-composer" data-assistant-form><label class="visually-hidden" for="tutorInput" data-ai-label="input"></label><textarea id="tutorInput" name="message" rows="2" maxlength="6000" data-ai-input></textarea><div class="tutor-composer-actions"><small data-ai-count>0 / 6000</small><button type="button" class="tutor-stop" data-ai-action="stop" data-ai-label="stop" hidden></button><button type="submit" class="tutor-send" data-ai-title="send"><i class="bi bi-arrow-up"></i><span data-ai-label="send"></span></button></div><small class="tutor-disclosure" id="tutorDisclosure" data-ai-label="disclosure"></small><label class="tutor-consent" data-ai-consent-region hidden><input type="checkbox" data-ai-consent aria-describedby="tutorDisclosure"><span data-ai-label="fileConsent"></span></label></form>
  </div><button type="button" class="assistant-launcher" data-assistant-open aria-controls="studhubTutor" aria-expanded="false" data-ai-title="open"><i class="bi bi-stars" aria-hidden="true"></i><span data-ai-label="title"></span></button>`;
  const panel = root.querySelector('[data-assistant-panel]');
  const launcher = root.querySelector('[data-assistant-open]');
  const heading = root.querySelector('#tutorHeading');
  const input = root.querySelector('[data-ai-input]');
  const attachButton = root.querySelector('[data-ai-attach]');
  const progress = root.querySelector('[data-ai-progress]');
  const fileInput = root.querySelector('[data-ai-files]');
  const messages = root.querySelector('[data-assistant-messages]');
  const form = root.querySelector('[data-assistant-form]');
  const consent = root.querySelector('[data-ai-consent]');
  const consentRegion = root.querySelector('[data-ai-consent-region]');
  const library = root.querySelector('[data-ai-library]');
  const libraryContent = root.querySelector('[data-ai-library-content]');
  const libraryToggle = root.querySelector('[data-ai-action="library"]');
  const errorBox = root.querySelector('[data-ai-error]');
  const accountId = document.querySelector('meta[name="studhub-account-id"]')?.content;
  const draftKey = accountId ? 'studhub-assistant-draft:' + accountId : null;
  // Discard legacy drafts and drafts from a previous account in this tab.
  try {
    for (const key of Object.keys(sessionStorage)) {
      if (key === 'studhub-assistant-draft' ||
          (key.startsWith('studhub-assistant-draft:') && key !== draftKey)) sessionStorage.removeItem(key);
    }
  } catch {}
  let mode = 'summary', files = [], history = [], configured = false, connection = 'checking', busy = false;
  let controller, sequence = 0, lastFailure, statusPromise, guideShown = false, libraryVersion = 0;
  let visibleError = null;
  const node = (tag,className,text) => { const result = document.createElement(tag); if (className) result.className = className; if (text !== undefined) result.textContent = text; return result; };
  const errorCopy = {NOT_CONFIGURED:'notConfigured',UNAUTHORIZED:'login',FILE_LIMIT:'fileLimit',FILE_FORMAT:'fileFormat',TEXT_LIMIT:'textLimit',EMPTY_MESSAGE:'empty',RATE_LIMIT:'rate',BUSY:'busy',PROVIDER_LIMIT:'rate',PROVIDER_AUTH:'providerAuth',PROVIDER_MODEL:'providerModel',PROVIDER_SETUP:'providerSetup',PROVIDER_UNAVAILABLE:'providerUnavailable',PROVIDER_ERROR:'providerError',INPUT_TOO_LARGE:'inputTooLarge',TIMEOUT:'timeout',BLOCKED:'blocked',TRUNCATED:'truncated',INVALID_RESPONSE:'invalid',VALIDATION:'invalid',FILE_CONSENT_REQUIRED:'consentRequired',NOTE_LIMIT:'noteLimit',NOTE_NOT_FOUND:'noteNotFound'};
  function showError(key,retry = false) {
    visibleError = errorCopy[key] || key || 'providerError';
    errorBox.hidden = !library.hidden;
    errorBox.querySelector('span').textContent = t(visibleError);
    errorBox.querySelector('button').hidden = !retry;
  }
  function clearError() { visibleError = null; errorBox.hidden = true; lastFailure = null; }
  function updateLabels() {
    root.querySelectorAll('[data-ai-label]').forEach(element => element.textContent = t(element.dataset.aiLabel));
    root.querySelectorAll('[data-ai-title]').forEach(element => { element.setAttribute('aria-label',t(element.dataset.aiTitle)); element.setAttribute('title',t(element.dataset.aiTitle)); });
    input.placeholder = t('placeholder'); root.setAttribute('aria-label',t('title'));
    root.querySelector('[data-ai-hint]').textContent = t(`${mode}Hint`);
    root.querySelector('[data-ai-status]').textContent = t(connection);
    if (visibleError) errorBox.querySelector('span').textContent = t(visibleError);
    updateFiles();
  }
  async function status() {
    if (preview) { connection = 'preview'; configured = false; updateLabels(); return; }
    if (!statusPromise) {
      connection = 'checking';
      root.querySelector('[data-ai-status]').textContent = t(connection);
      statusPromise = fetch('/api/assistant/status',{
        credentials:'same-origin',cache:'no-store',signal:AbortSignal.timeout(10000)
      }).then(async response => {
        if (response.redirected || response.headers.get('content-type')?.includes('text/html') || response.status === 401) { connection = 'login'; return; }
        if (!response.ok) throw new Error('connection');
        const result = await response.json(); configured = result.configured === true; connection = configured ? 'ready' : 'notConfigured';
      }).catch(() => {
        configured = false;
        connection = 'connectionError';
        statusPromise = null;
      }).finally(updateLabels);
    }
    await statusPromise;
  }
  function open() {
    if (!library.hidden) switchLibrary(false);
    panel.hidden = false;
    root.classList.add('is-open');
    launcher.setAttribute('aria-expanded','true');
    heading.focus({preventScroll:true});
    status();
  }
  function close(restore = true) {
    if (!library.hidden) switchLibrary(false);
    panel.hidden = true; root.classList.remove('is-open'); launcher.setAttribute('aria-expanded','false'); if (restore) launcher.focus(); }
  function chooseMode(next) {
    if (!modes.includes(next) || busy || !library.hidden) return;
    mode = next; clearError();
    root.querySelectorAll('[data-ai-mode]').forEach(button => { const active = button.dataset.aiMode === mode; button.setAttribute('aria-checked',String(active)); button.tabIndex = active ? 0 : -1; });
    root.querySelector('[data-ai-hint]').textContent = t(`${mode}Hint`);
    root.querySelector('[data-ai-drop]').hidden = mode === 'guide'; root.querySelector('[data-ai-selected-files]').hidden = mode === 'guide'; root.querySelector('.tutor-disclosure').hidden = mode === 'guide';
    updateFiles();
    if (mode === 'guide' && !guideShown) { appendGuide(''); guideShown = true; }
  }
  function updateFiles() {
    const region = root.querySelector('[data-ai-selected-files]');
    const focusedIndex = Array.from(region.querySelectorAll('button')).indexOf(document.activeElement);
    region.replaceChildren();
    consentRegion.hidden = mode === 'guide' || files.length === 0;
    consent.disabled = busy;
    files.forEach((file,index) => {
      const size = file.size < 1024*1024 ? `${(file.size/1024).toFixed(1)} KB` : `${(file.size/1024/1024).toFixed(2)} MB`;
      const chip = node('div','tutor-file');
      const name = node('span','',file.name);
      name.title = file.name;
      chip.append(node('i','bi bi-file-earmark-text'),name,node('small','',size));
      const remove = node('button','','×'); remove.type = 'button'; remove.disabled = busy; remove.setAttribute('aria-label',`${t('remove')}: ${file.name}`);
      remove.addEventListener('click',() => { if (!busy) { files.splice(index,1); consent.checked = false; clearError(); updateFiles(); } }); chip.append(remove); region.append(chip);
    });
    if (focusedIndex >= 0) {
      const next = region.querySelectorAll('button')[Math.min(focusedIndex,files.length-1)];
      (busy ? input : next || attachButton).focus({preventScroll:true});
    }
  }
  function attach(incoming) {
    if (busy || mode === 'guide') return;
    const merged = [...files];
    for (const file of incoming) {
      if (!/\.(pdf|docx|pptx|txt|md|jpe?g|png)$/i.test(file.name)) { showError('FILE_FORMAT'); return; }
      if (!merged.some(existing => existing.name === file.name && existing.size === file.size && existing.lastModified === file.lastModified)) merged.push(file);
    }
    if (merged.length > 3 || merged.some(file => file.size === 0 || file.size > 8*1024*1024) || merged.reduce((sum,file) => sum+file.size,0) > 12*1024*1024) { showError('FILE_LIMIT'); return; }
    if (merged.length === files.length) return;
    files = merged;
    consent.checked = false;
    clearError();
    updateFiles();
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
  function action(label, handler, className = '') {
    const button = node('button',className,t(label));
    button.type = 'button';
    button.dataset.aiLabel = label;
    button.addEventListener('click',handler);
    return button;
  }

  function appendNote(container,note,canDraft = false) {
    const section = node('section','tutor-result');
    section.append(node('h3','',note.title),node('div','tutor-message-copy',note.body));
    if (note.terms.length) {
      const heading = node('h4','',t('terms'));
      heading.dataset.aiLabel = 'terms';
      const terms = node('dl','tutor-terms');
      note.terms.forEach(({term,definition}) => terms.append(node('dt','',term),node('dd','',definition)));
      section.append(heading,terms);
    }
    if (canDraft) {
      const glossary = note.terms.map(({term,definition}) => `${term} — ${definition}`);
      appendTaskAction(section,{
        title:shorten(`${t('reviewMaterial')} ${note.title}`,150),
        description:shorten([note.body,...glossary].join('\n\n'),1400)
      },'draftFromNote');
    }
    container.append(section);
  }

  function appendFlashcards(container,cards) {
    let index = 0;
    const section = node('section','tutor-result tutor-flashcards');
    const heading = node('h3','',t('flashcards'));
    heading.dataset.aiLabel = 'flashcards';
    const position = node('p','tutor-result-meta');
    const question = node('strong','tutor-flash-question');
    const answer = node('p','tutor-flash-answer');
    function update() {
      position.textContent = `${index + 1} / ${cards.length}`;
      question.textContent = cards[index].question;
      answer.textContent = cards[index].answer;
      answer.hidden = true;
      reveal.dataset.aiLabel = 'revealAnswer';
      reveal.textContent = t('revealAnswer');
      reveal.setAttribute('aria-expanded','false');
      previous.disabled = index === 0;
      next.disabled = index === cards.length - 1;
    }
    const reveal = action('revealAnswer',() => {
      answer.hidden = !answer.hidden;
      reveal.dataset.aiLabel = answer.hidden ? 'revealAnswer' : 'hideAnswer';
      reveal.textContent = t(reveal.dataset.aiLabel);
      reveal.setAttribute('aria-expanded',String(!answer.hidden));
    });
    const previous = action('prevCard',() => { index--; update(); if (previous.disabled) reveal.focus(); });
    const next = action('nextCard',() => { index++; update(); if (next.disabled) reveal.focus(); });
    const actions = node('div','tutor-answer-actions');
    actions.append(previous,reveal,next);
    section.append(heading,position,question,answer,actions);
    container.append(section);
    update();
  }

  function appendQuiz(container,questions) {
    const section = node('section','tutor-result');
    const heading = node('h3','',t('quiz'));
    heading.dataset.aiLabel = 'quiz';
    const quizForm = node('form','tutor-quiz');
    const entries = questions.map((question,index) => {
      const group = node('fieldset','tutor-quiz-question');
      group.append(node('legend','',`${index + 1}. ${question.question}`));
      const inputs = question.options.map((option,optionIndex) => {
        const label = node('label','tutor-quiz-option');
        const radio = node('input');
        radio.type = 'radio';
        radio.name = 'question-' + index;
        radio.value = String(optionIndex);
        radio.required = true;
        label.append(radio,node('span','',option));
        group.append(label);
        return radio;
      });
      const feedback = node('p','tutor-quiz-feedback');
      feedback.hidden = true;
      group.append(feedback);
      quizForm.append(group);
      return {group,inputs,feedback};
    });
    const score = node('p','tutor-quiz-score');
    score.setAttribute('role','status');
    const grade = node('button','',t('checkQuiz'));
    grade.type = 'submit';
    grade.dataset.aiLabel = 'checkQuiz';
    const reset = action('retryQuiz',() => {
      quizForm.reset();
      entries.forEach(({group,feedback}) => {
        group.disabled = false;
        group.removeAttribute('data-outcome');
        feedback.hidden = true;
      });
      score.textContent = '';
      grade.hidden = false;
      reset.hidden = true;
      entries[0].inputs[0].focus();
    });
    reset.hidden = true;
    const actions = node('div','tutor-answer-actions');
    actions.append(grade,reset);
    quizForm.append(score,actions);
    quizForm.addEventListener('submit',event => {
      event.preventDefault();
      let correct = 0;
      entries.forEach(({group,inputs,feedback},index) => {
        const question = questions[index];
        const match = inputs[question.correctIndex].checked;
        if (match) correct++;
        group.disabled = true;
        group.dataset.outcome = match ? 'correct' : 'incorrect';
        feedback.textContent = match ? `${t('correct')} ${question.explanation}`
          : `${t('correctOption')} ${question.options[question.correctIndex]}. ${question.explanation}`;
        feedback.hidden = false;
      });
      score.textContent = `${t('quizScore')} ${correct} / ${questions.length}`;
      grade.hidden = true;
      reset.hidden = false;
      reset.focus();
    });
    section.append(heading,quizForm);
    container.append(section);
  }

  function appendPlan(container,plan) {
    const section = node('section','tutor-result');
    const heading = node('h3','',t('exam'));
    heading.dataset.aiLabel = 'exam';
    const list = node('ol','tutor-plan');
    plan.forEach(day => {
      const item = node('li');
      const header = node('div','tutor-day');
      const duration = node('small','tutor-result-meta',`${day.durationMinutes} ${t('minutes')}`);
      header.append(node('strong','',day.title),duration);
      const activities = node('ul');
      day.activities.forEach(text => activities.append(node('li','',text)));
      item.append(header,activities);
      appendTaskAction(item,{
        title:shorten(`${t('exam')}: ${day.title}`,150),
        description:shorten([`${day.durationMinutes} ${t('minutes')}`,...day.activities.map(item => `• ${item}`)].join('\n'),1400)
      },'draftFromSession');
      list.append(item);
    });
    section.append(heading,list);
    container.append(section);
  }

  function appendCoach(container,coach) {
    const section = node('section','tutor-result');
    const heading = node('h3','',t('coachHintTitle'));
    heading.dataset.aiLabel = 'coachHintTitle';
    section.append(heading,node('p','',coach.hint));
    for (const [key,value] of [['coachExplanation',coach.explanation],['coachNext',coach.nextStep]]) {
      const details = node('details','tutor-coach-step');
      const summary = node('summary','',t(key));
      summary.dataset.aiLabel = key;
      details.append(summary,node('p','',value));
      section.append(details);
    }
    container.append(section);
  }

  function shorten(text,limit) {
    if (text.length <= limit) return text;
    return text.slice(0,limit-1).replace(/[\uD800-\uDBFF]$/,'').trimEnd() + '…';
  }

  function appendTask(container,task) {
    const card = node('section','tutor-task');
    card.append(node('h3','',task.title),node('p','',task.description));
    appendTaskAction(card,task,'addTask');
    container.append(card);
  }

  function appendTaskAction(container,task,label) {
    const add = action(label,() => {
      confirmation.hidden = false;
      add.hidden = true;
      proceed.focus();
    });
    const confirmation = node('div','tutor-task-review');
    confirmation.hidden = true;
    const hint = node('p','',t('draftHint'));
    hint.dataset.aiLabel = 'draftHint';
    const proceed = action('openDraft',() => {
      if (preview) {
        close(false);
        window.previewApp.openAssignmentDraft(task);
        return;
      }
      try {
        if (!draftKey) throw new Error();
        sessionStorage.setItem(draftKey,JSON.stringify(task));
        location.assign('/assignments/new?assistantDraft=1');
      } catch {
        showError('draftError');
      }
    });
    const cancel = action('cancel',() => {
      confirmation.hidden = true;
      add.hidden = false;
      add.focus();
    });
    const choices = node('div','tutor-answer-actions');
    choices.append(proceed,cancel);
    confirmation.append(hint,choices);
    const actionRow = node('div','tutor-answer-actions');
    actionRow.append(add);
    container.append(actionRow,confirmation);
  }

  function answerText(answer) {
    const paragraphs = [answer.text];
    if (answer.note) paragraphs.push(answer.note.title,answer.note.body,...answer.note.terms.map(({term,definition}) => `${term} — ${definition}`));
    answer.flashcards.forEach(({question,answer}) => paragraphs.push(`${question}\n${answer}`));
    answer.quiz.forEach(({question,options}) => paragraphs.push([question,...options].join('\n')));
    answer.plan.forEach(({title,durationMinutes,activities}) => paragraphs.push([`${title} · ${durationMinutes} ${t('minutes')}`,...activities].join('\n')));
    if (answer.coach) paragraphs.push(answer.coach.hint,answer.coach.explanation,answer.coach.nextStep);
    answer.tasks.forEach(({title,description}) => paragraphs.push(`${title}\n${description}`));
    return paragraphs.filter(Boolean).join('\n\n');
  }

  function answerActions(article,text,kind,note = null) {
    const actions = node('div','tutor-answer-actions');
    const copy = action('copy',async () => {
      try {
        await navigator.clipboard.writeText(text);
        copy.textContent = t('copied');
        setTimeout(() => { copy.textContent = t('copy'); },1600);
      } catch { showError('copyError'); }
    });
    const download = action('download',() => {
      const url = URL.createObjectURL(new Blob([text],{type:'text/plain;charset=utf-8'}));
      const link = node('a');
      link.href = url;
      link.download = `StudHub-${kind}.txt`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url),1000);
    });
    actions.append(copy,download);
    if (note && !preview) {
      const save = action('saveNote',async () => {
        save.disabled = true;
        save.textContent = t('savingNote');
        try {
          await notesApi('',{method:'POST',body:note});
          save.dataset.aiLabel = 'savedNote';
        } catch (error) {
          showError(errorCopy[error.message] ? error.message : 'notesError');
          save.disabled = false;
        } finally { save.textContent = t(save.dataset.aiLabel); }
      });
      actions.append(save);
    }
    article.append(actions);
  }

  function appendAnswer(answer,kind) {
    const article = appendMessage(answer.text,'assistant');
    article.classList.add('tutor-structured-answer');
    if (answer.note) appendNote(article,answer.note,true);
    if (answer.flashcards.length) appendFlashcards(article,answer.flashcards);
    if (answer.quiz.length) appendQuiz(article,answer.quiz);
    if (answer.plan.length) appendPlan(article,answer.plan);
    if (answer.coach) appendCoach(article,answer.coach);
    answer.tasks.forEach(task => appendTask(article,task));
    answerActions(article,answerText(answer),kind,answer.note);
    if (library.hidden && !panel.hidden) {
      messages.scrollTop += article.getBoundingClientRect().top - messages.getBoundingClientRect().top - 10;
    }
  }

  async function notesApi(path,{method = 'GET',body} = {}) {
    const headers = body ? {'Content-Type':'application/json'} : {};
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (token && csrfHeader) headers[csrfHeader] = token;
    const response = await fetch('/api/assistant/notes' + path,{
      method,headers,credentials:'same-origin',cache:'no-store',body:body ? JSON.stringify(body) : undefined
    });
    if (response.redirected || response.status === 401 || response.status === 403 || response.headers.get('content-type')?.includes('text/html')) throw new Error('UNAUTHORIZED');
    if (response.status === 204) return null;
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'notesError');
    return data;
  }

  function switchLibrary(show) {
    if (preview || show === !library.hidden) return;
    libraryVersion++;
    library.hidden = !show;
    libraryToggle.setAttribute('aria-pressed',String(show));
    for (const region of [root.querySelector('.tutor-modes'),root.querySelector('.tutor-context'),root.querySelector('[data-ai-status]'),messages,form]) region.hidden = show;
    errorBox.hidden = show || !visibleError;
    if (show) {
      loadNotes();
      library.querySelector('[data-ai-action="backToChat"]').focus();
    } else {
      libraryContent.replaceChildren();
      libraryToggle.focus();
    }
  }

  function showLibraryMessage(key,role) {
    const message = node('p','tutor-result-meta',t(key));
    message.dataset.aiLabel = key;
    if (role) message.setAttribute('role',role);
    libraryContent.replaceChildren(message);
  }

  async function loadNotes() {
    const version = ++libraryVersion;
    showLibraryMessage('notesLoading','status');
    try {
      const notes = await notesApi('');
      if (version !== libraryVersion) return;
      if (!notes.length) { showLibraryMessage('notesEmpty','status'); return; }
      const list = node('ul','tutor-note-list');
      notes.forEach(note => {
        const item = node('li');
        const open = node('button','tutor-note-link');
        open.type = 'button';
        const date = new Intl.DateTimeFormat(window.StudHubI18n?.language || 'ru',{dateStyle:'medium'}).format(new Date(note.createdAt));
        open.append(node('strong','',note.title),node('small','',date));
        open.addEventListener('click',() => openNote(note.id));
        item.append(open);
        list.append(item);
      });
      libraryContent.replaceChildren(list);
    } catch (error) {
      if (version !== libraryVersion) return;
      showLibraryMessage(errorCopy[error.message] || 'notesError','alert');
      libraryContent.append(action('retry',loadNotes,'tutor-text-button'));
    }
  }

  async function openNote(id) {
    const version = ++libraryVersion;
    const path = '/' + encodeURIComponent(id);
    showLibraryMessage('notesLoading','status');
    try {
      const note = await notesApi(path);
      if (version !== libraryVersion) return;
      const back = action('backToNotes',loadNotes,'tutor-text-button');
      libraryContent.replaceChildren(back);
      appendNote(libraryContent,note);
      const remove = action('deleteNote',async () => {
        if (!window.confirm(t('confirmDeleteNote'))) return;
        remove.disabled = true;
        try {
          await notesApi(path,{method:'DELETE'});
          if (version === libraryVersion) loadNotes();
        } catch (error) {
          if (version !== libraryVersion) return;
          remove.disabled = false;
          libraryContent.querySelector('[data-ai-delete-error]')?.remove();
          const failure = node('p','tutor-result-meta',t(errorCopy[error.message] || 'notesError'));
          failure.setAttribute('role','alert');
          failure.dataset.aiDeleteError = '';
          remove.after(failure);
        }
      },'tutor-text-button');
      libraryContent.append(remove);
      back.focus();
    } catch (error) {
      if (version !== libraryVersion) return;
      showLibraryMessage(errorCopy[error.message] || 'notesError','alert');
      libraryContent.append(action('backToNotes',loadNotes,'tutor-text-button'));
    }
  }

  function setBusy(value) {
    const submitButton = form.querySelector('[type="submit"]');
    const stopButton = root.querySelector('[data-ai-action="stop"]');
    const active = document.activeElement;
    if ((value && (active === submitButton || active === attachButton)) || (!value && active === stopButton)) {
      input.focus({preventScroll:true});
    }
    busy = value;
    messages.setAttribute('aria-busy',String(value));
    submitButton.disabled = value;
    stopButton.hidden = !value;
    input.readOnly = value;
    attachButton.disabled = value;
    fileInput.disabled = value;
    root.querySelectorAll('[data-ai-mode]').forEach(button => button.disabled = value);
    updateFiles();
  }

  function validateAnswer(answer) {
    const nonempty = value => typeof value === 'string' && !!value.trim();
    const list = (value,check) => Array.isArray(value) && value.every(check);
    if (!answer || typeof answer.text !== 'string' || answer.text.length > 20000 ||
      !list(answer.tasks,task => nonempty(task?.title) && task.title.length >= 2 && task.title.length <= 150 && nonempty(task.description) && task.description.length <= 1400) ||
      !list(answer.flashcards,card => nonempty(card?.question) && nonempty(card.answer)) ||
      !list(answer.quiz,question => nonempty(question?.question) && list(question.options,nonempty) && question.options.length >= 2 &&
        Number.isInteger(question.correctIndex) && question.correctIndex >= 0 && question.correctIndex < question.options.length && nonempty(question.explanation)) ||
      !list(answer.plan,day => nonempty(day?.title) && Number.isInteger(day.durationMinutes) && day.durationMinutes >= 1 && list(day.activities,nonempty)) ||
      (answer.note && !(nonempty(answer.note.title) && nonempty(answer.note.body) && list(answer.note.terms,term => nonempty(term?.term) && nonempty(term.definition)))) ||
      (answer.coach && !(nonempty(answer.coach.hint) && nonempty(answer.coach.explanation) && nonempty(answer.coach.nextStep))) ||
      !(answer.text.trim() || answer.note || answer.tasks.length || answer.flashcards.length || answer.quiz.length || answer.plan.length || answer.coach)) {
      throw new Error('INVALID_RESPONSE');
    }
    return answer;
  }

  async function sendAttempt(attempt) {
    if (busy) return;
    const id = ++sequence;
    const requestController = new AbortController();
    controller = requestController;
    clearError();
    setBusy(true);
    const pendingKey = attempt.files.length ? 'sendingFiles' : 'thinking';
    const pending = appendMessage(t(pendingKey),'assistant');
    pending.classList.add('tutor-pending');
    pending.querySelector('.tutor-message-copy').dataset.aiLabel = pendingKey;
    pending.setAttribute('aria-hidden','true');
    progress.dataset.aiLabel = pendingKey;
    progress.textContent = t(pendingKey);
    const timeout = setTimeout(() => requestController.abort('timeout'),90000);
    try {
      const {mode,message,history,language,fileConsent} = attempt;
      const data = new FormData();
      data.append('request',new Blob([JSON.stringify({mode,message,language,history,fileConsent})],{type:'application/json'}));
      attempt.files.forEach(file => data.append('files',file));
      const token = document.querySelector('meta[name="_csrf"]')?.content;
      const header = document.querySelector('meta[name="_csrf_header"]')?.content;
      const response = await fetch('/api/assistant/messages',{
        method:'POST',credentials:'same-origin',headers:token && header ? {[header]:token} : {},
        body:data,signal:requestController.signal
      });
      if (response.redirected || response.status === 401 || response.status === 403) throw new Error('UNAUTHORIZED');
      const answer = await response.json().catch(() => null);
      if (!response.ok) throw new Error(answer?.error || (response.status === 413 ? 'FILE_LIMIT' : 'PROVIDER_ERROR'));
      if (id !== sequence) return;
      validateAnswer(answer);
      pending.remove();
      appendAnswer(answer,attempt.mode);
      const context = answerText(answer);
      const turns = [{role:'user',text:attempt.message || t(attempt.mode)},{role:'assistant',text:context}];
      const prior = attempt.history.concat(turns);
      historyForNextRequest(prior);
      if (attempt.files.length) consent.checked = false;
    } catch (error) {
      if (id !== sequence) return;
      const errorKey = requestController.signal.aborted
        ? requestController.signal.reason === 'timeout' ? 'TIMEOUT' : 'stopped'
        : errorCopy[error.message] ? error.message : 'PROVIDER_ERROR';
      const retryable = !['UNAUTHORIZED','NOT_CONFIGURED','FILE_CONSENT_REQUIRED','FILE_FORMAT','FILE_LIMIT','TEXT_LIMIT','INPUT_TOO_LARGE','BLOCKED','PROVIDER_AUTH','PROVIDER_MODEL','PROVIDER_SETUP'].includes(errorKey);
      lastFailure = retryable ? attempt : null;
      if (!retryable && !input.value) input.value = attempt.message;
      updateCount();
      showError(errorKey,retryable);
    } finally {
      clearTimeout(timeout);
      pending.remove();
      if (id === sequence) {
        delete progress.dataset.aiLabel;
        progress.textContent = '';
        setBusy(false);
      }
    }
  }

  function historyForNextRequest(turns) {
    history = turns.slice(-8).map(turn => ({role:turn.role,text:turn.text.slice(0,6000)}));
  }
  function updateCount() {
    root.querySelector('[data-ai-count]').textContent = `${input.value.length} / 6000`;
  }

  async function submit(event) {
    event?.preventDefault();
    if (busy || !library.hidden) return;
    const message = input.value.trim();
    if (mode === 'guide') {
      if (message) appendMessage(message,'user');
      input.value = '';
      updateCount();
      appendGuide(message);
      return;
    }
    if (!message && !files.length) { showError('EMPTY_MESSAGE'); return; }
    if (files.length && !consent.checked) {
      showError('FILE_CONSENT_REQUIRED');
      consent.focus();
      return;
    }
    const attempt = {mode,message,files:[...files],history:[...history],fileConsent:files.length > 0,
      language:window.StudHubI18n?.language || 'ru'};
    const currentSequence = sequence;
    await status();
    if (busy || panel.hidden || !library.hidden || sequence !== currentSequence || attempt.mode !== mode ||
        input.value.trim() !== message || files.length !== attempt.files.length ||
        files.some((file,index) => file !== attempt.files[index]) || (files.length && !consent.checked)) return;
    if (!configured) {
      showError(preview ? 'preview' : connection === 'login' ? 'UNAUTHORIZED' : connection === 'connectionError' ? 'connectionError' : 'NOT_CONFIGURED');
      return;
    }
    appendMessage((message || t(mode)) + (files.length ? `\n${files.map(file=>file.name).join(', ')}` : ''),'user');
    input.value = '';
    updateCount();
    await sendAttempt(attempt);
  }
  launcher.addEventListener('click',() => panel.hidden ? open() : close());
  form.addEventListener('submit',submit);
  input.addEventListener('input',() => { clearError(); updateCount(); });
  consent.addEventListener('change',clearError);
  input.addEventListener('keydown',event => { if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) submit(event); });
  attachButton.addEventListener('click',() => fileInput.click());
  fileInput.addEventListener('change',() => { attach([...fileInput.files]); fileInput.value = ''; });
  const drop = root.querySelector('[data-ai-drop]');
  drop.addEventListener('dragover',event => { event.preventDefault(); if (!busy) drop.classList.add('is-dragging'); });
  drop.addEventListener('dragleave',() => drop.classList.remove('is-dragging'));
  drop.addEventListener('drop',event => { event.preventDefault(); drop.classList.remove('is-dragging'); attach([...event.dataTransfer.files]); });
  root.addEventListener('click',event => {
    const tab = event.target.closest('[data-ai-mode]'); if (tab) chooseMode(tab.dataset.aiMode);
    const action = event.target.closest('[data-ai-action]')?.dataset.aiAction;
    if (action === 'close') close();
    if (action === 'library') switchLibrary(library.hidden);
    if (action === 'backToChat') switchLibrary(false);
    if (action === 'expand') event.target.closest('[data-ai-action]').setAttribute('aria-pressed',String(panel.classList.toggle('tutor-expanded')));
    if (action === 'stop') controller?.abort('user');
    if (action === 'retry' && lastFailure && !busy) {
      input.focus({preventScroll:true});
      sendAttempt(lastFailure);
    }
    if (action === 'clear') {
      sequence++;
      controller?.abort('clear');
      switchLibrary(false);
      history = [];
      files = [];
      consent.checked = false;
      messages.replaceChildren();
      delete progress.dataset.aiLabel;
      progress.textContent = '';
      guideShown = false;
      input.value = '';
      updateCount();
      clearError();
      setBusy(false);
      emptyState();
      chooseMode(mode);
      input.focus();
    }
  });
  root.querySelector('[role="radiogroup"]').addEventListener('keydown',event => {
    if (!['ArrowRight','ArrowLeft','ArrowDown','ArrowUp','Home','End'].includes(event.key) || busy) return;
    event.preventDefault();
    const direction = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? 1 : -1;
    const index = event.key === 'Home' ? 0 : event.key === 'End' ? modes.length-1 : (modes.indexOf(mode) + direction + modes.length) % modes.length;
    chooseMode(modes[index]);
    root.querySelector(`[data-ai-mode="${modes[index]}"]`).focus();
  });
  root.addEventListener('keydown',event => {
    if (event.key !== 'Escape' || panel.hidden || event.defaultPrevented) return;
    event.preventDefault();
    event.stopPropagation();
    if (!library.hidden) switchLibrary(false);
    else close();
  });
  document.addEventListener('studhub:assistant-hide',() => close(false));
  document.addEventListener('studhub:language',updateLabels);
  updateLabels(); emptyState(); chooseMode(mode);
  if (!preview && location.pathname === '/assignments/new' && new URLSearchParams(location.search).has('assistantDraft')) {
    try {
      const task = JSON.parse((draftKey && sessionStorage.getItem(draftKey)) || 'null');
      if (draftKey) sessionStorage.removeItem(draftKey);
      if (task && typeof task.title === 'string' && task.title.length <= 150 && typeof task.description === 'string' && task.description.length <= 1400) {
        const title = document.querySelector('input[name="title"]'), description = document.querySelector('textarea[name="description"]');
        if (title && description) {
          title.value = task.title;
          description.value = task.description;
          description.dispatchEvent(new Event('input',{bubbles:true}));
          const hint = node('p','tutor-draft-notice',t('draftHint'));
          title.closest('.field-group')?.before(hint);
          document.addEventListener('studhub:language',() => { hint.textContent = t('draftHint'); });
          title.focus();
        }
      }
    } catch { if (draftKey) sessionStorage.removeItem(draftKey); }
  }
})();
