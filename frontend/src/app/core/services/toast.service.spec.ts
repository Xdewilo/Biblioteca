// by Jeremy Posada
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let toast: ToastService;

  beforeEach(() => {
    toast = TestBed.inject(ToastService);
  });

  it('un aviso entra, se marca como saliendo a los 4,6 s y desaparece 320 ms después', fakeAsync(() => {
    toast.success('Listo');
    expect(toast.toasts().length).toBe(1);
    expect(toast.toasts()[0].leaving).toBeFalse();

    tick(4600);
    expect(toast.toasts()[0].leaving).toBeTrue();

    tick(320);
    expect(toast.toasts().length).toBe(0);
  }));

  it('cerrar a mano adelanta la salida y no la repite', fakeAsync(() => {
    toast.error('Falló');
    const id = toast.toasts()[0].id;

    toast.dismiss(id);
    toast.dismiss(id); // la segunda llamada no hace nada
    expect(toast.toasts()[0].leaving).toBeTrue();

    tick(320);
    expect(toast.toasts().length).toBe(0);
    tick(5000); // el temporizador original ya no encuentra el aviso
    expect(toast.toasts().length).toBe(0);
  }));
});
