import { Component, OnDestroy, inject, output, signal } from '@angular/core';
import { ISSUE_KINDS } from '../../../../core/models/radar.models';
import { RadarService } from '../../../../core/services/radar.service';
import { ThemeService } from '../../../../core/services/theme.service';
import { bizTone } from '../../../../core/utils/biz';
import { kindLabel } from '../../../../core/utils/kind';
import { fileName, sessionStamp } from '../../../../core/utils/time';

@Component({
  selector: 'app-inbox-chrome',
  templateUrl: './inbox-chrome.html',
  host: { class: 'block min-w-0 shrink-0 bg-canvas-2' },
})
export class InboxChrome implements OnDestroy {
  readonly radar = inject(RadarService);
  readonly theme = inject(ThemeService);
  readonly kinds = ISSUE_KINDS;
  readonly search = signal('');
  readonly openPath = signal('');
  readonly replay = signal(true);
  readonly showOpen = signal(false);
  readonly showHistory = signal(false);
  readonly sessionChanged = output<void>();

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnDestroy(): void {
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
    this.sessionChanged.emit();
  }

  backToLive(): void {
    this.radar.viewCurrentSession();
    this.showHistory.set(false);
    this.sessionChanged.emit();
  }

  async openSelected(): Promise<void> {
    const path = this.openPath().trim();
    if (!path) {
      return;
    }
    await this.radar.openLog(path, this.replay());
    this.showOpen.set(false);
    this.sessionChanged.emit();
  }

  chipLabel(kind: string): string {
    if (kind === 'ALL') {
      return 'All';
    }
    return kindLabel(kind);
  }

  bizTone(): string {
    return bizTone(this.radar.filters().bizKey);
  }
}
