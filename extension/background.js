const trusted = url => { try { const u = new URL(url); return u.protocol === 'https:' && ['x.com', 'twitter.com'].includes(u.hostname); } catch { return false; } };
chrome.runtime.onMessage.addListener((message, sender, respond) => {
  if (message?.type !== 'SUGGEST' || !trusted(sender.url || '')) return;
  (async () => {
    const settings = await chrome.storage.local.get(['backend', 'token', 'language', 'tone', 'instruction']);
    if (!settings.backend || !settings.token) throw new Error('Pehle extension icon se server aur pairing token set karo.');
    const endpoint = new URL(settings.backend);
    if (endpoint.protocol !== 'https:' || endpoint.username || endpoint.password) throw new Error('Server URL must use HTTPS.');
    if (!await chrome.permissions.contains({ origins: [endpoint.origin + '/*'] })) throw new Error('Server access missing. Save settings again.');
    const text = message.text;
    if (typeof text !== 'string' || text.length > 6000) throw new Error('Post text is too long. Maximum 6000 characters.');
    const response = await fetch(endpoint.origin + '/suggest', {
      method: 'POST', signal: AbortSignal.timeout(40000),
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + settings.token },
      body: JSON.stringify({ text, language: settings.language || 'Roman Urdu', tone: settings.tone || 'Friendly', instruction: settings.instruction || '' })
    });
    const body = await response.json();
    if (!response.ok) throw new Error(body.error || 'Server error');
    respond({ ok: true, ...body });
  })().catch(error => respond({ ok: false, error: error.message || 'Connection failed' }));
  return true;
});
