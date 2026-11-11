(() => {
 'use strict';const root=document.querySelector('[data-friends-root]');if(!root)return;
 let entries=[],tab='accepted',sequence=0,lastRendered='',busy=0,refreshing=false;
 const list=root.querySelector('[data-friends-list]'),results=root.querySelector('[data-friend-results]'),error=root.querySelector('.friends-error');
 const search=root.querySelector('[data-friends-filter]'),online=root.querySelector('[data-friends-online]'),sync=root.querySelector('[data-friends-sync]'),preference=root.querySelector('[data-my-presence]');
 const labels={ONLINE:'В сети',AWAY:'Отошёл',BUSY:'Не беспокоить',OFFLINE:'Не в сети'};
 const node=(tag,cls,text)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;};
 async function api(path,method='GET',body){const headers={};if(method!=='GET'){const k=document.querySelector('meta[name="_csrf_header"]')?.content,v=document.querySelector('meta[name="_csrf"]')?.content;if(k&&v)headers[k]=v;}if(body)headers['Content-Type']='application/json';const r=await fetch(path,{method,headers,credentials:'same-origin',cache:'no-store',body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(12000)});if(!r.ok||r.redirected)throw Error(r.status===409?'Заявка или дружба уже существует.':'Не удалось выполнить действие. Проверьте соединение и настройки приватности.');return r.json();}
 function fail(e){error.hidden=false;error.textContent=e.message;}
 function kind(e){return e.status==='ACCEPTED'?'accepted':e.incoming?'incoming':'outgoing';}
 function action(label,fn,primary=false){const b=node('button',`btn ${primary?'btn-primary':'btn-light'}`,label);b.type='button';b.onclick=async()=>{b.disabled=true;busy++;try{error.hidden=true;await fn();}catch(e){fail(e);}finally{busy--;b.disabled=false;}};return b;}
 function row(person,actions){
  const el=node('div','friends-row'),avatar=node('span','friends-avatar',(person.name||'У').slice(0,1).toUpperCase()),info=node('div','friends-info');el.dataset.friendId=person.id;
  info.append(node('strong','',person.name||'Участник'),node('small','',`SH-${person.userId??person.id}`));
  if(person.status==='ACCEPTED'){const state=labels[person.presence]?person.presence:'OFFLINE';info.append(node('span',`friend-presence friend-presence-${state.toLowerCase()}`,labels[state]));}
  const bar=node('div','friends-actions');actions.forEach(a=>bar.append(a));el.append(avatar,info,bar);return el;
 }
 function render(){
  const focused=list.contains(document.activeElement)?{id:document.activeElement.closest('[data-friend-id]')?.dataset.friendId,text:document.activeElement.textContent}:null;
  for(const name of ['accepted','incoming','outgoing'])root.querySelector(`[data-count="${name}"]`).textContent=entries.filter(e=>kind(e)===name).length;
  root.querySelectorAll('[data-friend-tab]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.friendTab===tab)));
  online.disabled=tab!=='accepted';list.replaceChildren();lastRendered=JSON.stringify(entries);
  const query=search.value.trim().toLocaleLowerCase();
  const rows=entries.filter(e=>kind(e)===tab&&(!query||`${e.name} SH-${e.userId}`.toLocaleLowerCase().includes(query))&&(tab!=='accepted'||!online.checked||['ONLINE','AWAY','BUSY'].includes(e.presence)));
  rows.sort((a,b)=>Number(b.presence!=='OFFLINE')-Number(a.presence!=='OFFLINE')||a.name.localeCompare(b.name));
  if(!rows.length){list.append(node('p','muted-copy',query||(tab==='accepted'&&online.checked)?'По выбранным условиям никого не найдено.':tab==='accepted'?'Список друзей пока пуст.':tab==='incoming'?'Новых заявок нет.':'Нет отправленных заявок.'));return;}
  for(const person of rows){const id=person.id,actions=[];
   if(tab==='incoming')actions.push(action('Принять',async()=>{await api(`/api/friends/${id}/accept`,'POST');await refresh();},true));
   if(tab==='accepted')actions.push(action('Написать',async()=>{const room=await api(`/api/friends/${id}/chat`,'POST');location.href=`/chat?room=${encodeURIComponent(room.id)}`;},true));
   actions.push(action(tab==='incoming'?'Отклонить':tab==='outgoing'?'Отменить':'Удалить',async()=>{if(tab==='accepted'&&!confirm('Удалить пользователя из друзей?'))return;await api(`/api/friends/${id}`,'DELETE');await refresh();}));
   list.append(row(person,actions));
  }
  if(focused){const r=[...list.children].find(r=>r.dataset.friendId===focused.id);[...r?.querySelectorAll('button')||[]].find(b=>b.textContent===focused.text)?.focus({preventScroll:true});}
 }
 async function refresh(){const seq=++sequence;const data=await api('/api/friends');if(seq!==sequence)return;entries=data;if(JSON.stringify(entries)!==lastRendered)render();sync.textContent='Список обновлён';}
 async function poll(){if(document.hidden||busy||refreshing)return;refreshing=true;try{await refresh();}catch{sync.textContent='Нет связи. Повторим обновление автоматически.';}finally{refreshing=false;}}
 root.querySelectorAll('[data-friend-tab]').forEach(b=>b.onclick=()=>{tab=b.dataset.friendTab;render();});search.addEventListener('input',render);online.addEventListener('change',render);
 root.querySelector('[data-friends-refresh]').addEventListener('click',async e=>{const b=e.currentTarget;b.disabled=true;try{await refresh();}catch(err){fail(err);}finally{b.disabled=false;}});
 root.querySelector('[data-friend-search]').addEventListener('submit',async e=>{
  e.preventDefault();const q=e.target.elements.query.value.trim();if(q.length<2)return;const b=e.target.querySelector('button');b.disabled=true;
  try{error.hidden=true;const people=await api(`/api/chat/users?q=${encodeURIComponent(q)}`);results.replaceChildren();
   if(!people.length){results.append(node('p','muted-copy','Пользователь не найден. В поиске видны только открытые профили.'));return;}
   for(const person of people){const existing=entries.find(x=>x.userId===person.id);const btn=existing?node('span','muted-copy',kind(existing)==='accepted'?'Уже в друзьях':'Заявка существует'):action('Добавить в друзья',async()=>{await api('/api/friends','POST',{userId:person.id});await refresh();results.replaceChildren(node('p','muted-copy','Заявка отправлена.'));},true);results.append(row(person,[btn]));}
  }catch(err){fail(err);}finally{b.disabled=false;}
 });
 let saved='ONLINE';
 api('/api/friends/presence').then(v=>{saved=v.status;preference.value=saved;preference.disabled=false;}).catch(fail);
 preference.addEventListener('change',async()=>{preference.disabled=true;const feedback=root.querySelector('[data-presence-feedback]');feedback.textContent='Сохраняем статус…';try{const v=await api('/api/friends/presence','PUT',{status:preference.value});saved=v.status;feedback.textContent='Статус сохранён';}catch(e){preference.value=saved;feedback.textContent='Не удалось сохранить статус';fail(e);}finally{preference.disabled=false;}});
 refresh().catch(fail);setInterval(poll,15000);document.addEventListener('visibilitychange',poll);window.addEventListener('online',poll);
})();
