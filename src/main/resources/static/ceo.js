// Painel do CEO: uso do produto, saúde de cada parte e os erros mais recentes. Só a conta com o papel CEO entra
const API = '/api/v1';
const SESSION_KEY = 'ceo';
const REFRESH_MS = 60_000;

const state = {session: readSession(), timer: null, loading: false};

const numberFormat = new Intl.NumberFormat('pt-BR');
const decimalFormat = new Intl.NumberFormat('pt-BR', {maximumFractionDigits: 1});
const timeFormat = new Intl.DateTimeFormat('pt-BR', {hour: '2-digit', minute: '2-digit', second: '2-digit'});
const dateTimeFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'medium'});
const dayFormat = new Intl.DateTimeFormat('pt-BR', {day: '2-digit', month: '2-digit', timeZone: 'UTC'});
const weekdayFormat = new Intl.DateTimeFormat('pt-BR', {weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC'});

const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g,
    (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

const OPEN_STATUS = {OPEN: 'Aberto', CONFIRMED: 'Confirmado', IN_PROGRESS: 'Em andamento'};
const HEALTH = {
    UP: {tone: 'good', label: 'Funcionando'},
    DEGRADED: {tone: 'warning', label: 'Atrasado'},
    DOWN: {tone: 'critical', label: 'Fora do ar'},
    OUT_OF_SERVICE: {tone: 'critical', label: 'Fora de serviço'},
    UNREACHABLE: {tone: 'critical', label: 'Não respondeu'},
    NOT_CONFIGURED: {tone: 'neutral', label: 'Não configurado'},
};
const TONE_ICONS = {good: '✓', warning: '!', critical: '✕', neutral: '–'};
const PHOTO_CHECKS = {
    AGREED: 'A foto confere com o tipo',
    MISMATCH: 'O modelo viu outro tipo',
    LOW_CONFIDENCE: 'O modelo ficou em dúvida',
    PENDING: 'Ainda não conferidas',
};
const PRECHECK = {
    passed: 'Fotos aceitas na hora',
    rejected: 'Recusadas por ser de outro tipo',
    insisted: 'Enviadas mesmo assim',
    unavailable: 'Conferência fora do ar (aceitas)',
};

// --- Sessão e login ---

function readSession() {
    try {
        return JSON.parse(sessionStorage.getItem(SESSION_KEY));
    } catch {
        return null;
    }
}

function saveSession(session) {
    state.session = session;
    try {
        if (session) {
            sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
        } else {
            sessionStorage.removeItem(SESSION_KEY);
        }
    } catch {
        // sem storage (aba anônima, bloqueio): a sessão vale até recarregar a página
    }
    render();
}

async function api(path, options = {}) {
    const headers = {...options.headers};
    if (state.session) {
        headers.Authorization = `Bearer ${state.session.accessToken}`;
    }
    const response = await fetch(API + path, {...options, headers});
    const body = response.headers.get('content-type')?.includes('json') ? await response.json() : null;
    if ((response.status === 401 || response.status === 403) && state.session) {
        saveSession(null);
        throw new Error('Sua sessão terminou. Entre de novo.');
    }
    if (!response.ok) {
        throw new Error(body?.detail || body?.title || `Erro ${response.status}`);
    }
    return body;
}

function render() {
    const signedIn = state.session?.role === 'CEO';
    document.getElementById('login').hidden = signedIn;
    document.getElementById('dashboard').hidden = !signedIn;
    document.getElementById('logout').hidden = !signedIn;
    if (signedIn) {
        loadDashboard();
    }
    syncRefresh();
}

const loginForm = document.getElementById('login-form');
const loginMessage = document.getElementById('login-message');
loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = loginForm.querySelector('[type="submit"]');
    const data = new FormData(loginForm);
    submit.disabled = true;
    loginMessage.className = 'message';
    loginMessage.textContent = '';
    try {
        const session = await api('/auth/login', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({email: data.get('email'), password: data.get('password')}),
        });
        if (session.role !== 'CEO') {
            // a API também recusaria (403): aqui só se evita guardar um token que não serve para esta página
            throw new Error('Esta conta é da equipe da prefeitura. O painel é só da conta do CEO.');
        }
        loginForm.reset();
        saveSession(session);
    } catch (error) {
        loginMessage.className = 'message error';
        loginMessage.textContent = error.message;
    } finally {
        submit.disabled = false;
    }
});

document.getElementById('logout').addEventListener('click', () => saveSession(null));

