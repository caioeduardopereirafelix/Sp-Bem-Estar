const API = '/api/v1';
const CITY = 'São Paulo';

// Status é um estado: paleta de status + formato do marcador, para não depender só da cor
const STATUS = {
    OPEN: {label: 'Aberto', color: '#d03b3b', shape: 'circle'},
    CONFIRMED: {label: 'Confirmado', color: '#ec835a', shape: 'square'},
    IN_PROGRESS: {label: 'Em andamento', color: '#fab219', shape: 'diamond'},
    RESOLVED: {label: 'Resolvido', color: '#0ca30c', shape: 'triangle'},
};

const TRANSITIONS = {
    OPEN: ['CONFIRMED', 'IN_PROGRESS', 'RESOLVED'],
    CONFIRMED: ['IN_PROGRESS', 'RESOLVED'],
    IN_PROGRESS: ['RESOLVED'],
    RESOLVED: [],
};

// Escala sequencial de um tom (azul), do claro ao escuro
const RATE_COLORS = ['#cde2fb', '#86b6ef', '#3987e5', '#1c5cab', '#0d366b'];

const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
const numberFormat = new Intl.NumberFormat('pt-BR');
const rateFormat = new Intl.NumberFormat('pt-BR', {minimumFractionDigits: 2, maximumFractionDigits: 2});
const dateFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'short'});

const state = {categories: [], rateBreaks: [], pickedLocation: null, pickMarker: null, loadTimer: null,
    session: loadSession(), reporter: null, markers: new Map(), userLocation: null};

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

const map = L.map('map', {zoomControl: true}).setView([-23.55, -46.63], 11);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
}).addTo(map);

map.createPane('districts').style.zIndex = 350;
const districtLayer = L.geoJSON(null, {pane: 'districts', style: districtStyle, onEachFeature: bindDistrict}).addTo(map);
const issueLayer = L.layerGroup().addTo(map);
L.control.layers(null, {'Distritos': districtLayer, 'Chamados': issueLayer}, {collapsed: false}).addTo(map);
const legend = L.control({position: 'bottomleft'});
legend.onAdd = () => L.DomUtil.create('div', 'legend');
legend.addTo(map);

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
        throw new Error('Sua sessão expirou. Entre de novo na área do operador.');
    }
    if (!response.ok) {
        throw new Error(body?.detail || body?.title || `Erro ${response.status}`);
    }
    return body;
}

// Identidade anônima do cidadão: um JWT de 15 min para enviar relatos, renovado com o refresh token.
// O par fica neste navegador para o mesmo cidadão ser reconhecido nas próximas visitas.
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
        localStorage.setItem(REPORTER_KEY, JSON.stringify(tokens));
    } catch {
        // sem storage (aba anônima, bloqueio): a identidade vale até recarregar a página
    }
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
            saveReporter(payload);
            return payload.accessToken;
        }
        if (response.status === 403) {
            throw new Error(payload?.detail ?? 'Este autor foi bloqueado pela moderação.');
        }
        // 401: vencido ou revogado; segue para uma identidade nova
    }
    const {response, payload} = await postReporter('/reporters');
    if (!response.ok) {
        throw new Error(payload?.detail || payload?.title || `Erro ${response.status}`);
    }
    saveReporter(payload);
    return payload.accessToken;
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

async function sendReport(data) {
    for (const forceRenew of [false, true]) {
        const token = await reporterToken(forceRenew);
        const response = await fetch(`${API}/reports`, {
            method: 'POST',
            body: data,
            headers: {Authorization: `Bearer ${token}`},
        });
        const body = response.headers.get('content-type')?.includes('json') ? await response.json() : null;
        if (response.status === 401 && !forceRenew) {
            continue; // token recusado (vencido ou revogado): renova e tenta uma vez
        }
        if (!response.ok) {
            throw new Error(body?.detail || body?.title || `Erro ${response.status}`);
        }
        return body;
    }
}

// Localização do usuário pelo navegador. Fica só nesta página: a API recebe apenas o ponto de um relato
// enviado, como já acontecia ao clicar no mapa.
const LOW_ACCURACY_METERS = 50;
const GEOLOCATION_ERRORS = {
    1: 'Sem permissão para usar sua localização. Libere nas configurações do navegador ou escolha o local no mapa.',
    2: 'Sua localização não está disponível agora. Escolha o local no mapa.',
    3: 'A localização demorou demais. Tente de novo ou escolha o local no mapa.',
};

