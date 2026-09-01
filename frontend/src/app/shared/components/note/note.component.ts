// by Jeremy Posada
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent, IconName } from '@shared/components/icon/icon.component';

export type NoteTone = 'bad' | 'info' | 'warn' | 'ok';

@Component({
  selector: 'app-note',
  standalone: true,
  imports: [IconComponent],
  templateUrl: './note.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NoteComponent {
  readonly tone = input<NoteTone>('info');
  readonly icon = input<IconName | null | undefined>(undefined);

  protected readonly icono = computed<IconName | null>(() => {
    const i = this.icon();
    if (i !== undefined) return i;
    return this.tone() === 'bad' || this.tone() === 'warn' ? 'alert' : null;
  });
  protected readonly role = computed(() => (this.tone() === 'bad' ? 'alert' : 'status'));
}
