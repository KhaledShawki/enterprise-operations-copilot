import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { CsrfTokenStore } from './csrf-token-store';
import { csrfTokenInterceptor } from './csrf-token-interceptor';

describe('csrfTokenInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([csrfTokenInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    TestBed.inject(CsrfTokenStore).set({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-token',
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('adds CSRF only to unsafe same-origin BFF/API requests', async () => {
    const response = firstValueFrom(http.post('/api/v1/example', {}));
    const request = httpTesting.expectOne('/api/v1/example');
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf-token');
    request.flush({});
    await response;
  });

  it('does not send CSRF metadata to unrelated origins', async () => {
    const response = firstValueFrom(http.post('https://example.test/public', {}));
    const request = httpTesting.expectOne('https://example.test/public');
    expect(request.request.headers.has('X-CSRF-TOKEN')).toBe(false);
    request.flush({});
    await response;
  });
});
