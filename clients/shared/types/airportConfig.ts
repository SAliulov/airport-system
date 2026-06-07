/** Публичная конфигурация аэропорта (GET /api/v1/airport). */
export interface AirportConfigRs {
  homeIata: string;
  timezone: string;
  gatePlanWindowHours: number;
  gatePostGraceMinutes: number;
}
