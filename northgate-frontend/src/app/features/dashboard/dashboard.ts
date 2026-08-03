import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TollApiService } from '../../core/api/toll-api.service';
import { Dashboard as DashboardData, LaneStats, TrafficBucket } from '../../shared/models/dashboard.model';

const REFRESH_MS = 15_000;

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly toll = inject(TollApiService);
  private timer?: ReturnType<typeof setInterval>;

  protected readonly session = this.auth.session;
  protected readonly data = signal<DashboardData | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly updatedAt = signal<Date | null>(null);

  /** Tallest bar sets the scale; guards against divide-by-zero on an empty day. */
  protected readonly peakTraffic = computed(() =>
    Math.max(1, ...(this.data()?.trafficByHour ?? []).map((b) => b.vehicles))
  );

  async ngOnInit(): Promise<void> {
    await this.load();
    this.timer = setInterval(() => void this.load(), REFRESH_MS);
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  protected async load(): Promise<void> {
    try {
      this.data.set(await firstValueFrom(this.toll.dashboard()));
      this.updatedAt.set(new Date());
      this.error.set(null);
    } catch (e) {
      this.error.set(
        e instanceof HttpErrorResponse && e.status === 0
          ? 'Cannot reach the toll service.'
          : 'Could not load the plaza overview.'
      );
    }
  }

  protected barHeight(bucket: TrafficBucket): string {
    return `${Math.max(4, (bucket.vehicles / this.peakTraffic()) * 100)}%`;
  }

  protected hourLabel(hour: number): string {
    return `${String(hour).padStart(2, '0')}:00`;
  }

  protected operatorLabel(lane: LaneStats): string {
    return lane.operatorName ?? 'Unmanned (automated)';
  }

  protected statusLabel(status: string): string {
    return status.charAt(0) + status.slice(1).toLowerCase();
  }

  protected exceptionLabel(count: number): string {
    return `${count} open exception${count === 1 ? '' : 's'}`;
  }

  protected money(amount: number | null | undefined): string {
    return (amount ?? 0).toFixed(2);
  }

  protected count(n: number | null | undefined): string {
    return (n ?? 0).toLocaleString('en-US');
  }

  protected updatedLabel(): string {
    const at = this.updatedAt();
    if (!at) {
      return 'Loading…';
    }
    const seconds = Math.round((Date.now() - at.getTime()) / 1000);
    return seconds < 20 ? 'Updated just now' : `Updated ${seconds}s ago`;
  }

  protected signOut(): void {
    this.auth.logout();
  }
}
