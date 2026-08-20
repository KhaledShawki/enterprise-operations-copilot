import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree, convertToParamMap, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { AUTH_SESSION, AuthSession, AuthSessionStatus } from '../auth/auth-session';
import { TenantContext } from './tenant-context';
import { activateTenantRoute, redirectToDefaultWorkspace } from './tenant-routing';

function auth(status: AuthSessionStatus): AuthSession {
  return { status: signal(status).asReadonly(), csrf: signal(null).asReadonly(), initialize: async () => undefined };
}

describe('tenant routing', () => {
  it('routes anonymous sessions to sign in', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AUTH_SESSION, useValue: auth('unauthenticated') }, {
      provide: TenantContext, useValue: { status: signal<'idle'>('idle').asReadonly() },
    }] });
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => redirectToDefaultWorkspace({} as never)) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/sign-in');
  });

  it('routes authenticated sessions to the first authorized tenant', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AUTH_SESSION, useValue: auth('authenticated') }, {
      provide: TenantContext, useValue: {
        status: signal<'ready'>('ready').asReadonly(),
        firstAccessibleTenant: () => ({ tenantKey: 'acme' }),
      },
    }] });
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => redirectToDefaultWorkspace({} as never)) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/t/acme');
  });

  it('never substitutes an inaccessible tenant', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AUTH_SESSION, useValue: auth('authenticated') }, {
      provide: TenantContext, useValue: {
        status: signal<'ready'>('ready').asReadonly(),
        activateFromRoute: () => false,
      },
    }] });
    const router = TestBed.inject(Router);
    const route = { paramMap: convertToParamMap({ tenantKey: 'other' }) } as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => activateTenantRoute(route, {} as never)) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/tenant-unavailable');
  });
});
