import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AUTH_SESSION } from '../../platform/auth/auth-session';
import { TenantContext } from '../../platform/tenant/tenant-context';

@Component({
  selector: 'eoc-app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app-shell.html',
  styleUrls: ['./app-shell.css', './app-shell-auth.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {
  readonly #document = inject(DOCUMENT);
  readonly #router = inject(Router);
  readonly #authSession = inject(AUTH_SESSION);
  readonly #tenantContext = inject(TenantContext);

  readonly tenants = this.#tenantContext.tenants;
  readonly activeTenant = this.#tenantContext.activeTenant;
  readonly csrf = this.#authSession.csrf;

  onTenantChange(event: Event): void {
    const tenantKey = (event.target as HTMLSelectElement).value;

    if (tenantKey && tenantKey !== this.activeTenant()?.tenantKey) {
      void this.#router.navigate(['/t', tenantKey]);
    }
  }

  focusMainContent(): void {
    queueMicrotask(() => this.#document.querySelector<HTMLElement>('#main-content')?.focus());
  }
}
