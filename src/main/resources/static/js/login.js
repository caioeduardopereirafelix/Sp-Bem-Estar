import {api, saveSession, state} from './core.js';
import {panel, showView} from './sheet.js';
import {citizenSignedIn, loadReporter, reporterFetch, reporterToken, saveReporter} from './reporter.js';
import {showToast} from './ui.js';
import {loadMyReports, setMyReportsMessage} from './mine.js';
import {push, saveSubscription} from './push.js';
import {showSubview} from './staff.js';

const login = {email: null, intent: null, back: 'more'};

const LOGIN_INTENTS = {
    report: () => document.getElementById('report-begin').click(),
    'report-form': () => (state.pickedLocation ? showView('report') : document.getElementById('report-begin').click()),
    mine: () => showView('mine'),
};

const PENDING_LOGIN_KEY = 'login-pending';

function savePendingLogin(pending) {
    try {
        if (pending) {
            localStorage.setItem(PENDING_LOGIN_KEY, JSON.stringify(pending));
        } else {
            localStorage.removeItem(PENDING_LOGIN_KEY);
        }
    } catch {
    }
}

function loadPendingLogin() {
    try {
        const pending = JSON.parse(localStorage.getItem(PENDING_LOGIN_KEY) ?? 'null');
        return pending && new Date(pending.expiresAt) > new Date() ? pending : null;
    } catch {
        return null;
    }
}

const loginEmailForm = document.getElementById('login-email-form');

const loginCodeForm = document.getElementById('login-code-form');

const loginMessage = document.getElementById('login-message');

function setLoginMessage(text, kind) {
    loginMessage.textContent = text;
    loginMessage.className = `message ${kind ?? ''}`;
}

export function openLogin(reason, intent) {
    login.intent = intent ?? null;
    login.reason = reason ?? null;
    login.back = panel.dataset.current && panel.dataset.current !== 'login' ? panel.dataset.current : 'more';
    document.getElementById('login-reason').textContent = reason
        ?? 'Sem senha: mandamos um código de 6 números para o seu e-mail.';
    showLoginStep('email');
    setLoginMessage('');
    showView('login');
    loginEmailForm.elements.email.focus();
}

function showLoginStep(step) {
    loginEmailForm.hidden = step !== 'email';
    loginCodeForm.hidden = step !== 'code';
    if (step === 'code') {
        loginCodeForm.reset();
        loginCodeForm.elements.code.focus();
    }
}

async function requestLoginCode(email) {
    const sent = await api('/auth/code', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({email}),
    });
    login.email = email;
    savePendingLogin({email, intent: login.intent, reason: login.reason, expiresAt: sent.expiresAt});
    showCodeStep(email, sent.expiresAt);
}

function showCodeStep(email, expiresAt) {
    const minutes = Math.round((new Date(expiresAt).getTime() - Date.now()) / 60_000);
    document.getElementById('login-code-hint').textContent = `Mandamos um código para ${email}. Ele vale por ${
        Math.max(1, minutes)} minutos. Não chegou? Veja a caixa de spam.`;
    showLoginStep('code');
}

export function resumePendingLogin() {
    const pending = loadPendingLogin();
    if (!pending || state.session || citizenSignedIn()) {
        savePendingLogin(null);
        return;
    }
    openLogin(pending.reason, pending.intent);
    login.email = pending.email;
    loginEmailForm.elements.email.value = pending.email;
    showCodeStep(pending.email, pending.expiresAt);
}

loginEmailForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = loginEmailForm.querySelector('[type="submit"]');
    submit.disabled = true;
    setLoginMessage('');
    try {
        await requestLoginCode(loginEmailForm.elements.email.value.trim());
    } catch (error) {
        setLoginMessage(error.message, 'error');
    } finally {
        submit.disabled = false;
    }
});

document.getElementById('login-resend').addEventListener('click', async (event) => {
    const button = event.currentTarget;
    button.disabled = true;
    try {
        await requestLoginCode(login.email);
        setLoginMessage('Mandamos um código novo. O anterior não vale mais.', 'success');
    } catch (error) {
        setLoginMessage(error.message, 'error');
    } finally {
        button.disabled = false;
    }
});

document.getElementById('login-change-email').addEventListener('click', () => {
    savePendingLogin(null);
    setLoginMessage('');
    showLoginStep('email');
    loginEmailForm.elements.email.focus();
});

