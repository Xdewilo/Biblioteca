// by Jeremy Posada
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type IconName =
  | 'shelf'
  | 'loans'
  | 'chart'
  | 'logout'
  | 'search'
  | 'plus'
  | 'x'
  | 'check'
  | 'alert'
  | 'clock'
  | 'queue'
  | 'arrow-left'
  | 'arrow-right'
  | 'eye'
  | 'eye-off'
  | 'sparkle'
  | 'unlock'
  | 'mail'
  | 'bookmark';

@Component({
  selector: 'app-icon',
  standalone: true,
  templateUrl: './icon.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'icon', '[style.width.px]': 'size()', '[style.height.px]': 'size()' },
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input(18);
}
