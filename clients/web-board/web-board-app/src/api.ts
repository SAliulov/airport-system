import { API_BASE } from './config';
import type { AirlineRs, FlightRs } from './types';

export async function fetchFlights(params: {
  date?: string;
  direction?: string;
  airline?: number;
}): Promise<FlightRs[]> {
  const q = new URLSearchParams();
  if (params.date) q.set('date', params.date);
  if (params.direction) q.set('direction', params.direction);
  if (params.airline != null) q.set('airline', String(params.airline));

  const res = await fetch(`${API_BASE}/api/v1/flights?${q}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json() as Promise<FlightRs[]>;
}

export async function fetchAirlines(): Promise<AirlineRs[]> {
  const res = await fetch(`${API_BASE}/api/v1/airlines`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json() as Promise<AirlineRs[]>;
}
