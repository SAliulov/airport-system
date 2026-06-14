import type { AirportConfigRs } from '../types/airportConfig';

let runtime: AirportConfigRs | null = null;

/** Устанавливается AirportConfigProvider после успешного GET /api/v1/airport. */
export function setAirportRuntime(config: AirportConfigRs): void {
  runtime = config;
}

export function clearAirportRuntime(): void {
  runtime = null;
}

function getAirportRuntime(): AirportConfigRs {
  if (!runtime) {
    throw new Error('Airport config not loaded');
  }
  return runtime;
}

export function getHomeIata(): string {
  return getAirportRuntime().homeIata.trim().toUpperCase();
}

export function getAirportTimezone(): string {
  return getAirportRuntime().timezone;
}
