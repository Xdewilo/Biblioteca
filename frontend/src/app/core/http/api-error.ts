// by Jeremy Posada
import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorBody } from '@core/auth/models/auth.models';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }

  static from(error: HttpErrorResponse): ApiError {
    if (error.status === 0) {
      return new ApiError(0, 'NETWORK_ERROR', 'No se pudo contactar el servidor. Revisa tu conexión.');
    }
    const body = (typeof error.error === 'object' ? error.error : null) as Partial<ApiErrorBody> | null;
    // 502/503/504 sin JSON: es nginx diciendo que el backend no está (caído o arrancando).
    if (!body?.code && (error.status === 502 || error.status === 503 || error.status === 504)) {
      return new ApiError(error.status, 'BACKEND_UNAVAILABLE', 'El servidor no responde ahora mismo. Inténtalo en un momento.');
    }
    const fieldErrors: Record<string, string> = {};
    body?.errors?.forEach((v) => (fieldErrors[v.field] = v.message));
    return new ApiError(
      error.status,
      body?.code ?? 'UNKNOWN_ERROR',
      body?.message ?? `El servidor respondió ${error.status}.`,
      fieldErrors,
    );
  }
}

export function mensajeDe(error: unknown, respaldo: string): string {
  return error instanceof ApiError ? error.message : respaldo;
}
