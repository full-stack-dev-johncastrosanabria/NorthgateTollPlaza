import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Placeholder for the manager plaza overview. The backend aggregate endpoint
 * (GET /api/toll/dashboard) is not built yet — see ARCHITECTURE.md §2.
 */
@Component({
  selector: 'app-dashboard',
  template: `
    <header class="topbar">
      <div>
        <h1>Plaza overview</h1>
        <p class="sub">Northgate Toll Plaza</p>
      </div>
      <div class="topright">
        <div class="who">
          <span class="name">{{ session()?.fullName }}</span>
          <span class="mono cap">{{ session()?.staffCode }}</span>
        </div>
        <button class="ghost" (click)="signOut()">Sign out</button>
      </div>
    </header>

    <main>
      <div class="card">
        <h2>Not built yet</h2>
        <p>
          The manager dashboard — plaza KPIs, per-lane cards and traffic by hour — is the next
          milestone. The operator lane console is the vertical slice that is complete today.
        </p>
      </div>
    </main>
  `,
  styles: `
    .topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 14px 26px;
      background: var(--card);
      border-bottom: 1px solid var(--line);
    }
    h1 { margin: 0; font-size: 21px; }
    .sub { margin: 2px 0 0; font-size: 13.5px; color: var(--ink-muted); }
    .topright { display: flex; align-items: center; gap: 22px; }
    .who { display: flex; flex-direction: column; align-items: flex-end; }
    .who .name { font-size: 14px; font-weight: 600; }
    .cap { font-size: 11.5px; color: var(--ink-muted); text-transform: uppercase; letter-spacing: 0.05em; }
    .ghost {
      padding: 8px 14px; background: var(--card); border: 1px solid var(--line);
      border-radius: 8px; color: var(--ink); font-weight: 500;
    }
    main { padding: 22px 26px; }
    .card { max-width: 620px; padding: 22px; background: var(--card); border: 1px solid var(--line); border-radius: var(--radius); }
    h2 { margin: 0 0 8px; font-size: 16px; }
    p { margin: 0; color: var(--ink-muted); font-size: 14px; }
  `,
})
export class Dashboard {
  private readonly auth = inject(AuthService);
  protected readonly session = this.auth.session;

  protected signOut(): void {
    this.auth.logout();
  }
}
