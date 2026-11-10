import {api, isDesktop, state} from './core.js';
import {map} from './mapview.js';
import {setSheet, showView} from './sheet.js';
import {setFollow} from './location.js';
import {loadIssues, openIssue, renderCategoryChips} from './issues.js';
import {loadMyReports, setMyReportsMessage} from './mine.js';

export function openShortcut() {
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

export function openLinkedIssue() {
    const params = new URLSearchParams(location.search);
    const id = params.get('issue');
    if (id) {
        history.replaceState(null, '', location.pathname);
        return openIssueById(id);
    }
}

export async function focusIssue(item) {
    setFollow(false);
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
        map.panBy([-210, 0], {animate: false});
    }
    clearTimeout(state.loadTimer);
    await loadIssues();
    const marker = state.markers.get(item.id);
    if (marker) {
        openIssue(item.id, marker);
    }
}
