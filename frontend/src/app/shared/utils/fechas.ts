// by Jeremy Posada
/** dd/mm/aaaa desde un LocalDate ISO, sin desfase de zona horaria. */
export function fecha(value: string | null | undefined): string {
  if (!value) return '—';
  const [y, m, d] = value.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

export function fechaHora(value: string | null | undefined): string {
  if (!value) return '—';
  return new Date(value).toLocaleString('es-CO', { dateStyle: 'short', timeStyle: 'short' });
}

export function diasHasta(value: string, hoy: Date = new Date()): number {
  const [y, m, d] = value.slice(0, 10).split('-').map(Number);
  const target = new Date(y, m - 1, d);
  const base = new Date(hoy);
  base.setHours(0, 0, 0, 0);
  return Math.round((target.getTime() - base.getTime()) / 86_400_000);
}

export function venceEn(dueDate: string, hoy: Date = new Date()): string {
  const n = diasHasta(dueDate, hoy);
  if (n === 0) return 'Vence hoy';
  if (n === 1) return 'Vence mañana';
  if (n > 1) return `Vence en ${n} días`;
  return `Venció hace ${-n} ${-n === 1 ? 'día' : 'días'}`;
}

export function iniciales(nombre: string | null | undefined): string {
  if (!nombre) return '?';
  return nombre
    .split(' ')
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? '')
    .join('');
}
