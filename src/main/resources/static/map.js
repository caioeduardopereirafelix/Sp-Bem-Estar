const API = '/api/v1';
const CITY = 'São Paulo';

// Status é um estado: paleta de status + formato do marcador, para não depender só da cor
const STATUS = {
    OPEN: {label: 'Aberto', track: 'Recebido', color: '#d03b3b', shape: 'circle'},
    CONFIRMED: {label: 'Confirmado', track: 'Confirmado', color: '#ec835a', shape: 'square'},
    IN_PROGRESS: {label: 'Em andamento', track: 'Em andamento', color: '#fab219', shape: 'diamond'},
    RESOLVED: {label: 'Resolvido', track: 'Resolvido', color: '#0ca30c', shape: 'triangle'},
};
const TRACK = ['OPEN', 'CONFIRMED', 'IN_PROGRESS', 'RESOLVED'];

const TRANSITIONS = {
    OPEN: ['CONFIRMED', 'IN_PROGRESS', 'RESOLVED'],
    CONFIRMED: ['IN_PROGRESS', 'RESOLVED'],
    IN_PROGRESS: ['RESOLVED'],
    RESOLVED: [],
};

// Nome curto e ícone de cada tipo de problema; o nome completo vem da API
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

function categoryIcon(code) {
    return `<svg viewBox="0 0 24 24" aria-hidden="true">${ICON_PATHS[code] ?? DEFAULT_ICON}</svg>`;
}

function shortName(code, fallback) {
    return SHORT_NAMES[code] ?? fallback ?? code;
}

// Escala sequencial de um tom (azul), do claro ao escuro
const RATE_COLORS = ['#dfe3ff', '#aab3f5', '#6f7ee6', '#3445d1', '#1f2a8a'];

const numberFormat = new Intl.NumberFormat('pt-BR');
const rateFormat = new Intl.NumberFormat('pt-BR', {minimumFractionDigits: 2, maximumFractionDigits: 2});
const dateFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'short'});
const relativeFormat = new Intl.RelativeTimeFormat('pt-BR', {numeric: 'auto'});
const isDesktop = () => window.matchMedia('(min-width: 900px)').matches;
// tela de toque: as etiquetas de passar o mouse (tooltips) grudam no toque e cobrem o mapa
const canHover = () => window.matchMedia('(hover: hover)').matches;

// "há 3 horas", "ontem": mais fácil de ler que uma data
function timeAgo(value) {
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

const state = {categories: [], rateBreaks: [], pickedLocation: null, pickMarker: null, loadTimer: null,
    session: loadSession(), reporter: null, markers: new Map(), userLocation: null,
    filters: {category: '', status: ''}, report: {step: 'where', category: null}};

// Sessão do operador: o token fica só nesta aba e vence sozinho (expiresAt)
function loadSession() {
    try {
        const session = JSON.parse(sessionStorage.getItem('operator') ?? 'null');
        return session && new Date(session.expiresAt) > new Date() ? session : null;
    } catch {
        return null;
    }
}

function saveSession(session) {
    state.session = session;
    try {
        if (session) {
            sessionStorage.setItem('operator', JSON.stringify(session));
        } else {
            sessionStorage.removeItem('operator');
        }
    } catch {
        // sem storage (aba anônima, bloqueio): a sessão vale até recarregar a página
    }
    renderSession();
}

const map = L.map('map', {zoomControl: false}).setView([-23.55, -46.63], 12);
L.control.zoom({position: 'topright'}).addTo(map);
// no celular a gaveta cobre o pé do mapa: os créditos sobem para o canto de cima
map.attributionControl.setPosition(isDesktop() ? 'bottomright' : 'topright');

// Mapa vetorial escuro (estilo Fiord do OpenFreeMap, sem chave nem cadastro), com as cores puxadas para o
// azul-marinho dos apps de navegação. Sem WebGL, ou se o estilo não carregar, ficam os tiles do OpenStreetMap
// escurecidos por filtro
const BASE_STYLE_URL = 'https://tiles.openfreemap.org/styles/fiord';
const BASE_COLORS = {
    background: {'background-color': '#222a38'},
    water: {'fill-color': '#1d3b6e'},
    building: {'fill-color': 'hsla(222,30%,30%,0.55)'},
    park: {'fill-color': '#22392f'},
    landcover_wood: {'fill-color': 'hsla(150,25%,20%,0.5)'},
    highway_minor: {'line-color': '#3f4d66'},
    highway_major_casing: {'line-color': '#52627f'},
    highway_major_inner: {'line-color': '#46556f'},
    highway_motorway_casing: {'line-color': '#5c6d8c'},
    highway_name_other: {'text-color': '#b7c3d9', 'text-halo-color': '#222a38'},
    highway_path: {'line-opacity': 0.35}, // calçadas e trilhas tracejadas: discretas, para as ruas se destacarem
};

function supportsWebGL() {
    try {
        return Boolean(document.createElement('canvas').getContext('webgl2'));
    } catch {
        return false;
    }
}

// os créditos (OpenFreeMap, OpenMapTiles, OpenStreetMap) vêm do próprio estilo, na barra do Leaflet
async function addBaseMap() {
    if (window.maplibregl && L.maplibreGL && supportsWebGL()) {
        try {
            const response = await fetch(BASE_STYLE_URL);
            if (!response.ok) {
                throw new Error(`estilo do mapa: ${response.status}`);
            }
            const style = await response.json();
            for (const layer of style.layers) {
                Object.assign(layer.paint ??= {}, BASE_COLORS[layer.id]);
            }
            L.maplibreGL({style}).addTo(map);
            return;
        } catch {
            // segue para os tiles do OpenStreetMap
        }
    }
    map.getContainer().classList.add('raster-dark');
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(map);
}

addBaseMap();

map.createPane('districts').style.zIndex = 350;
// os distritos coloridos ficam desligados por padrão: o mapa limpo deixa os problemas à vista
const districtLayer = L.geoJSON(null, {pane: 'districts', style: districtStyle, onEachFeature: bindDistrict});
const issueLayer = L.layerGroup().addTo(map);
const legend = L.control({position: 'topright'});
legend.onAdd = () => L.DomUtil.create('div', 'legend');

const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g,
    (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

async function api(path, options = {}) {
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

// --- Painel: abas, tamanho da gaveta no celular ---

const panel = document.getElementById('panel');
const VIEW_SIZES = {explore: 'peek', issue: 'half', report: 'half', sent: 'full', mine: 'full', more: 'full', operator: 'full',
    login: 'full'};

function setSheet(size) {
    panel.dataset.size = size;
    document.body.dataset.sheet = size;
}

function showView(name) {
    document.querySelectorAll('[data-view]').forEach((view) => {
        view.hidden = view.dataset.view !== name;
    });
    document.querySelectorAll('.tab').forEach((tab) => {
        const current = tab.dataset.goto === name
            || ((name === 'report' || name === 'issue') && tab.dataset.goto === 'explore')
            || (name === 'sent' && tab.dataset.goto === 'mine')
            || (name === 'login' && tab.dataset.goto === 'more');
        tab.toggleAttribute('aria-current', current);
        if (current) tab.setAttribute('aria-current', 'page');
    });
    panel.dataset.current = name;
    if (name !== 'issue') {
        selectMarker(null);
    }
    setSheet(VIEW_SIZES[name] ?? 'half');
    document.querySelector('.views').scrollTop = 0;
    if (name === 'mine') {
        loadMyReports().catch((error) => setMyReportsMessage(error.message, 'error'));
    }
}

document.addEventListener('click', (event) => {
    const target = event.target.closest('[data-goto]');
    if (!target) {
        return;
    }
    if (target.dataset.goto === 'report-start') {
        document.getElementById('report-begin').click(); // mesmo fluxo do botão principal, com o texto da permissão
    } else {
        if (state.report.active && target.dataset.goto !== 'report') {
            resetReport();
        }
        showView(target.dataset.goto);
    }
});

// A gaveta sobe e desce arrastando a alça, ou com um toque nela
const SHEET_SIZES = ['peek', 'half', 'full'];
const sheetHandle = document.getElementById('sheet-handle');
let sheetDrag = null;

sheetHandle.addEventListener('pointerdown', (event) => {
    sheetDrag = {y: event.clientY, moved: false};
    sheetHandle.setPointerCapture(event.pointerId);
});
sheetHandle.addEventListener('pointerup', (event) => {
    const dy = event.clientY - (sheetDrag?.y ?? event.clientY);
    if (Math.abs(dy) > 12) {
        const index = SHEET_SIZES.indexOf(panel.dataset.size) + (dy < 0 ? 1 : -1);
        setSheet(SHEET_SIZES[Math.min(Math.max(index, 0), SHEET_SIZES.length - 1)]);
        sheetDrag.moved = true;
    }
});
sheetHandle.addEventListener('click', () => {
    if (!sheetDrag?.moved) {
        setSheet(panel.dataset.size === 'full' ? 'peek' : 'full');
    }
    sheetDrag = null;
});

// Os botões sobre o mapa acompanham a altura da gaveta, como no app de navegação
new ResizeObserver(() => {
    document.documentElement.style.setProperty('--sheet-height', `${panel.offsetHeight}px`);
}).observe(panel);

document.getElementById('menu-button').addEventListener('click', () => {
    if (state.report.active) {
        resetReport();
    }
    showView(panel.dataset.current === 'more' && panel.dataset.size === 'full' ? 'explore' : 'more');
});

// O cidadão depois do login por código: um JWT de 15 min para enviar relatos, renovado com o refresh token.
// O par fica neste navegador, com o e-mail, para a pessoa continuar conectada nas próximas visitas. Um par sem
// e-mail é a identidade anônima de antes do login: ela não envia mais relatos, mas no primeiro login os relatos
// feitos com ela passam para a conta.
const REPORTER_KEY = 'reporter';
let reporterRequest = null;

function loadReporter() {
    try {
        const stored = localStorage.getItem(REPORTER_KEY);
        if (stored) {
            return JSON.parse(stored);
        }
    } catch {
        // sem storage: usa o par guardado em memória
    }
    return state.reporter;
}

function saveReporter(tokens) {
    state.reporter = tokens;
    try {
        if (tokens) {
            localStorage.setItem(REPORTER_KEY, JSON.stringify(tokens));
        } else {
            localStorage.removeItem(REPORTER_KEY);
        }
    } catch {
        // sem storage (aba anônima, bloqueio): a identidade vale até recarregar a página
    }
}

const citizenSignedIn = () => Boolean(loadReporter()?.email);

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
    const current = loadReporter(); // relê sempre: outra aba pode ter renovado o par
    if (!forceRenew && current && new Date(current.expiresAt).getTime() - 30_000 > Date.now()) {
        return current.accessToken;
    }
    if (current?.refreshToken && new Date(current.refreshTokenExpiresAt) > new Date()) {
        const {response, payload} = await postReporter('/reporters/refresh', {refreshToken: current.refreshToken});
        if (response.ok) {
            saveReporter({...payload, email: current.email}); // o e-mail não vem na renovação
            return payload.accessToken;
        }
        if (response.status === 403) {
            throw new Error(payload?.detail ?? 'Este autor foi bloqueado pela moderação.');
        }
        // 401: vencido ou revogado
    }
    if (current?.email) {
        saveReporter(null);
        renderAccount();
    }
    throw loginRequired('Sua sessão terminou. Entre de novo com o seu e-mail.');
}

// Uma renovação por vez, também entre abas: duas com o mesmo refresh token parecem uma cópia roubada,
// e a API revoga a identidade inteira
function reporterToken(forceRenew = false) {
    reporterRequest ??= (navigator.locks
        ? navigator.locks.request('spbemestar-reporter', () => obtainReporterToken(forceRenew))
        : obtainReporterToken(forceRenew)
    ).finally(() => {
        reporterRequest = null;
    });
    return reporterRequest;
}

// Requisição com o token do cidadão; se ele for recusado (vencido ou revogado), renova e tenta uma vez
async function reporterFetch(path, options = {}) {
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
            // o corpo do erro vai junto: a recusa da foto traz o tipo que o classificador sugere
            throw Object.assign(new Error(body?.detail || body?.title || `Erro ${response.status}`),
                {status: response.status, problem: body});
        }
        return body;
    }
}

