export interface BusinessIds {
  [key: string]: string;
}

export interface Issue {
  fingerprint: string;
  title: string;
  level: 'ERROR' | 'WARN' | 'FATAL' | string;
  kind: string;
  count: number;
  firstSeen: string;
  lastSeen: string;
  hasCustomFrame: boolean;
  muted: boolean;
  lastMessage: string;
  lastBusinessIds: BusinessIds;
}

export interface EventItem {
  id: number;
  runId: number;
  ts: string;
  level: string;
  logger: string;
  thread: string;
  message: string;
  exception: string;
  fingerprint: string;
  rawText: string;
  contextText: string;
  kind: string;
  hasCustomFrame: boolean;
  businessIds: BusinessIds;
}

export interface IssueDetail {
  issue: Issue;
  events: EventItem[];
}

export interface RunStatus {
  id: number;
  hybrisHome: string;
  logPath: string;
  startedAt: string | null;
  lastLineAt: string | null;
  live: boolean;
  mode: string;
  linesRead: number;
  eventsPersisted: number;
  lastLine: string;
  message: string;
  customPackagePrefix: string;
}

export interface Filters {
  level: 'ALL' | 'ERROR' | 'WARN';
  kind: string;
  q: string;
  mineOnly: boolean;
}

export const ISSUE_KINDS = [
  'ALL',
  'OCC',
  'CRONJOB',
  'IMPEX',
  'FLEXIBLE_SEARCH',
  'SOLR',
  'INTERCEPTOR',
  'MODEL_SAVE',
  'OTHER',
] as const;
