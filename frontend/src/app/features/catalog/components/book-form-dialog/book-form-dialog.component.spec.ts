// by Jeremy Posada
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '@core/auth/interceptors/error.interceptor';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { BookFormDialogComponent } from './book-form-dialog.component';

const PREVIEW = {
  isbn: '9780132350884',
  title: 'Clean Code',
  author: 'Robert C. Martin',
  publicationYear: 2008,
  coverUrl: null,
  subjects: ['Software'],
  source: 'openlibrary.org',
  alreadyRegistered: false,
};

describe('BookFormDialogComponent — autocompletar desde ISBN', () => {
  let fixture: ComponentFixture<BookFormDialogComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookFormDialogComponent],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    }).compileComponents();
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BookFormDialogComponent);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  function escribir(id: string, valor: string): void {
    const input = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function valor(id: string): string {
    return (fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement).value;
  }

  function boton(texto: string): HTMLButtonElement {
    return Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLButtonElement).textContent?.includes(texto),
    ) as HTMLButtonElement;
  }

  it('explica qué falla en el ISBN: cuántos dígitos lleva o que el de control no cuadra', fakeAsync(() => {
    escribir('isbn', '1234-1234-1234-1234');
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Llevas 16 dígitos: un ISBN tiene 10 o 13.');
    expect(boton('Autocompletar').disabled).toBeTrue();

    escribir('isbn', '978-0-13-235088-5');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('no cuadran con el de control');
    tick(600); // el ISBN no es válido: la consulta automática no se dispara
    backend.expectNone(() => true);
  }));

  it('en cuanto el ISBN es válido consulta Open Library sola y rellena título, autor y año sin guardar', fakeAsync(() => {
    escribir('isbn', '978-0-13-235088-4');
    expect(boton('Autocompletar').disabled).toBeFalse();
    backend.expectNone(() => true); // todavía no: espera el debounce

    tick(600);
    const req = backend.expectOne('/api/books/lookup/9780132350884');
    expect(req.request.method).toBe('GET');
    req.flush(PREVIEW);
    fixture.detectChanges();

    expect(valor('title')).toBe('Clean Code');
    expect(valor('author')).toBe('Robert C. Martin');
    expect(valor('year')).toBe('2008');
    expect(fixture.nativeElement.textContent).toContain('Datos traídos de openlibrary.org');
    backend.expectNone('/api/books');
  }));

  it('el botón repite la consulta a mano y no se duplica con la automática', fakeAsync(() => {
    escribir('isbn', '9780132350884');
    boton('Autocompletar').click();
    backend.expectOne('/api/books/lookup/9780132350884').flush(PREVIEW);
    tick(600);
    backend.expectNone('/api/books/lookup/9780132350884'); // la automática ve que ya se consultó

    boton('Autocompletar').click();
    backend.expectOne('/api/books/lookup/9780132350884').flush(PREVIEW);
  }));

  it('los ejemplos clicables rellenan el ISBN y disparan la consulta', fakeAsync(() => {
    boton('9780132350884').click();
    fixture.detectChanges();
    expect(valor('isbn')).toBe('9780132350884');
    backend.expectOne('/api/books/lookup/9780132350884').flush(PREVIEW);
    tick(600);
    fixture.detectChanges();
    expect(valor('title')).toBe('Clean Code');
  }));

  it('si Open Library falla, avisa y el formulario sigue siendo editable a mano', fakeAsync(() => {
    escribir('isbn', '9780132350884');
    tick(600);
    backend
      .expectOne('/api/books/lookup/9780132350884')
      .flush({ code: 'EXTERNAL_LOOKUP_FAILED', message: 'x' }, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Open Library no respondió');
    expect((fixture.nativeElement.querySelector('#title') as HTMLInputElement).disabled).toBeFalse();
  }));

  it('no guarda sin título y autor, y marca los campos', fakeAsync(() => {
    escribir('isbn', '9780132350884');
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('El título es obligatorio');
    backend.expectNone('/api/books');

    tick(600); // la consulta automática pendiente se atiende para dejar limpio el test
    backend.expectOne('/api/books/lookup/9780132350884').flush({ code: 'NOT_FOUND', message: 'no' }, { status: 404, statusText: 'Not Found' });
  }));
});