function sendReport(data) {
    return reporterFetch('/reports', {method: 'POST', body: data});
}

// Localização do usuário em tempo real, como nos apps de navegação. A posição fica só neste aparelho: a API
// recebe a área do mapa à vista, para buscar os problemas, e o ponto de um relato enviado.
const LOW_ACCURACY_METERS = 50;
const CORRECTABLE_METERS = 100; // acima disso, a posição é a da rede, não a do GPS, e a pessoa pode corrigi-la
const FOLLOW_ZOOM = 17;
const MOVING_SPEED = 0.7; // m/s: abaixo disso o rumo do GPS é ruído, e vale a bússola
const NO_GEOLOCATION = 'Este navegador não informa a localização aqui (é preciso HTTPS). Marque o ponto no mapa.';
const GEOLOCATION_ERRORS = {
    1: 'Sem permissão para usar sua localização. Libere nas configurações do navegador ou marque o ponto no mapa.',
    2: 'Sua localização não está disponível agora. Marque o ponto no mapa.',
    3: 'A localização demorou demais. Tente de novo ou marque o ponto no mapa.',
};

const tracking = {watchId: null, position: null, marker: null, accuracy: null, following: false,
    gpsHeading: null, compass: null, compassOn: false, highAccuracy: true, asked: false, warned: false, frame: 0,
    manual: null, fixing: false, fixDismissed: false};
const locateButton = document.getElementById('locate-button');

const geolocationAvailable = () => window.isSecureContext && 'geolocation' in navigator;

function currentPosition() {
    return new Promise((resolve, reject) => {
        if (!geolocationAvailable()) {
            reject(new Error(NO_GEOLOCATION));
            return;
        }
        // o acompanhamento em tempo real já tem uma posição recente: não precisa esperar o GPS de novo
        if (tracking.position && Date.now() - tracking.position.timestamp < 30_000) {
            resolve(tracking.position);
            return;
        }
        navigator.geolocation.getCurrentPosition(resolve,
            (error) => reject(new Error(GEOLOCATION_ERRORS[error.code] ?? 'Não foi possível obter sua localização.')),
            {enableHighAccuracy: true, timeout: 15000, maximumAge: 60000});
    });
}

// Alta precisão é o GPS: no celular, a seta acompanha a rua; no computador, sem GPS, a resposta pode nunca vir.
// Por isso uma posição aproximada (Wi-Fi, rede) chega logo, o GPS refina por cima, e se ele esgotar o tempo o
// acompanhamento segue em precisão normal
const HIGH_ACCURACY = {enableHighAccuracy: true, maximumAge: 5000, timeout: 20000};
const NORMAL_ACCURACY = {enableHighAccuracy: false, maximumAge: 60_000, timeout: 30000};

function startTracking() {
    if (tracking.watchId !== null || !geolocationAvailable()) {
        return;
    }
    watchPosition(HIGH_ACCURACY);
    navigator.geolocation.getCurrentPosition((position) => {
        if (!tracking.position) {
            onPosition(position);
        }
    }, () => {
        // sem a aproximada, os avisos vêm do acompanhamento
    }, {enableHighAccuracy: false, maximumAge: 600_000, timeout: 15000});
}

function watchPosition(options) {
    tracking.highAccuracy = options.enableHighAccuracy;
    tracking.watchId = navigator.geolocation.watchPosition(onPosition, onPositionError, options);
}

function stopTracking() {
    if (tracking.watchId !== null) {
        navigator.geolocation.clearWatch(tracking.watchId);
        tracking.watchId = null;
    }
}

function onPosition(position) {
    const first = !tracking.position;
    tracking.position = position;
    const precise = position.coords.accuracy <= CORRECTABLE_METERS;
    if (precise && tracking.manual) {
        tracking.manual = null; // o aparelho achou você com precisão: ele vale mais que o ponto marcado à mão
        showToast('Sua localização ficou precisa: o ponto azul voltou a ser o do aparelho.');
    }
    const {heading, speed} = position.coords;
    // parado, o GPS não sabe para onde você está virado: o rumo dele só vale em movimento
    tracking.gpsHeading = Number.isFinite(heading) && speed > MOVING_SPEED ? heading : null;
    drawUserMarker();
    locateButton.classList.remove('unavailable');
    if (first) {
        toast.hidden = true; // o "Buscando sua localização…", ou um aviso de demora que a posição já respondeu
    }
    if (tracking.following) {
        followUser(first);
    }
    if (!precise && !tracking.manual) {
        offerLocationFix();
    }
}

function onPositionError(error) {
    if (error.code === 3 && tracking.highAccuracy) {
        stopTracking();
        watchPosition(NORMAL_ACCURACY); // o GPS não respondeu: tenta pela rede antes de avisar
        return;
    }
    if (error.code === 1) {
        stopTracking(); // negado: o navegador não volta a perguntar, e o botão tenta de novo se a pessoa liberar
        setFollow(false);
        locateButton.classList.add('unavailable');
    }
    // recusar o pedido já é a resposta da pessoa; as outras falhas (GPS ou localização do sistema desligados,
    // demora) ficariam mudas sem o aviso. Um aviso por tentativa: o watchPosition repete o erro a cada 20 s
    if (tracking.position || tracking.warned || (error.code === 1 && !tracking.asked)) {
        return;
    }
    tracking.warned = true;
    let text = GEOLOCATION_ERRORS[error.code] ?? 'Não foi possível obter sua localização.';
    if (error.code !== 1 && isDesktop()) {
        text += ' No computador, confira se a localização está ligada nas configurações do sistema '
            + '(no Windows: Privacidade e segurança › Localização). Sem Wi-Fi, o Windows pode não achar a '
            + 'posição; ali dá para definir um local padrão.';
    }
    showToast(text, 10000);
}

// O ponto marcado à mão, se a pessoa corrigiu a posição aproximada; senão, o do aparelho
function userLatLng() {
    if (tracking.manual) {
        return tracking.manual;
    }
    const {latitude, longitude} = tracking.position.coords;
    return L.latLng(latitude, longitude);
}

function userAccuracy() {
    return tracking.manual ? 0 : tracking.position.coords.accuracy;
}

// A seta azul, virada para onde você vai (ou para onde o celular aponta); sem rumo, vira um ponto
function drawUserMarker() {
    const latlng = userLatLng();
    const accuracy = userAccuracy();
    if (!tracking.marker) {
        tracking.accuracy = L.circle(latlng, {radius: accuracy, className: 'user-accuracy', interactive: false});
        tracking.marker = L.marker(latlng, {
            icon: L.divIcon({className: 'user-marker', iconSize: [64, 64], html: `<span class="user-halo"></span>
                <svg class="user-arrow" viewBox="0 0 40 40" aria-hidden="true"><path d="M20 5l11 28-11-6.5L9 33z"/></svg>
                <span class="user-dot"></span>`}),
            interactive: false, keyboard: false, zIndexOffset: 1000,
        });
        tracking.accuracy.addTo(map);
        tracking.marker.addTo(map);
    } else {
        tracking.marker.setLatLng(latlng);
        tracking.accuracy.setLatLng(latlng).setRadius(accuracy);
    }
    // com GPS bom o círculo só polui; ele aparece quando a posição é aproximada
    tracking.accuracy.getElement()?.classList.toggle('visible', accuracy > LOW_ACCURACY_METERS);
    scheduleArrowUpdate();
}

function scheduleArrowUpdate() {
    if (tracking.frame || !tracking.marker) {
        return;
    }
    tracking.frame = requestAnimationFrame(() => {
        tracking.frame = 0;
        const element = tracking.marker.getElement();
        const heading = tracking.gpsHeading ?? tracking.compass;
        element?.classList.toggle('no-heading', heading === null);
        element?.querySelector('.user-arrow')?.style.setProperty('rotate', `${heading ?? 0}deg`);
    });
}

// Bússola do aparelho: no Android vem sem pedir; no iPhone, só depois de um toque e de a pessoa permitir
function onOrientation(event) {
    let heading = null;
    if (typeof event.webkitCompassHeading === 'number') {
        heading = event.webkitCompassHeading;
    } else if (event.absolute && event.alpha !== null) {
        heading = 360 - event.alpha;
    }
    if (heading === null) {
        return;
    }
    tracking.compass = (heading + (screen.orientation?.angle ?? 0)) % 360;
    scheduleArrowUpdate();
}

async function enableCompass() {
    const Orientation = window.DeviceOrientationEvent;
    if (tracking.compassOn || !Orientation) {
        return;
    }
    if (typeof Orientation.requestPermission === 'function') {
        try {
            if (await Orientation.requestPermission() !== 'granted') {
                return;
            }
        } catch {
            return; // fora de um toque o iPhone recusa; tenta de novo no próximo toque no botão
        }
    }
    tracking.compassOn = true;
    window.addEventListener('ondeviceorientationabsolute' in window ? 'deviceorientationabsolute' : 'deviceorientation',
        onOrientation);
}

