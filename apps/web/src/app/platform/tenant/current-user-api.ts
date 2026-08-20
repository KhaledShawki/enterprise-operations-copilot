import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';

import { ApiProblemError, toApiProblemError } from '../api/api-problem';

export interface CurrentUser {
  readonly issuer: string;
  readonly subject: string;
  readonly roles: readonly string[];
}

export interface CurrentUserTenant {
  readonly membershipId: string;
  readonly tenantId: string;
  readonly tenantKey: string;
  readonly displayName: string;
  readonly roles: readonly string[];
}

interface CurrentUserTenantsResponse {
  readonly tenants: readonly CurrentUserTenant[];
}

@Injectable({ providedIn: 'root' })
export class CurrentUserApi {
  readonly #http = inject(HttpClient);

  getCurrentUser(): Observable<CurrentUser> {
    return this.#http
      .get<CurrentUser>('/api/v1/me')
      .pipe(catchError((error: unknown) => throwError(() => normalize(error))));
  }

  getAccessibleTenants(): Observable<readonly CurrentUserTenant[]> {
    return this.#http.get<CurrentUserTenantsResponse>('/api/v1/me/tenants').pipe(
      map((response) => [...response.tenants]),
      catchError((error: unknown) => throwError(() => normalize(error))),
    );
  }
}

function normalize(error: unknown): ApiProblemError {
  return error instanceof ApiProblemError ? error : toApiProblemError(error);
}
