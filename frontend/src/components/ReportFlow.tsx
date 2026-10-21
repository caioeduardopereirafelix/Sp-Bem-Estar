import {useEffect, useId, useRef, useState} from 'react';
import type L from 'leaflet';
import {Camera, Crosshair, LocateFixed, MapPin} from 'lucide-react';
import {api, ApiError, type Category, type ReportResult} from '@/lib/api';
import {plural} from '@/lib/status';
import {Button} from '@/components/ui/button';
import {RadioGroup, RadioGroupItem} from '@/components/ui/radio-group';
import {cn} from '@/lib/utils';

const MAX_BYTES = 10 * 1024 * 1024;
const ACCEPTED = ['image/jpeg', 'image/png'];

interface Props {
    categories: Category[];
    picking: boolean;
    pickedPoint: L.LatLng | null;
    onStartPicking(): void;
    onPickCenter(): void;
    onLocate(): Promise<void>;
    onClearPoint(): void;
    onSubmitted(result: ReportResult): void;
    onViewIssue(id: number): void;
}

type Errors = Partial<Record<'location' | 'category' | 'photo' | 'form', string>>;

export function ReportFlow(props: Props) {
    const [category, setCategory] = useState('');
    const [photo, setPhoto] = useState<File | null>(null);
    const [preview, setPreview] = useState<string | null>(null);
    const [errors, setErrors] = useState<Errors>({});
    const [sending, setSending] = useState(false);
    const [locating, setLocating] = useState(false);
    const [result, setResult] = useState<ReportResult | null>(null);
    const fileRef = useRef<HTMLInputElement>(null);
    const resultRef = useRef<HTMLDivElement>(null);
    const ids = {location: useId(), category: useId(), photo: useId()};

    useEffect(() => () => {
        if (preview) URL.revokeObjectURL(preview);
    }, [preview]);

    useEffect(() => {
        if (props.pickedPoint) setErrors((e) => ({...e, location: undefined}));
    }, [props.pickedPoint]);

    useEffect(() => {
        if (result) resultRef.current?.focus();
    }, [result]);

    function choosePhoto(file: File | undefined) {
        if (!file) return;
        if (!ACCEPTED.includes(file.type)) {
            setErrors((e) => ({...e, photo: 'Use uma foto JPEG ou PNG.'}));
            return;
        }
        if (file.size > MAX_BYTES) {
            setErrors((e) => ({...e, photo: 'A foto passa de 10 MB. Escolha uma imagem menor.'}));
            return;
        }
        setErrors((e) => ({...e, photo: undefined}));
        setPhoto(file);
        setPreview(URL.createObjectURL(file));
    }

    async function locate() {
        setLocating(true);
        try {
            await props.onLocate();
        } catch (error) {
            setErrors((e) => ({...e, location: (error as Error).message}));
        } finally {
            setLocating(false);
        }
    }

    async function submit(event: React.FormEvent) {
        event.preventDefault();
        const next: Errors = {};
        if (!props.pickedPoint) next.location = 'Marque no mapa onde está o problema.';
        if (!category) next.category = 'Escolha o tipo de problema.';
        if (!photo) next.photo = 'Adicione uma foto do problema.';
        setErrors(next);
        if (Object.keys(next).length > 0) {
            document.getElementById(next.location ? ids.location : next.category ? ids.category : ids.photo)?.focus();
            return;
        }
        setSending(true);
        try {
            const response = await api.createReport({
                category, latitude: props.pickedPoint!.lat, longitude: props.pickedPoint!.lng, photo: photo!,
            });
            setResult(response);
            props.onSubmitted(response);
        } catch (error) {
            const fields = error instanceof ApiError ? error.fieldErrors : undefined;
            setErrors({form: (error as Error).message, ...(fields?.category ? {category: fields.category} : {})});
        } finally {
            setSending(false);
        }
    }

    function reset() {
        setResult(null);
        setCategory('');
        setPhoto(null);
        setPreview(null);
        setErrors({});
        props.onClearPoint();
    }

    if (result) {
        return (
            <div ref={resultRef} tabIndex={-1} className="m-4 rounded-md border-l-4 border-ok bg-card p-4 outline-none" role="status">
                <p className="text-lg font-extrabold">{result.newIssue ? 'Novo chamado aberto' : 'Relato somado a um chamado'}</p>
                <p className="mt-1 max-w-[52ch] text-sm text-muted-foreground">
                    {result.newIssue
                        ? `Seu relato abriu o chamado #${result.issueId}. Quem passar pelo mesmo ponto e reportar vai somar ao seu.`
                        : `Alguém já tinha reportado esse problema. O chamado #${result.issueId} agora tem ${plural(result.issueReportCount, 'relato', 'relatos')}; quanto mais relatos, mais alta a prioridade.`}
                </p>
                <div className="mt-4 flex flex-wrap gap-2">
                    <Button onClick={() => props.onViewIssue(result.issueId)}>Ver chamado #{result.issueId}</Button>
                    <Button variant="outline" onClick={reset}>Fazer outro relato</Button>
                </div>
            </div>
        );
    }

    const step = 'grid grid-cols-[1.75rem_1fr] gap-x-3';
    const num = 'flex size-7 items-center justify-center rounded-full bg-placa text-sm font-extrabold text-white';
    const errorText = 'mt-2 text-sm font-semibold text-destructive';

    return (
        <form onSubmit={submit} noValidate className="h-full overflow-y-auto px-4 pb-6 pt-4">
            <p className="max-w-[56ch] text-sm text-muted-foreground">
                Relatos do mesmo ponto viram um único chamado. Assim a prefeitura vê quantas pessoas
                enfrentam o mesmo problema.
            </p>

            <ol className="mt-5 space-y-6">
                <li className={step}>
                    <span className={num} aria-hidden>1</span>
                    <div>
                        <h3 className="font-semibold leading-7">Onde está o problema?</h3>
                        {props.pickedPoint ? (
                            <div className="mt-2 flex flex-wrap items-center gap-2">
                                <span className="inline-flex items-center gap-1.5 text-sm"><MapPin className="size-4 text-placa-text" aria-hidden/>Local marcado no mapa</span>
                                <Button type="button" variant="link" size="sm" className="px-1" onClick={props.onStartPicking}>Mudar local</Button>
                            </div>
                        ) : props.picking ? (
                            <div className="mt-2">
                                <p className="text-sm text-muted-foreground">Mova o mapa até a mira ficar sobre o problema, ou toque direto no ponto.</p>
                                <Button id={ids.location} type="button" className="mt-2" onClick={props.onPickCenter}>
                                    <Crosshair aria-hidden/>Marcar na mira
                                </Button>
                            </div>
                        ) : (
                            <div className="mt-2 flex flex-wrap gap-2">
                                <Button id={ids.location} type="button" variant="outline" onClick={props.onStartPicking}
                                        aria-describedby={errors.location ? `${ids.location}-erro` : undefined}>
                                    <MapPin aria-hidden/>Escolher no mapa
                                </Button>
                                <Button type="button" variant="outline" onClick={locate} disabled={locating}>
                                    <LocateFixed aria-hidden/>{locating ? 'Localizando…' : 'Usar minha localização'}
                                </Button>
                            </div>
                        )}
                        {errors.location && <p id={`${ids.location}-erro`} className={errorText}>{errors.location}</p>}
                    </div>
                </li>

                <li className={step}>
                    <span className={num} aria-hidden>2</span>
                    <div>
                        <h3 id={`${ids.category}-titulo`} className="font-semibold leading-7">Que tipo de problema?</h3>
                        <RadioGroup id={ids.category} value={category} onValueChange={(v) => {
                            setCategory(v);
                            setErrors((e) => ({...e, category: undefined}));
                        }} aria-labelledby={`${ids.category}-titulo`} aria-describedby={errors.category ? `${ids.category}-erro` : undefined}
                                    className="mt-2">
                            {props.categories.map((c) => (
                                <label key={c.code} className={cn('flex min-h-12 cursor-pointer items-center gap-3 rounded-md border bg-card px-3',
                                    category === c.code && 'border-placa ring-1 ring-placa')}>
                                    <RadioGroupItem value={c.code}/>
                                    <span className="font-semibold">{c.name}</span>
                                </label>
                            ))}
                        </RadioGroup>
                        {errors.category && <p id={`${ids.category}-erro`} className={errorText}>{errors.category}</p>}
                    </div>
                </li>

                <li className={step}>
                    <span className={num} aria-hidden>3</span>
                    <div>
                        <h3 className="font-semibold leading-7">Foto do problema</h3>
                        <input ref={fileRef} id={ids.photo} type="file" accept="image/jpeg,image/png" className="sr-only"
                               aria-describedby={`${ids.photo}-ajuda${errors.photo ? ` ${ids.photo}-erro` : ''}`}
                               onChange={(e) => choosePhoto(e.target.files?.[0])}/>
                        {preview ? (
                            <div className="mt-2 flex items-center gap-3">
                                <img src={preview} alt="Foto escolhida" className="size-20 rounded-md border object-cover"/>
                                <Button type="button" variant="link" size="sm" className="px-1" onClick={() => fileRef.current?.click()}>Trocar foto</Button>
                            </div>
                        ) : (
                            <label htmlFor={ids.photo}
                                   className="mt-2 flex min-h-20 cursor-pointer items-center justify-center gap-2 rounded-md border-2 border-dashed border-input bg-card font-semibold hover:bg-accent [input:focus-visible+&]:outline-3 [input:focus-visible+&]:outline-ring">
                                <Camera className="size-5" aria-hidden/>Tirar ou escolher foto
                            </label>
                        )}
                        <p id={`${ids.photo}-ajuda`} className="mt-2 max-w-[52ch] text-sm text-muted-foreground">
                            JPEG ou PNG, até 10 MB. Antes de guardar, apagamos da foto a localização e os dados da câmera.
                        </p>
                        {errors.photo && <p id={`${ids.photo}-erro`} className={errorText}>{errors.photo}</p>}
                    </div>
                </li>
            </ol>

            {errors.form && <p className="mt-5 rounded-md border border-destructive/40 p-3 text-sm" role="alert">{errors.form}</p>}

            <Button type="submit" className="mt-6 w-full" disabled={sending}>{sending ? 'Enviando relato…' : 'Enviar relato'}</Button>
        </form>
    );
}
