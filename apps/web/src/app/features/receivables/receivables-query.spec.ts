import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';

import {
  DEFAULT_RECEIVABLES_QUERY,
  parseReceivablesQuery,
  toReceivablesQueryParams,
} from './receivables-query';

describe('receivables URL state', () => {
  it('uses stable defaults for absent or unsupported values', () => {
    expect(
      parseReceivablesQuery(
        convertToParamMap({
          page: '-1',
          size: '999',
          status: 'UNKNOWN',
          sort: 'UNKNOWN',
          direction: 'SIDEWAYS',
        }),
      ),
    ).toEqual(DEFAULT_RECEIVABLES_QUERY);
  });

  it('round-trips supported filters, sorting and pagination into shareable query params', () => {
    const query = parseReceivablesQuery(
      convertToParamMap({
        businessDate: '2026-08-20',
        status: 'PARTIALLY_PAID',
        overdue: 'true',
        page: '3',
        size: '50',
        sort: 'OUTSTANDING_AMOUNT',
        direction: 'DESC',
      }),
    );

    expect(toReceivablesQueryParams(query)).toEqual({
      businessDate: '2026-08-20',
      status: 'PARTIALLY_PAID',
      overdue: true,
      page: 3,
      size: 50,
      sort: 'OUTSTANDING_AMOUNT',
      direction: 'DESC',
    });
  });
});
