(() => {
  'use strict';
  const t = key => window.StudHubChatCopy(key);
  const preview = location.pathname.endsWith('studhub-preview.html');
  const node = (tag, cls, text) => { const el=document.createElement(tag); if(cls)el.className=cls; if(text!==undefined)el.textContent=text; return el; };
  const icon = name => { const el=node('i',`bi bi-${name}`); el.setAttribute('aria-hidden','true'); return el; };
  const button = (text, action, cls='chat-button') => { const el=node('button',cls,text); el.type='button'; if(action)el.addEventListener('click',action); return el; };
  const personName = person => person?.name || `${t('member')}${person?.id ?? ''}`;
  const roomName = room => room.kind==='GLOBAL' ? t('global') : room.name || `${t('member')}${room.peerId ?? ''}`;
  const time = value => new Intl.DateTimeFormat(window.StudHubI18n?.language || 'ru',{hour:'2-digit',minute:'2-digit'}).format(new Date(value));
  let root, life, timer, current=null, meId, rooms=[], detail, rows=[], older=false, opening=0, syncing=false, sending=false, filter='all', signature='';
  const drafts=new Map(), reads=new Map();
  let dialogSequence=0, dialogMode, selected=new Map(), searchTimer, dialogOpener, failedSend=null;
  const $ = selector => root?.querySelector(selector);
  async function api(path,method='GET',payload) {
    const requestLife=life;
    if(preview) return window.StudHubChatDemo(path,method,payload);
    const headers={};
    if(payload)headers['Content-Type']='application/json';
    const token=document.querySelector('meta[name="_csrf"]')?.content, header=document.querySelector('meta[name="_csrf_header"]')?.content;
    if(token && header)headers[header]=token;
    let response;
    try { response=await fetch(`/api/chat${path}`,{method,headers,credentials:'same-origin',cache:'no-store',body:payload?JSON.stringify(payload):undefined,signal:AbortSignal.any([requestLife.signal,AbortSignal.timeout(15000)])}); }
    catch(error) { if(requestLife.signal.aborted)throw new DOMException('Aborted','AbortError'); throw new Error('NETWORK'); }
    if(response.redirected || response.status===401 || response.headers.get('content-type')?.includes('text/html'))throw new Error('LOGIN');
    const value=await response.json().catch(()=>({}));
    if(requestLife!==life || requestLife.signal.aborted)throw new DOMException('Aborted','AbortError');
    if(!response.ok)throw new Error(value.error || (response.status===403?'LOGIN':'NETWORK'));
    return value;
  }
  function error(error, inDialog=false) {
    if(!root?.isConnected || error.name==='AbortError')return;
    const box=$(inDialog?'[data-chat-dialog-error]':'[data-chat-error]');
    if(box){box.hidden=false;box.querySelector('span').textContent=t(error.message)===error.message?t('NETWORK'):t(error.message);}
    if(error.message==='NOT_FOUND' && !inDialog){ rows=[]; renderMessages(); $('[data-chat-composer]').hidden=true; }
  }
  function status(text='') { const el=$('[data-chat-status]');if(el&&el.textContent!==text)el.textContent=text; }
  function clearError() { $('[data-chat-error]').hidden=true; }
  function nearBottom() { const box=$('[data-chat-messages]'); return box && box.scrollHeight-box.scrollTop-box.clientHeight<80; }
  function scrollEnd() { const box=$('[data-chat-messages]');box.scrollTop=box.scrollHeight;$('[data-chat-newest]').hidden=true; }
  function saveDraft() { if(!current)return; const text=$('[data-chat-input]')?.value || '';const draft=drafts.get(current);drafts.set(current,{text,clientId:draft?.text===text?draft.clientId:crypto.randomUUID()}); }
  function updateComposer() {
    const input=$('[data-chat-input]'); $('[data-chat-count]').textContent=`${input.value.length} / 4000`;
    input.readOnly=sending;
    $('[data-chat-send]').disabled=sending || !input.value.trim() || !current;
    $('[data-chat-send]').textContent=t(sending?'sending':'send');
  }
  function updateLabels() {
    document.querySelectorAll('[data-chat-nav]').forEach(el=>{el.querySelector('span').textContent=t('title');el.title=t('title');el.setAttribute('aria-label',t('title'));});
    if(!root)return;
    root.querySelectorAll('[data-chat-label]').forEach(el=>el.textContent=t(el.dataset.chatLabel));
    root.querySelectorAll('[data-chat-placeholder]').forEach(el=>el.placeholder=t(el.dataset.chatPlaceholder));
    root.querySelectorAll('[data-chat-title]').forEach(el=>{el.title=t(el.dataset.chatTitle);el.setAttribute('aria-label',t(el.dataset.chatTitle));});
    document.querySelectorAll('[data-preview-title],body[data-page="chat"] .topbar-title').forEach(el=>{if(document.body.dataset.page==='chat')el.textContent=t('title');});
    signature='';renderList();if(detail)renderHeader();renderMessages();updateComposer();
  }
  function renderList() {
    const query=$('[data-chat-search]').value.trim().toLocaleLowerCase();
    const visible=rooms.filter(r=>(filter==='all'||r.kind===filter||(filter==='GROUP'&&r.kind==='PROJECT')||r.kind==='GLOBAL') && roomName(r).toLocaleLowerCase().includes(query));
    const next=JSON.stringify([visible,current,filter,query,window.StudHubI18n?.language]);if(next===signature)return;signature=next;
    const list=$('[data-chat-list]');list.replaceChildren();
    visible.sort((a,b)=>Number(b.kind==='GLOBAL')-Number(a.kind==='GLOBAL')).forEach(room=>{
      const el=button('',()=>openRoom(room.id),'chat-room');el.classList.toggle('active',room.id===current);el.setAttribute('aria-pressed',String(room.id===current));
      const avatar=node('span','chat-avatar');avatar.append(icon(room.kind==='GLOBAL'?'globe2':['GROUP','PROJECT'].includes(room.kind)?'people':'person'));el.append(avatar);
      const text=node('span','chat-room-copy'),line=node('span','chat-room-line');line.append(node('strong','',roomName(room)));
      if(room.lastMessage)line.append(node('time','',time(room.lastMessage.createdAt)));text.append(line);
      const summary=node('span','chat-room-preview',room.lastMessage?.text || t(room.kind==='GLOBAL'?'globalHint':'start'));text.append(summary);el.append(text);
      if(room.unread){const badge=node('span','chat-unread',room.unread>99?'99+':room.unread);badge.setAttribute('aria-label',`${room.unread} ${t('unread')}`);el.append(badge);}
      list.append(el);
    });
    if(!visible.length)list.append(node('p','chat-empty-list',t(query?'noMatches':'emptyList')));
    $('[data-chat-total]').textContent=rooms.length;
  }
  function renderHeader() {
    const room=detail.room;
    $('[data-chat-room-title]').textContent=roomName(room);
    $('[data-chat-room-subtitle]').textContent=room.kind==='GLOBAL'?t('globalHint'):room.kind==='DIRECT'?t('personal'):`${t('members')}: ${room.memberCount}`;
    $('[data-chat-room-hint]').textContent=t(room.kind==='GLOBAL'?'globalHint':room.kind==='DIRECT'?'privateHint':'groupHint');
    $('[data-chat-members]').hidden=room.kind==='GLOBAL';
  }
  function appendText(element,text) {
    const parts=text.split(/(https?:\/\/[^\s<>]+)/g);
    parts.forEach(part=>{
      if(/^https?:\/\//.test(part)) { const link=node('a','',part);link.href=part;link.target='_blank';link.rel='noopener noreferrer';element.append(link); }
      else element.append(document.createTextNode(part));
    });
  }
  function renderMessages() {
    const list=$('[data-chat-messages]');if(!list)return;
    list.replaceChildren();
    if(!rows.length){const empty=node('div','chat-conversation-empty');empty.append(icon('chat-square-text'),node('h3','',t('start')),node('p','',t('startHint')));list.append(empty);}
    let lastDate='';
    rows.forEach(message=>{
      const date=new Date(message.createdAt).toLocaleDateString(window.StudHubI18n?.language || 'ru',{day:'numeric',month:'long',year:'numeric'});
      if(date!==lastDate){list.append(node('div','chat-date',date));lastDate=date;}
      const own=message.sender.id===meId, item=node('article',`chat-message${own?' own':''}`);
      const meta=node('div','chat-message-meta');meta.append(node('strong','',own?t('you'):personName(message.sender)));
      const stamp=node('time','',time(message.createdAt));stamp.dateTime=message.createdAt;stamp.title=new Date(message.createdAt).toLocaleString();meta.append(stamp);
      const body=node('p','chat-message-body');appendText(body,message.text);item.append(meta,body);
      if(message.editedAt)meta.append(node('small','','изменено'));
      if(own&&!preview){
        const menu=node('details','chat-message-actions'), toggle=node('summary','chat-message-toggle');
        toggle.setAttribute('aria-label','Действия с сообщением');toggle.title='Действия с сообщением';toggle.append(icon('chevron-down'));
        const actions=node('div','chat-message-menu');
        const edit=button('Изменить',async()=>{menu.open=false;const text=prompt('Изменить сообщение',message.text);if(text===null||text===message.text)return;edit.disabled=true;try{const updated=await api(`/rooms/${current}/messages/${message.id}`,'PUT',{text});const index=rows.findIndex(m=>m.id===updated.id);if(index!==-1){rows[index]=updated;renderMessages();}}catch(e){error(e);}finally{edit.disabled=false;}},'chat-action');
        const remove=button('Удалить',async()=>{menu.open=false;if(!confirm('Удалить сообщение для всех участников?'))return;const id=current;remove.disabled=true;try{await api(`/rooms/${id}/messages/${message.id}`,'DELETE');if(current===id){rows=rows.filter(m=>m.id!==message.id);renderMessages();await refreshList();}}catch(e){error(e);}finally{remove.disabled=false;}},'chat-action');
        actions.append(edit,remove);menu.append(toggle,actions);meta.append(menu);
      }
      list.append(item);
    });
    $('[data-chat-older]').hidden=!older;
  }
  async function markRead() {
    if(!current||document.hidden||!$('[data-chat-messages]')?.getClientRects().length||!nearBottom()||!rows.length)return;
    const id=current,last=rows.at(-1).id;if((reads.get(id)||0)>=last)return;
    reads.set(id,last);
    try { await api(`/rooms/${id}/read`,'POST',{messageId:last}); if(current===id){const room=rooms.find(r=>r.id===id);if(room)room.unread=0;renderList();} }
    catch(e) {reads.delete(id);if(current===id)error(e);}
  }
  async function refreshList() {
    const result=await api('/rooms');if(!root?.isConnected)return;
    meId=result.meId;rooms=result.rooms;renderList();
  }
  async function openRoom(id) {
    if(!id)return;
    $('[data-chat-search-results]').hidden=true;$('[data-chat-search-close]').hidden=true;$('#chat-message-query').value='';
    saveDraft();current=id;$('[data-chat-input]').value=drafts.get(id)?.text || '';detail=null;rows=[];older=false;const version=++opening;clearError();
    root.classList.add('chat-has-room');$('[data-chat-welcome]').hidden=true;$('[data-chat-conversation]').hidden=false;
    $('[data-chat-room-title]').textContent=t('loading');$('[data-chat-room-subtitle]').textContent='';$('[data-chat-room-hint]').textContent='';
    $('[data-chat-members]').hidden=true;$('[data-chat-composer]').hidden=true;$('[data-chat-messages]').replaceChildren(node('p','chat-loading',t('loading')));
    $('[data-chat-older]').hidden=true;$('[data-chat-newest]').hidden=true;renderList();
    try {
      const [room,history]=await Promise.all([api(`/rooms/${id}`),api(`/rooms/${id}/messages`)]);
      if(version!==opening||!root?.isConnected)return;
      detail=room;rows=history.messages;older=history.hasMore;renderHeader();renderMessages();scrollEnd();
      $('[data-chat-composer]').hidden=false;$('[data-chat-input]').value=drafts.get(id)?.text || '';updateComposer();await markRead();
    } catch(e) {if(version===opening){$('[data-chat-room-title]').textContent=t('title');$('[data-chat-messages]').replaceChildren();error(e);}}
  }
  async function sync() {
    if(syncing||sending||!root?.isConnected||document.hidden)return;
    syncing=true;const id=current,version=opening,activeLife=life;
    try {
      await refreshList();
      if(id&&id===current&&detail) {
        const [history,recent]=await Promise.all([api(`/rooms/${id}/messages?after=${rows.at(-1)?.id || 0}`),api(`/rooms/${id}/messages`)]);
        if(version!==opening||!root?.isConnected)return;
        const bottom=nearBottom(),box=$('[data-chat-messages]'),top=box.scrollTop;
        const revised=new Map(recent.messages.map(m=>[m.id,m]));let changed=false;
        const earliest=recent.messages[0]?.id;
        rows=rows.filter(m=>{const covered=!recent.hasMore || (earliest!==undefined && m.id>=earliest);if(covered&&!revised.has(m.id)){changed=true;return false;}return true;});
        rows=rows.map(m=>{const next=revised.get(m.id);if(next && (next.text!==m.text || next.editedAt!==m.editedAt)){changed=true;return next;}return m;});
        if(changed){renderMessages();box.scrollTop=top;}
        if(history.messages.length) {
          const known=new Set(rows.map(m=>m.id));rows.push(...history.messages.filter(m=>!known.has(m.id)));renderMessages();
          if(bottom)scrollEnd();else{box.scrollTop=top;$('[data-chat-newest]').hidden=false;}
        }
        const latest=rooms.find(r=>r.id===id);if(latest){detail.room=latest;renderHeader();}
        await markRead();
      }
      status(t('updated'));
    } catch(e) {if(version===opening){error(e);status('');}} finally {if(activeLife===life)syncing=false;}
  }
  async function loadOlder() {
    if(!current||!rows.length)return;
    const id=current,version=opening,btn=$('[data-chat-older]');btn.disabled=true;
    try {
      const history=await api(`/rooms/${id}/messages?before=${rows[0].id}`);if(version!==opening)return;
      const box=$('[data-chat-messages]'),height=box.scrollHeight,top=box.scrollTop;
      rows=[...history.messages,...rows];older=history.hasMore;renderMessages();box.scrollTop=top+box.scrollHeight-height;
    } catch(e){error(e);} finally{btn.disabled=false;}
  }
  async function send(event) {
    event.preventDefault();if(sending||!current||!$('[data-chat-input]').value.trim())return;
    saveDraft();const id=current,draft=drafts.get(id);sending=true;failedSend=null;updateComposer();clearError();
    try {
      await api(`/rooms/${id}/messages`,'POST',{text:draft.text,clientId:draft.clientId});
      if(drafts.get(id)?.clientId===draft.clientId)drafts.delete(id);
      if(current===id){$('[data-chat-input]').value='';await openRoom(id);$('[data-chat-input]').focus();}
      await refreshList();status(t('sent'));
    } catch(e){failedSend=id;if(current===id)error(e);} finally{sending=false;if(root?.isConnected)updateComposer();}
  }
  function closeDialog() { dialogSequence++;clearTimeout(searchTimer);$('[data-chat-dialog]').close();dialogOpener?.focus(); }
  function dialogHeading(key) { $('[data-chat-dialog-title]').textContent=t(key); }
  function openDialog(mode) {
    dialogMode=mode;selected=new Map();dialogOpener=document.activeElement;dialogSequence++;
    const modal=$('[data-chat-dialog]');$('[data-chat-dialog-body]').replaceChildren();$('[data-chat-dialog-error]').hidden=true;
    dialogHeading(mode==='new'?'newChat':'members');if(!modal.open)modal.showModal();
    if(mode==='new') {
      const tabs=node('div','chat-create-tabs');['newDirect','newGroup'].forEach((key,i)=>{const b=button(t(key),()=>chooseType(i===0?'DIRECT':'GROUP'));b.dataset.createType=i===0?'DIRECT':'GROUP';tabs.append(b);});$('[data-chat-dialog-body]').append(tabs,node('div','chat-picker'));
      chooseType('DIRECT');
    } else showMembers();
  }
  function picker(type, adding=false) {
    dialogMode=adding?'add':type;selected=new Map();dialogSequence++;
    const host=$('.chat-picker');host.replaceChildren();
    const form=node('form','chat-create-form');
    if(type==='GROUP'&&!adding){const label=node('label','',t('name'));label.htmlFor='chatGroupName';const input=node('input');input.id='chatGroupName';input.name='name';input.required=true;input.minLength=2;input.maxLength=80;input.placeholder=t('namePlaceholder');form.append(label,input);}
    const label=node('label','',t('searchPeople'));label.htmlFor='chatPeopleSearch';const search=node('input');search.type='search';search.id='chatPeopleSearch';search.maxLength=80;search.autocomplete='off';search.placeholder='Имя, никнейм или SH-ID';
    const help=node('p','chat-picker-hint',t('peopleHint'));const choices=node('div','chat-people');choices.setAttribute('aria-live','polite');
    const picked=node('div','chat-picked');picked.setAttribute('aria-live','polite');const submit=node('button','chat-button primary',t(adding?'save':type==='DIRECT'?'startChat':'create'));submit.type='submit';submit.disabled=true;
    form.append(label,search,help,picked,choices,submit);host.append(form);
    let lookup=0;
    const updatePicked=()=>{picked.replaceChildren();for(const person of selected.values()){const chip=button(`${personName(person)} ×`,()=>{selected.delete(person.id);updatePicked();choices.querySelectorAll('input').forEach(el=>el.checked=selected.has(Number(el.value)));},'chat-person-chip');chip.setAttribute('aria-label',`${t('cancel')}: ${personName(person)}`);picked.append(chip);}submit.disabled=!selected.size;};
    const find=async()=>{
      const version=dialogSequence,request=++lookup;choices.replaceChildren(node('p','',t('loading')));
      try {
        const people=await api(`/users?q=${encodeURIComponent(search.value.trim())}`);if(version!==dialogSequence||request!==lookup)return;
        choices.replaceChildren();const eligible=people.filter(p=>!adding||!detail.members.some(m=>m.id===p.id));
        eligible.forEach(person=>{
          const row=node('label','chat-person-choice'),check=node('input');check.type=type==='DIRECT'?'radio':'checkbox';check.name='person';check.value=person.id;check.checked=selected.has(person.id);
          check.addEventListener('change',()=>{if(type==='DIRECT')selected.clear();if(check.checked)selected.set(person.id,person);else selected.delete(person.id);updatePicked();});
          row.append(check,node('span','chat-avatar small',personName(person).slice(0,1)),node('span','',`${personName(person)} · SH-${person.id}`));choices.append(row);
        });
        if(!eligible.length)choices.append(node('p','',t('noMatches')));
      }catch(e){if(version===dialogSequence){choices.replaceChildren();error(e,true);}}
    };
    search.addEventListener('input',()=>{clearTimeout(searchTimer);lookup++;searchTimer=setTimeout(find,250);});
    form.addEventListener('submit',async event=>{
      event.preventDefault();if(!selected.size||!form.reportValidity())return;
      if(selected.size>29){error(new Error('MEMBER_LIMIT'),true);return;}
      const version=dialogSequence;submit.disabled=true;$('[data-chat-dialog-error]').hidden=true;
      try {
        let room;
        if(adding){await api(`/rooms/${current}/members`,'POST',{userIds:[...selected.keys()]});room={id:current};}
        else room=await api(type==='DIRECT'?'/direct':'/groups','POST',type==='DIRECT'?{userId:[...selected.keys()][0]}:{name:form.elements.name.value.trim(),userIds:[...selected.keys()]});
        if(version!==dialogSequence)return;
        closeDialog();await refreshList();await openRoom(room.id);
      }catch(e){if(version===dialogSequence)error(e,true);}finally{submit.disabled=!selected.size;}
    });
    find();search.focus();
  }
  function chooseType(type) {
    $('[data-chat-dialog-body]').querySelectorAll('[data-create-type]').forEach(b=>{b.classList.toggle('selected',b.dataset.createType===type);b.setAttribute('aria-pressed',String(b.dataset.createType===type));});picker(type);
  }
  async function showMembers() {
    const host=$('[data-chat-dialog-body]'),id=current,version=dialogSequence;host.append(node('p','',t('loading')));
    try {
      const result=await api(`/rooms/${id}`);if(version!==dialogSequence)return;detail=result;host.replaceChildren();
      host.append(node('p','chat-picker-hint',detail.room.kind==='PROJECT'?'Состав чата соответствует участникам проекта.':t(detail.room.kind==='GROUP'?'groupHint':'privateHint')));
      const list=node('ul','chat-member-list');detail.members.forEach(person=>{const li=node('li');li.append(node('span','chat-avatar small',personName(person).slice(0,1)),node('span','',personName(person)+(person.id===meId?` (${t('you')})`:'')));if(person.id===detail.room.ownerId)li.append(node('small','',t('owner')));list.append(li);});host.append(list);
      if(detail.room.kind==='GROUP') {
        if(detail.room.ownerId===meId)host.append(button(t('add'),()=>{host.replaceChildren(node('div','chat-picker'));dialogHeading('add');picker('GROUP',true);}));
        host.append(button(t('leave'),()=>{
          host.replaceChildren(node('p','chat-picker-hint',t('leaveHint')));const leave=button(t('leave'),async()=>{
            leave.disabled=true;try{await api(`/rooms/${id}/leave`,'POST');closeDialog();drafts.delete(id);current=null;detail=null;rows=[];opening++;root.classList.remove('chat-has-room');$('[data-chat-conversation]').hidden=true;$('[data-chat-welcome]').hidden=false;await refreshList();}catch(e){error(e,true);leave.disabled=false;}
          },'chat-button danger');host.append(leave,button(t('cancel'),()=>{host.replaceChildren();showMembers();}));
        },'chat-button danger'));
      }
    }catch(e){if(version===dialogSequence){host.replaceChildren();error(e,true);}}
  }
  function mount() {
    const target=document.querySelector('[data-chat-root]');if(target===root)return;
    life?.abort();clearInterval(timer);clearTimeout(searchTimer);opening++;dialogSequence++;root=target;current=null;detail=null;rows=[];rooms=[];signature='';syncing=false;filter='all';
    if(!root)return;
    life=new AbortController();root.dataset.userContent='';
    root.innerHTML=`<div class="chat-page-heading"><div><p class="eyebrow">STUDHUB COMMUNITY</p><h2 data-chat-label="title"></h2><p data-chat-label="subtitle"></p></div><button type="button" class="chat-button primary" data-chat-new><i class="bi bi-plus-lg" aria-hidden="true"></i><span data-chat-label="newChat"></span></button></div>
      <p class="chat-demo-note" data-chat-demo data-chat-label="demo" ${preview?'':'hidden'}></p>
      <div class="chat-error" role="alert" data-chat-error hidden><span></span><button type="button" class="chat-button" data-chat-retry data-chat-label="retry"></button></div>
      <div class="chat-layout"><aside class="chat-inbox"><div class="chat-inbox-heading"><strong data-chat-label="title"></strong><span class="chat-total" data-chat-total>0</span></div><label class="chat-search"><i class="bi bi-search" aria-hidden="true"></i><input type="search" maxlength="80" data-chat-search data-chat-title="searchChats" data-chat-placeholder="searchChats"></label>
      <div class="chat-filters" role="group" data-chat-title="title"><button type="button" data-chat-filter="all" class="active" aria-pressed="true" data-chat-label="all"></button><button type="button" data-chat-filter="DIRECT" aria-pressed="false" data-chat-label="direct"></button><button type="button" data-chat-filter="GROUP" aria-pressed="false" data-chat-label="groups"></button></div><div class="chat-room-list" data-chat-list></div><small class="chat-sync" data-chat-status role="status"></small></aside>
      <section class="chat-welcome" data-chat-welcome><i class="bi bi-chat-dots" aria-hidden="true"></i><h3 data-chat-label="choose"></h3><p data-chat-label="chooseHint"></p><button class="chat-button" type="button" data-chat-global data-chat-label="global"></button></section>
      <section class="chat-conversation" data-chat-conversation hidden aria-labelledby="chatRoomTitle"><header class="chat-conversation-header"><button type="button" class="chat-icon-button chat-back" data-chat-back data-chat-title="back"><i class="bi bi-arrow-left" aria-hidden="true"></i></button><span class="chat-avatar"><i class="bi bi-chat-dots" aria-hidden="true"></i></span><div class="chat-conversation-heading"><h3 id="chatRoomTitle" data-chat-room-title></h3><small data-chat-room-subtitle></small></div><button type="button" class="chat-icon-button" data-chat-members data-chat-title="members"><i class="bi bi-people" aria-hidden="true"></i></button></header>
      <p class="chat-room-hint" data-chat-room-hint></p><form data-chat-message-search class="chat-search"><label class="visually-hidden" for="chat-message-query">Поиск в сообщениях</label><input id="chat-message-query" type="search" minlength="2" maxlength="120" required placeholder="Поиск в сообщениях"><button type="submit" class="chat-button">Найти</button><button type="button" data-chat-search-close class="chat-button" hidden>Закрыть</button></form><div data-chat-search-results aria-live="polite" hidden></div><button type="button" class="chat-older" data-chat-older data-chat-label="older" hidden></button><div class="chat-messages" data-chat-messages role="log" aria-live="off" data-chat-title="message"></div><button type="button" class="chat-newest" data-chat-newest data-chat-label="newest" hidden></button>
      <form class="chat-composer" data-chat-composer><label class="visually-hidden" for="chatMessage" data-chat-label="message"></label><textarea id="chatMessage" rows="2" maxlength="4000" data-chat-input data-chat-placeholder="placeholder"></textarea><div class="chat-compose-footer"><small><span data-chat-label="hint"></span><span data-chat-count></span></small><button type="submit" class="chat-button primary" data-chat-send data-chat-label="send"></button></div></form></section></div>
      <dialog class="chat-dialog" data-chat-dialog aria-labelledby="chatDialogTitle"><div class="chat-dialog-heading"><h3 id="chatDialogTitle" data-chat-dialog-title></h3><button type="button" class="chat-icon-button" data-chat-close data-chat-title="close"><i class="bi bi-x-lg" aria-hidden="true"></i></button></div><div class="chat-error" role="alert" data-chat-dialog-error hidden><span></span></div><div data-chat-dialog-body></div></dialog>`;
    $('[data-chat-new]').onclick=()=>openDialog('new');$('[data-chat-close]').onclick=closeDialog;
    $('[data-chat-dialog]').addEventListener('cancel',event=>{event.preventDefault();closeDialog();});
    $('[data-chat-search]').addEventListener('input',renderList);
    root.querySelectorAll('[data-chat-filter]').forEach(b=>b.onclick=()=>{filter=b.dataset.chatFilter;root.querySelectorAll('[data-chat-filter]').forEach(el=>{el.classList.toggle('active',el===b);el.setAttribute('aria-pressed',String(el===b));});renderList();});
    $('[data-chat-message-search]').addEventListener('submit',async event=>{event.preventDefault();if(!current||preview)return;const id=current,q=$('#chat-message-query').value.trim(),host=$('[data-chat-search-results]');if(q.length<2)return;try{const found=await api(`/rooms/${encodeURIComponent(id)}/search?q=${encodeURIComponent(q)}`);if(id!==current)return;host.replaceChildren();host.hidden=false;$('[data-chat-search-close]').hidden=false;host.append(node('strong','',`Найдено: ${found.length} (показано не более 50)`));found.forEach(m=>{const entry=node('p');entry.append(node('strong','',personName(m.sender)+': '));appendText(entry,m.text);host.append(entry);});}catch(e){error(e);}});
    $('[data-chat-search-close]').onclick=()=>{$('[data-chat-search-results]').hidden=true;$('[data-chat-search-close]').hidden=true;};
    $('[data-chat-composer]').addEventListener('submit',send);
    $('[data-chat-input]').addEventListener('input',()=>{saveDraft();updateComposer();});
    $('[data-chat-input]').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();$('[data-chat-composer]').requestSubmit();}});
    $('[data-chat-members]').onclick=()=>openDialog('members');$('[data-chat-global]').onclick=()=>openRoom('global');$('[data-chat-older]').onclick=loadOlder;
    $('[data-chat-retry]').onclick=async()=>{clearError();if(failedSend===current&&current){$('[data-chat-composer]').requestSubmit();return;}await sync();if(current&&!detail)openRoom(current);};
    $('[data-chat-back]').onclick=()=>{saveDraft();root.classList.remove('chat-has-room');$('[data-chat-search]').focus();};
    $('[data-chat-newest]').onclick=()=>{scrollEnd();markRead();};
    $('[data-chat-messages]').addEventListener('scroll',()=>{if(nearBottom()){$('[data-chat-newest]').hidden=true;markRead();}},{passive:true});
    updateLabels();sync().then(()=>{const room=new URLSearchParams(location.search).get('room');if(room && rooms.some(r=>r.id===room))openRoom(room);});timer=setInterval(sync,4000);
  }
  if(preview&&!document.querySelector('[data-chat-nav]')) {
    const link=node('a','sidebar-link');link.href='#chat';link.dataset.previewRoute='chat';link.dataset.chatNav='';link.append(icon('chat-dots'),node('span','',t('title')));
    document.querySelector('.sidebar-nav .sidebar-label-secondary')?.before(link);if(location.hash==='#chat')link.classList.add('active');
  }
  window.StudHubChat={mount};mount();updateLabels();
  const main=document.getElementById('main-content');if(main)new MutationObserver(mount).observe(main,{childList:true});
  document.addEventListener('studhub:language',updateLabels);
  document.addEventListener('visibilitychange',()=>{if(!document.hidden&&root)sync();});
})();
