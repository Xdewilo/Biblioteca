// by Jeremy Posada
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthStore } from '@core/auth/services/auth.store';
import { ApiError } from '@core/http/api-error';

// El 401 del propio login no cierra sesión: solo era una contraseña incorrecta.
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      if (error.status === 401 && !req.url.includes('/api/auth/login') && auth.isAuthenticated()) {
        auth.logout();
        void router.navigate(['/login']);
      }
      return throwError(() => ApiError.from(error));
    }),
  );
};
