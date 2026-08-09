import { Component, computed, input, output, signal } from '@angular/core';
import { IssueDetail } from './models';
import { StackBlock, collapseFramework, parseStack } from './stack';
import { clockTime, relativeTime } from './time';

@Component({
  selector: 'app-issue-detail',
  templateUrl: './issue-detail.html',
  host: { class: 'block min-h-full' },
})
export class IssueDetailView {
  readonly detail = input<IssueDetail | null>(null);
  readonly prefix = input('');
  readonly canGoBack = input(false);
  readonly mute = output<{ fingerprint: string; muted: boolean }>();
  readonly back = output<void>();

  readonly copied = signal(false);
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

  readonly ids = computed(() => {
    const map = this.event()?.businessIds ?? this.detail()?.issue.lastBusinessIds ?? {};
    return Object.entries(map);
  });

  rel(iso: string): string {
    return relativeTime(iso);
  }

  clock(iso: string): string {
    return clockTime(iso);
  }

  kindLabel(kind: string): string {
    return (kind || 'OTHER').replaceAll('_', ' ');
  }

  toggleGroup(index: number): void {
    this.openGroups.update((state) => ({ ...state, [index]: !state[index] }));
  }

  isOpen(index: number): boolean {
    return !!this.openGroups()[index];
  }

  async copyStack(): Promise<void> {
    const text = this.event()?.rawText;
    if (!text) {
      return;
    }
    await navigator.clipboard.writeText(text);
    this.copied.set(true);
    setTimeout(() => this.copied.set(false), 1600);
  }

  toggleMute(): void {
    const issue = this.detail()?.issue;
    if (!issue) {
      return;
    }
    this.mute.emit({ fingerprint: issue.fingerprint, muted: !issue.muted });
  }
}
