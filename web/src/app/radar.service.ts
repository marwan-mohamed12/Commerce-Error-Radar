import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Filters, Issue, IssueDetail, RunStatus } from './models';

@Injectable({ providedIn: 'root' })
export class RadarService {
  readonly issues = signal<Issue[]>([]);
  readonly selectedFingerprint = signal<string | null>(null);
  readonly detail = signal<IssueDetail | null>(null);
  readonly status = signal<RunStatus | null>(null);
  readonly filters = signal<Filters>({
    level: 'ALL',
    kind: 'ALL',
    q: '',
    mineOnly: false,
  });
  readonly connected = signal(false);
  readonly flashFingerprint = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  readonly selected = computed(() => {
    const fp = this.selectedFingerprint();
    return this.issues().find((i) => i.fingerprint === fp) ?? this.detail()?.issue ?? null;
  });

  private source: EventSource | null = null;
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly http: HttpClient) {}

  async bootstrap(): Promise<void> {
    await Promise.all([this.refreshStatus(), this.refreshIssues()]);
    this.connect();
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
    if (f.mineOnly) {
      params = params.set('mineOnly', 'true');
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
    const detail = await firstValueFrom(
      this.http.get<IssueDetail>('/api/issues/one', {
        params: new HttpParams().set('fingerprint', fingerprint),
      }),
    );
    this.detail.set(detail);
  }

  setFilter<K extends keyof Filters>(key: K, value: Filters[K]): void {
    this.filters.update((f) => ({ ...f, [key]: value }));
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
      try {
        const payload = JSON.parse((event as MessageEvent).data) as {
          issue?: Issue;
        };
        if (payload.issue) {
          this.upsertIssue(payload.issue);
        }
      } catch {
        /* ignore malformed frames */
      }
      void this.refreshStatus();
    });
    source.addEventListener('status', () => {
      void this.refreshStatus();
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

  private upsertIssue(incoming: Issue): void {
    const f = this.filters();
    if (f.level !== 'ALL' && incoming.level !== f.level) {
      return;
    }
    if (f.kind !== 'ALL' && incoming.kind !== f.kind) {
      return;
    }
    if (f.mineOnly && !incoming.hasCustomFrame) {
      return;
    }
    if (f.q.trim()) {
      const q = f.q.trim().toLowerCase();
      const blob = `${incoming.title} ${incoming.lastMessage} ${incoming.fingerprint}`.toLowerCase();
      if (!blob.includes(q)) {
        return;
      }
    }
    this.issues.update((list) => {
      const rest = list.filter((i) => i.fingerprint !== incoming.fingerprint);
      return [incoming, ...rest];
    });
    this.flash(incoming.fingerprint);
    if (this.selectedFingerprint() === incoming.fingerprint) {
      void this.select(incoming.fingerprint);
    } else if (!this.selectedFingerprint()) {
      void this.select(incoming.fingerprint);
    }
  }

  private flash(fingerprint: string): void {
    this.flashFingerprint.set(fingerprint);
    if (this.flashTimer) {
      clearTimeout(this.flashTimer);
    }
    this.flashTimer = setTimeout(() => this.flashFingerprint.set(null), 1400);
  }
}
