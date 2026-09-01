// by Jeremy Posada
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { Book, BookStatus, Loan, Page, Reservation } from '@core/auth/models/auth.models';
import { AuthStore } from '@core/auth/services/auth.store';
import { Cargable } from '@core/http/cargable';
import { ApiError, mensajeDe } from '@core/http/api-error';
import { BooksService } from '@core/services/books.service';
import { LoansService } from '@core/services/loans.service';
import { ReservationsService } from '@core/services/reservations.service';
import { ToastService } from '@core/services/toast.service';
import { RevealDirective } from '@core/motion/reveal.directive';
import { ChipComponent } from '@shared/components/chip/chip.component';
import { ConfirmComponent } from '@shared/components/confirm/confirm.component';
import { CoverComponent } from '@shared/components/cover/cover.component';
import { IconComponent } from '@shared/components/icon/icon.component';
import { NoteComponent } from '@shared/components/note/note.component';
import { SegmentOption, SegmentedComponent } from '@shared/components/segmented/segmented.component';
import { EmptyComponent, FailedComponent, ShelfSkeletonComponent } from '@shared/components/states/states.component';
import { RouterLink } from '@angular/router';
import { fecha } from '@shared/utils/fechas';
import { BookFormDialogComponent } from '@features/catalog/components/book-form-dialog/book-form-dialog.component';

