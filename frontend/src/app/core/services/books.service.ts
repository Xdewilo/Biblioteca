// by Jeremy Posada
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import { Book, BookLookup, BookStatus, CreateBookPayload, Page } from '@core/auth/models/auth.models';

export interface BookQuery {
  search?: string;
  status?: BookStatus | '';
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class BooksService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/books`;

  list(query: BookQuery): Observable<Page<Book>> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? 12));
    if (query.search) params = params.set('search', query.search);
    if (query.status) params = params.set('status', query.status);
    return this.http.get<Page<Book>>(this.base, { params });
  }

  lookup(isbn: string): Observable<BookLookup> {
    return this.http.get<BookLookup>(`${this.base}/lookup/${encodeURIComponent(isbn)}`);
  }

  create(payload: CreateBookPayload): Observable<Book> {
    return this.http.post<Book>(this.base, payload);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
