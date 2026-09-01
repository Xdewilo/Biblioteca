// by Jeremy Posada
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { Stats, User } from '@core/auth/models/auth.models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin`;

  stats(): Observable<Stats> {
    return this.http.get<Stats>(`${this.base}/stats`);
  }

  users(): Observable<User[]> {
    return this.http.get<User[]>(`${this.base}/users`);
  }

  unblock(id: number): Observable<User> {
    return this.http.post<User>(`${this.base}/users/${id}/unblock`, null);
  }
}
