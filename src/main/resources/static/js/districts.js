import {CITY, RATE_COLORS, api, canHover, escapeHtml, numberFormat, rateFormat, state} from './core.js';
import {districtLayer, legend, map} from './mapview.js';
import {showToast} from './ui.js';

function rateColor(rate) {
    if (rate == null) {
        return 'transparent';
    }
    const index = state.rateBreaks.findIndex((limit) => rate <= limit);
    return RATE_COLORS[index === -1 ? RATE_COLORS.length - 1 : index];
}

export function districtStyle(feature) {
    return {
        color: '#ffffff',
        weight: 1,
        fillColor: rateColor(feature.properties.openIssuesPer10k),
        fillOpacity: 0.55,
    };
}

export function bindDistrict(feature, layer) {
    const p = feature.properties;
    if (!canHover()) {
        layer.on('click', () => showToast(`${p.name}: ${numberFormat.format(p.openIssues)} problemas em aberto, `
            + `${rateFormat.format(p.openIssuesPer10k ?? 0)} a cada 10 mil moradores.`));
        return;
    }
    layer.bindTooltip(`<strong>${escapeHtml(p.name)}</strong><br>
        ${numberFormat.format(p.population ?? 0)} moradores<br>
        ${numberFormat.format(p.openIssues)} problemas em aberto<br>
        ${rateFormat.format(p.openIssuesPer10k ?? 0)} a cada 10 mil moradores`, {sticky: true});
    layer.on({
        mouseover: () => layer.setStyle({weight: 3, color: '#1b2130'}),
        mouseout: () => districtLayer.resetStyle(layer),
    });
}

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
    const rows = breaks.length === 1
        ? `<div class="legend-row"><span class="swatch" data-color="0"></span>Nenhum problema em aberto</div>`
        : breaks.map((limit, i) => {
            const text = i === 0 ? 'Nenhum' : `até ${rateFormat.format(limit)}`;
            return `<div class="legend-row"><span class="swatch" data-color="${i}"></span>${text}</div>`;
        }).join('');
    const container = legend.getContainer();
    container.innerHTML = `<h3>Em aberto a cada 10 mil moradores</h3>${rows}`;
    container.querySelectorAll('.swatch[data-color]').forEach((swatch) => {
        swatch.style.background = RATE_COLORS[swatch.dataset.color];
    });
}

export async function loadDistricts() {
    const collection = await api(`/neighborhoods?city=${encodeURIComponent(CITY)}`);
    state.rateBreaks = computeRateBreaks(collection.features);
    districtLayer.clearLayers().addData(collection);
    if (map.hasLayer(districtLayer)) {
        renderLegend();
    }
    renderRanking(collection.features.map((f) => f.properties));
}

function renderRanking(stats) {
    const rows = stats.filter((s) => s.openIssues > 0).slice(0, 10);
    document.getElementById('ranking-body').innerHTML = rows.length === 0
        ? '<tr><td colspan="3">Nenhum problema em aberto ainda.</td></tr>'
        : rows.map((s) => `<tr><td>${escapeHtml(s.name)}</td><td>${numberFormat.format(s.openIssues)}</td>
            <td>${rateFormat.format(s.openIssuesPer10k ?? 0)}</td></tr>`).join('');
}

document.getElementById('districts-toggle').addEventListener('change', (event) => {
    if (event.target.checked) {
        districtLayer.addTo(map);
        legend.addTo(map);
        renderLegend();
    } else {
        districtLayer.remove();
        legend.remove();
    }
});