// O centro do mapa que deixa um ponto no meio da parte visível: no celular a gaveta cobre o pé da tela,
// no computador o painel cobre a esquerda
function centerShowing(latlng, zoom) {
    const offset = isDesktop() ? L.point(-panel.offsetWidth / 2 - 8, 0) : L.point(0, panel.offsetHeight / 2);
    return map.unproject(map.project(latlng, zoom).add(offset), zoom);
}

function followUser(jump = false) {
    // com a posição aproximada (rede, Wi-Fi), o zoom de rua esconderia o círculo de precisão
    const accuracy = userAccuracy();
    const zoom = !jump ? map.getZoom() : accuracy > LOW_ACCURACY_METERS
        ? Math.min(FOLLOW_ZOOM, map.getBoundsZoom(userLatLng().toBounds(accuracy * 2.5)))
        : Math.max(map.getZoom(), FOLLOW_ZOOM);
    const target = centerShowing(userLatLng(), zoom);
    if (jump) {
        map.setView(target, zoom, {animate: false});
    } else if (map.latLngToContainerPoint(target).distanceTo(map.latLngToContainerPoint(map.getCenter())) > 8) {
        map.panTo(target, {animate: true, duration: 0.6});
    }
}

function setFollow(following) {
    tracking.following = following;
    locateButton.setAttribute('aria-pressed', String(following));
}

// Mostra a posição para escolher o ponto do relato; com precisão baixa, o círculo inteiro fica à vista
function showUserLocation(position) {
    onPosition(position);
    const latlng = userLatLng();
    const radius = Math.max(userAccuracy(), 40);
    map.fitBounds(latlng.toBounds(radius * 2), {maxZoom: 18, animate: false,
        paddingTopLeft: mapPadding(), paddingBottomRight: isDesktop() ? [0, 0] : [0, panel.offsetHeight]});
    return latlng;
}

// No computador o painel cobre a esquerda do mapa: o que é centralizado deve ficar à direita dele
function mapPadding() {
    return isDesktop() ? [440, 0] : [0, 0];
}

locateButton.addEventListener('click', async () => {
    enableCompass();
    if (!geolocationAvailable()) {
        showToast(NO_GEOLOCATION);
        return;
    }
    // bloqueado, o navegador nem pergunta de novo: o aviso diz onde liberar
    if (await geolocationPermission() === 'denied') {
        showToast('O acesso à localização está bloqueado para este site. Para liberar, toque no cadeado ao lado '
            + 'do endereço e permita a localização.');
        return;
    }
    tracking.asked = true;
    tracking.warned = false;
    setFollow(true);
    if (tracking.position) {
        followUser(true);
        if (!tracking.manual && tracking.position.coords.accuracy > CORRECTABLE_METERS) {
            tracking.fixDismissed = false; // pediu a localização de novo: o convite para corrigir volta
            offerLocationFix();
        }
    } else {
        showToast('Buscando sua localização…');
    }
    startTracking();
});

// --- Corrigir a posição aproximada com um toque no mapa ---

const locateFix = document.getElementById('locate-fix');

function offerLocationFix() {
    if (tracking.fixDismissed || tracking.fixing || state.report.active || !locateFix.hidden) {
        return;
    }
    const meters = numberFormat.format(Math.round(tracking.position.coords.accuracy / 10) * 10);
    document.getElementById('locate-fix-text').textContent = `Sua localização está aproximada (cerca de ${meters} m). `
        + 'Toque em "Marcar onde estou" e depois no mapa, no lugar onde você está.';
    locateFix.hidden = false;
}

function stopLocationFix() {
    tracking.fixing = false;
    document.body.classList.remove('fixing-location');
    locateFix.hidden = true;
}

document.getElementById('locate-fix-start').addEventListener('click', () => {
    tracking.fixing = true;
    locateFix.hidden = true;
    document.body.classList.add('fixing-location');
    if (!isDesktop()) {
        setSheet('peek');
    }
    showToast('Toque no mapa no lugar onde você está.', 20000);
});

document.getElementById('locate-fix-dismiss').addEventListener('click', () => {
    tracking.fixDismissed = true;
    stopLocationFix();
});

function setManualLocation(latlng) {
    stopLocationFix();
    tracking.manual = latlng;
    drawUserMarker();
    setFollow(true);
    followUser(true);
    showToast('Pronto: o ponto azul está onde você marcou. Ele volta a ser o do aparelho quando a localização '
        + 'ficar precisa.');
}

// Arrastar o mapa é olhar outro lugar: o mapa para de seguir você até o toque no botão
map.on('dragstart', () => setFollow(false));
map.on('zoomend', () => {
    if (tracking.following && tracking.position) {
        followUser();
    }
});

const toast = document.getElementById('toast');
let toastTimer = null;

function showToast(text, duration = 6000) {
    toast.textContent = text;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toast.hidden = true;
    }, duration);
}

