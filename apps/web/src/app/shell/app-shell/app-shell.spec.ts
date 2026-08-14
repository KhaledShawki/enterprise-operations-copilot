import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { AppShell } from './app-shell';

describe('AppShell', () => {
  it('renders an accessible operational shell', async () => {
    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(AppShell);
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    const main = element.querySelector('main');

    expect(element.querySelector('.skip-link')?.getAttribute('href')).toBe('#main-content');
    expect(element.querySelector('nav')?.getAttribute('aria-label')).toBe('Workspace');
    expect(main?.id).toBe('main-content');
    expect(main?.getAttribute('tabindex')).toBe('-1');
    expect(element.querySelector('h1')?.textContent?.trim()).toBe('Operational workspace');
  });
});
