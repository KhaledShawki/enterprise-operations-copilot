import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ApiProblemError } from '../../../platform/api/api-problem';
import { ReceivablesApi } from './receivables-api';

describe('ReceivablesApi', () => {
  let api: ReceivablesApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ReceivablesApi],
    });
    api = TestBed.inject(ReceivablesApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('maps tenant-scoped URL state to the receivables list contract', () => {
    api
      .list('00000000-0000-0000-0000-000000000071', {
        businessDate: '2026-08-20',
        status: 'OPEN',
        overdue: true,
        page: 2,
        size: 25,
        sort: 'OUTSTANDING_AMOUNT',
        direction: 'DESC',
      })
      .subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url ===
          '/api/v1/tenants/00000000-0000-0000-0000-000000000071/analytics/receivables' &&
        candidate.params.get('businessDate') === '2026-08-20' &&
        candidate.params.get('status') === 'OPEN' &&
        candidate.params.get('overdue') === 'true' &&
        candidate.params.get('page') === '2' &&
        candidate.params.get('size') === '25' &&
        candidate.params.get('sort') === 'OUTSTANDING_AMOUNT' &&
        candidate.params.get('direction') === 'DESC',
    );
    request.flush({
      receivables: [],
      page: 2,
      size: 25,
      totalElements: 0,
      totalPages: 0,
      businessDate: '2026-08-20',
      hasNext: false,
      hasPrevious: true,
    });
  });

  it('normalizes backend Problem Details without exposing detail as the message', () => {
    let received: unknown;

    api.summary('00000000-0000-0000-0000-000000000071', null).subscribe({
      error: (error: unknown) => {
        received = error;
      },
    });

    http
      .expectOne(
        '/api/v1/tenants/00000000-0000-0000-0000-000000000071/analytics/receivables/summary',
      )
      .flush(
        {
          title: 'Access denied',
          detail: 'Sensitive backend diagnostic',
          code: 'ACCESS_DENIED',
        },
        { status: 403, statusText: 'Forbidden' },
      );

    expect(received).toBeInstanceOf(ApiProblemError);
    expect((received as ApiProblemError).message).toBe('Access denied');
    expect((received as ApiProblemError).message).not.toContain('Sensitive backend diagnostic');
  });
});
