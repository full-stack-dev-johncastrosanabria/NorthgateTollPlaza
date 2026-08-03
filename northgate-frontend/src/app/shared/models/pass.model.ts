export type PaymentMethod = 'CASH' | 'CARD' | 'TAG';

export interface Pass {
  id: number;
  plate: string;
  vehicleClassCode: string;
  vehicleClassLabel: string;
  paymentMethod: PaymentMethod;
  amount: number;
  createdAt: string;
}

export interface PassRequest {
  vehicleClassCode: string;
  plate: string;
  paymentMethod: PaymentMethod;
}
