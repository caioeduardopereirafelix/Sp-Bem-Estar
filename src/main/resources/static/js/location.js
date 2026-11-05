import {isDesktop, numberFormat, state} from './core.js';
import {map} from './mapview.js';
import {panel, setSheet} from './sheet.js';
import {showToast, toast} from './ui.js';
import {geolocationPermission} from './report.js';

export const LOW_ACCURACY_METERS = 50;

const CORRECTABLE_METERS = 100;

const FOLLOW_ZOOM = 17;

const MOVING_SPEED = 0.7;

const NO_GEOLOCATION = 'Este navegador não informa a localização aqui (é preciso HTTPS). Marque o ponto no mapa.';

const GEOLOCATION_ERRORS = {
    1: 'Sem permissão para usar sua localização. Libere nas configurações do navegador ou marque o ponto no mapa.',
    2: 'Sua localização não está disponível agora. Marque o ponto no mapa.',
    3: 'A localização demorou demais. Tente de novo ou marque o ponto no mapa.',
};

export const tracking = {watchId: null, position: null, marker: null, accuracy: null, following: false,
    gpsHeading: null, compass: null, compassOn: false, highAccuracy: true, asked: false, warned: false, frame: 0,
    manual: null, fixing: false, fixDismissed: false};

export const locateButton = document.getElementById('locate-button');

export const geolocationAvailable = () => window.isSecureContext && 'geolocation' in navigator;

export function currentPosition() {
    return new Promise((resolve, reject) => {
        if (!geolocationAvailable()) {
            reject(new Error(NO_GEOLOCATION));
            return;
        }
        if (tracking.position && Date.now() - tracking.position.timestamp < 30_000) {
            resolve(tracking.position);
            return;
        }
        navigator.geolocation.getCurrentPosition(resolve,
            (error) => reject(new Error(GEOLOCATION_ERRORS[error.code] ?? 'Não foi possível obter sua localização.')),
            {enableHighAccuracy: true, timeout: 15000, maximumAge: 60000});
    });
}

const HIGH_ACCURACY = {enableHighAccuracy: true, maximumAge: 5000, timeout: 20000};

const NORMAL_ACCURACY = {enableHighAccuracy: false, maximumAge: 60_000, timeout: 30000};

export function startTracking() {
    if (tracking.watchId !== null || !geolocationAvailable()) {
        return;
    }
    watchPosition(HIGH_ACCURACY);
    navigator.geolocation.getCurrentPosition((position) => {
        if (!tracking.position) {
            onPosition(position);
        }
    }, () => {
    }, {enableHighAccuracy: false, maximumAge: 600_000, timeout: 15000});
}

function watchPosition(options) {
    tracking.highAccuracy = options.enableHighAccuracy;
    tracking.watchId = navigator.geolocation.watchPosition(onPosition, onPositionError, options);
}

function stopTracking() {
    if (tracking.watchId !== null) {
        navigator.geolocation.clearWatch(tracking.watchId);
        tracking.watchId = null;
    }
}

function onPosition(position) {
    const first = !tracking.position;
    tracking.position = position;
    const precise = position.coords.accuracy <= CORRECTABLE_METERS;
    if (precise && tracking.manual) {
        tracking.manual = null;
        showToast('Sua localização ficou precisa: o ponto azul voltou a ser o do aparelho.');
    }
    const {heading, speed} = position.coords;
    tracking.gpsHeading = Number.isFinite(heading) && speed > MOVING_SPEED ? heading : null;
    drawUserMarker();
    locateButton.classList.remove('unavailable');
    if (first) {
        toast.hidden = true;
    }
    if (tracking.following) {
        followUser(first);
    }
    if (!precise && !tracking.manual) {
        offerLocationFix();
    }
}

function onPositionError(error) {
    if (error.code === 3 && tracking.highAccuracy) {
        stopTracking();
        watchPosition(NORMAL_ACCURACY);
        return;
    }
    if (error.code === 1) {
        stopTracking();
        setFollow(false);
        locateButton.classList.add('unavailable');
    }
    if (tracking.position || tracking.warned || (error.code === 1 && !tracking.asked)) {
        return;
    }
    tracking.warned = true;
    let text = GEOLOCATION_ERRORS[error.code] ?? 'Não foi possível obter sua localização.';
    if (error.code !== 1 && isDesktop()) {
        text += ' No computador, confira se a localização está ligada nas configurações do sistema '
            + '(no Windows: Privacidade e segurança › Localização). Sem Wi-Fi, o Windows pode não achar a '
            + 'posição; ali dá para definir um local padrão.';
    }
    showToast(text, 10000);
}

function userLatLng() {
    if (tracking.manual) {
        return tracking.manual;
    }
    const {latitude, longitude} = tracking.position.coords;
    return L.latLng(latitude, longitude);
}

export function userAccuracy() {
    return tracking.manual ? 0 : tracking.position.coords.accuracy;
}

function drawUserMarker() {
    const latlng = userLatLng();
    const accuracy = userAccuracy();
    if (!tracking.marker) {
        tracking.accuracy = L.circle(latlng, {radius: accuracy, className: 'user-accuracy', interactive: false});
        tracking.marker = L.marker(latlng, {
            icon: L.divIcon({className: 'user-marker', iconSize: [64, 64], html: `<span class="user-halo"></span>
                <svg class="user-arrow" viewBox="0 0 40 40" aria-hidden="true"><path d="M20 5l11 28-11-6.5L9 33z"/></svg>
                <span class="user-dot"></span>`}),
            interactive: false, keyboard: false, zIndexOffset: 1000,
        });
        tracking.accuracy.addTo(map);
        tracking.marker.addTo(map);
    } else {
        tracking.marker.setLatLng(latlng);
        tracking.accuracy.setLatLng(latlng).setRadius(accuracy);
    }
    tracking.accuracy.getElement()?.classList.toggle('visible', accuracy > LOW_ACCURACY_METERS);
    scheduleArrowUpdate();
}

