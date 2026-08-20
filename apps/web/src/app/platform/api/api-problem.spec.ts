import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { toApiProblemError } from './api-problem';

describe('toApiProblemError', () => {
  it('preserves stable metadata without treating backend detail as display copy', () => {
    const normalized = toApiProblemError(new HttpErrorResponse({
      status: 404,
      error: {
        type: 'urn:eoc:problem:platform-user-not-found',
        title: 'Platform user not found',
        code: 'PLATFORM_USER_NOT_FOUND',
        detail: 'Sensitive backend diagnostic text',
      },
    }));

    expect(normalized.message).toBe('Platform user not found');
    expect(normalized.problem.code).toBe('PLATFORM_USER_NOT_FOUND');
    expect(normalized.problem.detail).toBe('Sensitive backend diagnostic text');
    expect(normalized.message).not.toContain('Sensitive backend diagnostic text');
  });
});
