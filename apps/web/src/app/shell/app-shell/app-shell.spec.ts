import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { AUTH_SESSION, AuthSession } from '../../platform/auth/auth-session';
import { CurrentUserTenant, TenantContext } from '../../platform/tenant/tenant-context';
import { AppShell } from './app-shell';

const tenant: CurrentUserTenant = {
  membershipId: '00000000-0000-0000-0000-000000000001',
  tenantId: '00000000-0000-0000-0000-000000000002',
  tenantKey: 'acme',
  displayName: 'Acme AG',
  roles: ['operations-manager'],
};

describe('AppShell', () => {
  it('renders the active tenant and CSRF-protected logout form', async () => {
    const authSession: AuthSession = {
      status: signal<'authenticated'>('authenticated').asReadonly(),
      csrf: signal({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token',
      }).asReadonly(),
      initialize: async () => undefined,
    };

    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([]),
        { provide: AUTH_SESSION, useValue: authSession },
        {
          provide: TenantContext,
          useValue: {
            tenants: signal<readonly CurrentUserTenant[]>([tenant]).asReadonly(),
            activeTenant: signal<CurrentUserTenant | null>(tenant).asReadonly(),
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(AppShell);
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    const main = element.querySelector('main');
    const select = element.querySelector<HTMLSelectElement>('#tenant-switcher');
    const logoutForm = element.querySelector<HTMLFormElement>('form[action="/logout"]');
    const csrfInput = logoutForm?.querySelector<HTMLInputElement>('input[name="_csrf"]');

    expect(element.querySelector('.skip-link')?.getAttribute('href')).toBe('#main-content');
    expect(main?.getAttribute('data-route-focus')).not.toBeNull();
    expect(select?.value).toBe('acme');
    expect(select?.options[0]?.textContent).toBe('Acme AG');
    expect(logoutForm?.method).toContain('post');
    expect(csrfInput?.value).toBe('csrf-token');
  });
});
