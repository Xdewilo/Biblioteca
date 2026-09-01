// by Jeremy Posada
import { Directive, ElementRef, afterRenderEffect, inject, input } from '@angular/core';
import { gsap, menosMovimiento } from './motion';

@Directive({ selector: '[appCountUp]', standalone: true })
export class CountUpDirective {
  readonly appCountUp = input.required<number>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private mostrado = 0;
  private tween: gsap.core.Tween | null = null;

  constructor() {
    afterRenderEffect(() => {
      const valor = this.appCountUp();
      const nodo = this.host.nativeElement;
      this.tween?.kill();
      if (menosMovimiento()) {
        nodo.textContent = String(valor);
        this.mostrado = valor;
        return;
      }
      const cuenta = { n: this.mostrado };
      this.tween = gsap.to(cuenta, {
        n: valor,
        duration: 0.85,
        ease: 'power2.out',
        onUpdate: () => (nodo.textContent = String(Math.round(cuenta.n))),
        onComplete: () => (this.mostrado = valor),
      });
    });
  }
}
