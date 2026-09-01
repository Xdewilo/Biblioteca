// by Jeremy Posada
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';

import { Loan, Stats } from '@core/auth/models/auth.models';
import { Cargable } from '@core/http/cargable';
import { mensajeDe } from '@core/http/api-error';
import { AdminService } from '@core/services/admin.service';
import { LoansService } from '@core/services/loans.service';
import { ToastService } from '@core/services/toast.service';
import { RevealDirective } from '@core/motion/reveal.directive';
import { ChipComponent } from '@shared/components/chip/chip.component';
import { CoverComponent } from '@shared/components/cover/cover.component';
import { IconComponent } from '@shared/components/icon/icon.component';
import { NoteComponent } from '@shared/components/note/note.component';
import { StatComponent } from '@shared/components/stat/stat.component';
import { EmptyComponent, FailedComponent, LoadingComponent } from '@shared/components/states/states.component';
import { diasHasta, fecha, fechaHora, venceEn } from '@shared/utils/fechas';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [RevealDirective, ChipComponent, CoverComponent, IconComponent, NoteComponent, StatComponent, EmptyComponent, FailedComponent, LoadingComponent],
  templateUrl: './admin.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminComponent {
  private readonly admin = inject(AdminService);
  private readonly loans = inject(LoansService);
  private readonly toast = inject(ToastService);

  protected readonly stats = new Cargable<Stats>('No se pudieron cargar las estadísticas.');
  protected readonly prestamos = new Cargable<Loan[]>('No se pudieron cargar los préstamos.');
  protected readonly ocupado = signal<number | null>(null);

  protected readonly activos = computed(() =>
    (this.prestamos.data() ?? []).filter((p) => !p.returned).sort((a, b) => a.dueDate.localeCompare(b.dueDate)),
  );
  protected readonly vencidos = computed(() => this.activos().filter((p) => p.overdue));
  protected readonly revealKey = computed(() => `${this.stats.data()?.totalBooks ?? ''}/${this.activos().length}`);

  protected readonly fecha = fecha;
  protected readonly fechaHora = fechaHora;
  protected readonly venceEn = venceEn;

  constructor() {
    this.stats.cargar(() => this.admin.stats());
    this.prestamos.cargar(() => this.loans.all());
    inject(DestroyRef).onDestroy(() => {
      this.stats.destruir();
      this.prestamos.destruir();
    });
  }

  protected tonoPlazo(p: Loan): 'ok' | 'warn' {
    return diasHasta(p.dueDate) <= 2 ? 'warn' : 'ok';
  }

  protected levantar(id: number, correo: string): void {
    this.ocupado.set(id);
    this.admin.unblock(id).subscribe({
      next: () => {
        this.toast.success(`${correo} ya puede volver a pedir libros.`);
        this.ocupado.set(null);
        this.stats.recargar();
      },
      error: (err: unknown) => {
        this.toast.error(mensajeDe(err, 'No se pudo levantar el bloqueo.'));
        this.ocupado.set(null);
      },
    });
  }
}
