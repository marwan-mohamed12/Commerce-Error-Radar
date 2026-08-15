import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { EventItem, Filters, Issue, IssueDetail, LogSource, NotifyPing, RunSession, RunStatus } from '../models/radar.models';
import { bizFilterTitle, bizLabel } from '../utils/biz';
import { NotifyService } from './notify.service';

@Injectable({ providedIn: 'root' })
export class RadarService {
  readonly issues = signal<Issue[]>([]);
  readonly selectedFingerprint = signal<string | null>(null);
  readonly detail = signal<IssueDetail | null>(null);
  readonly status = signal<RunStatus | null>(null);
  readonly filters = signal<Filters>({
    level: 'ALL',
    kind: 'ALL',
    logKind: 'ALL',
    q: '',
    bizKey: '',
    bizValue: '',
  });
  readonly businessFilterTitle = computed(() => {
    const f = this.filters();
    if (!f.bizKey || !f.bizValue) {
      return '';
    }
    return bizFilterTitle(f.bizKey, f.bizValue);
  });
  readonly businessFilterChip = computed(() => {
    const f = this.filters();
    if (!f.bizKey || !f.bizValue) {
      return '';
    }
    return `${bizLabel(f.bizKey)} ${f.bizValue}`;
  });
  readonly connected = signal(false);
  readonly flashFingerprint = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly sessions = signal<RunSession[]>([]);
  readonly sources = signal<LogSource[]>([]);
  readonly viewRunId = signal<number | null>(null);

  readonly viewingHistory = computed(() => {
    const view = this.viewRunId();
    if (view == null) {
      return false;
    }
    const active = this.status()?.activeRunIds ?? [];
    if (active.includes(view)) {
      return false;
    }
    const current = this.status()?.id ?? 0;
    return current > 0 && view !== current;
  });

  readonly selected = computed(() => {
    const fp = this.selectedFingerprint();
    return this.issues().find((i) => i.fingerprint === fp) ?? this.detail()?.issue ?? null;
  });

