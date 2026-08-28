import { apiClient, authHeaders } from '../http/client';
import type { DelayWarningRq } from '../../types/requests';
import type { DelayWarningRs } from '../../types';

export type DelayWarningListParams = {
  date?: string;
  airline?: number;
  query?: string;
};

function delayWarningListParams(params?: DelayWarningListParams): Record<string, string | number> {
  const q: Record<string, string | number> = {};
  if (params?.date) q.date = params.date;
  if (params?.airline != null) q.airline = params.airline;
  if (params?.query) q.query = params.query;
  return q;
}

export const getDelayWarnings = (params?: DelayWarningListParams) =>
  apiClient.get<DelayWarningRs[]>('/delay-warnings', { params: delayWarningListParams(params) }).then(r => r.data);

export const updateDelayWarning = (id: number, d: DelayWarningRq) =>
  apiClient.put<DelayWarningRs>(`/delay-warnings/${id}`, d, { headers: authHeaders() }).then(r => r.data);

export const deleteDelayWarning = (id: number) =>
  apiClient.delete(`/delay-warnings/${id}`, { headers: authHeaders() });
