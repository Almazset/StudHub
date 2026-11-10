(() => {
  'use strict';
  const t = key => window.StudHubTutorCopy.t(key);
  const preview = location.pathname.endsWith('studhub-preview.html');
  const destinations = {
    courses:{preview:'courses',path:'/courses'}, assignments:{preview:'assignments',path:'/assignments'},
    settings:{preview:'settings-appearance',path:'/settings#appearance'}
  };
  const steps = [
    {key:'courses',selector:'.page-intro .btn-primary,.mobile-fab',title:'tourCourseTitle',text:'tourCourseText'},
    {key:'assignments',selector:'.page-intro .btn-primary,.mobile-fab',title:'tourTaskTitle',text:'tourTaskText'},
    {key:'settings',selector:'.settings-nav',title:'tourSettingsTitle',text:'tourSettingsText'}
  ];
  let card, target, observer, timer;
  function stop() {
    const active = Boolean(card);
    observer?.disconnect(); clearTimeout(timer); card?.remove(); target?.classList.remove('assistant-guide-target');
    card = null; target = null;
    const url = new URL(location.href); url.searchParams.delete('studhubTour'); history.replaceState(null,'',url.pathname + url.search + url.hash);
    if (active) document.querySelector('[data-assistant-open]')?.focus({preventScroll:true});
  }
  function navigate(key) {
    const destination = destinations[key]; if (!destination) return;
    document.dispatchEvent(new Event('studhub:assistant-hide'));
    if (preview) window.previewBaseRender(destination.preview); else location.assign(destination.path);
  }
  function tour(index = 0) {
    stop(); const step = steps[index]; if (!step) return;
    document.dispatchEvent(new Event('studhub:assistant-hide'));
    const destination = destinations[step.key];
    if (!preview && location.pathname !== destination.path.split('#')[0]) {
      const [path,hash] = destination.path.split('#'); location.assign(`${path}?studhubTour=${index}${hash ? `#${hash}` : ''}`); return;
    }
    if (preview) window.previewBaseRender(destination.preview);
    else if (step.key === 'settings') window.StudHubSettings?.open('appearance');
    const show = () => {
      target = [...document.querySelectorAll(step.selector)].find(element => element.getClientRects().length > 0);
      if (!target) return false;
      observer?.disconnect(); clearTimeout(timer);
      target.classList.add('assistant-guide-target'); target.scrollIntoView({block:'center',behavior:'instant'});
      card = document.createElement('aside'); card.className = 'assistant-guide-card'; card.dataset.aiGuide = ''; card.setAttribute('role','region'); card.setAttribute('aria-label',t('guide'));
      const heading = document.createElement('h2'); heading.textContent = t(step.title);
      const text = document.createElement('p'); text.textContent = t(step.text);
      const actions = document.createElement('div'); actions.className = 'assistant-guide-actions';
      const close = document.createElement('button'); close.type = 'button'; close.className = 'btn btn-light'; close.textContent = t('closeTour'); close.addEventListener('click',stop);
      const next = document.createElement('button'); next.type = 'button'; next.className = 'btn btn-primary'; next.textContent = t(index === steps.length-1 ? 'finish' : 'next'); next.addEventListener('click',() => index === steps.length-1 ? stop() : tour(index+1));
      actions.append(close,next); card.append(heading,text,actions);
      if (target.getBoundingClientRect().bottom > innerHeight * .65) { card.style.bottom = 'auto'; card.style.top = 'calc(var(--topbar-height) + 16px)'; }
      document.body.append(card); next.focus({preventScroll:true}); return true;
    };
    if (!show()) { observer = new MutationObserver(show); observer.observe(document.getElementById('main-content'),{childList:true,subtree:true}); timer = setTimeout(stop,7000); }
  }
  window.StudHubGuide = {
    navigate,tour,stop,
    answer(question) {
      const text = question.toLocaleLowerCase();
      let kind = /курс|предмет|course|пән/.test(text) ? 'Courses' : /задан|срок|дедлайн|assign|task|тапсыр|мерзім/.test(text) ? 'Assignments' : /настрой|профил|парол|безопас|setting|profile|password|баптау|қауіп|құпия/.test(text) ? 'Settings' : 'Welcome';
      return {text:t(`guide${kind}`),links:kind === 'Welcome' ? ['courses','assignments','settings'] : [kind.toLowerCase()]};
    }
  };
  document.addEventListener('keydown',event => { if (event.key === 'Escape' && card) stop(); });
  document.addEventListener('studhub:language',stop);
  const start = new URLSearchParams(location.search).get('studhubTour');
  if (start !== null && /^[0-2]$/.test(start)) tour(Number(start));
})();
