import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RadarService } from '../../core/services/radar.service';
import { BusinessFilter } from '../../core/utils/biz';
import { InboxChrome } from './components/inbox-chrome/inbox-chrome';
import { IssueDetailView } from './components/issue-detail/issue-detail';
import { IssueList } from './components/issue-list/issue-list';

@Component({
  selector: 'app-inbox',
  imports: [InboxChrome, IssueList, IssueDetailView],
  templateUrl: './inbox.html',
  host: {
    class: 'block h-full min-h-0 min-w-0',
    '(document:keydown)': 'onChromeKey($event)',
  },
})
export class Inbox implements OnInit, OnDestroy {
  readonly radar = inject(RadarService);
  readonly pane = signal<'list' | 'detail'>('list');
  readonly railOpen = signal(true);

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

  onSessionChanged(): void {
    this.pane.set('list');
  }

  async pickIssue(fingerprint: string): Promise<void> {
    await this.radar.select(fingerprint);
    this.pane.set('detail');
  }

  filterBiz(id: BusinessFilter): void {
    this.radar.filterByBusinessId(id.key, id.value);
    this.pane.set('list');
  }
}
