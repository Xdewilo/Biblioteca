// by Jeremy Posada
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  afterNextRender,
  inject,
  input,
  output,
} from '@angular/core';
import { IconComponent } from '@shared/components/icon/icon.component';
import { aparecer, gsap, menosMovimiento } from '@core/motion/motion';

@Component({
  selector: 'app-sheet',
  standalone: true,
  imports: [IconComponent],
  templateUrl: './sheet.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SheetComponent {
  readonly title = input.required<string>();
  readonly width = input<number | null>(null);
  readonly closed = output<void>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private tl: gsap.core.Timeline | null = null;
  private cerrando = false;

  constructor() {
    afterNextRender(() => {
      const veil = this.host.nativeElement.querySelector('.veil');
      const sheet = this.host.nativeElement.querySelector('.sheet');
      const reducido = menosMovimiento();
      const t = gsap.timeline();
      t.fromTo(veil, ...aparecer({}, { duration: reducido ? 0.12 : 0.22 }));
      if (!reducido) {
        t.fromTo(sheet, ...aparecer({ y: 22, scale: 0.98 }, { duration: 0.36, ease: 'power3.out' }), '<');
      }
      this.tl = t;
    });

    const previo = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    this.destroyRef.onDestroy(() => {
      document.body.style.overflow = previo;
      this.tl?.kill();
    });
  }

  @HostListener('document:keydown.escape')
  close(): void {
    if (this.cerrando) return;
    this.cerrando = true;
    if (!this.tl) {
      this.closed.emit();
      return;
    }
    this.tl.eventCallback('onReverseComplete', () => this.closed.emit());
    this.tl.timeScale(1.5).reverse();
  }
}
