import { Component, OnDestroy, inject, output, signal } from '@angular/core';
import { ISSUE_KINDS, LOG_SOURCES } from '../../../../core/models/radar.models';
import { NotifyService } from '../../../../core/services/notify.service';
import { RadarService } from '../../../../core/services/radar.service';
import { ThemeService } from '../../../../core/services/theme.service';
import { bizTone } from '../../../../core/utils/biz';
import { kindLabel, logKindLabel } from '../../../../core/utils/kind';
import { fileName, fileSize, relativeTime, sessionStamp } from '../../../../core/utils/time';

@Component({
  selector: 'app-inbox-chrome',
  templateUrl: './inbox-chrome.html',
  host: { class: 'block min-w-0 shrink-0 bg-canvas-2' },
})
export class InboxChrome implements OnDestroy {
  readonly radar = inject(RadarService);
  readonly theme = inject(ThemeService);
  readonly notify = inject(NotifyService);
  readonly kinds = ISSUE_KINDS;
  readonly logSources = LOG_SOURCES;
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

  onLogKind(value: string): void {
    this.radar.setLogKind(value);
  }

  logName(): string {
    const kind = this.radar.filters().logKind;
    if (!kind || kind === 'ALL') {
      const count = this.radar.status()?.sources?.length ?? 0;
      return count > 1 ? `All logs · ${count}` : fileName(this.radar.status()?.logPath);
    }
    const source = this.radar.status()?.sources?.find((item) => item.kind === kind);
    return source?.fileName || logKindLabel(kind);
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

  async toggleOpen(): Promise<void> {
    const next = !this.showOpen();
    this.showOpen.set(next);
    this.showHistory.set(false);
    if (next) {
      await this.radar.refreshSources();
    }
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

  async openSource(path: string, replay: boolean): Promise<void> {
    await this.radar.openLog(path, replay);
    this.showOpen.set(false);
    this.sessionChanged.emit();
  }

  async followNewest(): Promise<void> {
    await this.radar.followNewest();
    this.showOpen.set(false);
    this.sessionChanged.emit();
  }

  selectedLogLabel(): string {
    return logKindLabel(this.radar.filters().logKind);
  }

  sourceKind(kind: string): string {
    return logKindLabel(kind);
  }

  sourceSize(bytes: number): string {
    return fileSize(bytes);
  }

  sourceAge(iso: string | null): string {
    return relativeTime(iso);
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
