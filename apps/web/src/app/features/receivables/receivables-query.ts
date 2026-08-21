import { ParamMap, Params } from '@angular/router';

import {
  ReceivablesQuery,
  ReceivableSortField,
  ReceivableStatus,
  SortDirection,
} from './data-access/receivables-api';

const STATUSES: readonly ReceivableStatus[] = ['OPEN', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'];
const SORT_FIELDS: readonly ReceivableSortField[] = [
  'DUE_DATE',
  'ISSUE_DATE',
  'OUTSTANDING_AMOUNT',
  'INVOICE_NUMBER',
];
const DIRECTIONS: readonly SortDirection[] = ['ASC', 'DESC'];
const PAGE_SIZES = [25, 50, 100] as const;

export const DEFAULT_RECEIVABLES_QUERY: ReceivablesQuery = {
  businessDate: null,
  status: null,
  overdue: null,
  page: 0,
  size: 25,
  sort: 'DUE_DATE',
  direction: 'ASC',
};

export function parseReceivablesQuery(params: ParamMap): ReceivablesQuery {
  const status = member(params.get('status'), STATUSES);
  const sort = member(params.get('sort'), SORT_FIELDS) ?? DEFAULT_RECEIVABLES_QUERY.sort;
  const direction =
    member(params.get('direction'), DIRECTIONS) ?? DEFAULT_RECEIVABLES_QUERY.direction;
  const requestedSize = integer(params.get('size'), DEFAULT_RECEIVABLES_QUERY.size);
  const size = PAGE_SIZES.includes(requestedSize as (typeof PAGE_SIZES)[number])
    ? requestedSize
    : DEFAULT_RECEIVABLES_QUERY.size;
  const overdueValue = params.get('overdue');

  return {
    businessDate: nonBlank(params.get('businessDate')),
    status,
    overdue: overdueValue === 'true' ? true : overdueValue === 'false' ? false : null,
    page: Math.max(0, integer(params.get('page'), DEFAULT_RECEIVABLES_QUERY.page)),
    size,
    sort,
    direction,
  };
}

export function toReceivablesQueryParams(query: ReceivablesQuery): Params {
  const params: Params = {};

  if (query.businessDate) params['businessDate'] = query.businessDate;
  if (query.status) params['status'] = query.status;
  if (query.overdue !== null) params['overdue'] = query.overdue;
  if (query.page !== DEFAULT_RECEIVABLES_QUERY.page) params['page'] = query.page;
  if (query.size !== DEFAULT_RECEIVABLES_QUERY.size) params['size'] = query.size;
  if (query.sort !== DEFAULT_RECEIVABLES_QUERY.sort) params['sort'] = query.sort;
  if (query.direction !== DEFAULT_RECEIVABLES_QUERY.direction) {
    params['direction'] = query.direction;
  }

  return params;
}

function nonBlank(value: string | null): string | null {
  return value && value.trim().length > 0 ? value : null;
}

function integer(value: string | null, fallback: number): number {
  if (value === null || !/^\d+$/.test(value)) return fallback;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : fallback;
}

function member<T extends string>(value: string | null, values: readonly T[]): T | null {
  return value !== null && values.includes(value as T) ? (value as T) : null;
}
