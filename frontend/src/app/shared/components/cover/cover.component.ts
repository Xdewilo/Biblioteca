// by Jeremy Posada
import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';

type Estado = 'loading' | 'ok' | 'fail';

// default=false: Open Library devuelve 404 si no tiene portada y entra el respaldo.
export function portadaPorIsbn(isbn: string, size: 'card' | 'thumb'): string {
  return `https://covers.openlibrary.org/b/isbn/${isbn}-${size === 'card' ? 'L' : 'M'}.jpg?default=false`;
}

@Component({
  selector: 'app-cover',
  standalone: true,
  templateUrl: './cover.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoverComponent {
  readonly url = input<string | null>(null);
  readonly isbn = input<string | null>(null);
  readonly title = input.required<string>();
  readonly size = input<'card' | 'thumb'>('card');

  protected readonly src = computed(() => {
    const url = this.url();
    if (url) return url;
    const isbn = this.isbn()?.trim();
    return isbn ? portadaPorIsbn(isbn, this.size()) : null;
  });

  protected readonly estado = signal<Estado>('fail');
  protected readonly inicial = computed(() => this.title().trim().charAt(0).toUpperCase() || '?');

  constructor() {
    // Si cambia la URL (p. ej. tras autocompletar), se vuelve a intentar cargar.
    effect(() => this.estado.set(this.src() ? 'loading' : 'fail'));
  }

  protected cargada(): void {
    this.estado.set('ok');
  }

  protected fallida(): void {
    this.estado.set('fail');
  }
}
