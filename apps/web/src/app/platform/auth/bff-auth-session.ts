import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AuthSession, AuthSessionStatus, CsrfToken } from './auth-session';
import { CsrfTokenStore } from './csrf-token-store';

interface BffSessionResponse {
  readonly authenticated: boolean;
  readonly csrf: CsrfToken;
}

@Injectable()
export class BffAuthSession implements AuthSession {
  readonly #http = inject(HttpClient);
  readonly #csrfStore = inject(CsrfTokenStore);
  readonly #status = signal<AuthSessionStatus>('initializing');

  readonly status = this.#status.asReadonly();
  readonly csrf = this.#csrfStore.token;

  async initialize(): Promise<void> {
    this.#status.set('initializing');

    try {
      const session = await firstValueFrom(this.#http.get<BffSessionResponse>('/bff/session'));
      this.#csrfStore.set(session.csrf);
      this.#status.set(session.authenticated ? 'authenticated' : 'unauthenticated');
    } catch {
      this.#csrfStore.clear();
      this.#status.set('failed');
    }
  }
}
