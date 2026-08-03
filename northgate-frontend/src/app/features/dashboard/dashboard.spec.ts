import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Dashboard } from './dashboard';
import { AuthService } from '../../core/auth/auth.service';
import { Dashboard as DashboardData } from '../../shared/models/dashboard.model';

const PAYLOAD: DashboardData = {
  revenueToday: 7632,
  vehiclesToday: 1574,
  lanesOpen: 4,
  lanesTotal: 6,
  vehiclesQueued: 18,
  exceptionsAwaitingReview: 6,
  lanes: [
    {
      laneNumber: 1, status: 'OPEN', mode: 'MANNED', operatorName: 'M. Iqbal',
      queueLength: 2, vehiclesToday: 390, revenue: 2137, openExceptions: 1,
    },
    {
      laneNumber: 5, status: 'OPEN', mode: 'AUTOMATED', operatorName: null,
      queueLength: 1, vehiclesToday: 391, revenue: 1627.5, openExceptions: 0,
    },
  ],
  trafficByHour: [
    { hour: 5, vehicles: 88 },
    { hour: 8, vehicles: 402 },
    { hour: 10, vehicles: 231 },
  ],
};

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            session: () => ({ fullName: 'D. Okafor', staffCode: 'MG-02', role: 'MANAGER' }),
            logout: vi.fn(),
          },
        },
      ],
    });

    fixture = TestBed.createComponent(Dashboard);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    fixture.destroy();
    httpMock.verify();
  });

  async function render(data: DashboardData): Promise<HTMLElement> {
    fixture.detectChanges();
    httpMock.expectOne('/api/toll/dashboard').flush(data);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the plaza headline figures', async () => {
    const page = await render(PAYLOAD);

    const kpis = [...page.querySelectorAll('.kpi-value')].map((el) => el.textContent!.trim());
    expect(kpis).toEqual(['$7632.00', '1,574', '4 / 6', '18']);
    expect(page.textContent).toContain('6 exceptions awaiting review');
  });

  it('labels an automated lane as unmanned and flags open exceptions', async () => {
    const page = await render(PAYLOAD);

    const lanes = page.querySelectorAll('.lane');
    expect(lanes).toHaveLength(2);
    expect(lanes[1].textContent).toContain('Unmanned (automated)');
    expect(lanes[0].textContent).toContain('1 open exception');
    // Singular vs plural, and no banner at all on a clean lane.
    expect(lanes[1].querySelector('.lane-exc')).toBeNull();
  });

  it('scales the busiest hour to a full-height bar', async () => {
    const page = await render(PAYLOAD);

    const heights = [...page.querySelectorAll<HTMLElement>('.bar')].map((bar) => bar.style.height);
    // 88/402 and 231/402 of the tallest, which is pinned at 100%.
    expect(heights[1]).toBe('100%');
    expect(parseFloat(heights[0])).toBeCloseTo(21.9, 1);
    expect(parseFloat(heights[2])).toBeCloseTo(57.5, 1);
  });

  it('shows an empty chart message rather than dividing by zero on a quiet day', async () => {
    const page = await render({ ...PAYLOAD, trafficByHour: [] });

    expect(page.querySelectorAll('.bar-col')).toHaveLength(0);
    expect(page.querySelector('.chart .empty')?.textContent).toContain('No traffic recorded today yet');
  });

  it('draws flat bars rather than NaN when every hour recorded zero vehicles', async () => {
    const page = await render({
      ...PAYLOAD,
      trafficByHour: [
        { hour: 5, vehicles: 0 },
        { hour: 6, vehicles: 0 },
      ],
    });

    // The peak is 0 here, so an unguarded divide would give every bar NaN%.
    const heights = [...page.querySelectorAll<HTMLElement>('.bar')].map((bar) => bar.style.height);
    expect(heights).toEqual(['4%', '4%']);
  });

  it('reports a failure instead of rendering a blank page', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/toll/dashboard').flush('boom', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.error')?.textContent)
      .toContain('Could not load the plaza overview');
  });
});
