import axios from 'axios';
import { API_BASE } from '../config';
import { getToken } from './auth';
import type {
  AircraftTypeRs,
  AirlineRs,
  DelayWarningRs,
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

// ──── Schedules ────────────────────────────────────────────────────────────────
export const getSchedules = (params?: object) =>
  ax.get<ScheduleRs[]>('/schedules', { params }).then(r => r.data);
export const createSchedule = (d: object) =>
  ax.post<ScheduleRs>('/schedules', d, { headers: auth() }).then(r => r.data);
export const updateSchedule = (id: number, d: object) =>
  ax.put<ScheduleRs>(`/schedules/${id}`, d, { headers: auth() }).then(r => r.data);
export const deleteSchedule = (id: number) =>
  ax.delete(`/schedules/${id}`, { headers: auth() });

// ──── Flights ──────────────────────────────────────────────────────────────────
export const getFlights = (params?: object) =>
  ax.get<FlightRs[]>('/flights', { params }).then(r => r.data);
export const getFlightById = (id: number) =>
  ax.get<FlightRs>(`/flights/${id}`).then(r => r.data);
export const createFlight = (d: object) =>
  ax.post<FlightRs>('/flights', d, { headers: auth() }).then(r => r.data);
export const deleteFlight = (id: number) =>
  ax.delete(`/flights/${id}`, { headers: auth() });
export const updateFlightStatus = (id: number, status: string) =>
  ax.put<FlightRs>(`/flights/${id}/status`, { status }, { headers: auth() }).then(r => r.data);
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
