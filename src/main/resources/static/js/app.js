(() => {
  'use strict';

  const body = document.body;
  const messages = document.getElementById('uiMessages');
  const msg = (key, fallback = '') => messages?.dataset[key] || fallback;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

  function setTheme(theme) {
    theme = theme === 'light' ? 'light' : 'dark';
    document.documentElement.dataset.theme = theme;
    body.dataset.theme = theme;
    body.dataset.bsTheme = theme;
    localStorage.setItem('studhub-theme', theme);
    document.querySelectorAll('[data-theme-toggle] i').forEach((icon) => {
      icon.className = theme === 'dark' ? 'bi bi-sun' : 'bi bi-moon-stars';
    });
  }
  window.StudHubTheme = { set: setTheme };

  const preferredTheme = localStorage.getItem('studhub-theme') || 'light';
  setTheme(preferredTheme);
  document.querySelector('[data-theme-toggle]')?.addEventListener('click', () => {
    setTheme(body.dataset.theme === 'dark' ? 'light' : 'dark');
  });

  const panelTriggers = [...document.querySelectorAll('[data-panel-trigger]')];
  const closePanels = (restoreFocus = false) => {
    panelTriggers.forEach((trigger) => {
      const panel = document.getElementById(trigger.getAttribute('aria-controls'));
      if (!panel || panel.hidden) return;
      panel.hidden = true;
      panel.inert = true;
      trigger.setAttribute('aria-expanded', 'false');
      if (restoreFocus) trigger.focus();
    });
  };
  panelTriggers.forEach((trigger) => trigger.addEventListener('click', () => {
    const panel = document.getElementById(trigger.getAttribute('aria-controls'));
    if (!panel) return;
    const opening = panel.hidden;
    closePanels();
    panel.hidden = !opening;
    panel.inert = !opening;
    trigger.setAttribute('aria-expanded', String(opening));
    if (opening) panel.querySelector('input,button,a')?.focus();
  }));
  document.addEventListener('click', (event) => {
    if (!event.target.closest('.topbar-tool')) closePanels();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closePanels(true);
  });

  const sidebar = document.querySelector('.sidebar');
  const backdrop = document.querySelector('.drawer-backdrop');
  const openDrawer = document.querySelector('[data-drawer-open]');
  const closeDrawerButtons = document.querySelectorAll('[data-drawer-close]');
  const collapseSidebar = document.querySelector('[data-sidebar-collapse]');
  const drawerQuery = window.matchMedia('(max-width: 767.98px)');
  const railQuery = window.matchMedia('(min-width: 768px) and (max-width: 1199.98px)');

  function closeSidebar() {
    sidebar?.classList.remove('mobile-open');
    body.classList.remove('sidebar-expanded');
    backdrop?.classList.remove('show');
    openDrawer?.setAttribute('aria-expanded', 'false');
    if (drawerQuery.matches) openDrawer?.focus();
  }

  openDrawer?.addEventListener('click', () => {
    if (drawerQuery.matches) {
      sidebar?.classList.add('mobile-open');
      backdrop?.classList.add('show');
      openDrawer.setAttribute('aria-expanded', 'true');
      sidebar?.querySelector('a')?.focus();
    } else if (railQuery.matches) {
      const expanded = body.classList.toggle('sidebar-expanded');
      openDrawer.setAttribute('aria-expanded', String(expanded));
      backdrop?.classList.toggle('show', expanded);
    }
  });

  closeDrawerButtons.forEach((element) => element.addEventListener('click', closeSidebar));
  sidebar?.addEventListener('click', (event) => {
    if (event.target.closest('a[href]') && (drawerQuery.matches || railQuery.matches)) closeSidebar();
  });
  collapseSidebar?.addEventListener('click', () => {
    if (railQuery.matches) {
      const expanded = body.classList.toggle('sidebar-expanded');
      collapseSidebar.setAttribute('aria-expanded', String(expanded));
      backdrop?.classList.toggle('show', expanded);
      return;
    }
    body.classList.toggle('sidebar-collapsed');
    localStorage.setItem('studhub-sidebar-collapsed', String(body.classList.contains('sidebar-collapsed')));
  });

  if (window.matchMedia('(min-width: 1200px)').matches
      && localStorage.getItem('studhub-sidebar-collapsed') === 'true') {
    body.classList.add('sidebar-collapsed');
  }

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && (sidebar?.classList.contains('mobile-open') || body.classList.contains('sidebar-expanded'))) {
      closeSidebar();
    }
  });

  const path = window.location.pathname;
  document.querySelectorAll('[data-nav]').forEach((link) => {
    const route = link.dataset.nav;
    const active = route === '/' ? path === '/' : path.startsWith(route);
    link.classList.toggle('active', active);
    if (active) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });

  function toast(message, kind = 'success') {
    const region = document.getElementById('toastRegion');
    if (!region || !message) return;
    const item = document.createElement('div');
    item.className = `app-toast ${kind}`;
    item.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    const icon = document.createElement('i');
    icon.className = `bi ${kind === 'error' ? 'bi-exclamation-circle' : kind === 'info' ? 'bi-info-circle' : 'bi-check-circle'}`;
    icon.setAttribute('aria-hidden', 'true');
    const copy = document.createElement('span');
    copy.className = 'toast-content';
    copy.textContent = message;
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'toast-dismiss';
    close.setAttribute('aria-label', msg('toastClose', 'Закрыть'));
    close.innerHTML = '<i class="bi bi-x-lg" aria-hidden="true"></i>';
    const progress = document.createElement('span');
    progress.className = 'toast-progress';
    item.append(icon, copy, close, progress);
    region.append(item);
    let timer = window.setTimeout(() => item.remove(), 4000);
    item.addEventListener('mouseenter', () => {
      window.clearTimeout(timer);
      progress.style.animationPlayState = 'paused';
    });
    item.addEventListener('mouseleave', () => {
      progress.style.animationPlayState = 'running';
      timer = window.setTimeout(() => item.remove(), 1800);
    });
    close.addEventListener('click', () => item.remove());
  }

  const params = new URLSearchParams(window.location.search);
  if (params.has('saved')) toast(msg('toastSaved', 'Изменения сохранены'));
  if (params.has('deleted')) toast(msg('toastDeleted', 'Запись удалена'));

  // Counters show the real value immediately; frequently-read data must not count up.

  const ring = document.querySelector('.progress-ring');
  if (ring) {
    const percent = Number(ring.textContent.replace('%', '')) || 0;
    ring.style.setProperty('--progress', `${percent}%`);
  }

  document.querySelectorAll('[title]').forEach((element) => {
    if (window.bootstrap?.Tooltip && element.classList.contains('icon-button')) {
      window.bootstrap.Tooltip.getOrCreateInstance(element, { trigger: 'hover focus', placement: 'top' });
    }
  });

  document.querySelectorAll('[data-password-toggle]').forEach((button) => {
    button.addEventListener('click', () => {
      const input = document.getElementById(button.dataset.passwordToggle);
      if (!input) return;
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      button.innerHTML = `<i class="bi ${show ? 'bi-eye-slash' : 'bi-eye'}" aria-hidden="true"></i>`;
      button.setAttribute('aria-pressed', String(show));
      button.setAttribute('aria-label', show ? msg('passwordHide', 'Скрыть пароль') : msg('passwordShow', 'Показать пароль'));
      button.title = show ? msg('passwordHide', 'Скрыть пароль') : msg('passwordShow', 'Показать пароль');
    });
  });

  document.querySelectorAll('[data-count-chars]').forEach((field) => {
    const update = () => {
      const counter = field.closest('.field-group')?.querySelector('[data-char-count]');
      if (counter) counter.textContent = String(field.value.length);
    };
    field.addEventListener('input', update);
    update();
  });

  document.querySelectorAll('[data-password-strength]').forEach((input) => {
    const meter = input.closest('.field-group')?.querySelector('[data-strength-meter]');
    const hint = input.closest('.field-group')?.querySelector('[data-strength-text]');
    const update = () => {
      const value = input.value;
      const strength = value.length < 8 ? (value.length ? 1 : 0)
        : 1 + Number(/[A-ZА-Я]/.test(value)) + Number(/[0-9]/.test(value)) + Number(/[^A-Za-zА-Яа-я0-9]/.test(value));
      meter?.setAttribute('data-strength', String(Math.min(strength, 4)));
      if (hint) hint.textContent = value.length < 8 ? msg('passwordLength', 'Минимум 8 символов') : strength >= 4 ? msg('passwordStrong', 'Надёжный пароль') : strength >= 3 ? msg('passwordOk', 'Добавьте цифры или символы') : msg('passwordWeak', 'Надёжность пароля');
      const confirmation = document.querySelector('[data-confirm-password]');
      if (confirmation && confirmation.value) confirmation.dispatchEvent(new Event('input', { bubbles: true }));
    };
    input.addEventListener('input', update);
  });

  document.querySelectorAll('[data-confirm-password]').forEach((input) => {
    const update = () => {
      const password = document.querySelector('[data-password-strength]');
      const error = document.querySelector('[data-confirm-error]');
      const mismatch = input.value.length > 0 && input.value !== password?.value;
      input.setCustomValidity(mismatch ? msg('passwordMismatch', 'Пароли не совпадают') : '');
      input.classList.toggle('is-invalid', mismatch);
      if (error) error.textContent = mismatch ? msg('passwordMismatch', 'Пароли не совпадают') : '';
    };
    input.addEventListener('input', update);
    input.addEventListener('blur', update);
  });

  document.querySelectorAll('[data-validate-form]').forEach((form) => {
    form.addEventListener('focusout', (event) => {
      if (event.target.matches('input,select,textarea')) {
        event.target.classList.toggle('is-invalid', !event.target.checkValidity());
      }
    });
    form.addEventListener('submit', (event) => {
      const invalid = [...form.querySelectorAll('input,select,textarea')].find((field) => !field.disabled && !field.checkValidity());
      if (invalid) {
        event.preventDefault();
        invalid.classList.add('is-invalid');
        invalid.focus();
        invalid.reportValidity();
        return;
      }
      setLoading(form);
    });
  });

  function setLoading(form) {
    const button = form.querySelector('button[type="submit"]');
    if (!button) return;
    button.disabled = true;
    button.querySelector('.spinner-border')?.classList.remove('d-none');
  }

  document.querySelectorAll('[data-submit-loading]').forEach((form) => {
    form.addEventListener('submit', () => setLoading(form));
  });

  let dirtyForm = false;
  document.querySelectorAll('[data-unsaved-form]').forEach((form) => {
    form.addEventListener('input', () => { dirtyForm = true; });
    form.addEventListener('change', () => { dirtyForm = true; });
    form.addEventListener('submit', () => { dirtyForm = false; });
  });
  window.addEventListener('beforeunload', (event) => {
    if (dirtyForm) {
      event.preventDefault();
      event.returnValue = '';
    }
  });

  const debounce = (callback, delay = 150) => {
    let timeout;
    return (...args) => {
      window.clearTimeout(timeout);
      timeout = window.setTimeout(() => callback(...args), delay);
    };
  };

  const courseGrid = document.querySelector('[data-course-grid]');
  const courseTable = document.querySelector('[data-course-table]');
  if (courseGrid) {
    const cards = [...courseGrid.querySelectorAll('.course-card')];
    const tableRows = [...(courseTable?.querySelectorAll('tbody tr') || [])];
    const coverClasses = ['cover-1', 'cover-2', 'cover-3', 'cover-4', 'cover-5', 'cover-6'];
    cards.forEach((card) => {
      const hash = [...(card.dataset.title || '')].reduce((sum, char) => sum + char.charCodeAt(0), 0);
      card.querySelector('[data-course-cover]')?.classList.add(coverClasses[hash % coverClasses.length]);
    });
    const search = document.querySelector('[data-course-search]');
    const sortSelect = document.querySelector('[data-course-sort]');
    const filterEmpty = document.querySelector('.filter-empty');
    const emptyInitial = document.querySelector('.list-surface > .empty-state:not(.filter-empty)');
    const applySearch = () => {
      const value = (search?.value || '').trim().toLocaleLowerCase('ru');
      let visible = 0;
      cards.forEach((card) => {
        const match = `${card.dataset.title} ${card.dataset.teacher}`.toLocaleLowerCase('ru').includes(value);
        card.classList.toggle('d-none', !match);
        if (match) visible++;
      });
      tableRows.forEach((row) => {
        const match = `${row.dataset.title} ${row.dataset.teacher}`.toLocaleLowerCase('ru').includes(value);
        row.classList.toggle('d-none', !match);
      });
      filterEmpty?.classList.toggle('d-none', visible > 0 || !cards.length);
      if (emptyInitial) emptyInitial.classList.toggle('d-none', cards.length > 0);
    };
    search?.addEventListener('input', debounce(applySearch));
    if (params.has('search') && search) {
      search.value = params.get('search') || '';
      applySearch();
    }
    document.querySelector('[data-reset-course-search]')?.addEventListener('click', () => {
      if (search) search.value = '';
      applySearch();
      search?.focus();
    });
    sortSelect?.addEventListener('change', () => {
      const sorter = (a, b) => {
        const mode = sortSelect.value;
        const aValue = mode === 'teacher' ? a.dataset.teacher : a.dataset.title;
        const bValue = mode === 'teacher' ? b.dataset.teacher : b.dataset.title;
        const result = aValue.localeCompare(bValue, 'ru');
        return mode === 'title-desc' ? -result : result;
      };
      [...cards].sort(sorter).forEach((card) => courseGrid.append(card));
      if (courseTable) [...tableRows].sort(sorter).forEach((row) => courseTable.querySelector('tbody')?.append(row));
    });
    const setView = (view, save = true) => {
      courseGrid.classList.toggle('d-none', view !== 'grid');
      courseTable?.classList.toggle('d-none', view !== 'table');
      document.querySelectorAll('[data-course-view]').forEach((button) => button.classList.toggle('active', button.dataset.courseView === view));
      if (save) localStorage.setItem('studhub-course-view', view);
    };
    document.querySelectorAll('[data-course-view]').forEach((button) => button.addEventListener('click', () => setView(button.dataset.courseView)));
    setView(localStorage.getItem('studhub-course-view') || 'grid', false);
  }

  const assignmentList = document.querySelector('[data-assignment-list]');
  if (assignmentList) {
    const rows = [...assignmentList.querySelectorAll('.assignment-row')];
    const search = document.querySelector('[data-assignment-search]');
    const courseSelect = document.querySelector('[data-course-filter]');
    const prioritySelect = document.querySelector('[data-priority-filter]');
    const filterEmpty = document.querySelector('.filter-empty');
    const initialEmpty = document.querySelector('.list-empty');
    let statusFilter = 'all';
    const now = new Date(); now.setHours(0, 0, 0, 0);
    let calendarMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    const calendarGrid = document.querySelector('[data-calendar-grid]');
    const calendarHeading = document.querySelector('[data-calendar-heading]');
    const calendarWeekdays = document.querySelector('[data-calendar-weekdays]');
    const calendarTabs = [...document.querySelectorAll('[data-assignment-view-tab]')];
    const calendarPanels = [...document.querySelectorAll('[data-assignment-view-panel]')];
    const renderCalendar = () => {
      if (!calendarGrid) return;
      calendarHeading.textContent = new Intl.DateTimeFormat(window.StudHubI18n?.language || 'ru', { month: 'long', year: 'numeric' }).format(calendarMonth);
      if (!calendarWeekdays.childElementCount) {
        const monday = new Date(2024, 0, 1);
        for (let day = 0; day < 7; day++) {
          const label = document.createElement('span');
          label.textContent = new Intl.DateTimeFormat(window.StudHubI18n?.language || 'ru', { weekday: 'short' }).format(new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + day));
          calendarWeekdays.append(label);
        }
      }
      calendarGrid.replaceChildren();
      const firstDay = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth(), 1);
      const offset = (firstDay.getDay() + 6) % 7;
      const filteredRows = rows.filter((row) => !row.classList.contains('d-none'));
      document.querySelector('[data-calendar-empty]')?.toggleAttribute('hidden', filteredRows.length > 0);
      const monthPrefix = `${calendarMonth.getFullYear()}-${String(calendarMonth.getMonth() + 1).padStart(2, '0')}-`;
      const todayKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
      const monthCount = filteredRows.filter((row) => row.dataset.due.startsWith(monthPrefix)).length;
      const overdueCount = filteredRows.filter((row) => row.dataset.completed !== 'true' && row.dataset.due < todayKey).length;
      document.querySelector('[data-calendar-month-count]')?.replaceChildren(String(monthCount));
      document.querySelector('[data-calendar-overdue-count]')?.replaceChildren(String(overdueCount));
      document.querySelector('[data-calendar-done-count]')?.replaceChildren(String(filteredRows.filter((row) => row.dataset.completed === 'true').length));
      for (let cell = 0; cell < 42; cell++) {
        const date = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth(), cell - offset + 1);
        const dateKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
        const dayCell = document.createElement('div');
        dayCell.className = 'calendar-day';
        if (date.getMonth() !== calendarMonth.getMonth()) dayCell.classList.add('is-outside-month');
        if (dateKey === `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`) dayCell.classList.add('is-today');
        const number = document.createElement('span');
        number.className = 'calendar-day-number';
        number.textContent = String(date.getDate());
        dayCell.append(number);
        filteredRows.filter((row) => row.dataset.due === dateKey).forEach((row) => {
          const task = document.createElement('a');
          task.className = `calendar-task priority-${(row.dataset.priority || 'MEDIUM').toLowerCase()}`;
          if (row.dataset.completed === 'true') task.classList.add('is-complete');
          task.href = `/assignments/${encodeURIComponent(row.dataset.id)}/edit`;
          task.textContent = row.dataset.title;
          task.title = `${row.dataset.title} · ${row.dataset.courseName}`;
          dayCell.append(task);
        });
        calendarGrid.append(dayCell);
      }
    };
    const setAssignmentView = (view) => {
      calendarTabs.forEach((tab) => {
        const active = tab.dataset.assignmentViewTab === view;
        tab.classList.toggle('active', active);
        tab.setAttribute('aria-selected', String(active));
        tab.tabIndex = active ? 0 : -1;
      });
      calendarPanels.forEach((panel) => { panel.hidden = panel.dataset.assignmentViewPanel !== view; });
      if (filterEmpty) filterEmpty.classList.toggle('d-none', view !== 'list' || rows.some((row) => !row.classList.contains('d-none')));
      if (view === 'calendar') renderCalendar();
    };
    calendarTabs.forEach((tab, index) => {
      tab.addEventListener('click', () => setAssignmentView(tab.dataset.assignmentViewTab));
      tab.addEventListener('keydown', (event) => {
        if (!['ArrowLeft', 'ArrowRight'].includes(event.key) || calendarTabs.length < 2) return;
        event.preventDefault();
        const next = calendarTabs[(index + (event.key === 'ArrowRight' ? 1 : calendarTabs.length - 1)) % calendarTabs.length];
        next.focus();
        setAssignmentView(next.dataset.assignmentViewTab);
      });
    });
    document.querySelector('[data-calendar-prev]')?.addEventListener('click', () => { calendarMonth.setMonth(calendarMonth.getMonth() - 1); renderCalendar(); });
    document.querySelector('[data-calendar-next]')?.addEventListener('click', () => { calendarMonth.setMonth(calendarMonth.getMonth() + 1); renderCalendar(); });
    document.querySelector('[data-calendar-today]')?.addEventListener('click', () => { calendarMonth = new Date(now.getFullYear(), now.getMonth(), 1); renderCalendar(); });
    const relToday = msg('relativeToday', 'Срок сегодня');
    const relTomorrow = msg('relativeTomorrow', 'Срок завтра');
    rows.forEach((row) => {
      const due = new Date(`${row.dataset.due}T00:00:00`);
      const days = Math.round((due - now) / 86400000);
      const label = row.querySelector('.relative-date');
      if (label) {
        label.textContent = days === 0 ? relToday : days === 1 ? relTomorrow : days < 0 ? msg('relativeAgo', '{0} дн. назад').replace('{0}', String(Math.abs(days))) : msg('relativeDays', 'Осталось дней: {0}').replace('{0}', String(days));
        if (row.dataset.completed !== 'true' && days < 0) label.classList.add('is-overdue');
        else if (row.dataset.completed !== 'true' && days <= 3) label.classList.add('is-soon');
      }
      if (row.dataset.completed !== 'true' && days < 0) row.querySelector('.status-badge')?.classList.add('overdue');
    });
    const applyFilters = () => {
      const query = (search?.value || '').trim().toLocaleLowerCase('ru');
      const course = courseSelect?.value || '';
      const priority = prioritySelect?.value || '';
      let shown = 0;
      rows.forEach((row) => {
        const completed = row.dataset.completed === 'true';
        const days = Math.floor((new Date(`${row.dataset.due}T00:00:00`) - now) / 86400000);
        const statusMatch = statusFilter === 'all' || (statusFilter === 'completed' ? completed : statusFilter === 'overdue' ? (!completed && days < 0) : (!completed && days >= 0));
        const queryMatch = `${row.dataset.title} ${row.dataset.courseName}`.toLocaleLowerCase('ru').includes(query);
        const courseMatch = !course || row.dataset.course === course;
        const priorityMatch = !priority || row.dataset.priority === priority;
        const visible = statusMatch && queryMatch && courseMatch && priorityMatch;
        row.classList.toggle('d-none', !visible);
        if (visible) shown++;
      });
      const listPanel = document.querySelector('[data-assignment-view-panel="list"]');
      filterEmpty?.classList.toggle('d-none', shown > 0 || !rows.length || Boolean(listPanel?.hidden));
      if (initialEmpty) initialEmpty.classList.toggle('d-none', rows.length > 0);
      if (!document.querySelector('[data-assignment-view-panel="calendar"]')?.hidden) renderCalendar();
    };
    document.querySelectorAll('[data-assignment-filter]').forEach((button) => button.addEventListener('click', () => {
      statusFilter = button.dataset.assignmentFilter;
      document.querySelectorAll('[data-assignment-filter]').forEach((item) => item.classList.toggle('active', item === button));
      applyFilters();
    }));
    search?.addEventListener('input', debounce(applyFilters));
    if (params.has('search') && search) {
      search.value = params.get('search') || '';
      applyFilters();
    }
    courseSelect?.addEventListener('change', applyFilters);
    prioritySelect?.addEventListener('change', applyFilters);
    document.querySelector('[data-reset-filters]')?.addEventListener('click', () => {
      statusFilter = 'all';
      if (search) search.value = '';
      if (courseSelect) courseSelect.value = '';
      if (prioritySelect) prioritySelect.value = '';
      document.querySelectorAll('[data-assignment-filter]').forEach((item) => item.classList.toggle('active', item.dataset.assignmentFilter === 'all'));
      applyFilters();
    });
    const sortBy = (key, ascending) => {
      rows.sort((a, b) => {
        const left = key === 'due' ? a.dataset.due : key === 'course' ? a.dataset.courseName : key === 'priority' ? a.dataset.priority : a.dataset.title;
        const right = key === 'due' ? b.dataset.due : key === 'course' ? b.dataset.courseName : key === 'priority' ? b.dataset.priority : b.dataset.title;
        const comparison = key === 'priority' ? ({ LOW: 1, MEDIUM: 2, HIGH: 3 }[left] - { LOW: 1, MEDIUM: 2, HIGH: 3 }[right]) : key === 'due' ? left.localeCompare(right) : left.localeCompare(right, 'ru');
        return ascending ? comparison : -comparison;
      }).forEach((row) => assignmentList.append(row));
    };
    sortBy('due', true);
    document.querySelectorAll('[data-sort]').forEach((button) => button.addEventListener('click', () => {
      const ascending = button.dataset.direction !== 'asc';
      document.querySelectorAll('[data-sort]').forEach((item) => { item.dataset.direction = ''; item.classList.remove('sorted'); });
      button.dataset.direction = ascending ? 'asc' : 'desc';
      button.classList.add('sorted');
      const icon = button.querySelector('i');
      if (icon) icon.className = ascending ? 'bi bi-arrow-up-short' : 'bi bi-arrow-down-short';
      sortBy(button.dataset.sort, ascending);
      applyFilters();
    }));
    if (courseSelect && new URLSearchParams(location.search).has('course')) applyFilters();
    if (new URLSearchParams(location.search).get('view') === 'calendar') setAssignmentView('calendar');
  }

  function animateDelete(form, row) {
    if (form.dataset.confirmed === 'true') return;
    form.dataset.confirmed = 'true';
    if (row) {
      row.classList.add('row-removing');
      window.setTimeout(() => form.submit(), 250);
    } else form.submit();
  }

  document.querySelectorAll('[data-delete-course],[data-delete-assignment]').forEach((button) => {
    button.addEventListener('click', () => {
      const modal = button.dataset.deleteCourse ? document.getElementById('deleteCourseModal') : document.getElementById('deleteAssignmentModal');
      const message = modal?.querySelector('[data-delete-message]');
      const form = modal?.querySelector('[data-delete-form]');
      const template = button.dataset.deleteCourse ? msg('courseDeleteTemplate', 'Удалить курс «{0}»?') : msg('assignmentDeleteTemplate', 'Удалить задание «{0}»?');
      if (message) message.textContent = template.replace('{0}', button.dataset.title || '');
      if (form) form.action = `${button.dataset.deleteCourse ? '/courses' : '/assignments'}/${button.dataset.id}/delete`;
      if (form) form.dataset.rowSelector = button.dataset.deleteCourse ? '.course-card' : '.assignment-row';
    });
  });
  document.querySelectorAll('[data-delete-form]').forEach((form) => form.addEventListener('submit', (event) => {
    if (form.dataset.confirmed === 'true') return;
    event.preventDefault();
    const id = form.action.split('/').at(-2);
    const row = document.querySelector(`${form.dataset.rowSelector}[data-id="${CSS.escape(id)}"]`);
    animateDelete(form, row);
  }));

  const userList = document.querySelector('[data-user-list]');
  if (userList) {
    const errorBox = document.querySelector('[data-user-error]');
    const empty = document.querySelector('[data-user-empty]');
    const form = document.querySelector('[data-user-form]');
    const formError = document.querySelector('[data-user-form-error]');
    const modalTitle = document.querySelector('[data-user-modal-title]');
    const passwordField = document.querySelector('[data-password-field]');
    const modal = document.getElementById('userModal');
    const deleteModal = document.getElementById('deleteUserModal');
    const selfId = body.dataset.currentUserId;
    let users = [];
    let deleteTarget = null;
    const userModal = window.bootstrap?.Modal.getOrCreateInstance(modal);
    const confirmDeleteModal = window.bootstrap?.Modal.getOrCreateInstance(deleteModal);

    const addCell = (row, label, child, className = '') => {
      const cell = document.createElement('td');
      cell.dataset.label = label;
      if (className) cell.className = className;
      if (child instanceof Node) cell.append(child); else cell.textContent = child;
      row.append(cell);
      return cell;
    };
    const actionButton = (icon, label, action, disabled = false) => {
      const button = document.createElement('button');
      button.type = 'button'; button.className = `icon-button ${action === 'delete' ? 'danger-icon' : ''}`;
      button.setAttribute('aria-label', label); button.title = label; button.disabled = disabled;
      const glyph = document.createElement('i'); glyph.className = `bi ${icon}`; glyph.setAttribute('aria-hidden', 'true'); button.append(glyph);
      button.dataset[action === 'edit' ? 'editUser' : 'deleteUser'] = '';
      return button;
    };
    const renderUsers = (filter = '') => {
      const query = filter.toLocaleLowerCase('ru');
      const role = document.querySelector('[data-user-role-filter]')?.value || '';
      const filtered = users.filter((user) => `${user.fullName} ${user.email}`.toLocaleLowerCase('ru').includes(query) && (!role || user.role === role));
      userList.replaceChildren();
      filtered.forEach((user) => {
        const row = document.createElement('tr'); row.dataset.id = user.id;
        const identity = document.createElement('span'); identity.className = 'user-identity';
        const avatar = document.createElement('span'); avatar.className = 'user-avatar'; avatar.textContent = (user.fullName || '?').trim().slice(0, 1).toLocaleUpperCase('ru');
        const name = document.createElement('span'); name.className = 'user-name'; name.textContent = user.fullName;
        identity.append(avatar, name);
        addCell(row, body.dataset.userName, identity);
        addCell(row, body.dataset.userEmail, user.email, 'user-email');
        const roleBadge = document.createElement('span'); roleBadge.className = `status-badge ${user.role === 'ADMIN' ? 'admin' : ''}`; roleBadge.textContent = user.role === 'ADMIN' ? msg('roleAdmin', 'Администратор') : msg('roleUser', 'Пользователь');
        addCell(row, body.dataset.userRole, roleBadge);
        const actions = document.createElement('div'); actions.className = 'row-actions';
        actions.append(actionButton('bi-pencil', msg('actionEdit', 'Изменить'), 'edit'));
        const isSelf = String(user.id) === String(selfId);
        const remove = actionButton('bi-trash3', isSelf ? msg('usersSelfDelete', 'Нельзя удалить свою учётную запись.') : msg('actionDelete', 'Удалить'), 'delete', isSelf);
        actions.append(remove);
        addCell(row, msg('usersActions', 'Действия'), actions, 'row-actions');
        userList.append(row);
      });
      empty?.classList.toggle('d-none', filtered.length > 0);
    };
    const loadUsers = async () => {
      try {
        const response = await fetch('/api/users', { headers: { Accept: 'application/json', [csrfHeader]: csrfToken } });
        if (!response.ok) throw new Error(body.dataset.usersError);
        users = await response.json();
        renderUsers(document.querySelector('[data-user-search]')?.value || '');
      } catch (error) {
        errorBox.textContent = error.message || body.dataset.usersError;
        errorBox.classList.remove('d-none');
        userList.replaceChildren();
      }
    };
    loadUsers();
    document.querySelector('[data-user-search]')?.addEventListener('input', debounce((event) => renderUsers(event.target.value)));
    document.querySelector('[data-user-role-filter]')?.addEventListener('change', () => renderUsers(document.querySelector('[data-user-search]')?.value || ''));

    document.querySelectorAll('[data-add-user]').forEach((button) => button.addEventListener('click', () => {
      form.reset(); form.elements.id.value = ''; passwordField.classList.remove('d-none'); form.elements.password.required = true;
      modalTitle.textContent = msg('usersNew', 'Новый пользователь'); formError.classList.add('d-none');
      form.querySelector('[data-strength-meter]')?.setAttribute('data-strength', '0');
      delete form.dataset.editingId;
    }));
    userList.addEventListener('click', (event) => {
      const edit = event.target.closest('[data-edit-user]');
      const remove = event.target.closest('[data-delete-user]');
      const row = event.target.closest('tr');
      const user = users.find((item) => String(item.id) === row?.dataset.id);
      if (!user) return;
      if (edit) {
        form.reset(); form.elements.id.value = user.id; form.elements.fullName.value = user.fullName; form.elements.email.value = user.email; form.elements.role.value = user.role;
        passwordField.classList.add('d-none'); form.elements.password.required = false; modalTitle.textContent = msg('usersEdit', 'Редактировать пользователя');
        formError.classList.add('d-none'); form.dataset.editingId = user.id; userModal?.show();
      }
      if (remove && String(user.id) !== String(selfId)) {
        deleteTarget = user; document.querySelector('[data-user-delete-message]').textContent = msg('usersDeleteConfirm', 'Удалить пользователя «{0}»?').replace('{0}', user.fullName); confirmDeleteModal?.show();
      }
    });
    form?.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (!form.reportValidity()) return;
      const editingId = form.dataset.editingId;
      const payload = { fullName: form.elements.fullName.value.trim(), email: form.elements.email.value.trim(), role: form.elements.role.value };
      if (!editingId) payload.password = form.elements.password.value;
      const submit = form.querySelector('[type="submit"]'); submit.disabled = true; submit.querySelector('.spinner-border')?.classList.remove('d-none');
      try {
        const response = await fetch(editingId ? `/api/users/${editingId}` : '/api/users', {
          method: editingId ? 'PUT' : 'POST',
          headers: { 'Content-Type': 'application/json', Accept: 'application/json', [csrfHeader]: csrfToken },
          body: JSON.stringify(payload),
        });
        const result = response.status === 204 ? null : await response.json().catch(() => null);
        if (!response.ok) throw new Error(result?.error || body.dataset.usersRequestError);
        const saved = result;
        users = editingId ? users.map((user) => user.id === saved.id ? saved : user) : [...users, saved];
        renderUsers(document.querySelector('[data-user-search]')?.value || '');
        userList.querySelector(`tr[data-id="${CSS.escape(String(saved.id))}"]`)?.classList.add('row-highlight');
        userModal?.hide(); toast(msg('usersSaveSuccess', 'Данные пользователя сохранены.'));
      } catch (error) {
        formError.textContent = error.message || body.dataset.usersRequestError; formError.classList.remove('d-none');
      } finally {
        submit.disabled = false; submit.querySelector('.spinner-border')?.classList.add('d-none');
      }
    });
    document.querySelector('[data-confirm-user-delete]')?.addEventListener('click', async (event) => {
      if (!deleteTarget) return;
      const button = event.currentTarget; button.disabled = true;
      try {
        const response = await fetch(`/api/users/${deleteTarget.id}`, { method: 'DELETE', headers: { [csrfHeader]: csrfToken } });
        if (!response.ok) {
          const result = await response.json().catch(() => null);
          throw new Error(result?.error || body.dataset.usersRequestError);
        }
        users = users.filter((user) => user.id !== deleteTarget.id); renderUsers(document.querySelector('[data-user-search]')?.value || '');
        confirmDeleteModal?.hide(); toast(msg('usersDeleteSuccess', 'Пользователь удалён.')); deleteTarget = null;
      } catch (error) { toast(error.message || body.dataset.usersRequestError, 'error'); }
      finally { button.disabled = false; }
    });
  }
})();

