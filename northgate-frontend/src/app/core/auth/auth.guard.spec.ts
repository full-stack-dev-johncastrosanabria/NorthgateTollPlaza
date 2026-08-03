import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard, roleGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { Role } from '../../shared/models/session.model';

describe('route guards', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  function configure(signedIn: boolean, role: Role | null): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: () => signedIn,
            role: () => role,
            homeRoute: () => (role === 'MANAGER' ? '/dashboard' : '/lane'),
          },
        },
      ],
    });
  }

  function runAuthGuard(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() => authGuard(route, state)) as boolean | UrlTree;
  }

  function runRoleGuard(required: Role): boolean | UrlTree {
    return TestBed.runInInjectionContext(() => roleGuard(required)(route, state)) as boolean | UrlTree;
  }

  describe('authGuard', () => {
    it('lets a signed-in user through', () => {
      configure(true, 'OPERATOR');

      expect(runAuthGuard()).toBe(true);
    });

    it('sends an anonymous visitor to the login screen', () => {
      configure(false, null);

      expect(runAuthGuard().toString()).toBe('/login');
    });
  });

  describe('roleGuard', () => {
    it('lets the matching role through', () => {
      configure(true, 'MANAGER');

      expect(runRoleGuard('MANAGER')).toBe(true);
    });

    it('bounces an operator off the manager dashboard to their own console', () => {
      configure(true, 'OPERATOR');

      // Not a dead end: the operator lands where they do belong.
      expect(runRoleGuard('MANAGER').toString()).toBe('/lane');
    });

    it('bounces a manager off the lane console to the dashboard', () => {
      configure(true, 'MANAGER');

      expect(runRoleGuard('OPERATOR').toString()).toBe('/dashboard');
    });

    it('sends an anonymous visitor to the login screen rather than their home', () => {
      configure(false, null);

      expect(runRoleGuard('MANAGER').toString()).toBe('/login');
    });
  });
});
