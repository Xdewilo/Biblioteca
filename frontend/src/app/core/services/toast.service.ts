// by Jeremy Posada
import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
  leaving: boolean;
}

const VISIBLE_MS = 4600;
const LEAVE_MS = 320;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _toasts = signal<Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();
  private nextId = 1;

  success(message: string): void {
    this.push('success', message);
  }

  error(message: string): void {
    this.push('error', message);
  }

  info(message: string): void {
    this.push('info', message);
  }

  dismiss(id: number): void {
    if (!this._toasts().some((t) => t.id === id && !t.leaving)) return;
    this._toasts.update((list) => list.map((t) => (t.id === id ? { ...t, leaving: true } : t)));
    setTimeout(() => this._toasts.update((list) => list.filter((t) => t.id !== id)), LEAVE_MS);
  }

  private push(kind: ToastKind, message: string): void {
    const id = this.nextId++;
    this._toasts.update((list) => [...list, { id, kind, message, leaving: false }]);
    setTimeout(() => this.dismiss(id), VISIBLE_MS);
  }
}
