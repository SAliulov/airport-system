import { apiClient, authHeaders } from '../http/client';
import type { AircraftTypeRq } from '../../types/requests';
import type { AircraftTypeRs } from '../../types';

export const getAircraftTypes = () =>
  apiClient.get<AircraftTypeRs[]>('/aircraft-types').then(r => r.data);

export const createAircraftType = (d: AircraftTypeRq) =>
  apiClient.post<AircraftTypeRs>('/aircraft-types', d, { headers: authHeaders() }).then(r => r.data);

export const updateAircraftType = (id: number, d: AircraftTypeRq) =>
  apiClient.put<AircraftTypeRs>(`/aircraft-types/${id}`, d, { headers: authHeaders() }).then(r => r.data);

export const deleteAircraftType = (id: number) =>
  apiClient.delete(`/aircraft-types/${id}`, { headers: authHeaders() });
