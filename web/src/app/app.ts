import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { IssueDetailView } from './issue-detail';
import { IssueList } from './issue-list';
import { ISSUE_KINDS } from './models';
import { RadarService } from './radar.service';
import { ThemeService } from './theme';
import { fileName } from './time';

@Component({
  selector: 'app-root',
  imports: [IssueList, IssueDetailView],
  templateUrl: './app.html',
})
export class App implements OnInit, OnDestroy {
  readonly radar = inject(RadarService);
  readonly theme = inject(ThemeService);
  readonly kinds = ISSUE_KINDS;
  readonly search = signal('');
  readonly openPath = signal('');
  readonly replay = signal(true);
  readonly showOpen = signal(false);
  readonly pane = signal<'list' | 'detail'>('list');
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    void this.radar.bootstrap().catch(() => undefined);
  }

  ngOnDestroy(): void {
    this.radar.disconnect();
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
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
      return 'All types';
    }
    if (kind === 'FLEXIBLE_SEARCH') {
      return 'FlexibleSearch';
    }
    if (kind === 'MODEL_SAVE') {
      return 'Model save';
    }
    return kind.replaceAll('_', ' ');
  }
}