// The chart reads the rendered calendar, so it uses the same deadlines and links.
(() => {
  'use strict';
  const main = document.getElementById('main-content');
  if (!main) return;
  const copy = {
    ru: ['Нагрузка на неделю', 'Задания по дням сдачи', 'заданий', 'На этой неделе дедлайнов нет.'],
    en: ['Weekly workload', 'Assignments by due date', 'assignments', 'No deadlines this week.'],
    kk: ['Апталық жүктеме', 'Тапсыру күні бойынша тапсырмалар', 'тапсырма', 'Осы аптада мерзімдер жоқ.']
  };
  function render() {
    const core = main.querySelector('.dashboard-core');
    if (!core || core.querySelector('.study-load')) return;
    const days = [...main.querySelectorAll('.week-day')];
    if (!days.length) return;
    const labels = copy[document.documentElement.lang] || copy.ru;
    const counts = days.map(day => day.querySelectorAll('.week-task').length);
    const maximum = Math.max(1, ...counts);
    const section = document.createElement('section');
    section.className = 'surface study-load';
    const heading = document.createElement('div');
    heading.className = 'study-load-heading';
    const titleGroup = document.createElement('div');
    const title = document.createElement('h3');
    title.textContent = labels[0];
    const subtitle = document.createElement('p');
    subtitle.textContent = labels[1];
    titleGroup.append(title, subtitle);
    const total = document.createElement('span');
    total.className = 'study-load-total';
    total.textContent = String(counts.reduce((sum, count) => sum + count, 0));
    total.setAttribute('aria-label', `${total.textContent} ${labels[2]}`);
    heading.append(titleGroup, total);
    const chart = document.createElement('div');
    chart.className = 'study-load-chart';
    days.forEach((day, index) => {
      const link = document.createElement('a');
      link.className = 'study-load-day';
      const target = day.querySelector('.week-task') || main.querySelector('.week-heading>a');
      link.href = target?.getAttribute('href') || '#main-content';
      if (target?.dataset.previewRoute) link.dataset.previewRoute = target.dataset.previewRoute;
      const label = [...(day.querySelector('.week-day-heading')?.children || [])].map(node => node.textContent.trim()).join(' ') || String(index + 1);
      link.setAttribute('aria-label', `${label}: ${counts[index]} ${labels[2]}`);
      link.title = `${label}: ${counts[index]}`;
      const count = document.createElement('strong');
      count.textContent = String(counts[index]);
      const bar = document.createElement('span');
      bar.className = 'study-load-bar';
      bar.style.height = `${counts[index] / maximum * 110}px`;
      bar.setAttribute('aria-hidden', 'true');
      const date = document.createElement('time');
      date.textContent = label;
      link.append(count, bar, date);
      chart.append(link);
    });
    section.append(heading, chart);
    if (counts.every(count => count === 0)) {
      const empty = document.createElement('p');
      empty.className = 'study-load-empty';
      empty.textContent = labels[3];
      section.append(empty);
    }
    core.prepend(section);
  }
  render();
  new MutationObserver(render).observe(main, {childList:true, subtree:true});
})();

