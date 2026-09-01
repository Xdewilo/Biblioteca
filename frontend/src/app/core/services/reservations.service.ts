// by Jeremy Posada
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { Loan, Reservation } from '@core/auth/models/auth.models';

@Injectable({ providedIn: 'root' })
export class ReservationsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/reservations`;

  create(bookId: number): Observable<Reservation> {
    return this.http.post<Reservation>(this.base, { bookId });
  }

  mine(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.base}/mine`);
  }

  confirm(id: number): Observable<Loan> {
    return this.http.post<Loan>(`${this.base}/${id}/confirm`, null);
  }

  cancel(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