const passwordForm = document.getElementById('password-form');
const passwordMessage = document.getElementById('password-message');
passwordForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const data = new FormData(passwordForm);
    if (data.get('newPassword') !== data.get('confirmPassword')) {
        passwordMessage.className = 'message error';
        passwordMessage.textContent = 'A senha nova e a repetição não são iguais.';
        return;
    }
    const submit = passwordForm.querySelector('[type="submit"]');
    submit.disabled = true;
    try {
        const session = await api('/auth/password', {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({currentPassword: data.get('currentPassword'), newPassword: data.get('newPassword')}),
        });
        passwordForm.reset();
        state.session = session; // o token antigo deixou de valer: segue com o novo
        try {
            sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
        } catch {
            // sem storage: segue só na memória
        }
        passwordMessage.className = 'message success';
        passwordMessage.textContent = 'Senha trocada. As outras sessões foram encerradas.';
    } catch (error) {
        passwordMessage.className = 'message error';
        passwordMessage.textContent = error.message;
    } finally {
        submit.disabled = false;
    }
});

// --- Painel ---

function healthOf(status) {
    return HEALTH[status] ?? {tone: 'warning', label: status ? `Status ${status}` : 'Desconhecido'};
}

function toneBadge(tone, label) {
    return `<span class="dash-state tone-${tone}"><span class="dash-icon" aria-hidden="true">${TONE_ICONS[tone]}</span>${
        escapeHtml(label)}</span>`;
}

function tile(label, value, detail = '') {
    return `<div class="dash-tile"><span class="dash-tile-label">${escapeHtml(label)}</span>
        <strong class="dash-tile-value">${value}</strong>${detail ? `<span class="dash-tile-detail">${detail}</span>` : ''}</div>`;
}

function durationLabel(hours) {
    if (hours == null) return '—';
    if (hours < 1) return `${Math.max(1, Math.round(hours * 60))} min`;
    if (hours < 48) return `${decimalFormat.format(hours)} h`;
    return `${decimalFormat.format(hours / 24)} dias`;
}

