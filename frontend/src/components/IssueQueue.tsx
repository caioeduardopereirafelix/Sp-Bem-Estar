import type {Category, IssueFeature, IssueStatus} from '@/lib/api';
import {daysOpen, numberFormat, plural, STATUS, STATUS_ORDER} from '@/lib/status';
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select';
import {Button} from '@/components/ui/button';
import {StatusShape} from './StatusMark';
import {cn} from '@/lib/utils';

export interface Filters {
    category: string | 'ALL';
    status: IssueStatus | 'UNRESOLVED';
}

interface Props {
    categories: Category[];
    filters: Filters;
    onFiltersChange(filters: Filters): void;
    issues: IssueFeature[];
    loading: boolean;
    error: string | null;
    selectedIssueId: number | null;
    onSelect(issue: IssueFeature): void;
    onRetry(): void;
    onStartReport(): void;
}

const LIMIT = 500;

// A API já devolve os chamados ordenados por prioridade: esta lista é a fila de atendimento da área visível
export function IssueQueue(props: Props) {
    const {filters, issues} = props;
    const filtered = filters.category !== 'ALL' || filters.status !== 'UNRESOLVED';

    return (
        <div className="flex h-full flex-col">
            <div className="grid grid-cols-2 gap-3 border-b px-4 py-4">
                <div className="grid gap-1.5">
                    <span id="filtro-categoria" className="text-sm font-semibold">Categoria</span>
                    <Select value={filters.category} onValueChange={(category) => props.onFiltersChange({...filters, category})}>
                        <SelectTrigger aria-labelledby="filtro-categoria"><SelectValue/></SelectTrigger>
                        <SelectContent>
                            <SelectItem value="ALL">Todas</SelectItem>
                            {props.categories.map((c) => <SelectItem key={c.code} value={c.code}>{c.name}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>
                <div className="grid gap-1.5">
                    <span id="filtro-status" className="text-sm font-semibold">Status</span>
                    <Select value={filters.status}
                            onValueChange={(status) => props.onFiltersChange({...filters, status: status as Filters['status']})}>
                        <SelectTrigger aria-labelledby="filtro-status"><SelectValue/></SelectTrigger>
                        <SelectContent>
                            <SelectItem value="UNRESOLVED">Não resolvidos</SelectItem>
                            {STATUS_ORDER.map((s) => <SelectItem key={s} value={s}>{STATUS[s].label}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>
            </div>

            <p className="px-4 pt-3 text-sm text-muted-foreground" aria-live="polite">
                {props.loading && issues.length === 0 ? 'Carregando chamados…'
                    : props.error ? ''
                        : issues.length === 0 ? ''
                            : `${plural(issues.length, 'chamado', 'chamados')} nesta área do mapa, do mais urgente ao menos urgente${issues.length >= LIMIT ? `. Mostrando os ${numberFormat.format(LIMIT)} primeiros; aproxime o mapa para ver o resto` : ''}.`}
            </p>

            {props.error ? (
                <div className="m-4 rounded-md border border-destructive/40 p-4" role="alert">
                    <p className="font-semibold">Não foi possível carregar os chamados</p>
                    <p className="mt-1 text-sm text-muted-foreground">{props.error}</p>
                    <Button variant="outline" size="sm" className="mt-3" onClick={props.onRetry}>Tentar de novo</Button>
                </div>
            ) : !props.loading && issues.length === 0 ? (
                <div className="m-4 rounded-md border border-dashed p-4">
                    <p className="font-semibold">Nenhum chamado nesta área</p>
                    <p className="mt-1 text-sm text-muted-foreground">
                        {filtered ? 'Mude os filtros ou afaste o zoom do mapa.' : 'Afaste o zoom do mapa ou reporte o primeiro problema daqui.'}
                    </p>
                    <Button variant="outline" size="sm" className="mt-3" onClick={props.onStartReport}>Reportar um problema</Button>
                </div>
            ) : (
                <ol className="min-h-0 flex-1 overflow-y-auto px-2 pb-4 pt-1" aria-label="Chamados por prioridade">
                    {issues.map((issue) => {
                        const p = issue.properties;
                        const days = daysOpen(p.firstReportedAt);
                        const selected = issue.id === props.selectedIssueId;
                        return (
                            <li key={issue.id}>
                                <button type="button" onClick={() => props.onSelect(issue)} aria-current={selected || undefined}
                                        className={cn('grid w-full cursor-pointer grid-cols-[auto_1fr_auto] items-start gap-x-3 rounded-md px-2 py-3 text-left hover:bg-accent',
                                            selected && 'bg-accent')}>
                                    <span className="pt-0.5"><StatusShape status={p.status} size={18}/></span>
                                    <span className="min-w-0">
                                        <span className="block font-semibold leading-snug">{p.categoryName}</span>
                                        <span className="block truncate text-sm text-muted-foreground">
                                            {p.neighborhood ?? 'Fora dos distritos'}
                                        </span>
                                        <span className="block text-sm text-muted-foreground">
                                            {STATUS[p.status].label}, {plural(p.reportCount, 'relato', 'relatos')}, {days === 0 ? 'desde hoje' : `há ${plural(days, 'dia', 'dias')}`}
                                        </span>
                                    </span>
                                    <span className="pt-0.5 text-sm font-semibold tabular-nums text-muted-foreground">#{issue.id}</span>
                                </button>
                            </li>
                        );
                    })}
                </ol>
            )}
        </div>
    );
}
