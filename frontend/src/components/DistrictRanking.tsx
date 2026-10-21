import type {NeighborhoodStats} from '@/lib/api';
import {numberFormat, plural, rateFormat} from '@/lib/status';

interface Props {
    stats: NeighborhoodStats[] | null;
    error: string | null;
    onSelect(id: number): void;
}

export function DistrictRanking({stats, error, onSelect}: Props) {
    if (error) {
        return <p className="m-4 text-sm" role="alert">Não foi possível carregar os distritos: {error}</p>;
    }
    if (!stats) {
        return <p className="m-4 text-sm text-muted-foreground">Carregando distritos…</p>;
    }
    if (stats.length === 0) {
        return (
            <div className="m-4 rounded-md border border-dashed p-4 text-sm">
                <p className="font-semibold">Nenhum distrito importado</p>
                <p className="mt-1 text-muted-foreground">Rode a aplicação com <code>--app.neighborhoods.import-file=data/sp-distritos.geojson</code> para carregar os 96 distritos.</p>
            </div>
        );
    }

    const withOpen = stats.filter((s) => s.openIssues > 0);
    const max = Math.max(...withOpen.map((s) => Number(s.openIssuesPer10k ?? 0)), 0);

    return (
        <div className="h-full overflow-y-auto px-4 pb-6 pt-4">
            <p className="max-w-[60ch] text-sm text-muted-foreground">
                Chamados abertos a cada 10 mil habitantes (Censo 2022). Dividir pela população evita que distritos
                grandes liderem só por terem mais gente reportando.
            </p>

            {withOpen.length === 0 ? (
                <p className="mt-4 text-sm">Nenhum distrito tem chamados abertos agora.</p>
            ) : (
                <ol className="mt-4 space-y-1" aria-label="Distritos com mais chamados abertos por habitante">
                    {withOpen.map((s, index) => {
                        const rate = Number(s.openIssuesPer10k ?? 0);
                        return (
                            <li key={s.id}>
                                <button type="button" onClick={() => onSelect(s.id)}
                                        className="grid w-full cursor-pointer grid-cols-[2ch_1fr_auto] items-baseline gap-x-3 rounded-md px-2 py-2.5 text-left hover:bg-accent">
                                    <span className="text-sm font-semibold tabular-nums text-muted-foreground">{index + 1}</span>
                                    <span className="min-w-0">
                                        <span className="block truncate font-semibold">{s.name}</span>
                                        <span className="mt-1.5 block h-1.5 rounded-full bg-muted" aria-hidden>
                                            <span className="block h-full rounded-full bg-placa" style={{width: `${max ? (rate / max) * 100 : 0}%`}}/>
                                        </span>
                                        <span className="mt-1 block text-sm text-muted-foreground">
                                            {plural(s.openIssues, 'aberto', 'abertos')} em {numberFormat.format(s.population ?? 0)} habitantes
                                        </span>
                                    </span>
                                    <span className="text-right">
                                        <span className="block text-lg font-extrabold tabular-nums leading-none">{rateFormat.format(rate)}</span>
                                        <span className="block text-xs text-muted-foreground">por 10 mil</span>
                                    </span>
                                </button>
                            </li>
                        );
                    })}
                </ol>
            )}

            <p className="mt-4 text-sm text-muted-foreground">
                {stats.length - withOpen.length > 0 && `${plural(stats.length - withOpen.length, 'distrito', 'distritos')} sem chamados abertos. `}
                Limites: <a className="text-placa-text underline" href="https://geosampa.prefeitura.sp.gov.br" target="_blank" rel="noopener">GeoSampa</a>.
                População: <a className="text-placa-text underline" href="https://sidra.ibge.gov.br/tabela/9923" target="_blank" rel="noopener">IBGE, Censo 2022</a>.
            </p>
        </div>
    );
}
