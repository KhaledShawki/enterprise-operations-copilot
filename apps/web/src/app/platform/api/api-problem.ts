import { HttpErrorResponse } from '@angular/common/http';

export interface ApiProblem {
  readonly type?: string;
  readonly title: string;
  readonly status: number;
  readonly code?: string;
  readonly detail?: string;
}

export class ApiProblemError extends Error {
  override readonly name = 'ApiProblemError';

  constructor(readonly problem: ApiProblem) {
    super(problem.title);
  }
}

export function toApiProblemError(error: unknown): ApiProblemError {
  if (!(error instanceof HttpErrorResponse)) {
    return new ApiProblemError({ title: 'Request failed', status: 0 });
  }

  const body = isRecord(error.error) ? error.error : {};
  return new ApiProblemError({
    type: stringValue(body['type']),
    title: stringValue(body['title']) ?? statusTitle(error.status),
    status: error.status,
    code: stringValue(body['code']),
    detail: stringValue(body['detail']),
  });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

function statusTitle(status: number): string {
  if (status === 401) return 'Authentication required';
  if (status === 403) return 'Access denied';
  if (status === 503 || status === 502) return 'Service unavailable';
  return 'Request failed';
}
