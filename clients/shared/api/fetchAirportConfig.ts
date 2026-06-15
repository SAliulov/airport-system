import type { AirportConfigRs } from '../types/airportConfig';

export async function fetchAirportConfig(apiBase: string): Promise<AirportConfigRs> {
  const base = apiBase.replace(/\/$/, '');
  const resp = await fetch(`${base}/api/v1/airport`);
  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}`);
  }
  const data = (await resp.json()) as AirportConfigRs;
  if (!data.homeIata || !data.timezone) {
    throw new Error('Invalid airport config response');
  }
  return {
    homeIata: data.homeIata.trim().toUpperCase(),
    timezone: data.timezone,
    gatePlanWindowHours: data.gatePlanWindowHours,
    gatePostGraceMinutes: data.gatePostGraceMinutes,
  };
}
