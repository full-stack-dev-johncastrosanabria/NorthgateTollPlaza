import { Routes } from '@angular/router';
import { roleGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'lane',
    canActivate: [roleGuard('OPERATOR')],
    loadComponent: () =>
      import('./features/lane-console/lane-console').then((m) => m.LaneConsole),
  },
  {
    path: 'dashboard',
    canActivate: [roleGuard('MANAGER')],
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
