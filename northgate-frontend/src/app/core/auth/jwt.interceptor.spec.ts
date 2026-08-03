import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from './auth.service';

describe('jwtInterceptor', () => {
  let authStub: { token: () => string | null; logout: ReturnType<typeof vi.fn> };
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let currentToken: string | null;

  beforeEach(() => {
    currentToken = 'signed-token';
    authStub = { token: () => currentToken, logout: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authStub },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function send(url: string): void {
    http.get(url).subscribe({ next: () => undefined, error: () => undefined });
  }

  it('attaches the bearer token to outgoing requests', () => {
    send('/api/toll/passes');

    const request = httpMock.expectOne('/api/toll/passes');
    expect(request.request.headers.get('Authorization')).toBe('Bearer signed-token');
    request.flush([]);
  });

  it('sends no Authorization header when signed out', () => {
    currentToken = null;
    send('/api/toll/vehicle-classes');

    const request = httpMock.expectOne('/api/toll/vehicle-classes');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush([]);
  });

  it('signs the user out when the token is rejected', () => {
    send('/api/toll/passes');

    httpMock.expectOne('/api/toll/passes').flush('nope', { status: 401, statusText: 'Unauthorized' });

    expect(authStub.logout).toHaveBeenCalled();
  });

  it('signs the user out when the token lacks the required role', () => {
    send('/api/toll/dashboard');

    httpMock.expectOne('/api/toll/dashboard').flush('nope', { status: 403, statusText: 'Forbidden' });

    expect(authStub.logout).toHaveBeenCalled();
  });

  it('leaves the session alone when a failed sign-in is rejected', () => {
    http.post('/api/toll/auth/login', {}).subscribe({ next: () => undefined, error: () => undefined });

    httpMock
      .expectOne('/api/toll/auth/login')
      .flush('bad pin', { status: 401, statusText: 'Unauthorized' });

    // A wrong PIN has to surface as a form error, not bounce through logout.
    expect(authStub.logout).not.toHaveBeenCalled();
  });

  it('leaves the session alone for other server errors', () => {
    send('/api/toll/shifts/current');

    httpMock
      .expectOne('/api/toll/shifts/current')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(authStub.logout).not.toHaveBeenCalled();
  });
});
