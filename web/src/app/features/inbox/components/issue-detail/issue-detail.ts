import { Component, computed, effect, input, output, signal } from '@angular/core';
import { IssueDetail } from '../../../../core/models/radar.models';
import { BusinessFilter, bizEntries, bizFilterTitle, bizLabel, bizTone } from '../../../../core/utils/biz';
import { displayTitle, kindKey, kindLabel as labelForKind, logKindLabel } from '../../../../core/utils/kind';
import { copyHtml, issueMarkdown, issueTeams } from '../../../../core/utils/share';
import { StackBlock, collapseFramework, parseStack, shortPackage } from '../../../../core/utils/stack';
import { clockTime, relativeTime } from '../../../../core/utils/time';

@Component({
  selector: 'app-issue-detail',
  templateUrl: './issue-detail.html',
  host: { class: 'block min-h-full min-w-0' },
})
export class IssueDetailView {
  readonly detail = input<IssueDetail | null>(null);
  readonly prefix = input('');
  readonly canGoBack = input(false);
  readonly wide = input(false);
  readonly mute = output<{ fingerprint: string; muted: boolean }>();
  readonly back = output<void>();
  readonly biz = output<BusinessFilter>();
  readonly activeBizKey = input('');
  readonly activeBizValue = input('');

  readonly copied = signal<'stack' | 'md' | 'teams' | null>(null);
  readonly showContext = signal(false);
  readonly openGroups = signal<Record<number, boolean>>({});

  readonly event = computed(() => this.detail()?.events[0] ?? null);

  readonly blocks = computed<StackBlock[]>(() => {
    const event = this.event();
    if (!event) {
      return [];
    }
    return collapseFramework(parseStack(event.rawText, this.prefix()));
  });

  readonly foldIndexes = computed(() =>
    this.blocks().flatMap((block, index) => (block.hidden?.length ? [index] : [])),
  );

  readonly canFold = computed(() => this.foldIndexes().length > 0);

  readonly allExpanded = computed(() => {
    const folds = this.foldIndexes();
    if (folds.length === 0) {
      return false;
    }
    const open = this.openGroups();
    return folds.every((index) => open[index]);
  });

  readonly allCollapsed = computed(() => {
    const folds = this.foldIndexes();
    if (folds.length === 0) {
      return true;
    }
    const open = this.openGroups();
    return folds.every((index) => !open[index]);
  });

  constructor() {
    effect(() => {
      this.detail()?.issue.fingerprint;
      this.openGroups.set({});
      this.showContext.set(false);
    });
  }

  readonly ids = computed(() => {
    const map = this.event()?.businessIds ?? this.detail()?.issue.lastBusinessIds ?? {};
    return bizEntries(map);
  });

  rel(iso: string): string {
    return relativeTime(iso);
  }

  clock(iso: string): string {
    return clockTime(iso);
  }

  pkg(packageName: string): string {
    return shortPackage(packageName, this.wide());
  }

  kindLabel(kind: string): string {
    return labelForKind(kind);
  }

  sourceLabel(kind: string): string {
    return logKindLabel(kind);
  }

  kindTone(kind: string): string {
    return kindKey(kind);
  }

  titleOf(title: string, kind: string): string {
    return displayTitle(title, kind);
  }

  toggleGroup(index: number): void {
    this.openGroups.update((state) => ({ ...state, [index]: !state[index] }));
  }

  isOpen(index: number): boolean {
    return !!this.openGroups()[index];
  }

  expandAll(): void {
    const next: Record<number, boolean> = {};
    for (const index of this.foldIndexes()) {
      next[index] = true;
    }
    this.openGroups.set(next);
  }

  collapseAll(): void {
    this.openGroups.set({});
  }

  idLabel(key: string): string {
    return bizLabel(key);
  }

  idTone(key: string): string {
    return bizTone(key);
  }

  idTitle(key: string, value: string): string {
    return bizFilterTitle(key, value);
  }

  isActiveId(key: string, value: string): boolean {
    return this.activeBizKey() === key && this.activeBizValue() === value;
  }

  pickId(key: string, value: string): void {
    this.biz.emit({ key, value });
  }

  async copyStack(): Promise<void> {
    const text = this.event()?.rawText;
    if (!text) {
      return;
    }
    await this.copyText(text, 'stack');
  }

  async copyMarkdown(): Promise<void> {
    const detail = this.detail();
    if (!detail) {
      return;
    }
    await this.copyText(issueMarkdown(detail), 'md');
  }

  async copyTeams(): Promise<void> {
    const detail = this.detail();
    if (!detail) {
      return;
    }
    const payload = issueTeams(detail);
    await copyHtml(payload.html, payload.plain);
    this.copied.set('teams');
    setTimeout(() => {
      if (this.copied() === 'teams') {
        this.copied.set(null);
      }
    }, 1600);
  }

  private async copyText(text: string, kind: 'stack' | 'md' | 'teams'): Promise<void> {
    await navigator.clipboard.writeText(text);
    this.copied.set(kind);
    setTimeout(() => {
      if (this.copied() === kind) {
        this.copied.set(null);
      }
    }, 1600);
  }

  toggleMute(): void {
    const issue = this.detail()?.issue;
    if (!issue) {
      return;
    }
    this.mute.emit({ fingerprint: issue.fingerprint, muted: !issue.muted });
  }
}
