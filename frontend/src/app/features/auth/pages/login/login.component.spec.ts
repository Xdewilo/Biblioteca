// by Jeremy Posada
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { LoginComponent } from './login.component';

describe('LoginComponent — el formulario aplica las mismas reglas que el backend', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let backend: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  function escribir(id: string, valor: string): void {
    const input = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  function enviar(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('no llama al servidor con correo inválido o contraseña corta, y explica por qué', () => {
    escribir('correo', 'no-es-un-correo');
    escribir('clave', '1234567');
    enviar();

    const errores = Array.from(fixture.nativeElement.querySelectorAll('.field__bad')).map((e) =>
      (e as HTMLElement).textContent?.trim(),
    );
    expect(errores).toContain('El correo no tiene un formato válido.');
    expect(errores).toContain('La contraseña debe tener al menos 8 caracteres.');
    backend.expectNone('/api/auth/login');
  });

  it('con datos válidos hace POST /api/auth/login y navega al catálogo', async () => {
    const navegar = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    escribir('correo', 'admin@anaquel.app');
    escribir('clave', 'Admin123*');
    enviar();

    const req = backend.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ email: 'admin@anaquel.app', password: 'Admin123*' });
    req.flush({
      token: 'jwt',
      tokenType: 'Bearer',
      expiresInSeconds: 1,
      user: { id: 1, name: 'Admin', email: 'admin@anaquel.app', role: 'ADMIN', blocked: false },
    });
    await fixture.whenStable();

    expect(navegar).toHaveBeenCalledWith('/catalogo', { replaceUrl: true });
  });

  it('al pasar a "Crear cuenta" el nombre se vuelve obligatorio', () => {
    (fixture.nativeElement.querySelector('.link') as HTMLButtonElement).click();
    fixture.detectChanges();

    escribir('correo', 'ana@anaquel.app');
    escribir('clave', 'Secreta123');
    enviar();

    expect(fixture.nativeElement.querySelector('#nombre')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Escribe tu nombre.');
    backend.expectNone('/api/auth/register');
  });
});