const POR_PAGINA = 12;
type Filtro = BookStatus | '';

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [
    RouterLink,
    RevealDirective,
    ChipComponent,
    ConfirmComponent,
    CoverComponent,
    IconComponent,
    NoteComponent,
    SegmentedComponent,
    EmptyComponent,
    FailedComponent,
    ShelfSkeletonComponent,
    BookFormDialogComponent,
  ],
  templateUrl: './catalog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogComponent {
  protected readonly auth = inject(AuthStore);
  private readonly books = inject(BooksService);
  private readonly loans = inject(LoansService);
  private readonly reservations = inject(ReservationsService);
  private readonly toast = inject(ToastService);

  protected readonly FILTROS: SegmentOption<Filtro>[] = [
    { value: '', label: 'Todos' },
    { value: 'DISPONIBLE', label: 'Disponibles' },
    { value: 'PRESTADO', label: 'Prestados' },
    { value: 'RESERVADO', label: 'Reservados' },
  ];

  protected readonly texto = signal('');
  protected readonly estado = signal<Filtro>('');
  protected readonly pagina = signal(0);
  protected readonly ocupado = signal<number | null>(null);
  protected readonly alta = signal(false);
  protected readonly porQuitar = signal<Book | null>(null);

  private readonly buscado = toSignal(
    toObservable(this.texto).pipe(debounceTime(350), distinctUntilChanged()),
    { initialValue: '' },
  );

  protected readonly libros = new Cargable<Page<Book>>('No se pudo cargar el catálogo.');
  protected readonly misPrestamos = new Cargable<Loan[]>();
  protected readonly misReservas = new Cargable<Reservation[]>();
  private readonly enMisManos = computed(
    () => new Set((this.misPrestamos.data() ?? []).filter((p) => !p.returned).map((p) => p.bookId)),
  );
  private readonly miReservaPorLibro = computed(() => {
    const m = new Map<number, Reservation>();
    for (const r of this.misReservas.data() ?? []) {
      if (r.status === 'PENDIENTE' || r.status === 'NOTIFICADO') m.set(r.bookId, r);
    }
    return m;
  });
  protected readonly ids = computed(() => this.libros.data()?.content.map((b) => b.id).join(',') ?? '');
  protected readonly hayFiltro = computed(() => this.texto() !== '' || this.estado() !== '');
  protected readonly POR_PAGINA = POR_PAGINA;

  constructor() {
    effect(() => {
      const query = { search: this.buscado(), status: this.estado(), page: this.pagina(), size: POR_PAGINA };
      this.libros.cargar(() => this.books.list(query));
    });
    this.cargarLoMio();
    inject(DestroyRef).onDestroy(() => {
      this.libros.destruir();
      this.misPrestamos.destruir();
      this.misReservas.destruir();
    });
  }

  protected situacion(libro: Book): 'mio' | 'recoger' | 'en-fila' | 'pedir' | 'esperar' {
    if (this.enMisManos().has(libro.id)) return 'mio';
    const r = this.miReservaPorLibro().get(libro.id);
    if (r?.readyToConfirm) return 'recoger';
    if (r) return 'en-fila';
    return libro.status === 'DISPONIBLE' ? 'pedir' : 'esperar';
  }

  protected miReserva(libro: Book): Reservation | undefined {
    return this.miReservaPorLibro().get(libro.id);
  }

  protected recoger(libro: Book): void {
    const r = this.miReserva(libro);
    if (!r) return;
    this.ocupado.set(libro.id);
    this.reservations.confirm(r.id).subscribe({
      next: (prestamo) => {
        this.toast.success(`«${libro.title}» ya es tuyo. Devuélvelo antes del ${fecha(prestamo.dueDate)}.`);
        this.ocupado.set(null);
        this.libros.recargar();
        this.cargarLoMio();
      },
      error: (err: unknown) => {
        this.toast.error(mensajeDe(err, 'No se pudo confirmar el préstamo.'));
        this.ocupado.set(null);
        this.cargarLoMio();
      },
    });
  }

  protected salirDeLaFila(libro: Book): void {
    const r = this.miReserva(libro);
    if (!r) return;
    this.ocupado.set(libro.id);
    this.reservations.cancel(r.id).subscribe({
      next: () => {
        this.toast.success(`Saliste de la lista de espera de «${libro.title}».`);
        this.ocupado.set(null);
        this.cargarLoMio();
      },
      error: (err: unknown) => {
        this.toast.error(mensajeDe(err, 'No se pudo cancelar la reserva.'));
        this.ocupado.set(null);
      },
    });
  }

  private cargarLoMio(): void {
    this.misPrestamos.cargar(() => this.loans.mine());
    this.misReservas.cargar(() => this.reservations.mine());
  }

  protected buscar(valor: string): void {
    this.texto.set(valor);
    this.pagina.set(0);
  }

  protected filtrar(valor: Filtro): void {
    this.estado.set(valor);
    this.pagina.set(0);
  }

  protected prestar(libro: Book): void {
    this.ocupado.set(libro.id);
    this.loans.create(libro.id).subscribe({
      next: (prestamo) => {
        this.toast.success(`«${libro.title}» es tuyo. Devuélvelo antes del ${fecha(prestamo.dueDate)}.`);
        this.ocupado.set(null);
        this.libros.recargar();
        this.cargarLoMio();
      },
      error: (err: unknown) => {
        this.toast.error(mensajeDe(err, 'No se pudo registrar el préstamo.'));
        if (err instanceof ApiError && err.code === 'USER_BLOCKED') void this.auth.refreshUser();
        this.ocupado.set(null);
      },
    });
  }

  protected esperar(libro: Book): void {
    this.ocupado.set(libro.id);
    this.reservations.create(libro.id).subscribe({
      next: (reserva) => {
        this.toast.success(
          reserva.queuePosition
            ? `Estás en la fila por «${libro.title}», puesto ${reserva.queuePosition}. Te avisamos por correo.`
            : `Quedaste en la lista de espera de «${libro.title}». Te avisamos por correo.`,
        );
        this.ocupado.set(null);
        this.cargarLoMio();
      },
      error: (err: unknown) => {
        this.toast.error(mensajeDe(err, 'No se pudo crear la reserva.'));
        this.ocupado.set(null);
      },
    });
  }

  protected quitar(libro: Book): void {
    this.ocupado.set(libro.id);
    this.books.remove(libro.id).subscribe({
      next: () => {
        this.toast.success(`«${libro.title}» salió del catálogo.`);
        this.ocupado.set(null);
        this.porQuitar.set(null);
        this.libros.recargar();
      },
      error: (err: unknown) => {
        this.toast.error(mensajeDe(err, 'No se pudo eliminar el libro.'));
        this.ocupado.set(null);
      },
    });
  }

  protected creado(): void {
    this.alta.set(false);
    this.libros.recargar();
  }
}