function currentPosition() {
    return new Promise((resolve, reject) => {
        if (!window.isSecureContext || !('geolocation' in navigator)) {
            reject(new Error('Este navegador não informa a localização aqui (é preciso HTTPS). Escolha o local no mapa.'));
            return;
        }
        navigator.geolocation.getCurrentPosition(resolve,
            (error) => reject(new Error(GEOLOCATION_ERRORS[error.code] ?? 'Não foi possível obter sua localização.')),
            {enableHighAccuracy: true, timeout: 15000, maximumAge: 60000});
    });
}

// Ponto azul com o círculo de precisão, como nos apps de mapa
function showUserLocation(position) {
    const latlng = L.latLng(position.coords.latitude, position.coords.longitude);
    state.userLocation?.remove();
    const accuracy = L.circle(latlng, {radius: position.coords.accuracy, color: '#1a73e8', weight: 1,
        fillOpacity: 0.12, interactive: false});
    state.userLocation = L.layerGroup([
        accuracy,
        L.circleMarker(latlng, {radius: 7, color: '#fff', weight: 2, fillColor: '#1a73e8', fillOpacity: 1})
            .bindTooltip('Você está aqui'),
    ]).addTo(map);
    // com GPS preciso, aproxima até a rua; com precisão baixa, mostra o círculo inteiro
    map.fitBounds(accuracy.getBounds(), {maxZoom: 17, animate: false});
    return latlng;
}

const locateControl = L.control({position: 'topleft'});
locateControl.onAdd = () => {
    const container = L.DomUtil.create('div', 'leaflet-bar leaflet-control locate-control');
    container.innerHTML = `<button type="button" title="Mostrar minha localização" aria-label="Mostrar minha localização">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <circle cx="12" cy="12" r="4"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/><circle cx="12" cy="12" r="8"/>
        </svg></button>`;
    const button = container.querySelector('button');
    L.DomEvent.disableClickPropagation(container);
    button.addEventListener('click', async () => {
        button.disabled = true;
        try {
            showUserLocation(await currentPosition());
        } catch (error) {
            setReportMessage(error.message, 'error');
        } finally {
            button.disabled = false;
        }
    });
    return container;
};
locateControl.addTo(map);

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

// "5 relatos de 2 pessoas": a prioridade conta pessoas, e quem reporta de novo não soma
function reportsLabel(reports, reporters) {
    const text = `${numberFormat.format(reports)} relato${reports === 1 ? '' : 's'}`;
    return reporters === reports ? text
        : `${text} de ${numberFormat.format(reporters)} pessoa${reporters === 1 ? '' : 's'}`;
}

