// by Jeremy Posada
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CountUpDirective } from '@core/motion/count-up.directive';

@Component({
  selector: 'app-stat',
  standalone: true,
  imports: [CountUpDirective],
  templateUrl: './stat.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatComponent {
  readonly label = input.required<string>();
  readonly value = input.required<number>();
  readonly tone = input<'bad' | 'warn' | 'ok' | null>(null);
  readonly hint = input<string | null>(null);
}
