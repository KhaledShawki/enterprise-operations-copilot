import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, forkJoin } from 'rxjs';

import { ApiProblemError } from '../api/api-problem';
import { CurrentUser, CurrentUserApi, CurrentUserTenant } from './current-user-api';

export type TenantContextStatus = 'idle' | 'ready' | 'no-access' | 'failed';

@Injectable({ providedIn: 'root' })
export class TenantContext {
  readonly #api = inject(CurrentUserApi);
  readonly #status = signal<TenantContextStatus>('idle');
  readonly #currentUser = signal<CurrentUser | null>(null);
  readonly #tenants = signal<readonly CurrentUserTenant[]>([]);
  readonly #activeTenantKey = signal<string | null>(null);
  readonly #problem = signal<ApiProblemError | null>(null);

  readonly status = this.#status.asReadonly();
  readonly currentUser = this.#currentUser.asReadonly();
  readonly tenants = this.#tenants.asReadonly();
  readonly problem = this.#problem.asReadonly();
  readonly activeTenant = computed(() => {
    const key = this.#activeTenantKey();
    return this.#tenants().find((tenant) => tenant.tenantKey === key) ?? null;
  });

  async initialize(): Promise<void> {
    try {
      const result = await firstValueFrom(forkJoin({
        currentUser: this.#api.getCurrentUser(),
        tenants: this.#api.getAccessibleTenants(),
      }));
      const tenants = [...result.tenants].sort((left, right) =>
        left.tenantKey.localeCompare(right.tenantKey),
      );
      this.#currentUser.set(result.currentUser);
      this.#tenants.set(tenants);
      this.#problem.set(null);
      this.#status.set(tenants.length > 0 ? 'ready' : 'no-access');
    } catch (error) {
      this.#currentUser.set(null);
      this.#tenants.set([]);
      this.#activeTenantKey.set(null);
      this.#problem.set(error instanceof ApiProblemError ? error : null);
      this.#status.set('failed');
    }
  }

  activateFromRoute(tenantKey: string): boolean {
    const tenant = this.#tenants().find((candidate) => candidate.tenantKey === tenantKey);
    if (!tenant) {
      this.#activeTenantKey.set(null);
      return false;
    }
    this.#activeTenantKey.set(tenant.tenantKey);
    return true;
  }

  firstAccessibleTenant(): CurrentUserTenant | null {
    return this.#tenants()[0] ?? null;
  }
}

export type { CurrentUser, CurrentUserTenant } from './current-user-api';
