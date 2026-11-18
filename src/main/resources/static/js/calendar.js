(() => {
  'use strict';

  const root = document.querySelector('[data-study-calendar]');
  if (!root) return;
  const copy = {
    heading: ['Учебный календарь', 'Study calendar', 'Оқу күнтізбесі'],
    subheading: ['Дедлайны и личный план в одном расписании.', 'Your deadlines and personal plan in one schedule.', 'Мерзімдер мен жеке жоспар бір кестеде.'],
    add: ['Добавить в план', 'Add to plan', 'Жоспарға қосу'],
    addToday: ['Запланировать на сегодня', 'Plan for today', 'Бүгінге жоспарлау'],
    addFor: ['Добавить занятие ({date})', 'Add a study session ({date})', 'Оқу сессиясын қосу ({date})'],
    retry: ['Повторить', 'Try again', 'Қайталау'],
    viewLabel: ['Вид календаря', 'Calendar view', 'Күнтізбе көрінісі'],
    day: ['День', 'Day', 'Күн'],
    week: ['Неделя', 'Week', 'Апта'],
    month: ['Месяц', 'Month', 'Ай'],
    previous: ['Предыдущий период', 'Previous period', 'Алдыңғы кезең'],
    next: ['Следующий период', 'Next period', 'Келесі кезең'],
    today: ['Сегодня', 'Today', 'Бүгін'],
    dateLabel: ['Выбрать дату', 'Choose date', 'Күнді таңдау'],
    loading: ['Загружаем расписание…', 'Loading schedule…', 'Кесте жүктелуде…'],
    overdue: ['Просрочено', 'Overdue', 'Мерзімі өткен'],
    nearby: ['Сегодня и скоро', 'Today and soon', 'Бүгін және жақында'],
    soon: ['Скоро', 'Due soon', 'Жақында'],
    upcoming: ['Запланировано', 'Upcoming', 'Жоспарланған'],
    done: ['Выполнено', 'Done', 'Орындалды'],
    plan: ['Личный план', 'Personal plan', 'Жеке жоспар'],
    focusHeading: ['Что учить сегодня', 'What to study today', 'Бүгін не оқу керек'],
    focusPlan: ['План на сегодня', 'Today’s plan', 'Бүгінгі жоспар'],
    focusDue: ['Срочные задания', 'Urgent assignments', 'Шұғыл тапсырмалар'],
    noFocusDue: ['Срочных заданий нет.', 'No urgent assignments.', 'Шұғыл тапсырмалар жоқ.'],
    noFocusPlan: ['План на сегодня пока пуст.', 'Nothing planned for today.', 'Бүгінге әлі жоспар жоқ.'],
    emptyDay: ['Нет записей', 'Nothing scheduled', 'Жоспар бос'],
    minutes: ['{minutes} мин', '{minutes} min', '{minutes} мин'],
    more: ['Ещё {count}', '{count} more', 'Тағы {count}'],
    count: ['Записей: {count}', 'Items: {count}', 'Жазба саны: {count}'],
    titleLabel: ['Что запланировать', 'What to plan', 'Нені жоспарлау керек'],
    planDate: ['Дата', 'Date', 'Күні'],
    minutesLabel: ['Минуты', 'Minutes', 'Минут'],
    descriptionLabel: ['Заметка (необязательно)', 'Note (optional)', 'Ескертпе (міндетті емес)'],
    completed: ['Выполнено', 'Completed', 'Орындалды'],
    close: ['Закрыть', 'Close', 'Жабу'],
    delete: ['Удалить', 'Delete', 'Жою'],
    cancel: ['Отмена', 'Cancel', 'Болдырмау'],
    save: ['Сохранить', 'Save', 'Сақтау'],
    saving: ['Сохраняем…', 'Saving…', 'Сақталуда…'],
    deleting: ['Удаляем…', 'Deleting…', 'Жойылуда…'],
    edit: ['Редактировать план', 'Edit planned item', 'Жоспарды өңдеу'],
    confirmDelete: ['Удалить «{title}» из личного плана?', 'Delete “{title}” from your personal plan?', '«{title}» тармағын жеке жоспардан жою керек пе?'],
    toggle: ['Отметить выполнение: {title}', 'Mark completion: {title}', 'Орындалуын белгілеу: {title}'],
    saved: ['План обновлён.', 'Plan updated.', 'Жоспар жаңартылды.'],
    deleted: ['Запись удалена.', 'Item deleted.', 'Жазба жойылды.'],
    personal: ['Личное задание', 'Personal assignment', 'Жеке тапсырма'],
    course: ['Курс', 'Course', 'Курс'],
    project: ['Проект', 'Project', 'Жоба'],
    invalid: ['Проверьте дату, название и длительность.', 'Check the date, title and duration.', 'Күнді, атауды және ұзақтығын тексеріңіз.'],
    invalidDate: ['Выберите допустимую дату календаря.', 'Choose a valid calendar date.', 'Жарамды күнтізбе күнін таңдаңыз.'],
    invalidView: ['Выберите день, неделю или месяц.', 'Choose day, week or month.', 'Күнді, аптаны немесе айды таңдаңыз.'],
    invalidZone: ['Не удалось распознать часовой пояс браузера.', 'The browser time zone could not be recognized.', 'Браузердің уақыт белдеуі танылмады.'],
    limit: ['В плане уже 500 записей. Удалите ненужные записи и повторите.', 'Your plan has 500 items. Delete any you no longer need and try again.', 'Жоспарда 500 жазба бар. Қажетсіз жазбаларды жойып, қайталаңыз.'],
    missing: ['Запись уже удалена или недоступна.', 'This item was deleted or is unavailable.', 'Бұл жазба жойылған немесе қолжетімсіз.'],
    auth: ['Сеанс завершён. Войдите в StudHub снова.', 'Your session ended. Sign in to StudHub again.', 'Сеанс аяқталды. StudHub жүйесіне қайта кіріңіз.'],
    calendarError: ['Не удалось загрузить календарь. Попробуйте ещё раз.', 'Could not load the calendar. Try again.', 'Күнтізбе жүктелмеді. Қайта көріңіз.'],
    planError: ['Не удалось загрузить личный план.', 'Could not load your personal plan.', 'Жеке жоспар жүктелмеді.'],
    writeError: ['Не удалось обновить план. Попробуйте ещё раз.', 'Could not update the plan. Try again.', 'Жоспар жаңартылмады. Қайта көріңіз.']
  };

  const $ = selector => root.querySelector(selector);
  const views = ['day', 'week', 'month'];
  const language = () => window.StudHubI18n?.language || 'ru';
  const t = (key, replacements = {}) => (copy[key]?.[['ru', 'en', 'kk'].indexOf(language())] || copy[key]?.[0] || key)
    .replace(/\{(\w+)\}/g, (_, name) => String(replacements[name] ?? ''));
  const node = (tag, className, value) => {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (value != null) element.textContent = value;
    return element;
  };
  const button = (className, value, onClick) => {
    const control = node('button', className, value);
    control.type = 'button';
    control.addEventListener('click', onClick);
    return control;
  };
  const icon = name => {
    const element = node('i', 'bi bi-' + name);
    element.setAttribute('aria-hidden', 'true');
    return element;
  };

  // Parse calendar dates at UTC noon, then format them in UTC. Only finding today uses the browser's time zone on the server.
  const asDate = date => new Date(date + 'T12:00:00Z');
  const validDate = value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number(value.slice(0, 4)) >= 1 && !Number.isNaN(asDate(value).getTime()) &&
    asDate(value).toISOString().slice(0, 10) === value;
  const toDate = value => value.getUTCFullYear() >= 1 && value.getUTCFullYear() <= 9999
    ? value.toISOString().slice(0, 10) : null;
  function shift(date, unit, amount) {
    const next = asDate(date);
    if (unit === 'month') {
      const oldDay = next.getUTCDate();
      next.setUTCDate(1);
      next.setUTCMonth(next.getUTCMonth() + amount + 1, 0);
      next.setUTCDate(Math.min(oldDay, next.getUTCDate()));
    } else {
      next.setUTCDate(next.getUTCDate() + amount * (unit === 'week' ? 7 : 1));
    }
    return toDate(next);
  }
  const format = (date, options) => new Intl.DateTimeFormat(language(), { ...options, timeZone: 'UTC' }).format(asDate(date));
  const fullDate = date => format(date, {weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'});
  function formatPeriod(model) {
    if (model.view === 'day') return fullDate(model.date);
    if (model.view === 'month') return format(model.date, {month: 'long', year: 'numeric'});
    return new Intl.DateTimeFormat(language(), {day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC'})
      .formatRange(asDate(model.from), asDate(model.to));
  }

  const params = new URLSearchParams(location.search);
  let view = views.includes(params.get('view')) ? params.get('view') : 'week';
  let date = validDate(params.get('date')) ? params.get('date') : null;
  let zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  let model = null, plans = [], todayPlans = [], planReady = false;
  let requesting = false, mutating = false, request = null;
  let edited = null, editorTrigger = null;
  const schedule = $('[data-study-schedule]');
  const focus = $('[data-study-focus]');
  const editor = $('[data-study-editor]');
  const form = $('[data-study-form]');
  const fields = form.elements;
  let lastFailure = null, retryTarget = null;

  async function api(path, {method = 'GET', body, signal} = {}) {
    const headers = new Headers({Accept: 'application/json'});
    if (body) {
      headers.set('Content-Type', 'application/json');
    }
    if (method !== 'GET') {
      const header = document.querySelector('meta[name="_csrf_header"]')?.content;
      const token = document.querySelector('meta[name="_csrf"]')?.content;
      if (header && token) headers.set(header, token);
    }
    const response = await fetch('/api/calendar' + path, {
      method, signal, headers, credentials: 'same-origin', cache: 'no-store',
      body: body ? JSON.stringify(body) : undefined
    });
    if (response.redirected || response.status === 401 || response.status === 403) throw new Error('AUTH');
    if (response.status === 204) return null;
    const data = await response.json().catch(() => null);
    if (!response.ok || !data) throw new Error(data?.error || 'REQUEST_FAILED');
    return data;
  }

  function errorText(error, fallback) {
    const codes = {AUTH: 'auth', VALIDATION: 'invalid', INVALID_RANGE: 'invalid', INVALID_DATE: 'invalidDate',
      INVALID_CALENDAR_DATE: 'invalidDate', INVALID_CALENDAR_VIEW: 'invalidView', INVALID_TIME_ZONE: 'invalidZone',
      PLAN_LIMIT: 'limit', PLAN_NOT_FOUND: 'missing'};
    return t(codes[error?.message] || fallback);
  }

  function showError(error, fallback) {
    lastFailure = {error, fallback};
    $('[data-study-error]').textContent = errorText(error, fallback);
    $('[data-study-feedback]').hidden = false;
    $('[data-study-retry]').hidden = error?.message === 'AUTH';
  }
  function clearError() {
    lastFailure = null;
    $('[data-study-feedback]').hidden = true;
  }

  function updateControls() {
    const blocked = !model || mutating || requesting;
    root.querySelectorAll('[data-study-view]').forEach(control => {
      control.setAttribute('aria-pressed', String(control.dataset.studyView === view));
      control.disabled = mutating;
    });
    $('[data-study-add]').disabled = blocked || !planReady;
    $('[data-study-retry]').disabled = mutating;
    $('[data-study-nav="prev"]').disabled = blocked || !shift(model.date, view, -1);
    $('[data-study-nav="next"]').disabled = blocked || !shift(model.date, view, 1);
    $('[data-study-nav="today"]').disabled = blocked || model.date === model.today;
    const picker = $('[data-study-date]');
    picker.disabled = blocked;
    if (model) picker.value = model.date;
    schedule.setAttribute('aria-busy', String(requesting));
    focus.setAttribute('aria-busy', String(requesting));
    schedule.inert = requesting || mutating;
    focus.inert = requesting || mutating;
    $('[data-study-loading]').hidden = !requesting;
  }

  const planRange = (from, to, signal) => api('/plan?' + new URLSearchParams({from, to}), {signal});
  async function loadCalendar(args, signal) {
    try {
      return await api('?' + args, {signal});
    } catch (error) {
      if (error?.message !== 'INVALID_TIME_ZONE' || !args.has('zone')) throw error;
      args.delete('zone');
      const fallback = await api('?' + args, {signal});
      zone = null;
      return fallback;
    }
  }

  async function load({updateUrl = true} = {}) {
    if (mutating) return;
    request?.abort();
    const active = new AbortController();
    request = active;
    requesting = true;
    retryTarget = null;
    const target = {view, date};
    clearError();
    updateControls();
    const args = new URLSearchParams({view});
    if (date) args.set('date', date);
    if (zone) args.set('zone', zone);
    try {
      const result = await loadCalendar(args, active.signal);
      const range = planRange(result.from, result.to, active.signal);
      const todayInRange = result.today >= result.from && result.today <= result.to;
      // Reuse the visible range when it already contains today.
      const focused = todayInRange
        ? range.then(items => items.filter(item => item.date === result.today))
        : planRange(result.today, result.today, active.signal);
      const [rangeResult, focusResult] = await Promise.allSettled([range, focused]);
      if (active !== request) return;

      model = result;
      view = result.view;
      date = result.date;
      plans = rangeResult.status === 'fulfilled' ? rangeResult.value : [];
      todayPlans = focusResult.status === 'fulfilled' ? focusResult.value : [];
      planReady = rangeResult.status === 'fulfilled' && focusResult.status === 'fulfilled';
      if (!planReady) showError(rangeResult.reason || focusResult.reason, 'planError');

      if (updateUrl) {
        const url = new URL(location.href);
        url.searchParams.set('view', view);
        url.searchParams.set('date', date);
        history.replaceState(history.state, '', url);
      }
      render();
    } catch (error) {
      if (active !== request || active.signal.aborted) return;
      retryTarget = target;
      if (model) {
        view = model.view;
        date = model.date;
      }
      showError(error, 'calendarError');
    } finally {
      if (request === active) {
        requesting = false;
        updateControls();
      }
    }
  }

  function linksWithinApp(url) {
    if (typeof url !== 'string' || !url.startsWith('/') || url.startsWith('//')) return null;
    try {
      const target = new URL(url, location.origin);
      return target.origin === location.origin && /^\/(?:assignments|courses|projects)(?:\/|$)/.test(target.pathname)
        ? target.href : null;
    } catch {
      return null;
    }
  }
  function eventCard(event, {compact = false, withDate = false} = {}) {
    const href = linksWithinApp(event.url);
    const card = node(href ? 'a' : 'div', 'study-item');
    if (href) card.href = href;
    card.dataset.status = event.status;
    card.dataset.deadlineKind = event.kind;
    card.append(node('span', compact ? 'study-item-title study-item-compact' : 'study-item-title', event.title));
    const status = t(event.status);
    card.append(node('small', 'study-item-meta', withDate
      ? status + ' · ' + format(event.date, {day: 'numeric', month: 'short'}) : status));
    if (!compact) {
      card.append(node('span', 'study-item-context', [t(event.kind), event.context].filter(Boolean).join(' · ')));
    }
    return card;
  }

  function planCard(plan, {compact = false} = {}) {
    const card = button('study-item', null, event => openEditor(plan.date, plan, event.currentTarget));
    card.dataset.status = plan.completed ? 'done' : 'plan';
    card.dataset.studyPlanEdit = String(plan.id);
    card.append(node('span', compact ? 'study-item-title study-item-compact' : 'study-item-title', plan.title));
    const duration = t('minutes', {minutes: plan.minutes});
    card.append(node('small', 'study-item-meta', (plan.completed ? t('done') : t('plan')) + ' · ' + duration));
    if (!compact && plan.description) card.append(node('small', 'study-item-description', plan.description));
    if (compact) return card;

    const row = node('div', 'study-plan-row');
    const checkbox = node('input');
    checkbox.type = 'checkbox';
    checkbox.checked = plan.completed;
    checkbox.disabled = !planReady;
    checkbox.dataset.studyToggle = String(plan.id);
    checkbox.setAttribute('aria-label', t('toggle', {title: plan.title}));
    checkbox.addEventListener('change', event => toggleCompletion(plan, event.currentTarget));
    row.append(checkbox, card);
    return row;
  }

  const goTo = (nextView, nextDate) => {
    if (mutating) return;
    view = nextView;
    date = nextDate;
    load();
  };
  function planButton(date, small = false) {
    const label = t('addFor', {date: fullDate(date)});
    const control = button(small ? 'study-day-add' : 'study-button', small ? null : t('addToday'),
      event => openEditor(date, null, event.currentTarget));
    control.dataset.studyAdd = '';
    control.setAttribute('aria-label', label);
    control.title = label;
    control.disabled = !planReady;
    if (small) control.append(icon('plus-lg'));
    return control;
  }

  function buildDay(day, dayEvents, dayPlans) {
    const section = node('section', 'study-day');
    section.dataset.studyDay = day;
    const shortDay = asDate(day).getUTCDate();
    const dayLabel = fullDate(day);
    if (day === model.today) section.dataset.today = '';
    section.setAttribute('aria-label', dayLabel);

    const header = node('header', 'study-day-header');
    const label = view === 'day'
      ? node('div', 'study-day-link')
      : button('study-day-link', null, () => goTo('day', day));
    if (view !== 'day') label.dataset.studyOpenDay = day;
    const total = dayEvents.length + dayPlans.length;
    label.setAttribute('aria-label', dayLabel + (total ? ' · ' + t('count', {count: total}) : ''));
    if (day === model.today) label.setAttribute('aria-current', 'date');
    label.append(node('span', 'study-day-number', shortDay));
    if (view !== 'month') label.append(node('span', 'study-day-name', format(day, {weekday: 'short'})));
    header.append(label, planButton(day, true));
    section.append(header);

    const items = node('ul', 'study-items');
    const compact = view === 'month';
    const display = [...dayEvents.map(event => () => eventCard(event, {compact})),
      ...dayPlans.map(plan => () => planCard(plan, {compact}))];
    for (const renderItem of (compact ? display.slice(0, 2) : display)) {
      const item = node('li');
      item.append(renderItem());
      items.append(item);
    }
    if (compact && total > 2) {
      const more = node('li');
      more.append(button('study-more', t('more', {count: total - 2}), () => goTo('day', day)));
      items.append(more);
    }
    if (!compact && total === 0) {
      const empty = node('li', 'study-day-note', t('emptyDay'));
      empty.dataset.studyEmpty = '';
      items.append(empty);
    }
    section.append(items);

    if (compact && total > 0) {
      const mini = node('div', 'study-month-mobile');
      mini.setAttribute('aria-hidden', 'true');
      const statuses = new Set(dayEvents.map(event => ['overdue','today','soon'].includes(event.status)
        ? event.status === 'overdue' ? 'overdue' : 'soon' : 'upcoming'));
      if (dayPlans.length) statuses.add('plan');
      statuses.forEach(status => mini.append(node('i', 'study-dot study-dot-' + status)));
      mini.append(node('small', 'study-month-count', total));
      section.append(mini);
    }
    return section;
  }

  function byDate(items) {
    const groups = new Map();
    for (const item of items) {
      if (!groups.has(item.date)) groups.set(item.date, []);
      groups.get(item.date).push(item);
    }
    return groups;
  }

  function renderSchedule() {
    const grid = node('div', 'study-grid');
    grid.dataset.view = model.view;
    let slots = 0;
    if (model.view === 'month') {
      // July 1, 2024 was Monday. A fixed week provides localized weekday abbreviations.
      for (let value = 1; value <= 7; value++) {
        const cell = node('span', 'study-weekday', format('2024-07-0' + value, {weekday: 'short'}));
        grid.append(cell);
      }
      const offset = (asDate(model.from).getUTCDay() + 6) % 7;
      for (let i = 0; i < offset; i++) grid.append(node('div', 'study-day-empty'));
      slots = offset;
    }
    const deadlines = byDate(model.deadlines);
    const studyPlan = byDate(plans);
    for (let day = model.from; day && day <= model.to; day = shift(day, 'day', 1)) {
      grid.append(buildDay(day, deadlines.get(day) || [], studyPlan.get(day) || []));
      slots++;
    }
    if (model.view === 'month') {
      while (slots % 7 !== 0) {
        grid.append(node('div', 'study-day-empty'));
        slots++;
      }
    }
    schedule.replaceChildren(grid);
    $('[data-study-period]').textContent = formatPeriod(model);
    $('[data-study-zone] span').textContent = model.zone;
    $('[data-study-zone]').hidden = !model.zone;
    $('[data-study-legend]').hidden = false;
  }

  function focusGroup(title, items, empty, renderItem) {
    const group = node('section', 'study-focus-group');
    group.append(node('h4', '', t(title)));
    if (!items.length) {
      group.append(node('p', 'study-focus-empty', t(empty)));
      return group;
    }
    const list = node('ul', 'study-focus-list');
    for (const entry of items) {
      const item = node('li');
      item.append(renderItem(entry));
      list.append(item);
    }
    group.append(list);
    return group;
  }
  function renderFocus() {
    $('[data-study-today-date]').textContent = fullDate(model.today);
    focus.replaceChildren(
      focusGroup('focusPlan', todayPlans, planReady ? 'noFocusPlan' : 'planError', plan => planCard(plan)),
      focusGroup('focusDue', model.focus, 'noFocusDue', event => eventCard(event, {withDate: true})),
      planButton(model.today)
    );
  }
  function render() {
    if (!model) return;
    renderSchedule();
    renderFocus();
  }

  function localize() {
    for (const element of root.querySelectorAll('[data-study-copy]')) element.textContent = t(element.dataset.studyCopy);
    for (const element of root.querySelectorAll('[data-study-label]')) element.setAttribute('aria-label', t(element.dataset.studyLabel));
    if (lastFailure) showError(lastFailure.error, lastFailure.fallback);
    if (editor.open) $('[data-study-editor-title]').textContent = t(edited ? 'edit' : 'add');
    render();
    updateControls();
  }

  function openEditor(day, plan, trigger) {
    if (!planReady || requesting || mutating) return;
    edited = plan;
    editorTrigger = trigger;
    form.reset();
    fields.title.value = plan?.title || '';
    fields.date.value = day;
    fields.minutes.value = plan?.minutes || 30;
    fields.description.value = plan?.description || '';
    fields.completed.checked = plan?.completed || false;
    fields.title.setCustomValidity('');
    $('[data-study-delete]').hidden = !plan;
    $('[data-study-editor-title]').textContent = t(plan ? 'edit' : 'add');
    $('[data-study-editor-error]').hidden = true;
    editor.showModal();
  }

  function closeEditor() {
    if (mutating) return;
    editor.close();
    const restore = editorTrigger?.isConnected ? editorTrigger : root.querySelector('.study-heading [data-study-add]');
    restore?.focus();
  }
  function setMutation(active, deleting = false) {
    mutating = active;
    for (const control of form.querySelectorAll('input, textarea, button')) control.disabled = active;
    $('[data-study-save]').textContent = t(active && !deleting ? 'saving' : 'save');
    $('[data-study-delete]').textContent = t(active && deleting ? 'deleting' : 'delete');
    updateControls();
  }

  function updateSavedPlan(id, plan) {
    const replace = (items, shouldInclude) => {
      const updated = items.filter(item => item.id !== id);
      if (plan && shouldInclude(plan.date)) updated.push(plan);
      return updated.sort((left, right) => left.date.localeCompare(right.date) || Number(left.id) - Number(right.id));
    };
    plans = replace(plans, value => value >= model.from && value <= model.to);
    todayPlans = replace(todayPlans, value => value === model.today);
    render();
  }
  async function savePlan(event) {
    event.preventDefault();
    if (mutating || !form.reportValidity()) return;
    const title = fields.title.value.trim();
    if (!title) {
      fields.title.setCustomValidity(t('invalid'));
      fields.title.reportValidity();
      return;
    }
    if (!validDate(fields.date.value)) {
      showEditorError(new Error('INVALID_DATE'));
      return;
    }
    const payload = {
      title, date: fields.date.value, minutes: Number(fields.minutes.value),
      description: fields.description.value.trim(), completed: fields.completed.checked
    };
    const editId = edited?.id;
    $('[data-study-editor-error]').hidden = true;
    setMutation(true);
    try {
      const updated = await api(editId ? '/plan/' + encodeURIComponent(editId) : '/plan',
        {method: editId ? 'PUT' : 'POST', body: payload});
      updateSavedPlan(updated.id, updated);
      setMutation(false);
      closeEditor();
      $('[data-study-announcement]').textContent = t('saved');
      // Keep the saved item visible when it was moved outside the displayed range.
      if (updated.date < model.from || updated.date > model.to) goTo(view, updated.date);
    } catch (error) {
      showEditorError(error);
    } finally {
      setMutation(false);
    }
  }

  function showEditorError(error) {
    const banner = $('[data-study-editor-error]');
    banner.textContent = errorText(error, 'writeError');
    banner.hidden = false;
  }

  async function deletePlan() {
    if (mutating || !edited || !window.confirm(t('confirmDelete', {title: edited.title}))) return;
    const itemId = edited.id;
    $('[data-study-editor-error]').hidden = true;
    setMutation(true, true);
    try {
      await api('/plan/' + encodeURIComponent(itemId), {method: 'DELETE'});
      updateSavedPlan(itemId, null);
      setMutation(false);
      closeEditor();
      $('[data-study-announcement]').textContent = t('deleted');
    } catch (error) {
      showEditorError(error);
    } finally {
      setMutation(false);
    }
  }

  async function toggleCompletion(plan, control) {
    if (mutating || requesting) return;
    const inFocus = focus.contains(control);
    setMutation(true);
    try {
      const {title, date, minutes, description} = plan;
      const updated = await api('/plan/' + encodeURIComponent(plan.id), {
        method: 'PUT', body: {title, date, minutes, description, completed: control.checked}
      });
      updateSavedPlan(plan.id, updated);
      $('[data-study-announcement]').textContent = t('saved');
    } catch (error) {
      control.checked = plan.completed;
      showError(error, 'writeError');
    } finally {
      setMutation(false);
      const container = inFocus ? focus : schedule;
      const restore = [...container.querySelectorAll('[data-study-toggle]')].find(box => box.dataset.studyToggle === String(plan.id));
      restore?.focus();
    }
  }

  root.querySelectorAll('[data-study-view]').forEach(control => control.addEventListener('click', () => {
    const nextView = control.dataset.studyView;
    if (nextView !== view) goTo(nextView, date);
  }));
  root.querySelectorAll('[data-study-nav]').forEach(control => control.addEventListener('click', () => {
    if (!model) return;
    const direction = control.dataset.studyNav;
    const nextDate = direction === 'today' ? model.today : shift(model.date, view, direction === 'next' ? 1 : -1);
    if (nextDate) goTo(view, nextDate);
  }));
  $('[data-study-date]').addEventListener('change', event => {
    if (validDate(event.currentTarget.value)) goTo(view, event.currentTarget.value);
  });
  $('[data-study-add]').addEventListener('click', event => model && openEditor(model.date, null, event.currentTarget));
  $('[data-study-retry]').addEventListener('click', () => {
    if (retryTarget) goTo(retryTarget.view, retryTarget.date);
    else load();
  });
  $('[data-study-close]').addEventListener('click', closeEditor);
  $('[data-study-cancel]').addEventListener('click', closeEditor);
  $('[data-study-delete]').addEventListener('click', deletePlan);
  fields.title.addEventListener('input', () => fields.title.setCustomValidity(''));
  editor.addEventListener('cancel', event => {
    event.preventDefault();
    closeEditor();
  });
  form.addEventListener('submit', savePlan);
  document.addEventListener('studhub:language', localize);

  localize();
  load({updateUrl: false});
})();