function shapeSvg(status, size = 18) {
    const {color, shape} = STATUS[status];
    const c = size / 2;
    const r = size / 2 - 3;
    const shapes = {
        circle: `<circle cx="${c}" cy="${c}" r="${r}"/>`,
        square: `<rect x="${c - r}" y="${c - r}" width="${2 * r}" height="${2 * r}" rx="2"/>`,
        diamond: `<polygon points="${c},${c - r - 1} ${c + r + 1},${c} ${c},${c + r + 1} ${c - r - 1},${c}"/>`,
        triangle: `<polygon points="${c},${c - r - 1} ${c + r + 1},${c + r} ${c - r - 1},${c + r}"/>`,
    };
    const mark = shapes[shape];
    // Contorno escuro + anel branco: o marcador se destaca em qualquer cor de fundo
    return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" aria-hidden="true">
        <g fill="none" stroke="rgba(11,11,11,0.55)" stroke-width="4" stroke-linejoin="round">${mark}</g>
        <g fill="${color}" stroke="#ffffff" stroke-width="2" stroke-linejoin="round">${mark}</g></svg>`;
}

// "3 pessoas relataram": a prioridade conta pessoas, e quem reporta de novo não soma
function peopleLabel(reporters) {
    return reporters === 1 ? '1 pessoa relatou' : `${numberFormat.format(reporters)} pessoas relataram`;
}

function reportsLabel(reports, reporters) {
    const text = `${numberFormat.format(reports)} relato${reports === 1 ? '' : 's'}`;
    return reporters === reports ? text
        : `${text} de ${numberFormat.format(reporters)} pessoa${reporters === 1 ? '' : 's'}`;
}

function statusLabel(status) {
    return `<span class="status-label">${shapeSvg(status, 14)}${STATUS[status].label}</span>`;
}

// A trilha Recebido → Confirmado → Em andamento → Resolvido, como o rastreio de uma encomenda
function trackLine(status) {
    const position = TRACK.indexOf(status);
    return `<ol class="track-line" aria-label="Andamento: ${STATUS[status]?.label ?? status}">${TRACK.map((step, i) => {
        const cls = i < position ? 'reached' : i === position ? 'reached current' : '';
        return `<li class="${cls}" data-status="${step}" ${i === position ? 'aria-current="step"' : ''}>${STATUS[step].track}</li>`;
    }).join('')}</ol>`;
}

document.getElementById('status-key').innerHTML = TRACK
    .map((s) => `<li>${shapeSvg(s, 16)}${STATUS[s].label}</li>`).join('');

function rateColor(rate) {
    if (rate == null) {
        return 'transparent';
    }
    const index = state.rateBreaks.findIndex((limit) => rate <= limit);
    return RATE_COLORS[index === -1 ? RATE_COLORS.length - 1 : index];
}

function districtStyle(feature) {
    return {
        color: '#ffffff',
        weight: 1,
        fillColor: rateColor(feature.properties.openIssuesPer10k),
        fillOpacity: 0.55,
    };
}

function bindDistrict(feature, layer) {
    const p = feature.properties;
    if (!canHover()) {
        // no toque, o resumo do distrito vem num aviso, que some sozinho
        layer.on('click', () => showToast(`${p.name}: ${numberFormat.format(p.openIssues)} problemas em aberto, `
            + `${rateFormat.format(p.openIssuesPer10k ?? 0)} a cada 10 mil moradores.`));
        return;
    }
    layer.bindTooltip(`<strong>${escapeHtml(p.name)}</strong><br>
        ${numberFormat.format(p.population ?? 0)} moradores<br>
        ${numberFormat.format(p.openIssues)} problemas em aberto<br>
        ${rateFormat.format(p.openIssuesPer10k ?? 0)} a cada 10 mil moradores`, {sticky: true});
    layer.on({
        mouseover: () => layer.setStyle({weight: 3, color: '#1b2130'}),
        mouseout: () => districtLayer.resetStyle(layer),
    });
}

// Faixas: zero, e o restante dividido em quatro intervalos iguais até o máximo
function computeRateBreaks(features) {
    const max = Math.max(0, ...features.map((f) => Number(f.properties.openIssuesPer10k ?? 0)));
    if (max === 0) {
        return [0];
    }
    const step = max / 4;
    return [0, step, 2 * step, 3 * step, max];
}

function renderLegend() {
    const breaks = state.rateBreaks;
    const rows = breaks.length === 1
        ? `<div class="legend-row"><span class="swatch" data-color="0"></span>Nenhum problema em aberto</div>`
        : breaks.map((limit, i) => {
            const text = i === 0 ? 'Nenhum' : `até ${rateFormat.format(limit)}`;
            return `<div class="legend-row"><span class="swatch" data-color="${i}"></span>${text}</div>`;
        }).join('');
    const container = legend.getContainer();
    container.innerHTML = `<h3>Em aberto a cada 10 mil moradores</h3>${rows}`;
    // a cor vai pelo DOM, não por atributo style: a CSP da página bloqueia estilos inline
    container.querySelectorAll('.swatch[data-color]').forEach((swatch) => {
        swatch.style.background = RATE_COLORS[swatch.dataset.color];
    });
}

async function loadDistricts() {
    const collection = await api(`/neighborhoods?city=${encodeURIComponent(CITY)}`);
    state.rateBreaks = computeRateBreaks(collection.features);
    districtLayer.clearLayers().addData(collection);
    if (map.hasLayer(districtLayer)) {
        renderLegend();
    }
    renderRanking(collection.features.map((f) => f.properties));
}

function renderRanking(stats) {
    const rows = stats.filter((s) => s.openIssues > 0).slice(0, 10);
    document.getElementById('ranking-body').innerHTML = rows.length === 0
        ? '<tr><td colspan="3">Nenhum problema em aberto ainda.</td></tr>'
        : rows.map((s) => `<tr><td>${escapeHtml(s.name)}</td><td>${numberFormat.format(s.openIssues)}</td>
            <td>${rateFormat.format(s.openIssuesPer10k ?? 0)}</td></tr>`).join('');
}

document.getElementById('districts-toggle').addEventListener('change', (event) => {
    if (event.target.checked) {
        districtLayer.addTo(map);
        legend.addTo(map);
        renderLegend();
    } else {
        districtLayer.remove();
        legend.remove();
    }
});

// --- Problemas no mapa ---

function scheduleIssueLoad() {
    // o Leaflet desloca o mapa para o popup caber na tela; recarregar aqui recriaria o marcador e fecharia o popup
    if (state.autoPanning) {
        state.autoPanning = false;
        return;
    }
    // seguindo você, o mapa anda a cada passo: dentro da área já carregada, basta recontar
    if (state.loaded?.zoom === map.getZoom() && state.loaded.bounds.contains(map.getBounds())) {
        renderIssueCount();
        return;
    }
    clearTimeout(state.loadTimer);
    state.loadTimer = setTimeout(loadIssues, 250);
}

async function loadIssues() {
    const visible = map.getBounds();
    if (visible.getWest() >= visible.getEast() || visible.getSouth() >= visible.getNorth()) {
        return;
    }
    // busca uma margem além da tela, para pequenos deslocamentos não pedirem tudo de novo
    const padded = visible.pad(0.3);
    const bounds = L.latLngBounds([Math.max(padded.getSouth(), -90), Math.max(padded.getWest(), -180)],
        [Math.min(padded.getNorth(), 90), Math.min(padded.getEast(), 180)]);
    const params = new URLSearchParams({
        bbox: [bounds.getWest(), bounds.getSouth(), bounds.getEast(), bounds.getNorth()].map((v) => v.toFixed(6)).join(','),
    });
    if (state.filters.category) params.set('category', state.filters.category);
    if (state.filters.status) params.set('status', state.filters.status);

    const collection = await api(`/issues?${params}`);
    state.loaded = {bounds, zoom: map.getZoom(), truncated: collection.features.length >= 500};
    issueLayer.clearLayers();
    state.markers.clear();
    for (const feature of collection.features) {
        const [lon, lat] = feature.geometry.coordinates;
        const p = feature.properties;
        const marker = L.marker([lat, lon], {
            icon: L.divIcon({className: '', html: shapeSvg(p.status, 28), iconSize: [28, 28]}),
            title: `${p.categoryName} · ${STATUS[p.status].label}`,
            keyboard: true,
        });
        if (canHover()) {
            marker.bindTooltip(`${escapeHtml(shortName(p.category, p.categoryName))}: ${STATUS[p.status].label}`);
        }
        marker.on('click', () => openIssue(feature.id, marker));
        issueLayer.addLayer(marker);
        state.markers.set(feature.id, marker);
    }
    renderIssueCount();
}

// Conta só o que está na tela, não a margem carregada além dela
function renderIssueCount() {
    const visible = map.getBounds();
    let count = 0;
    state.markers.forEach((marker) => {
        if (visible.contains(marker.getLatLng())) count++;
    });
    const resolved = state.filters.status === 'RESOLVED';
    document.getElementById('issue-count').textContent = count === 0
        ? (resolved ? 'Nenhum problema resolvido nesta área' : 'Nenhum problema em aberto nesta área')
        : `${numberFormat.format(count)} ${count === 1 ? 'problema' : 'problemas'} ${resolved
            ? (count === 1 ? 'resolvido' : 'resolvidos') : 'em aberto'} nesta área${state.loaded?.truncated
            ? ' (mostrando parte)' : ''}`;
}

// --- O chamado aberto: pop-up no computador, gaveta no celular ---

// No celular um pop-up sobre o mapa briga com a gaveta e com os botões e não cabe na tela; ali o chamado
// abre na própria gaveta, como nos apps de mapa
function openIssue(id, marker) {
    setFollow(false); // o mapa parado, para o chamado não sair da tela
    if (isDesktop()) {
        if (!marker.getPopup()) {
            marker.bindPopup('Carregando…', {maxWidth: 320, minWidth: 260, autoPanPaddingTopLeft: mapPadding()});
            marker.on('popupopen', () => showIssue(id, popupHost(marker)));
        }
        marker.openPopup();
        return;
    }
    map.closePopup();
    selectMarker(marker);
    issueDetail.innerHTML = '<p class="hint">Carregando…</p>';
    showView('issue');
    // a gaveta sobe até a metade: o ponto vai para o meio do mapa que sobra à vista
    setTimeout(() => map.panTo(centerShowing(marker.getLatLng(), map.getZoom()), {animate: true}), 280);
    showIssue(id, sheetHost);
}

const issueDetail = document.getElementById('issue-detail');

function popupHost(marker) {
    return {
        setContent: (html) => marker.setPopupContent(html),
        element: () => marker.getPopup()?.getElement(),
        close: () => marker.closePopup(),
    };
}

const sheetHost = {
    setContent: (html) => {
        issueDetail.innerHTML = html;
    },
    element: () => issueDetail,
    close: () => showView('explore'),
};

function selectMarker(marker) {
    state.selectedMarker?.getElement()?.classList.remove('selected');
    state.selectedMarker = marker;
    marker?.getElement()?.classList.add('selected');
}

document.getElementById('issue-back').addEventListener('click', () => showView('explore'));

async function showIssue(id, host) {
    try {
        const issue = await api(`/issues/${id}`);
        const [hero, ...more] = issue.reports;
        const after = issue.resolution?.photoUrl;
        const photos = more.slice(0, 4).map((r) =>
            `<a href="${r.photoUrl}" target="_blank" rel="noopener"><img src="${r.photoUrl}" alt="Outra foto do problema" loading="lazy"></a>`).join('');
        const history = issue.history.map((h) =>
            `<li>${STATUS[h.from]?.label ?? 'Recebido'} → ${STATUS[h.to].label}, ${dateFormat.format(new Date(h.changedAt))}${
                h.changedBy ? ` por ${escapeHtml(h.changedBy)}` : ''}</li>`).join('');
        const actions = state.session ? TRANSITIONS[issue.status].map((s) =>
            `<button type="button" class="button" data-status="${s}">${STATUS[s].label}</button>`).join('') : '';
        // antes e depois: a foto do relato ao lado da foto do conserto, quando a equipe deixou uma
        const heroHtml = hero && after
            ? `<div class="popup-hero before-after">
                <a href="${hero.photoUrl}" target="_blank" rel="noopener"><img src="${hero.photoUrl}" alt="Antes: foto do problema"><span>Antes</span></a>
                <a href="${after}" target="_blank" rel="noopener"><img src="${after}" alt="Depois: foto do conserto"><span>Depois</span></a>
            </div>`
            : hero ? `<a class="popup-hero" href="${hero.photoUrl}" target="_blank" rel="noopener">
                <img src="${hero.photoUrl}" alt="Foto do problema"></a>` : '';
        const note = issue.resolution?.note
            ? `<p class="resolution-note"><strong>O que foi feito:</strong> ${escapeHtml(issue.resolution.note)}</p>` : '';
        const moderation = state.session ? issue.reports.slice(0, 4).map((r) =>
            `<button type="button" class="button" data-block-report="${r.id}">Bloquear autor do relato #${r.id}</button>`).join('') : '';

        host.setContent(`<div class="issue-popup">
            ${heroHtml}
            <h3>${escapeHtml(issue.categoryName)}</h3>
            ${trackLine(issue.status)}
            ${note}
            <ul class="popup-facts">
                <li>${peopleLabel(issue.reporterCount)}${issue.reportCount !== issue.reporterCount
                    ? ` (${reportsLabel(issue.reportCount, issue.reporterCount)})` : ''}</li>
                <li>Primeiro relato ${timeAgo(issue.firstReportedAt)}</li>
                <li>${escapeHtml(issue.neighborhood ?? 'Fora dos distritos importados')} · chamado #${issue.id}</li>
            </ul>
            ${photos ? `<div class="photos">${photos}</div>` : ''}
            ${history ? `<details><summary>Histórico</summary><ul class="history">${history}</ul></details>` : ''}
            ${actions ? `<p class="popup-section-title">Alterar status</p>
                <div class="popup-actions">${actions}</div>` : ''}
            ${moderation ? `<p class="popup-section-title">Moderação</p>
                <div class="popup-actions">${moderation}</div>` : ''}
        </div>`);

        // só os botões: a linha de etapas (trackLine) também tem data-status, e um clique nela não muda nada
        host.element()?.querySelectorAll('.popup-actions button[data-status]').forEach((button) => {
            button.addEventListener('click', (event) => {
                if (button.dataset.status !== 'RESOLVED') {
                    changeStatus(issue.id, button.dataset.status, host);
                    return;
                }
                // o botão sai da página com a troca pelo formulário, e o Leaflet tomaria o clique por um clique
                // no mapa, que fecha o pop-up
                event.stopPropagation();
                showResolveForm(issue.id, host, button.closest('.popup-actions'));
            });
        });
        host.element()?.querySelectorAll('[data-block-report]').forEach((button) => {
            button.addEventListener('click', () => blockReporter(button.dataset.blockReport, button));
        });
    } catch (error) {
        host.setContent(`<p class="message error">${escapeHtml(error.message)}</p>`);
    }
}

async function blockReporter(reportId, button) {
    if (!confirm(`Bloquear quem enviou o relato #${reportId}? A pessoa não poderá enviar novos relatos.`)) {
        return;
    }
    button.disabled = true;
    try {
        await api(`/reports/${reportId}/block-reporter`, {method: 'POST'});
        button.textContent = `Autor do relato #${reportId} bloqueado`;
    } catch (error) {
        button.disabled = false;
        button.textContent = error.message;
    }
}

async function changeStatus(id, status, host) {
    try {
        await api(`/issues/${id}/status`, {
            method: 'PATCH',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({status}),
        });
        host.close();
        await Promise.all([loadIssues(), loadDistricts(), loadQueue()]);
    } catch (error) {
        host.setContent(`<p class="message error">${escapeHtml(error.message)}</p>`);
    }
}

