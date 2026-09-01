// by Jeremy Posada
export function normalizarIsbn(raw: string): string {
  return raw.replace(/[^0-9Xx]/g, '').toUpperCase();
}

// Misma regla que IsbnUtils.isValid del backend (mod 11 ISBN-10, mod 10 ISBN-13).
export function isbnValido(n: string): boolean {
  if (n.length === 10) {
    let suma = 0;
    for (let i = 0; i < 10; i++) {
      const c = n[i];
      const v = c === 'X' && i === 9 ? 10 : c >= '0' && c <= '9' ? Number(c) : NaN;
      if (Number.isNaN(v)) return false;
      suma += v * (10 - i);
    }
    return suma % 11 === 0;
  }
  if (n.length === 13) {
    if (!/^\d{13}$/.test(n)) return false;
    let suma = 0;
    for (let i = 0; i < 13; i++) suma += Number(n[i]) * (i % 2 === 0 ? 1 : 3);
    return suma % 10 === 0;
  }
  return false;
}
