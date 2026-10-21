import {forwardRef, useEffect, useImperativeHandle, useRef} from 'react';
import L from 'leaflet';
import type {FeatureCollection, IssueFeature, NeighborhoodFeature} from '@/lib/api';
import {numberFormat, plural, rateFormat, shapeSvg, STATUS} from '@/lib/status';
import {rateColor} from '@/lib/rates';

export interface MapHandle {
    flyTo(lat: number, lng: number, zoom?: number): void;
    fitDistrict(id: number): void;
    center(): L.LatLng;
}

interface Props {
    districts: FeatureCollection<NeighborhoodFeature> | null;
    rateBreaks: number[];
    showDistricts: boolean;
    issues: IssueFeature[];
    selectedIssueId: number | null;
    picking: boolean;
    pickedPoint: L.LatLng | null;
    onIssueSelect(id: number): void;
    onBoundsChange(bbox: [number, number, number, number]): void;
    onPick(point: L.LatLng): void;
}

const SAO_PAULO: L.LatLngExpression = [-23.55, -46.63];

const escapeHtml = (value: unknown) => String(value ?? '').replace(/[&<>"']/g,
    (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]!));

const pinIcon = L.divIcon({
    className: '',
    iconSize: [30, 40],
    iconAnchor: [15, 38],
    html: `<svg width="30" height="40" viewBox="0 0 30 40" aria-hidden="true">
        <path d="M15 38C15 38 3 23.5 3 14a12 12 0 0 1 24 0c0 9.5-12 24-12 24Z" fill="#f4c20d" stroke="#1e2326" stroke-width="2.5"/>
        <circle cx="15" cy="14" r="4.5" fill="#1e2326"/></svg>`,
});