// Resolver mostrando o que foi feito: a foto do conserto e uma nota, para quem relatou ver o resultado
function showResolveForm(id, host, actions) {
    const form = document.createElement('form');
    form.className = 'resolve-form';
    form.innerHTML = `<label>Foto do conserto (opcional)
            <input type="file" name="photo" accept="image/jpeg,image/png" capture="environment">
        </label>
        <label>O que foi feito (opcional)
            <textarea name="note" maxlength="500" rows="3" placeholder="Ex.: buraco tapado com asfalto novo"></textarea>
        </label>
        <p class="hint">A foto e a nota ficam públicas no mapa e vão no aviso para quem relatou. Enquadre só o local,
            sem rostos nem placas.</p>
        <div class="popup-actions">
            <button type="submit" class="button primary">Confirmar resolvido</button>
            <button type="button" class="button" data-cancel>Cancelar</button>
        </div>
        <p class="message" aria-live="polite"></p>`;
    actions.replaceWith(form);
    form.querySelector('[data-cancel]').addEventListener('click', (event) => {
        event.stopPropagation(); // pelo mesmo motivo do botão "Resolvido"
        form.replaceWith(actions);
    });
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const submit = form.querySelector('[type="submit"]');
        const data = new FormData(form);
        if (!data.get('photo')?.size) {
            data.delete('photo');
        }
        submit.disabled = true;
        try {
            await api(`/issues/${id}/resolution`, {method: 'POST', body: data});
            host.close();
            await Promise.all([loadIssues(), loadDistricts(), loadQueue()]);
        } catch (error) {
            const message = form.querySelector('.message');
            message.textContent = error.message;
            message.className = 'message error';
            submit.disabled = false;
        }
    });
}

// Filtros: chips de tipo e a situação (em aberto ou resolvidos)
function renderCategoryChips() {
    const chips = [{code: '', name: 'Todos'}, ...state.categories];
    const container = document.getElementById('category-chips');
    container.innerHTML = chips.map((c) => `<button type="button" class="chip" data-category="${c.code}"
        aria-pressed="${state.filters.category === c.code}">${c.code ? categoryIcon(c.code) : ''}${
        escapeHtml(c.code ? shortName(c.code, c.name) : c.name)}</button>`).join('');
    container.querySelectorAll('[data-category]').forEach((chip) => {
        chip.addEventListener('click', () => {
            state.filters.category = chip.dataset.category;
            renderCategoryChips();
            loadIssues();
        });
    });
}

document.querySelectorAll('.segmented [data-status]').forEach((button) => {
    button.addEventListener('click', () => {
        state.filters.status = button.dataset.status;
        document.querySelectorAll('.segmented [data-status]').forEach((b) => {
            b.setAttribute('aria-pressed', String(b === button));
        });
        loadIssues();
    });
});

// --- Novo relato: local, tipo e foto ---

const reportForm = document.getElementById('report-form');
const reportMessage = document.getElementById('report-message');

function setReportMessage(text, kind) {
    reportMessage.textContent = text;
    reportMessage.className = `message ${kind ?? ''}`;
}

function goToStep(step) {
    state.report.step = step;
    const order = ['where', 'what', 'photo'];
    document.querySelectorAll('[data-step-panel]').forEach((panelStep) => {
        panelStep.hidden = panelStep.dataset.stepPanel !== step;
    });
    document.querySelectorAll('.steps [data-step]').forEach((item) => {
        const index = order.indexOf(item.dataset.step);
        item.classList.toggle('done', index < order.indexOf(step));
        if (item.dataset.step === step) {
            item.setAttribute('aria-current', 'step');
        } else {
            item.removeAttribute('aria-current');
        }
    });
    // escolher o ponto precisa do mapa à vista; tipo e foto, de espaço no painel
    setSheet(step === 'where' ? 'half' : 'full');
    document.body.classList.toggle('picking', step === 'where');
    document.querySelector('.views').scrollTop = 0;
}

function startReport() {
    resetReport();
    stopLocationFix(); // o relato também usa o toque no mapa: os dois modos não convivem
    setFollow(false); // o mapa fica parado enquanto a pessoa marca o ponto
    state.report.active = true;
    map.closePopup();
    showView('report');
    goToStep('where');
}

function resetReport() {
    state.report = {step: 'where', category: null, active: false, confirmCategory: false};
    hideSuggestion();
    state.pickedLocation = null;
    state.pickMarker?.remove();
    state.pickMarker = null;
    reportForm.reset();
    document.getElementById('photo-preview').hidden = true;
    document.getElementById('photo-drop-text').hidden = false;
    document.getElementById('report-category').value = '';
    document.getElementById('where-choice').hidden = false;
    document.getElementById('where-confirm').hidden = true;
    document.getElementById('report-hint').hidden = true;
    document.getElementById('where-text').hidden = false;
    document.getElementById('where-text').textContent =
        'Use a localização do seu aparelho para marcar o ponto, ou toque no mapa.';
    document.querySelectorAll('#category-grid .tile').forEach((tile) => tile.setAttribute('aria-pressed', 'false'));
    document.body.classList.remove('picking');
    setReportMessage('');
}

// Ponto do relato: o pino pode ser arrastado, e um toque no mapa o move
function pickLocation(latlng) {
    state.pickedLocation = latlng;
    state.pickMarker?.remove();
    state.pickMarker = L.marker(latlng, {draggable: true, title: 'Local do relato', autoPan: true}).addTo(map);
    state.pickMarker.on('dragend', () => {
        state.pickedLocation = state.pickMarker.getLatLng();
    });
    document.getElementById('where-choice').hidden = true;
    document.getElementById('report-hint').hidden = true;
    document.getElementById('where-text').hidden = true;
    document.getElementById('where-confirm').hidden = false;
}

function startManualPick(message = '', kind) {
    setReportMessage(message, kind);
    document.getElementById('where-choice').hidden = true;
    document.getElementById('report-hint').hidden = false;
}

async function locateForReport() {
    setReportMessage('Buscando sua localização…');
    try {
        const position = await currentPosition();
        pickLocation(showUserLocation(position));
        const accuracy = Math.round(userAccuracy());
        setReportMessage(accuracy > LOW_ACCURACY_METERS
            ? `Localização aproximada (cerca de ${numberFormat.format(accuracy)} m). Confira o pino antes de continuar.`
            : '');
    } catch (error) {
        // sem localização, o relato continua: o ponto é escolhido no mapa
        startManualPick(error.message, 'error');
    }
}

// Estado da permissão sem disparar o pedido do navegador; sem a Permissions API (Safari antigo), "prompt"
async function geolocationPermission() {
    if (!window.isSecureContext || !('geolocation' in navigator)) {
        return 'unsupported';
    }
    try {
        return (await navigator.permissions.query({name: 'geolocation'})).state;
    } catch {
        return 'prompt';
    }
}

// Antes do pedido do navegador, o próprio passo explica para que serve a localização
const WHERE_TEXT = {
    prompt: 'Para marcar o lugar certo, usamos a localização do seu aparelho. Ela só marca o ponto do relato e '
        + 'não é guardada. O navegador vai pedir sua permissão.',
    denied: 'O acesso à localização está bloqueado para este site. Para liberar, toque no cadeado ao lado do endereço. '
        + 'Ou marque o ponto no mapa.',
    unsupported: 'Este navegador não informa a localização nesta página. Marque o ponto no mapa.',
};

document.getElementById('report-begin').addEventListener('click', async () => {
    if (!citizenSignedIn()) {
        openLogin('Para relatar, entre com o seu e-mail. Assim você acompanha o relato em qualquer aparelho.',
            () => document.getElementById('report-begin').click());
        return;
    }
    startReport();
    const permission = await geolocationPermission();
    if (permission === 'granted') {
        await locateForReport(); // já permitido: vai direto para o ponto do GPS
        return;
    }
    document.getElementById('where-text').textContent = WHERE_TEXT[permission] ?? WHERE_TEXT.prompt;
    document.getElementById('location-allow').hidden = permission !== 'prompt';
});

document.getElementById('location-allow').addEventListener('click', () => locateForReport());
document.getElementById('location-manual').addEventListener('click', () => startManualPick());
document.getElementById('where-next').addEventListener('click', () => goToStep('what'));
document.getElementById('report-cancel').addEventListener('click', () => {
    resetReport();
    showView('explore');
});
document.querySelectorAll('[data-step-back]').forEach((button) => {
    button.addEventListener('click', () => goToStep(button.dataset.stepBack));
});

map.on('click', (event) => {
    if (tracking.fixing) {
        setManualLocation(event.latlng);
        return;
    }
    if (state.report.active && state.report.step === 'where'
        && (document.getElementById('report-hint').hidden === false || state.pickMarker)) {
        pickLocation(event.latlng);
        return;
    }
    // no celular, tocar fora do chamado aberto na gaveta é voltar ao mapa, como fecharia um pop-up
    if (panel.dataset.current === 'issue') {
        showView('explore');
    }
});

function renderCategoryTiles() {
    const grid = document.getElementById('category-grid');
    grid.innerHTML = state.categories.map((c) => `<button type="button" class="tile" data-category="${c.code}"
        aria-pressed="false" title="${escapeHtml(c.name)}">${categoryIcon(c.code)}<span>${
        escapeHtml(shortName(c.code, c.name))}</span></button>`).join('');
    grid.querySelectorAll('[data-category]').forEach((tile) => {
        tile.addEventListener('click', () => {
            chooseCategory(tile.dataset.category);
            hideSuggestion();
            state.report.confirmCategory = false; // outro tipo: o classificador confere de novo
            goToStep('photo');
        });
    });
}

// --- O classificador recusou a foto: sugere o tipo certo, outra foto, ou enviar assim mesmo ---

const suggestion = document.getElementById('category-suggestion');

function hideSuggestion() {
    suggestion.hidden = true;
    reportForm.querySelector('[type="submit"]').hidden = false;
}

function showSuggestion(problem) {
    const chosen = problem.chosenCategory;
    const predicted = problem.predictedCategory;
    const accept = document.getElementById('suggestion-accept');
    // sem tipo para sugerir, a saída principal é outra foto
    document.getElementById('suggestion-photo').className = predicted === 'OTHER' ? 'cta' : 'button';
    if (predicted === 'OTHER') {
        document.getElementById('suggestion-text').textContent =
            'A foto não parece mostrar um problema de rua. Tire outra foto, enquadrando o problema.';
        accept.hidden = true;
    } else {
        document.getElementById('suggestion-text').textContent =
            `A foto parece ser de ${categoryLabel(predicted)}, e não de ${categoryLabel(chosen)}. `
            + 'Quer enviar com o tipo certo?';
        accept.textContent = `Enviar como ${shortName(predicted, problem.predictedCategoryName)}`;
        accept.dataset.category = predicted;
        accept.hidden = false;
    }
    document.getElementById('suggestion-insist').textContent =
        `Enviar como ${categoryLabel(chosen)} mesmo assim (a prefeitura confere)`;
    suggestion.hidden = false;
    reportForm.querySelector('[type="submit"]').hidden = true;
    suggestion.scrollIntoView({block: 'nearest'});
}

