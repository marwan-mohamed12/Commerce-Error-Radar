import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { IssueDetailView } from './issue-detail';
import { IssueList } from './issue-list';
import { kindLabel } from './kind';
import { ISSUE_KINDS } from './models';
import { RadarService } from './radar.service';
import { ThemeService } from './theme';
import { fileName, sessionStamp } from './time';

@Component({
  selector: 'app-root',
  imports: [IssueList, IssueDetailView],
  templateUrl: './app.html',
  host: {
    class: 'block h-full min-h-0 min-w-0',
    '(document:keydown)': 'onChromeKey($event)',
  },
})
export class App implements OnInit, OnDestroy {
  readonly radar = inject(RadarService);
  readonly theme = inject(ThemeService);
  readonly kinds = ISSUE_KINDS;
  readonly search = signal('');
  readonly openPath = signal('');
  readonly replay = signal(true);
  readonly showOpen = signal(false);
  readonly showHistory = signal(false);
  readonly pane = signal<'list' | 'detail'>('list');
  readonly railOpen = signal(true);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    if (localStorage.getItem('radar-rail') === 'closed') {
      this.railOpen.set(false);
    }
  }

  ngOnInit(): void {
    void this.radar.bootstrap().catch(() => undefined);
  }

  ngOnDestroy(): void {
    this.radar.disconnect();
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }

  setRail(open: boolean): void {
    this.railOpen.set(open);
    localStorage.setItem('radar-rail', open ? 'open' : 'closed');
  }

  onChromeKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (target?.closest('input, textarea, select, [contenteditable="true"]')) {
      return;
    }
    if (event.key !== '[' && event.key !== ']') {
      return;
    }
    event.preventDefault();
    this.setRail(event.key === ']');
  }

  onSearch(value: string): void {
    this.search.set(value);
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => this.radar.setFilter('q', value), 200);
  }

  onKind(value: string): void {
    this.radar.setFilter('kind', value);
  }

  logName(): string {
    return fileName(this.radar.status()?.logPath);
  }

  fileName(path: string | null | undefined): string {
    return fileName(path);
  }

  sessionLabel(): string {
    if (this.radar.viewingHistory()) {
      const run = this.radar.sessions().find((session) => session.id === this.radar.viewRunId());
      return run ? `History · ${sessionStamp(run.startedAt)}` : 'History';
    }
    return 'This session';
  }

  sessionStamp(iso: string | null | undefined): string {
    return sessionStamp(iso);
  }

  pickSession(runId: number): void {
    this.radar.viewSession(runId);
    this.showHistory.set(false);
    this.pane.set('list');
  }

  backToLive(): void {
    this.radar.viewCurrentSession();
    this.showHistory.set(false);
    this.pane.set('list');
  }

  async openSelected(): Promise<void> {
    const path = this.openPath().trim();
    if (!path) {
      return;
    }
    await this.radar.openLog(path, this.replay());
    this.showOpen.set(false);
  }

  async pickIssue(fingerprint: string): Promise<void> {
    await this.radar.select(fingerprint);
    this.pane.set('detail');
  }

  chipLabel(kind: string): string {
    if (kind === 'ALL') {
      return 'All';
    }
    return kindLabel(kind);
  }
}
