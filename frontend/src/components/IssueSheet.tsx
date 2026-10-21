import {useCallback, useEffect, useState} from 'react';
import {toast} from 'sonner';
import {api, type IssueDetail, type IssueStatus} from '@/lib/api';
import {ACTIONS, ago, dateTimeFormat, numberFormat, plural, STATUS, TRANSITIONS} from '@/lib/status';
import {Sheet, SheetContent, SheetDescription, SheetTitle} from '@/components/ui/sheet';
import {Button} from '@/components/ui/button';
import {StatusMark} from './StatusMark';

interface Props {
    issueId: number | null;
    refreshKey: number;
    onClose(): void;
    onChanged(): void;
}

export function IssueSheet({issueId, refreshKey, onClose, onChanged}: Props) {
    const [issue, setIssue] = useState<IssueDetail | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [changing, setChanging] = useState<IssueStatus | null>(null);

    const load = useCallback((id: number, signal?: AbortSignal) => {
        setError(null);
        api.issue(id, signal).then(setIssue).catch((e: Error) => {
            if (e.name !== 'AbortError') setError(e.message);
        });
    }, []);

    useEffect(() => {
        if (issueId == null) return;
        setIssue((current) => (current?.id === issueId ? current : null));
        const controller = new AbortController();
        load(issueId, controller.signal);
        return () => controller.abort();
    }, [issueId, refreshKey, load]);

    async function change(to: IssueStatus) {
        if (!issue) return;
        setChanging(to);
        try {
            await api.changeStatus(issue.id, to);
            toast.success(ACTIONS[to].done(issue.id));
            load(issue.id);
            onChanged();
        } catch (e) {
            toast.error((e as Error).message);
            load(issue.id);
        } finally {
            setChanging(null);
        }
    }

    const transitions = issue ? TRANSITIONS[issue.status] : [];

    // Não modal: o mapa continua visível e clicável, e outro marcador troca o chamado aberto
    return (
        <Sheet modal={false} open={issueId != null} onOpenChange={(open) => !open && onClose()}>
            <SheetContent aria-describedby={undefined} onInteractOutside={(e) => e.preventDefault()}>
                <div className="placa m-3 mb-0 pr-12">
                    <div className="placa-inner">
                        <p className="text-[0.8125rem] font-semibold opacity-95">Chamado #{issueId}</p>
                        <SheetTitle className="text-[1.375rem] font-extrabold leading-[1.1]">
                            {issue?.categoryName ?? 'Carregando…'}
                        </SheetTitle>
                        <p className="mt-1 text-[0.8125rem] font-semibold opacity-95">{issue ? issue.neighborhood ?? 'Fora dos distritos importados' : ' '}</p>
                    </div>
                </div>

                <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-8 pt-5">
                    {error && (
                        <div role="alert" className="rounded-md border border-destructive/40 p-4">
                            <p className="font-semibold">Não foi possível abrir o chamado</p>
                            <p className="mt-1 text-sm text-muted-foreground">{error}</p>
                            <Button variant="outline" size="sm" className="mt-3" onClick={() => issueId && load(issueId)}>Tentar de novo</Button>
                        </div>
                    )}

                    {issue && (
                        <>
                            <SheetDescription asChild>
                                <div className="flex flex-wrap items-baseline justify-between gap-2">
                                    <StatusMark status={issue.status} className="text-base text-foreground"/>
                                    <span className="text-sm text-muted-foreground">último relato {ago(issue.lastReportedAt)}</span>
                                </div>
                            </SheetDescription>

                            <dl className="mt-5 grid grid-cols-2 gap-px overflow-hidden rounded-md border bg-border">
                                <div className="bg-card p-3">
                                    <dt className="text-sm text-muted-foreground">Relatos</dt>
                                    <dd className="text-2xl font-extrabold tabular-nums">{numberFormat.format(issue.reportCount)}</dd>
                                </div>
                                <div className="bg-card p-3">
                                    <dt className="text-sm text-muted-foreground">Prioridade</dt>
                                    <dd className="text-2xl font-extrabold tabular-nums">{Number(issue.priorityScore).toLocaleString('pt-BR', {maximumFractionDigits: 2})}</dd>
                                </div>
                                <div className="bg-card p-3">
                                    <dt className="text-sm text-muted-foreground">Primeiro relato</dt>
                                    <dd className="font-semibold tabular-nums">{dateTimeFormat.format(new Date(issue.firstReportedAt))}</dd>
                                </div>
                                <div className="bg-card p-3">
                                    <dt className="text-sm text-muted-foreground">{issue.resolvedAt ? 'Resolvido em' : 'Último relato'}</dt>
                                    <dd className="font-semibold tabular-nums">{dateTimeFormat.format(new Date(issue.resolvedAt ?? issue.lastReportedAt))}</dd>
                                </div>
                            </dl>
                            <p className="mt-2 max-w-[56ch] text-sm text-muted-foreground">
                                A prioridade cresce com a gravidade da categoria, o número de relatos e o tempo em aberto.
                            </p>

                            {issue.reports.length > 0 && (
                                <section className="mt-6">
                                    <h3 className="font-semibold">Fotos dos relatos</h3>
                                    <ul className="mt-2 grid grid-cols-3 gap-2">
                                        {issue.reports.slice(0, 9).map((r, i) => (
                                            <li key={r.id}>
                                                <a href={r.photoUrl} target="_blank" rel="noopener" className="block rounded-md">
                                                    <img src={r.photoUrl} loading="lazy"
                                                         alt={`Foto do relato ${i + 1}, enviada ${ago(r.createdAt)}`}
                                                         className="aspect-square w-full rounded-md border bg-muted object-cover"/>
                                                </a>
                                            </li>
                                        ))}
                                    </ul>
                                    {issue.reports.length > 9 && (
                                        <p className="mt-2 text-sm text-muted-foreground">Mais {plural(issue.reports.length - 9, 'foto', 'fotos')} neste chamado.</p>
                                    )}
                                </section>
                            )}

                            <section className="mt-6">
                                <h3 className="font-semibold">Histórico</h3>
                                <ol className="mt-2 border-l-2 border-border pl-4">
                                    <li className="relative pb-3">
                                        <span className="absolute -left-[1.4rem] top-1.5 size-2.5 rounded-full bg-muted-foreground" aria-hidden/>
                                        <span className="font-semibold">Chamado aberto</span>
                                        <span className="block text-sm text-muted-foreground">{dateTimeFormat.format(new Date(issue.firstReportedAt))}</span>
                                    </li>
                                    {issue.history.filter((h) => h.from != null).map((h) => (
                                        <li key={h.changedAt + h.to} className="relative pb-3">
                                            <span className="absolute -left-[1.4rem] top-1.5 size-2.5 rounded-full" style={{background: STATUS[h.to].color}} aria-hidden/>
                                            <span className="font-semibold">{STATUS[h.from!].label} para {STATUS[h.to].label.toLowerCase()}</span>
                                            <span className="block text-sm text-muted-foreground">{dateTimeFormat.format(new Date(h.changedAt))}</span>
                                        </li>
                                    ))}
                                </ol>
                            </section>

                            {transitions.length > 0 && (
                                <section className="mt-6 rounded-md bg-muted p-4">
                                    <h3 className="font-semibold">Atualizar status</h3>
                                    <p className="mt-1 text-sm text-muted-foreground">Uso do órgão público. Nesta versão de demonstração não há login.</p>
                                    <div className="mt-3 flex flex-wrap gap-2">
                                        {transitions.map((to) => (
                                            <Button key={to} variant={to === transitions[0] ? 'default' : 'outline'} size="sm"
                                                    disabled={changing != null} onClick={() => change(to)}>
                                                {changing === to ? 'Salvando…' : ACTIONS[to].button}
                                            </Button>
                                        ))}
                                    </div>
                                </section>
                            )}
                        </>
                    )}
                </div>
            </SheetContent>
        </Sheet>
    );
}