function chooseCategory(code) {
    state.report.category = code;
    document.getElementById('report-category').value = code;
    document.querySelectorAll('#category-grid .tile').forEach((tile) => {
        tile.setAttribute('aria-pressed', String(tile.dataset.category === code));
    });
}

document.getElementById('suggestion-accept').addEventListener('click', (event) => {
    chooseCategory(event.currentTarget.dataset.category);
    hideSuggestion();
    reportForm.requestSubmit();
});
document.getElementById('suggestion-photo').addEventListener('click', () => {
    hideSuggestion();
    reportForm.elements.photo.click();
});
document.getElementById('suggestion-insist').addEventListener('click', () => {
    state.report.confirmCategory = true; // vai como está, e a classificação pela fila leva para a moderação
    hideSuggestion();
    reportForm.requestSubmit();
});

reportForm.elements.photo.addEventListener('change', () => {
    hideSuggestion();
    state.report.confirmCategory = false; // foto nova: o classificador confere de novo
    const file = reportForm.elements.photo.files[0];
    const preview = document.getElementById('photo-preview');
    if (state.photoUrl) {
        URL.revokeObjectURL(state.photoUrl);
    }
    if (!file) {
        preview.hidden = true;
        document.getElementById('photo-drop-text').hidden = false;
        return;
    }
    state.photoUrl = URL.createObjectURL(file);
    preview.src = state.photoUrl;
    preview.hidden = false;
    document.getElementById('photo-drop-text').hidden = true;
});

reportForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = reportForm.querySelector('[type="submit"]');
    const data = new FormData(reportForm);
    data.set('latitude', state.pickedLocation.lat.toFixed(6));
    data.set('longitude', state.pickedLocation.lng.toFixed(6));
    if (state.report.confirmCategory) {
        data.set('confirmCategory', 'true');
    }

    submit.disabled = true;
    setReportMessage('Enviando…');
    try {
        const result = await sendReport(data);
        const others = result.issueReportCount - 1;
        const chosen = data.get('category');
        resetReport();
        document.getElementById('sent-title').textContent = 'Relato enviado. Obrigado!';
        document.getElementById('sent-text').textContent = result.newIssue
            ? `Você abriu o chamado #${result.issueId}. A prefeitura vê o seu relato na fila de atendimento.`
            : `Este problema já estava no mapa (chamado #${result.issueId}): o seu relato soma a ${others === 1
                ? 'outro relato' : `outros ${numberFormat.format(others)} relatos`} e aumenta a prioridade.`;
        showView('sent');
        checkPhoto(result.reportId, chosen);
        await Promise.all([loadIssues(), loadDistricts(), loadMyReports()]);
    } catch (error) {
        if (error.status === 422 && error.problem?.predictedCategory) {
            setReportMessage('');
            showSuggestion(error.problem);
            return;
        }
        if (error.loginRequired) {
            // a foto e o ponto continuam no formulário: depois do login, é só enviar de novo
            setReportMessage('');
            openLogin(`${error.message} O relato continua aqui, pronto para enviar.`, () => showView('report'));
            return;
        }
        setReportMessage(error.message, 'error');
    } finally {
        submit.disabled = false;
    }
});

// O classificador olha a foto segundos depois do envio (outbox → RabbitMQ → worker). O app espera a resposta e
// conta o resultado na hora: quem errou o tipo entende o que houve, e quem mandou outra coisa vê que foi notado
const PHOTO_CHECK_TRIES = 12;
const PHOTO_CHECK_INTERVAL = 1500;

function categoryLabel(code) {
    const category = state.categories.find((c) => c.code === code);
    return shortName(code, category?.name).toLowerCase();
}

function photoCheckMessage(check, chosen) {
    switch (check.status) {
        case 'AGREED':
            return {kind: 'success', text: `A foto confere: é ${categoryLabel(chosen)} mesmo.`};
        case 'MISMATCH':
            return check.predictedCategory === 'OTHER'
                ? {kind: 'warning', text: 'A foto não parece mostrar um problema de rua. A prefeitura vai conferir '
                        + 'este relato, e ele pode ser recusado.'}
                : {kind: 'warning', text: `A foto parece ser de ${categoryLabel(check.predictedCategory)}, e não de `
                        + `${categoryLabel(chosen)}. A prefeitura vai conferir o tipo do relato.`};
        case 'LOW_CONFIDENCE':
            return {kind: 'warning', text: `Não deu para confirmar pela foto que é ${categoryLabel(chosen)}. `
                    + 'A prefeitura vai conferir o relato.'};
        default:
            return null;
    }
}

async function checkPhoto(reportId, chosen) {
    const box = document.getElementById('sent-check');
    box.hidden = false;
    box.className = 'photo-check pending';
    box.textContent = 'Conferindo a foto…';
    for (let attempt = 0; attempt < PHOTO_CHECK_TRIES; attempt++) {
        await new Promise((resolve) => setTimeout(resolve, PHOTO_CHECK_INTERVAL));
        if (panel.dataset.current !== 'sent') {
            return; // a pessoa saiu da tela: o resultado aparece em "Meus relatos"
        }
        try {
            const page = await reporterFetch('/reporters/me/reports?page=0&size=10');
            const message = photoCheckMessage(page.items.find((item) => item.reportId === reportId)?.photoCheck
                ?? {status: 'PENDING'}, chosen);
            if (message) {
                box.className = `photo-check ${message.kind}`;
                box.textContent = message.text;
                return;
            }
        } catch {
            break; // sem a lista agora: não insiste, e o relato já foi enviado
        }
    }
    // sem resposta do classificador (fora do ar ou atrasado): a moderação continua valendo
    box.className = 'photo-check';
    box.textContent = 'A conferência automática da foto ainda não terminou. Você vê o andamento em "Meus relatos".';
}

// --- Meus relatos ---

const myReports = {page: 0, items: [], total: 0};
const myReportsMessage = document.getElementById('my-reports-message');

function setMyReportsMessage(text, kind) {
    myReportsMessage.textContent = text;
    myReportsMessage.className = `message ${kind ?? ''}`;
}

async function loadMyReports(append = false) {
    const list = document.getElementById('my-reports-list');
    if (!citizenSignedIn()) {
        // sem login: nada a buscar
        myReports.items = [];
        myReports.total = 0;
        list.innerHTML = '';
        document.getElementById('my-reports-more').hidden = true;
        renderMineEmpty(0);
        renderPush();
        return;
    }
    myReports.page = append ? myReports.page + 1 : 0;
    const result = await reporterFetch(`/reporters/me/reports?page=${myReports.page}&size=10`);
    myReports.items = append ? myReports.items.concat(result.items) : result.items;
    myReports.total = result.total;
    list.innerHTML = myReports.items.map((item, index) => {
        const open = !item.rejected && STATUS[item.issueStatus];
        const note = item.rejected
            ? '<p class="track-note">A moderação não aprovou este relato, e ele não conta mais no chamado.</p>'
            : STATUS[item.issueStatus] ? trackLine(item.issueStatus) : '<p class="track-note">Chamado encerrado pela moderação.</p>';
        return `<li><button type="button" class="track" data-index="${index}" ${open ? '' : 'disabled'}>
            <span class="track-head">
                <span class="track-icon">${categoryIcon(item.category)}</span>
                <span class="track-title">${escapeHtml(shortName(item.category, item.categoryName))}</span>
                <span class="track-meta">Enviado ${timeAgo(item.sentAt)}${item.history.length
                    ? `, atualizado ${timeAgo(item.updatedAt)}` : ''}</span>
            </span>
            ${note}
            ${['MISMATCH', 'LOW_CONFIDENCE'].includes(item.photoCheck?.status) && !item.rejected
                ? '<span class="track-check">A foto está em análise: a prefeitura vai conferir o tipo do relato.</span>' : ''}
            ${item.resolution ? `<span class="track-resolution">${item.resolution.photoUrl
                ? `<img src="${item.resolution.photoUrl}" alt="Foto do conserto" loading="lazy">` : ''}<span>${
                item.resolution.note ? escapeHtml(item.resolution.note) : 'A equipe deixou uma foto do conserto.'}</span></span>` : ''}
        </button></li>`;
    }).join('');
    list.querySelectorAll('[data-index]:not([disabled])').forEach((button) => {
        const item = myReports.items[Number(button.dataset.index)];
        button.addEventListener('click', () => focusIssue({id: item.issueId, latitude: item.latitude,
            longitude: item.longitude, status: item.issueStatus, category: item.category}));
    });
    document.getElementById('my-reports-more').hidden = myReports.items.length >= result.total;
    renderMineEmpty(result.total);
    renderPush();
}

function renderMineEmpty(total) {
    const signedIn = citizenSignedIn();
    document.getElementById('mine-count').textContent = total ? numberFormat.format(total) : '';
    document.getElementById('my-reports-hint').textContent = !signedIn
        ? 'Entre com o seu e-mail para ver aqui os seus relatos, em qualquer aparelho, com o andamento de cada um.'
        : total === 0
            ? 'Você ainda não relatou nada. Quando relatar, acompanhe aqui cada etapa até o problema ser resolvido.'
            : 'Toque num relato para ver o chamado no mapa.';
    document.getElementById('mine-empty-cta').hidden = !signedIn || total > 0;
    document.getElementById('mine-login').hidden = signedIn;
}

document.getElementById('mine-login').addEventListener('click', () =>
    openLogin('Entre com o seu e-mail para ver os seus relatos.', () => showView('mine')));

document.getElementById('my-reports-more').addEventListener('click', () =>
    loadMyReports(true).catch((error) => setMyReportsMessage(error.message, 'error')));

// Avisos por push: o service worker recebe a mensagem cifrada mesmo com a página fechada
const push = {config: null, registration: null, subscription: null};
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

async function setupPush() {
    push.config = await api('/reporters/push/config');
    if (!push.config.enabled || !pushSupported) {
        renderPush();
        return;
    }
    push.registration = await navigator.serviceWorker.register('/sw.js');
    let subscription = await push.registration.pushManager.getSubscription();
    if (subscription && !sameKey(subscription.options.applicationServerKey, push.config.publicKey)) {
        await subscription.unsubscribe(); // o servidor trocou as chaves VAPID: a inscrição antiga não recebe mais nada
        subscription = null;
    }
    push.subscription = subscription;
    if (subscription && citizenSignedIn()) {
        // liga o navegador à identidade atual, que pode ter sido trocada depois da inscrição
        await saveSubscription(subscription);
    }
    renderPush();
}