export const MapView = forwardRef<MapHandle, Props>(function MapView(props, ref) {
    const containerRef = useRef<HTMLDivElement>(null);
    const mapRef = useRef<L.Map | null>(null);
    const districtLayerRef = useRef<L.GeoJSON | null>(null);
    const issueLayerRef = useRef<L.LayerGroup | null>(null);
    const markersRef = useRef(new Map<number, L.Marker>());
    const pinRef = useRef<L.Marker | null>(null);
    // Os handlers do Leaflet são registrados uma vez; lêem sempre as props mais recentes por esta ref
    const latest = useRef(props);
    latest.current = props;

    useImperativeHandle(ref, () => ({
        flyTo(lat, lng, zoom = 17) {
            mapRef.current?.flyTo([lat, lng], Math.max(zoom, mapRef.current.getZoom()), {duration: 0.6});
        },
        fitDistrict(id) {
            districtLayerRef.current?.eachLayer((layer) => {
                const feature = (layer as L.Polygon).feature as NeighborhoodFeature | undefined;
                if (feature?.id === id) mapRef.current?.fitBounds((layer as L.Polygon).getBounds(), {padding: [24, 24]});
            });
        },
        center: () => mapRef.current!.getCenter(),
    }), []);

    useEffect(() => {
        const map = L.map(containerRef.current!, {zoomControl: false, keyboard: true}).setView(SAO_PAULO, 11);
        mapRef.current = map;
        L.control.zoom({position: 'bottomright', zoomInTitle: 'Aproximar', zoomOutTitle: 'Afastar'}).addTo(map);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        }).addTo(map);
        map.attributionControl.setPrefix(false);
        map.getContainer().setAttribute('aria-label', 'Mapa de São Paulo com chamados e distritos. Use as setas para mover e + ou − para o zoom.');

        const districtPane = map.createPane('districts');
        districtPane.style.zIndex = '350';
        // De perto, o preenchimento dos distritos esconde as ruas: fica mais transparente
        map.on('zoomend', () => {
            districtPane.style.opacity = map.getZoom() >= 14 ? '0.35' : '1';
        });
        districtLayerRef.current = L.geoJSON(undefined, {pane: 'districts'}).addTo(map);
        issueLayerRef.current = L.layerGroup().addTo(map);

        const emitBounds = () => {
            const b = map.getBounds();
            latest.current.onBoundsChange([b.getWest(), b.getSouth(), b.getEast(), b.getNorth()]);
        };
        map.on('moveend', emitBounds);
        map.on('click', (e: L.LeafletMouseEvent) => {
            if (latest.current.picking) latest.current.onPick(e.latlng);
        });
        map.whenReady(() => {
            map.invalidateSize();
            emitBounds();
        });
        const resize = new ResizeObserver(() => map.invalidateSize());
        resize.observe(containerRef.current!);
        return () => {
            resize.disconnect();
            map.remove();
        };
    }, []);

    // Distritos: coroplético por chamados abertos a cada 10 mil habitantes
    useEffect(() => {
        const layer = districtLayerRef.current!;
        layer.clearLayers();
        if (!props.districts) return;
        const style = (feature?: GeoJSON.Feature): L.PathOptions => ({
            color: '#ffffff',
            weight: 1,
            fillColor: rateColor((feature as NeighborhoodFeature).properties.openIssuesPer10k, props.rateBreaks),
            fillOpacity: 0.55,
        });
        layer.options.style = style;
        layer.options.onEachFeature = (feature, l) => {
            const p = (feature as NeighborhoodFeature).properties;
            l.bindTooltip(`<strong>${escapeHtml(p.name)}</strong><br>
                ${plural(p.openIssues, 'chamado aberto', 'chamados abertos')}<br>
                ${p.openIssuesPer10k == null ? 'Sem dado de população' : `${rateFormat.format(p.openIssuesPer10k)} por 10 mil habitantes`}<br>
                ${numberFormat.format(p.population ?? 0)} habitantes`, {sticky: true});
            l.on({
                mouseover: () => (l as L.Path).setStyle({weight: 3, color: '#1e2326'}),
                mouseout: () => layer.resetStyle(l as L.Path),
            });
        };
        layer.addData(props.districts as GeoJSON.FeatureCollection);
    }, [props.districts, props.rateBreaks]);

    useEffect(() => {
        const map = mapRef.current!;
        const layer = districtLayerRef.current!;
        if (props.showDistricts) layer.addTo(map); else layer.remove();
    }, [props.showDistricts]);

    // Chamados
    useEffect(() => {
        const layer = issueLayerRef.current!;
        layer.clearLayers();
        markersRef.current.clear();
        for (const feature of props.issues) {
            const [lon, lat] = feature.geometry.coordinates;
            const p = feature.properties;
            const marker = L.marker([lat, lon], {
                icon: L.divIcon({className: 'issue-marker', html: shapeSvg(p.status, 24), iconSize: [24, 24]}),
                title: `${p.categoryName}, ${STATUS[p.status].label.toLowerCase()}, ${plural(p.reportCount, 'relato', 'relatos')}`,
                keyboard: true,
                riseOnHover: true,
            });
            const select = () => {
                if (!latest.current.picking) latest.current.onIssueSelect(feature.id);
            };
            marker.on('click', select);
            // O Leaflet só trata Enter em marcadores com popup: aqui o marcador abre o painel do chamado
            marker.on('keypress', (e: L.LeafletEvent) => {
                const key = (e as L.LeafletKeyboardEvent).originalEvent;
                if (key.key === 'Enter' || key.key === ' ') {
                    key.preventDefault();
                    select();
                }
            });
            marker.on('add', () => marker.getElement()?.setAttribute('role', 'button'));
            marker.addTo(layer);
            markersRef.current.set(feature.id, marker);
        }
    }, [props.issues]);

    useEffect(() => {
        markersRef.current.forEach((marker, id) => {
            marker.getElement()?.classList.toggle('is-selected', id === props.selectedIssueId);
            marker.setZIndexOffset(id === props.selectedIssueId ? 1000 : 0);
        });
    }, [props.selectedIssueId, props.issues]);

    // Ponto escolhido para o novo relato
    useEffect(() => {
        pinRef.current?.remove();
        pinRef.current = null;
        if (props.pickedPoint) {
            pinRef.current = L.marker(props.pickedPoint, {icon: pinIcon, keyboard: false, interactive: false})
                .addTo(mapRef.current!);
        }
    }, [props.pickedPoint]);

    useEffect(() => {
        containerRef.current!.style.cursor = props.picking ? 'crosshair' : '';
    }, [props.picking]);

    return <div ref={containerRef} className="absolute inset-0"/>;
});
