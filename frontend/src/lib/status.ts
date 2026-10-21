import type {IssueStatus} from './api';

// Status é estado: cor + forma do marcador, para nunca depender só da cor
export const STATUS: Record<IssueStatus, {label: string; color: string; shape: 'circle' | 'square' | 'diamond' | 'triangle'}> = {
    OPEN: {label: 'Aberto', color: '#d03b3b', shape: 'circle'},
    CONFIRMED: {label: 'Confirmado', color: '#ec835a', shape: 'square'},
    IN_PROGRESS: {label: 'Em andamento', color: '#fab219', shape: 'diamond'},
    RESOLVED: {label: 'Resolvido', color: '#1f9d3a', shape: 'triangle'},
};

export const STATUS_ORDER: IssueStatus[] = ['OPEN', 'CONFIRMED', 'IN_PROGRESS', 'RESOLVED'];

// Espelha IssueStatus.allowedTransitions() do backend
export const TRANSITIONS: Record<IssueStatus, IssueStatus[]> = {
    OPEN: ['CONFIRMED', 'IN_PROGRESS', 'RESOLVED'],
    CONFIRMED: ['IN_PROGRESS', 'RESOLVED'],
    IN_PROGRESS: ['RESOLVED'],
    RESOLVED: [],
};

// O botão diz o que acontece; o aviso de confirmação repete o mesmo verbo
export const ACTIONS: Record<IssueStatus, {button: string; done: (id: number) => string}> = {
    OPEN: {button: 'Reabrir', done: (id) => `Chamado #${id} reaberto`},
    CONFIRMED: {button: 'Confirmar chamado', done: (id) => `Chamado #${id} confirmado`},
    IN_PROGRESS: {button: 'Iniciar atendimento', done: (id) => `Atendimento do chamado #${id} iniciado`},
    RESOLVED: {button: 'Marcar como resolvido', done: (id) => `Chamado #${id} marcado como resolvido`},
};

export function shapeSvg(status: IssueStatus, size = 18): string {
    const {color, shape} = STATUS[status];
    const c = size / 2;
    const r = size / 2 - 3;
    const marks = {
        circle: `<circle cx="${c}" cy="${c}" r="${r}"/>`,
        square: `<rect x="${c - r}" y="${c - r}" width="${2 * r}" height="${2 * r}" rx="2"/>`,
        diamond: `<polygon points="${c},${c - r - 1} ${c + r + 1},${c} ${c},${c + r + 1} ${c - r - 1},${c}"/>`,
        triangle: `<polygon points="${c},${c - r - 1} ${c + r + 1},${c + r} ${c - r - 1},${c + r}"/>`,
    };
    const mark = marks[shape];
    return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" aria-hidden="true" focusable="false">`
        + `<g fill="none" stroke="rgba(20,24,26,0.6)" stroke-width="4" stroke-linejoin="round">${mark}</g>`
        + `<g fill="${color}" stroke="#ffffff" stroke-width="2" stroke-linejoin="round">${mark}</g></svg>`;
}

export const numberFormat = new Intl.NumberFormat('pt-BR');
export const rateFormat = new Intl.NumberFormat('pt-BR', {minimumFractionDigits: 1, maximumFractionDigits: 1});
export const dateTimeFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'short'});
const relative = new Intl.RelativeTimeFormat('pt-BR', {numeric: 'auto'});

export function daysOpen(firstReportedAt: string, now = Date.now()): number {
    return Math.max(0, Math.floor((now - new Date(firstReportedAt).getTime()) / 86_400_000));
}

export function ago(iso: string, now = Date.now()): string {
    const minutes = Math.round((new Date(iso).getTime() - now) / 60_000);
    if (Math.abs(minutes) < 60) return relative.format(minutes, 'minute');
    const hours = Math.round(minutes / 60);
    if (Math.abs(hours) < 24) return relative.format(hours, 'hour');
    return relative.format(Math.round(hours / 24), 'day');
}

export function plural(n: number, one: string, many: string): string {
    return `${numberFormat.format(n)} ${n === 1 ? one : many}`;
}