// A oferta de avisos aparece depois do envio e em Meus relatos, sempre com o mesmo estado
function renderPush() {
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

function saveSubscription(subscription) {
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

// --- App instalável (PWA): ícone na tela de início, tela cheia e, no iPhone, as notificações ---

const standalone = () => window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
// o iPad se apresenta como Mac; a tela de toque denuncia
const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent)
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
let installPrompt = null;

// Android e computador: o navegador oferece instalar, e o botão abre esse pedido. iPhone: não há pedido, só o
// passo a passo pelo Compartilhar do Safari
function renderInstall() {
    const box = document.getElementById('install-box');
    const button = document.getElementById('install-button');
    if (standalone()) {
        box.hidden = true;
        return;
    }
    if (installPrompt) {
        box.hidden = false;
        button.hidden = false;
        return;
    }
    if (isIos) {
        document.getElementById('install-text').textContent = 'No iPhone: toque em Compartilhar (o quadrado com a '
            + 'seta para cima, na barra do Safari) e depois em "Adicionar à Tela de Início". O mapa passa a abrir '
            + 'como um aplicativo, e você pode ligar os avisos dos seus relatos.';
        box.hidden = false;
        button.hidden = true;
        return;
    }
    box.hidden = true;
}

window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault(); // o pedido fica guardado para o botão, em vez de aparecer sozinho
    installPrompt = event;
    renderInstall();
});
window.addEventListener('appinstalled', () => {
    installPrompt = null;
    renderInstall();
    showToast('App instalado: o SP Bem-Estar está na sua tela de início.');
});
document.getElementById('install-button').addEventListener('click', async () => {
    if (!installPrompt) {
        return;
    }
    installPrompt.prompt();
    await installPrompt.userChoice;
    installPrompt = null;
    renderInstall();
});

// o service worker entra sempre (a tela de "sem internet" e a instalação dependem dele), não só com os avisos
navigator.serviceWorker?.register('/sw.js').catch(() => {
    // sem service worker o app funciona igual, só sem a tela de "sem internet"
});
renderInstall();

// Atalhos do ícone do app (manifest.webmanifest): segurar o ícone mostra "Relatar um problema" e "Meus relatos"
function openShortcut() {
    const params = new URLSearchParams(location.search);
    const action = params.get('acao');
    const tab = params.get('aba');
    if (!action && !tab) {
        return;
    }
    history.replaceState(null, '', location.pathname);
    if (action === 'relatar') {
        document.getElementById('report-begin').click();
    } else if (tab === 'meus-relatos') {
        showView('mine');
    }
}

// O service worker avisa a página aberta: a lista se atualiza e o clique na notificação abre o chamado
navigator.serviceWorker?.addEventListener('message', (event) => {
    if (event.data?.type === 'issue-status') {
        loadMyReports().catch(() => {});
    } else if (event.data?.type === 'open-issue') {
        openIssueById(event.data.issueId);
    }
});

async function openIssueById(id) {
    if (!Number.isInteger(Number(id)) || Number(id) <= 0) {
        return;
    }
    try {
        const issue = await api(`/issues/${Number(id)}`);
        await focusIssue({id: issue.id, latitude: issue.latitude, longitude: issue.longitude,
            status: issue.status, category: issue.category});
    } catch (error) {
        setMyReportsMessage(`Chamado #${Number(id)}: ${error.message}`, 'error');
    }
}

// Link da notificação: /?issue=123 abre o chamado
function openLinkedIssue() {
    const params = new URLSearchParams(location.search);
    const id = params.get('issue');
    if (id) {
        history.replaceState(null, '', location.pathname);
        return openIssueById(id);
    }
}

// Leva o mapa até o chamado e abre o popup, onde ficam as ações de status e de moderação
async function focusIssue(item) {
    setFollow(false);
    // um chamado resolvido, ou de outro tipo, não estaria no mapa com os filtros atuais
    const resolved = item.status === 'RESOLVED';
    if (item.status && (state.filters.status === 'RESOLVED') !== resolved) {
        state.filters.status = resolved ? 'RESOLVED' : '';
        document.querySelectorAll('.segmented [data-status]').forEach((b) => {
            b.setAttribute('aria-pressed', String(b.dataset.status === state.filters.status));
        });
    }
    if (item.category && state.filters.category && state.filters.category !== item.category) {
        state.filters.category = '';
        renderCategoryChips();
    }
    if (!isDesktop()) {
        setSheet('peek');
    }
    map.setView([item.latitude, item.longitude], 18, {animate: false});
    if (isDesktop()) {
        map.panBy([-210, 0], {animate: false}); // tira o ponto de baixo do painel
    }
    clearTimeout(state.loadTimer); // o moveend agendou um recarregamento que fecharia o popup
    await loadIssues();
    const marker = state.markers.get(item.id);
    if (marker) {
        openIssue(item.id, marker);
    }
}

// --- Prefeitura ---

function renderSession() {
    const session = state.session;
    document.getElementById('operator-tab').hidden = !session;
    renderAccount();
    document.getElementById('operator-name').textContent = session
        ? `Conectado como ${session.name}. Toque num chamado da fila para abri-lo no mapa e alterar o status.` : '';
    document.getElementById('team-subtab').hidden = session?.role !== 'ADMIN';
    map.closePopup();
    if (session) {
        loadOperatorQueues();
        if (session.role === 'ADMIN') {
            loadTeam().catch((error) => setTeamMessage(error.message, 'error'));
        }
    } else if (!document.querySelector('[data-view="operator"]').hidden) {
        showView('more');
    }
}

function showSubview(name) {
    document.querySelectorAll('[data-subview-panel]').forEach((p) => {
        p.hidden = p.dataset.subviewPanel !== name;
    });
    document.querySelectorAll('.subtabs [data-subview]').forEach((b) => {
        if (b.dataset.subview === name) {
            b.setAttribute('aria-current', 'page');
        } else {
            b.removeAttribute('aria-current');
        }
    });
}

document.querySelectorAll('.subtabs [data-subview]').forEach((button) => {
    button.addEventListener('click', () => showSubview(button.dataset.subview));
});

// Filas do operador: atendimento (por prioridade) e moderação (relatos marcados pelo classificador)
const PAGE_SIZE = 20;
const queue = {page: 0, items: []};
const moderation = {page: 0, items: []};
const moderationMessage = document.getElementById('moderation-message');

function loadOperatorQueues() {
    Promise.all([loadQueue(), loadModeration()]).catch((error) => {
        moderationMessage.textContent = error.message;
        moderationMessage.className = 'message error';
    });
}

async function loadQueue(append = false) {
    if (!state.session) {
        return;
    }
    queue.page = append ? queue.page + 1 : 0;
    const params = new URLSearchParams({page: queue.page, size: PAGE_SIZE});
    const category = document.getElementById('queue-category').value;
    const status = document.getElementById('queue-status').value;
    if (category) params.set('category', category);
    if (status) params.set('status', status);

    const result = await api(`/issues/queue?${params}`);
    queue.items = append ? queue.items.concat(result.items) : result.items;
    const list = document.getElementById('queue-list');
    list.innerHTML = queue.items.map((item, index) => `<li>
        <button type="button" class="queue-item" data-issue="${item.id}">
            <span class="queue-rank">${index + 1}</span>
            <span class="queue-body">
                <strong>${escapeHtml(item.categoryName)} · #${item.id}</strong>
                <span>${statusLabel(item.status)} · ${escapeHtml(item.neighborhood ?? 'Fora dos distritos importados')}</span>
                <span class="queue-meta">Prioridade ${Number(item.priorityScore).toFixed(2)} · ${reportsLabel(item.reportCount, item.reporterCount)}${
                    item.pendingModeration ? ` · ${item.pendingModeration} para moderar` : ''}</span>
            </span>
        </button></li>`).join('');
    list.querySelectorAll('[data-issue]').forEach((button, index) => {
        button.addEventListener('click', () => focusIssue(queue.items[index]));
    });
    document.getElementById('queue-summary').textContent = result.total === 0
        ? 'Nenhum chamado em aberto.'
        : `${numberFormat.format(queue.items.length)} de ${numberFormat.format(result.total)} chamado(s).`;
    document.getElementById('queue-more').hidden = queue.items.length >= result.total;
}

async function loadModeration(append = false) {
    if (!state.session) {
        return;
    }
    moderation.page = append ? moderation.page + 1 : 0;
    const result = await api(`/moderation/reports?page=${moderation.page}&size=${PAGE_SIZE}`);
    moderation.items = append ? moderation.items.concat(result.items) : result.items;
    document.getElementById('moderation-count').textContent = result.total ? numberFormat.format(result.total) : '';
    const list = document.getElementById('moderation-list');
    list.innerHTML = moderation.items.length === 0
        ? '<li class="hint">Nenhum relato esperando moderação.</li>'
        : moderation.items.map((item) => {
            const confidence = item.confidence == null ? '' : ` (${Math.round(Number(item.confidence) * 100)}%)`;
            const verdict = item.classificationStatus === 'LOW_CONFIDENCE'
                ? `Modelo em dúvida: ${escapeHtml(item.predictedCategoryName ?? item.predictedCategory)}${confidence}`
                : `Modelo viu: ${escapeHtml(item.predictedCategoryName ?? item.predictedCategory)}${confidence}`;
            return `<li class="moderation-item">
                <a href="${item.photoUrl}" target="_blank" rel="noopener"><img src="${item.photoUrl}" alt="Foto do relato ${item.reportId}" loading="lazy"></a>
                <div class="moderation-body">
                    <strong>Relato #${item.reportId} · chamado #${item.issueId}</strong>
                    <span>Enviado como: ${escapeHtml(item.categoryName)}</span>
                    <span class="queue-meta">${verdict}</span>
                    ${item.reporterBlocked ? '<span class="queue-meta">Autor já bloqueado</span>' : ''}
                    <div class="actions">
                        <button type="button" class="button" data-report="${item.reportId}" data-decision="APPROVED">Aprovar</button>
                        <button type="button" class="button" data-report="${item.reportId}" data-decision="REJECTED">Rejeitar</button>
                        ${item.reporterBlocked ? '' : `<button type="button" class="button" data-report="${item.reportId}"
                            data-decision="REJECTED" data-block="true">Rejeitar e bloquear autor</button>`}
                    </div>
                </div>
            </li>`;
        }).join('');
    list.querySelectorAll('[data-decision]').forEach((button) => {
        button.addEventListener('click', () => moderate(button.dataset.report, button.dataset.decision,
            button.dataset.block === 'true', button));
    });
    document.getElementById('moderation-more').hidden = moderation.items.length >= result.total;
}

