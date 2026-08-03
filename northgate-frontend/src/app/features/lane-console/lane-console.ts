import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TollApiService } from '../../core/api/toll-api.service';
import { AuditApiService } from '../../core/api/audit-api.service';
import { VehicleClass } from '../../shared/models/vehicle-class.model';
import { Pass, PaymentMethod } from '../../shared/models/pass.model';
import { LaneException, ShiftSummary } from '../../shared/models/lane.model';

@Component({
  selector: 'app-lane-console',
  imports: [FormsModule],
  templateUrl: './lane-console.html',
  styleUrl: './lane-console.css',
})
export class LaneConsole implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly toll = inject(TollApiService);
  private readonly audit = inject(AuditApiService);

  protected readonly session = this.auth.session;
  protected readonly paymentMethods: PaymentMethod[] = ['CASH', 'CARD', 'TAG'];

  protected readonly vehicleClasses = signal<VehicleClass[]>([]);
  protected readonly selectedClass = signal<VehicleClass | null>(null);
  protected readonly payment = signal<PaymentMethod>('CASH');
  protected readonly passes = signal<Pass[]>([]);
  protected readonly exceptions = signal<LaneException[]>([]);
  protected readonly shift = signal<ShiftSummary | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly submitting = signal(false);

  protected plate = '';

  protected readonly fareDue = computed(() => this.selectedClass()?.fare ?? 0);
  protected readonly openExceptions = computed(
    () => this.exceptions().filter((e) => e.status === 'OPEN').length
  );

  async ngOnInit(): Promise<void> {
    const classes = await firstValueFrom(this.toll.vehicleClasses());
    this.vehicleClasses.set(classes);
    this.selectedClass.set(classes.find((c) => c.code === 'CAR') ?? classes[0] ?? null);
    await this.refresh();
    await this.autoRead();
  }

  protected async refresh(): Promise<void> {
    const [passes, exceptions, shift] = await Promise.all([
      firstValueFrom(this.toll.recentPasses()),
      firstValueFrom(this.toll.exceptions()),
      firstValueFrom(this.toll.currentShift()),
    ]);
    this.passes.set(passes);
    this.exceptions.set(exceptions);
    this.shift.set(shift);
  }

  /** Pull the newest ANPR read for this lane out of MongoDB. */
  protected async autoRead(): Promise<void> {
    const lane = this.session()?.laneNumber;
    if (lane == null) {
      return;
    }
    try {
      const scan = await firstValueFrom(this.audit.latestScan(lane));
      this.plate = scan.plate;
      this.notice.set(`Auto-read ${scan.plate} (${Math.round(scan.confidence * 100)}% confidence)`);
    } catch {
      this.notice.set(null);
    }
  }

  protected select(vehicleClass: VehicleClass): void {
    this.selectedClass.set(vehicleClass);
  }

  protected async takePayment(): Promise<void> {
    const vehicleClass = this.selectedClass();
    if (!vehicleClass || !this.plate.trim() || this.submitting()) {
      return;
    }
    this.error.set(null);
    this.submitting.set(true);
    try {
      await firstValueFrom(
        this.toll.recordPass({
          vehicleClassCode: vehicleClass.code,
          plate: this.plate.trim(),
          paymentMethod: this.payment(),
        })
      );
      this.plate = '';
      this.notice.set(null);
      await this.refresh();
    } catch (e) {
      this.error.set(messageFor(e));
    } finally {
      this.submitting.set(false);
    }
  }

  protected async override(exception: LaneException): Promise<void> {
    this.error.set(null);
    try {
      await firstValueFrom(this.toll.overrideException(exception.id));
      await this.refresh();
    } catch (e) {
      this.error.set(messageFor(e));
    }
  }

  protected signOut(): void {
    this.auth.logout();
  }

  protected money(amount: number | null | undefined): string {
    return (amount ?? 0).toFixed(2);
  }

  protected time(iso: string): string {
    return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  }

  protected shiftWindow(shift: ShiftSummary): string {
    return `${this.time(shift.startsAt)}–${this.time(shift.endsAt)}`;
  }

  protected badgeLabel(method: PaymentMethod): string {
    return method.charAt(0) + method.slice(1).toLowerCase();
  }

  protected typeLabel(type: string): string {
    return type.replace('_', ' ');
  }
}

function messageFor(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) {
      return 'Cannot reach the toll service.';
    }
    return e.error?.message ?? 'Request failed';
  }
  return 'Request failed';
}
