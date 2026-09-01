// by Jeremy Posada
import { Subject, of, throwError } from 'rxjs';
import { Cargable } from './cargable';
import { ApiError } from './api-error';

describe('Cargable — tres estados explícitos y cancelación', () => {
  it('pasa por cargando y entrega el dato', () => {
    const c = new Cargable<number>();
    const fuente = new Subject<number>();

    c.cargar(() => fuente.asObservable());
    expect(c.loading()).toBeTrue();
    expect(c.data()).toBeNull();

    fuente.next(42);
    expect(c.loading()).toBeFalse();
    expect(c.data()).toBe(42);
    expect(c.error()).toBeNull();
  });

  it('expone el mensaje real del backend cuando falla', () => {
    const c = new Cargable<number>('respaldo');
    c.cargar(() => throwError(() => new ApiError(500, 'INTERNAL_ERROR', 'Se cayó la base de datos')));
    expect(c.error()).toBe('Se cayó la base de datos');
    expect(c.loading()).toBeFalse();
  });

  it('usa el mensaje de respaldo para errores que no vienen de la API', () => {
    const c = new Cargable<number>('No se pudo cargar.');
    c.cargar(() => throwError(() => new Error('boom')));
    expect(c.error()).toBe('No se pudo cargar.');
  });

  it('cancela la petición anterior al recargar: la respuesta vieja no pisa la nueva', () => {
    const c = new Cargable<string>();
    const vieja = new Subject<string>();
    c.cargar(() => vieja.asObservable());
    c.cargar(() => of('nueva'));

    vieja.next('vieja'); // ya nadie escucha
    expect(c.data()).toBe('nueva');
  });

  it('recargar repite la última petición', () => {
    let llamadas = 0;
    const c = new Cargable<number>();
    c.cargar(() => of(++llamadas));
    c.recargar();
    expect(llamadas).toBe(2);
    expect(c.data()).toBe(2);
  });
});
