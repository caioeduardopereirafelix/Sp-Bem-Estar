import {
    STATUS, TRANSITIONS, api, canHover, categoryIcon, dateFormat, escapeHtml, isDesktop, numberFormat, shortName,
    state, timeAgo
} from './core.js';
import {issueLayer, map} from './mapview.js';
import {showView} from './sheet.js';
import {centerShowing, mapPadding, setFollow} from './location.js';
import {peopleLabel, reportsLabel, shapeSvg, trackLine} from './ui.js';
import {loadDistricts} from './districts.js';
import {loadQueue, moderation} from './staff.js';

export function scheduleIssueLoad() {
    if (state.autoPanning) {
        state.autoPanning = false;
        return;
    }
    if (state.loaded?.zoom === map.getZoom() && state.loaded.bounds.contains(map.getBounds())) {
        renderIssueCount();
        return;
    }
    clearTimeout(state.loadTimer);
    state.loadTimer = setTimeout(loadIssues, 250);
}

export async function loadIssues() {
    const visible = map.getBounds();
    if (visible.getWest() >= visible.getEast() || visible.getSouth() >= visible.getNorth()) {
        return;
    }
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

export function openIssue(id, marker) {
    setFollow(false);
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

export function selectMarker(marker) {
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

        host.element()?.querySelectorAll('.popup-actions button[data-status]').forEach((button) => {
            button.addEventListener('click', (event) => {
                if (button.dataset.status !== 'RESOLVED') {
                    changeStatus(issue.id, button.dataset.status, host);
                    return;
                }
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

export async function blockReporter(reportId, button) {
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
        event.stopPropagation();
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

export function renderCategoryChips() {
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
