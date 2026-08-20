import { Routes } from '@angular/router';

import { activateTenantRoute, redirectToDefaultWorkspace } from './platform/tenant/tenant-routing';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: redirectToDefaultWorkspace,
  },
  {
    path: 'sign-in',
    loadComponent: () =>
      import('./shell/sign-in/sign-in-page').then(({ SignInPage }) => SignInPage),
    title: 'Sign in | Enterprise Operations Copilot',
  },
  {
    path: 't/:tenantKey',
    canActivate: [activateTenantRoute],
    loadComponent: () =>
      import('./shell/app-shell/app-shell').then(({ AppShell }) => AppShell),
    title: 'Workspace | Enterprise Operations Copilot',
  },
  {
    path: 'no-tenant-access',
    loadComponent: () =>
      import('./shell/access-state/access-state-page').then(({ AccessStatePage }) => AccessStatePage),
    data: { accessState: 'no-tenant-access' },
    title: 'No tenant access | Enterprise Operations Copilot',
  },
  {
    path: 'tenant-unavailable',
    loadComponent: () =>
      import('./shell/access-state/access-state-page').then(({ AccessStatePage }) => AccessStatePage),
    data: { accessState: 'tenant-unavailable' },
    title: 'Tenant unavailable | Enterprise Operations Copilot',
  },
  {
    path: 'session-unavailable',
    loadComponent: () =>
      import('./shell/access-state/access-state-page').then(({ AccessStatePage }) => AccessStatePage),
    data: { accessState: 'session-unavailable' },
    title: 'Session unavailable | Enterprise Operations Copilot',
  },
  {
    path: '**',
    redirectTo: '',
  },
];
