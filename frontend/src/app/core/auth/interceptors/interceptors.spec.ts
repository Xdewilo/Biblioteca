// by Jeremy Posada
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';
import { AuthStore } from '@core/auth/services/auth.store';
import { ApiError } from '@core/http/api-error';

describe('interceptores HTTP', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthStore;

  beforeEach(() => {
    localStorage.setItem(
      'anaquel-auth',
      JSON.stringify({ token: 'jwt-123', user: { id: 1, name: 'Ana', email: 'a@a.co', role: 'BIBLIOTECARIO', blocked: false } }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthStore);
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  it('agrega el Bearer a las peticiones a la API', () => {
    http.get('/api/books').subscribe();
    const req = backend.expectOne('/api/books');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-123');
    req.flush({});
  });

  it('no manda token al login ni al registro', () => {
    http.post('/api/auth/login', {}).subscribe();
    const req = backend.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('convierte los errores del backend en ApiError con código y violaciones por campo', (done) => {
    http.post('/api/books', {}).subscribe({
      error: (err: unknown) => {
        expect(err).toBeInstanceOf(ApiError);
        const api = err as ApiError;
        expect(api.status).toBe(400);
        expect(api.code).toBe('VALIDATION_ERROR');
        expect(api.fieldErrors['isbn']).toBe('El ISBN es obligatorio');
        done();
      },
    });
    backend.expectOne('/api/books').flush(
      { code: 'VALIDATION_ERROR', message: 'Datos inválidos', errors: [{ field: 'isbn', message: 'El ISBN es obligatorio' }] },
      { status: 400, statusText: 'Bad Request' },
    );
  });

  it('un 401 fuera del login cierra la sesión y manda al login', (done) => {
    const router = TestBed.inject(Router);
    const navegar = spyOn(router, 'navigate').and.resolveTo(true);

    http.get('/api/loans/mine').subscribe({
      error: () => {
        expect(auth.isAuthenticated()).toBeFalse();
        expect(navegar).toHaveBeenCalledWith(['/login']);
        done();
      },
    });
    backend.expectOne('/api/loans/mine').flush({ code: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });
  });

  it('un fallo de red se explica como tal, sin código del servidor', (done) => {
    http.get('/api/books').subscribe({
      error: (err: unknown) => {
        expect((err as ApiError).code).toBe('NETWORK_ERROR');
        done();
      },
    });
    backend.expectOne('/api/books').error(new ProgressEvent('error'), { status: 0 });
  });
});
