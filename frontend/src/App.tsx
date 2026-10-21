import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import L from 'leaflet';
import {Toaster} from 'sonner';
import {X} from 'lucide-react';
import {api, type Category, type FeatureCollection, type IssueFeature, type NeighborhoodFeature} from '@/lib/api';
import {computeRateBreaks} from '@/lib/rates';
import {MapView, type MapHandle} from '@/components/MapView';
import {StreetPlate} from '@/components/StreetPlate';
import {Legend} from '@/components/Legend';
import {IssueQueue, type Filters} from '@/components/IssueQueue';
import {DistrictRanking} from '@/components/DistrictRanking';
import {ReportFlow} from '@/components/ReportFlow';
import {IssueSheet} from '@/components/IssueSheet';
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs';
import {Button} from '@/components/ui/button';

const CITY = 'São Paulo';
type Tab = 'fila' | 'reportar' | 'distritos';

export function App() {
    const mapRef = useRef<MapHandle>(null);
    const [tab, setTab] = useState<Tab>('fila');
    const [categories, setCategories] = useState<Category[]>([]);
    const [districts, setDistricts] = useState<FeatureCollection<NeighborhoodFeature> | null>(null);
    const [districtError, setDistrictError] = useState<string | null>(null);
    const [showDistricts, setShowDistricts] = useState(true);
    const [bbox, setBbox] = useState<[number, number, number, number] | null>(null);
    const [filters, setFilters] = useState<Filters>({category: 'ALL', status: 'UNRESOLVED'});
    const [issues, setIssues] = useState<IssueFeature[]>([]);
    const [issuesLoading, setIssuesLoading] = useState(true);
    const [issuesError, setIssuesError] = useState<string | null>(null);
    const [selectedIssueId, setSelectedIssueId] = useState<number | null>(null);
    const [picking, setPicking] = useState(false);
    const [pickedPoint, setPickedPoint] = useState<L.LatLng | null>(null);
    // Muda a cada escrita (relato ou status) para recarregar o que depende dela
    const [version, setVersion] = useState(0);
    const bumpVersion = useCallback(() => setVersion((v) => v + 1), []);

    useEffect(() => {
        api.categories().then(setCategories).catch(() => setCategories([]));
    }, []);

    useEffect(() => {
        api.neighborhoods(CITY).then((c) => {
            setDistricts(c);
            setDistrictError(null);
        }).catch((e: Error) => setDistrictError(e.message));
    }, [version]);

    // Chamados da área visível. Com debounce e cancelamento: uma resposta antiga nunca sobrescreve a mais nova
    const loadIssues = useCallback((signal: AbortSignal) => {
        if (!bbox) return;
        setIssuesLoading(true);
        api.issues({
            bbox,
            category: filters.category === 'ALL' ? undefined : filters.category,
            status: filters.status === 'UNRESOLVED' ? undefined : filters.status,
        }, signal).then((c) => {
            setIssues(c.features);
            setIssuesError(null);
            setIssuesLoading(false);
        }).catch((e: Error) => {
            if (e.name === 'AbortError') return;
            setIssuesError(e.message);
            setIssuesLoading(false);
        });
    }, [bbox, filters]);

    const [retry, setRetry] = useState(0);
    useEffect(() => {
        const controller = new AbortController();
        const timer = setTimeout(() => loadIssues(controller.signal), 200);
        return () => {
            clearTimeout(timer);
            controller.abort();
        };
    }, [loadIssues, version, retry]);

    const rateBreaks = useMemo(() => computeRateBreaks(districts?.features ?? []), [districts]);
    const stats = useMemo(() => districts?.features.map((f) => f.properties) ?? null, [districts]);

    const selectIssue = useCallback((id: number) => setSelectedIssueId(id), []);

    function startPicking() {
        setPickedPoint(null);
        setPicking(true);
        setTab('reportar');
    }

    function pick(point: L.LatLng) {
        setPickedPoint(point);
        setPicking(false);
    }

    function locate(): Promise<void> {
        return new Promise((resolve, reject) => {
            if (!navigator.geolocation) {
                reject(new Error('Este navegador não informa a localização. Escolha o local no mapa.'));
                return;
            }
            navigator.geolocation.getCurrentPosition((position) => {
                const point = L.latLng(position.coords.latitude, position.coords.longitude);
                pick(point);
                mapRef.current?.flyTo(point.lat, point.lng, 17);
                resolve();
            }, () => reject(new Error('Não deu para obter sua localização. Permita o acesso ou escolha o local no mapa.')),
            {enableHighAccuracy: true, timeout: 10_000});
        });
    }

    return (
        <div className="grid min-h-full grid-rows-[60dvh_auto] md:h-full md:grid-cols-[minmax(0,1fr)_420px] md:grid-rows-1">
            <main className="relative isolate overflow-hidden" aria-label="Mapa">
                <MapView ref={mapRef} districts={districts} rateBreaks={rateBreaks} showDistricts={showDistricts}
                         issues={issues} selectedIssueId={selectedIssueId} picking={picking} pickedPoint={pickedPoint}
                         onIssueSelect={selectIssue} onBoundsChange={setBbox} onPick={pick}/>

                <StreetPlate as="header" className="pointer-events-none absolute left-3 top-3 z-[700] md:left-4 md:top-4"
                             title={<h1>SP Bem-Estar</h1>} caption="Zeladoria colaborativa de São Paulo"/>

                <div className="absolute bottom-6 left-3 z-[700] md:left-4">
                    <Legend breaks={rateBreaks} showDistricts={showDistricts} onToggleDistricts={setShowDistricts}/>
                </div>

                {picking && (
                    <>
                        <svg className="mira" viewBox="0 0 44 44" aria-hidden>
                            <circle cx="22" cy="22" r="13" fill="none" stroke="#1e2326" strokeWidth="5"/>
                            <circle cx="22" cy="22" r="13" fill="none" stroke="#f4c20d" strokeWidth="3"/>
                            <path d="M22 1v12M22 31v12M1 22h12M31 22h12" stroke="#1e2326" strokeWidth="5" strokeLinecap="round"/>
                            <path d="M22 2v10M22 32v10M2 22h10M32 22h10" stroke="#f4c20d" strokeWidth="2.5" strokeLinecap="round"/>
                        </svg>
                        <div className="absolute inset-x-3 top-24 z-[700] mx-auto flex max-w-md items-center gap-3 rounded-md bg-card p-2 pl-4 shadow-lg md:top-auto md:bottom-6"
                             role="status">
                            <p className="flex-1 text-sm font-semibold">Mova o mapa até a mira ficar sobre o problema.</p>
                            <Button size="sm" onClick={() => pick(mapRef.current!.center())}>Marcar aqui</Button>
                            <Button size="icon" variant="ghost" onClick={() => setPicking(false)} aria-label="Cancelar escolha do local">
                                <X aria-hidden/>
                            </Button>
                        </div>
                    </>
                )}
            </main>

            <aside className="flex min-h-0 flex-col border-t bg-card md:border-l md:border-t-0" aria-label="Painel">
                <Tabs value={tab} onValueChange={(v) => setTab(v as Tab)} className="flex min-h-0 flex-1 flex-col">
                    <TabsList className="sticky top-0 z-10 bg-card">
                        <TabsTrigger value="fila">Chamados</TabsTrigger>
                        <TabsTrigger value="reportar">Reportar</TabsTrigger>
                        <TabsTrigger value="distritos">Distritos</TabsTrigger>
                    </TabsList>
                    <TabsContent value="fila">
                        <IssueQueue categories={categories} filters={filters} onFiltersChange={setFilters}
                                    issues={issues} loading={issuesLoading} error={issuesError}
                                    selectedIssueId={selectedIssueId} onRetry={() => setRetry((r) => r + 1)}
                                    onStartReport={startPicking}
                                    onSelect={(issue) => {
                                        const [lon, lat] = issue.geometry.coordinates;
                                        mapRef.current?.flyTo(lat, lon, 16);
                                        setSelectedIssueId(issue.id);
                                    }}/>
                    </TabsContent>
                    <TabsContent value="reportar" forceMount hidden={tab !== 'reportar'}>
                        <ReportFlow categories={categories} picking={picking} pickedPoint={pickedPoint}
                                    onStartPicking={startPicking} onPickCenter={() => pick(mapRef.current!.center())}
                                    onLocate={locate} onClearPoint={() => {
                                        setPickedPoint(null);
                                        setPicking(false);
                                    }}
                                    onSubmitted={bumpVersion}
                                    onViewIssue={(id) => setSelectedIssueId(id)}/>
                    </TabsContent>
                    <TabsContent value="distritos">
                        <DistrictRanking stats={stats} error={districtError} onSelect={(id) => {
                            setShowDistricts(true);
                            mapRef.current?.fitDistrict(id);
                        }}/>
                    </TabsContent>
                </Tabs>
            </aside>

            <IssueSheet issueId={selectedIssueId} refreshKey={version} onClose={() => setSelectedIssueId(null)} onChanged={bumpVersion}/>
            <Toaster position="top-center" richColors closeButton toastOptions={{style: {fontFamily: 'var(--font-sans)'}}}/>
        </div>
    );
}
