import {isDesktop} from './core.js';
import {bindDistrict, districtStyle} from './districts.js';

export const map = L.map('map', {zoomControl: false}).setView([-23.55, -46.63], 12);

L.control.zoom({position: 'topright'}).addTo(map);

map.attributionControl.setPosition(isDesktop() ? 'bottomright' : 'topright');

const BASE_STYLE_URL = 'https://tiles.openfreemap.org/styles/fiord';

const BASE_COLORS = {
    background: {'background-color': '#222a38'},
    water: {'fill-color': '#1d3b6e'},
    building: {'fill-color': 'hsla(222,30%,30%,0.55)'},
    park: {'fill-color': '#22392f'},
    landcover_wood: {'fill-color': 'hsla(150,25%,20%,0.5)'},
    highway_minor: {'line-color': '#3f4d66'},
    highway_major_casing: {'line-color': '#52627f'},
    highway_major_inner: {'line-color': '#46556f'},
    highway_motorway_casing: {'line-color': '#5c6d8c'},
    highway_name_other: {'text-color': '#b7c3d9', 'text-halo-color': '#222a38'},
    highway_path: {'line-opacity': 0.35},
};

function supportsWebGL() {
    try {
        return Boolean(document.createElement('canvas').getContext('webgl2'));
    } catch {
        return false;
    }
}

async function addBaseMap() {
    if (window.maplibregl && L.maplibreGL && supportsWebGL()) {
        try {
            const response = await fetch(BASE_STYLE_URL);
            if (!response.ok) {
                throw new Error(`estilo do mapa: ${response.status}`);
            }
            const style = await response.json();
            for (const layer of style.layers) {
                Object.assign(layer.paint ??= {}, BASE_COLORS[layer.id]);
            }
            L.maplibreGL({style}).addTo(map);
            return;
        } catch {
        }
    }
    map.getContainer().classList.add('raster-dark');
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(map);
}

addBaseMap();

map.createPane('districts').style.zIndex = 350;

export const districtLayer = L.geoJSON(null, {pane: 'districts', style: districtStyle, onEachFeature: bindDistrict});

export const issueLayer = L.layerGroup().addTo(map);

export const legend = L.control({position: 'topright'});

legend.onAdd = () => L.DomUtil.create('div', 'legend');
