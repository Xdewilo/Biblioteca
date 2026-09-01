// by Jeremy Posada
import { signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { mensajeDe } from './api-error';

// Cancela la petición anterior al recargar o al destruir el componente.
export class Cargable<T> {
  readonly data = signal<T | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  private sub?: Subscription;
  private ultima?: () => Observable<T>;

  constructor(private readonly mensajeRespaldo = 'No se pudieron cargar los datos.') {}

  cargar(peticion: () => Observable<T>): void {
    this.ultima = peticion;
    this.sub?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.sub = peticion().subscribe({
      next: (valor) => {
        this.data.set(valor);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.data.set(null);
        this.error.set(mensajeDe(err, this.mensajeRespaldo));
        this.loading.set(false);
      },
    });
  }

  recargar(): void {
    if (this.ultima) this.cargar(this.ultima);
  }

  destruir(): void {
    this.sub?.unsubscribe();
  }
}
