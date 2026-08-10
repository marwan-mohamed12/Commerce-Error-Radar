import { Component, computed, input, output } from '@angular/core';
import { Issue } from './models';
import { clockTime, relativeTime } from './time';

export interface IssueGroup {
  kind: string;
  issues: Issue[];
}

const KIND_ORDER = [
  'OCC',
  'CRONJOB',
  'IMPEX',
  'FLEXIBLE_SEARCH',
  'SOLR',
  'INTERCEPTOR',
  'MODEL_SAVE',
  'OTHER',
];

@Component({
  selector: 'app-issue-list',
  templateUrl: './issue-list.html',
  host: {
    class: 'block h-full min-w-0',
    '(document:keydown)': 'onKey($event)',
  },
})
export class IssueList {
  readonly issues = input<Issue[]>([]);
  readonly selected = input<string | null>(null);
  readonly flash = input<string | null>(null);
  readonly pick = output<string>();

  readonly groups = computed<IssueGroup[]>(() => {
    const buckets = new Map<string, Issue[]>();
    for (const issue of this.issues()) {
      const kind = issue.kind || 'OTHER';
      const list = buckets.get(kind);
      if (list) {
        list.push(issue);
      } else {
        buckets.set(kind, [issue]);
      }
    }
    const known = KIND_ORDER.filter((kind) => buckets.has(kind)).map((kind) => ({
      kind,
      issues: buckets.get(kind)!,
    }));
    const extra = [...buckets.keys()]
      .filter((kind) => !KIND_ORDER.includes(kind))
      .sort()
      .map((kind) => ({ kind, issues: buckets.get(kind)! }));
    return [...known, ...extra];
  });

  readonly showHeads = computed(() => this.groups().length > 1);

  readonly flat = computed(() => this.groups().flatMap((group) => group.issues));

  rel(iso: string): string {
    return relativeTime(iso);
  }

  clock(iso: string): string {
    return clockTime(iso);
  }

  stamp(level: string): string {
    if (level === 'WARN') {
      return 'WRN';
    }
    if (level === 'FATAL') {
      return 'FTL';
    }
    return 'ERR';
  }

  kindLabel(kind: string): string {
    if (kind === 'FLEXIBLE_SEARCH') {
      return 'FlexibleSearch';
    }
    if (kind === 'MODEL_SAVE') {
      return 'Model save';
    }
    if (kind === 'CRONJOB') {
      return 'CronJob';
    }
    if (kind === 'IMPEX') {
      return 'ImpEx';
    }
    return (kind || 'OTHER').replaceAll('_', ' ');
  }

  onKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (target?.closest('input, textarea, select, [contenteditable="true"]')) {
      return;
    }
    const items = this.flat();
    if (items.length === 0) {
      return;
    }
    const down = event.key === 'j' || event.key === 'ArrowDown';
    const up = event.key === 'k' || event.key === 'ArrowUp';
    if (!down && !up) {
      return;
    }
    event.preventDefault();
    const current = this.selected();
    let index = items.findIndex((issue) => issue.fingerprint === current);
    if (index < 0) {
      index = down ? 0 : items.length - 1;
    } else {
      index = down ? Math.min(items.length - 1, index + 1) : Math.max(0, index - 1);
    }
    const next = items[index];
    if (next && next.fingerprint !== current) {
      this.pick.emit(next.fingerprint);
      queueMicrotask(() => {
        document
          .querySelector<HTMLElement>(`[data-fp="${CSS.escape(next.fingerprint)}"]`)
          ?.scrollIntoView({ block: 'nearest' });
      });
    }
  }
}
