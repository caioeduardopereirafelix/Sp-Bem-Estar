import {state} from './core.js';
import {selectMarker} from './issues.js';
import {resetReport} from './report.js';
import {loadMyReports, setMyReportsMessage} from './mine.js';

export const panel = document.getElementById('panel');

const VIEW_SIZES = {explore: 'peek', issue: 'half', report: 'half', sent: 'full', mine: 'full', more: 'full', operator: 'full',
    login: 'full'};

export function setSheet(size) {
    panel.dataset.size = size;
    document.body.dataset.sheet = size;
}

const CITIZEN_VIEWS = new Set(['explore', 'report', 'sent', 'mine', 'more', 'login']);

export function showView(name) {
    if (state.session && CITIZEN_VIEWS.has(name)) {
        name = 'operator';
    }
    document.querySelectorAll('[data-view]').forEach((view) => {
        view.hidden = view.dataset.view !== name;
    });
    document.querySelectorAll('.tab').forEach((tab) => {
        const current = tab.dataset.goto === name
            || ((name === 'report' || name === 'issue') && tab.dataset.goto === (state.session ? 'operator' : 'explore'))
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
        document.getElementById('report-begin').click();
    } else {
        if (state.report.active && target.dataset.goto !== 'report') {
            resetReport();
        }
        showView(target.dataset.goto);
    }
});

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

new ResizeObserver(() => {
    document.documentElement.style.setProperty('--sheet-height', `${panel.offsetHeight}px`);
}).observe(panel);

document.getElementById('menu-button').addEventListener('click', () => {
    if (state.session) {
        if (panel.dataset.current === 'operator' && panel.dataset.size === 'full') {
            setSheet('peek');
        } else {
            showView('operator');
        }
        return;
    }
    if (state.report.active) {
        resetReport();
    }
    showView(panel.dataset.current === 'more' && panel.dataset.size === 'full' ? 'explore' : 'more');
});
