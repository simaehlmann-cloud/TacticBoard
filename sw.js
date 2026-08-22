/* TacticBoard Service Worker
 *
 * Strategie:
 *   - App-Shell (index.html / Navigation): NETWORK FIRST.
 *     So bekommt jeder Nutzer beim naechsten Start automatisch die aktuelle
 *     Version. Der Cache dient nur noch als Offline-Fallback.
 *   - Uebrige Assets (Icons, Bibliotheken, datenschutz.html): CACHE FIRST,
 *     da sie sich praktisch nie aendern und offline sofort da sein muessen.
 */
const CACHE_NAME = 'tacticboard-v3.2';
const SHELL = './index.html';
const ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './image.png',
  './datenschutz.html'
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      // einzeln cachen: eine fehlende Datei darf die Installation nicht kippen
      .then(cache => Promise.all(ASSETS.map(url => cache.add(url).catch(() => {}))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.map(key => key !== CACHE_NAME ? caches.delete(key) : null)))
      .then(() => self.clients.claim())
  );
});

function putInCache(request, response) {
  if (!response || !response.ok || response.type === 'opaque') return response;
  const copy = response.clone();
  caches.open(CACHE_NAME).then(cache => cache.put(request, copy)).catch(() => {});
  return response;
}

self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const isShell = req.mode === 'navigate' || new URL(req.url).pathname.endsWith('/index.html');

  if (isShell) {
    // NETWORK FIRST - immer die neueste index.html, Cache nur als Fallback
    event.respondWith(
      fetch(req)
        .then(res => {
          if (res && res.ok) {
            const copy = res.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(SHELL, copy)).catch(() => {});
          }
          return res;
        })
        .catch(() => caches.match(SHELL).then(hit => hit || caches.match('./')))
    );
    return;
  }

  // CACHE FIRST fuer alles andere
  event.respondWith(
    caches.match(req).then(hit => {
      if (hit) return hit;
      return fetch(req)
        .then(res => putInCache(req, res))
        .catch(() => undefined);
    })
  );
});
