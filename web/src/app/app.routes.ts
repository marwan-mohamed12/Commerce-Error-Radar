import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadChildren: () => import('./features/inbox/inbox.routes').then((m) => m.INBOX_ROUTES),
  },
  { path: '**', redirectTo: '' },
];
