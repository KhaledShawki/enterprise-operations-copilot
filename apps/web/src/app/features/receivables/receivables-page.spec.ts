import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ApiProblemError } from '../../platform/api/api-problem';
import { CurrentUserTenant, TenantContext } from '../../platform/tenant/tenant-context';
import { ReceivablesApi, ReceivablesPage as ReceivablesPageResult, ReceivablesSummary } from './data-access/receivables-api';
import { ReceivablesPage } from './receivables-page';

const tenant: CurrentUserTenant = {
  membershipId: '00000000-0000-0000-0000-000000000001',
  tenantId: '00000000-0000-0000-0000-000000000071',
  tenantKey: 'acme',
  displayName: 'Acme AG',
  roles: ['operations-manager'],
};

const summary: ReceivablesSummary = {
  tenantId: tenant.tenantId,
  businessDate: '2026-08-20',
  invoiceCount: 3,
  openCount: 2,
  overdueCount: 1,
  currencies: [
    {
      currency: 'CHF',
      invoiceCount: 3,
      openCount: 2,
      overdueCount: 1,
      outstandingAmount: 1250,
      overdueAmount: 250,
      aging: {
        currentAmount: 1000,
        days1To30OverdueAmount: 250,
        days31To60OverdueAmount: 0,
        days61To90OverdueAmount: 0,
        days91PlusOverdueAmount: 0,
      },
    },
  ],
};

const page: ReceivablesPageResult = {
  receivables: [
    {
      id: '00000000-0000-0000-0000-000000000072',
      tenantId: tenant.tenantId,
      customer: {
        id: '00000000-0000-0000-0000-000000000073',
        projected: false,
        partnerNumber: 'C-100',
        displayName: 'Acme Customer',
      },
      invoiceNumber: 'INV-1001',
      originalAmount: { amount: 500, currency: 'CHF' },
      paidAmount: { amount: 250, currency: 'CHF' },
      outstandingAmount: { amount: 250, currency: 'CHF' },
      issueDate: '2026-07-01',
      dueDate: '2026-08-01',
      businessDate: '2026-08-20',
      status: 'PARTIALLY_PAID',
      cancelled: false,
      overdue: true,
    },
  ],
  page: 0,
  size: 25,
  totalElements: 1,
  totalPages: 1,
  businessDate: '2026-08-20',
  hasNext: false,
  hasPrevious: false,
};

describe('ReceivablesPage', () => {
  it('renders summary/list data and derives the request from URL state', async () => {
    const navigate = vi.fn().mockResolvedValue(true);
    const api = {
      summary: vi.fn().mockReturnValue(of(summary)),
      list: vi.fn().mockReturnValue(of(page)),
    };

    await TestBed.configureTestingModule({
      imports: [ReceivablesPage],
      providers: [
        { provide: ReceivablesApi, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap({ overdue: 'true' })) },
        },
        { provide: Router, useValue: { navigate } },
        {
          provide: TenantContext,
          useValue: { activeTenant: signal<CurrentUserTenant | null>(tenant).asReadonly() },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ReceivablesPage);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Receivables');
    expect(element.textContent).toContain('Acme Customer');
    expect(element.textContent).toContain('INV-1001');
    expect(api.summary).toHaveBeenCalledWith(tenant.tenantId, null);
    expect(api.list).toHaveBeenCalledWith(
      tenant.tenantId,
      expect.objectContaining({ overdue: true, page: 0, size: 25 }),
    );

    const status = element.querySelector<HTMLSelectElement>('#receivable-status');
    expect(status).not.toBeNull();
    if (!status) return;
    status.value = 'OPEN';
    status.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ status: 'OPEN', overdue: true }),
      }),
    );
  });

  it('keeps successful summary data visible when the list is unavailable', async () => {
    const api = {
      summary: vi.fn().mockReturnValue(of(summary)),
      list: vi
        .fn()
        .mockReturnValue(
          throwError(() => new ApiProblemError({ title: 'Receivables unavailable', status: 503 })),
        ),
    };

    await TestBed.configureTestingModule({
      imports: [ReceivablesPage],
      providers: [
        { provide: ReceivablesApi, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap({})) },
        },
        { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } },
        {
          provide: TenantContext,
          useValue: { activeTenant: signal<CurrentUserTenant | null>(tenant).asReadonly() },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ReceivablesPage);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Operational summary');
    expect(text).toContain('Receivables list unavailable');
  });
});
