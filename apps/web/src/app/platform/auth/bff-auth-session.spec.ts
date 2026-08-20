import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { BffAuthSession } from './bff-auth-session';

describe('BffAuthSession', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), BffAuthSession],
    });
  });

  it('derives browser authentication from the BFF session without receiving OAuth tokens', async () => {
    const session = TestBed.inject(BffAuthSession);
    const http = TestBed.inject(HttpTestingController);
    const initialized = session.initialize();
    const request = http.expectOne('/bff/session');

    request.flush({
      authenticated: true,
      csrf: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' },
    });

    await initialized;
    expect(session.status()).toBe('authenticated');
    expect(session.csrf()?.token).toBe('csrf-token');
    http.verify();
  });

  it('represents an anonymous BFF session explicitly', async () => {
    const session = TestBed.inject(BffAuthSession);
    const http = TestBed.inject(HttpTestingController);
    const initialized = session.initialize();
    http.expectOne('/bff/session').flush({
      authenticated: false,
      csrf: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' },
    });

    await initialized;
    expect(session.status()).toBe('unauthenticated');
    http.verify();
  });
});
