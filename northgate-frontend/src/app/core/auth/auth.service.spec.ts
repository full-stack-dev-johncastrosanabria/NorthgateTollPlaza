import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Session } from '../../shared/models/session.model';

const STORAGE_KEY = 'northgate.session';

const OPERATOR: Session = {
  token: 'signed-token',
  staffCode: 'OP-14',
  fullName: 'R. Alvarez',
  role: 'OPERATOR',
  laneNumber: 3,
};

const MANAGER: Session = { ...OPERATOR, staffCode: 'MG-02', role: 'MANAGER', laneNumber: null };

describe('AuthService', () => {
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.clear();
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
  });

  afterEach(() => localStorage.clear());

  function service(): AuthService {
    return TestBed.inject(AuthService);
  }

  it('starts signed out when nothing is stored', () => {
    const auth = service();

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.session()).toBeNull();
    expect(auth.token()).toBeNull();
  });

  it('restores a stored session so a refresh does not sign the operator out', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(OPERATOR));

    const auth = service();

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.session()).toEqual(OPERATOR);
    expect(auth.token()).toBe('signed-token');
  });

  it('discards corrupt stored data instead of crashing on start-up', () => {
    localStorage.setItem(STORAGE_KEY, 'not-json{');

    const auth = service();

    expect(auth.session()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('posts the credentials and keeps the returned session', async () => {
    const auth = service();
    const httpMock = TestBed.inject(HttpTestingController);

    const pending = auth.login('op-14', '1234');
    const request = httpMock.expectOne('/api/toll/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ staffCode: 'op-14', pin: '1234' });
    request.flush(OPERATOR);
    await pending;

    expect(auth.session()).toEqual(OPERATOR);
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual(OPERATOR);
    httpMock.verify();
  });

  it('clears the stored session and returns to the login screen on sign out', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(OPERATOR));
    const auth = service();

    auth.logout();

    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('sends each role to its own home screen', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(MANAGER));
    expect(service().homeRoute()).toBe('/dashboard');

    TestBed.resetTestingModule();
    localStorage.setItem(STORAGE_KEY, JSON.stringify(OPERATOR));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    expect(TestBed.inject(AuthService).homeRoute()).toBe('/lane');
  });
});
