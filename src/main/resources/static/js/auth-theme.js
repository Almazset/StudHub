(() => {
  let theme = 'light';
  try { theme = localStorage.getItem('studhub-theme') || 'light'; } catch {}
  document.documentElement.dataset.theme = theme === 'dark' ? 'dark' : 'light';
  document.addEventListener('DOMContentLoaded', () => {
    const button = document.querySelector('.auth-theme-toggle');
    const sync = () => {
      const dark = document.documentElement.dataset.theme === 'dark';
      button.setAttribute('aria-pressed', String(dark));
      button.setAttribute('aria-label', dark ? 'Включить светлую тему' : 'Включить тёмную тему');
      button.querySelector('span').textContent = dark ? 'Светлая тема' : 'Тёмная тема';
    };
    sync();
    new MutationObserver(sync).observe(document.documentElement, {attributes:true, attributeFilter:['data-theme']});
  });
})();
