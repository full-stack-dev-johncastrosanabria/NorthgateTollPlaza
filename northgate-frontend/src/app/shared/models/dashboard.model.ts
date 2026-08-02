import { LaneStatus } from './lane.model';

export type LaneMode = 'MANNED' | 'AUTOMATED';

export interface LaneStats {
  laneNumber: number;
  status: LaneStatus;
  mode: LaneMode;
  /** Null on unmanned automated lanes. */
  operatorName: string | null;
  queueLength: number;
  vehiclesToday: number;
  revenue: number;
  openExceptions: number;
}

export interface TrafficBucket {
  hour: number;
  vehicles: number;
}

export interface Dashboard {
  revenueToday: number;
  vehiclesToday: number;
  lanesOpen: number;
  lanesTotal: number;
  vehiclesQueued: number;
  exceptionsAwaitingReview: number;
  lanes: LaneStats[];
  trafficByHour: TrafficBucket[];
}
