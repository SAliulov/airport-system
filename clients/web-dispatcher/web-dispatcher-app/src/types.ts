export type FlightStatus = 'SCHEDULED' | 'DEPARTED' | 'ARRIVED' | 'DELAYED' | 'CANCELLED';
export type SizeCategory = 'NARROW' | 'WIDE' | 'JUMBO';

export interface AirlineRs {
  airlineId: number;
  iataCode: string;
  name: string;
  country?: string;
}

export interface AircraftTypeRs {
  aircraftTypeId: number;
  icaoCode: string;
  passengerCapacity?: number;
  sizeCategory?: SizeCategory;
}

export interface GateRs {
  gateId: number;
  gateNumber: string;
  terminal?: string;
  isActive: boolean;
  maxSizeCategory?: SizeCategory;
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

export interface DelayWarningRs {
  warningId: number;
  delayMinutes: number;
  reason?: string;
  createdAt?: string;
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
  aircraftType?: AircraftTypeRs;
  currentGateAssignment?: GateAssignmentRs;
  gateAssignments?: GateAssignmentRs[];
  delayWarnings?: DelayWarningRs[];
}

export interface GateTimelineSegmentRs {
  gateId: number;
  gateNumber: string;
  terminal?: string;
  flightId: number;
  flightNumber: string;
  assignedFrom: string;
  assignedTo: string;
}

export interface LoginRs {
  accessToken: string;
  tokenType: string;
  role: string;
}

export interface UserProfileRs {
  userId: number;
  username: string;
  role: string;
}
