import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { VehicleClass } from '../../shared/models/vehicle-class.model';
import { Pass, PassRequest } from '../../shared/models/pass.model';
import { LaneException, ShiftSummary } from '../../shared/models/lane.model';
import { Dashboard } from '../../shared/models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class TollApiService {
  private readonly http = inject(HttpClient);

  vehicleClasses(): Observable<VehicleClass[]> {
    return this.http.get<VehicleClass[]>('/api/toll/vehicle-classes');
  }

  recentPasses(): Observable<Pass[]> {
    return this.http.get<Pass[]>('/api/toll/passes');
  }

  recordPass(request: PassRequest): Observable<Pass> {
    return this.http.post<Pass>('/api/toll/passes', request);
  }

  currentShift(): Observable<ShiftSummary> {
    return this.http.get<ShiftSummary>('/api/toll/shifts/current');
  }

  exceptions(): Observable<LaneException[]> {
    return this.http.get<LaneException[]>('/api/toll/exceptions');
  }

  overrideException(id: number): Observable<LaneException> {
    return this.http.post<LaneException>(`/api/toll/exceptions/${id}/override`, {});
  }

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/toll/dashboard');
  }
}
