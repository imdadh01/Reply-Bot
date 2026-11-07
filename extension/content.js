(() => {
  let panel;
  function stop(event) { event.preventDefault(); event.stopPropagation(); }
  function openPanel(article) {
    panel?.remove();
    const host = document.createElement('div');
    host.style.cssText = 'position:fixed;inset:0;z-index:2147483647;pointer-events:none';
    document.documentElement.append(host); panel = host;
    const root = host.attachShadow({ mode: 'closed' });
    const style = document.createElement('style');
    style.textContent = `*{box-sizing:border-box}section{position:absolute;right:18px;top:80px;width:min(400px,calc(100vw - 36px));max-height:80vh;overflow:auto;pointer-events:auto;background:#10151e;border:1px solid #394459;border-radius:20px;padding:22px;color:#edf2fa;box-shadow:0 20px 80px #0009;font:14px/1.5 system-ui}header{display:flex;align-items:center;justify-content:space-between}h2{font-size:20px;letter-spacing:-.5px;margin:0}small{color:#b5f477}p{color:#aebbd0;font-size:13px}button{cursor:pointer;border:0;background:#b5f477;color:#172b09;border-radius:8px;padding:8px 14px;font-weight:700}button:disabled{opacity:.5;cursor:wait}.close{background:#293345;color:white;font-size:18px}textarea{width:100%;resize:vertical;background:#1b2433;color:#fff;border:1px solid #394459;border-radius:10px;padding:12px;font:14px/1.5 system-ui;margin:12px 0 5px;min-height:88px}.source{max-height:160px;overflow:auto;white-space:pre-wrap}.hint{font-size:11px;color:#9caac0}button:focus-visible,textarea:focus-visible{outline:2px solid #b5f477}`;
    root.append(style);
    const section = document.createElement('section'); section.setAttribute('role', 'dialog'); section.setAttribute('aria-label', 'Reply suggestions');
    const header = document.createElement('header');
    const title = document.createElement('h2'); title.textContent = '✦ Reply Bot';
    const close = document.createElement('button'); close.textContent = '×'; close.className = 'close'; close.setAttribute('aria-label', 'Close'); close.onclick = () => host.remove();
    header.append(title, close); section.append(header);
    const sub = document.createElement('small'); sub.textContent = 'A little inspiration. Your final say.'; section.append(sub);
    const post = [...article.querySelectorAll('[data-testid="tweetText"]')].filter(el => el.closest('article') === article)[0];
    const source = document.createElement('p'); source.className = 'source'; source.textContent = post?.innerText || ''; section.append(source);
    const status = document.createElement('p'); status.setAttribute('role', 'status'); section.append(status);
    const results = document.createElement('div'); section.append(results);
    const generate = document.createElement('button'); generate.textContent = 'Suggest 3 replies'; section.append(generate);
    const hint = document.createElement('p'); hint.className = 'hint'; hint.textContent = 'This text goes to your configured AI when you generate. Images, videos, and linked pages are not read. Edit and copy a reply, then post it yourself.'; section.append(hint);
    root.append(section);
    host.addEventListener('keydown', e => { if (e.key === 'Escape') host.remove(); });
    close.focus();
    generate.onclick = async () => {
      generate.disabled = true; results.replaceChildren(); status.textContent = 'Thinking of something relevant…';
      try {
        const response = await chrome.runtime.sendMessage({ type: 'SUGGEST', text: source.textContent });
        if (!response?.ok) throw new Error(response?.error || 'No response. Reload X and try again.');
        status.textContent = `3 ideas · ${response.remaining} requests left today`;
        for (const suggestion of response.suggestions) {
          const card = document.createElement('div');
          const text = document.createElement('textarea'); text.value = suggestion; text.dir = 'auto'; text.setAttribute('aria-label', 'Edit suggested reply');
          const copy = document.createElement('button'); copy.textContent = 'Copy reply';
          copy.onclick = async () => { try { await navigator.clipboard.writeText(text.value); copy.textContent = 'Copied ✓'; } catch { status.textContent = 'Clipboard unavailable. Select and copy the reply manually.'; } };
          card.append(text, copy); results.append(card);
        }
      } catch (e) { status.textContent = e.message; } finally { generate.disabled = false; }
    };
    if (!source.textContent.trim()) { status.textContent = 'Is post ka text nahi mila. Text wali post kholo; photos aur videos abhi supported nahi hain.'; generate.disabled = true; }
    else generate.click();
  }
  function decorate() {
    for (const article of document.querySelectorAll('article[data-testid="tweet"]')) {
      const anchor = article.querySelector('[role="group"]');
      if (!anchor || article.querySelector('[data-reply-saathi]')) continue;
      const button = document.createElement('button'); button.type = 'button'; button.dataset.replySaathi = 'true';
      button.textContent = '✦ Reply Bot'; button.title = 'Get AI reply suggestions for this post';
      button.style.cssText = 'display:block;margin:8px 0;padding:7px 12px;border:1px solid #647e3b;border-radius:999px;color:#b5f477;background:#182511;font:600 12px system-ui;cursor:pointer';
      button.onclick = event => { stop(event); openPanel(article); };
      anchor.parentElement.append(button);
    }
  }
  let scheduled = false;
  new MutationObserver(() => {
    if (scheduled) return; scheduled = true;
    requestAnimationFrame(() => { scheduled = false; decorate(); });
  }).observe(document.body, { childList: true, subtree: true });
  decorate();
})();