async function moderate(reportId, decision, blockReporter, button) {
    if (decision === 'REJECTED' && !confirm(blockReporter
        ? `Rejeitar o relato #${reportId} e bloquear quem o enviou? A foto será apagada.`
        : `Rejeitar o relato #${reportId}? Ele deixa de contar no chamado e a foto será apagada.`)) {
        return;
    }
    button.disabled = true;
    moderationMessage.className = 'message';
    try {
        const result = await api(`/moderation/reports/${reportId}`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({decision, blockReporter}),
        });
        moderationMessage.textContent = decision === 'APPROVED'
            ? `Relato #${reportId} aprovado.`
            : result.issueStatus === 'DISCARDED'
                ? `Relato #${reportId} rejeitado; o chamado #${result.issueId} ficou sem relatos e foi descartado.`
                : `Relato #${reportId} rejeitado; o chamado #${result.issueId} agora tem ${result.issueReportCount} relato(s).`;
        moderationMessage.className = 'message success';
        await Promise.all([loadModeration(), loadQueue(), loadIssues(), loadDistricts()]);
    } catch (error) {
        button.disabled = false;
        moderationMessage.textContent = error.message;
        moderationMessage.className = 'message error';
    }
}

document.getElementById('queue-category').addEventListener('change', () => loadQueue());
document.getElementById('queue-status').addEventListener('change', () => loadQueue());
document.getElementById('queue-more').addEventListener('click', () => loadQueue(true));

// Planilha dos chamados: o arquivo vem com o token do operador, então o download passa por um blob
document.getElementById('export-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const submit = form.querySelector('[type="submit"]');
    const message = document.getElementById('export-message');
    const params = new URLSearchParams([...new FormData(form)].filter(([, value]) => value));
    submit.disabled = true;
    message.className = 'message';
    message.textContent = 'Gerando a planilha…';
    try {
        const response = await fetch(`${API}/issues/export?${params}`,
            {headers: {Authorization: `Bearer ${state.session?.accessToken}`}});
        if (response.status === 401) {
            saveSession(null);
            throw new Error('Sua sessão terminou. Toque em Entrar, no alto do app, para entrar de novo.');
        }
        if (!response.ok) {
            const body = response.headers.get('content-type')?.includes('json') ? await response.json() : null;
            throw new Error(body?.detail || body?.title || `Erro ${response.status}`);
        }
        const name = /filename="?([^";]+)"?/.exec(response.headers.get('content-disposition') ?? '')?.[1]
            ?? 'chamados.csv';
        const url = URL.createObjectURL(await response.blob());
        const link = Object.assign(document.createElement('a'), {href: url, download: name});
        document.body.append(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 10_000);
        message.className = 'message success';
        message.textContent = `Planilha ${name} baixada.`;
    } catch (error) {
        message.className = 'message error';
        message.textContent = error.message;
    } finally {
        submit.disabled = false;
    }
});
document.getElementById('moderation-more').addEventListener('click', () => loadModeration(true));

// Equipe (só o administrador) e troca da própria senha
const teamMessage = document.getElementById('team-message');
const ROLE_LABELS = {OPERATOR: 'Operador', ADMIN: 'Administrador'};

function setTeamMessage(text, kind) {
    teamMessage.textContent = text;
    teamMessage.className = `message ${kind ?? ''}`;
}

// id do operador logado, lido do próprio token: o administrador não pode desativar a si mesmo
function sessionUserId() {
    try {
        const payload = state.session.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        return Number(JSON.parse(atob(payload)).sub);
    } catch {
        return null;
    }
}

async function loadTeam() {
    const team = await api('/operators');
    const me = sessionUserId();
    const list = document.getElementById('team-list');
    list.innerHTML = team.map((user) => `<li class="team-item ${user.active ? '' : 'inactive'}">
        <span class="queue-body">
            <strong>${escapeHtml(user.name)}</strong>
            <span class="queue-meta">${escapeHtml(user.email)} · ${ROLE_LABELS[user.role] ?? escapeHtml(user.role)}${
                user.active ? '' : ' · desativado'}</span>
        </span>
        ${user.id === me ? '<span class="queue-meta">Você</span>'
            : `<button type="button" class="button" data-user="${user.id}" data-active="${!user.active}">${
                user.active ? 'Desativar' : 'Reativar'}</button>`}
    </li>`).join('');
    list.querySelectorAll('[data-user]').forEach((button) => {
        button.addEventListener('click', () => setUserActive(button.dataset.user, button.dataset.active === 'true', button));
    });
}

async function setUserActive(id, active, button) {
    const name = button.closest('li').querySelector('strong').textContent;
    if (!active && !confirm(`Desativar ${name}? A pessoa perde o acesso agora, mesmo com a sessão aberta.`)) {
        return;
    }
    button.disabled = true;
    try {
        await api(`/operators/${Number(id)}`, {
            method: 'PATCH',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({active}),
        });
        setTeamMessage(`Acesso de ${name} ${active ? 'reativado' : 'desativado'}.`, 'success');
        await loadTeam();
    } catch (error) {
        button.disabled = false;
        setTeamMessage(error.message, 'error');
    }
}

const teamForm = document.getElementById('team-form');

teamForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = teamForm.querySelector('[type="submit"]');
    const data = new FormData(teamForm);
    submit.disabled = true;
    setTeamMessage('');
    try {
        const user = await api('/operators', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({name: data.get('name'), email: data.get('email'), role: data.get('role')}),
        });
        teamForm.reset();
        setTeamMessage(`Cadastro de ${user.name} feito. A pessoa já pode tocar em Entrar, no alto do app, e usar o código que chega `
            + 'no e-mail.', 'success');
        await loadTeam();
    } catch (error) {
        setTeamMessage(error.message, 'error');
    } finally {
        submit.disabled = false;
    }
});

// --- Entrar: um login só, por código enviado por e-mail. Quem entra decide a API, pelo e-mail: a equipe da
// prefeitura vai para a aba Prefeitura, o CEO para o painel dele, e qualquer outra pessoa entra como cidadão ---

const login = {email: null, then: null, back: 'more'};
const loginEmailForm = document.getElementById('login-email-form');
const loginCodeForm = document.getElementById('login-code-form');
const loginMessage = document.getElementById('login-message');

function setLoginMessage(text, kind) {
    loginMessage.textContent = text;
    loginMessage.className = `message ${kind ?? ''}`;
}

/**
 * Abre a tela de entrar; {@code then} roda depois que um cidadão entra (voltar ao relato, por exemplo).
 */
function openLogin(reason, then) {
    login.then = then ?? null;
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
    const minutes = Math.round((new Date(sent.expiresAt).getTime() - Date.now()) / 60_000);
    document.getElementById('login-code-hint').textContent = `Mandamos um código para ${email}. Ele vale por ${
        Math.max(1, minutes)} minutos. Não chegou? Veja a caixa de spam.`;
    showLoginStep('code');
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
    setLoginMessage('');
    showLoginStep('email');
    loginEmailForm.elements.email.focus();
});

document.getElementById('login-cancel').addEventListener('click', () => showView(login.back));

loginCodeForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = loginCodeForm.querySelector('[type="submit"]');
    submit.disabled = true;
    setLoginMessage('');
    try {
        // a identidade anônima de antes do login, se houver: os relatos dela passam para a conta
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

async function signedIn(result) {
    const then = login.then;
    login.then = null;
    if (result.role === 'CEO') {
        // a conta do CEO não é da equipe da prefeitura: ela abre o painel próprio, já conectada
        try {
            sessionStorage.setItem('ceo', JSON.stringify(result));
        } catch {
            // sem storage: o painel pede o login de novo
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
        saveSubscription(push.subscription).catch(() => {}); // os avisos deste aparelho passam para a conta
    }
    loadMyReports().catch((error) => setMyReportsMessage(error.message, 'error'));
    if (then) {
        then();
    } else {
        showView('explore');
        showToast(`Você entrou como ${result.email}.`);
    }
}

// Mais › Sua conta: quem está conectado neste aparelho
function renderAccount() {
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
// conectado, a inicial abre Mais, onde ficam a conta e o Sair
document.getElementById('account-chip').addEventListener('click', () => {
    if (state.session || citizenSignedIn()) {
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
    // os avisos deste aparelho param de chegar: quem sai não deve receber notícias da conta
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
            // sem rede: a inscrição fica, mas a sessão sai mesmo assim
        }
    }
    saveReporter(null);
    renderAccount();
    loadMyReports().catch(() => {});
    showToast('Você saiu da sua conta neste aparelho.');
});

document.getElementById('logout').addEventListener('click', () => saveSession(null));

async function loadCategories() {
    state.categories = await api('/categories');
    const options = state.categories.map((c) => `<option value="${c.code}">${escapeHtml(c.name)}</option>`).join('');
    document.getElementById('queue-category').insertAdjacentHTML('beforeend', options);
    document.getElementById('export-category').insertAdjacentHTML('beforeend', options);
    renderCategoryChips();
    renderCategoryTiles();
}

map.on('autopanstart', () => {
    state.autoPanning = true;
});
map.on('moveend', scheduleIssueLoad);

map.whenReady(() => map.invalidateSize());
renderSession();
showView('explore');
// Ao abrir, o mapa vai até você e passa a seguir, como no app de navegação. Quem chega por um link de chamado
// (a notificação) vê o chamado, e a seta aparece sem mover o mapa.
if (geolocationAvailable()) {
    setFollow(!new URLSearchParams(location.search).has('issue'));
    geolocationPermission().then((permission) => {
        if (permission !== 'denied') {
            startTracking();
            enableCompass();
        } else {
            setFollow(false);
            locateButton.classList.add('unavailable');
        }
    });
} else {
    locateButton.classList.add('unavailable');
}
Promise.all([loadCategories(), loadDistricts(), loadIssues()])
    .then(openLinkedIssue)
    .then(openShortcut)
    .catch((error) => setReportMessage(`Não foi possível carregar o mapa: ${error.message}`, 'error'));
loadMyReports().then(setupPush).catch((error) => setMyReportsMessage(error.message, 'error'));
