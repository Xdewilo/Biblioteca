// by Jeremy Posada
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from '@core/auth/interceptors/auth.interceptor';
import { errorInterceptor } from '@core/auth/interceptors/error.interceptor';
import { AuthStore } from '@core/auth/services/auth.store';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    // Se vuelve a pedir /api/auth/me al arrancar: un bloqueo levantado o un token caducado se ven al recargar.
    provideAppInitializer(() => inject(AuthStore).refreshUser()),
  ],
};
