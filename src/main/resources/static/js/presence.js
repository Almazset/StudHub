(() => {
 'use strict';
 if(!document.body.classList.contains('app-shell') || location.protocol==='file:' || location.pathname.endsWith('studhub-preview.html'))return;
 let pending=false,stopped=false;
 async function heartbeat(){
  if(stopped||pending||document.hidden)return;
  const key=document.querySelector('meta[name="_csrf_header"]')?.content,token=document.querySelector('meta[name="_csrf"]')?.content;
  if(!key||!token)return;pending=true;
  try{const response=await fetch('/api/friends/presence',{method:'POST',headers:{[key]:token},credentials:'same-origin',signal:AbortSignal.timeout(10000)});
   if(response.redirected||[401,403].includes(response.status)){stopped=true;clearInterval(timer);}
  }catch{/* A missed heartbeat expires naturally; resume when connectivity returns. */}finally{pending=false;}
 }
 const timer=setInterval(heartbeat,25000);
 document.addEventListener('visibilitychange',heartbeat);window.addEventListener('online',heartbeat);heartbeat();
})();
