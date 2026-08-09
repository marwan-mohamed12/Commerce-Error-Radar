import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IssueDetailView } from './issue-detail';
import { IssueList } from './issue-list';
import { ISSUE_KINDS } from './models';
import { RadarService } from './radar.service';
import { fileName } from './time';

@Component({
  selector: 'app-root',
  imports: [FormsModule, IssueList, IssueDetailView],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit, OnDestroy {
  readonly radar = inject(RadarService);
  readonly kinds = ISSUE_KINDS;
  readonly search = signal('');
  readonly openPath = signal('');
  readonly replay = signal(true);
  readonly showOpen = signal(false);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private clock = signal(Date.now());
  private clockId: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    void this.radar.bootstrap().catch(() => undefined);
    this.clockId = setInterval(() => this.clock.set(Date.now()), 15000);
  }

  ngOnDestroy(): void {
    this.radar.disconnect();
    if (this.clockId) {
      clearInterval(this.clockId);
    }
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
}