document.getElementById('login-cancel').addEventListener('click', () => {
    savePendingLogin(null);
    showView(login.back);
});

loginCodeForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = loginCodeForm.querySelector('[type="submit"]');
    submit.disabled = true;
    setLoginMessage('');
    try {
        const previous = loadReporter()?.refreshToken && !citizenSignedIn()
            ? await reporterToken().catch(() => null) : null;
        const result = await api('/auth/verify', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({email: login.email, code: loginCodeForm.elements.code.value.trim(),
                reporterToken: previous}),
        });
        loginCodeForm.reset();
        await signedIn(result);
    } catch (error) {
        setLoginMessage(error.message, 'error');
    } finally {
        submit.disabled = false;
    }
});

export async function signedIn(result) {
    const then = LOGIN_INTENTS[login.intent];
    login.intent = null;
    savePendingLogin(null);
    if (result.role === 'CEO') {
        try {
            sessionStorage.setItem('ceo', JSON.stringify(result));
        } catch {
        }
        location.assign('/ceo.html');
        return;
    }
    if (result.role !== 'CITIZEN') {
        saveSession({accessToken: result.accessToken, tokenType: result.tokenType, expiresAt: result.expiresAt,
            name: result.name, role: result.role});
        showView('operator');
        showSubview('queue');
        return;
    }
    saveReporter({accessToken: result.accessToken, tokenType: result.tokenType, expiresAt: result.expiresAt,
        refreshToken: result.refreshToken, refreshTokenExpiresAt: result.refreshTokenExpiresAt, email: result.email});
    renderAccount();
    if (push.subscription) {
        saveSubscription(push.subscription).catch(() => {});
    }
    loadMyReports().catch((error) => setMyReportsMessage(error.message, 'error'));
    if (then) {
        then();
    } else {
        showView('explore');
        showToast(`Você entrou como ${result.email}.`);
    }
}

export function renderAccount() {
    const reporter = loadReporter();
    const status = document.getElementById('account-status');
    if (state.session) {
        status.textContent = `Conectado como ${state.session.name}, da equipe da prefeitura. As filas ficam na aba Prefeitura.`;
    } else if (reporter?.email) {
        status.textContent = `Conectado como ${reporter.email}. Seus relatos ficam em "Meus relatos", em qualquer aparelho.`;
    } else {
        status.textContent = 'Entre com o seu e-mail para relatar e acompanhar os seus relatos. Sem senha: chega um código.';
    }
    const signed = Boolean(state.session || reporter?.email);
    document.getElementById('menu-button').setAttribute('aria-label', state.session
        ? 'Abrir ou recolher a área da prefeitura' : 'Menu: sua conta, relatos e privacidade');
    document.getElementById('account-login').hidden = signed;
    document.getElementById('account-logout').hidden = !signed;

    const who = state.session?.name ?? reporter?.email;
    const chip = document.getElementById('account-chip');
    chip.classList.toggle('signed-in', signed);
    document.getElementById('account-chip-text').textContent = signed ? who.trim().charAt(0).toUpperCase() : 'Entrar';
    chip.setAttribute('aria-label', signed ? `Sua conta: ${who}` : 'Entrar com o e-mail');
    chip.title = signed ? `Conectado como ${who}` : '';
}

document.getElementById('account-login').addEventListener('click', () => openLogin());

document.getElementById('account-chip').addEventListener('click', () => {
    if (state.session) {
        showView('operator');
    } else if (citizenSignedIn()) {
        showView('more');
    } else if (panel.dataset.current !== 'login') {
        openLogin();
    }
});

document.getElementById('account-logout').addEventListener('click', async () => {
    if (state.session) {
        saveSession(null);
        return;
    }
    if (push.subscription) {
        try {
            await reporterFetch('/reporters/me/push-subscription', {
                method: 'DELETE',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(push.subscription.toJSON()),
            });
            await push.subscription.unsubscribe();
            push.subscription = null;
        } catch {
        }
    }
    saveReporter(null);
    renderAccount();
    loadMyReports().catch(() => {});
    showToast('Você saiu da sua conta neste aparelho.');
});

document.getElementById('logout').addEventListener('click', () => saveSession(null));
