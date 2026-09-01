// by Jeremy Posada
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent, IconName } from '@shared/components/icon/icon.component';

@Component({
  selector: 'app-loading',
  standalone: true,
  templateUrl: './loading.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoadingComponent {
  readonly label = input('Cargando…');
}

@Component({
  selector: 'app-failed',
  standalone: true,
  imports: [IconComponent],
  templateUrl: './failed.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FailedComponent {
  readonly message = input.required<string>();
  readonly canRetry = input(true);
  readonly retry = output<void>();
}

@Component({
  selector: 'app-empty',
  standalone: true,
  imports: [IconComponent],
  templateUrl: './empty.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyComponent {
  readonly message = input.required<string>();
  readonly hint = input<string | null>(null);
  readonly icon = input<IconName | null>(null);
}

@Component({
  selector: 'app-shelf-skeleton',
  standalone: true,
  templateUrl: './shelf-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShelfSkeletonComponent {
  readonly count = input(8);

  protected huecos(): number[] {
    return Array.from({ length: this.count() }, (_, i) => i);
  }
}
