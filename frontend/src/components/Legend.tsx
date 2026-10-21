import {rateFormat, STATUS, STATUS_ORDER} from '@/lib/status';
import {RATE_COLORS} from '@/lib/rates';
import {StatusShape} from './StatusMark';

export function Legend({breaks, showDistricts, onToggleDistricts}: {
    breaks: number[];
    showDistricts: boolean;
    onToggleDistricts(value: boolean): void;
}) {
    const rows = breaks.length === 1
        ? [{color: RATE_COLORS[0], text: 'Nenhum chamado aberto'}]
        : breaks.map((limit, i) => ({color: RATE_COLORS[i], text: i === 0 ? 'Nenhum' : `até ${rateFormat.format(limit)}`}));

    return (
        <details className="group max-w-60 rounded-md border bg-card/95 text-[0.8125rem] shadow-md backdrop-blur" open={window.matchMedia('(min-width: 768px)').matches}>
            <summary className="flex min-h-11 cursor-pointer items-center px-3 font-semibold">Legenda</summary>
            <div className="space-y-3 px-3 pb-3">
                <section>
                    <h3 className="mb-1 font-semibold text-muted-foreground">Status do chamado</h3>
                    <ul className="space-y-0.5">
                        {STATUS_ORDER.map((s) => (
                            <li key={s} className="flex items-center gap-2"><StatusShape status={s} size={14}/>{STATUS[s].label}</li>
                        ))}
                    </ul>
                </section>
                <section>
                    <label className="flex min-h-8 cursor-pointer items-center gap-2 font-semibold text-muted-foreground">
                        <input type="checkbox" className="size-4 accent-[var(--placa)]" checked={showDistricts}
                               onChange={(e) => onToggleDistricts(e.target.checked)}/>
                        Distritos por chamados abertos a cada 10 mil hab.
                    </label>
                    {showDistricts && (
                        <ul className="mt-1 space-y-0.5">
                            {rows.map((row) => (
                                <li key={row.text} className="flex items-center gap-2">
                                    <span className="h-3 w-4 shrink-0 rounded-[2px] border border-black/15" style={{background: row.color}}/>
                                    {row.text}
                                </li>
                            ))}
                        </ul>
                    )}
                </section>
            </div>
        </details>
    );
}
