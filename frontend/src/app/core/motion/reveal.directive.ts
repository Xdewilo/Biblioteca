// by Jeremy Posada
import { Directive, ElementRef, afterRenderEffect, inject, input } from '@angular/core';
import { entradaEscalonada, gsap } from './motion';

@Directive({ selector: '[appReveal]', standalone: true })
export class RevealDirective {
  readonly appReveal = input.required<string>();
  readonly revealKey = input<unknown>(null);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private tween: gsap.core.Tween | null = null;

  constructor() {
    afterRenderEffect(() => {
      this.revealKey(); // dependencia: se vuelve a ejecutar cuando cambia la clave
      const targets = this.host.nativeElement.querySelectorAll(this.appReveal());
      this.tween?.kill();
      this.tween = targets.length ? entradaEscalonada(targets) : null;
    });
  }
}
