import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./shell/app-shell/app-shell').then(({ AppShell }) => AppShell),
    pathMatch: 'full',
    title: 'Workspace | Enterprise Operations Copilot',
  },
  {
    path: '**',
    redirectTo: '',
  },
];
