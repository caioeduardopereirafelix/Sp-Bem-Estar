import {api, escapeHtml, state} from './core.js';
import {map} from './mapview.js';
import {showView} from './sheet.js';
import './reporter.js';
import {enableCompass, geolocationAvailable, locateButton, setFollow, startTracking} from './location.js';
import './ui.js';
import {loadDistricts} from './districts.js';
import {loadIssues, renderCategoryChips, scheduleIssueLoad} from './issues.js';
import {geolocationPermission, renderCategoryTiles, setReportMessage} from './report.js';
import {loadMyReports, setMyReportsMessage} from './mine.js';
import {setupPush} from './push.js';
import './install.js';
import {openLinkedIssue, openShortcut} from './links.js';
import {renderSession} from './staff.js';
import {resumePendingLogin} from './login.js';

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

resumePendingLogin();

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
