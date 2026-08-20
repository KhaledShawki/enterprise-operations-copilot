import { inject } from '@angular/core';
import { CanActivateFn, RedirectFunction, Router } from '@angular/router';

import { AUTH_SESSION } from '../auth/auth-session';
import { TenantContext } from './tenant-context';

export const redirectToDefaultWorkspace: RedirectFunction = () => {
  const router = inject(Router);
  const auth = inject(AUTH_SESSION);
  const tenants = inject(TenantContext);

  if (auth.status() === 'unauthenticated') return router.createUrlTree(['/sign-in']);
  if (auth.status() !== 'authenticated' || tenants.status() === 'failed') {
    return router.createUrlTree(['/session-unavailable']);
  }
  if (tenants.status() === 'no-access') return router.createUrlTree(['/no-tenant-access']);

  const tenant = tenants.firstAccessibleTenant();
  return tenant ? router.createUrlTree(['/t', tenant.tenantKey]) : router.createUrlTree(['/session-unavailable']);
};

export const activateTenantRoute: CanActivateFn = (route) => {
  const router = inject(Router);
  const auth = inject(AUTH_SESSION);
  const tenants = inject(TenantContext);

  if (auth.status() === 'unauthenticated') return router.createUrlTree(['/sign-in']);
  if (auth.status() !== 'authenticated' || tenants.status() === 'failed') {
    return router.createUrlTree(['/session-unavailable']);
  }
  if (tenants.status() === 'no-access') return router.createUrlTree(['/no-tenant-access']);

  const tenantKey = route.paramMap.get('tenantKey');
  return tenantKey && tenants.activateFromRoute(tenantKey)
    ? true
    : router.createUrlTree(['/tenant-unavailable']);
};
