import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Session } from '../../shared/models/session.model';

const STORAGE_KEY = 'northgate.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _session = signal<Session | null>(readStoredSession());

  readonly session = this._session.asReadonly();
  readonly isAuthenticated = computed(() => this._session() !== null);
  readonly role = computed(() => this._session()?.role ?? null);

  async login(staffCode: string, pin: string): Promise<Session> {
    const session = await firstValueFrom(
      this.http.post<Session>('/api/toll/auth/login', { staffCode, pin })
    );
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this._session.set(session);
    return session;
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this._session.set(null);
    this.router.navigate(['/login']);
  }

  token(): string | null {
    return this._session()?.token ?? null;
  }

  /** Where this session belongs after signing in. */
  homeRoute(): string {
    return this._session()?.role === 'MANAGER' ? '/dashboard' : '/lane';
  }
}

function readStoredSession(): Session | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as Session;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}
