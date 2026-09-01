// by Jeremy Posada
import { isbnValido, normalizarIsbn } from './isbn';

describe('isbn — la misma regla que el backend (IsbnUtils.isValid)', () => {
  it('normaliza guiones, espacios y la x minúscula', () => {
    expect(normalizarIsbn('978-0-13-235088-4')).toBe('9780132350884');
    expect(normalizarIsbn('978 0 13 235088 4')).toBe('9780132350884');
    expect(normalizarIsbn('080442957x')).toBe('080442957X');
  });

  it('acepta ISBN-13 e ISBN-10 con dígito de control correcto', () => {
    for (const ok of ['9780132350884', '9780134685991', '9788497592581', '0306406152', '080442957X']) {
      expect(isbnValido(ok)).withContext(ok).toBeTrue();
    }
  });

  it('rechaza dígitos de control incorrectos y formas inválidas', () => {
    for (const bad of ['9780132350885', '9999999999999', '0306406153', '080442957A', '', '12345']) {
      expect(isbnValido(bad)).withContext(bad).toBeFalse();
    }
  });
});
