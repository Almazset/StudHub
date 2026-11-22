(() => {
  const editor = document.querySelector('[data-news-editor]');
  if (!editor) return;

  const category = editor.elements.category;
  const link = editor.elements.link;
  const status = editor.elements.status;
  const sourceName = editor.elements.sourceName;
  const sourceDate = editor.elements.sourceDate;
  const statusField = editor.querySelector('[data-news-status-field]');
  const sourceFields = editor.querySelector('[data-news-source-fields]');

  function syncFields() {
    const useStatus = category.value === 'UPDATE';
    statusField.hidden = !useStatus;
    status.required = useStatus;
    status.disabled = !useStatus;

    const useSource = /^https?:/i.test(link.value.trim());
    sourceFields.hidden = !useSource;
    sourceFields.disabled = !useSource;
    sourceName.required = useSource;
    sourceDate.required = useSource;
  }

  category.addEventListener('change', syncFields);
  link.addEventListener('input', syncFields);
  window.addEventListener('pageshow', syncFields);
  syncFields();
})();
