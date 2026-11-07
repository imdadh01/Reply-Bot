const ids = ['backend', 'token', 'language', 'tone', 'instruction'];
(async () => {
  const saved = await chrome.storage.local.get(ids);
  for (const id of ids) if (saved[id]) document.getElementById(id).value = saved[id];
})();
document.getElementById('settings').addEventListener('submit', async event => {
  event.preventDefault();
  const status = document.getElementById('status');
  const settings = Object.fromEntries(ids.map(id => [id, document.getElementById(id).value.trim()]));
  try {
    const url = new URL(settings.backend);
    if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) throw new Error('Enter only an HTTPS server origin, without a path.');
    if (settings.token.length < 32) throw new Error('Pairing token must be at least 32 characters.');
    const granted = await chrome.permissions.request({ origins: [url.origin + '/*'] });
    if (!granted) throw new Error('Server permission was not granted.');
    settings.backend = url.origin;
    await chrome.storage.local.set(settings);
    status.textContent = 'Saved. Server ALLOWED_ORIGINS mein ye add karo: chrome-extension://' + chrome.runtime.id;
  } catch (error) { status.textContent = error.message; }
});
