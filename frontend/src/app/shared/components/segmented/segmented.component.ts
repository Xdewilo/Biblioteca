// by Jeremy Posada
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  afterRenderEffect,
  inject,
  input,
  model,
} from '@angular/core';
import { gsap, menosMovimiento } from '@core/motion/motion';

export interface SegmentOption<T extends string> {
  value: T;
  label: string;
}

@Component({
  selector: 'app-segmented',
  standalone: true,
  templateUrl: './segmented.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SegmentedComponent<T extends string> {
  readonly options = input.required<SegmentOption<T>[]>();
  readonly label = input('Opciones');
  readonly value = model.required<T>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private primera = true;

  constructor() {
    afterRenderEffect(() => {
      this.value();
      this.colocar(!this.primera && !menosMovimiento());
      this.primera = false;
    });

    // Si cambia el ancho (carga de fuentes, ventana), el pulgar se recoloca sin animar.
    afterNextRender(() => {
      const ro = new ResizeObserver(() => this.colocar(false));
      ro.observe(this.host.nativeElement);
      this.destroyRef.onDestroy(() => ro.disconnect());
    });
  }

  protected elegir(v: T): void {
    this.value.set(v);
  }

  private colocar(animar: boolean): void {
    const root = this.host.nativeElement;
    const thumb = root.querySelector<HTMLElement>('.seg__thumb');
    const active = root.querySelector<HTMLElement>('.seg__opt--on');
    if (!thumb || !active) return;
    const target = { x: active.offsetLeft, width: active.offsetWidth };
    if (!animar) {
      gsap.set(thumb, target);
      return;
    }
    gsap.to(thumb, { ...target, duration: 0.38, ease: 'power3.out', overwrite: true });
  }
}
