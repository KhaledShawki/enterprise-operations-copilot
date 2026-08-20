import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { AUTH_SESSION } from './platform/auth/auth-session';
import { BffAuthSession } from './platform/auth/bff-auth-session';
import { csrfTokenInterceptor } from './platform/auth/csrf-token-interceptor';
import { TenantContext } from './platform/tenant/tenant-context';

async function initializeWorkspace(): Promise<void> {
  const authSession = inject(AUTH_SESSION);
  const tenantContext = inject(TenantContext);

  await authSession.initialize();

  if (authSession.status() === 'authenticated') {
    await tenantContext.initialize();
  }
}

export const appConfig: ApplicationConfig = {
  providers: [
    BffAuthSession,
    { provide: AUTH_SESSION, useExisting: BffAuthSession },
    provideHttpClient(withInterceptors([csrfTokenInterceptor])),
    provideAppInitializer(initializeWorkspace),
    provideRouter(routes),
  ],
};
