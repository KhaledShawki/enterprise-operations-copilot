import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import {
  Observable,
  Subject,
  catchError,
  combineLatest,
  forkJoin,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';

import { ApiProblemError } from '../../platform/api/api-problem';
import { TenantContext } from '../../platform/tenant/tenant-context';
import {
  Receivable,
  ReceivablesApi,
  ReceivablesPage as ReceivablesPageResult,
  ReceivablesQuery,
  ReceivablesSummary,
  ReceivableSortField,
  ReceivableStatus,
  SortDirection,
} from './data-access/receivables-api';
import {
  DEFAULT_RECEIVABLES_QUERY,
  parseReceivablesQuery,
  toReceivablesQueryParams,
} from './receivables-query';

type ReceivablesViewState = 'loading' | 'ready' | 'empty' | 'partial' | 'stale' | 'failed';

type Outcome<T> =
  | { readonly ok: true; readonly value: T }
  | { readonly ok: false; readonly problem: ApiProblemError };

interface LoadResult {
  readonly summary: Outcome<ReceivablesSummary>;
  readonly receivables: Outcome<ReceivablesPageResult>;
}

@Component({
  selector: 'eoc-receivables-page',
  templateUrl: './receivables-page.html',
  styleUrl: './receivables-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceivablesPage {
  readonly #api = inject(ReceivablesApi);
  readonly #destroyRef = inject(DestroyRef);
  readonly #route = inject(ActivatedRoute);
  readonly #router = inject(Router);
  readonly #tenantContext = inject(TenantContext);
  readonly #reload = new Subject<void>();

  readonly activeTenant = this.#tenantContext.activeTenant;
  readonly #state = signal<ReceivablesViewState>('loading');
  readonly #filters = signal<ReceivablesQuery>({ ...DEFAULT_RECEIVABLES_QUERY });
  readonly #summary = signal<ReceivablesSummary | null>(null);
  readonly #page = signal<ReceivablesPageResult | null>(null);
  readonly #problem = signal<ApiProblemError | null>(null);
  readonly #refreshing = signal(false);

  readonly state = this.#state.asReadonly();
  readonly filters = this.#filters.asReadonly();
  readonly summary = this.#summary.asReadonly();
  readonly page = this.#page.asReadonly();
  readonly problem = this.#problem.asReadonly();
  readonly refreshing = this.#refreshing.asReadonly();
  readonly hasActiveFilters = computed(() => {
    const filters = this.#filters();
    return (
      filters.businessDate !== null || filters.status !== null || filters.overdue !== null
    );
  });

  constructor() {
    combineLatest([
      this.#route.queryParamMap,
      this.#reload.pipe(startWith(undefined)),
    ])
      .pipe(
        map(([params]) => parseReceivablesQuery(params)),
        tap((query) => this.#beginLoad(query)),
        switchMap((query) => this.#load(query)),
        takeUntilDestroyed(this.#destroyRef),
      )
      .subscribe((result) => this.#apply(result));
  }

  retry(): void {
    this.#reload.next();
  }

  clearFilters(): void {
    this.#navigate({ ...DEFAULT_RECEIVABLES_QUERY });
  }

  onBusinessDateChange(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.#navigate({ ...this.#filters(), businessDate: value || null, page: 0 });
  }

  onStatusChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.#navigate({
      ...this.#filters(),
      status: value ? (value as ReceivableStatus) : null,
      page: 0,
    });
  }

  onOverdueChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.#navigate({
      ...this.#filters(),
      overdue: value === 'true' ? true : value === 'false' ? false : null,
      page: 0,
    });
  }

  onSortChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as ReceivableSortField;
    this.#navigate({ ...this.#filters(), sort: value, page: 0 });
  }

  onDirectionChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as SortDirection;
    this.#navigate({ ...this.#filters(), direction: value, page: 0 });
  }

  onPageSizeChange(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    this.#navigate({ ...this.#filters(), size: value, page: 0 });
  }

  previousPage(): void {
    const page = this.#page();
    if (page?.hasPrevious) {
      this.#navigate({ ...this.#filters(), page: Math.max(0, page.page - 1) });
    }
  }

  nextPage(): void {
    const page = this.#page();
    if (page?.hasNext) {
      this.#navigate({ ...this.#filters(), page: page.page + 1 });
    }
  }

  customerLabel(receivable: Receivable): string {
    return (
      receivable.customer.displayName ??
      receivable.customer.partnerNumber ??
      (receivable.customer.projected ? 'Projected customer' : 'Unknown customer')
    );
  }

  statusLabel(status: ReceivableStatus): string {
    if (status === 'PARTIALLY_PAID') return 'Partially paid';
    if (status === 'CANCELLED') return 'Cancelled';
    if (status === 'PAID') return 'Paid';
    return 'Open';
  }

  formatMoney(amount: number, currency: string): string {
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency,
        currencyDisplay: 'code',
      }).format(amount);
    } catch {
      return `${amount.toLocaleString()} ${currency}`;
    }
  }

  #beginLoad(query: ReceivablesQuery): void {
    this.#filters.set(query);
    this.#problem.set(null);
    const hasPreviousData = this.#summary() !== null || this.#page() !== null;
    this.#refreshing.set(hasPreviousData);
    if (!hasPreviousData) this.#state.set('loading');
  }

  #load(query: ReceivablesQuery): Observable<LoadResult> {
    const tenant = this.#tenantContext.activeTenant();
    if (!tenant) {
      const problem = new ApiProblemError({
        title: 'Tenant context unavailable',
        status: 0,
      });
      return of({
        summary: { ok: false, problem },
        receivables: { ok: false, problem },
      });
    }

    return forkJoin({
      summary: outcome(this.#api.summary(tenant.tenantId, query.businessDate)),
      receivables: outcome(this.#api.list(tenant.tenantId, query)),
    });
  }

  #apply(result: LoadResult): void {
    const hadPreviousData = this.#summary() !== null || this.#page() !== null;
    this.#refreshing.set(false);

    if (result.summary.ok && result.receivables.ok) {
      this.#summary.set(result.summary.value);
      this.#page.set(result.receivables.value);
      this.#problem.set(null);
      this.#state.set(result.receivables.value.totalElements === 0 ? 'empty' : 'ready');
      return;
    }

    if (result.summary.ok || result.receivables.ok) {
      this.#summary.set(result.summary.ok ? result.summary.value : null);
      this.#page.set(result.receivables.ok ? result.receivables.value : null);
      this.#problem.set(firstProblem(result));
      this.#state.set('partial');
      return;
    }

    this.#problem.set(firstProblem(result));
    if (hadPreviousData) {
      this.#state.set('stale');
    } else {
      this.#summary.set(null);
      this.#page.set(null);
      this.#state.set('failed');
    }
  }

  #navigate(query: ReceivablesQuery): void {
    void this.#router.navigate([], {
      relativeTo: this.#route,
      queryParams: toReceivablesQueryParams(query),
    });
  }
}

function outcome<T>(source: Observable<T>): Observable<Outcome<T>> {
  return source.pipe(
    map((value) => ({ ok: true, value }) as const),
    catchError((error: unknown) =>
      of({
        ok: false,
        problem:
          error instanceof ApiProblemError
            ? error
            : new ApiProblemError({ title: 'Request failed', status: 0 }),
      } as const),
    ),
  );
}

function firstProblem(result: LoadResult): ApiProblemError {
  if ('problem' in result.summary) return result.summary.problem;
  if ('problem' in result.receivables) return result.receivables.problem;
  return new ApiProblemError({ title: 'Request failed', status: 0 });
}
