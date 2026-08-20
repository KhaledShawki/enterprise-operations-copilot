import { InjectionToken, Signal } from '@angular/core';

export type AuthSessionStatus = 'initializing' | 'authenticated' | 'unauthenticated' | 'failed';

export interface CsrfToken {
  readonly headerName: string;
  readonly parameterName: string;
  readonly token: string;
}

export interface AuthSession {
  readonly status: Signal<AuthSessionStatus>;
  readonly csrf: Signal<CsrfToken | null>;

  initialize(): Promise<void>;
}

export const AUTH_SESSION = new InjectionToken<AuthSession>('AUTH_SESSION');
