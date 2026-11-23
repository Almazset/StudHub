(() => {
  'use strict';
  const t = key => window.StudHubChatCopy(key);
  const safety = window.StudHubContactSafety;
  const preview = location.pathname.endsWith('studhub-preview.html');
  const node = (tag, cls, text) => { const el=document.createElement(tag); if(cls)el.className=cls; if(text!==undefined)el.textContent=text; return el; };
  const icon = name => { const el=node('i',`bi bi-${name}`); el.setAttribute('aria-hidden','true'); return el; };
  const button = (text, action, cls='chat-button') => { const el=node('button',cls,text); el.type='button'; if(action)el.addEventListener('click',action); return el; };
  const personName = person => person?.name || t('anonymousMember');
  const roomName = room => room.kind==='GLOBAL' ? t('global') : room.name || t('anonymousMember');
  const time = value => new Intl.DateTimeFormat(window.StudHubI18n?.language || 'ru',{hour:'2-digit',minute:'2-digit'}).format(new Date(value));
  let root, life, timer, current=null, meId, rooms=[], detail, rows=[], older=false, opening=0, syncing=false, sending=false, filter='all', signature='';
  let roomsState='loading';
  let editing=false, replying=null, browsing=false, networkError=false, resultSequence=0, renderPending=false;
  const MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024;
  const FILE_EXTENSION = /\.(?:png|jpe?g|pdf|docx|zip)$/i;
  const drafts=new Map(), reads=new Map(), chosenFiles=new Map();
  const messagePreview = message => message?.text || message?.attachment?.name || '';
  const fileSize = size => size < 1024 * 1024
    ? `${Math.max(1,Math.round(size / 1024))} ${t('kilobytes')}`
    : `${(size / (1024 * 1024)).toLocaleString(window.StudHubI18n?.language || 'ru',{maximumFractionDigits:1})} ${t('megabytes')}`;
  let dialogSequence=0, dialogMode, selected=new Map(), searchTimer, dialogOpener, failedSend=null;
  const $ = selector => root?.querySelector(selector);
  const canSend = () => detail?.room.canSend !== false;
  function openUserActions(id, name, opener, messageId) {
    safety.open({id, name}, {opener, messageId, onBlock: sync, fallbackFocus: () => $('[data-chat-messages]')});
  }
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
  function uploadMessage(room, draft) {
    const data = new FormData();
    const {text, clientId, replyToId, file} = draft;
    data.append('message', new Blob([JSON.stringify({text, clientId, replyToId})], {type:'application/json'}));
    data.append('file', file);
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest(), signal = life.signal;
      const abort = () => xhr.abort();
      xhr.open('POST', `/api/chat/rooms/${encodeURIComponent(room)}/messages`);
      xhr.timeout = 60000;
      const token = document.querySelector('meta[name="_csrf"]')?.content;
      const header = document.querySelector('meta[name="_csrf_header"]')?.content;
      if(token && header) xhr.setRequestHeader(header,token);
      xhr.responseType = 'json';
      xhr.upload.addEventListener('progress', event => {
        if(room === current && event.lengthComputable) showUpload(Math.round(event.loaded / event.total * 100));
      });
      xhr.addEventListener('load', () => {
        if(xhr.status >= 200 && xhr.status < 300 && xhr.response) resolve(xhr.response);
        else if(xhr.response?.error) reject(new Error(xhr.response.error));
        else if(xhr.status === 413) reject(new Error('FILE_LIMIT'));
        else if(xhr.status === 401 || xhr.status === 403 || new URL(xhr.responseURL,location.href).pathname === '/login')
          reject(new Error('LOGIN'));
        else reject(new Error('NETWORK'));
      });
      xhr.addEventListener('error', () => reject(new Error('NETWORK')));
      xhr.addEventListener('timeout', () => reject(new Error('NETWORK')));
      xhr.addEventListener('abort', () => reject(new DOMException('Aborted','AbortError')));
      xhr.addEventListener('loadend', () => signal.removeEventListener('abort',abort));
      signal.addEventListener('abort',abort,{once:true});
      xhr.send(data);
    });
  }

  function showUpload(percent) {
    const box = $('[data-chat-upload]');
    if(!box) return;
    box.hidden = percent === null;
    if(percent === null) return;
    box.querySelector('progress').value = percent;
    box.querySelector('span').textContent = percent === 100 ? t('fileChecking') : `${t('fileUploading')} ${percent}%`;
  }
  function error(error, inDialog=false) {
    if(!root?.isConnected || error.name==='AbortError')return;
    const box=$(inDialog?'[data-chat-dialog-error]':'[data-chat-error]');
    if(box){box.hidden=false;box.querySelector('span').textContent=t(error.message)===error.message?t('NETWORK'):t(error.message);}
    if(error.message==='NETWORK'){networkError=true;status(t('reconnecting'));}
    if(error.message==='DIRECT_UNAVAILABLE' && !inDialog) queueMicrotask(sync);
    if(error.message==='NOT_FOUND' && !inDialog){ rows=[]; renderMessages(); $('[data-chat-composer]').hidden=true; }
  }
  function status(text='') { const el=$('[data-chat-status]');if(el&&el.textContent!==text)el.textContent=text; }
  function clearError() { $('[data-chat-error]').hidden=true; }
  function nearBottom() { const box=$('[data-chat-messages]'); return box && box.scrollHeight-box.scrollTop-box.clientHeight<80; }
  function scrollEnd() { const box=$('[data-chat-messages]');box.scrollTop=box.scrollHeight;$('[data-chat-newest]').hidden=true; }
  function saveDraft() {
    if(!current) return;
    const text = $('[data-chat-input]')?.value || '';
    const oldDraft = drafts.get(current), file = chosenFiles.get(current);
    const replyToId = replying?.id || null;
    const sameDraft = oldDraft && oldDraft.text === text && oldDraft.replyToId === replyToId && oldDraft.file === file;
    drafts.set(current,{text, replyToId, file, reply:replying, clientId:sameDraft ? oldDraft.clientId : crypto.randomUUID()});
  }
  function updateComposer() {
    const quote=$('[data-chat-reply]');if(quote){quote.replaceChildren();quote.hidden=!replying;if(replying){quote.append(node('span','',`Ответ: ${replying.text.slice(0,160)}`),button('Отменить ответ',()=>{replying=null;saveDraft();updateComposer();}));}}

    const input=$('[data-chat-input]'); $('[data-chat-count]').textContent=`${input.value.length} / 4000`;
    input.readOnly=sending || !canSend();
    const picked = chosenFiles.get(current), fileBox = $('[data-chat-file-picked]');
    $('[data-chat-attach]').disabled = sending || !canSend() || !current;
    fileBox.hidden = !picked;
    $('[data-chat-file-name]').textContent = picked ? `${picked.name} · ${fileSize(picked.size)}` : '';
    $('[data-chat-file-remove]').disabled = sending;
    $('[data-chat-send]').disabled=sending || !canSend() || (!input.value.trim() && !picked) || !current;
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
    const next=JSON.stringify([visible,current,filter,query,roomsState,window.StudHubI18n?.language]);if(next===signature)return;signature=next;
    const list=$('[data-chat-list]'),focusedRoom=document.activeElement?.dataset.chatRoomId;
    list.replaceChildren();
    list.setAttribute('aria-busy',String(roomsState==='loading'));
    if(roomsState==='loading')list.append(node('p','chat-loading',t('loading')));
    visible.sort((a,b)=>Number(b.kind==='GLOBAL')-Number(a.kind==='GLOBAL')).forEach(room=>{
      const el=button('',()=>openRoom(room.id,true),'chat-room');
      el.dataset.chatRoomId=room.id;
      el.title=roomName(room);
      el.classList.toggle('active',room.id===current);
      el.setAttribute('aria-pressed',String(room.id===current));
      const avatar=node('span','chat-avatar');avatar.append(icon(room.kind==='GLOBAL'?'globe2':['GROUP','PROJECT'].includes(room.kind)?'people':'person'));el.append(avatar);
      const text=node('span','chat-room-copy'),line=node('span','chat-room-line');line.append(node('strong','',roomName(room)));
      if(room.lastMessage)line.append(node('time','',time(room.lastMessage.createdAt)));text.append(line);
      const summary=node('span','chat-room-preview',messagePreview(room.lastMessage) || t(room.kind==='GLOBAL'?'globalHint':'start'));text.append(summary);el.append(text);
      if(room.unread){const badge=node('span','chat-unread',room.unread>99?'99+':room.unread);badge.setAttribute('aria-label',`${room.unread} ${t('unread')}`);el.append(badge);}
      list.append(el);
      if(el.dataset.chatRoomId===focusedRoom)el.focus({preventScroll:true});
    });
    if(roomsState==='ready'&&!visible.length)list.append(node('p','chat-empty-list',t(query?'noMatches':'emptyList')));
    if(focusedRoom&&!list.contains(document.activeElement))$('[data-chat-search]').focus({preventScroll:true});
    $('[data-chat-total]').textContent=rooms.length;
  }
  function renderHeader() {
    const room=detail.room;
    $('[data-chat-room-title]').textContent=roomName(room);
    $('[data-chat-room-subtitle]').textContent=room.kind==='GLOBAL'?t('globalHint'):room.kind==='DIRECT'?t('personal'):`${t('members')}: ${room.memberCount}`;
    const hint = $('[data-chat-room-hint]');
    const label = t(!canSend()?'DIRECT_UNAVAILABLE':room.kind==='GLOBAL'?'globalHint':room.kind==='DIRECT'?'privateHint':'groupHint');
    if (hint.textContent !== label) hint.textContent = label;
    $('[data-chat-composer]').hidden = !canSend();
    const safetyButton = $('[data-chat-room-safety]');
    safetyButton.hidden = preview || room.kind !== 'DIRECT' || !Number.isSafeInteger(room.peerId);
    if (!safetyButton.hidden) {
      safetyButton.setAttribute('aria-label', safety.t('safety'));
      safetyButton.title = safety.t('safety');
    }
    $('[data-chat-members]').hidden=room.kind==='GLOBAL';
  }
  function appendText(element,text) {
    const parts=text.split(/(https?:\/\/[^\s<>]+)/g);
    parts.forEach(part=>{
      if(/^https?:\/\//.test(part)) { const link=node('a','',part);link.href=part;link.target='_blank';link.rel='noopener noreferrer';element.append(link); }
      else element.append(document.createTextNode(part));
    });
  }
  function messageElement(id) { return $(`[data-message-id="${CSS.escape(String(id))}"]`); }
  function focusMessageActions(id) {
    (messageElement(id)?.querySelector('.chat-message-toggle') || $('[data-chat-messages]')).focus({preventScroll:true});
  }
  function messageControls(message) {
    return Array.from(message.querySelectorAll('button:not(:disabled),a[href]')).filter(control=>!control.closest('.chat-message-menu'));
  }
  function renderMessages() {
    const list=$('[data-chat-messages]');if(!list)return;
    if(editing||list.querySelector('.chat-message-menu:popover-open')){renderPending=true;return;}
    renderPending=false;
    const active=document.activeElement;
    const message=list.contains(active)?active.closest('[data-message-id]'):null;
    const focusIndex=message?messageControls(message).indexOf(active):-1;
    list.replaceChildren();
    if(!rows.length){const empty=node('div','chat-conversation-empty');empty.append(icon('chat-square-text'),node('h3','',t(canSend()?'start':'emptyHistory')));if(canSend())empty.append(node('p','',t('startHint')));list.append(empty);}
    let lastDate='';
    rows.forEach(message=>{
      const date=new Date(message.createdAt).toLocaleDateString(window.StudHubI18n?.language || 'ru',{day:'numeric',month:'long',year:'numeric'});
      if(date!==lastDate){list.append(node('div','chat-date',date));lastDate=date;}
      const own=message.sender.id===meId, item=node('article',`chat-message${own?' own':''}`);item.dataset.messageId=message.id;item.tabIndex=-1;
      const meta=node('div','chat-message-meta');meta.append(node('strong','',own?t('you'):personName(message.sender)));
      const stamp=node('time','',time(message.createdAt));stamp.dateTime=message.createdAt;stamp.title=new Date(message.createdAt).toLocaleString();meta.append(stamp);
      const body=node('p','chat-message-body');appendText(body,message.text);body.hidden=!message.text;item.append(meta,body);
      if(message.attachment && !preview) {
        const link = node('a','chat-attachment');
        link.href = `/api/chat/rooms/${encodeURIComponent(current)}/messages/${message.id}/attachment`;
        link.download = '';
        link.append(icon('paperclip'),node('strong','',message.attachment.name),
          node('small','',`${fileSize(message.attachment.size)} · ${t('fileDownload')}`));
        item.append(link);
      }
      if(message.forwarded)item.prepend(node('small','chat-message-note','Пересланное сообщение'));
      if(message.pinned)meta.append(node('small','chat-message-note','Закреплено'));
      if(message.reply){const quote=button(message.reply.text ? `${personName(message.reply.sender)}: ${message.reply.text.slice(0,180)}` : 'Сообщение удалено',()=>jumpTo(message.reply.id),'chat-quote');quote.disabled=!message.reply.text;body.before(quote);}
      if(message.reactions?.length){const strip=node('div','chat-reactions');message.reactions.forEach(r=>{const b=button(`${r.emoji} ${r.count}`,()=>messageAction(message,'reaction',{emoji:r.mine?'':r.emoji}),'chat-reaction');b.disabled=!canSend();b.setAttribute('aria-pressed',String(r.mine));b.setAttribute('aria-label',`${r.emoji}: ${r.count}. ${r.mine?'Убрать реакцию':'Добавить реакцию'}`);strip.append(b);});item.append(strip);}
      if(message.editedAt)meta.append(node('small','','изменено'));
      if(!preview){
        const menu=node('div','chat-message-actions'),toggle=button('',null,'chat-message-toggle');
        toggle.setAttribute('aria-label',t('messageActions'));
        toggle.title=t('messageActions');
        toggle.setAttribute('aria-expanded','false');
        toggle.append(icon('chevron-down'));
        const actions=node('div','chat-message-menu');
        actions.popover='auto';
        toggle.popoverTargetElement=actions;
        actions.addEventListener('toggle',event=>{
          const opened=event.newState==='open';
          toggle.setAttribute('aria-expanded',String(opened));
          if(opened){
            const anchor=toggle.getBoundingClientRect(), popup=actions.getBoundingClientRect();
            actions.style.left=`${Math.max(8,Math.min(document.documentElement.clientWidth-popup.width-8,anchor.right-popup.width))}px`;
            actions.style.top=`${Math.max(8,Math.min(innerHeight-popup.height-8,anchor.bottom+4))}px`;
          } else if(renderPending)renderMessages();
        });
        const closeActions=()=>{
          actions.hidePopover();
          toggle.focus({preventScroll:true});
        };
        const edit=button('Изменить',()=>{
          closeActions();if(editing)return;editing=true;const roomId=current;
          const form=node('form','chat-message-edit'),input=node('textarea'),actions=node('div','chat-edit-actions'),notice=node('p');
          input.value=message.text;input.maxLength=4000;input.required=!message.attachment;input.rows=3;input.setAttribute('aria-label',t('message'));
          notice.setAttribute('role','alert');const save=button('Сохранить');save.type='submit';
          const cancel=button(t('cancel'),()=>{editing=false;renderMessages();focusMessageActions(message.id);});
          actions.append(save,cancel);form.append(input,actions,notice);body.hidden=true;menu.hidden=true;item.append(form);input.focus();
          input.addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();cancel.click();}});
          form.addEventListener('submit',async e=>{e.preventDefault();if(!message.attachment&&!input.value.trim()){notice.textContent='Введите текст сообщения.';return;}
            if(document.activeElement===save)input.focus({preventScroll:true});
            save.disabled=true;cancel.disabled=true;
            input.readOnly=true;
            try{const updated=await api(`/rooms/${roomId}/messages/${message.id}`,'PUT',{text:input.value});if(current===roomId){const index=rows.findIndex(m=>m.id===updated.id);if(index!==-1)rows[index]=updated;const restoreFocus=form.contains(document.activeElement);editing=false;renderMessages();if(restoreFocus)focusMessageActions(message.id);}}
            catch(e){notice.textContent='Не удалось сохранить сообщение. Попробуйте ещё раз.';}
            finally{save.disabled=false;cancel.disabled=false;input.readOnly=false;}
          });
        },'chat-action');
        const remove=button('Удалить',async()=>{closeActions();if(!confirm('Удалить сообщение для всех участников?'))return;const id=current;remove.disabled=true;try{await api(`/rooms/${id}/messages/${message.id}`,'DELETE');if(current===id){rows=rows.filter(m=>m.id!==message.id);renderMessages();await refreshList();}}catch(e){error(e);}finally{remove.disabled=false;}},'chat-action');
        if(canSend()) actions.append(button('Ответить',()=>{closeActions();replying={id:message.id,text:messagePreview(message)};saveDraft();updateComposer();$('[data-chat-input]').focus();},'chat-action'));
        const reactions=node('div','chat-reactions');['👍','❤️','😂','🎉','🤔','👀'].forEach(emoji=>{const b=button(emoji,()=>{closeActions();messageAction(message,'reaction',{emoji:message.reactions?.some(r=>r.emoji===emoji&&r.mine)?'':emoji});},'chat-reaction');b.setAttribute('aria-label',`Реакция ${emoji}`);reactions.append(b);});if(canSend()) actions.append(reactions);
        actions.append(button('Переслать',()=>{closeActions();forwardDialog(message,toggle);},'chat-action'));
        actions.append(button(message.favorite?'Убрать из избранного':'В избранное',()=>{closeActions();messageAction(message,'favorite',{enabled:!message.favorite});},'chat-action'));
        if(canSend() && detail?.canPin)actions.append(button(message.pinned?'Открепить':'Закрепить',()=>{closeActions();messageAction(message,'pin',{enabled:!message.pinned});},'chat-action'));
        if(own && canSend()) actions.append(edit);
        if(own) actions.append(remove);
        if(!own && Number.isSafeInteger(message.sender.id)) {
          actions.append(button(safety.t('safety'),()=>{closeActions();openUserActions(message.sender.id,personName(message.sender),toggle,message.id);},'chat-action'));
        }
        menu.append(toggle,actions);meta.append(menu);
      }
      list.append(item);
    });
    $('[data-chat-older]').hidden=!older;
    if(message){
      const updated=messageElement(message.dataset.messageId);
      (updated && (messageControls(updated)[focusIndex] || updated) || list).focus({preventScroll:true});
    }
  }
  async function messageAction(message,action,payload) {
    const room=current;try{const updated=await api(`/rooms/${room}/messages/${message.id}/${action}`,'PUT',payload);
      if(current===room){rows=rows.map(m=>m.id===updated.id?updated:m);const box=$('[data-chat-messages]'),top=box.scrollTop;renderMessages();box.scrollTop=top;}
    }catch(e){error(e);}
  }
  async function showResults(path,title) {
    if(!current||preview)return;const room=current,version=++resultSequence,host=$('[data-chat-search-results]');
    host.hidden=false;host.replaceChildren(node('p','',t('loading')));$('[data-chat-search-close]').hidden=false;
    try{const found=await api(`/rooms/${room}${path}`);if(room!==current||version!==resultSequence)return;
      host.replaceChildren(node('strong','',`${title}: ${found.length}`));if(!found.length)host.append(node('p','','Сообщений нет.'));
      found.forEach(m=>{const b=button('',()=>jumpTo(m.id),'chat-search-result');b.append(node('strong','',`${personName(m.sender)} · ${time(m.createdAt)}`),node('span','',messagePreview(m)));host.append(b);});
    }catch(e){if(room===current&&version===resultSequence){host.replaceChildren();error(e);}}
  }
  async function jumpTo(id) {
    const room=current,version=++opening;try{const result=await api(`/rooms/${room}/around/${id}`);if(version!==opening||room!==current)return;
      editing=false;browsing=true;rows=result.messages;older=result.hasMore;renderMessages();$('[data-chat-search-results]').hidden=true;
      $('[data-chat-search-close]').hidden=true;
      const target=messageElement(id);
      target?.scrollIntoView({block:'center'});
      target?.focus({preventScroll:true});
      target?.classList.add('chat-message-highlight');
      $('[data-chat-newest]').hidden=false;
    }catch(e){if(room===current)error(e);}
  }
  function forwardDialog(message,opener) {
    const source=current,token=crypto.randomUUID();dialogOpener=opener;dialogSequence++;
    const modal=$('[data-chat-dialog]'),host=$('[data-chat-dialog-body]');$('[data-chat-dialog-title]').textContent='Переслать сообщение';$('[data-chat-dialog-error]').hidden=true;
    host.replaceChildren(node('p','','Выберите чат. Получатели увидят отдельную копию сообщения вместе с вложением, если оно есть.'));
    const destinations = rooms.filter(room => room.id !== source && room.canSend !== false);
    if (!destinations.length) host.append(node('p','muted-copy',t('noForwardDestinations')));
    destinations.forEach(room=>{const b=button(roomName(room),async()=>{host.querySelectorAll('button').forEach(el=>el.disabled=true);
      try{await api(`/rooms/${source}/messages/${message.id}/forward`,'POST',{roomId:room.id,clientId:token});closeDialog();status('Сообщение переслано');await refreshList();}
      catch(e){error(e,true);host.querySelectorAll('button').forEach(el=>el.disabled=false);}
    });host.append(b);});modal.showModal();
  }
  async function markRead() {
    if(editing||renderPending||$('.chat-message-menu:popover-open')||browsing||!current||document.hidden||!$('[data-chat-messages]')?.getClientRects().length||!nearBottom()||!rows.length)return;
    const id=current,last=rows.at(-1).id;if((reads.get(id)||0)>=last)return;
    reads.set(id,last);
    try { await api(`/rooms/${id}/read`,'POST',{messageId:last}); if(current===id){const room=rooms.find(r=>r.id===id);if(room&&(!room.lastMessage||room.lastMessage.id<=last)){room.unread=0;renderList();}} }
    catch(e) {reads.delete(id);if(current===id)error(e);}
  }
  async function refreshList() {
    const result=await api('/rooms');if(!root?.isConnected)return;
    meId=result.meId;rooms=result.rooms;roomsState='ready';renderList();
  }
  async function openRoom(id,focusRoom=false) {
    if(!id)return;
    showUpload(null);
    root.querySelectorAll('.chat-message-menu:popover-open').forEach(menu=>menu.hidePopover());
    $('[data-chat-search-results]').hidden=true;$('[data-chat-search-close]').hidden=true;$('#chat-message-query').value='';
    editing=false;saveDraft();current=id;replying=drafts.get(id)?.reply||null;browsing=false;resultSequence++;$('[data-chat-input]').value=drafts.get(id)?.text || '';detail=null;rows=[];older=false;const version=++opening;clearError();
    root.classList.add('chat-has-room');$('[data-chat-welcome]').hidden=true;$('[data-chat-conversation]').hidden=false;
    const heading=$('[data-chat-room-title]');
    heading.textContent=t('loading');
    if(focusRoom)heading.focus({preventScroll:true});
    $('[data-chat-room-subtitle]').textContent='';$('[data-chat-room-hint]').textContent='';
    $('[data-chat-members]').hidden=true;$('[data-chat-room-safety]').hidden=true;$('[data-chat-composer]').hidden=true;$('[data-chat-messages]').replaceChildren(node('p','chat-loading',t('loading')));
    $('[data-chat-older]').hidden=true;$('[data-chat-newest]').hidden=true;renderList();
    try {
      const [room,history]=await Promise.all([api(`/rooms/${id}`),api(`/rooms/${id}/messages`)]);
      if(version!==opening||!root?.isConnected)return;
      detail=room;rows=history.messages;older=history.hasMore;renderHeader();renderMessages();scrollEnd();
      $('[data-chat-input]').value=drafts.get(id)?.text || '';updateComposer();await markRead();
    } catch(e) {if(version===opening){heading.textContent=t('title');$('[data-chat-messages]').replaceChildren();error(e);}}
  }
  async function sync() {
    if(syncing||sending||editing||!root?.isConnected||document.hidden)return;
    syncing=true;const id=current,version=opening,activeLife=life;
    try {
      await refreshList();
      if(id&&id===current&&detail) {
        const loaded=rows.slice(),loadedIds=new Set(loaded.map(m=>m.id)),cursor=loaded.at(-1)?.id||0;
        const history=browsing?{messages:[]}:await api(`/rooms/${id}/messages?after=${cursor}`);
        const refreshed=[];
        if(preview)refreshed.push(...(await api(`/rooms/${id}/messages`)).messages);
        else for(let offset=0;offset<loaded.length;offset+=500){refreshed.push(...await api(`/rooms/${id}/refresh`,'POST',{ids:loaded.slice(offset,offset+500).map(m=>m.id)}));}
        if(version!==opening||!root?.isConnected||editing)return;
        const bottom=nearBottom(),box=$('[data-chat-messages]'),top=box.scrollTop;
        const revised=new Map(refreshed.map(m=>[m.id,m]));
        const next=rows.filter(m=>!loadedIds.has(m.id)||revised.has(m.id)).map(m=>revised.get(m.id)||m);
        const known=new Set(next.map(m=>m.id));next.push(...history.messages.filter(m=>!known.has(m.id)));
        const latest=rooms.find(r=>r.id===id);
        const accessChanged=latest && latest.canSend!==detail.room.canSend;
        if(latest){
          detail.room=latest;
          if(latest.kind==='DIRECT' && typeof latest.canSend==='boolean') detail.canPin=latest.canSend;
          renderHeader();
          if(accessChanged) updateComposer();
        }
        if(accessChanged || JSON.stringify(next)!==JSON.stringify(rows)){
          rows=next;renderMessages();if(bottom&&!browsing)scrollEnd();else{box.scrollTop=top;if(history.messages.length)$('[data-chat-newest]').hidden=false;}
        }
        await markRead();
      }
      if(networkError&&!failedSend){clearError();networkError=false;}status(t(navigator.onLine?'updated':'offline'));
    } catch(e) {
      if(roomsState!=='ready' && e.name!=='AbortError'){roomsState='error';renderList();}
      if(version===opening){error(e);if(e.message!=='NETWORK')status('');}
    } finally {if(activeLife===life)syncing=false;}
  }
  async function loadOlder() {
    if(editing||!current||!rows.length)return;
    const id=current,version=opening,btn=$('[data-chat-older]');btn.disabled=true;
    try {
      const history=await api(`/rooms/${id}/messages?before=${rows[0].id}`);if(version!==opening)return;
      const box=$('[data-chat-messages]'),height=box.scrollHeight,top=box.scrollTop;
      rows=[...history.messages,...rows];older=history.hasMore;renderMessages();box.scrollTop=top+box.scrollHeight-height;
    } catch(e){error(e);} finally{btn.disabled=false;}
  }
  async function send(event) {
    event.preventDefault();if(sending||!canSend()||!current||(!$('[data-chat-input]').value.trim()&&!chosenFiles.has(current)))return;
    const composer=$('[data-chat-composer]'),input=$('[data-chat-input]');
    if(composer.contains(document.activeElement))input.focus({preventScroll:true});
    saveDraft();const id=current,draft=drafts.get(id);sending=true;failedSend=null;updateComposer();clearError();
    try {
      if(draft.file) {
        showUpload(0);
        await uploadMessage(id,draft);
      } else {
        await api(`/rooms/${id}/messages`,'POST',{text:draft.text,clientId:draft.clientId,replyToId:draft.replyToId});
      }
      chosenFiles.delete(id);
      if(drafts.get(id)?.clientId===draft.clientId)drafts.delete(id);
      if(current===id){
        const restoreFocus=composer.contains(document.activeElement);
        replying=null;
        input.value='';
        await openRoom(id);
        if(current===id && restoreFocus && document.activeElement===document.body)input.focus({preventScroll:true});
      }
      await refreshList();status(t('sent'));
    } catch(e){failedSend=e.message==='DIRECT_UNAVAILABLE'?null:id;if(current===id)error(e);} finally{sending=false;if(root?.isConnected){showUpload(null);updateComposer();}}
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
        closeDialog();await refreshList();await openRoom(room.id,true);
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
      const result=await api(`/rooms/${id}`);if(version!==dialogSequence)return;
      detail=result;renderHeader();updateComposer();host.replaceChildren();
      host.append(node('p','chat-picker-hint',detail.room.kind==='PROJECT'?'Состав чата соответствует участникам проекта.':t(detail.room.kind==='GROUP'?'groupHint':'privateHint')));
      const list=node('ul','chat-member-list');detail.members.forEach(person=>{const li=node('li');li.append(node('span','chat-avatar small',personName(person).slice(0,1)),node('span','',personName(person)+(person.id===meId?` (${t('you')})`:'')));if(person.id===detail.room.ownerId)li.append(node('small','',t('owner')));list.append(li);});host.append(list);
      if(detail.room.kind==='GROUP') {
        if(detail.room.ownerId===meId)host.append(button(t('add'),()=>{host.replaceChildren(node('div','chat-picker'));dialogHeading('add');picker('GROUP',true);}));
        host.append(button(t('leave'),()=>{
          host.replaceChildren(node('p','chat-picker-hint',t('leaveHint')));const leave=button(t('leave'),async()=>{
            leave.disabled=true;try{await api(`/rooms/${id}/leave`,'POST');closeDialog();drafts.delete(id);chosenFiles.delete(id);current=null;detail=null;rows=[];opening++;root.classList.remove('chat-has-room');$('[data-chat-conversation]').hidden=true;$('[data-chat-welcome]').hidden=false;await refreshList();}catch(e){error(e,true);leave.disabled=false;}
          },'chat-button danger');host.append(leave,button(t('cancel'),()=>{host.replaceChildren();showMembers();}));
        },'chat-button danger'));
      }
    }catch(e){if(version===dialogSequence){host.replaceChildren();error(e,true);}}
  }
  function mount() {
    const target=document.querySelector('[data-chat-root]');if(target===root)return;
    life?.abort();clearInterval(timer);clearTimeout(searchTimer);opening++;dialogSequence++;root=target;current=null;detail=null;rows=[];rooms=[];editing=false;renderPending=false;roomsState='loading';signature='';syncing=false;filter='all';
    if(!root)return;
    life=new AbortController();root.dataset.userContent='';
    root.innerHTML=`<div class="chat-page-heading"><div><p class="eyebrow">STUDHUB COMMUNITY</p><h2 data-chat-label="title"></h2><p data-chat-label="subtitle"></p></div><button type="button" class="chat-button primary" data-chat-new><i class="bi bi-plus-lg" aria-hidden="true"></i><span data-chat-label="newChat"></span></button></div>
      <p class="chat-demo-note" data-chat-demo data-chat-label="demo" ${preview?'':'hidden'}></p>
      <div class="chat-error" role="alert" data-chat-error hidden><span></span><button type="button" class="chat-button" data-chat-retry data-chat-label="retry"></button></div>
      <p class="chat-sync" data-chat-status role="status"></p>
      <div class="chat-layout"><aside class="chat-inbox"><div class="chat-inbox-heading"><strong data-chat-label="title"></strong><span class="chat-total" data-chat-total>0</span></div><label class="chat-search"><i class="bi bi-search" aria-hidden="true"></i><input type="search" maxlength="80" data-chat-search data-chat-title="searchChats" data-chat-placeholder="searchChats"></label>
      <div class="chat-filters" role="group" data-chat-title="title"><button type="button" data-chat-filter="all" class="active" aria-pressed="true" data-chat-label="all"></button><button type="button" data-chat-filter="DIRECT" aria-pressed="false" data-chat-label="direct"></button><button type="button" data-chat-filter="GROUP" aria-pressed="false" data-chat-label="groups"></button></div><div class="chat-room-list" data-chat-list></div></aside>
      <section class="chat-welcome" data-chat-welcome><i class="bi bi-chat-dots" aria-hidden="true"></i><h3 data-chat-label="choose"></h3><p data-chat-label="chooseHint"></p><button class="chat-button" type="button" data-chat-global data-chat-label="global"></button></section>
      <section class="chat-conversation" data-chat-conversation hidden aria-labelledby="chatRoomTitle"><header class="chat-conversation-header"><button type="button" class="chat-icon-button chat-back" data-chat-back data-chat-title="back"><i class="bi bi-arrow-left" aria-hidden="true"></i></button><span class="chat-avatar"><i class="bi bi-chat-dots" aria-hidden="true"></i></span><div class="chat-conversation-heading"><h3 id="chatRoomTitle" data-chat-room-title tabindex="-1"></h3><small data-chat-room-subtitle></small></div><button type="button" class="chat-icon-button" data-chat-room-safety hidden><i class="bi bi-shield" aria-hidden="true"></i></button><button type="button" class="chat-icon-button" data-chat-members data-chat-title="members"><i class="bi bi-people" aria-hidden="true"></i></button></header>
      <p class="chat-room-hint" role="status" data-chat-room-hint></p><div class="chat-saved-tools"><button type="button" class="chat-button" data-chat-saved="favorites">Избранные</button><button type="button" class="chat-button" data-chat-saved="pins">Закреплённые</button></div><form data-chat-message-search class="chat-search"><label class="visually-hidden" for="chat-message-query">Поиск в сообщениях</label><input id="chat-message-query" type="search" minlength="2" maxlength="120" required placeholder="Поиск в сообщениях"><button type="submit" class="chat-button">Найти</button><button type="button" data-chat-search-close class="chat-button" hidden>Закрыть</button></form><div data-chat-search-results aria-live="polite" hidden></div><button type="button" class="chat-older" data-chat-older data-chat-label="older" hidden></button><div class="chat-messages" data-chat-messages role="log" tabindex="-1" aria-live="off" data-chat-title="message"></div><button type="button" class="chat-newest" data-chat-newest data-chat-label="newest" hidden></button>
      <form class="chat-composer" data-chat-composer><div class="chat-reply-preview" data-chat-reply hidden></div><label class="visually-hidden" for="chatMessage" data-chat-label="message"></label><textarea id="chatMessage" rows="2" maxlength="4000" data-chat-input data-chat-placeholder="placeholder"></textarea><div class="chat-upload" data-chat-upload hidden><progress value="0" max="100" aria-labelledby="chatUploadStatus"></progress><span id="chatUploadStatus" role="status"></span></div><div class="chat-file-picker" data-chat-file-picker><input type="file" accept=".png,.jpg,.jpeg,.pdf,.docx,.zip" data-chat-file hidden><button type="button" class="chat-button" data-chat-attach><i class="bi bi-paperclip" aria-hidden="true"></i><span data-chat-label="fileAttach"></span></button><div class="chat-file-picked" data-chat-file-picked hidden><span data-chat-file-name></span><button type="button" class="chat-icon-button" data-chat-file-remove data-chat-title="fileRemove"><i class="bi bi-x-lg" aria-hidden="true"></i></button></div></div><div class="chat-compose-footer"><small><span data-chat-label="hint"></span><span data-chat-count></span></small><button type="submit" class="chat-button primary" data-chat-send data-chat-label="send"></button></div></form></section></div>
      <dialog class="chat-dialog" data-chat-dialog aria-labelledby="chatDialogTitle"><div class="chat-dialog-heading"><h3 id="chatDialogTitle" data-chat-dialog-title></h3><button type="button" class="chat-icon-button" data-chat-close data-chat-title="close"><i class="bi bi-x-lg" aria-hidden="true"></i></button></div><div class="chat-error" role="alert" data-chat-dialog-error hidden><span></span></div><div data-chat-dialog-body></div></dialog>`;
    $('[data-chat-saved]').parentElement.hidden=preview;
    $('[data-chat-new]').onclick=()=>openDialog('new');$('[data-chat-close]').onclick=closeDialog;
    $('[data-chat-dialog]').addEventListener('cancel',event=>{event.preventDefault();closeDialog();});
    $('[data-chat-search]').addEventListener('input',renderList);
    root.querySelectorAll('[data-chat-filter]').forEach(b=>b.onclick=()=>{filter=b.dataset.chatFilter;root.querySelectorAll('[data-chat-filter]').forEach(el=>{el.classList.toggle('active',el===b);el.setAttribute('aria-pressed',String(el===b));});renderList();});
    $('[data-chat-message-search]').addEventListener('submit',event=>{event.preventDefault();const q=$('#chat-message-query').value.trim();if(q.length>=2)showResults(`/search?q=${encodeURIComponent(q)}`,'Результаты поиска (до 50)');});
    root.querySelectorAll('[data-chat-saved]').forEach(b=>b.onclick=()=>showResults(`/saved?kind=${b.dataset.chatSaved}`,`${b.textContent} (до 100)`));
    $('[data-chat-search-close]').onclick=()=>{resultSequence++;$('[data-chat-search-results]').hidden=true;$('[data-chat-search-close]').hidden=true;$('#chat-message-query').focus();};
    $('[data-chat-file-picker]').hidden = preview;
    const picker = $('[data-chat-file]');
    $('[data-chat-attach]').onclick = () => picker.click();
    $('[data-chat-file-remove]').onclick = () => {
      chosenFiles.delete(current);
      failedSend = null;
      clearError();
      saveDraft();
      updateComposer();
      $('[data-chat-attach]').focus();
    };
    picker.addEventListener('change', () => {
      const file = picker.files[0];
      picker.value = '';
      if(!file || !current || sending) return;
      if(!FILE_EXTENSION.test(file.name)) { error(new Error('FILE_TYPE')); return; }
      if(!file.size || file.size > MAX_ATTACHMENT_BYTES) { error(new Error('FILE_LIMIT')); return; }
      chosenFiles.set(current,file);
      failedSend = null;
      clearError();
      saveDraft();
      updateComposer();
    });
    $('[data-chat-composer]').addEventListener('submit',send);
    $('[data-chat-input]').addEventListener('input',()=>{saveDraft();updateComposer();});
    $('[data-chat-input]').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();$('[data-chat-composer]').requestSubmit();}});
    $('[data-chat-room-safety]').onclick=event=>{
      const room=detail?.room;
      if(room?.kind==='DIRECT') openUserActions(room.peerId,roomName(room),event.currentTarget);
    };
    $('[data-chat-members]').onclick=()=>openDialog('members');$('[data-chat-global]').onclick=()=>openRoom('global',true);$('[data-chat-older]').onclick=loadOlder;
    $('[data-chat-retry]').onclick=async()=>{clearError();if(failedSend===current&&current){$('[data-chat-composer]').requestSubmit();return;}await sync();if(current&&!detail)openRoom(current);};
    $('[data-chat-back]').onclick=()=>{saveDraft();root.classList.remove('chat-has-room');$('[data-chat-search]').focus();};
    $('[data-chat-newest]').onclick=()=>{if(browsing)openRoom(current);else{scrollEnd();markRead();}};
    $('[data-chat-messages]').addEventListener('scroll',()=>{if(!browsing&&nearBottom()){$('[data-chat-newest]').hidden=true;markRead();}},{passive:true});
    updateLabels();sync().then(()=>{const room=new URLSearchParams(location.search).get('room');if(room && rooms.some(r=>r.id===room))openRoom(room);});timer=setInterval(sync,4000);
  }
  if(preview&&!document.querySelector('[data-chat-nav]')) {
    const link=node('a','sidebar-link');link.href='#chat';link.dataset.previewRoute='chat';link.dataset.chatNav='';link.append(icon('chat-dots'),node('span','',t('title')));
    document.querySelector('.sidebar-nav .sidebar-label-secondary')?.before(link);if(location.hash==='#chat')link.classList.add('active');
  }
  window.StudHubChat={mount};mount();updateLabels();
  const main=document.getElementById('main-content');if(main)new MutationObserver(mount).observe(main,{childList:true});
  document.addEventListener('studhub:language',updateLabels);
  window.addEventListener('online',()=>{if(root)sync();});
  window.addEventListener('offline',()=>{if(root)error(new Error('NETWORK'));});
  document.addEventListener('visibilitychange',()=>{if(!document.hidden&&root)sync();});
})();
