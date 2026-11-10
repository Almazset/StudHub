(() => {
  'use strict';
  if (!location.pathname.endsWith('studhub-preview.html')) return;
  const key = 'studhub-preview-chats-v1';
  const people = [{id:1,name:'Алмаз'},{id:2,name:'Мария Садыкова'},{id:3,name:'Данияр Омаров'},{id:4,name:'Алия Садыкова'}];
  const initial = () => {
    const now = Date.now();
    const message = (id,roomId,senderId,text,minutes) => ({id,roomId,senderId,text,createdAt:new Date(now-minutes*60000).toISOString(),clientId:crypto.randomUUID()});
    return {nextId:5,rooms:[
      {id:'global',kind:'GLOBAL',name:'StudHub',ownerId:null,userIds:[],lastRead:0,updatedAt:new Date(now-60000).toISOString()},
      {id:'demo-direct',kind:'DIRECT',name:'',ownerId:null,userIds:[1,2],lastRead:2,updatedAt:new Date(now-120000).toISOString()},
      {id:'demo-group',kind:'GROUP',name:'Готовимся к Java',ownerId:1,userIds:[1,3,4],lastRead:3,updatedAt:new Date(now-180000).toISOString()}
    ],messages:[message(1,'global',4,'Добро пожаловать! Здесь можно обсуждать учёбу и делиться полезными материалами.',30),message(2,'demo-direct',2,'Привет! Давай обсудим подготовку к проекту.',12),message(3,'demo-group',3,'Предлагаю сначала повторить коллекции, а потом решить задачи.',8),message(4,'global',3,'Документация Java: https://dev.java/learn/',1)]};
  };
  function load() { try { const value = JSON.parse(localStorage.getItem(key)); return value?.rooms && value?.messages ? value : initial(); } catch { return initial(); } }
  let state = load();
  try { if (!localStorage.getItem(key)) localStorage.setItem(key,JSON.stringify(state)); } catch { /* Sending reports storage errors explicitly. */ }
  const person = id => {
    let nickname = '';
    try { nickname = JSON.parse(localStorage.getItem('studhub-preview-settings-v1') || '{}').profile?.nickname || ''; } catch { /* Use the name when settings are unavailable. */ }
    return {...people.find(p=>p.id===id), ...(id===1 ? {name:nickname || localStorage.getItem('studhub-preview-name') || 'Алмаз'} : {})};
  };
  const visible = r => r.kind==='GLOBAL' || r.userIds.includes(1);
  const messageView = m => ({...m,sender:person(m.senderId)});
  function roomView(r) {
    const rows = state.messages.filter(m=>m.roomId===r.id), peerId = r.kind==='DIRECT' ? r.userIds.find(id=>id!==1) : null;
    return {...r,name:peerId ? person(peerId).name : r.name,peerId,memberCount:r.userIds.length,
      unread:rows.filter(m=>m.id>r.lastRead && m.senderId!==1).length,lastMessage:rows.length ? messageView(rows.at(-1)) : null};
  }
  window.StudHubChatDemo = async (path,method='GET',body) => {
    state = load(); const url = new URL(path,'http://preview.local'), parts = url.pathname.split('/').filter(Boolean);
    let result;
    if (path==='/rooms') return {meId:1,rooms:state.rooms.filter(visible).sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt)).map(roomView)};
    if (parts[0]==='users') return people.filter(p=>p.id!==1 && p.name.toLocaleLowerCase().includes((url.searchParams.get('q') || '').toLocaleLowerCase()));
    if (path==='/direct' || path==='/groups') {
      let room = path==='/direct' ? state.rooms.find(r=>r.kind==='DIRECT' && r.userIds.includes(body.userId)) : null;
      if (!room) {
        const ids = path==='/direct' ? [body.userId] : body.userIds;
        if (!ids?.length || ids.length>29 || ids.some(id=>id===1 || !people.some(p=>p.id===id))) throw new Error('VALIDATION');
        room = {id:crypto.randomUUID(),kind:path==='/direct'?'DIRECT':'GROUP',name:body.name?.trim() || '',ownerId:path==='/groups'?1:null,userIds:[1,...ids],lastRead:0,updatedAt:new Date().toISOString()};
        state.rooms.push(room);
      }
      result = roomView(room);
    } else {
      const room = state.rooms.find(r=>r.id===parts[1] && visible(r));
      if (!room) throw new Error('NOT_FOUND');
      if (parts.length===2) return {room:roomView(room),members:room.userIds.map(person)};
      if (parts[2]==='messages' && method==='GET') {
        const after=url.searchParams.get('after'), before=url.searchParams.get('before');
        const rows=state.messages.filter(m=>m.roomId===room.id && (!before || m.id<Number(before)) && (after===null || m.id>Number(after)));
        return {messages:(after===null?rows.slice(-50):rows.slice(0,50)).map(messageView),hasMore:rows.length>50};
      }
      if (parts[2]==='messages') {
        let message = state.messages.find(m=>m.roomId===room.id && m.senderId===1 && m.clientId===body.clientId);
        if (!message) { message={id:state.nextId++,roomId:room.id,senderId:1,text:body.text.trim(),clientId:body.clientId,createdAt:new Date().toISOString()}; state.messages.push(message); room.updatedAt=message.createdAt; }
        result=messageView(message);
      } else if (parts[2]==='read') { room.lastRead=Math.max(room.lastRead,body.messageId); result={saved:true}; }
      else if (parts[2]==='members') {
        if (room.ownerId!==1) throw new Error('OWNER_ONLY');
        room.userIds=[...new Set([...room.userIds,...body.userIds])]; result={saved:true};
      } else if (parts[2]==='leave') { room.userIds=room.userIds.filter(id=>id!==1); if(room.ownerId===1)room.ownerId=room.userIds[0] || null; result={saved:true}; }
    }
    try { localStorage.setItem(key,JSON.stringify(state)); } catch { throw new Error('STORAGE'); }
    return result;
  };
})();
