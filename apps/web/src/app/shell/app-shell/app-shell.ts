import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AUTH_SESSION } from '../../platform/auth/auth-session';
import { TenantContext } from '../../platform/tenant/tenant-context';

@Component({
  selector: 'eoc-app-shell',
  imports: [RouterLink],
  templateUrl: './app-shell.html',
  styleUrls: ['./app-shell.css', './app-shell-auth.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {
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
}
