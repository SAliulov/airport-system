/** Request DTOs для REST API (суффикс Rq). */

export interface AirlineRq {
  iataCode: string;
  name: string;
  country?: string;
}

export interface AircraftTypeRq {
  icaoCode: string;
  passengerCapacity?: number;
  sizeCategory: string;
}

export interface GateRq {
  gateNumber: string;
  terminal?: string;
  isActive: boolean;
  maxSizeCategory: string;
}

export interface ScheduleSlotRq {
  slotId?: number;
  dayOfWeek?: number | null;
  departureTime: string;
  arrivalTime: string;
}

export interface ScheduleRq {
  flightNumber: string;
  originAirport: string;
  destinationAirport: string;
  effectiveFrom: string;
  effectiveTo?: string | null;
  isActive: boolean;
  periodicityType: string;
  periodicityStep: number;
  airlineId: number;
  slots: ScheduleSlotRq[];
}

export interface CreateFlightRq {
  slotId: number;
  operationDate: string;
}

export interface FlightStatusUpdateRq {
  status: string;
  actualDeparture?: string;
  actualArrival?: string;
}

export interface ActualTimesRq {
  actualDeparture?: string;
  actualArrival?: string;
}

export interface GateAssignmentRq {
  gateId: number;
  assignedFrom: string;
  assignedTo: string;
}

export interface DelayWarningRq {
  delayMinutes: number;
  reason?: string;
}

export interface FlightGenerateRq {
  fromDate: string;
  toDate: string;
  scheduleId?: number;
}

export interface FlightGenerateRs {
  created: number;
  skipped: number;
}

export interface FlightBulkDeleteRs {
  scheduleId: number;
  deleted: number;
}
