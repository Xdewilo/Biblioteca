// by Jeremy Posada
import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { environment } from '@environments/environment';
import { ApiError } from '@core/http/api-error';
import { AuthResponse, User } from '@core/auth/models/auth.models';

const STORAGE_KEY = 'anaquel-auth';

interface Persisted {
  token: string | null;
  user: User | null;
}

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  private readonly _token = signal<string | null>(null);
  private readonly _user = signal<User | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly token = this._token.asReadonly();
  readonly user = this._user.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();
  readonly isAuthenticated = computed(() => this._token() !== null);
  readonly isAdmin = computed(() => this._user()?.role === 'ADMIN');

  constructor() {
    this.restore();
  }

  async login(email: string, password: string): Promise<void> {
    await this.autenticar(() =>
      this.http.post<AuthResponse>(`${this.base}/api/auth/login`, { email, password }),
    );
  }

  async register(name: string, email: string, password: string): Promise<void> {
    await this.autenticar(() =>
      this.http.post<AuthResponse>(`${this.base}/api/auth/register`, { name, email, password }),
    );
  }

  logout(): void {
    this._token.set(null);
    this._user.set(null);
    this._error.set(null);
    this.persist();
  }

  async refreshUser(): Promise<void> {
    if (!this._token()) return;
    try {
      const user = await firstValueFrom(this.http.get<User>(`${this.base}/api/auth/me`));
      this._user.set(user);
      this.persist();
    } catch {
      /* si falla se conserva el usuario en caché; el 401 ya cierra la sesión */
    }
  }

  clearError(): void {
    this._error.set(null);
  }

  private async autenticar(peticion: () => ReturnType<HttpClient['post']>): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const response = (await firstValueFrom(peticion())) as AuthResponse;
      this._token.set(response.token);
      this._user.set(response.user);
      this.persist();
    } catch (error) {
      this._error.set(error instanceof ApiError ? error.message : 'No se pudo iniciar sesión.');
      throw error;
    } finally {
      this._loading.set(false);
    }
  }

  private restore(): void {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const saved = JSON.parse(raw) as Persisted;
      this._token.set(saved.token ?? null);
      this._user.set(saved.user ?? null);
    } catch {
      /* almacenamiento corrupto o no disponible: se arranca sin sesión */
    }
  }

  private persist(): void {
    try {
      const data: Persisted = { token: this._token(), user: this._user() };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch {
      /* modo privado o cuota llena: la sesión vive solo en memoria */
    }
  }
}
