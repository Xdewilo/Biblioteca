// by Jeremy Posada
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';

import { Loan, Reservation } from '@core/auth/models/auth.models';
import { AuthStore } from '@core/auth/services/auth.store';
import { Cargable } from '@core/http/cargable';
import { mensajeDe } from '@core/http/api-error';
import { LoansService } from '@core/services/loans.service';
import { ReservationsService } from '@core/services/reservations.service';
import { ToastService } from '@core/services/toast.service';
import { RevealDirective } from '@core/motion/reveal.directive';
import { ChipComponent } from '@shared/components/chip/chip.component';
import { CoverComponent } from '@shared/components/cover/cover.component';
import { IconComponent } from '@shared/components/icon/icon.component';
import { NoteComponent } from '@shared/components/note/note.component';
import { EmptyComponent, FailedComponent, LoadingComponent } from '@shared/components/states/states.component';
import { diasHasta, fecha, fechaHora, venceEn } from '@shared/utils/fechas';

@Component({
  selector: 'app-my-loans',
  standalone: true,
  imports: [RevealDirective, ChipComponent, CoverComponent, IconComponent, NoteComponent, EmptyComponent, FailedComponent, LoadingComponent],
  templateUrl: './my-loans.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyLoansComponent {
  protected readonly auth = inject(AuthStore);
  private readonly loans = inject(LoansService);
  private readonly reservations = inject(ReservationsService);
  private readonly toast = inject(ToastService);

  protected readonly prestamos = new Cargable<Loan[]>('No se pudieron cargar tus préstamos.');
  protected readonly reservas = new Cargable<Reservation[]>('No se pudo cargar la lista de espera.');
  protected readonly ocupado = signal<number | null>(null);

  protected readonly activos = computed(() =>
    (this.prestamos.data() ?? []).filter((p) => !p.returned).sort((a, b) => a.dueDate.localeCompare(b.dueDate)),
  );
  protected readonly historial = computed(() =>
    (this.prestamos.data() ?? [])
      .filter((p) => p.returned)
      .sort((a, b) => (b.returnDate ?? '').localeCompare(a.returnDate ?? '')),
  );
  protected readonly enEspera = computed(() =>
    (this.reservas.data() ?? []).filter((r) => r.status === 'PENDIENTE' || r.status === 'NOTIFICADO'),
  );
  protected readonly listos = computed(() => this.enEspera().filter((r) => r.readyToConfirm));
  protected readonly revealKey = computed(
    () => `${this.activos().length}/${this.enEspera().length}/${this.historial().length}`,
  );

  protected readonly fecha = fecha;
  protected readonly fechaHora = fechaHora;
  protected readonly venceEn = venceEn;

  constructor() {
    this.cargar();
    inject(DestroyRef).onDestroy(() => {
      this.prestamos.destruir();
      this.reservas.destruir();
    });
  }

  /** Verde si hay margen, ámbar si vence en dos días o menos. */
  protected tonoPlazo(p: Loan): 'ok' | 'warn' {
    return diasHasta(p.dueDate) <= 2 ? 'warn' : 'ok';
  }

  protected devolver(p: Loan): void {
    this.ocupado.set(p.id);
    this.loans.markReturned(p.id).subscribe({
      next: (devuelto) => {
        this.toast.success(
          devuelto.returnedLate
            ? `«${p.bookTitle}» devuelto con atraso. Quedó registrado en tu historial.`
            : `«${p.bookTitle}» devuelto a tiempo. Gracias.`,
        );
        this.ocupado.set(null);
        this.cargar();
        // La devolución tardía pudo bloquear la cuenta: hay que refrescar el estado.
        void this.auth.refreshUser();
      },
      error: (err: unknown) => {
        this.toast.error(mensajeDe(err, 'No se pudo registrar la devolución.'));
        this.ocupado.set(null);
      },
    });
  }

  protected confirmar(r: Reservation): void {
    this.ocupado.set(r.id);
    this.reservations.confirm(r.id).subscribe({
      next: (prestamo) => {
        this.toast.success(`«${r.bookTitle}» ya es tuyo. Devuélvelo antes del ${fecha(prestamo.dueDate)}.`);
        this.ocupado.set(null);
        this.cargar();
      },
      error: (err: unknown) => {
        this.toast.error(mensajeDe(err, 'No se pudo confirmar el préstamo.'));
        this.ocupado.set(null);
        this.reservas.recargar();
      },
    });
  }

  protected cancelar(r: Reservation): void {
    this.ocupado.set(r.id);
    this.reservations.cancel(r.id).subscribe({
      next: () => {
        this.toast.success('Saliste de la lista de espera.');
        this.ocupado.set(null);
        this.reservas.recargar();
      },
      error: (err: unknown) => {
        this.toast.error(mensajeDe(err, 'No se pudo cancelar la reserva.'));
        this.ocupado.set(null);
      },
    });
  }

  private cargar(): void {
    this.prestamos.cargar(() => this.loans.mine());
    this.reservas.cargar(() => this.reservations.mine());
  }
}
