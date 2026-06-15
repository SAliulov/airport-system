import { apiClient, authHeaders } from '../http/client';
import type {
  ActualTimesRq,
  CreateFlightRq,
  DelayWarningRq,
  FlightGenerateRq,
  FlightGenerateRs,
  FlightBulkDeleteRs,
  FlightStatusUpdateRq,
  GateAssignmentRq,
} from '../../types/requests';
import type {
  FlightRs,
  GateAssignmentRs,
  PageRs,
  DelayWarningRs,
} from '../../types';

export type FlightListParams = {
  page?: number;
  size?: number;
  sort?: 'asc' | 'desc';
  date?: string;
  status?: string;
  airline?: number;
  origin?: string;
  destination?: string;
  terminal?: string;
  hourFrom?: number;
};

function flightListParams(params?: FlightListParams): Record<string, string | number> {
  const q: Record<string, string | number> = {};
  if (params?.page != null) q.page = params.page;
  if (params?.size != null) q.size = params.size;
  if (params?.sort) q.sort = params.sort;
  if (params?.date) q.date = params.date;
  if (params?.status) q.status = params.status;
  if (params?.airline != null) q.airline = params.airline;
  if (params?.origin) q.origin = params.origin;
  if (params?.destination) q.destination = params.destination;
  if (params?.terminal) q.terminal = params.terminal;
  if (params?.hourFrom != null) q.hourFrom = params.hourFrom;
  return q;
}

export const getAllFlights = (params?: FlightListParams) =>
  apiClient.get<PageRs<FlightRs>>('/flights', { params: flightListParams(params) }).then(r => r.data);

export const getFilteredFlights = (params?: FlightListParams) =>
  apiClient.get<PageRs<FlightRs>>('/flights/filter', { params: flightListParams(params) }).then(r => r.data);

export const searchFlights = (query: string, params?: FlightListParams) =>
  apiClient.get<PageRs<FlightRs>>('/flights/search', {
    params: { query, ...flightListParams(params) },
  }).then(r => r.data);

export const getFlightById = (id: number) =>
  apiClient.get<FlightRs>(`/flights/${id}`).then(r => r.data);

export const createFlight = (d: CreateFlightRq) =>
  apiClient.post<FlightRs>('/flights', d, { headers: authHeaders() }).then(r => r.data);

export const generateFlights = (d: FlightGenerateRq) =>
  apiClient.post<FlightGenerateRs>('/flights/generate', d, { headers: authHeaders() }).then(r => r.data);

export const deleteFlight = (id: number) =>
  apiClient.delete(`/flights/${id}`, { headers: authHeaders() });

export const deleteFlightsBySchedule = (scheduleId: number) =>
  apiClient.delete<FlightBulkDeleteRs>(`/flights/by-schedule/${scheduleId}`, { headers: authHeaders() })
    .then(r => r.data);

export const updateFlightStatus = (id: number, body: FlightStatusUpdateRq) =>
  apiClient.put<FlightRs>(`/flights/${id}/status`, body, { headers: authHeaders() }).then(r => r.data);

export const correctActualTimes = (id: number, body: ActualTimesRq) =>
  apiClient.put<FlightRs>(`/flights/${id}/actual-times`, body, { headers: authHeaders() }).then(r => r.data);

export const assignAircraft = (id: number, aircraftTypeId: number) =>
  apiClient.put<FlightRs>(
    `/flights/${id}/aircraft`,
    { aircraftTypeId } satisfies { aircraftTypeId: number },
    { headers: authHeaders() },
  ).then(r => r.data);

export const assignGate = (id: number, d: GateAssignmentRq) =>
  apiClient.post<GateAssignmentRs>(`/flights/${id}/gate-assignment`, d, { headers: authHeaders() }).then(r => r.data);

export const createDelayWarning = (flightId: number, d: DelayWarningRq) =>
  apiClient.post<DelayWarningRs>(
    `/flights/${flightId}/delay-warnings`,
    d,
    { headers: authHeaders() },
  ).then(r => r.data);
