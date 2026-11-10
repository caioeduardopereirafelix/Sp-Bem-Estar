import {showToast} from './ui.js';

export const standalone = () => window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;

export const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent)
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

let installPrompt = null;

function renderInstall() {
    const box = document.getElementById('install-box');
    const button = document.getElementById('install-button');
    if (standalone()) {
        box.hidden = true;
        return;
    }
    if (installPrompt) {
        box.hidden = false;
        button.hidden = false;
        return;
    }
    if (isIos) {
        document.getElementById('install-text').textContent = 'No iPhone: toque em Compartilhar (o quadrado com a '
            + 'seta para cima, na barra do Safari) e depois em "Adicionar à Tela de Início". O mapa passa a abrir '
            + 'como um aplicativo, e você pode ligar os avisos dos seus relatos.';
        box.hidden = false;
        button.hidden = true;
        return;
    }
    box.hidden = true;
}

window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    installPrompt = event;
    renderInstall();
});

window.addEventListener('appinstalled', () => {
    installPrompt = null;
    renderInstall();
    showToast('App instalado: o SP Bem-Estar está na sua tela de início.');
});

document.getElementById('install-button').addEventListener('click', async () => {
    if (!installPrompt) {
        return;
    }
    installPrompt.prompt();
    await installPrompt.userChoice;
    installPrompt = null;
    renderInstall();
});

navigator.serviceWorker?.register('/sw.js').catch(() => {
});

renderInstall();