function scheduleArrowUpdate() {
    if (tracking.frame || !tracking.marker) {
        return;
    }
    tracking.frame = requestAnimationFrame(() => {
        tracking.frame = 0;
        const element = tracking.marker.getElement();
        const heading = tracking.gpsHeading ?? tracking.compass;
        element?.classList.toggle('no-heading', heading === null);
        element?.querySelector('.user-arrow')?.style.setProperty('rotate', `${heading ?? 0}deg`);
    });
}

function onOrientation(event) {
    let heading = null;
    if (typeof event.webkitCompassHeading === 'number') {
        heading = event.webkitCompassHeading;
    } else if (event.absolute && event.alpha !== null) {
        heading = 360 - event.alpha;
    }
    if (heading === null) {
        return;
    }
    tracking.compass = (heading + (screen.orientation?.angle ?? 0)) % 360;
    scheduleArrowUpdate();
}

export async function enableCompass() {
    const Orientation = window.DeviceOrientationEvent;
    if (tracking.compassOn || !Orientation) {
        return;
    }
    if (typeof Orientation.requestPermission === 'function') {
        try {
            if (await Orientation.requestPermission() !== 'granted') {
                return;
            }
        } catch {
            return;
        }
    }
    tracking.compassOn = true;
    window.addEventListener('ondeviceorientationabsolute' in window ? 'deviceorientationabsolute' : 'deviceorientation',
        onOrientation);
}

export function centerShowing(latlng, zoom) {
    const offset = isDesktop() ? L.point(-panel.offsetWidth / 2 - 8, 0) : L.point(0, panel.offsetHeight / 2);
    return map.unproject(map.project(latlng, zoom).add(offset), zoom);
}

function followUser(jump = false) {
    const accuracy = userAccuracy();
    const zoom = !jump ? map.getZoom() : accuracy > LOW_ACCURACY_METERS
        ? Math.min(FOLLOW_ZOOM, map.getBoundsZoom(userLatLng().toBounds(accuracy * 2.5)))
        : Math.max(map.getZoom(), FOLLOW_ZOOM);
    const target = centerShowing(userLatLng(), zoom);
    if (jump) {
        map.setView(target, zoom, {animate: false});
    } else if (map.latLngToContainerPoint(target).distanceTo(map.latLngToContainerPoint(map.getCenter())) > 8) {
        map.panTo(target, {animate: true, duration: 0.6});
    }
}

export function setFollow(following) {
    tracking.following = following;
    locateButton.setAttribute('aria-pressed', String(following));
}

export function showUserLocation(position) {
    onPosition(position);
    const latlng = userLatLng();
    const radius = Math.max(userAccuracy(), 40);
    map.fitBounds(latlng.toBounds(radius * 2), {maxZoom: 18, animate: false,
        paddingTopLeft: mapPadding(), paddingBottomRight: isDesktop() ? [0, 0] : [0, panel.offsetHeight]});
    return latlng;
}

export function mapPadding() {
    return isDesktop() ? [440, 0] : [0, 0];
}

locateButton.addEventListener('click', async () => {
    enableCompass();
    if (!geolocationAvailable()) {
        showToast(NO_GEOLOCATION);
        return;
    }
    if (await geolocationPermission() === 'denied') {
        showToast('O acesso à localização está bloqueado para este site. Para liberar, toque no cadeado ao lado '
            + 'do endereço e permita a localização.');
        return;
    }
    tracking.asked = true;
    tracking.warned = false;
    setFollow(true);
    if (tracking.position) {
        followUser(true);
        if (!tracking.manual && tracking.position.coords.accuracy > CORRECTABLE_METERS) {
            tracking.fixDismissed = false;
            offerLocationFix();
        }
    } else {
        showToast('Buscando sua localização…');
    }
    startTracking();
});

const locateFix = document.getElementById('locate-fix');

function offerLocationFix() {
    if (tracking.fixDismissed || tracking.fixing || state.report.active || !locateFix.hidden) {
        return;
    }
    const meters = numberFormat.format(Math.round(tracking.position.coords.accuracy / 10) * 10);
    document.getElementById('locate-fix-text').textContent = `Sua localização está aproximada (cerca de ${meters} m). `
        + 'Toque em "Marcar onde estou" e depois no mapa, no lugar onde você está.';
    locateFix.hidden = false;
}

export function stopLocationFix() {
    tracking.fixing = false;
    document.body.classList.remove('fixing-location');
    locateFix.hidden = true;
}

document.getElementById('locate-fix-start').addEventListener('click', () => {
    tracking.fixing = true;
    locateFix.hidden = true;
    document.body.classList.add('fixing-location');
    if (!isDesktop()) {
        setSheet('peek');
    }
    showToast('Toque no mapa no lugar onde você está.', 20000);
});

document.getElementById('locate-fix-dismiss').addEventListener('click', () => {
    tracking.fixDismissed = true;
    stopLocationFix();
});

export function setManualLocation(latlng) {
    stopLocationFix();
    tracking.manual = latlng;
    drawUserMarker();
    setFollow(true);
    followUser(true);
    showToast('Pronto: o ponto azul está onde você marcou. Ele volta a ser o do aparelho quando a localização '
        + 'ficar precisa.');
}

map.on('dragstart', () => setFollow(false));

map.on('zoomend', () => {
    if (tracking.following && tracking.position) {
        followUser();
    }
});
