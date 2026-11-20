(() => {
  'use strict';
  const panel = document.querySelector('[data-user-reports]');
  if (!panel) return;
  const status = panel.querySelector('[data-report-status]');
  const list = panel.querySelector('[data-report-list]');
  const words = {
    title: ['Жалобы на пользователей', 'User reports', 'Пайдаланушы шағымдары'],
    people: ['От SH-{0} на SH-{1}', 'From SH-{0} about SH-{1}', 'SH-{0} қолданушысынан SH-{1} туралы'],
    loading: ['Загрузка…', 'Loading…', 'Жүктелуде…'],
    empty: ['Жалоб пока нет.', 'No reports yet.', 'Әзірге шағым жоқ.'],
    failed: ['Не удалось загрузить жалобы.', 'Could not load reports.', 'Шағымдарды жүктеу мүмкін болмады.'],
    refresh: ['Обновить', 'Refresh', 'Жаңарту']
  };
  const locales = ['ru', 'en', 'kk'];
  const language = () => window.StudHubI18n?.language || 'ru';
  const t = key => words[key][Math.max(0, locales.indexOf(language()))];
  const button = panel.querySelector('[data-report-refresh]');
  let reports = [];
  let state = '';

  function render() {
    panel.querySelectorAll('[data-report-copy]').forEach(el => {
      el.textContent = t(el.dataset.reportCopy);
    });
    status.textContent = state ? t(state) : '';
    list.replaceChildren();
    const format = new Intl.DateTimeFormat(language(), {dateStyle: 'medium', timeStyle: 'short'});
    for (const report of reports) {
      const item = document.createElement('li');
      item.className = 'py-3 border-bottom';
      const title = document.createElement('strong');
      title.textContent = window.StudHubContactSafety.t(report.reason);
      const meta = document.createElement('p');
      meta.className = 'muted-copy small mb-0';
      meta.textContent = t('people').replace('{0}', String(report.reporterId)).replace('{1}', String(report.reportedId))
        + ' · ' + format.format(new Date(report.createdAt));
      item.append(title, meta);
      if (report.details) {
        const details = document.createElement('p');
        details.className = 'text-break mb-0 mt-2';
        details.textContent = report.details;
        item.append(details);
      }
      list.append(item);
    }
  }

  async function load() {
    if (button.disabled) return;
    button.disabled = true;
    state = 'loading';
    render();
    try {
      const response = await fetch('/api/friends/reports', {cache: 'no-store', signal: AbortSignal.timeout(12000)});
      if (!response.ok || response.redirected) throw new Error();
      reports = await response.json();
      state = reports.length ? '' : 'empty';
    } catch {
      state = 'failed';
    } finally {
      button.disabled = false;
      render();
    }
  }

  panel.addEventListener('toggle', () => {
    if (panel.open) load();
  });
  button.addEventListener('click', load);
  document.addEventListener('studhub:language', render);
  render();
})();
