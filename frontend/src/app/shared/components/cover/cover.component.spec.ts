// by Jeremy Posada
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CoverComponent, portadaPorIsbn } from './cover.component';

@Component({
  standalone: true,
  imports: [CoverComponent],
  template: `<app-cover [url]="url()" [isbn]="isbn()" title="Rayuela" [size]="size()" />`,
})
class HostComponent {
  url = signal<string | null>(null);
  isbn = signal<string | null>('9788437604572');
  size = signal<'card' | 'thumb'>('card');
}

describe('CoverComponent — portada del backend, de Open Library o respaldo tipográfico', () => {
  function crear() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('sin URL del backend intenta la portada de Open Library por ISBN (grande en tarjeta, mediana en tabla)', () => {
    const fixture = crear();
    const img = fixture.nativeElement.querySelector('img') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe(portadaPorIsbn('9788437604572', 'card'));
    expect(portadaPorIsbn('9788437604572', 'thumb')).toContain('-M.jpg?default=false');
  });

  it('la URL del backend tiene prioridad sobre el ISBN', () => {
    const fixture = crear();
    fixture.componentInstance.url.set('https://cdn/x.jpg');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('https://cdn/x.jpg');
  });

  it('sin URL ni ISBN, o si la imagen falla, muestra la inicial del título', () => {
    const fixture = crear();
    fixture.componentInstance.isbn.set(null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.querySelector('.cover__glyph').textContent.trim()).toBe('R');

    fixture.componentInstance.isbn.set('9788437604572');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('img').dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.querySelector('.cover__glyph').textContent.trim()).toBe('R');
  });
});