  private source: EventSource | null = null;
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly http: HttpClient,
    private readonly notify: NotifyService,
  ) {}

  async bootstrap(): Promise<void> {
    await Promise.all([this.refreshStatus(), this.refreshSessions(), this.refreshIssues(), this.notify.load()]);
    this.connect();
  }

  activeRunId(): number {
    return this.viewRunId() ?? this.status()?.id ?? 0;
  }

  async refreshStatus(): Promise<void> {
    try {
      const status = await firstValueFrom(this.http.get<RunStatus>('/api/runs/current'));
      this.status.set(status);
    } catch (err) {
      this.error.set('Collector is not reachable on :8088');
      throw err;
    }
  }

  async refreshIssues(): Promise<void> {
    const f = this.filters();
    let params = new HttpParams();
    if (f.level !== 'ALL') {
      params = params.set('level', f.level);
    }
    if (f.kind !== 'ALL') {
      params = params.set('kind', f.kind);
    }
    if (f.q.trim()) {
      params = params.set('q', f.q.trim());
    }
    if (f.bizKey && f.bizValue) {
      params = params.set('bizKey', f.bizKey).set('bizValue', f.bizValue);
    }
    if (this.viewingHistory()) {
      params = params.set('runId', String(this.viewRunId()));
    } else {
      params = params.set('logKind', f.logKind || 'ALL');
    }
    const issues = await firstValueFrom(this.http.get<Issue[]>('/api/issues', { params }));
    this.issues.set(issues);
    const current = this.selectedFingerprint();
    if (current && !issues.some((i) => i.fingerprint === current)) {
      // keep detail if it still exists server-side
    } else if (!current && issues.length > 0) {
      await this.select(issues[0].fingerprint);
    }
  }

  async select(fingerprint: string): Promise<void> {
    this.selectedFingerprint.set(fingerprint);
    let params = new HttpParams().set('fingerprint', fingerprint);
    if (this.viewingHistory()) {
      params = params.set('runId', String(this.viewRunId()));
    } else {
      params = params.set('logKind', this.filters().logKind || 'ALL');
    }
    const detail = await firstValueFrom(this.http.get<IssueDetail>('/api/issues/one', { params }));
    this.detail.set(detail);
  }

  async refreshSessions(): Promise<void> {
    try {
      const sessions = await firstValueFrom(this.http.get<RunSession[]>('/api/runs'));
      this.sessions.set(sessions);
    } catch {
      /* status refresh already reports collector down */
    }
  }

  async refreshSources(): Promise<void> {
    try {
      const sources = await firstValueFrom(this.http.get<LogSource[]>('/api/runs/sources'));
      this.sources.set(sources);
    } catch {
      this.sources.set([]);
    }
  }

  viewSession(runId: number): void {
    this.viewRunId.set(runId);
    this.selectedFingerprint.set(null);
    this.detail.set(null);
    void this.refreshIssues();
  }

  viewCurrentSession(): void {
    this.viewRunId.set(null);
    this.selectedFingerprint.set(null);
    this.detail.set(null);
    void this.refreshIssues();
  }

  setFilter<K extends keyof Filters>(key: K, value: Filters[K]): void {
    this.filters.update((f) => ({ ...f, [key]: value }));
    void this.refreshIssues();
  }

  setLogKind(logKind: string): void {
    this.viewRunId.set(null);
    this.filters.update((f) => ({ ...f, logKind }));
    void this.refreshIssues();
  }

  filterByBusinessId(key: string, value: string): void {
    const current = this.filters();
    if (current.bizKey === key && current.bizValue === value) {
      this.clearBusinessFilter();
      return;
    }
    this.filters.update((f) => ({ ...f, bizKey: key, bizValue: value }));
    void this.refreshIssues();
  }

  clearBusinessFilter(): void {
    if (!this.filters().bizValue) {
      return;
    }
    this.filters.update((f) => ({ ...f, bizKey: '', bizValue: '' }));
    void this.refreshIssues();
  }

  async mute(fingerprint: string, muted = true): Promise<void> {
    await firstValueFrom(
      this.http.post(
        '/api/issues/mute',
        { muted },
        { params: new HttpParams().set('fingerprint', fingerprint) },
      ),
    );
    await this.refreshIssues();
    if (this.selectedFingerprint() === fingerprint) {
      await this.select(fingerprint);
    }
  }

  async openLog(path: string, replay: boolean): Promise<void> {
    const status = await firstValueFrom(
      this.http.post<RunStatus>('/api/runs/open', { path, replay }),
    );
    this.status.set(status);
    this.viewRunId.set(null);
    await this.refreshSessions();
    await this.refreshSources();
    await this.refreshIssues();
  }

  async followNewest(): Promise<void> {
    const status = await firstValueFrom(this.http.post<RunStatus>('/api/runs/follow', {}));
    this.status.set(status);
    this.viewRunId.set(null);
    await this.refreshSessions();
    await this.refreshSources();
    await this.refreshIssues();
  }

  connect(): void {
    this.disconnect();
    const source = new EventSource('/api/stream');
    this.source = source;
    source.addEventListener('hello', () => {
      this.connected.set(true);
      this.error.set(null);
    });
    source.addEventListener('issue', (event) => {
      this.connected.set(true);
      let fingerprint: string | null = null;
      try {
        const payload = JSON.parse((event as MessageEvent).data) as {
          issue?: Issue;
          event?: EventItem;
        };
        fingerprint = payload.issue?.fingerprint ?? null;
      } catch {
        fingerprint = null;
      }
      if (!this.viewingHistory()) {
        void this.refreshIssues().then(() => {
          if (fingerprint) {
            this.flash(fingerprint);
          }
        });
      }
      void this.refreshStatus();
      void this.refreshSessions();
    });
    source.addEventListener('notify', (event) => {
      try {
        const ping = JSON.parse((event as MessageEvent).data) as NotifyPing;
        this.notify.onPing(ping);
      } catch {
        /* ignore malformed notify */
      }
    });
    source.addEventListener('status', () => {
      void this.refreshStatus();
      void this.refreshSessions();
    });
    source.onopen = () => {
      this.connected.set(true);
      this.error.set(null);
    };
    source.onerror = () => {
      this.connected.set(false);
    };
  }

  disconnect(): void {
    this.source?.close();
    this.source = null;
    this.connected.set(false);
  }

  private flash(fingerprint: string): void {
    this.flashFingerprint.set(fingerprint);
    if (this.flashTimer) {
      clearTimeout(this.flashTimer);
    }
    this.flashTimer = setTimeout(() => this.flashFingerprint.set(null), 1400);
  }
}
