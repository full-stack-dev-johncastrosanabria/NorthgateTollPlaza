export type ExceptionType = 'UNREAD_TAG' | 'VIOLATION' | 'OVERPAYMENT';
export type ExceptionStatus = 'OPEN' | 'CLEARED' | 'OVERRIDDEN';

export interface LaneException {
  id: number;
  plate: string | null;
  type: ExceptionType;
  description: string;
  status: ExceptionStatus;
  createdAt: string;
}

export interface ShiftSummary {
  shiftId: number;
  laneNumber: number;
  operatorName: string;
  staffCode: string;
  startsAt: string;
  endsAt: string;
  vehicles: number;
  collected: number;
  openExceptions: number;
}

export interface PlateScan {
  id: string;
  laneNumber: number;
  plate: string;
  confidence: number;
  tagId: string | null;
  scannedAt: string;
}
