import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AUTH_SESSION } from '../../platform/auth/auth-session';

type AccessState = 'no-tenant-access' | 'tenant-unavailable' | 'session-unavailable';

const CONTENT = {
  'no-tenant-access': { eyebrow: 'Tenant access', title: 'No tenant access', description: 'Your account is authenticated, but no active EOC tenant membership is available.', canReturn: false, canRetry: false },
  'tenant-unavailable': { eyebrow: 'Tenant access', title: 'Tenant unavailable', description: 'The requested tenant is not available to this account. EOC did not substitute another tenant.', canReturn: true, canRetry: false },
  'session-unavailable': { eyebrow: 'Session state', title: 'Workspace unavailable', description: 'EOC could not establish the authenticated workspace. Retry the session.', canReturn: false, canRetry: true },
} as const;

@Component({
  selector: 'eoc-access-state-page',
  imports: [RouterLink],
  templateUrl: './access-state-page.html',
  styleUrl: './access-state-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessStatePage {
  readonly #route = inject(ActivatedRoute);
  readonly authSession = inject(AUTH_SESSION);
  readonly content = CONTENT[this.#route.snapshot.data['accessState'] as AccessState];

  retry(): void { globalThis.location.reload(); }
}
