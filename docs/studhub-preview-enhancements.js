(() => {
  'use strict';

  const main = document.getElementById('main-content');
  const storageKey = 'studhub-preview-data-v2';
  const initialData = {
    courses: [
      { id: 1, title: 'Основы Java', teacher: 'Алия Садыкова', description: 'Переменные, коллекции и объектно-ориентированное программирование.' },
      { id: 2, title: 'Базы данных', teacher: 'Марат Ермеков', description: 'Реляционные модели, SQL-запросы и проектирование схем.' },
      { id: 3, title: 'Web-разработка', teacher: 'Данияр Омаров', description: 'Основы HTML, CSS, JavaScript и адаптивного дизайна.' },
      { id: 4, title: 'Алгоритмы', teacher: 'Елена Ким', description: 'Структуры данных, поиск и оценка сложности алгоритмов.' }
    ],
    assignments: [
      { id: 1, title: 'Подготовить конспект по коллекциям', courseId: 1, due: '2026-10-25', completed: false, description: '' },
      { id: 2, title: 'Сдать лабораторную работу № 3', courseId: 2, due: '2026-10-26', completed: false, description: '' },
      { id: 3, title: 'Подготовить презентацию проекта', courseId: 3, due: '2026-10-29', completed: false, description: '' },
      { id: 4, title: 'Прочитать главу 5', courseId: 4, due: '2026-10-14', completed: true, description: '' }
    ],
    users: [
      { id: 1, name: 'Алмаз', email: 'almaz@studhub.local', role: 'Студент' },
      { id: 2, name: 'Мария Садыкова', email: 'maria@example.com', role: 'Администратор' }
    ]
  };
  let state;
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || 'null');
    state = saved && Array.isArray(saved.courses) && Array.isArray(saved.assignments) && Array.isArray(saved.users)
      ? saved : structuredClone(initialData);
  } catch {
    state = structuredClone(initialData);
  }
  const save = () => localStorage.setItem(storageKey, JSON.stringify(state));
  const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[char]));
  const courseById = (id) => state.courses.find((course) => course.id === Number(id));
  const assignmentById = (id) => state.assignments.find((assignment) => assignment.id === Number(id));
  const courseName = (id) => courseById(id)?.title || 'Без курса';
  const nextId = (items) => Math.max(0, ...items.map((item) => item.id)) + 1;
  const countText = (count, one, few, many) => `${count} ${count % 10 === 1 && count % 100 !== 11 ? one : count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 12 || count % 100 > 14) ? few : many}`;
  const dateText = (iso) => iso ? new Intl.DateTimeFormat(window.StudHubI18n?.language || 'ru', { day: 'numeric', month: 'short' }).format(new Date(`${iso}T12:00:00`)) : '';
  const pending = () => state.assignments.filter((assignment) => !assignment.completed).sort((a, b) => a.due.localeCompare(b.due));

  function setPage(title, route, html, activeRoute) {
    main.innerHTML = html;
    document.body.dataset.page = activeRoute || route;
    document.querySelectorAll('[data-preview-title]').forEach((node) => node.textContent = title);
    document.querySelectorAll('.sidebar-link[data-preview-route]').forEach((node) => node.classList.toggle('active', node.dataset.previewRoute === (activeRoute || route)));
    history.replaceState(null, '', `#${route}`);
    updateNotifications();
    main.querySelector('h2')?.setAttribute('tabindex', '-1');
    main.querySelector('h2')?.focus({ preventScroll: true });
  }

  function courseCard(course) {
    const count = state.assignments.filter((assignment) => assignment.courseId === course.id).length;
    const title = esc(course.title);
    return `<article class="course-card" data-preview-course-card="${course.id}"><a class="course-cover course-cover-link" href="#course-${course.id}" data-preview-route="course-${course.id}" aria-label="Открыть курс ${title}"><span class="cover-initial">${esc(course.title.slice(0, 1).toUpperCase())}</span><span class="cover-mark"><i class="bi bi-bookmark"></i></span></a><div class="course-card-body"><span class="course-kicker">КУРС</span><h3><a class="course-title-link" href="#course-${course.id}" data-preview-route="course-${course.id}">${title}</a></h3><div class="teacher-line"><span class="teacher-avatar">${esc(course.teacher.slice(0, 1).toUpperCase())}</span><span>${esc(course.teacher)}</span></div><p class="course-description">${esc(course.description || 'Описание не добавлено')}</p></div><div class="course-card-footer"><a class="text-link" href="#assignments-course-${course.id}" data-preview-route="assignments-course-${course.id}"><i class="bi bi-list-check"></i> Задания курса</a><span class="role-badge">${countText(count, 'задание', 'задания', 'заданий')}</span></div></article>`;
  }

  function showCourse(id) {
    const course = courseById(id);
    if (!course) { window.previewBaseRender('courses'); window.previewToast('Курс не найден.'); return; }
    const tasks = state.assignments.filter((assignment) => assignment.courseId === course.id).sort((a, b) => a.due.localeCompare(b.due));
    const done = tasks.filter((task) => task.completed).length;
    const percent = tasks.length ? Math.round(done * 100 / tasks.length) : 0;
    const taskRows = tasks.length ? tasks.map((task) => `<a class="course-detail-task" href="#assignment-${task.id}" data-preview-route="assignment-${task.id}"><span class="course-detail-task-status"><i class="bi ${task.completed ? 'bi-check2-circle' : 'bi-circle'}"></i></span><span class="course-detail-task-copy"><strong>${esc(task.title)}</strong><small>${task.completed ? 'Выполнено' : 'В процессе'}</small></span><time>${dateText(task.due)}</time><i class="bi bi-arrow-up-right"></i></a>`).join('') : '<p class="panel-empty">В этом курсе пока нет заданий.</p>';
    setPage(course.title, `course-${course.id}`, `<div class="form-breadcrumb"><a href="#courses" data-preview-route="courses">Курсы</a><i class="bi bi-chevron-right"></i><span>${esc(course.title)}</span></div><section class="page-intro course-detail-intro"><div><p class="eyebrow">ОБЗОР КУРСА</p><h2>${esc(course.title)}</h2><p class="muted-copy">${esc(course.description || 'Описание не добавлено')}</p></div><a class="btn btn-light" href="#course-edit-${course.id}" data-preview-route="course-edit-${course.id}"><i class="bi bi-pencil"></i> Изменить курс</a></section><div class="course-detail-layout"><section class="surface course-detail-main"><div class="course-detail-section-heading"><div><p class="eyebrow">ЗАДАНИЯ КУРСА</p><h3>Задания этого курса</h3></div><a class="btn btn-primary" href="#assignment-new-course-${course.id}" data-preview-route="assignment-new-course-${course.id}"><i class="bi bi-plus-lg"></i> Новое задание</a></div><div class="course-detail-task-list">${taskRows}</div><a class="rail-all course-detail-all" href="#assignments-course-${course.id}" data-preview-route="assignments-course-${course.id}"><span>Все задания</span><i class="bi bi-arrow-right"></i></a></section><aside class="surface course-detail-side"><span class="panel-icon"><i class="bi bi-person"></i></span><p class="eyebrow">Преподаватель</p><strong class="course-detail-teacher">${esc(course.teacher)}</strong><div class="course-detail-divider"></div><p class="eyebrow">Выполнение заданий</p><div class="course-detail-progress"><div class="progress-ring" style="--progress:${percent}%" role="img" aria-label="Выполнение заданий"><span>${percent}%</span></div><p><strong>${done}</strong> из <strong>${tasks.length}</strong><br>Выполнено</p></div></aside></div>`, 'courses');
  }

  function showCourseForm(id) {
    const course = id ? courseById(id) : null;
    if (id && !course) { window.previewBaseRender('courses'); return; }
    const route = course ? `course-edit-${id}` : 'course-new';
    const title = course ? 'Изменить курс' : 'Новый курс';
    setPage(title, route, `<div class="form-breadcrumb"><a href="#courses" data-preview-route="courses">Курсы</a><i class="bi bi-chevron-right"></i><span>${title}</span></div><section class="form-card surface preview-form-card"><header class="form-heading"><span class="form-icon"><i class="bi bi-journals"></i></span><div><p class="eyebrow">КУРСЫ</p><h2>${title}</h2><p class="muted-copy">Добавьте название, преподавателя и описание.</p></div></header><form data-preview-course-form data-id="${course?.id || ''}"><div class="field-group"><label for="previewCourseTitle">Название курса</label><input id="previewCourseTitle" name="title" required minlength="2" maxlength="120" value="${esc(course?.title || '')}"></div><div class="field-group"><label for="previewCourseTeacher">Преподаватель</label><input id="previewCourseTeacher" name="teacher" required maxlength="80" value="${esc(course?.teacher || '')}"></div><div class="field-group"><label for="previewCourseDescription">Описание</label><textarea id="previewCourseDescription" name="description" rows="5" maxlength="1000">${esc(course?.description || '')}</textarea></div><div class="form-actions"><a class="btn btn-light" href="${course ? `#course-${course.id}` : '#courses'}" data-preview-route="${course ? `course-${course.id}` : 'courses'}">Отмена</a>${course ? `<button class="btn btn-light preview-danger" type="button" data-preview-delete-course="${course.id}">Удалить курс</button>` : ''}<button class="btn btn-primary" type="submit">Сохранить</button></div></form></section>`, 'courses');
  }

  function assignmentRow(task) {
    const done = task.completed;
    return `<div class="deadline-row" data-state="${done ? 'completed' : 'pending'}" data-preview-assignment="${task.id}"><button class="deadline-check" type="button" data-preview-toggle-assignment="${task.id}" aria-label="${done ? 'Вернуть в работу' : 'Отметить выполненным'}"><i class="bi ${done ? 'bi-check2-circle' : 'bi-circle'}"></i></button><span class="deadline-copy"><a class="assignment-title" href="#assignment-${task.id}" data-preview-route="assignment-${task.id}">${esc(task.title)}</a><small>${esc(courseName(task.courseId))}</small></span><span class="due-chip"><small>${done ? 'Выполнено' : 'Срок выполнения'}</small><strong>${dateText(task.due)}</strong></span></div>`;
  }

  function decorateAssignments(courseId) {
    const tasks = courseId ? state.assignments.filter((task) => task.courseId === Number(courseId)) : state.assignments;
    const list = main.querySelector('[data-preview-assignments]');
    if (!list) return;
    list.innerHTML = tasks.map(assignmentRow).join('') || '<p class="panel-empty">Заданий пока нет. Добавьте первое задание.</p>';
    const counts = main.querySelectorAll('[data-preview-filter] b');
    if (counts.length === 3) {
      counts[0].textContent = tasks.length;
      counts[1].textContent = tasks.filter((task) => !task.completed).length;
      counts[2].textContent = tasks.filter((task) => task.completed).length;
    }
    if (courseId) {
      const course = courseById(courseId);
      main.querySelector('.page-intro h2').textContent = `Задания: ${course?.title || 'курс'}`;
      main.querySelector('.page-intro .muted-copy').textContent = 'Задания выбранного курса.';
      const createButton = main.querySelector('[data-preview-action="Новое задание"]');
      if (createButton) createButton.dataset.previewCourseId = courseId;
      main.querySelector('.page-intro').insertAdjacentHTML('beforeend', `<a class="text-link" href="#course-${courseId}" data-preview-route="course-${courseId}"><i class="bi bi-arrow-left"></i> К курсу</a>`);
    }
    if (!main.querySelector('[data-preview-route="calendar"]')) main.querySelector('.page-intro').insertAdjacentHTML('beforeend', '<a class="btn btn-light" href="#calendar" data-preview-route="calendar"><i class="bi bi-calendar3"></i> Календарь</a>');
  }

  function showAssignmentForm(id, selectedCourseId) {
    const task = id ? assignmentById(id) : null;
    if (id && !task) { window.previewBaseRender('assignments'); return; }
    const route = task ? `assignment-${task.id}` : (selectedCourseId ? `assignment-new-course-${selectedCourseId}` : 'assignment-new');
    const title = task ? 'Задание' : 'Новое задание';
    const selectedId = task?.courseId || Number(selectedCourseId) || state.courses[0]?.id || 0;
    const options = state.courses.map((course) => `<option value="${course.id}" ${course.id === selectedId ? 'selected' : ''}>${esc(course.title)}</option>`).join('');
    setPage(title, route, `<div class="form-breadcrumb"><a href="#assignments" data-preview-route="assignments">Задания</a><i class="bi bi-chevron-right"></i><span>${title}</span></div><section class="form-card surface preview-form-card"><header class="form-heading"><span class="form-icon"><i class="bi bi-check2-square"></i></span><div><p class="eyebrow">ЗАДАНИЯ</p><h2>${task ? esc(task.title) : title}</h2><p class="muted-copy">Срок и курс помогают держать учебный план в порядке.</p></div></header><form data-preview-assignment-form data-id="${task?.id || ''}"><div class="field-group"><label for="previewAssignmentTitle">Название задания</label><input id="previewAssignmentTitle" name="title" required minlength="2" maxlength="150" value="${esc(task?.title || '')}"></div><div class="form-grid-two"><div class="field-group"><label for="previewAssignmentCourse">Курс</label><select id="previewAssignmentCourse" name="courseId" required>${options}</select></div><div class="field-group"><label for="previewAssignmentDue">Срок выполнения</label><input id="previewAssignmentDue" name="due" type="date" required value="${esc(task?.due || '')}"></div></div><div class="field-group"><label for="previewAssignmentDescription">Описание</label><textarea id="previewAssignmentDescription" name="description" rows="4" maxlength="1500">${esc(task?.description || '')}</textarea></div><label class="preview-checkbox"><input type="checkbox" name="completed" ${task?.completed ? 'checked' : ''}> Выполнено</label><div class="form-actions"><a class="btn btn-light" href="${selectedCourseId ? `#course-${selectedCourseId}` : '#assignments'}" data-preview-route="${selectedCourseId ? `course-${selectedCourseId}` : 'assignments'}">Отмена</a>${task ? `<button class="btn btn-light preview-danger" type="button" data-preview-delete-assignment="${task.id}">Удалить</button>` : ''}<button class="btn btn-primary" type="submit">Сохранить</button></div></form></section>`, 'assignments');
  }

  function showUserForm(id) {
    const user = id ? state.users.find((item) => item.id === Number(id)) : null;
    if (id && !user) { window.previewBaseRender('users'); return; }
    const title = user ? 'Изменить пользователя' : 'Добавить пользователя';
    const route = user ? `user-${user.id}` : 'user-new';
    setPage(title, route, `<div class="form-breadcrumb"><a href="#users" data-preview-route="users">Пользователи</a><i class="bi bi-chevron-right"></i><span>${title}</span></div><section class="form-card surface preview-form-card"><header class="form-heading"><span class="form-icon"><i class="bi bi-people"></i></span><div><p class="eyebrow">ПОЛЬЗОВАТЕЛИ</p><h2>${title}</h2><p class="muted-copy">Демонстрационные данные участников StudHub.</p></div></header><form data-preview-user-form data-id="${user?.id || ''}"><div class="field-group"><label for="previewUserName">Полное имя</label><input id="previewUserName" name="name" required minlength="2" maxlength="80" value="${esc(user?.name || '')}"></div><div class="field-group"><label for="previewUserEmail">Электронная почта</label><input id="previewUserEmail" name="email" type="email" required maxlength="120" value="${esc(user?.email || '')}"></div><div class="field-group"><label for="previewUserRole">Роль</label><select id="previewUserRole" name="role"><option ${user?.role !== 'Администратор' ? 'selected' : ''}>Студент</option><option ${user?.role === 'Администратор' ? 'selected' : ''}>Администратор</option></select></div><div class="form-actions"><a class="btn btn-light" href="#users" data-preview-route="users">Отмена</a>${user ? `<button class="btn btn-light preview-danger" type="button" data-preview-delete-user="${user.id}">Удалить</button>` : ''}<button class="btn btn-primary" type="submit">Сохранить</button></div></form></section>`, 'users');
  }

  function decorateUsers() {
    const rows = state.users.map((user) => `<tr data-preview-user-row><td><span class="user-avatar">${esc(user.name.slice(0, 1).toUpperCase())}</span>${esc(user.name)}</td><td>${esc(user.email)}</td><td><span class="status-badge ${user.role === 'Администратор' ? 'admin' : ''}">${esc(user.role)}</span></td><td><a class="icon-button" href="#user-${user.id}" data-preview-route="user-${user.id}" aria-label="Редактировать ${esc(user.name)}"><i class="bi bi-pencil"></i></a></td></tr>`).join('');
    const tbody = main.querySelector('tbody');
    if (!tbody) return;
    tbody.innerHTML = rows || '<tr><td colspan="4">Пользователей пока нет.</td></tr>';
    const search = main.querySelector('.search-box input');
    search?.setAttribute('aria-label', 'Поиск пользователей');
    search?.addEventListener('input', () => {
      const query = search.value.trim().toLocaleLowerCase('ru');
      main.querySelectorAll('[data-preview-user-row]').forEach((row) => row.classList.toggle('d-none', !row.textContent.toLocaleLowerCase('ru').includes(query)));
    });
  }

  let calendarMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
  const localISO = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  function showCalendar() {
    const year = calendarMonth.getFullYear();
    const month = calendarMonth.getMonth();
    const first = new Date(year, month, 1);
    const start = new Date(year, month, 1 - (first.getDay() + 6) % 7);
    const days = Array.from({ length: 42 }, (_, offset) => {
      const day = new Date(start.getFullYear(), start.getMonth(), start.getDate() + offset);
      const iso = localISO(day);
      const tasks = state.assignments.filter((task) => task.due === iso);
      return `<div class="preview-calendar-day ${day.getMonth() !== month ? 'is-outside' : ''} ${iso === localISO(new Date()) ? 'is-today' : ''}"><span>${day.getDate()}</span>${tasks.map((task) => `<a href="#assignment-${task.id}" data-preview-route="assignment-${task.id}" title="${esc(task.title)}">${esc(task.title)}</a>`).join('')}</div>`;
    }).join('');
    const monthTitle = new Intl.DateTimeFormat(window.StudHubI18n?.language || 'ru', { month: 'long', year: 'numeric' }).format(first);
    setPage('Календарь', 'calendar', `<div class="form-breadcrumb"><a href="#assignments" data-preview-route="assignments">Задания</a><i class="bi bi-chevron-right"></i><span>Календарь</span></div><section class="surface preview-calendar"><header class="preview-calendar-head"><div><p class="eyebrow">ДЕДЛАЙНЫ</p><h2>Календарь заданий</h2><p class="muted-copy">Откройте задание прямо из дня его выполнения.</p></div><a class="btn btn-primary" href="#assignment-new" data-preview-route="assignment-new"><i class="bi bi-plus-lg"></i> Новое задание</a></header><div class="preview-calendar-toolbar"><h3>${esc(monthTitle)}</h3><div><button class="btn btn-light" type="button" data-preview-calendar-today>Сегодня</button><button class="icon-button" type="button" data-preview-calendar-step="-1" aria-label="Предыдущий месяц"><i class="bi bi-chevron-left"></i></button><button class="icon-button" type="button" data-preview-calendar-step="1" aria-label="Следующий месяц"><i class="bi bi-chevron-right"></i></button></div></div><div class="preview-calendar-weekdays"><span>Пн</span><span>Вт</span><span>Ср</span><span>Чт</span><span>Пт</span><span>Сб</span><span>Вс</span></div><div class="preview-calendar-grid">${days}</div></section>`, 'assignments');
  }

  function decorateDashboard() {
    window.StudHubProjects?.dashboard();
    const dateLabel = main.querySelector('.dashboard-date');
    if (dateLabel) dateLabel.innerHTML = `<i class="bi bi-calendar3"></i>${new Intl.DateTimeFormat(window.StudHubI18n?.language || 'ru',{day:'numeric',month:'long',year:'numeric',timeZone:'Asia/Qyzylorda'}).format(new Date())}`;
    const numbers = main.querySelectorAll('.stat-card .stat-number');
    const completed = state.assignments.filter((task) => task.completed).length;
    const values = [state.courses.length, state.assignments.length, state.assignments.length - completed, completed];
    numbers.forEach((node, index) => { node.textContent = values[index]; node.dataset.count = 'false'; });
    const upcoming = pending();
    const focus = main.querySelector('.focus-list');
    if (focus) focus.innerHTML = upcoming.slice(0, 3).map((task) => `<a href="#assignment-${task.id}" data-preview-route="assignment-${task.id}"><span class="focus-check"><i class="bi bi-circle"></i></span><span><strong>${esc(task.title)}</strong><small>${esc(courseName(task.courseId))}</small></span><time>${dateText(task.due)}</time></a>`).join('') || '<p class="panel-empty">План пока свободен</p>';
    const rail = main.querySelector('.rail-list');
    if (rail) rail.innerHTML = upcoming.slice(0, 5).map((task) => `<a class="rail-item" href="#assignment-${task.id}" data-preview-route="assignment-${task.id}"><span class="rail-marker"></span><span class="rail-copy"><strong>${esc(task.title)}</strong><small>${esc(courseName(task.courseId))}</small></span><time>${dateText(task.due)}</time></a>`).join('') || '<p class="rail-empty">Ближайших заданий нет.</p>';
    const percent = state.assignments.length ? Math.round(completed * 100 / state.assignments.length) : 0;
    const progress = main.querySelector('.progress-panel');
    if (progress) {
      const language = window.StudHubI18n?.language || 'ru';
      progress.querySelector('.panel-description').textContent = language === 'en' ? `${completed} of ${state.assignments.length} completed` : language === 'kk' ? `${state.assignments.length} тапсырманың ${completed} орындалды` : `${completed} из ${state.assignments.length} выполнено`;
      progress.querySelector('.progress-ring span').textContent = `${percent}%`;
      progress.querySelector('.progress-ring').style.setProperty('--progress', `${percent}%`);
      const meta = progress.querySelectorAll('.progress-meta strong');
      if (meta.length === 2) { meta[0].textContent = completed; meta[1].textContent = state.assignments.length - completed; }
    }
    const today = new Date();
    const monday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (today.getDay() + 6) % 7);
    const week = main.querySelector('.week-grid');
    if (week) week.innerHTML = Array.from({ length: 7 }, (_, offset) => {
      const day = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + offset);
      const iso = localISO(day);
      const tasks = upcoming.filter((task) => task.due === iso);
      return `<div class="week-day ${iso === localISO(today) ? 'is-today' : ''}"><div class="week-day-heading"><span>${new Intl.DateTimeFormat(window.StudHubI18n?.language || 'ru', { weekday: 'short' }).format(day)}</span><strong>${day.getDate()}</strong></div>${tasks.map((task) => `<a class="week-task" href="#assignment-${task.id}" data-preview-route="assignment-${task.id}">${esc(task.title)}</a>`).join('') || '<span class="week-day-empty">—</span>'}</div>`;
    }).join('');
    const calendarLink = main.querySelector('.week-heading a');
    if (calendarLink) { calendarLink.href = '#calendar'; calendarLink.dataset.previewRoute = 'calendar'; }
  }

  function updateNotifications() {
    const panel = document.getElementById('topbarNotifications');
    const heading = panel?.querySelector('.popover-heading');
    if (!panel || !heading) return;
    [...panel.children].filter((node) => node !== heading).forEach((node) => node.remove());
    const alerts = pending().filter((task) => task.due <= new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10)).slice(0, 5);
    panel.insertAdjacentHTML('beforeend', alerts.length ? alerts.map((task) => `<a class="notification-item" href="#assignment-${task.id}" data-preview-route="assignment-${task.id}"><span class="notification-icon"><i class="bi bi-calendar-event"></i></span><span><strong>${esc(task.title)}</strong><small>${esc(courseName(task.courseId))} · ${dateText(task.due)}</small></span></a>`).join('') : '<p class="popover-empty">Ближайших дедлайнов нет.</p>');
    document.querySelector('.notification-dot')?.classList.toggle('d-none', !alerts.length);
  }

  window.previewApp = {
    openAssignmentDraft(task) {
      showAssignmentForm();
      const form = main.querySelector('[data-preview-assignment-form]');
      if (!form || typeof task.title !== 'string' || typeof task.description !== 'string') return;
      form.elements.title.value = task.title.slice(0,150);
      form.elements.description.value = task.description.slice(0,1400);
      form.elements.courseId.focus();
    },
    updateProfile(profile) {
      const user = state.users.find(user => user.id === profile.id);
      if (user) { user.name = profile.name; user.email = profile.email; save(); }
    },
    render(route) {
      if (/^projects(?:-(new|members))?$/.test(route)) {
        setPage('Проекты', route, '<div data-project-root></div>', 'projects');
        window.StudHubProjects?.mount(route.split('-')[1] || '');
        return true;
      }
      if (route === 'chat') {
        setPage('Чат', 'chat', '<div data-chat-root></div>');
        window.StudHubChat?.mount();
        return true;
      }
      if (route === 'profile' || /^settings-(appearance|profile|privacy|about)$/.test(route)) {
        const section = route === 'profile' ? 'profile' : route.slice(9);
        if (window.StudHubSettings) window.StudHubSettings.open(section);
        else window.studhubPendingSettings = section;
        return true;
      }
      if (/^course-\d+$/.test(route)) { showCourse(route.slice(7)); return true; }
      if (route === 'course-new') { showCourseForm(); return true; }
      if (/^course-edit-\d+$/.test(route)) { showCourseForm(route.slice(12)); return true; }
      if (route === 'assignment-new') { showAssignmentForm(); return true; }
      if (/^assignment-new-course-\d+$/.test(route)) { showAssignmentForm(null, route.slice(22)); return true; }
      if (/^assignment-\d+$/.test(route)) { showAssignmentForm(route.slice(11)); return true; }
      if (/^assignments-course-\d+$/.test(route)) {
        const courseId = route.slice(19);
        window.previewBaseRender('assignments');
        decorateAssignments(courseId);
        history.replaceState(null, '', `#${route}`);
        return true;
      }
      if (route === 'user-new') { showUserForm(); return true; }
      if (/^user-\d+$/.test(route)) { showUserForm(route.slice(5)); return true; }
      if (route === 'calendar') { showCalendar(); return true; }
      return false;
    },
    decorate(route) {
      const displayName = localStorage.getItem('studhub-preview-name') || 'Алмаз';
      const avatar = document.querySelector('[data-profile-avatar]');
      if (avatar) avatar.textContent = displayName.trim().slice(0, 1).toLocaleUpperCase('ru');
      if (route === 'courses') {
        main.querySelector('[data-preview-courses]').innerHTML = state.courses.map(courseCard).join('') || '<p class="panel-empty">Курсов пока нет. Создайте первый курс.</p>';
        main.querySelector('.count-badge').textContent = countText(state.courses.length, 'курс', 'курса', 'курсов');
      }
      if (route === 'assignments') decorateAssignments();
      if (route === 'users') decorateUsers();
      if (route === 'dashboard') decorateDashboard();
      window.StudHubSettings?.refreshIdentity();
      if (route === 'login' || route === 'register') {
        main.querySelector('[data-preview-auth]')?.insertAdjacentHTML('beforeend', '<p class="preview-auth-note">Демонстрационный режим: пароль не проверяется и не сохраняется. Настоящий вход работает в приложении.</p>');
      }
      updateNotifications();
    }
  };

  document.addEventListener('click', (event) => {
    const action = event.target.closest('[data-preview-action]');
    if (action?.dataset.previewAction === 'Новый курс') {
      event.preventDefault(); event.stopPropagation(); window.previewBaseRender('course-new');
    }
    if (action?.dataset.previewAction === 'Новое задание') {
      event.preventDefault(); event.stopPropagation();
      window.previewBaseRender(action.dataset.previewCourseId ? `assignment-new-course-${action.dataset.previewCourseId}` : 'assignment-new');
    }
    if (action?.dataset.previewAction === 'Добавить пользователя') {
      event.preventDefault(); event.stopPropagation(); window.previewBaseRender('user-new');
    }
    const remove = event.target.closest('[data-preview-delete-course]');
    if (remove) {
      event.preventDefault(); event.stopPropagation();
      const id = Number(remove.dataset.previewDeleteCourse);
      const course = courseById(id);
      if (!course || !confirm(`Удалить курс «${course.title}» и его задания из демонстрационных данных?`)) return;
      state.courses = state.courses.filter((item) => item.id !== id);
      state.assignments = state.assignments.filter((item) => item.courseId !== id);
      save(); window.previewBaseRender('courses'); window.previewToast('Курс удалён из предпросмотра.');
    }
    const removeAssignment = event.target.closest('[data-preview-delete-assignment]');
    if (removeAssignment) {
      event.preventDefault(); event.stopPropagation();
      const id = Number(removeAssignment.dataset.previewDeleteAssignment);
      const task = assignmentById(id);
      if (!task || !confirm(`Удалить задание «${task.title}» из демонстрационных данных?`)) return;
      state.assignments = state.assignments.filter((item) => item.id !== id);
      save(); window.previewBaseRender('assignments'); window.previewToast('Задание удалено из предпросмотра.');
    }
    const toggle = event.target.closest('[data-preview-toggle-assignment]');
    if (toggle) {
      event.preventDefault(); event.stopPropagation();
      const task = assignmentById(toggle.dataset.previewToggleAssignment);
      if (!task) return;
      task.completed = !task.completed;
      save(); window.previewBaseRender('assignments');
    }
    const removeUser = event.target.closest('[data-preview-delete-user]');
    if (removeUser) {
      event.preventDefault(); event.stopPropagation();
      const id = Number(removeUser.dataset.previewDeleteUser);
      const user = state.users.find((item) => item.id === id);
      if (!user || !confirm(`Удалить пользователя «${user.name}» из демонстрационных данных?`)) return;
      state.users = state.users.filter((item) => item.id !== id);
      save(); window.previewBaseRender('users'); window.previewToast('Пользователь удалён из предпросмотра.');
    }
    const monthStep = event.target.closest('[data-preview-calendar-step]');
    if (monthStep) {
      event.preventDefault(); event.stopPropagation();
      calendarMonth = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + Number(monthStep.dataset.previewCalendarStep), 1);
      showCalendar();
    }
    if (event.target.closest('[data-preview-calendar-today]')) {
      event.preventDefault(); event.stopPropagation();
      calendarMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
      showCalendar();
    }
    if (event.target.closest('[data-preview-reset]')) {
      event.preventDefault(); event.stopPropagation();
      if (!confirm('Сбросить изменения в демонстрационных курсах, заданиях и пользователях?')) return;
      state = structuredClone(initialData);
      save(); localStorage.removeItem('studhub-preview-name');
      document.dispatchEvent(new CustomEvent('studhub:preview-reset'));
      window.previewBaseRender('dashboard'); window.previewToast('Демонстрационные данные восстановлены.');
    }
  }, true);

  document.addEventListener('submit', (event) => {
    const form = event.target.closest('[data-preview-auth]');
    if (!form) return;
    event.preventDefault(); event.stopPropagation();
    if (!form.reportValidity()) return;
    const fields = [...form.querySelectorAll('input')];
    const registering = document.body.dataset.page === 'register';
    const email = fields[registering ? 1 : 0].value.trim().toLocaleLowerCase();
    if (registering) {
      if (state.users.some((item) => item.email.toLocaleLowerCase() === email)) { window.previewToast('Такой email уже есть в предпросмотре.'); return; }
      const name = fields[0].value.trim();
      state.users.push({ id: nextId(state.users), name, email, role: 'Студент' });
      save();
      localStorage.setItem('studhub-preview-name', name);
      window.previewBaseRender('dashboard');
      window.previewToast('Демонстрационный профиль создан.');
    } else {
      const user = state.users.find((item) => item.email.toLocaleLowerCase() === email);
      if (!user) { window.previewToast('Пользователь не найден в предпросмотре.'); return; }
      localStorage.setItem('studhub-preview-name', user.name);
      window.previewBaseRender('dashboard');
      window.previewToast('Демонстрационный вход выполнен.');
    }
  }, true);

  document.addEventListener('submit', (event) => {
    const form = event.target.closest('[data-preview-course-form]');
    if (!form) return;
    event.preventDefault(); event.stopPropagation();
    if (!form.reportValidity()) return;
    const id = Number(form.dataset.id) || nextId(state.courses);
    const course = { id, title: form.elements.title.value.trim(), teacher: form.elements.teacher.value.trim(), description: form.elements.description.value.trim() };
    if (form.dataset.id) state.courses = state.courses.map((item) => item.id === id ? course : item);
    else state.courses.push(course);
    save(); window.previewBaseRender(`course-${id}`); window.previewToast('Курс сохранён в предпросмотре.');
  }, true);

  document.addEventListener('submit', (event) => {
    const form = event.target.closest('[data-preview-user-form]');
    if (!form) return;
    event.preventDefault(); event.stopPropagation();
    if (!form.reportValidity()) return;
    const id = Number(form.dataset.id) || nextId(state.users);
    const user = { id, name: form.elements.name.value.trim(), email: form.elements.email.value.trim(), role: form.elements.role.value };
    if (state.users.some((item) => item.email.toLocaleLowerCase() === user.email.toLocaleLowerCase() && item.id !== id)) { window.previewToast('Пользователь с таким email уже есть.'); return; }
    if (form.dataset.id) state.users = state.users.map((item) => item.id === id ? user : item);
    else state.users.push(user);
    save(); window.previewBaseRender('users'); window.previewToast('Пользователь сохранён в предпросмотре.');
  }, true);

  document.addEventListener('submit', (event) => {
    const form = event.target.closest('[data-preview-assignment-form]');
    if (!form) return;
    event.preventDefault(); event.stopPropagation();
    if (!form.reportValidity()) return;
    const id = Number(form.dataset.id) || nextId(state.assignments);
    const task = { id, title: form.elements.title.value.trim(), courseId: Number(form.elements.courseId.value), due: form.elements.due.value, description: form.elements.description.value.trim(), completed: form.elements.completed.checked };
    if (form.dataset.id) state.assignments = state.assignments.map((item) => item.id === id ? task : item);
    else state.assignments.push(task);
    save(); window.previewBaseRender(`assignment-${id}`); window.previewToast('Задание сохранено в предпросмотре.');
  }, true);

  document.querySelector('#topbarSettings')?.insertAdjacentHTML('beforeend', '<button class="popover-action preview-reset-action" type="button" data-preview-reset><i class="bi bi-arrow-counterclockwise"></i><span>Сбросить данные предпросмотра</span></button>');
  const refreshLanguage = () => {
    const route = location.hash.slice(1) || 'dashboard';
    if (!route.startsWith('settings-') && route !== 'profile') window.previewBaseRender(route);
  };
  document.addEventListener('studhub:i18n-ready',refreshLanguage);
  document.addEventListener('studhub:language',refreshLanguage);
  window.previewBaseRender(location.hash.slice(1) || 'dashboard');
})();
