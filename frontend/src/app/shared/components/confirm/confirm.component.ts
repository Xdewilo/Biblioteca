// by Jeremy Posada
import { ChangeDetectionStrategy, Component, input, output, viewChild } from '@angular/core';
import { SheetComponent } from '@shared/components/sheet/sheet.component';

@Component({
  selector: 'app-confirm',
  standalone: true,
  imports: [SheetComponent],
  templateUrl: './confirm.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmComponent {
  readonly title = input.required<string>();
  readonly confirmLabel = input('Confirmar');
  readonly danger = input(false);
  readonly busy = input(false);
  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  private readonly sheet = viewChild.required(SheetComponent);

  protected cancelar(): void {
    this.sheet().close();
  }
}
