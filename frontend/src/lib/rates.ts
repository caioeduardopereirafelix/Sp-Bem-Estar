import type {NeighborhoodFeature} from './api';

// Escala sequencial de um só tom — o azul das placas — do claro ao escuro
export const RATE_COLORS = ['#dbe5f7', '#9db8f2', '#5f86d6', '#2c56b8', '#163070'];

// Faixas: "nenhum" e o restante dividido em quatro intervalos iguais até o máximo
export function computeRateBreaks(features: NeighborhoodFeature[]): number[] {
    const max = Math.max(0, ...features.map((f) => Number(f.properties.openIssuesPer10k ?? 0)));
    if (max === 0) return [0];
    const step = max / 4;
    return [0, step, 2 * step, 3 * step, max];
}

export function rateColor(rate: number | null | undefined, breaks: number[]): string {
    if (rate == null) return 'transparent';
    const index = breaks.findIndex((limit) => Number(rate) <= limit);
    return RATE_COLORS[index === -1 ? RATE_COLORS.length - 1 : index];
}
