import axios from 'axios';
import { API_BASE, APP_BASENAME } from '../config';
import { NETWORK_ERROR_MESSAGE } from '../utils/apiError';
import { clearSession, getToken } from './auth';
import type {
  AircraftTypeRs,
  AirlineRs,
  DelayWarningRs,
  FlightGenerateRq,
  FlightGenerateRs,
  FlightRs,
  GateAssignmentRs,
  GateRs,
  GateTimelineSegmentRs,
  ScheduleRs,
} from '../types';

function auth() {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}` } : {};
}

const ax = axios.create({ baseURL: `${API_BASE}/api/v1` });

/** Проверка доступности backend (без JWT). */
export function pingBackend(): Promise<void> {
  return ax.get('/airlines', { timeout: 3000 }).then(() => undefined);
}

function backendErrorText(data: unknown): string | null {
  if (!data) return null;
  if (typeof data === 'string') return data;
  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;
    const candidate = obj.message ?? obj.error ?? obj.detail;
    return typeof candidate === 'string' ? candidate : null;
  }
  return null;
}

function statusText(status?: number): string {
  switch (status) {
    case 400: return 'Некорректные данные запроса';
    case 401: return 'Требуется авторизация';
    case 403: return 'Недостаточно прав для операции';
    case 404: return 'Ресурс не найден';
    case 409: return 'Конфликт данных (например, занятый гейт)';
    case 500: return 'Внутренняя ошибка сервера';
    default: return status ? `HTTP ${status}` : NETWORK_ERROR_MESSAGE;
  }
}

ax.interceptors.response.use(
  response => response,
  error => {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status;
      const url = error.config?.url ?? '';
      const isLoginRequest = url.includes('/auth/login');
      if (status === 401 && !isLoginRequest) {
        clearSession();
        if (!window.location.pathname.endsWith('/login')) {
          window.location.assign(`${APP_BASENAME}/login`);
        }
      }
      const backend = backendErrorText(error.response?.data);
      const text = backend ?? statusText(status);
      return Promise.reject(new Error(text));
    }
    return Promise.reject(error);
  },
);

// ──── Airlines ────────────────────────────────────────────────────────────────
export const getAirlines = () => ax.get<AirlineRs[]>('/airlines').then(r => r.data);
export const createAirline = (d: object) =>
  ax.post<AirlineRs>('/airlines', d, { headers: auth() }).then(r => r.data);
export const updateAirline = (id: number, d: object) =>
  ax.put<AirlineRs>(`/airlines/${id}`, d, { headers: auth() }).then(r => r.data);
export const deleteAirline = (id: number) =>
  ax.delete(`/airlines/${id}`, { headers: auth() });

// ──── Aircraft types ───────────────────────────────────────────────────────────
export const getAircraftTypes = () => ax.get<AircraftTypeRs[]>('/aircraft-types').then(r => r.data);
export const createAircraftType = (d: object) =>
  ax.post<AircraftTypeRs>('/aircraft-types', d, { headers: auth() }).then(r => r.data);
export const updateAircraftType = (id: number, d: object) =>
  ax.put<AircraftTypeRs>(`/aircraft-types/${id}`, d, { headers: auth() }).then(r => r.data);
export const deleteAircraftType = (id: number) =>
  ax.delete(`/aircraft-types/${id}`, { headers: auth() });

// ──── Gates ────────────────────────────────────────────────────────────────────
export const getGates = () => ax.get<GateRs[]>('/gates').then(r => r.data);
export const createGate = (d: object) =>
  ax.post<GateRs>('/gates', d, { headers: auth() }).then(r => r.data);
export const updateGate = (id: number, d: object) =>
  ax.put<GateRs>(`/gates/${id}`, d, { headers: auth() }).then(r => r.data);
export const deleteGate = (id: number) =>
  ax.delete(`/gates/${id}`, { headers: auth() });

// ──── Schedules ────────────────────────────────────────────────────────────────
export const getSchedules = () =>
  ax.get<ScheduleRs[]>('/schedules').then(r => r.data);
export const getFilteredSchedules = (params?: object) =>
  ax.get<ScheduleRs[]>('/schedules/filter', { params }).then(r => r.data);
export const searchSchedules = (query: string, params?: object) =>
  ax.get<ScheduleRs[]>('/schedules/search', {
    params: { query, ...(params ?? {}) },
  }).then(r => r.data);
export const createSchedule = (d: object) =>
  ax.post<ScheduleRs>('/schedules', d, { headers: auth() }).then(r => r.data);
export const updateSchedule = (id: number, d: object) =>
  ax.put<ScheduleRs>(`/schedules/${id}`, d, { headers: auth() }).then(r => r.data);
export const deleteSchedule = (id: number) =>
  ax.delete(`/schedules/${id}`, { headers: auth() });

// ──── Flights ──────────────────────────────────────────────────────────────────
export const getAllFlights = () =>
  ax.get<FlightRs[]>('/flights').then(r => r.data);
export const getFilteredFlights = (params?: object) =>
  ax.get<FlightRs[]>('/flights/filter', { params }).then(r => r.data);
export const searchFlights = (query: string, params?: object) =>
  ax.get<FlightRs[]>('/flights/search', {
    params: { query, ...(params ?? {}) },
  }).then(r => r.data);
export const getFlightById = (id: number) =>
  ax.get<FlightRs>(`/flights/${id}`).then(r => r.data);
export const getAvailableGates = (flightId: number) =>
  ax.get<GateRs[]>(`/flights/${flightId}/available-gates`).then(r => r.data);
export const getCompatibleAircraftTypes = (flightId: number) =>
  ax.get<AircraftTypeRs[]>(`/flights/${flightId}/compatible-aircraft-types`).then(r => r.data);
export const createFlight = (d: { slotId: number; operationDate: string }) =>
  ax.post<FlightRs>('/flights', d, { headers: auth() }).then(r => r.data);
export const generateFlights = (d: FlightGenerateRq) =>
  ax.post<FlightGenerateRs>('/flights/generate', d, { headers: auth() }).then(r => r.data);
export const updateFlight = (id: number, d: object) =>
  ax.put<FlightRs>(`/flights/${id}`, d, { headers: auth() }).then(r => r.data);
export const deleteFlight = (id: number) =>
  ax.delete(`/flights/${id}`, { headers: auth() });
export const updateFlightStatus = (
  id: number,
  body: { status: string; actualDeparture?: string; actualArrival?: string },
) =>
  ax.put<FlightRs>(`/flights/${id}/status`, body, { headers: auth() }).then(r => r.data);
export const correctActualTimes = (
  id: number,
  body: { actualDeparture?: string; actualArrival?: string },
) =>
  ax.put<FlightRs>(`/flights/${id}/actual-times`, body, { headers: auth() }).then(r => r.data);
export const assignAircraft = (id: number, aircraftTypeId: number) =>
  ax.put<FlightRs>(
    `/flights/${id}/aircraft`,
    { aircraftTypeId },
    { headers: auth() },
  ).then(r => r.data);
export const assignGate = (id: number, d: object) =>
  ax.post<GateAssignmentRs>(`/flights/${id}/gate-assignment`, d, { headers: auth() }).then(r => r.data);

// ──── Delay warnings ──────────────────────────────────────────────────────────
export const getDelayWarnings = (flightId: number) =>
  ax.get<DelayWarningRs[]>(`/flights/${flightId}/delay-warnings`).then(r => r.data);
export const createDelayWarning = (flightId: number, d: object) =>
  ax.post<DelayWarningRs>(
    `/flights/${flightId}/delay-warnings`,
    d,
    { headers: auth() },
  ).then(r => r.data);

// ──── Timeline ─────────────────────────────────────────────────────────────────
export const getTimeline = (date: string) =>
  ax.get<GateTimelineSegmentRs[]>('/gates/timeline', {
    params: { date },
    headers: auth(),
  }).then(r => r.data);

// ──── Export ───────────────────────────────────────────────────────────────────
export function exportUrl(format: 'pdf' | 'excel', date: string) {
  return `${API_BASE}/api/v1/schedules/export/${format}?date=${date}`;
}
