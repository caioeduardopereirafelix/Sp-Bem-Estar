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
    session: loadSession()};

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
            `<li>${STATUS[h.from]?.label ?? '—'} → ${STATUS[h.to].label} · ${dateFormat.format(new Date(h.changedAt))}</li>`).join('');
        const actions = state.session ? TRANSITIONS[issue.status].map((s) =>
            `<button type="button" class="button" data-status="${s}">${STATUS[s].label}</button>`).join('') : '';
        const canChange = TRANSITIONS[issue.status].length > 0;

        marker.setPopupContent(`<div class="issue-popup">
            <h3>${escapeHtml(issue.categoryName)} · #${issue.id}</h3>
            ${statusLabel(issue.status)}
            <dl>
                <dt>Relatos</dt><dd>${issue.reportCount}</dd>
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
        </div>`);

        marker.getPopup().getElement()?.querySelectorAll('[data-status]').forEach((button) => {
            button.addEventListener('click', () => changeStatus(issue.id, button.dataset.status, marker));
        });
    } catch (error) {
        marker.setPopupContent(`<p class="message error">${escapeHtml(error.message)}</p>`);
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
        await Promise.all([loadIssues(), loadDistricts()]);
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
    document.getElementById('report-start').hidden = false;
    document.body.classList.remove('picking');
}

document.getElementById('report-start').addEventListener('click', () => {
    setReportMessage('');
    document.getElementById('report-start').hidden = true;
    document.getElementById('report-hint').hidden = false;
    document.body.classList.add('picking');
});

document.getElementById('report-cancel').addEventListener('click', resetReport);

map.on('click', (event) => {
    if (!document.body.classList.contains('picking')) {
        return;
    }
    state.pickedLocation = event.latlng;
    state.pickMarker?.remove();
    state.pickMarker = L.marker(event.latlng).addTo(map);
    document.getElementById('report-hint').hidden = true;
    reportForm.hidden = false;
});

reportForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = reportForm.querySelector('[type="submit"]');
    const data = new FormData(reportForm);
    data.set('latitude', state.pickedLocation.lat.toFixed(6));
    data.set('longitude', state.pickedLocation.lng.toFixed(6));

    submit.disabled = true;
    try {
        const result = await api('/reports', {method: 'POST', body: data});
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
    map.closePopup();
}

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
    document.getElementById('report-category').innerHTML = options;
}

document.getElementById('filter-category').addEventListener('change', loadIssues);
document.getElementById('filter-status').addEventListener('change', loadIssues);
map.on('moveend', scheduleIssueLoad);

map.whenReady(() => map.invalidateSize());
renderSession();
Promise.all([loadCategories(), loadDistricts(), loadIssues()])
    .catch((error) => setReportMessage(`Não foi possível carregar o mapa: ${error.message}`, 'error'));
