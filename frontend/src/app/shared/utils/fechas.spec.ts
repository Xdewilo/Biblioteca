// by Jeremy Posada
import { diasHasta, fecha, iniciales, venceEn } from './fechas';

describe('fechas', () => {
  const hoy = new Date(2026, 7, 31); // 31/08/2026

  it('formatea un LocalDate ISO como dd/mm/aaaa sin desfase de zona horaria', () => {
    expect(fecha('2026-09-14')).toBe('14/09/2026');
    expect(fecha(null)).toBe('—');
  });

  it('cuenta los días hasta la fecha límite', () => {
    expect(diasHasta('2026-08-31', hoy)).toBe(0);
    expect(diasHasta('2026-09-02', hoy)).toBe(2);
    expect(diasHasta('2026-08-28', hoy)).toBe(-3);
  });

  it('describe el plazo de un vistazo', () => {
    expect(venceEn('2026-08-31', hoy)).toBe('Vence hoy');
    expect(venceEn('2026-09-01', hoy)).toBe('Vence mañana');
    expect(venceEn('2026-09-05', hoy)).toBe('Vence en 5 días');
    expect(venceEn('2026-08-30', hoy)).toBe('Venció hace 1 día');
    expect(venceEn('2026-08-25', hoy)).toBe('Venció hace 6 días');
  });

  it('saca las iniciales del nombre', () => {
    expect(iniciales('Ana Pérez')).toBe('AP');
    expect(iniciales('Administrador')).toBe('A');
    expect(iniciales(undefined)).toBe('?');
  });
});
