// Service worker do mapa: só mostra as notificações push dos chamados do cidadão.
// A mensagem chega cifrada pelo serviço de push e o navegador a decifra antes deste evento.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

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
