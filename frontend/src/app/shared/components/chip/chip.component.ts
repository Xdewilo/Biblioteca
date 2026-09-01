// by Jeremy Posada
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { BookStatus } from '@core/auth/models/auth.models';

export type ChipTone = 'ok' | 'warn' | 'info' | 'bad' | 'mute' | BookStatus;

const STATUS_LABEL: Record<BookStatus, string> = {
  DISPONIBLE: 'Disponible',
  PRESTADO: 'Prestado',
  RESERVADO: 'Reservado',
};

@Component({
  selector: 'app-chip',
  standalone: true,
  templateUrl: './chip.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChipComponent {
  readonly tone = input<ChipTone>('mute');
  readonly status = input<BookStatus | null>(null);
  readonly pulse = input(false);

  protected readonly clases = computed(
    () => `chip chip--${this.status() ?? this.tone()}${this.pulse() ? ' chip--pulse' : ''}`,
  );
  protected readonly etiqueta = computed(() => {
    const s = this.status();
    return s ? STATUS_LABEL[s] : null;
  });
}
