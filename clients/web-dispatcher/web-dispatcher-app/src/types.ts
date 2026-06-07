export type FlightStatus = 'SCHEDULED' | 'DEPARTED' | 'ARRIVED' | 'DELAYED' | 'CANCELLED';
export type SizeCategory = 'NARROW' | 'WIDE' | 'JUMBO';
export type PeriodicityType = 'WEEKLY' | 'INTERVAL';

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
  maxSizeCategory?: SizeCategory;
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
  periodicityType: PeriodicityType;
  periodicityStep: number;
  airline?: AirlineRs;
  slots?: ScheduleSlotRs[];
  /** При фильтрации по дате — вычисленные времена на этот день. */
  departureAtDate?: string;
  arrivalAtDate?: string;
  /** UI-hint: период в будущем, но шаблон выключен. */
  reactivationSuggested?: boolean;
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
  flightStatus?: FlightStatus;
  assignedFrom: string;
  assignedTo: string;
}

export interface PageRs<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
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
