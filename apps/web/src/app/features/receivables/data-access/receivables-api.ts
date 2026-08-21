import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';

import { ApiProblemError, toApiProblemError } from '../../../platform/api/api-problem';

export type ReceivableStatus = 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';
export type ReceivableSortField =
  | 'DUE_DATE'
  | 'ISSUE_DATE'
  | 'OUTSTANDING_AMOUNT'
  | 'INVOICE_NUMBER';
export type SortDirection = 'ASC' | 'DESC';

export interface ReceivablesQuery {
  readonly businessDate: string | null;
  readonly status: ReceivableStatus | null;
  readonly overdue: boolean | null;
  readonly page: number;
  readonly size: number;
  readonly sort: ReceivableSortField;
  readonly direction: SortDirection;
}

export interface ReceivableMoney {
  readonly amount: number;
  readonly currency: string;
}

export interface ReceivableCustomer {
  readonly id: string;
  readonly projected: boolean;
  readonly partnerNumber: string | null;
  readonly displayName: string | null;
}

export interface Receivable {
  readonly id: string;
  readonly tenantId: string;
  readonly customer: ReceivableCustomer;
  readonly invoiceNumber: string;
  readonly originalAmount: ReceivableMoney;
  readonly paidAmount: ReceivableMoney;
  readonly outstandingAmount: ReceivableMoney;
  readonly issueDate: string;
  readonly dueDate: string;
  readonly businessDate: string;
  readonly status: ReceivableStatus;
  readonly cancelled: boolean;
  readonly overdue: boolean;
}

export interface ReceivablesPage {
  readonly receivables: readonly Receivable[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly businessDate: string;
  readonly hasNext: boolean;
  readonly hasPrevious: boolean;
}

export interface ReceivablesAging {
  readonly currentAmount: number;
  readonly days1To30OverdueAmount: number;
  readonly days31To60OverdueAmount: number;
  readonly days61To90OverdueAmount: number;
  readonly days91PlusOverdueAmount: number;
}

export interface ReceivablesCurrencySummary {
  readonly currency: string;
  readonly invoiceCount: number;
  readonly openCount: number;
  readonly overdueCount: number;
  readonly outstandingAmount: number;
  readonly overdueAmount: number;
  readonly aging: ReceivablesAging;
}

export interface ReceivablesSummary {
  readonly tenantId: string;
  readonly businessDate: string;
  readonly invoiceCount: number;
  readonly openCount: number;
  readonly overdueCount: number;
  readonly currencies: readonly ReceivablesCurrencySummary[];
}

@Injectable({ providedIn: 'root' })
export class ReceivablesApi {
  readonly #http = inject(HttpClient);

  list(tenantId: string, query: ReceivablesQuery): Observable<ReceivablesPage> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', query.sort)
      .set('direction', query.direction);

    if (query.businessDate) params = params.set('businessDate', query.businessDate);
    if (query.status) params = params.set('status', query.status);
    if (query.overdue !== null) params = params.set('overdue', query.overdue);

    return this.#http
      .get<ReceivablesPage>(`${receivablesPath(tenantId)}`, { params })
      .pipe(catchError((error: unknown) => throwError(() => normalize(error))));
  }

  summary(tenantId: string, businessDate: string | null): Observable<ReceivablesSummary> {
    const params = businessDate
      ? new HttpParams().set('businessDate', businessDate)
      : new HttpParams();

    return this.#http
      .get<ReceivablesSummary>(`${receivablesPath(tenantId)}/summary`, { params })
      .pipe(catchError((error: unknown) => throwError(() => normalize(error))));
  }
}

function receivablesPath(tenantId: string): string {
  return `/api/v1/tenants/${encodeURIComponent(tenantId)}/analytics/receivables`;
}

function normalize(error: unknown): ApiProblemError {
  return error instanceof ApiProblemError ? error : toApiProblemError(error);
}
