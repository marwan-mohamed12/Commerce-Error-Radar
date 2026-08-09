import { Component, input, output } from '@angular/core';
import { Issue } from './models';
import { relativeTime } from './time';

@Component({
  selector: 'app-issue-list',
  templateUrl: './issue-list.html',
  styleUrl: './issue-list.scss',
})
export class IssueList {
  readonly issues = input<Issue[]>([]);
  readonly selected = input<string | null>(null);
  readonly flash = input<string | null>(null);
  readonly pick = output<string>();

  rel(iso: string): string {
    return relativeTime(iso);
  }

  kindLabel(kind: string): string {
    return (kind || 'OTHER').replaceAll('_', ' ');
  }
}
