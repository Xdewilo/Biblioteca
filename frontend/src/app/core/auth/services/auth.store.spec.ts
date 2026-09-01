// by Jeremy Posada
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuthStore } from './auth.store';
import { errorInterceptor } from '@core/auth/interceptors/error.interceptor';
import { User } from '@core/auth/models/auth.models';

const USER: User = {
  id: 1,
  name: 'Ana',
  email: 'ana@anaquel.app',
  role: 'BIBLIOTECARIO',
  blocked: false,
  blockedUntil: null,
  blockedReason: null,
};

describe('AuthStore', () => {
  let store: AuthStore;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    store = TestBed.inject(AuthStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('arranca sin sesión', () => {
    expect(store.isAuthenticated()).toBeFalse();
    expect(store.isAdmin()).toBeFalse();
  });

  it('login guarda token y usuario, y los persiste para sobrevivir un F5', async () => {
    const promesa = store.login('ana@anaquel.app', 'Secreta123');
    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'ana@anaquel.app', password: 'Secreta123' });
    req.flush({ token: 'jwt', tokenType: 'Bearer', expiresInSeconds: 100, user: USER });
    await promesa;

    expect(store.token()).toBe('jwt');
    expect(store.user()?.name).toBe('Ana');
    expect(JSON.parse(localStorage.getItem('anaquel-auth')!).token).toBe('jwt');
  });

  it('un login fallido deja el mensaje del backend y no abre sesión', async () => {
    const promesa = store.login('ana@anaquel.app', 'mala');
    http.expectOne('/api/auth/login').flush(
      { code: 'BAD_CREDENTIALS', message: 'Correo o contraseña incorrectos.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await expectAsync(promesa).toBeRejected();

    expect(store.error()).toBe('Correo o contraseña incorrectos.');
    expect(store.isAuthenticated()).toBeFalse();
  });

  it('logout limpia el estado y el almacenamiento', async () => {
    const promesa = store.login('ana@anaquel.app', 'Secreta123');
    http.expectOne('/api/auth/login').flush({ token: 'jwt', tokenType: 'Bearer', expiresInSeconds: 1, user: USER });
    await promesa;

    store.logout();

    expect(store.isAuthenticated()).toBeFalse();
    expect(JSON.parse(localStorage.getItem('anaquel-auth')!).token).toBeNull();
  });

  it('refreshUser vuelve a pedir /api/auth/me y actualiza el bloqueo', async () => {
    const promesa = store.login('ana@anaquel.app', 'Secreta123');
    http.expectOne('/api/auth/login').flush({ token: 'jwt', tokenType: 'Bearer', expiresInSeconds: 1, user: USER });
    await promesa;
    expect(store.user()?.blocked).toBeFalse();

    const refresco = store.refreshUser();
    http.expectOne('/api/auth/me').flush({ ...USER, blocked: true, blockedReason: '3 atrasos' });
    await refresco;

    expect(store.user()?.blocked).toBeTrue();
    expect(JSON.parse(localStorage.getItem('anaquel-auth')!).user.blocked).toBeTrue();
  });

  it('refreshUser sin sesión no llama al servidor', async () => {
    await store.refreshUser();
    http.expectNone('/api/auth/me');
  });

  it('isAdmin depende del rol del usuario', async () => {
    const promesa = store.login('admin@anaquel.app', 'Admin123*');
    http
      .expectOne('/api/auth/login')
      .flush({ token: 'jwt', tokenType: 'Bearer', expiresInSeconds: 1, user: { ...USER, role: 'ADMIN' } });
    await promesa;
    expect(store.isAdmin()).toBeTrue();
  });
});
