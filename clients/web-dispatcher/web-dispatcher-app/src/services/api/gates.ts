import { apiClient, authHeaders } from '../http/client';
import type { GateRq } from '../../types/requests';
import type { GateRs, GateTimelineSegmentRs } from '../../types';

export const getGates = () => apiClient.get<GateRs[]>('/gates').then(r => r.data);

export const createGate = (d: GateRq) =>
  apiClient.post<GateRs>('/gates', d, { headers: authHeaders() }).then(r => r.data);

export const updateGate = (id: number, d: GateRq) =>
  apiClient.put<GateRs>(`/gates/${id}`, d, { headers: authHeaders() }).then(r => r.data);

export const deleteGate = (id: number) =>
  apiClient.delete(`/gates/${id}`, { headers: authHeaders() });

/** Timeline занятости гейтов на дату. */
export const getTimeline = (date: string) =>
  apiClient.get<GateTimelineSegmentRs[]>('/gates/timeline', {
    params: { date },
    headers: authHeaders(),
  }).then(r => r.data);
