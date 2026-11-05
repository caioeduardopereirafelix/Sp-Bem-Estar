import {API, state} from './core.js';
import {renderAccount} from './login.js';

const REPORTER_KEY = 'reporter';

let reporterRequest = null;

export function loadReporter() {
    try {
        const stored = localStorage.getItem(REPORTER_KEY);
        if (stored) {
            return JSON.parse(stored);
        }
    } catch {
    }
    return state.reporter;
}

export function saveReporter(tokens) {
    state.reporter = tokens;
    try {
        if (tokens) {
            localStorage.setItem(REPORTER_KEY, JSON.stringify(tokens));
        } else {
            localStorage.removeItem(REPORTER_KEY);
        }
    } catch {
    }
}

export const citizenSignedIn = () => Boolean(loadReporter()?.email);

function loginRequired(message = 'Entre com o seu e-mail para continuar.') {
    return Object.assign(new Error(message), {loginRequired: true});
}

async function postReporter(path, body) {
    const response = await fetch(API + path, {
        method: 'POST',
        headers: body ? {'Content-Type': 'application/json'} : {},
        body: body ? JSON.stringify(body) : undefined,
    });
    const payload = response.headers.get('content-type')?.includes('json') ? await response.json() : null;
    return {response, payload};
}

async function obtainReporterToken(forceRenew) {
    const current = loadReporter();
    if (!forceRenew && current && new Date(current.expiresAt).getTime() - 30_000 > Date.now()) {
        return current.accessToken;
    }
    if (current?.refreshToken && new Date(current.refreshTokenExpiresAt) > new Date()) {
        const {response, payload} = await postReporter('/reporters/refresh', {refreshToken: current.refreshToken});
        if (response.ok) {
            saveReporter({...payload, email: current.email});
            return payload.accessToken;
        }
        if (response.status === 403) {
            throw new Error(payload?.detail ?? 'Este autor foi bloqueado pela moderação.');
        }
    }
    if (current?.email) {
        saveReporter(null);
        renderAccount();
    }
    throw loginRequired('Sua sessão terminou. Entre de novo com o seu e-mail.');
}

export function reporterToken(forceRenew = false) {
    reporterRequest ??= (navigator.locks
        ? navigator.locks.request('spbemestar-reporter', () => obtainReporterToken(forceRenew))
        : obtainReporterToken(forceRenew)
    ).finally(() => {
        reporterRequest = null;
    });
    return reporterRequest;
}

export async function reporterFetch(path, options = {}) {
    for (const forceRenew of [false, true]) {
        const token = await reporterToken(forceRenew);
        const response = await fetch(API + path, {...options, headers: {...options.headers, Authorization: `Bearer ${token}`}});
        const body = response.headers.get('content-type')?.includes('json') ? await response.json() : null;
        if (response.status === 401 && !forceRenew) {
            continue;
        }
        if (body?.loginRequired) {
            throw loginRequired(body.detail);
        }
        if (!response.ok) {
            throw Object.assign(new Error(body?.detail || body?.title || `Erro ${response.status}`),
                {status: response.status, problem: body});
        }
        return body;
    }
}

export function sendReport(data) {
    return reporterFetch('/reports', {method: 'POST', body: data});
}
