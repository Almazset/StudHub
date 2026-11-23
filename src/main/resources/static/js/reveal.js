/* Progressive enhancement: content remains visible if motion or JS is unavailable. */
(() => {
  'use strict';
  const main = document.getElementById('main-content');
  if (!main || !('IntersectionObserver' in window) || !Element.prototype.animate) return;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const selector = '.dashboard-heading,.page-intro,.stat-card,.quick-actions,.study-load,' +
    '.focus-panel,.progress-panel,.week-panel,.deadline-rail,.news-home,.course-card,' +
    '.news-card,.news-article,.settings-card,.settings-section-heading,.form-card,' +
    '.project-card,.empty-state';
  const registered = new WeakSet();
  const finished = new WeakSet();
  const active = new Map();
  const tokens = getComputedStyle(document.documentElement);
  const duration = parseFloat(tokens.getPropertyValue('--motion-reveal')) || 240;
  const easing = tokens.getPropertyValue('--motion-ease').trim() || 'cubic-bezier(.23,1,.32,1)';
  document.documentElement.classList.add('reveal-enabled');

  function cancel(element) {
    active.get(element)?.cancel();
    active.delete(element);
  }
  const observer = new IntersectionObserver(entries => {
    let order = 0;
    for (const entry of entries) {
      const element = entry.target;
      if (!entry.isIntersecting || !element.isConnected || finished.has(element)) continue;
      observer.unobserve(element);
      finished.add(element);
      // Keep content fully visible with reduced motion and during keyboard navigation.
      if (reduced.matches || document.documentElement.dataset.inputMethod === 'keyboard' ||
          element.contains(document.activeElement)) continue;
      const animation = element.animate([
        {opacity:0, transform:'translateY(8px)'},
        {opacity:1, transform:'translateY(0)'}
      ], {duration, delay:Math.min(order++ * 40,80), easing, fill:'backwards'});
      active.set(element, animation);
      animation.onfinish = () => { if (active.get(element) === animation) active.delete(element); };
    }
  }, {threshold:0, rootMargin:'0px 0px -20px 0px'});

  function register(root) {
    const candidates = [...(root.matches?.(selector) ? [root] : []), ...root.querySelectorAll(selector)];
    for (const element of candidates) {
      if (registered.has(element) || element.parentElement?.closest('[data-reveal-block]')) continue;
      registered.add(element);
      element.dataset.revealBlock = '';
      observer.observe(element);
    }
  }
  register(main);
  new MutationObserver(records => {
    for (const record of records) {
      for (const node of record.removedNodes) {
        if (node.nodeType !== Node.ELEMENT_NODE) continue;
        for (const element of [node, ...node.querySelectorAll('[data-reveal-block]')]) {
          observer.unobserve(element);
          cancel(element);
          // A move by sorting should remain visible, rather than replaying the effect.
          finished.add(element);
        }
      }
      for (const node of record.addedNodes) {
        if (node.nodeType === Node.ELEMENT_NODE) register(node);
      }
    }
  }).observe(main, {childList:true, subtree:true});
  main.addEventListener('focusin', event => {
    const block = event.target.closest('[data-reveal-block]');
    if (block) { finished.add(block); observer.unobserve(block); cancel(block); }
  });
  reduced.addEventListener('change', () => {
    if (reduced.matches) for (const element of [...active.keys()]) cancel(element);
  });
  document.addEventListener('keydown', () => {
    for (const element of [...active.keys()]) cancel(element);
  }, true);
})();