function uptimeLabel(seconds) {
    const days = Math.floor(seconds / 86_400);
    const hours = Math.floor((seconds % 86_400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (days) return `${days} d ${hours} h`;
    if (hours) return `${hours} h ${minutes} min`;
    return `${minutes} min`;
}

function countList(entries, empty) {
    return entries.length === 0 ? `<li class="hint">${empty}</li>`
        : entries.map(([label, count]) => `<li><span>${label}</span><strong>${numberFormat.format(count)}</strong></li>`).join('');
}

// A CSP não aceita style="" no HTML: o tamanho de cada barra vai pelo CSSOM, que ela permite
function sizeBars(root, property) {
    root.querySelectorAll('[data-size]').forEach((bar) => {
        bar.style[property] = `${bar.dataset.size}%`;
    });
}

// Lista com uma barra fina proporcional ao primeiro (o maior); o número fica sempre escrito ao lado
function rankedList(items) {
    if (items.length === 0) return '<li class="hint">Nenhum chamado em aberto.</li>';
    const max = items[0].count || 1;
    return items.map((item) => `<li><span>${escapeHtml(item.name)}</span><strong>${numberFormat.format(item.count)}</strong>
        <span class="dash-bar" data-size="${Math.max(4, (item.count / max) * 100)}"></span></li>`).join('');
}

// Colunas em HTML (e não um SVG esticado): o texto fica nítido em qualquer largura
function renderDailyChart(days) {
    const plot = document.getElementById('dash-chart');
    const max = Math.max(...days.map((d) => d.count), 0);
    const top = Math.max(max, 1);
    const total = days.reduce((sum, d) => sum + d.count, 0);
    const peak = days.findIndex((d) => d.count === max);
    plot.setAttribute('role', 'img');
    plot.setAttribute('aria-label', `${numberFormat.format(total)} relatos nos últimos 14 dias; o dia com mais teve ${
        numberFormat.format(max)}. A tabela abaixo tem os números de cada dia.`);
    plot.innerHTML = `<span class="dash-axis-max" aria-hidden="true">${numberFormat.format(top)}</span>
        <div class="dash-columns-plot" aria-hidden="true">${days.map((d, i) => {
            const date = new Date(`${d.day}T00:00:00Z`);
            const labelled = max > 0 && (i === peak || i === days.length - 1);
            return `<div class="dash-column" data-index="${i}">
                <span class="dash-column-bar ${d.count ? '' : 'zero'}" data-size="${(d.count / top) * 100}">${
                    labelled ? `<span class="dash-column-value">${numberFormat.format(d.count)}</span>` : ''}</span>
                <span class="dash-column-day">${i % 2 === days.length % 2 ? '' : date.getUTCDate()}</span>
            </div>`;
        }).join('')}</div>
        <div class="dash-tooltip" hidden></div>`;
    sizeBars(plot, 'height');
    const tooltip = plot.querySelector('.dash-tooltip');
    plot.querySelectorAll('.dash-column').forEach((column) => {
        column.addEventListener('pointerenter', () => {
            const d = days[column.dataset.index];
            tooltip.innerHTML = `<strong>${numberFormat.format(d.count)} relato${d.count === 1 ? '' : 's'}</strong>${
                weekdayFormat.format(new Date(`${d.day}T00:00:00Z`))}`;
            tooltip.hidden = false;
            const left = column.offsetLeft + column.offsetWidth / 2;
            tooltip.style.left = `${Math.min(Math.max(left, 70), plot.clientWidth - 70)}px`;
            column.classList.add('active');
        });
        column.addEventListener('pointerleave', () => {
            tooltip.hidden = true;
            column.classList.remove('active');
        });
    });
    document.getElementById('dash-chart-table').innerHTML = `<thead><tr><th scope="col">Dia</th>
        <th scope="col">Relatos</th></tr></thead><tbody>${days.map((d) => `<tr><td>${
            dayFormat.format(new Date(`${d.day}T00:00:00Z`))}</td><td>${numberFormat.format(d.count)}</td></tr>`).join('')}</tbody>`;
}

function renderDashboard(data) {
    const {product, health, activity} = data;
    const parts = health.components.concat({label: 'Classificador de fotos (worker)', status: health.worker.status});
    const problems = parts.filter((c) => healthOf(c.status).tone === 'critical').map((c) => c.label);
    const warnings = parts.filter((c) => healthOf(c.status).tone === 'warning')
        .map((c) => `${c.label.toLowerCase()} ${healthOf(c.status).label.toLowerCase()}`);
    const deadLetters = Object.values(health.queues.deadLetters).reduce((sum, n) => sum + n, 0);
    if (deadLetters) warnings.push(`${numberFormat.format(deadLetters)} mensagem(ns) com falha nas filas`);
    if (health.queues.outboxOldestSeconds > 300) warnings.push('envio para o classificador atrasado');
    document.getElementById('dash-health').innerHTML = problems.length
        ? toneBadge('critical', `Com problema: ${problems.join(', ')}`)
        : warnings.length ? toneBadge('warning', `Funcionando, mas com ${warnings.join(' e ')}`)
            : toneBadge('good', 'Tudo funcionando');

    const openTotal = Object.values(product.openByStatus).reduce((sum, n) => sum + n, 0);
    document.getElementById('dash-product').innerHTML = [
        tile('Relatos hoje', numberFormat.format(product.reportsToday)),
        tile('Últimos 7 dias', numberFormat.format(product.reports7d)),
        tile('Últimos 30 dias', numberFormat.format(product.reports30d), `${numberFormat.format(product.reportsTotal)} desde o início`),
        tile('Pessoas que relataram', numberFormat.format(product.reporters30d), 'em 30 dias, sem identificar ninguém'),
        tile('Chamados em aberto', numberFormat.format(openTotal)),
        tile('Resolvidos em 30 dias', numberFormat.format(product.resolved30d), `${numberFormat.format(product.resolvedTotal)} desde o início`),
        tile('Tempo até resolver', durationLabel(product.medianResolutionHours30d), 'mediana, do 1º relato à resolução'),
        tile('Esperando moderação', numberFormat.format(product.moderationPending),
            `${numberFormat.format(product.rejected30d)} rejeitado(s) em 30 dias`),
        tile('Avisos no celular', numberFormat.format(product.pushSubscriptions), 'aparelhos inscritos'),
    ].join('');
    renderDailyChart(product.reportsByDay);
    document.getElementById('dash-open').innerHTML = countList(Object.entries(product.openByStatus)
        .map(([status, count]) => [`<span class="dash-status" data-status="${escapeHtml(status)}">${
            escapeHtml(OPEN_STATUS[status] ?? status)}</span>`, count]), '');
    document.getElementById('dash-photos').innerHTML = countList(Object.entries(product.photoChecks30d)
        .map(([status, count]) => [escapeHtml(PHOTO_CHECKS[status] ?? status), count]), 'Nenhum relato em 30 dias.');
    for (const [id, items] of [['dash-categories', product.topCategories], ['dash-neighborhoods', product.topNeighborhoods]]) {
        const list = document.getElementById(id);
        list.innerHTML = rankedList(items);
        sizeBars(list, 'width');
    }

    const worker = healthOf(health.worker.status);
    const classifier = health.worker.classifier ? ` · modelo: ${healthOf(health.worker.classifier).label.toLowerCase()}` : '';
    document.getElementById('dash-components').innerHTML = health.components
        .map((c) => `<li><span>${escapeHtml(c.label)}</span>${toneBadge(healthOf(c.status).tone, healthOf(c.status).label)}</li>`)
        .concat(`<li><span>Classificador de fotos (worker)</span>${toneBadge(worker.tone, worker.label + classifier)}</li>`)
        .concat(Object.entries(health.queues.deadLetters).map(([queueName, count]) => `<li><span>Falhas na fila
            <code>${escapeHtml(queueName)}</code></span>${toneBadge(count ? 'warning' : 'good',
                count ? `${numberFormat.format(count)} parada(s)` : 'Nenhuma')}</li>`))
        .join('');
    const {runtime, queues} = health;
    document.getElementById('dash-runtime').innerHTML = [
        tile('No ar há', uptimeLabel(runtime.uptimeSeconds), `desde ${dateTimeFormat.format(new Date(runtime.startedAt))}`),
        tile('Memória', `${numberFormat.format(runtime.heapUsedMb)} MB`,
            runtime.heapMaxMb > 0 ? `de ${numberFormat.format(runtime.heapMaxMb)} MB` : ''),
        tile('Processador', runtime.cpuUsage == null ? '—' : `${decimalFormat.format(runtime.cpuUsage * 100)}%`, 'uso da API'),
        tile('Fila do classificador', numberFormat.format(queues.outboxPending),
            queues.outboxPending ? `mais antigo há ${uptimeLabel(queues.outboxOldestSeconds)}` : 'nada esperando'),
    ].join('');

    const precheck = Object.entries(activity.precheck).filter(([, n]) => n > 0)
        .map(([outcome, n]) => `${escapeHtml(PRECHECK[outcome] ?? outcome)}: ${numberFormat.format(n)}`).join('<br>');
    document.getElementById('dash-activity').innerHTML = [
        tile('Requisições', numberFormat.format(activity.httpRequests),
            activity.httpMeanMs == null ? '' : `média de ${decimalFormat.format(activity.httpMeanMs)} ms`),
        tile('Erros do servidor (5xx)', numberFormat.format(activity.http5xx),
            `${numberFormat.format(activity.http4xx)} pedido(s) recusado(s) (4xx)`),
        tile('Logins errados', numberFormat.format(activity.loginFailures)),
        tile('Barrados por excesso', numberFormat.format(activity.rateLimited), 'limite de requisições'),
        tile('Conferência das fotos', numberFormat.format(Object.values(activity.precheck).reduce((s, n) => s + n, 0)),
            precheck),
    ].join('');

    document.getElementById('dash-errors').innerHTML = data.recentErrors.length === 0
        ? '<li class="hint">Nenhum erro inesperado desde que a API subiu.</li>'
        : data.recentErrors.map((e) => `<li>
            <strong>${escapeHtml(e.method)} ${escapeHtml(e.path)}</strong>
            <span class="dash-meta">${dateTimeFormat.format(new Date(e.at))} · ${escapeHtml(e.error)}</span>
            ${e.message ? `<span class="dash-error-message">${escapeHtml(e.message)}</span>` : ''}
            ${e.traceId ? `<span class="dash-meta">Rastreio: <code>${escapeHtml(e.traceId)}</code></span>` : ''}
        </li>`).join('');
    document.getElementById('dash-updated').textContent = `Atualizado às ${timeFormat.format(new Date(data.generatedAt))}.`
        + ' Atualiza sozinho a cada minuto.';
}

async function loadDashboard() {
    if (state.session?.role !== 'CEO' || state.loading) return;
    state.loading = true;
    const message = document.getElementById('dash-message');
    const button = document.getElementById('dash-refresh');
    button.disabled = true;
    try {
        renderDashboard(await api('/ceo/dashboard'));
        message.textContent = '';
    } catch (error) {
        message.textContent = `Não foi possível atualizar o painel: ${error.message}`;
    } finally {
        state.loading = false;
        button.disabled = false;
    }
}

// Só atualiza sozinho com a aba à vista: escondida, não gasta requisições
function syncRefresh() {
    clearInterval(state.timer);
    state.timer = null;
    if (state.session?.role === 'CEO' && !document.hidden) {
        state.timer = setInterval(loadDashboard, REFRESH_MS);
    }
}

document.getElementById('dash-refresh').addEventListener('click', loadDashboard);
document.addEventListener('visibilitychange', () => {
    if (!document.hidden) loadDashboard();
    syncRefresh();
});

render();
