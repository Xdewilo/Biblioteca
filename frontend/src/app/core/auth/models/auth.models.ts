// by Jeremy Posada

export type Role = 'ADMIN' | 'BIBLIOTECARIO';
export type BookStatus = 'DISPONIBLE' | 'PRESTADO' | 'RESERVADO';
export type ReservationStatus = 'PENDIENTE' | 'NOTIFICADO' | 'CANCELADO' | 'CUMPLIDO';

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  blocked: boolean;
  blockedUntil: string | null;
  blockedReason: string | null;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

export interface Book {
  id: number;
  title: string;
  author: string;
  isbn: string;
  publicationYear: number | null;
  status: BookStatus;
  coverUrl: string | null;
  subjects: string[];
  enrichedFromExternal: boolean;
}

export interface BookLookup {
  isbn: string;
  title: string | null;
  author: string | null;
  publicationYear: number | null;
  coverUrl: string | null;
  subjects: string[];
  source: string;
  alreadyRegistered: boolean;
}

export interface CreateBookPayload {
  isbn: string;
  title?: string;
  author?: string;
  publicationYear?: number | null;
  coverUrl?: string | null;
  subjects?: string[];
}

export interface Loan {
  id: number;
  bookId: number;
  bookTitle: string;
  bookAuthor: string;
  bookIsbn: string;
  bookCoverUrl: string | null;
  borrowerName: string;
  borrowerEmail: string;
  loanDate: string;
  dueDate: string;
  returnDate: string | null;
  returned: boolean;
  overdue: boolean;
  daysOverdue: number;
  returnedLate: boolean;
}

export interface Reservation {
  id: number;
  bookId: number;
  bookTitle: string;
  requesterEmail: string;
  requestedAt: string;
  status: ReservationStatus;
  notifiedAt: string | null;
  /** Hasta cuándo se te guarda el libro. null si aún no te toca el turno. */
  holdExpiresAt: string | null;
  readyToConfirm: boolean;
  queuePosition: number | null;
}

export interface Stats {
  totalBooks: number;
  availableBooks: number;
  loanedBooks: number;
  reservedBooks: number;
  activeLoans: number;
  overdueLoans: number;
  dueSoonLoans: number;
  totalUsers: number;
  blockedUsers: number;
  pendingReservations: number;
  blockedAccounts: User[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  errors?: { field: string; message: string }[];
}
