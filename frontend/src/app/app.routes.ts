// by Jeremy Posada
import { Routes } from '@angular/router';
import { authGuard, guestGuard } from '@core/auth/guards/auth.guard';
import { adminGuard } from '@core/auth/guards/admin.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('@features/auth/pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('@app/layouts/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'catalogo' },
      {
        path: 'catalogo',
        loadComponent: () =>
          import('@features/catalog/pages/catalog/catalog.component').then((m) => m.CatalogComponent),
      },
      {
        path: 'mis-prestamos',
        loadComponent: () =>
          import('@features/loans/pages/my-loans/my-loans.component').then((m) => m.MyLoansComponent),
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('@features/admin/pages/admin/admin.component').then((m) => m.AdminComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'catalogo' },
];
