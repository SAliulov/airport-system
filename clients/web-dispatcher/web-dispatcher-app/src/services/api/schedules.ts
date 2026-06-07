import { apiClient, authHeaders } from '../http/client';
import type { ScheduleRq } from '../../types/requests';
import type { ScheduleRs } from '../../types';

export const getSchedules = () =>
  apiClient.get<ScheduleRs[]>('/schedules').then(r => r.data);

export const getFilteredSchedules = (params?: Record<string, string | number>) =>
  apiClient.get<ScheduleRs[]>('/schedules/filter', { params }).then(r => r.data);

export const searchSchedules = (query: string, params?: Record<string, string | number>) =>
  apiClient.get<ScheduleRs[]>('/schedules/search', {
    params: { query, ...(params ?? {}) },
  }).then(r => r.data);

export const createSchedule = (d: ScheduleRq | Record<string, unknown>) =>
  apiClient.post<ScheduleRs>('/schedules', d, { headers: authHeaders() }).then(r => r.data);

export const updateSchedule = (id: number, d: ScheduleRq | Record<string, unknown>) =>
  apiClient.put<ScheduleRs>(`/schedules/${id}`, d, { headers: authHeaders() }).then(r => r.data);

export const deleteSchedule = (id: number) =>
  apiClient.delete(`/schedules/${id}`, { headers: authHeaders() });
