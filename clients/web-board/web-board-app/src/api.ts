import { API_BASE } from './config';
import type { AirlineRs, FlightRs, FlightStatus, GateRs, PageRs } from './types';

function toHttpError(status: number): string {
  switch (status) {
    case 400: return 'Некорректные параметры запроса';
    case 401: return 'Требуется авторизация';
    case 403: return 'Недостаточно прав';
    case 404: return 'Ресурс не найден';
    case 409: return 'Конфликт данных';
    case 500: return 'Внутренняя ошибка сервера';
    default: return `HTTP ${status}`;
  }
}

export type FlightFetchParams = {
  date: string;
  search?: string;
  status?: FlightStatus | '';
  origin?: string;
  destination?: string;
  airline?: number;
  terminal?: string;
  hourFrom?: number;
  page?: number;
  size?: number;
};

function buildQuery(params: FlightFetchParams): URLSearchParams {
  const q = new URLSearchParams();
  q.set('date', params.date);
  if (params.search) q.set('query', params.search);
  if (params.status) q.set('status', params.status);
  if (params.origin) q.set('origin', params.origin);
  if (params.destination) q.set('destination', params.destination);
  if (params.airline != null) q.set('airline', String(params.airline));
  if (params.terminal) q.set('terminal', params.terminal);
  if (params.hourFrom != null) q.set('hourFrom', String(params.hourFrom));
  q.set('page', String(params.page ?? 0));
  q.set('size', String(params.size ?? 20));
  return q;
}

export async function fetchFlightsPage(params: FlightFetchParams): Promise<PageRs<FlightRs>> {
  const search = params.search?.trim();
  const q = buildQuery(params);
  const path = search
    ? `${API_BASE}/api/v1/flights/search?${q}`
    : `${API_BASE}/api/v1/flights/filter?${q}`;
  const res = await fetch(path);
  if (!res.ok) throw new Error(toHttpError(res.status));
  return res.json() as Promise<PageRs<FlightRs>>;
}

export async function fetchAirlines(): Promise<AirlineRs[]> {
  const res = await fetch(`${API_BASE}/api/v1/airlines`);
  if (!res.ok) throw new Error(toHttpError(res.status));
  return res.json() as Promise<AirlineRs[]>;
}

export async function fetchGates(): Promise<GateRs[]> {
  const res = await fetch(`${API_BASE}/api/v1/gates`);
  if (!res.ok) throw new Error(toHttpError(res.status));
  return res.json() as Promise<GateRs[]>;
}
