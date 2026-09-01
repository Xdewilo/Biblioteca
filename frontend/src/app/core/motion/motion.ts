// by Jeremy Posada
import gsap from 'gsap';

export function menosMovimiento(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

// fromTo en vez de gsap.from: con transición CSS, from() lee un valor a medio camino y deja el elemento invisible.
export function aparecer(
  from: gsap.TweenVars = {},
  to: gsap.TweenVars = {},
): [gsap.TweenVars, gsap.TweenVars] {
  return [
    { autoAlpha: 0, transition: 'none', ...from },
    {
      autoAlpha: 1,
      x: 0,
      y: 0,
      scale: 1,
      ease: 'power2.out',
      ...to,
      clearProps: 'transform,opacity,visibility,transition',
    },
  ];
}

export function entradaEscalonada(targets: gsap.TweenTarget, cada = 0.045): gsap.core.Tween | null {
  const reducido = menosMovimiento();
  return gsap.fromTo(
    targets,
    ...aparecer(
      { y: reducido ? 0 : 14 },
      { duration: reducido ? 0.2 : 0.5, stagger: reducido ? 0 : { each: cada, from: 'start' } },
    ),
  );
}

export { gsap };
