import {STATUS, TRACK, numberFormat} from './core.js';

export const toast = document.getElementById('toast');

let toastTimer = null;

export function showToast(text, duration = 6000) {
    toast.textContent = text;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toast.hidden = true;
    }, duration);
}

export function shapeSvg(status, size = 18) {
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
    return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" aria-hidden="true">
        <g fill="none" stroke="rgba(11,11,11,0.55)" stroke-width="4" stroke-linejoin="round">${mark}</g>
        <g fill="${color}" stroke="#ffffff" stroke-width="2" stroke-linejoin="round">${mark}</g></svg>`;
}

export function peopleLabel(reporters) {
    return reporters === 1 ? '1 pessoa relatou' : `${numberFormat.format(reporters)} pessoas relataram`;
}

export function reportsLabel(reports, reporters) {
    const text = `${numberFormat.format(reports)} relato${reports === 1 ? '' : 's'}`;
    return reporters === reports ? text
        : `${text} de ${numberFormat.format(reporters)} pessoa${reporters === 1 ? '' : 's'}`;
}

export function statusLabel(status) {
    return `<span class="status-label">${shapeSvg(status, 14)}${STATUS[status].label}</span>`;
}

export function trackLine(status) {
    const position = TRACK.indexOf(status);
    return `<ol class="track-line" aria-label="Andamento: ${STATUS[status]?.label ?? status}">${TRACK.map((step, i) => {
        const cls = i < position ? 'reached' : i === position ? 'reached current' : '';
        return `<li class="${cls}" data-status="${step}" ${i === position ? 'aria-current="step"' : ''}>${STATUS[step].track}</li>`;
    }).join('')}</ol>`;
}

document.getElementById('status-key').innerHTML = TRACK
    .map((s) => `<li>${shapeSvg(s, 16)}${STATUS[s].label}</li>`).join('');
