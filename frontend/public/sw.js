/*
 * Service worker básico de EnglishOS (PWA instalable, Fase 8).
 *
 * - La API (/api/…) NUNCA se guarda en caché: saldo, cobros y progreso siempre vienen del servidor.
 * - Archivos con hash de Vite (/assets/…): caché primero (son inmutables).
 * - Navegación: red primero; sin conexión se muestra la última versión de la aplicación (index.html) y la propia
 *   interfaz avisa que no hay conexión al fallar las llamadas a la API.
 */
const VERSION = 'englishos-v1';
const SHELL = ['/', '/manifest.webmanifest', '/favicon.svg', '/theme-init.js', '/icons/icon-192.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(VERSION).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;

  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(VERSION).then((cache) => cache.put('/', copy));
          return res;
        })
        .catch(() => caches.match('/').then((r) => r || Response.error())),
    );
    return;
  }

  if (url.pathname.startsWith('/assets/') || url.pathname.startsWith('/icons/')) {
    event.respondWith(
      caches.match(req).then(
        (hit) =>
          hit ||
          fetch(req).then((res) => {
            if (res.ok) {
              const copy = res.clone();
              caches.open(VERSION).then((cache) => cache.put(req, copy));
            }
            return res;
          }),
      ),
    );
  }
});
