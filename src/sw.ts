/// <reference lib="webworker" />
import { cleanupOutdatedCaches, precacheAndRoute } from 'workbox-precaching';
import { registerRoute } from 'workbox-routing';
import { NetworkFirst, CacheFirst, StaleWhileRevalidate } from 'workbox-strategies';
import { ExpirationPlugin } from 'workbox-expiration';

declare let self: ServiceWorkerGlobalScope & {
  __WB_MANIFEST: Array<{ revision: string | null; url: string }>;
};

cleanupOutdatedCaches();
precacheAndRoute(self.__WB_MANIFEST);

registerRoute(
  ({ url }: { url: URL }) =>
    /^https:\/\/(f1api\.dev|api\.rss2json\.com)\//.test(url.href),
  new NetworkFirst({
    cacheName: 'api-cache',
    plugins: [new ExpirationPlugin({ maxEntries: 50, maxAgeSeconds: 86400 })],
    networkTimeoutSeconds: 10,
  })
);

registerRoute(
  ({ url }: { url: URL }) => /^https:\/\/flagcdn\.com\//.test(url.href),
  new CacheFirst({
    cacheName: 'flag-images',
    plugins: [new ExpirationPlugin({ maxEntries: 100, maxAgeSeconds: 2592000 })],
  })
);

registerRoute(
  ({ url }: { url: URL }) =>
    /^https:\/\/fonts\.googleapis\.com\//.test(url.href),
  new StaleWhileRevalidate({ cacheName: 'google-fonts-stylesheets' })
);

registerRoute(
  ({ url }: { url: URL }) => /^https:\/\/fonts\.gstatic\.com\//.test(url.href),
  new CacheFirst({
    cacheName: 'google-fonts-webfonts',
    plugins: [new ExpirationPlugin({ maxEntries: 30, maxAgeSeconds: 31536000 })],
  })
);

self.addEventListener('notificationclick', (event: NotificationEvent) => {
  event.notification.close();
  event.waitUntil(
    self.clients
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then((clientList) => {
        for (const client of clientList) {
          if ('focus' in client) return (client as WindowClient).focus();
        }
        return self.clients.openWindow('/');
      })
  );
});

self.addEventListener('push', (event: PushEvent) => {
  if (!event.data) return;
  let data: { title?: string; body?: string; tag?: string } = {};
  try {
    data = event.data.json() as typeof data;
  } catch {
    return;
  }
  event.waitUntil(
    self.registration.showNotification(data.title ?? 'F1 Schedule Pro', {
      body: data.body,
      icon: '/pwa-192x192.png',
      badge: '/pwa-192x192.png',
      tag: data.tag,
      vibrate: [200, 100, 200],
    } as NotificationOptions)
  );
});
