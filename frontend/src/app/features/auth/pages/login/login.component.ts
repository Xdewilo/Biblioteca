// by Jeremy Posada
import { ChangeDetectionStrategy, Component, ElementRef, afterNextRender, afterRenderEffect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthStore } from '@core/auth/services/auth.store';
import { ToastService } from '@core/services/toast.service';
import { aparecer, gsap, menosMovimiento } from '@core/motion/motion';
import { IconComponent } from '@shared/components/icon/icon.component';
import { NoteComponent } from '@shared/components/note/note.component';

type Modo = 'entrar' | 'registrar';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, IconComponent, NoteComponent],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  protected readonly auth = inject(AuthStore);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly modo = signal<Modo>('entrar');
  protected readonly verClave = signal(false);
  protected readonly enviado = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.minLength(2)]],
    correo: ['', [Validators.required, Validators.email]],
    clave: ['', [Validators.required, Validators.minLength(8)]],
  });

  constructor() {
    afterNextRender(() => {
      if (menosMovimiento()) return;
      const q = (s: string) => this.host.nativeElement.querySelectorAll(s);
      const tl = gsap.timeline();
      tl.fromTo(q('.gate__art'), ...aparecer({}, { duration: 0.5 }))
        .fromTo(q('.gate__glow'), ...aparecer({ scale: 0.6 }, { duration: 1.4 }), '<')
        .fromTo(q('.gate__quote'), ...aparecer({ y: 22 }, { duration: 0.7, ease: 'power3.out' }), '-=0.9')
        .fromTo(q('.gate__cite'), ...aparecer({}, { duration: 0.5 }), '-=0.35')
        .fromTo(q('.gate__box > *'), ...aparecer({ y: 14 }, { duration: 0.5, stagger: 0.07, ease: 'power3.out' }), '-=0.6');
    });

    let primera = true;
    afterRenderEffect(() => {
      this.modo();
      if (primera || menosMovimiento()) {
        primera = false;
        return;
      }
      gsap.fromTo(
        this.host.nativeElement.querySelectorAll('.gate__form > *'),
        ...aparecer({ y: 8 }, { duration: 0.3, stagger: 0.04 }),
      );
    });
  }

  protected error(campo: 'nombre' | 'correo' | 'clave'): string | null {
    const c = this.form.controls[campo];
    if (!this.enviado() || c.valid) return null;
    if (campo === 'nombre') return 'Escribe tu nombre.';
    if (campo === 'correo') return 'El correo no tiene un formato válido.';
    return 'La contraseña debe tener al menos 8 caracteres.';
  }

  protected cambiarModo(): void {
    const nuevo: Modo = this.modo() === 'entrar' ? 'registrar' : 'entrar';
    this.modo.set(nuevo);
    this.enviado.set(false);
    this.auth.clearError();
    const nombre = this.form.controls.nombre;
    nombre.setValidators(nuevo === 'registrar' ? [Validators.required, Validators.minLength(2)] : [Validators.minLength(2)]);
    nombre.updateValueAndValidity();
  }

  protected async enviar(): Promise<void> {
    this.enviado.set(true);
    this.auth.clearError();
    if (this.form.invalid) return;
    const { nombre, correo, clave } = this.form.getRawValue();
    try {
      if (this.modo() === 'entrar') {
        await this.auth.login(correo.trim(), clave);
      } else {
        await this.auth.register(nombre.trim(), correo.trim(), clave);
        this.toast.success('Cuenta creada. Ya puedes pedir libros.');
      }
      const from = this.route.snapshot.queryParamMap.get('from');
      await this.router.navigateByUrl(from && from.startsWith('/') ? from : '/catalogo', { replaceUrl: true });
    } catch {
      /* el mensaje ya quedó en el store */
    }
  }
}
