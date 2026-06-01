import { API_BASE } from './config';
import type { AirlineRs, FlightRs } from './types';

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

export async function fetchFlights(params: {
  date?: string;
  origin?: string;
  destination?: string;
  airline?: number;
}): Promise<FlightRs[]> {
  const q = new URLSearchParams();
  if (params.date) q.set('date', params.date);
  if (params.origin) q.set('origin', params.origin);
  if (params.destination) q.set('destination', params.destination);
  if (params.airline != null) q.set('airline', String(params.airline));

  const hasFilters = q.size > 0;
  const endpoint = hasFilters ? '/api/v1/flights/filter' : '/api/v1/flights';
  const url = hasFilters ? `${API_BASE}${endpoint}?${q}` : `${API_BASE}${endpoint}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(toHttpError(res.status));
  return res.json() as Promise<FlightRs[]>;
}

export async function fetchAirlines(): Promise<AirlineRs[]> {
  const res = await fetch(`${API_BASE}/api/v1/airlines`);
  if (!res.ok) throw new Error(toHttpError(res.status));
  return res.json() as Promise<AirlineRs[]>;
}
