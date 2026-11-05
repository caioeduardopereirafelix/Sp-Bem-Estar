import {STATUS, categoryIcon, escapeHtml, numberFormat, shortName, timeAgo} from './core.js';
import {citizenSignedIn, reporterFetch} from './reporter.js';
import {trackLine} from './ui.js';
import {renderPush} from './push.js';
import {focusIssue} from './links.js';
import {openLogin, signedIn} from './login.js';

export const myReports = {page: 0, items: [], total: 0};

const myReportsMessage = document.getElementById('my-reports-message');

export function setMyReportsMessage(text, kind) {
    myReportsMessage.textContent = text;
    myReportsMessage.className = `message ${kind ?? ''}`;
}

export async function loadMyReports(append = false) {
    const list = document.getElementById('my-reports-list');
    if (!citizenSignedIn()) {
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
    openLogin('Entre com o seu e-mail para ver os seus relatos.', 'mine'));

document.getElementById('my-reports-more').addEventListener('click', () =>
    loadMyReports(true).catch((error) => setMyReportsMessage(error.message, 'error')));
