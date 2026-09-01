// by Jeremy Posada
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { Loan } from '@core/auth/models/auth.models';

@Injectable({ providedIn: 'root' })
export class LoansService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/loans`;

  create(bookId: number): Observable<Loan> {
    return this.http.post<Loan>(this.base, { bookId });
  }

  mine(): Observable<Loan[]> {
    return this.http.get<Loan[]>(`${this.base}/mine`);
  }

  all(): Observable<Loan[]> {
    return this.http.get<Loan[]>(this.base);
  }

  markReturned(id: number): Observable<Loan> {
    return this.http.put<Loan>(`${this.base}/${id}/return`, null);
  }
}
