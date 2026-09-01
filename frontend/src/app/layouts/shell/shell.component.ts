// by Jeremy Posada
import { ChangeDetectionStrategy, Component, ElementRef, afterNextRender, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';

import { AuthStore } from '@core/auth/services/auth.store';
import { aparecer, gsap, menosMovimiento } from '@core/motion/motion';
import { IconComponent } from '@shared/components/icon/icon.component';
import { iniciales } from '@shared/utils/fechas';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  templateUrl: './shell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly iniciales = iniciales;

  constructor() {
    afterNextRender(() => {
      if (menosMovimiento()) return;
      gsap.fromTo(
        this.host.nativeElement.querySelectorAll('.brand, .nav__link, .who, .aside__out'),
        ...aparecer({ x: -12 }, { duration: 0.45, stagger: 0.05 }),
      );
    });

    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.fundirContenido());
  }

  protected rolLegible(): string {
    return this.auth.user()?.role === 'ADMIN' ? 'Administración' : 'Bibliotecario';
  }

  protected salir(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }

  private fundirContenido(): void {
    if (menosMovimiento()) return;
    const wrap = this.host.nativeElement.querySelector('.wrap');
    if (wrap) gsap.fromTo(wrap, ...aparecer({ y: 8 }, { duration: 0.3 }));
  }
}
