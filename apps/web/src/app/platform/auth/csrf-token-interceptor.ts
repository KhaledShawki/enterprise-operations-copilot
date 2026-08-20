import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { CsrfTokenStore } from './csrf-token-store';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

export const csrfTokenInterceptor: HttpInterceptorFn = (request, next) => {
  if (SAFE_METHODS.has(request.method.toUpperCase()) || !isSameOriginBffRequest(request.url)) {
    return next(request);
  }

  const csrf = inject(CsrfTokenStore).token();
  if (!csrf) {
    return next(request);
  }

  return next(request.clone({ setHeaders: { [csrf.headerName]: csrf.token } }));
};

function isSameOriginBffRequest(url: string): boolean {
  return url === '/api' || url.startsWith('/api/') || url === '/bff' || url.startsWith('/bff/');
}
