import { Injectable, signal } from '@angular/core';

import { CsrfToken } from './auth-session';

@Injectable({ providedIn: 'root' })
export class CsrfTokenStore {
  readonly #token = signal<CsrfToken | null>(null);
  readonly token = this.#token.asReadonly();

  set(token: CsrfToken): void {
    this.#token.set(token);
  }

  clear(): void {
    this.#token.set(null);
  }
}
