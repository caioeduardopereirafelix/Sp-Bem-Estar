export type IssueStatus = 'OPEN' | 'CONFIRMED' | 'IN_PROGRESS' | 'RESOLVED';

export interface Category {
    code: string;
    name: string;
}

export interface IssueFeature {
    type: 'Feature';
    id: number;
    geometry: {type: 'Point'; coordinates: [number, number]};
    properties: {
        category: string;
        categoryName: string;
        status: IssueStatus;
        reportCount: number;
        priorityScore: number;
        neighborhood: string | null;
        firstReportedAt: string;
        lastReportedAt: string;
    };
}

export interface NeighborhoodStats {
    id: number;
    name: string;
    city: string;
    population: number | null;
    openIssues: number;
    resolvedIssues: number;
    openReports: number;
    openIssuesPer10k: number | null;
    averagePriority: number | null;
}

export interface NeighborhoodFeature {
    type: 'Feature';
    id: number;
    geometry: GeoJSON.MultiPolygon;
    properties: NeighborhoodStats;
}

export interface FeatureCollection<F> {
    type: 'FeatureCollection';
    features: F[];
}

export interface IssueDetail {
    id: number;
    category: string;
    categoryName: string;
    status: IssueStatus;
    latitude: number;
    longitude: number;
    neighborhood: string | null;
    reportCount: number;
    priorityScore: number;
    firstReportedAt: string;
    lastReportedAt: string;
    resolvedAt: string | null;
    reports: {id: number; createdAt: string; photoUrl: string; classificationStatus: string}[];
    history: {from: IssueStatus | null; to: IssueStatus; changedAt: string}[];
}

export interface ReportResult {
    reportId: number;
    issueId: number;
    newIssue: boolean;
    issueReportCount: number;
    issueStatus: IssueStatus;
}

export class ApiError extends Error {
    constructor(message: string, readonly status: number, readonly fieldErrors?: Record<string, string>) {
        super(message);
    }
}

const BASE = '/api/v1';

// O backend responde erros como ProblemDetail (RFC 9457): usa o `detail`, que já vem em português
async function request<T>(path: string, init?: RequestInit): Promise<T> {
    let response: Response;
    try {
        response = await fetch(BASE + path, init);
    } catch (error) {
        if ((error as Error).name === 'AbortError') throw error;
        throw new ApiError('Sem conexão com o servidor. Verifique a internet e tente de novo.', 0);
    }
    const isJson = response.headers.get('content-type')?.includes('json');
    const body = isJson ? await response.json() : null;
    if (!response.ok) {
        const message = body?.detail || body?.title || (response.status === 413
            ? 'A foto passa de 10 MB. Escolha uma imagem menor.'
            : `O servidor respondeu com erro ${response.status}.`);
        throw new ApiError(message, response.status, body?.errors);
    }
    return body as T;
}

export const api = {
    categories: () => request<Category[]>('/categories'),

    neighborhoods: (city: string) =>
        request<FeatureCollection<NeighborhoodFeature>>(`/neighborhoods?city=${encodeURIComponent(city)}`),

    issues: (params: {bbox: [number, number, number, number]; category?: string; status?: IssueStatus}, signal?: AbortSignal) => {
        const query = new URLSearchParams({bbox: params.bbox.map((v) => v.toFixed(6)).join(',')});
        if (params.category) query.set('category', params.category);
        if (params.status) query.set('status', params.status);
        return request<FeatureCollection<IssueFeature>>(`/issues?${query}`, {signal});
    },

    issue: (id: number, signal?: AbortSignal) => request<IssueDetail>(`/issues/${id}`, {signal}),

    changeStatus: (id: number, status: IssueStatus) => request<{to: IssueStatus}>(`/issues/${id}/status`, {
        method: 'PATCH',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({status}),
    }),

    createReport: (data: {category: string; latitude: number; longitude: number; photo: File}) => {
        const form = new FormData();
        form.set('category', data.category);
        form.set('latitude', data.latitude.toFixed(6));
        form.set('longitude', data.longitude.toFixed(6));
        form.set('photo', data.photo);
        return request<ReportResult>('/reports', {method: 'POST', body: form});
    },
};
