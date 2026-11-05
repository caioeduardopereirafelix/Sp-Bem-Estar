import {API, api, escapeHtml, numberFormat, onSessionChange, saveSession, state} from './core.js';
import {map} from './mapview.js';
import {showView} from './sheet.js';
import {reportsLabel, statusLabel} from './ui.js';
import {loadDistricts} from './districts.js';
import {blockReporter, loadIssues} from './issues.js';
import {focusIssue} from './links.js';
import {renderAccount} from './login.js';

export function renderSession() {
    const session = state.session;
    document.getElementById('operator-tab').hidden = !session;
    document.querySelectorAll('.tab[data-goto="explore"], .tab[data-goto="mine"], .tab[data-goto="more"]')
        .forEach((tab) => { tab.hidden = Boolean(session); });
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
        showView('explore');
    }
}

onSessionChange(renderSession);

export function showSubview(name) {
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

const PAGE_SIZE = 20;

const queue = {page: 0, items: []};

export const moderation = {page: 0, items: []};

const moderationMessage = document.getElementById('moderation-message');

function loadOperatorQueues() {
    Promise.all([loadQueue(), loadModeration()]).catch((error) => {
        moderationMessage.textContent = error.message;
        moderationMessage.className = 'message error';
    });
}

export async function loadQueue(append = false) {
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

const teamMessage = document.getElementById('team-message');

const ROLE_LABELS = {OPERATOR: 'Operador', ADMIN: 'Administrador'};

function setTeamMessage(text, kind) {
    teamMessage.textContent = text;
    teamMessage.className = `message ${kind ?? ''}`;
}

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
