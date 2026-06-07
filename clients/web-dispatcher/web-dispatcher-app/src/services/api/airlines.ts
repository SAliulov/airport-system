import { apiClient, authHeaders } from '../http/client';
import type { AirlineRq } from '../../types/requests';
import type { AirlineRs } from '../../types';

/** Список авиакомпаний. */
export const getAirlines = () => apiClient.get<AirlineRs[]>('/airlines').then(r => r.data);

/** Создание авиакомпании (DISPATCHER). */
export const createAirline = (d: AirlineRq) =>
  apiClient.post<AirlineRs>('/airlines', d, { headers: authHeaders() }).then(r => r.data);

/** Обновление авиакомпании (DISPATCHER). */
export const updateAirline = (id: number, d: AirlineRq) =>
  apiClient.put<AirlineRs>(`/airlines/${id}`, d, { headers: authHeaders() }).then(r => r.data);

/** Удаление авиакомпании (DISPATCHER). */
export const deleteAirline = (id: number) =>
  apiClient.delete(`/airlines/${id}`, { headers: authHeaders() });
