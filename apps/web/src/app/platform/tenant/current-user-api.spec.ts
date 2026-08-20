import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ApiProblemError } from '../api/api-problem';
import { CurrentUserApi } from './current-user-api';

describe('CurrentUserApi', () => {
  let api: CurrentUserApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(CurrentUserApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads trusted current-user identity through the BFF', async () => {
    const result = firstValueFrom(api.getCurrentUser());
    http.expectOne('/api/v1/me').flush({
      issuer: 'http://localhost:8180/realms/eoc',
      subject: 'user-123',
      roles: ['platform-admin'],
    });
    await expect(result).resolves.toEqual({
      issuer: 'http://localhost:8180/realms/eoc',
      subject: 'user-123',
      roles: ['platform-admin'],
    });
  });

  it('loads only backend-authorized tenant memberships', async () => {
    const result = firstValueFrom(api.getAccessibleTenants());
    http.expectOne('/api/v1/me/tenants').flush({
      tenants: [{
        membershipId: '00000000-0000-0000-0000-000000000001',
        tenantId: '00000000-0000-0000-0000-000000000002',
        tenantKey: 'acme',
        displayName: 'Acme AG',
        roles: ['operations-manager'],
      }],
    });
    await expect(result).resolves.toHaveLength(1);
  });

  it('normalizes forwarded platform Problem Details', async () => {
    const result = firstValueFrom(api.getAccessibleTenants());
    http.expectOne('/api/v1/me/tenants').flush(
      { title: 'Platform user not found', code: 'PLATFORM_USER_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    await expect(result).rejects.toBeInstanceOf(ApiProblemError);
  });
});