// Input modality and truthful feedback for native POST navigation.
(() => {
  const root = document.documentElement;
  document.addEventListener('pointerdown', () => { root.dataset.inputMethod = 'pointer'; }, true);
  document.addEventListener('keydown', () => { root.dataset.inputMethod = 'keyboard'; }, true);
  const pending = new Map();
  document.addEventListener('submit', event => {
    if (pending.has(event.target)) event.preventDefault();
  }, true);
  document.addEventListener('submit', event => {
    const form = event.target;
    // AJAX forms own their pending/success/error states. Never invent success here.
    queueMicrotask(() => {
      if (event.defaultPrevented || form.method.toLowerCase() !== 'post' || !form.isConnected) return;
      const status = document.createElement('span');
      status.className = 'submit-feedback';
      status.setAttribute('role', 'status');
      status.textContent = ({ru:'Отправляем…',en:'Sending…',kk:'Жіберілуде…'})[root.lang] || 'Отправляем…';
      form.setAttribute('aria-busy', 'true');
      const button = event.submitter;
      // Keep submitter enabled: its name/value may be part of the native request.
      if (button) button.insertAdjacentElement('afterend', status); else form.append(status);
      pending.set(form, status);
    });
  });
  window.addEventListener('pageshow', () => {
    for (const [form, status] of pending) {
      form.removeAttribute('aria-busy'); status.remove();
      form.querySelectorAll('button[type="submit"]').forEach(button => {
        button.disabled = false;
        button.querySelector('.spinner-border')?.classList.add('d-none');
      });
    }
    pending.clear();
  });
})();
