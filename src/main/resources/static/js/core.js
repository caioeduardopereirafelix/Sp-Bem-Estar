export const API = '/api/v1';

export const CITY = 'São Paulo';

export const STATUS = {
    OPEN: {label: 'Aberto', track: 'Recebido', color: '#d03b3b', shape: 'circle'},
    CONFIRMED: {label: 'Confirmado', track: 'Confirmado', color: '#ec835a', shape: 'square'},
    IN_PROGRESS: {label: 'Em andamento', track: 'Em andamento', color: '#fab219', shape: 'diamond'},
    RESOLVED: {label: 'Resolvido', track: 'Resolvido', color: '#0ca30c', shape: 'triangle'},
};

export const TRACK = ['OPEN', 'CONFIRMED', 'IN_PROGRESS', 'RESOLVED'];

export const TRANSITIONS = {
    OPEN: ['CONFIRMED', 'IN_PROGRESS', 'RESOLVED'],
    CONFIRMED: ['IN_PROGRESS', 'RESOLVED'],
    IN_PROGRESS: ['RESOLVED'],
    RESOLVED: [],
};

const ICON_PATHS = {
    POTHOLE: '<path d="M3 18h18M6 6l-3 12M18 6l3 12"/><ellipse cx="12" cy="13.5" rx="4.5" ry="2"/><path d="M12 6v2"/>',
    TRASH: '<path d="M5 8h14l-1.2 12H6.2z"/><path d="M9 8V5h6v3M3.5 8h17"/><path d="M10 12v5M14 12v5"/>',
    STREETLIGHT: '<path d="M8 21h6M11 21V7a3 3 0 0 1 3-3h1"/><path d="M15 4h4l-1 3h-2z"/><path d="M17 9.5v1.5M20 9l1 1M14 9l-1 1"/>',
    TREE: '<path d="M12 21v-6"/><path d="M12 15c-4 0-6-2.4-6-5.2C6 6.6 8.7 3 12 3s6 3.6 6 6.8C18 12.6 16 15 12 15z"/><path d="M9 21h6"/>',
    FLOODING: '<path d="M3 15c1.5 0 1.5-1.5 3-1.5S7.5 15 9 15s1.5-1.5 3-1.5 1.5 1.5 3 1.5 1.5-1.5 3-1.5 1.5 1.5 3 1.5"/><path d="M3 19c1.5 0 1.5-1.5 3-1.5S7.5 19 9 19s1.5-1.5 3-1.5 1.5 1.5 3 1.5 1.5-1.5 3-1.5 1.5 1.5 3 1.5"/><path d="M12 3c2 2.6 3 4.4 3 6a3 3 0 0 1-6 0c0-1.6 1-3.4 3-6z"/>',
    SIDEWALK: '<path d="M3 20l3-14h12l3 14z"/><path d="M5 13h14M12 6v14M8.5 6l-1.5 14M15.5 6l1.5 14"/>',
    CLOGGED_DRAIN: '<rect x="4" y="6" width="16" height="12" rx="2"/><path d="M8 9v6M12 9v6M16 9v6"/>',
    SEWAGE: '<path d="M3 8h8a3 3 0 0 1 3 3v1"/><path d="M3 5v6"/><path d="M14 15c1.5 2 2.2 3.2 2.2 4a2.2 2.2 0 0 1-4.4 0c0-.8.7-2 2.2-4z"/>',
    BUS_STOP: '<path d="M7 21V3"/><rect x="7" y="3" width="10" height="7" rx="1.5"/><path d="M10 6.5h4M4 21h6"/>',
    OVERGROWN: '<path d="M3 21h18"/><path d="M6 21c0-5-1-8-3-10M10 21c0-6 0-10-2-14M14 21c0-6 1-10 3-13M18 21c0-4 1-7 3-9"/>',
};

const DEFAULT_ICON = '<circle cx="12" cy="12" r="8"/><path d="M12 8v5M12 16h.01"/>';

const SHORT_NAMES = {
    POTHOLE: 'Buraco na rua', TRASH: 'Lixo ou entulho', STREETLIGHT: 'Poste apagado', TREE: 'Árvore',
    FLOODING: 'Alagamento', SIDEWALK: 'Calçada', CLOGGED_DRAIN: 'Bueiro entupido', SEWAGE: 'Esgoto',
    BUS_STOP: 'Ponto de ônibus', OVERGROWN: 'Mato alto',
};

export function categoryIcon(code) {
    return `<svg viewBox="0 0 24 24" aria-hidden="true">${ICON_PATHS[code] ?? DEFAULT_ICON}</svg>`;
}

export function shortName(code, fallback) {
    return SHORT_NAMES[code] ?? fallback ?? code;
}

export const RATE_COLORS = ['#dfe3ff', '#aab3f5', '#6f7ee6', '#3445d1', '#1f2a8a'];

export const numberFormat = new Intl.NumberFormat('pt-BR');

export const rateFormat = new Intl.NumberFormat('pt-BR', {minimumFractionDigits: 2, maximumFractionDigits: 2});

export const dateFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'short'});

const relativeFormat = new Intl.RelativeTimeFormat('pt-BR', {numeric: 'auto'});

export const isDesktop = () => window.matchMedia('(min-width: 900px)').matches;

export const canHover = () => window.matchMedia('(hover: hover)').matches;

export function timeAgo(value) {
    const seconds = (new Date(value).getTime() - Date.now()) / 1000;
    const steps = [[60, 'second'], [60, 'minute'], [24, 'hour'], [30, 'day'], [12, 'month'], [Infinity, 'year']];
    let amount = seconds;
    for (const [size, unit] of steps) {
        if (Math.abs(amount) < size) {
            return unit === 'second' ? 'agora há pouco' : relativeFormat.format(Math.round(amount), unit);
        }
        amount /= size;
    }
    return dateFormat.format(new Date(value));
}

export const state = {categories: [], rateBreaks: [], pickedLocation: null, pickMarker: null, loadTimer: null,
    session: loadSession(), reporter: null, markers: new Map(), userLocation: null,
    filters: {category: '', status: ''}, report: {step: 'where', category: null}};

function loadSession() {
    try {
        const session = JSON.parse(sessionStorage.getItem('operator') ?? 'null');
        return session && new Date(session.expiresAt) > new Date() ? session : null;
    } catch {
        return null;
    }
}

const sessionListeners = [];

export function onSessionChange(listener) {
    sessionListeners.push(listener);
}

export function saveSession(session) {
    state.session = session;
    try {
        if (session) {
            sessionStorage.setItem('operator', JSON.stringify(session));
        } else {
            sessionStorage.removeItem('operator');
        }
    } catch {
    }
    sessionListeners.forEach((listener) => listener());
}

export const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g,
    (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

export async function api(path, options = {}) {
    const headers = {...options.headers};
    if (state.session) {
        headers.Authorization = `Bearer ${state.session.accessToken}`;
    }
    const response = await fetch(API + path, {...options, headers});
    const body = response.headers.get('content-type')?.includes('json') ? await response.json() : null;
    if (response.status === 401 && state.session) {
        saveSession(null);
        throw new Error('Sua sessão terminou (venceu ou foi encerrada). Toque em Entrar, no alto do app, para entrar de novo.');
    }
    if (!response.ok) {
        throw new Error(body?.detail || body?.title || `Erro ${response.status}`);
    }
    return body;
}
