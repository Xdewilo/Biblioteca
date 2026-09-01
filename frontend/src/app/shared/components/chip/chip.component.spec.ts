// by Jeremy Posada
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ChipComponent } from './chip.component';

@Component({
  standalone: true,
  imports: [ChipComponent],
  template: `<app-chip status="RESERVADO" /><app-chip tone="bad" [pulse]="true">3 vencidos</app-chip>`,
})
class HostComponent {}

describe('ChipComponent', () => {
  it('traduce el estado del libro y aplica el tono; el pulso es opcional', async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    const chips = fixture.nativeElement.querySelectorAll('.chip') as NodeListOf<HTMLElement>;
    expect(chips[0].textContent?.trim()).toBe('Reservado');
    expect(chips[0].classList).toContain('chip--RESERVADO');
    expect(chips[1].textContent?.trim()).toBe('3 vencidos');
    expect(chips[1].classList).toContain('chip--bad');
    expect(chips[1].classList).toContain('chip--pulse');
  });
});