function statusLabel(status) {
    return `<span class="status-label">${shapeSvg(status, 14)}${STATUS[status].label}</span>`;
}

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
    layer.bindTooltip(`<strong>${escapeHtml(p.name)}</strong><br>
        ${numberFormat.format(p.population ?? 0)} habitantes<br>
        ${numberFormat.format(p.openIssues)} chamados abertos<br>
        ${rateFormat.format(p.openIssuesPer10k ?? 0)} por 10 mil hab.`, {sticky: true});
    layer.on({
        mouseover: () => layer.setStyle({weight: 3, color: '#0b0b0b'}),
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
    const rateRows = breaks.length === 1
        ? `<div class="legend-row"><span class="swatch" data-color="0"></span>Nenhum chamado aberto</div>`
        : breaks.map((limit, i) => {
            const text = i === 0 ? 'Nenhum' : `até ${rateFormat.format(limit)}`;
            return `<div class="legend-row"><span class="swatch" data-color="${i}"></span>${text}</div>`;
        }).join('');
    const statusRows = Object.keys(STATUS)
        .map((s) => `<div class="legend-row">${shapeSvg(s, 14)}${STATUS[s].label}</div>`).join('');
    const container = legend.getContainer();
    const wasOpen = container.querySelector('details')?.open ?? !window.matchMedia('(max-width: 767px)').matches;
    container.innerHTML = `<details ${wasOpen ? 'open' : ''}><summary>Legenda</summary>
        <div class="legend-section"><h3>Chamados abertos por 10 mil hab.</h3>${rateRows}</div>
        <div class="legend-section"><h3>Status do chamado</h3>${statusRows}</div></details>`;
    // a cor vai pelo DOM, não por atributo style: a CSP da página bloqueia estilos inline
    container.querySelectorAll('.swatch[data-color]').forEach((swatch) => {
        swatch.style.background = RATE_COLORS[swatch.dataset.color];
    });
}

async function loadDistricts() {
    const collection = await api(`/neighborhoods?city=${encodeURIComponent(CITY)}`);
    state.rateBreaks = computeRateBreaks(collection.features);
    districtLayer.clearLayers().addData(collection);
    renderLegend();
    renderRanking(collection.features.map((f) => f.properties));
}

function renderRanking(stats) {
    const rows = stats.filter((s) => s.openIssues > 0).slice(0, 10);
    document.getElementById('ranking-body').innerHTML = rows.length === 0
        ? '<tr><td colspan="3">Nenhum chamado aberto ainda.</td></tr>'
        : rows.map((s) => `<tr><td>${escapeHtml(s.name)}</td><td>${numberFormat.format(s.openIssues)}</td>
            <td>${rateFormat.format(s.openIssuesPer10k ?? 0)}</td></tr>`).join('');
}

function scheduleIssueLoad() {
    // o Leaflet desloca o mapa para o popup caber na tela; recarregar aqui recriaria o marcador e fecharia o popup
    if (state.autoPanning) {
        state.autoPanning = false;
        return;
    }
    clearTimeout(state.loadTimer);
    state.loadTimer = setTimeout(loadIssues, 250);
}

async function loadIssues() {
    const bounds = map.getBounds();
    if (bounds.getWest() >= bounds.getEast() || bounds.getSouth() >= bounds.getNorth()) {
        return;
    }
    const params = new URLSearchParams({
        bbox: [bounds.getWest(), bounds.getSouth(), bounds.getEast(), bounds.getNorth()].map((v) => v.toFixed(6)).join(','),
    });
    const category = document.getElementById('filter-category').value;
    const status = document.getElementById('filter-status').value;
    if (category) params.set('category', category);
    if (status) params.set('status', status);

    const collection = await api(`/issues?${params}`);
    issueLayer.clearLayers();
    state.markers.clear();
    for (const feature of collection.features) {
        const [lon, lat] = feature.geometry.coordinates;
        const p = feature.properties;
        const marker = L.marker([lat, lon], {
            icon: L.divIcon({className: '', html: shapeSvg(p.status, 22), iconSize: [22, 22]}),
            title: `${p.categoryName} · ${STATUS[p.status].label}`,
            keyboard: true,
        });
        marker.bindTooltip(`${escapeHtml(p.categoryName)} · ${STATUS[p.status].label}<br>${p.reportCount} relato(s)`);
        marker.bindPopup('Carregando…', {maxWidth: 320});
        marker.on('popupopen', () => showIssue(feature.id, marker));
        issueLayer.addLayer(marker);
        state.markers.set(feature.id, marker);
    }
    const count = collection.features.length;
    document.getElementById('issue-count').textContent =
        `${numberFormat.format(count)} chamado(s) na área visível${count >= 500 ? ' (limite de 500)' : ''}.`;
}

async function showIssue(id, marker) {
    try {
        const issue = await api(`/issues/${id}`);
        const photos = issue.reports.slice(0, 4).map((r) =>
            `<a href="${r.photoUrl}" target="_blank" rel="noopener"><img src="${r.photoUrl}" alt="Foto do relato ${r.id}" loading="lazy"></a>`).join('');
        const history = issue.history.map((h) =>
            `<li>${STATUS[h.from]?.label ?? '—'} → ${STATUS[h.to].label} · ${dateFormat.format(new Date(h.changedAt))}${
                h.changedBy ? ` · ${escapeHtml(h.changedBy)}` : ''}</li>`).join('');
        const actions = state.session ? TRANSITIONS[issue.status].map((s) =>
            `<button type="button" class="button" data-status="${s}">${STATUS[s].label}</button>`).join('') : '';
        const canChange = TRANSITIONS[issue.status].length > 0;
        const moderation = state.session ? issue.reports.slice(0, 4).map((r) =>
            `<button type="button" class="button" data-block-report="${r.id}">Bloquear autor do relato #${r.id}</button>`).join('') : '';

        marker.setPopupContent(`<div class="issue-popup">
            <h3>${escapeHtml(issue.categoryName)} · #${issue.id}</h3>
            ${statusLabel(issue.status)}
            <dl>
                <dt>Relatos</dt><dd>${reportsLabel(issue.reportCount, issue.reporterCount)}</dd>
                <dt>Prioridade</dt><dd>${Number(issue.priorityScore).toFixed(2)}</dd>
                <dt>Distrito</dt><dd>${escapeHtml(issue.neighborhood ?? 'Fora dos distritos importados')}</dd>
                <dt>1º relato</dt><dd>${dateFormat.format(new Date(issue.firstReportedAt))}</dd>
                <dt>Último</dt><dd>${dateFormat.format(new Date(issue.lastReportedAt))}</dd>
            </dl>
            ${photos ? `<div class="photos">${photos}</div>` : ''}
            ${history ? `<strong>Histórico</strong><ul class="history">${history}</ul>` : ''}
            ${actions ? `<strong>Alterar status</strong>
                <div class="popup-actions">${actions}</div>` : ''}
            ${canChange && !state.session ? '<p class="hint">Operadores alteram o status pela área do operador.</p>' : ''}
            ${moderation ? `<strong>Moderação</strong>
                <div class="popup-actions">${moderation}</div>` : ''}
        </div>`);

        marker.getPopup().getElement()?.querySelectorAll('[data-status]').forEach((button) => {
            button.addEventListener('click', () => changeStatus(issue.id, button.dataset.status, marker));
        });
        marker.getPopup().getElement()?.querySelectorAll('[data-block-report]').forEach((button) => {
            button.addEventListener('click', () => blockReporter(button.dataset.blockReport, button));
        });
    } catch (error) {
        marker.setPopupContent(`<p class="message error">${escapeHtml(error.message)}</p>`);
    }
}

async function blockReporter(reportId, button) {
    if (!confirm(`Bloquear quem enviou o relato #${reportId}? Ele não poderá enviar novos relatos.`)) {
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

async function changeStatus(id, status, marker) {
    try {
        await api(`/issues/${id}/status`, {
            method: 'PATCH',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({status}),
        });
        marker.closePopup();
        await Promise.all([loadIssues(), loadDistricts(), loadQueue()]);
    } catch (error) {
        marker.setPopupContent(`<p class="message error">${escapeHtml(error.message)}</p>`);
    }
}

// Fluxo de novo relato: escolher o ponto no mapa, depois categoria e foto
const reportForm = document.getElementById('report-form');
const reportMessage = document.getElementById('report-message');

function setReportMessage(text, kind) {
    reportMessage.textContent = text;
    reportMessage.className = `message ${kind ?? ''}`;
}

function resetReport() {
    state.pickedLocation = null;
    state.pickMarker?.remove();
    state.pickMarker = null;
    reportForm.reset();
    reportForm.hidden = true;
    document.getElementById('report-hint').hidden = true;
    document.getElementById('report-adjust').hidden = true;
    document.getElementById('report-choice').hidden = false;
    document.body.classList.remove('picking');
}

// Ponto do relato: o marcador pode ser arrastado, e um clique no mapa o move
function pickLocation(latlng) {
    state.pickedLocation = latlng;
    state.pickMarker?.remove();
    state.pickMarker = L.marker(latlng, {draggable: true, title: 'Local do relato'}).addTo(map);
    state.pickMarker.on('dragend', () => {
        state.pickedLocation = state.pickMarker.getLatLng();
    });
    document.body.classList.add('picking');
    document.getElementById('report-choice').hidden = true;
    document.getElementById('report-hint').hidden = true;
    document.getElementById('report-adjust').hidden = false;
    reportForm.hidden = false;
}

function startManualPick(message = '', kind) {
    setReportMessage(message, kind);
    document.getElementById('report-choice').hidden = true;
    document.getElementById('report-hint').hidden = false;
    document.body.classList.add('picking');
}

async function locateForReport() {
    setReportMessage('Buscando sua localização…');
    try {
        const position = await currentPosition();
        pickLocation(showUserLocation(position));
        const accuracy = Math.round(position.coords.accuracy);
        setReportMessage(accuracy > LOW_ACCURACY_METERS
            ? `Localização aproximada (cerca de ${numberFormat.format(accuracy)} m). Confira o marcador antes de enviar.`
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

const locationDialog = document.getElementById('location-dialog');
const LOCATION_DIALOG = {
    prompt: {
        title: 'Permitir acesso à localização?',
        text: 'Para registrar o problema no lugar certo, o mapa usa a localização do seu aparelho. '
            + 'Ela só marca o ponto do relato e não é guardada. Na próxima tela, o navegador vai pedir sua permissão.',
        allow: true,
    },
    denied: {
        title: 'Localização bloqueada',
        text: 'O acesso à localização está bloqueado para este site. Para liberar, toque no cadeado ao lado do '
            + 'endereço e permita a localização. Você também pode escolher o ponto no mapa.',
        allow: false,
    },
    unsupported: {
        title: 'Localização indisponível',
        text: 'Este navegador não informa a localização nesta página (é preciso HTTPS). Escolha o ponto no mapa.',
        allow: false,
    },
};

document.getElementById('report-begin').addEventListener('click', async () => {
    setReportMessage('');
    const permission = await geolocationPermission();
    if (permission === 'granted') {
        await locateForReport(); // já permitido: vai direto para o ponto do GPS
        return;
    }
    const content = LOCATION_DIALOG[permission] ?? LOCATION_DIALOG.prompt;
    document.getElementById('location-dialog-title').textContent = content.title;
    document.getElementById('location-dialog-text').textContent = content.text;
    document.getElementById('location-allow').hidden = !content.allow;
    locationDialog.showModal();
    (content.allow ? document.getElementById('location-allow') : document.getElementById('location-manual')).focus();
});

document.getElementById('location-allow').addEventListener('click', () => {
    locationDialog.close();
    locateForReport(); // aqui o navegador mostra o pedido de permissão do aparelho
});

document.getElementById('location-manual').addEventListener('click', () => {
    locationDialog.close();
    startManualPick();
});

document.getElementById('report-cancel').addEventListener('click', resetReport);

map.on('click', (event) => {
    if (document.body.classList.contains('picking')) {
        pickLocation(event.latlng);
    }
});

reportForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = reportForm.querySelector('[type="submit"]');
    const data = new FormData(reportForm);
    data.set('latitude', state.pickedLocation.lat.toFixed(6));
    data.set('longitude', state.pickedLocation.lng.toFixed(6));

    submit.disabled = true;
    try {
        const result = await sendReport(data);
        resetReport();
        setReportMessage(result.newIssue
            ? `Relato registrado: novo chamado #${result.issueId}.`
            : `Relato agrupado ao chamado #${result.issueId}, que agora tem ${result.issueReportCount} relatos.`, 'success');
        await Promise.all([loadIssues(), loadDistricts()]);
    } catch (error) {
        setReportMessage(error.message, 'error');
    } finally {
        submit.disabled = false;
    }
});

// Área do operador
const loginForm = document.getElementById('login-form');
const loginMessage = document.getElementById('login-message');

function renderSession() {
    const session = state.session;
    loginForm.hidden = Boolean(session);
    document.getElementById('operator-hint').hidden = Boolean(session);
    document.getElementById('operator-session').hidden = !session;
    document.getElementById('operator-name').textContent = session
        ? `Conectado como ${session.name}. Abra um chamado no mapa para alterar o status.` : '';
    document.getElementById('queue-card').hidden = !session;
    document.getElementById('moderation-card').hidden = !session;
    map.closePopup();
    if (session) {
        loadOperatorQueues();
    }
}

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

// Leva o mapa até o chamado e abre o popup, onde ficam as ações de status e de moderação
async function focusIssue(item) {
    map.setView([item.latitude, item.longitude], 18, {animate: false});
    clearTimeout(state.loadTimer); // o moveend agendou um recarregamento que fecharia o popup
    await loadIssues();
    state.markers.get(item.id)?.openPopup();
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
document.getElementById('moderation-more').addEventListener('click', () => loadModeration(true));

loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = loginForm.querySelector('[type="submit"]');
    const data = new FormData(loginForm);
    submit.disabled = true;
    loginMessage.textContent = '';
    loginMessage.className = 'message';
    try {
        const session = await api('/auth/login', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({email: data.get('email'), password: data.get('password')}),
        });
        loginForm.reset();
        saveSession(session);
    } catch (error) {
        loginMessage.textContent = error.message;
        loginMessage.className = 'message error';
    } finally {
        submit.disabled = false;
    }
});

document.getElementById('logout').addEventListener('click', () => saveSession(null));

async function loadCategories() {
    state.categories = await api('/categories');
    const options = state.categories.map((c) => `<option value="${c.code}">${escapeHtml(c.name)}</option>`).join('');
    document.getElementById('filter-category').insertAdjacentHTML('beforeend', options);
    document.getElementById('queue-category').insertAdjacentHTML('beforeend', options);
    document.getElementById('report-category').innerHTML = options;
}

document.getElementById('filter-category').addEventListener('change', loadIssues);
document.getElementById('filter-status').addEventListener('change', loadIssues);
map.on('autopanstart', () => {
    state.autoPanning = true;
});
map.on('moveend', scheduleIssueLoad);

map.whenReady(() => map.invalidateSize());
renderSession();
Promise.all([loadCategories(), loadDistricts(), loadIssues()])
    .catch((error) => setReportMessage(`Não foi possível carregar o mapa: ${error.message}`, 'error'));
