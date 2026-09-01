// by Jeremy Posada
import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, inject, output, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, map } from 'rxjs';

import { ApiError, mensajeDe } from '@core/http/api-error';
import { BooksService } from '@core/services/books.service';
import { ToastService } from '@core/services/toast.service';
import { aparecer, gsap, menosMovimiento } from '@core/motion/motion';
import { CoverComponent } from '@shared/components/cover/cover.component';
import { IconComponent } from '@shared/components/icon/icon.component';
import { NoteComponent } from '@shared/components/note/note.component';
import { SheetComponent } from '@shared/components/sheet/sheet.component';
import { isbnValido, normalizarIsbn } from '@shared/utils/isbn';
import { ISBN_MSG, isbnValidator } from '@shared/validators/isbn.validator';

interface Aviso {
  kind: 'ok' | 'warn';
  text: string;
}

/** ISBN reales con portada en Open Library, para quien no tenga uno a mano. */
export const ISBN_EJEMPLO = [
  { isbn: '9780132350884', titulo: 'Clean Code' },
  { isbn: '9788478884452', titulo: 'Harry Potter y la piedra filosofal' },
  { isbn: '9780451524935', titulo: '1984' },
];

// Con ISBN válido la consulta a /lookup se dispara sola (debounce); nunca guarda nada.
@Component({
  selector: 'app-book-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, CoverComponent, IconComponent, NoteComponent, SheetComponent],
  templateUrl: './book-form-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookFormDialogComponent {
  readonly closed = output<void>();
  readonly created = output<void>();

  private readonly books = inject(BooksService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly sheet = viewChild.required(SheetComponent);

  protected readonly form = this.fb.nonNullable.group({
    isbn: ['', [Validators.required, isbnValidator()]],
    title: ['', [Validators.required]],
    author: ['', [Validators.required]],
    year: ['', [Validators.pattern(/^\d{0,4}$/)]],
  });

  protected readonly coverUrl = signal<string | null>(null);
  protected readonly subjects = signal<string[]>([]);
  protected readonly source = signal<string | null>(null);
  protected readonly looking = signal(false);
  protected readonly saving = signal(false);
  protected readonly enviado = signal(false);
  protected readonly aviso = signal<Aviso | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected readonly EJEMPLOS = ISBN_EJEMPLO;

  protected readonly isbnNormalizado = signal('');
  protected readonly isbnLooksValid = computed(() => isbnValido(this.isbnNormalizado()));
  private ultimoConsultado: string | null = null;

  constructor() {
    const isbn$ = this.form.controls.isbn.valueChanges.pipe(map((v) => normalizarIsbn(v ?? '')));
    isbn$.subscribe((n) => this.isbnNormalizado.set(n));
    isbn$.pipe(debounceTime(500), takeUntilDestroyed()).subscribe((n) => {
      if (isbnValido(n) && n !== this.ultimoConsultado) this.autocompletar();
    });

    afterRenderEffect(() => {
      if (!this.source() || menosMovimiento()) return;
      const root = this.host.nativeElement;
      gsap.fromTo(
        root.querySelectorAll('.in--filled'),
        { backgroundColor: 'var(--moss-wash)' },
        { backgroundColor: 'var(--surface)', duration: 1.1, ease: 'power2.out', stagger: 0.06, clearProps: 'backgroundColor' },
      );
      const preview = root.querySelector('.preview');
      if (preview) gsap.fromTo(preview, ...aparecer({ y: 10 }, { duration: 0.4 }));
    });
  }

  protected error(campo: 'isbn' | 'title' | 'author' | 'year'): string | null {
    const server = this.serverErrors()[campo === 'year' ? 'publicationYear' : campo];
    if (server) return server;
    const c = this.form.controls[campo];
    if (campo === 'isbn' && c.value && c.invalid) return c.errors?.['isbn'] ?? ISBN_MSG;
    if (!this.enviado() || c.valid) return null;
    if (campo === 'isbn') return c.errors?.['isbn'] ?? ISBN_MSG;
    if (campo === 'title') return 'El título es obligatorio si la API no lo completó.';
    if (campo === 'author') return 'El autor es obligatorio si la API no lo completó.';
    return 'Año fuera de rango.';
  }

  protected relleno(campo: 'title' | 'author' | 'year'): boolean {
    return !!this.source() && !!this.form.controls[campo].value;
  }

  protected cerrar(): void {
    this.sheet().close();
  }

  protected enterEnIsbn(ev: Event): void {
    if (this.isbnLooksValid() && !this.form.controls.title.value) {
      ev.preventDefault();
      this.autocompletar();
    }
  }

  protected usarEjemplo(isbn: string): void {
    this.form.controls.isbn.setValue(isbn);
    this.autocompletar();
  }

  protected autocompletar(): void {
    if (!this.isbnLooksValid()) {
      this.enviado.set(true);
      return;
    }
    this.ultimoConsultado = this.isbnNormalizado();
    this.looking.set(true);
    this.aviso.set(null);
    this.serverErrors.set({});
    this.source.set(null);
    this.books.lookup(this.isbnNormalizado()).subscribe({
      next: (preview) => {
        this.form.patchValue({
          title: preview.title ?? '',
          author: preview.author ?? '',
          year: preview.publicationYear ? String(preview.publicationYear) : '',
        });
        this.coverUrl.set(preview.coverUrl);
        this.subjects.set(preview.subjects ?? []);
        this.source.set(preview.source);
        this.aviso.set(
          preview.alreadyRegistered
            ? { kind: 'warn', text: 'Ojo: este ISBN ya está en el catálogo. Guardarlo dará error de ISBN duplicado.' }
            : { kind: 'ok', text: `Datos traídos de ${preview.source}. Puedes corregirlos antes de guardar.` },
        );
        this.looking.set(false);
      },
      error: (err: unknown) => {
        // Que la API externa falle no puede bloquear el registro.
        const api = err instanceof ApiError ? err : null;
        this.aviso.set({
          kind: 'warn',
          text:
            api?.code === 'EXTERNAL_LOOKUP_FAILED'
              ? 'Open Library no respondió. Escribe los datos a mano: el libro se guarda igual.'
              : (api?.message ?? 'No encontramos ese ISBN. Escribe los datos a mano.'),
        });
        this.looking.set(false);
      },
    });
  }

  protected guardar(): void {
    this.enviado.set(true);
    this.formError.set(null);
    this.serverErrors.set({});
    const year = this.form.controls.year.value;
    if (year && (Number(year) < 1450 || Number(year) > 2100)) {
      this.form.controls.year.setErrors({ range: true });
    }
    if (this.form.invalid) return;

    this.saving.set(true);
    const { title, author } = this.form.getRawValue();
    this.books
      .create({
        isbn: this.isbnNormalizado(),
        title: title.trim(),
        author: author.trim(),
        publicationYear: year ? Number(year) : null,
        coverUrl: this.coverUrl(),
        subjects: this.subjects(),
      })
      .subscribe({
        next: (book) => {
          this.toast.success(`«${book.title}» ya está en el catálogo.`);
          this.saving.set(false);
          this.created.emit();
        },
        error: (err: unknown) => {
          this.formError.set(mensajeDe(err, 'No se pudo guardar el libro.'));
          if (err instanceof ApiError) this.serverErrors.set(err.fieldErrors);
          this.saving.set(false);
        },
      });
  }
}
