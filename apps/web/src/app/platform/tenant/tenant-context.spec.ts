import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';

import { ApiProblemError } from '../api/api-problem';
import { CurrentUser, CurrentUserApi, CurrentUserTenant } from './current-user-api';
import { TenantContext } from './tenant-context';

const currentUser: CurrentUser = { issuer: 'issuer', subject: 'subject', roles: ['platform-admin'] };
const tenants: readonly CurrentUserTenant[] = [
  { membershipId: '1', tenantId: '2', tenantKey: 'zeta', displayName: 'Zeta AG', roles: [] },
  { membershipId: '3', tenantId: '4', tenantKey: 'acme', displayName: 'Acme AG', roles: [] },
];

function context(
  user: Observable<CurrentUser> = of(currentUser),
  memberships: Observable<readonly CurrentUserTenant[]> = of(tenants),
): TenantContext {
  TestBed.configureTestingModule({ providers: [TenantContext, {
    provide: CurrentUserApi,
    useValue: { getCurrentUser: () => user, getAccessibleTenants: () => memberships },
  }] });
  return TestBed.inject(TenantContext);
}

describe('TenantContext', () => {
  it('loads and deterministically orders authorized tenants', async () => {
    const value = context();
    await value.initialize();
    expect(value.status()).toBe('ready');
    expect(value.tenants().map((tenant) => tenant.tenantKey)).toEqual(['acme', 'zeta']);
  });

  it('activates only a tenant present in the membership response', async () => {
    const value = context();
    await value.initialize();
    expect(value.activateFromRoute('zeta')).toBe(true);
    expect(value.activateFromRoute('other')).toBe(false);
    expect(value.activeTenant()).toBeNull();
  });

  it('distinguishes an authenticated user with no tenant memberships', async () => {
    const value = context(of(currentUser), of([]));
    await value.initialize();
    expect(value.status()).toBe('no-access');
  });

  it('fails honestly when bootstrap data is unavailable', async () => {
    const problem = new ApiProblemError({ title: 'Unavailable', status: 502 });
    const value = context(of(currentUser), throwError(() => problem));
    await value.initialize();
    expect(value.status()).toBe('failed');
    expect(value.problem()).toBe(problem);
  });
});
