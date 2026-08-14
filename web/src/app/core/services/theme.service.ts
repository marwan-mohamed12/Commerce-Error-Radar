import { Injectable, signal } from '@angular/core';

export type RadarTheme = 'dark' | 'light';

const STORAGE_KEY = 'radar-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<RadarTheme>('dark');

  constructor() {
    this.apply(this.readInitial());
  }

  toggle(): void {
    this.apply(this.theme() === 'dark' ? 'light' : 'dark');
  }

  apply(theme: RadarTheme): void {
    this.theme.set(theme);
    const root = document.documentElement;
    root.dataset['theme'] = theme;
    root.style.colorScheme = theme;
    localStorage.setItem(STORAGE_KEY, theme);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#080808' : '#e8edf2');
  }

  private readInitial(): RadarTheme {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') {
      return stored;
    }
    return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  }
}
