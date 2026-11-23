(() => {
 'use strict';
 const root=document.querySelector('[data-project-tasks]'); if(!root)return;
 const form=root.querySelector('[data-task-form]'), list=root.querySelector('[data-task-list]'), error=root.querySelector('[data-task-error]');
 const projectBase=`/api/projects/${encodeURIComponent(root.dataset.projectTasks)}`,base=`${projectBase}/tasks`;
 const owner=root.dataset.owner==='true',viewer=Number(root.dataset.viewer),closed=root.dataset.closed==='true';
 const statuses={TODO:'Запланировано',IN_PROGRESS:'В работе',REVIEW:'На проверке',DONE:'Готово'};
 const progress = root.querySelector('[data-project-progress]');
 const progressBar = root.querySelector('[data-project-progress-bar]');
 const retry = root.querySelector('[data-task-retry]');
 const taskFormSubmit = form.querySelector('[type=submit]');
 let rows = null, editing = null;
 async function request(url,method='GET',body){
  const headers={},key=document.querySelector('meta[name="_csrf_header"]')?.content,token=document.querySelector('meta[name="_csrf"]')?.content;
  if(key&&token)headers[key]=token;if(body)headers['Content-Type']='application/json';
  let response;
  try {
   response = await fetch(url, {
    method, headers, credentials: 'same-origin',
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000)
   });
  } catch {
   throw new Error('Не удалось связаться с сервером. Проверьте подключение и повторите попытку.');
  }
  if(!response.ok||response.redirected)throw Error(response.status===409?'Действие недоступно: проект закрыт либо достигнут лимит. Обновите страницу и проверьте состояние проекта.':'Не удалось выполнить действие. Проверьте права доступа и соединение.');
  return response.json();
 }
 function showError(e){error.hidden=false;error.textContent=e.message;}
 function node(tag,text,cls){const n=document.createElement(tag);if(text)n.textContent=text;if(cls)n.className=cls;return n;}
 function button(text,action){const b=node('button',text,'btn btn-light');b.type='button';b.addEventListener('click',()=>action(b));return b;}
 const overdue=t=>{const d=new Date(),today=[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');return t.status!=='DONE'&&t.dueDate&&t.dueDate<today;};
 function cancelEdit() {
  editing = null;
  form.reset();
  taskFormSubmit.textContent = 'Добавить задачу';
  form.querySelector('[data-task-cancel]').hidden = true;
 }
 function restoreFocus(next) {
  if (document.activeElement === document.body) next?.focus();
 }
 function editTask(task){editing=task.id;form.elements.title.value=task.title;form.elements.assigneeId.value=task.assigneeId??'';form.elements.dueDate.value=task.dueDate??'';form.elements.priority.value=task.priority||'MEDIUM';form.querySelector('[type=submit]').textContent='Сохранить задачу';form.querySelector('[data-task-cancel]').hidden=false;form.elements.title.focus();}
 function render(){
  if (!rows) return;
  const done=rows.filter(t=>t.status==='DONE').length,percent=rows.length?Math.round(done/rows.length*100):0;
  progress.textContent = rows.length
    ? `Готово ${done} из ${rows.length} · ${percent}% · Просрочено: ${rows.filter(overdue).length}`
    : 'Задач пока нет.';
  progressBar.value = percent;
  progressBar.hidden = false;
  const query=root.querySelector('[data-task-search]').value.trim().toLocaleLowerCase(),status=root.querySelector('[data-task-filter]').value;
  const assignee=root.querySelector('[data-task-assignee]').value,from=root.querySelector('[data-task-from]').value,to=root.querySelector('[data-task-to]').value;
  const filtered=rows.filter(t=>(!assignee||(assignee==='unassigned'?t.assigneeId==null:String(t.assigneeId)===assignee))&&(!from||(t.dueDate&&t.dueDate>=from))&&(!to||(t.dueDate&&t.dueDate<=to))&&(!root.querySelector('[data-task-undated]').checked||!t.dueDate)&&(!query||t.title.toLocaleLowerCase().includes(query))&&(!status||t.status===status)&&(!root.querySelector('[data-task-mine]').checked||t.assigneeId===viewer)&&(!root.querySelector('[data-task-overdue]').checked||overdue(t)));
  list.replaceChildren();
  if(!filtered.length){list.append(node('p',rows.length?'По выбранным условиям задач нет.':closed ? 'В проекте не было задач.' : 'Добавьте задачу, назначьте исполнителя и срок.','muted-copy'));return;}
  filtered.forEach(task=>{
   const row=node('article',null,'project-task-row'),info=node('div'),title=node('strong',task.title),assignee=[...form.elements.assigneeId.options].find(o=>o.value===String(task.assigneeId));
   row.id=`task-${task.id}`;
   const meta=node('small',[({LOW:'Низкий',MEDIUM:'Средний',HIGH:'Высокий'}[task.priority||'MEDIUM'])+' приоритет',task.assigneeId?(assignee?.textContent||'Участник'):'Не назначен',task.dueDate?`Срок: ${task.dueDate.split('-').reverse().join('.')}`:'Без срока',overdue(task)?'Просрочено':''].filter(Boolean).join(' · '));
   if(overdue(task))meta.className='project-error';info.append(title,meta);
   const controls=node('div',null,'project-task-controls'),select=node('select');select.setAttribute('aria-label',`Статус задачи ${task.title}`);
   for(const [value,label] of Object.entries(statuses)){const option=node('option',label);option.value=value;option.selected=task.status===value;select.append(option);}
   select.addEventListener('change', async () => {
    const hadFocus = document.activeElement === select;
    select.disabled = true;
    error.hidden = true;
    try {
     await request(`${base}/${task.id}`, 'PATCH', {status: select.value});
     await load();
     if (hadFocus) restoreFocus(document.getElementById(row.id)?.querySelector('select') || root.querySelector('[data-task-search]'));
    } catch (e) {
     showError(e);
     select.value = task.status;
    } finally {
     select.disabled = false;
     if (hadFocus && select.isConnected) restoreFocus(select);
    }
   });
   if (closed) {
    controls.append(node('span', statuses[task.status], 'status-badge'));
   } else {
    const editButton = button('Изменить', () => editTask(task));
    editButton.dataset.editProjectTask = '';
    controls.append(select, editButton);
   }
   if (owner && !closed) {
    controls.append(button('Удалить', async b => {
     if (!confirm(`Удалить задачу «${task.title}»?`)) return;
     const hadFocus = document.activeElement === b;
     const neighbor = row.nextElementSibling || row.previousElementSibling;
     b.disabled = true;
     error.hidden = true;
     try {
      await request(`${base}/${task.id}`, 'DELETE');
      if (editing === task.id) cancelEdit();
      await load();
      if (hadFocus) restoreFocus(document.getElementById(neighbor?.id)?.querySelector('select') || root.querySelector('[data-task-search]'));
     } catch (e) {
      showError(e);
     } finally {
      b.disabled = false;
      if (hadFocus && b.isConnected) restoreFocus(b);
     }
    }));
   }
   row.append(info,controls);list.append(row);
  });
 }

 async function load() {
  list.setAttribute('aria-busy', 'true');
  if (!rows) progress.textContent = 'Загружаем задачи…';
  retry.hidden = true;
  try {
   rows = await request(base);
   render();
  } catch {
   if (!rows) progress.textContent = 'Прогресс пока недоступен.';
   showError(new Error('Не удалось загрузить задачи. Проверьте подключение и повторите попытку.'));
   retry.hidden = false;
   return;
  } finally {
   list.setAttribute('aria-busy', 'false');
  }
  historyBefore = null;
  void loadHistory();
 }

 retry.addEventListener('click', async () => {
  error.hidden = true;
  await load();
  restoreFocus(retry.hidden ? root.querySelector('[data-task-search]') : retry);
 });
 root.querySelectorAll('[data-task-search],[data-task-filter],[data-task-mine],[data-task-overdue],[data-task-assignee],[data-task-from],[data-task-to],[data-task-undated]').forEach(el=>el.addEventListener('input',render));
 root.querySelector('[data-task-reset]').addEventListener('click',()=>{root.querySelectorAll('.project-task-filters input,.project-task-filters select').forEach(e=>{if(e.type==='checkbox')e.checked=false;else e.value='';});render();});
 form.querySelector('[data-task-cancel]').addEventListener('click', () => {
  const editButton = document.getElementById('task-' + editing)?.querySelector('[data-edit-project-task]');
  cancelEdit();
  (editButton || form.elements.title).focus();
 });
 form.addEventListener('submit',async e=>{
  e.preventDefault();
  const title = form.elements.title.value.trim();
  if (!title) {
   showError(new Error('Укажите название задачи.'));
   form.elements.title.focus();
   return;
  }
  const hadFocus = document.activeElement === taskFormSubmit;
  taskFormSubmit.disabled = true;
  form.setAttribute('aria-busy', 'true');
  error.hidden = true;
  try {
   await request(editing ? `${base}/${editing}` : base, editing ? 'PUT' : 'POST', {
    title,
    assigneeId: form.elements.assigneeId.value ? Number(form.elements.assigneeId.value) : null,
    dueDate: form.elements.dueDate.value || null,
    priority: form.elements.priority.value
   });
   cancelEdit();
   await load();
  } catch (e) {
   showError(e);
  } finally {
   form.removeAttribute('aria-busy');
   taskFormSubmit.disabled = false;
   if (hadFocus) restoreFocus(taskFormSubmit);
  }
 });
 const teamError=document.querySelector('[data-team-error]');
 async function teamAction(btn,action){btn.disabled=true;teamError.hidden=true;try{await action();}catch(e){teamError.textContent=e.message;teamError.hidden=false;}finally{btn.disabled=false;}}
 document.querySelector('[data-project-edit]')?.addEventListener('submit',e=>{e.preventDefault();const f=e.currentTarget;teamAction(f.querySelector('button'),async()=>{await request(projectBase,'PUT',{title:f.elements.title.value,description:f.elements.description.value});location.reload();});});
 document.querySelectorAll('[data-remove-member]').forEach(b=>b.addEventListener('click',()=>{if(!confirm('Удалить участника? Доступ к чату закроется, его задачи останутся без исполнителя.'))return;teamAction(b,async()=>{await request(`${projectBase}/members/${b.dataset.removeMember}`,'DELETE');location.reload();});}));
 document.querySelector('[data-team-search]')?.addEventListener('submit',e=>{
  e.preventDefault();const f=e.currentTarget;teamAction(f.querySelector('button'),async()=>{
   const people=await request(`/api/chat/users?q=${encodeURIComponent(f.elements.query.value.trim())}`),host=document.querySelector('[data-team-results]');host.replaceChildren();
   const existing=new Set([...form.elements.assigneeId.options].map(o=>o.value));const available=people.filter(p=>!existing.has(String(p.id)));
   if(!available.length)host.append(node('p','Новых участников не найдено. Поиск доступен только по открытым профилям.','muted-copy'));
   available.forEach(p=>host.append(button(`${p.name} · SH-${p.id} · Пригласить`,b=>teamAction(b,async()=>{await request(`${projectBase}/members`,'POST',{userId:p.id});location.reload();}))));
  });
 });
 const chatButton=document.querySelector('[data-project-chat]');
 chatButton?.addEventListener('click',async()=>{const box=document.querySelector('[data-project-chat-error]');chatButton.disabled=true;chatButton.textContent='Открываем чат…';box.hidden=true;try{const room=await request(`${projectBase}/chat`,'POST',{});location.assign(`/chat?room=${encodeURIComponent(room.id)}`);}catch(e){box.textContent=e.message;box.hidden=false;chatButton.disabled=false;chatButton.textContent='Открыть чат команды';}});
 document.querySelectorAll('[data-cancel-invitation]').forEach(b=>b.addEventListener('click',()=>teamAction(b,async()=>{await request(`${projectBase}/invitations/${b.dataset.cancelInvitation}`,'DELETE');location.reload();})));
 document.querySelector('[data-project-state]')?.addEventListener('click',async e=>{if(!confirm(closed?'Открыть проект повторно?':'Закрыть проект? Данные сохранятся, изменение проекта и задач будет приостановлено.'))return;const b=e.currentTarget,box=document.querySelector('[data-state-error]');b.disabled=true;box.hidden=true;try{await request(`${projectBase}/state`,'PATCH',{closed:!closed});location.reload();}catch(err){box.textContent=err.message;box.hidden=false;b.disabled=false;}});
 let historyBefore = null;
 let historyRequest = 0;
 const historyHost = document.querySelector('[data-project-history]');
 const historyError = document.querySelector('[data-history-error]');
 const historyLoading = document.querySelector('[data-history-loading]');
 const historyRetry = document.querySelector('[data-history-retry]');
 const more = document.querySelector('[data-history-more]');
 async function loadHistory() {
  const requestNumber = ++historyRequest;
  const before = historyBefore;
  more.disabled = true;
  historyRetry.hidden = true;
  historyError.hidden = true;
  historyLoading.hidden = false;
  historyHost.setAttribute('aria-busy', 'true');
  try {
   const entries = await request(`${projectBase}/history${before ? '?before=' + before : ''}`);
   if (requestNumber !== historyRequest) return;
   if (!before) historyHost.replaceChildren();
   if (!entries.length && !before) historyHost.append(node('li', 'История пока пуста.'));
   for (const entry of entries) {
    historyHost.append(node('li', `${new Date(entry.occurredAt).toLocaleString()} · SH-${entry.actorId} · ${entry.description}`));
   }
   if (entries.length) historyBefore = entries.at(-1).id;
   more.hidden = entries.length < 50;
  } catch {
   if (requestNumber !== historyRequest) return;
   historyError.textContent = 'Не удалось загрузить историю проекта. Попробуйте ещё раз.';
   historyError.hidden = false;
   historyRetry.hidden = false;
  } finally {
   if (requestNumber === historyRequest) {
    historyLoading.hidden = true;
    historyHost.setAttribute('aria-busy', 'false');
    more.disabled = false;
   }
  }
 }
 async function loadMore(event) {
  const hadFocus = document.activeElement === event.currentTarget;
  await loadHistory();
  if (!hadFocus) return;
  const next = !historyRetry.hidden ? historyRetry : !more.hidden ? more : historyHost;
  if (next === historyHost) historyHost.tabIndex = -1;
  restoreFocus(next);
 }
 more.addEventListener('click', loadMore);
 historyRetry.addEventListener('click', loadMore);
 load().then(() => {
  if (!rows) {
   void loadHistory();
   return;
  }
  if (!/^#task-\d+$/.test(location.hash)) return;
  const target = document.getElementById(location.hash.slice(1));
  if (!target) return;
  target.tabIndex = -1;
  target.focus({preventScroll: true});
  target.scrollIntoView({block: 'center'});
 });
})();
