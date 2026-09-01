// by Jeremy Posada
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { adminGuard } from './admin.guard';
import { authGuard, guestGuard } from './auth.guard';

function sesion(role: 'ADMIN' | 'BIBLIOTECARIO' | null): void {
  localStorage.clear();
  if (role) {
    localStorage.setItem(
      'anaquel-auth',
      JSON.stringify({ token: 'jwt', user: { id: 1, name: 'X', email: 'x@x.co', role, blocked: false } }),
    );
  }
}

function correr(guard: typeof authGuard, url = '/catalogo'): boolean | UrlTree {
  return TestBed.runInInjectionContext(() =>
    guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
  ) as boolean | UrlTree;
}

describe('guards de sesión y rol', () => {
  let router: Router;

  function preparar(role: 'ADMIN' | 'BIBLIOTECARIO' | null): void {
    sesion(role);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient()] });
    router = TestBed.inject(Router);
  }

  afterEach(() => localStorage.clear());

  it('authGuard deja pasar con sesión', () => {
    preparar('BIBLIOTECARIO');
    expect(correr(authGuard)).toBeTrue();
  });

  it('authGuard sin sesión redirige al login recordando adónde iba', () => {
    preparar(null);
    const tree = correr(authGuard, '/mis-prestamos') as UrlTree;
    expect(router.serializeUrl(tree)).toBe('/login?from=%2Fmis-prestamos');
  });

  it('guestGuard manda al catálogo a quien ya tiene sesión', () => {
    preparar('BIBLIOTECARIO');
    expect(router.serializeUrl(correr(guestGuard) as UrlTree)).toBe('/catalogo');
  });

  it('adminGuard solo deja pasar a ADMIN', () => {
    preparar('ADMIN');
    expect(correr(adminGuard)).toBeTrue();
    preparar('BIBLIOTECARIO');
    expect(router.serializeUrl(correr(adminGuard) as UrlTree)).toBe('/catalogo');
  });
});
