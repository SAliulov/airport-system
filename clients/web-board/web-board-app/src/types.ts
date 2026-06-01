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

export interface ScheduleSlotRs {
  slotId: number;
  dayOfWeek?: number;
  departureTime: string;
  arrivalTime: string;
}

export interface ScheduleRs {
  scheduleId: number;
  flightNumber: string;
  originAirport: string;
  destinationAirport: string;
  effectiveFrom: string;
  effectiveTo?: string;
  isActive?: boolean;
  periodicityType?: string;
  periodicityStep?: number;
  airline?: AirlineRs;
  slots?: ScheduleSlotRs[];
  departureAtDate?: string;
  arrivalAtDate?: string;
}

export interface FlightRs {
  flightId: number;
  status: FlightStatus;
  operationDate?: string;
  scheduledDeparture?: string;
  scheduledArrival?: string;
  actualDeparture?: string;
  actualArrival?: string;
  schedule: ScheduleRs;
  currentGateAssignment?: GateAssignmentRs;
}

export interface FlightStatusPush {
  flightId: number;
  status: string;
}

export interface GateAssignmentPush {
  flightId: number;
  assignment: GateAssignmentRs;
}

export interface DelayWarningPush {
  flightId: number;
  warning: {
    warningId: number;
    delayMinutes: number;
    reason?: string;
    createdAt?: string;
  };
}
