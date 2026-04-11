export type FlightStatus = 'SCHEDULED' | 'DEPARTED' | 'ARRIVED' | 'DELAYED' | 'CANCELLED';

export interface AirlineRs {
  airlineId: number;
  iataCode: string;
  name: string;
}

export interface GateSummaryRs {
  gateId: number;
  gateNumber: string;
  terminal?: string;
}

export interface GateAssignmentRs {
  assignmentId: number;
  assignedFrom?: string;
  assignedTo?: string;
  gate?: GateSummaryRs;
}

export interface ScheduleRs {
  scheduleId: number;
  flightNumber: string;
  originAirport: string;
  destinationAirport: string;
  scheduledDeparture: string;
  scheduledArrival: string;
  airline?: AirlineRs;
}

export interface FlightRs {
  flightId: number;
  status: FlightStatus;
  actualDeparture?: string;
  actualArrival?: string;
  schedule: ScheduleRs;
  currentGateAssignment?: GateAssignmentRs;
}

export interface FlightStatusPush {
  flightId: number;
  status: string;
}
