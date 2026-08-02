import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { AuthService } from './auth.service';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();

  const request = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    tap({
      error: (error) => {
        // An expired or rejected token means the stored session is dead.
        if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
          if (!req.url.includes('/auth/login')) {
            auth.logout();
          }
        }
      },
    })
  );
};
