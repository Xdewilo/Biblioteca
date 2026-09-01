// by Jeremy Posada
import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, inject } from '@angular/core';
import { ToastService } from '@core/services/toast.service';
import { IconComponent, IconName } from '@shared/components/icon/icon.component';
import { gsap, menosMovimiento } from '@core/motion/motion';

const ICONO: Record<string, IconName> = { success: 'check', error: 'alert', info: 'mail' };

@Component({
  selector: 'app-toasts',
  standalone: true,
  imports: [IconComponent],
  templateUrl: './toasts.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastsComponent {
  protected readonly toast = inject(ToastService);
  protected readonly iconoDe = (kind: string): IconName => ICONO[kind] ?? 'mail';

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly entrados = new Set<number>();
  private readonly salidos = new Set<number>();

  constructor() {
    afterRenderEffect(() => {
      const reducido = menosMovimiento();
      for (const t of this.toast.toasts()) {
        const el = this.host.nativeElement.querySelector<HTMLElement>(`[data-toast="${t.id}"]`);
        if (!el) continue;
        if (!this.entrados.has(t.id)) {
          this.entrados.add(t.id);
          gsap.from(el, { autoAlpha: 0, x: reducido ? 0 : 28, duration: reducido ? 0.15 : 0.36, ease: 'power3.out' });
          gsap.fromTo(el.querySelector('.toast__bar'), { scaleX: 1 }, { scaleX: 0, duration: 4.6, ease: 'none' });
        }
        if (t.leaving && !this.salidos.has(t.id)) {
          this.salidos.add(t.id);
          gsap.to(el, {
            autoAlpha: 0,
            x: reducido ? 0 : 28,
            height: 0,
            marginBottom: 0,
            paddingTop: 0,
            paddingBottom: 0,
            duration: 0.3,
            ease: 'power2.in',
            overwrite: true,
          });
        }
      }
    });
  }
}
