import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlateScan } from '../../shared/models/lane.model';

@Injectable({ providedIn: 'root' })
export class AuditApiService {
  private readonly http = inject(HttpClient);

  /** Latest ANPR read for a lane; 404 when no scan is waiting. */
  latestScan(laneNumber: number): Observable<PlateScan> {
    return this.http.get<PlateScan>('/api/audit/scans/latest', {
      params: { laneNumber },
    });
  }
}
