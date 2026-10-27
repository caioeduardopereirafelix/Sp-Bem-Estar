// Service worker do app: a tela de "sem internet" e as notificações push dos chamados do cidadão.
// A mensagem de push chega cifrada pelo serviço de push e o navegador a decifra antes do evento.

// Só a página de "sem internet" e o que ela usa ficam guardados. O resto vem sempre da rede: o mapa, os relatos e
// o código mudam a cada versão, e uma cópia velha no aparelho mostraria dados ou telas desatualizados.
const CACHE = 'spbemestar-offline-v1';
const OFFLINE_FILES = ['/offline.html', '/map.css', '/icons/icon-192.png', '/icons/favicon-32.png',
    '/fonts/atkinson-400.woff2', '/fonts/atkinson-700.woff2'];

self.addEventListener('install', (event) => {
    event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(OFFLINE_FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
    event.waitUntil(caches.keys()
        .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
        .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) {
        return; // outros domínios (mapa, bibliotecas) e envios seguem direto, sem passar por aqui
    }
    if (request.mode === 'navigate') {
        // abrir o app sem rede: em vez da página de erro do navegador, a explicação e o "tentar de novo"
        event.respondWith(fetch(request).catch(() => caches.match('/offline.html')));
        return;
    }
    if (OFFLINE_FILES.includes(new URL(request.url).pathname)) {
        // os arquivos da página de "sem internet": da rede quando dá, do aparelho quando não
        event.respondWith(fetch(request).catch(() => caches.match(request)));
    }
});

self.addEventListener('push', (event) => {
    let data;
    try {
        data = event.data ? event.data.json() : {};
    } catch {
        data = {body: event.data?.text() ?? ''};
    }
    const issueId = Number(data.issueId) || null;
    event.waitUntil(Promise.all([
        self.registration.showNotification(data.title || 'SP Bem-Estar', {
            body: data.body || 'Um chamado que você relatou mudou de status.',
            tag: data.tag,
            lang: 'pt-BR',
            data: {issueId},
        }),
        // com o mapa aberto, a lista "Meus relatos" se atualiza sozinha
        self.clients.matchAll({type: 'window'})
            .then((clients) => clients.forEach((client) => client.postMessage({type: 'issue-status', issueId}))),
    ]));
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    const issueId = event.notification.data?.issueId;
    // a URL é montada aqui, nunca vinda da mensagem: o clique só abre páginas do próprio site
    const url = new URL(issueId ? `/?issue=${issueId}` : '/', self.location.origin).href;
    event.waitUntil(self.clients.matchAll({type: 'window', includeUncontrolled: true}).then((clients) => {
        const open = clients.find((client) => new URL(client.url).origin === self.location.origin);
        if (open) {
            open.postMessage({type: 'open-issue', issueId});
            return open.focus();
        }
        return self.clients.openWindow(url);
    }));
});
