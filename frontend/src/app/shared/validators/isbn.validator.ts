// by Jeremy Posada
import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { isbnValido, normalizarIsbn } from '@shared/utils/isbn';

export const ISBN_MSG = 'El ISBN debe tener 10 o 13 dígitos y un dígito de control correcto.';

export function mensajeIsbn(normalizado: string): string | null {
  if (isbnValido(normalizado)) return null;
  const n = normalizado.length;
  if (n !== 10 && n !== 13) {
    return `Llevas ${n} ${n === 1 ? 'dígito' : 'dígitos'}: un ISBN tiene 10 o 13.`;
  }
  return 'Los dígitos no cuadran con el de control: revisa si se te fue un número.';
}

export function isbnValidator(): ValidatorFn {
  return (control: AbstractControl<string | null>): ValidationErrors | null => {
    const raw = control.value ?? '';
    if (!raw.trim()) return null; // lo cubre `required`
    const msg = mensajeIsbn(normalizarIsbn(raw));
    return msg ? { isbn: msg } : null;
  };
}
