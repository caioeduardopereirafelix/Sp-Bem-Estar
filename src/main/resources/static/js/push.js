import {API} from './core.js';
import {citizenSignedIn, reporterFetch} from './reporter.js';
import {myReports, setMyReportsMessage} from './mine.js';
import {isIos, standalone} from './install.js';

export const push = {config: null, registration: null, subscription: null};

const pushSupported = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;

function base64UrlToBytes(value) {
    const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
    return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

function sameKey(buffer, key) {
    if (!buffer) {
        return false;
    }
    const a = new Uint8Array(buffer);
    const b = base64UrlToBytes(key);
    return a.length === b.length && a.every((byte, i) => byte === b[i]);
}

export async function setupPush() {
    const response = await fetch(`${API}/reporters/push/config`);
    if (!response.ok) {
        throw new Error(`Erro ${response.status}`);
    }
    push.config = await response.json();
    if (!push.config.enabled || !pushSupported) {
        renderPush();
        return;
    }
    push.registration = await navigator.serviceWorker.register('/sw.js');
    let subscription = await push.registration.pushManager.getSubscription();
    if (subscription && !sameKey(subscription.options.applicationServerKey, push.config.publicKey)) {
        await subscription.unsubscribe();
        subscription = null;
    }
    push.subscription = subscription;
    if (subscription && citizenSignedIn()) {
        await saveSubscription(subscription);
    }
    renderPush();
}

export function renderPush() {
    let text = '';
    let action = '';
    if (!push.config?.enabled || myReports.total === 0) {
        document.querySelectorAll('.push-box').forEach((box) => { box.hidden = true; });
        return;
    }
    if (!pushSupported) {
        text = isIos && !standalone()
            ? 'No iPhone, os avisos chegam pelo app: instale-o na tela de início (veja em Mais › Instalar o app).'
            : 'Este navegador não recebe notificações deste site.';
    } else if (Notification.permission === 'denied') {
        text = 'As notificações deste site estão bloqueadas. Libere nas configurações do navegador para ser avisado.';
    } else if (push.subscription) {
        text = 'Você será avisado neste aparelho quando o chamado for confirmado, entrar em andamento ou for resolvido.';
        action = 'Desligar avisos';
    } else {
        text = 'Quer saber quando a prefeitura confirmar, começar ou resolver o que você relatou?';
        action = 'Avisar quando o status mudar';
    }
    document.querySelectorAll('.push-box').forEach((box) => {
        box.hidden = false;
        box.querySelector('[data-push-status]').textContent = text;
        const toggle = box.querySelector('[data-push-toggle]');
        toggle.hidden = !action;
        toggle.textContent = action;
    });
}

export function saveSubscription(subscription) {
    return reporterFetch('/reporters/me/push-subscription', {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(subscription.toJSON()),
    });
}

async function togglePush(toggle) {
    toggle.disabled = true;
    setMyReportsMessage('');
    try {
        if (push.subscription) {
            const subscription = push.subscription;
            await reporterFetch('/reporters/me/push-subscription', {
                method: 'DELETE',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(subscription.toJSON()),
            });
            await subscription.unsubscribe();
            push.subscription = null;
        } else if (await Notification.requestPermission() === 'granted') {
            const subscription = await push.registration.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: base64UrlToBytes(push.config.publicKey),
            });
            await saveSubscription(subscription);
            push.subscription = subscription;
        }
    } catch (error) {
        setMyReportsMessage(`Não foi possível alterar os avisos: ${error.message}`, 'error');
    } finally {
        toggle.disabled = false;
        renderPush();
    }
}

document.querySelectorAll('[data-push-toggle]').forEach((toggle) => {
    toggle.addEventListener('click', () => togglePush(toggle));
});
