export type Role = 'OPERATOR' | 'MANAGER';

export interface Session {
  token: string;
  staffCode: string;
  fullName: string;
  role: Role;
  /** Lane the operator is on shift at; null for managers. */
  laneNumber: number | null;
}
