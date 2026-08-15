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
  logKind?: string;
  logPath?: string;
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
  logKind: string;
  pinned: boolean;
  startedAt: string | null;
  lastLineAt: string | null;
  live: boolean;
  mode: string;
  linesRead: number;
  eventsPersisted: number;
  lastLine: string;
  message: string;
  customPackagePrefix: string;
  sources: ActiveSource[];
  activeRunIds: number[];
}

export interface ActiveSource {
  runId: number;
  kind: string;
  path: string;
  fileName: string;
}

export interface Filters {
  level: 'ALL' | 'ERROR' | 'WARN';
  kind: string;
  logKind: string;
  q: string;
  bizKey: string;
  bizValue: string;
}

export interface NotifySettings {
  enabled: boolean;
  tabHidden: boolean;
  windowsToast: boolean;
}

export interface NotifyPing {
  fingerprint: string;
  level: string;
  kind: string;
  title: string;
  message: string;
  count: number;
}

export interface RunSession {
  id: number;
  hybrisHome: string;
  logPath: string;
  logKind: string;
  startedAt: string | null;
  endedAt: string | null;
  mode: string;
  eventCount: number;
  issueCount: number;
  current: boolean;
}

export interface LogSource {
  kind: string;
  path: string;
  fileName: string;
  sizeBytes: number;
  lastModified: string | null;
  current: boolean;
}

export const LOG_SOURCES = [
  { id: 'ALL', label: 'All' },
  { id: 'CONSOLE', label: 'console' },
  { id: 'WRAPPER', label: 'wrapper.log' },
  { id: 'ANT', label: 'ant.log' },
  { id: 'CATALINA', label: 'catalina' },
  { id: 'LOCALHOST', label: 'localhost' },
] as const;

export const ISSUE_KINDS = [
  'ALL',
  'OCC',
  'CRONJOB',
  'IMPEX',
  'FLEXIBLE_SEARCH',
  'SOLR',
  'INTERCEPTOR',
  'MODEL_SAVE',
  'INITIALIZE',
  'UPDATE',
  'ANT',
  'TOMCAT',
  'OTHER',
] as const;
