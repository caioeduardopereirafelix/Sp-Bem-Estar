import {categoryIcon, escapeHtml, numberFormat, shortName, state} from './core.js';
import {map} from './mapview.js';
import {panel, setSheet, showView} from './sheet.js';
import {citizenSignedIn, reporterFetch, sendReport} from './reporter.js';
import {
    LOW_ACCURACY_METERS, currentPosition, setFollow, setManualLocation, showUserLocation, stopLocationFix, tracking,
    userAccuracy
} from './location.js';
import {loadDistricts} from './districts.js';
import {loadIssues} from './issues.js';
import {loadMyReports} from './mine.js';
import {openLogin} from './login.js';

const reportForm = document.getElementById('report-form');

const reportMessage = document.getElementById('report-message');

export function setReportMessage(text, kind) {
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
    setSheet(step === 'where' ? 'half' : 'full');
    document.body.classList.toggle('picking', step === 'where');
    document.querySelector('.views').scrollTop = 0;
}

function startReport() {
    resetReport();
    stopLocationFix();
    setFollow(false);
    state.report.active = true;
    map.closePopup();
    showView('report');
    goToStep('where');
}

export function resetReport() {
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
        startManualPick(error.message, 'error');
    }
}

export async function geolocationPermission() {
    if (!window.isSecureContext || !('geolocation' in navigator)) {
        return 'unsupported';
    }
    try {
        return (await navigator.permissions.query({name: 'geolocation'})).state;
    } catch {
        return 'prompt';
    }
}

const WHERE_TEXT = {
    prompt: 'Para marcar o lugar certo, usamos a localização do seu aparelho. Ela só marca o ponto do relato e '
        + 'não é guardada. O navegador vai pedir sua permissão.',
    denied: 'O acesso à localização está bloqueado para este site. Para liberar, toque no cadeado ao lado do endereço. '
        + 'Ou marque o ponto no mapa.',
    unsupported: 'Este navegador não informa a localização nesta página. Marque o ponto no mapa.',
};

document.getElementById('report-begin').addEventListener('click', async () => {
    if (!citizenSignedIn()) {
        openLogin('Para relatar, entre com o seu e-mail. Assim você acompanha o relato em qualquer aparelho.', 'report');
        return;
    }
    startReport();
    const permission = await geolocationPermission();
    if (permission === 'granted') {
        await locateForReport();
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
    if (panel.dataset.current === 'issue') {
        showView('explore');
    }
});

export function renderCategoryTiles() {
    const grid = document.getElementById('category-grid');
    grid.innerHTML = state.categories.map((c) => `<button type="button" class="tile" data-category="${c.code}"
        aria-pressed="false" title="${escapeHtml(c.name)}">${categoryIcon(c.code)}<span>${
        escapeHtml(shortName(c.code, c.name))}</span></button>`).join('');
    grid.querySelectorAll('[data-category]').forEach((tile) => {
        tile.addEventListener('click', () => {
            chooseCategory(tile.dataset.category);
            hideSuggestion();
            state.report.confirmCategory = false;
            goToStep('photo');
        });
    });
}

const suggestion = document.getElementById('category-suggestion');

function hideSuggestion() {
    suggestion.hidden = true;
    reportForm.querySelector('[type="submit"]').hidden = false;
}

function showSuggestion(problem) {
    const chosen = problem.chosenCategory;
    const predicted = problem.predictedCategory;
    const accept = document.getElementById('suggestion-accept');
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
    state.report.confirmCategory = true;
    hideSuggestion();
    reportForm.requestSubmit();
});

reportForm.elements.photo.addEventListener('change', () => {
    hideSuggestion();
    state.report.confirmCategory = false;
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
            setReportMessage('');
            openLogin(`${error.message} O relato continua aqui, pronto para enviar.`, 'report-form');
            return;
        }
        setReportMessage(error.message, 'error');
    } finally {
        submit.disabled = false;
    }
});

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
            return;
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
            break;
        }
    }
    box.className = 'photo-check';
    box.textContent = 'A conferência automática da foto ainda não terminou. Você vê o andamento em "Meus relatos".';
}
