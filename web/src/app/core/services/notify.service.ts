import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { NotifyPing, NotifySettings } from '../models/radar.models';

const BASE_TITLE = 'Commerce Error Radar';
const BASE_ICON = 'favicon-32.png';
const TOAST_ICON = 'favicon-48.png';
const TOAST_TAG = 'radar-error';
const TOAST_GAP_MS = 8000;

interface IconSpec {
  rel: string;
  href: string;
  type?: string;
  sizes?: string;
}

const DEFAULT_ICONS: IconSpec[] = [
  { rel: 'icon', href: 'favicon.svg?v=2', type: 'image/svg+xml' },
  { rel: 'icon', href: 'favicon-32.png?v=2', type: 'image/png', sizes: '32x32' },
  { rel: 'icon', href: 'favicon-16.png?v=2', type: 'image/png', sizes: '16x16' },
  { rel: 'shortcut icon', href: 'favicon.ico?v=2' },
];

/**
 * Tab-side half of ERROR notify: favicon badge + presence.
 * The collector decides what is notifiable, persists the toggle, and fires
 * the Windows toast while this window reports it is unfocused or hidden.
 */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  readonly enabled = signal(false);
  readonly windowsToast = signal(false);
  readonly permission = signal<NotificationPermission>(this.readPermission());
  readonly hint = computed(() => {
    if (!this.enabled()) {
      return 'Notify on ERROR when Radar is unfocused or in another tab';
    }
    if (this.windowsToast()) {
      return 'ERROR notifications on — Windows toast when this window is unfocused';
    }
    if (this.canBrowserToast()) {
      return 'ERROR notifications on — browser toast when this window is unfocused';
    }
    return 'Tab badge on — allow notifications or use a Windows toast from the collector';
  });

  private unseen = 0;
  private lastToastAt = 0;
  private toast: Notification | null = null;
  private drawGen = 0;
  private baseImage: HTMLImageElement | null = null;
  private baseImageFailed = false;
  private presenceBound = false;

  constructor(private readonly http: HttpClient) {}

  async load(): Promise<void> {
    try {
      const settings = await firstValueFrom(this.http.get<NotifySettings>('/api/notify'));
      this.applySettings(settings);
      if (this.enabled()) {
        await this.requestPermission();
      }
    } catch {
      /* collector down — radar.service already surfaces that */
    }
    this.bindPresence();
    await this.reportPresence();
  }

  async toggle(): Promise<void> {
    const next = !this.enabled();
    if (next) {
      await this.requestPermission();
    }
    try {
      const settings = await firstValueFrom(
        this.http.post<NotifySettings>('/api/notify', { enabled: next }),
      );
      this.applySettings(settings);
    } catch {
      this.enabled.set(next);
    }
    if (!next) {
      this.clearUnseen();
    }
    await this.reportPresence();
  }

  onPing(ping: NotifyPing): void {
    if (!this.enabled() || !this.tabHidden()) {
      return;
    }
    this.unseen += 1;
    this.applyBadge(this.unseen);
    // Windows toast is the primary channel, but Show() can succeed and still
    // never pop (unregistered AUMID). Browser toast is the safety net.
    this.showBrowserToast(ping);
  }

  private applySettings(settings: NotifySettings): void {
    this.enabled.set(settings.enabled);
    this.windowsToast.set(settings.windowsToast);
  }

  private bindPresence(): void {
    if (this.presenceBound) {
      return;
    }
    this.presenceBound = true;
    document.addEventListener('visibilitychange', () => {
      if (!this.tabHidden()) {
        this.clearUnseen();
      }
      void this.reportPresence();
    });
    window.addEventListener('focus', () => {
      this.clearUnseen();
      void this.reportPresence(false);
    });
    window.addEventListener('blur', () => {
      void this.reportPresence(true);
    });
    window.addEventListener('pagehide', () => {
      void this.reportPresence(true);
    });
  }

  private async reportPresence(hidden = this.tabHidden()): Promise<void> {
    try {
      await firstValueFrom(this.http.post<NotifySettings>('/api/notify/presence', { hidden }));
    } catch {
      /* collector down */
    }
  }

  private canBrowserToast(): boolean {
    return typeof Notification !== 'undefined' && this.permission() === 'granted';
  }

  private async requestPermission(): Promise<void> {
    if (typeof Notification === 'undefined') {
      this.permission.set('denied');
      return;
    }
    let perm = Notification.permission;
    if (perm === 'default') {
      try {
        perm = await Notification.requestPermission();
      } catch {
        perm = Notification.permission;
      }
    }
    this.permission.set(perm);
  }

  private readPermission(): NotificationPermission {
    if (typeof Notification === 'undefined') {
      return 'denied';
    }
    return Notification.permission;
  }

  /**
   * Hidden to the developer — not only `visibilityState === 'hidden'`.
   * Alt-tabbing to the Hybris console leaves the Radar tab "visible",
   * and that used to suppress every Windows toast.
   */
  private tabHidden(): boolean {
    return document.visibilityState === 'hidden' || !document.hasFocus();
  }

  private showBrowserToast(ping: NotifyPing): void {
    if (!this.canBrowserToast()) {
      return;
    }
    const now = Date.now();
    if (now - this.lastToastAt < TOAST_GAP_MS && this.toast) {
      return;
    }
    this.lastToastAt = now;
    this.toast?.close();
    const count = this.unseen;
    const title = count > 1 ? `Radar · ${count} new ${ping.level}s` : `Radar · ${ping.level} · ${ping.kind}`;
    const body = count > 1 ? `Latest: ${ping.message}` : ping.message;
    const icon = new URL(TOAST_ICON, document.baseURI).href;
    try {
      const toast = new Notification(title, {
        body,
        icon,
        badge: icon,
        tag: TOAST_TAG,
      });
      toast.onclick = () => {
        window.focus();
        toast.close();
        this.clearUnseen();
      };
      toast.onclose = () => {
        if (this.toast === toast) {
          this.toast = null;
        }
      };
      this.toast = toast;
    } catch {
      /* permission flipped */
    }
  }

  private clearUnseen(): void {
    if (this.unseen === 0 && !this.toast) {
      return;
    }
    this.unseen = 0;
    this.toast?.close();
    this.toast = null;
    this.applyBadge(0);
  }

  private applyBadge(count: number): void {
    document.title = count > 0 ? `(${count}) ${BASE_TITLE}` : BASE_TITLE;
    this.setAppBadge(count);
    if (count <= 0) {
      this.drawGen += 1;
      this.replaceIcons(DEFAULT_ICONS);
      return;
    }
    void this.paintFavicon(count);
  }

  private async paintFavicon(count: number): Promise<void> {
    const gen = ++this.drawGen;
    const img = await this.loadBaseIcon();
    if (gen !== this.drawGen || !img) {
      return;
    }
    const href = drawBadge(img, count);
    if (gen !== this.drawGen) {
      return;
    }
    this.replaceIcons([{ rel: 'icon', href, type: 'image/png' }]);
  }

  private loadBaseIcon(): Promise<HTMLImageElement | null> {
    if (this.baseImage) {
      return Promise.resolve(this.baseImage);
    }
    if (this.baseImageFailed) {
      return Promise.resolve(null);
    }
    return new Promise((resolve) => {
      const img = new Image();
      img.onload = () => {
        this.baseImage = img;
        resolve(img);
      };
      img.onerror = () => {
        this.baseImageFailed = true;
        resolve(null);
      };
      img.src = BASE_ICON;
    });
  }

  private replaceIcons(specs: IconSpec[]): void {
    document.querySelectorAll('link[rel="icon"], link[rel="shortcut icon"]').forEach((el) => el.remove());
    for (const spec of specs) {
      const link = document.createElement('link');
      link.rel = spec.rel;
      link.href = spec.href;
      if (spec.type) {
        link.type = spec.type;
      }
      if (spec.sizes) {
        link.setAttribute('sizes', spec.sizes);
      }
      document.head.appendChild(link);
    }
  }

  private setAppBadge(count: number): void {
    const nav = navigator as Navigator & {
      setAppBadge?: (n?: number) => Promise<void>;
      clearAppBadge?: () => Promise<void>;
    };
    if (count > 0) {
      void nav.setAppBadge?.(count);
    } else {
      void nav.clearAppBadge?.();
    }
  }
}

function drawBadge(base: HTMLImageElement, count: number): string {
  const size = 32;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return BASE_ICON;
  }
  ctx.drawImage(base, 0, 0, size, size);
  const label = count > 99 ? '99+' : String(count);
  const wide = label.length > 1;
  const cx = wide ? 22 : 24;
  const cy = 8;
  const r = wide ? 8 : 7;
  ctx.beginPath();
  if (wide) {
    ctx.roundRect(cx - 10, cy - r, 20, r * 2, r);
  } else {
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
  }
  ctx.fillStyle = '#e07058';
  ctx.fill();
  ctx.lineWidth = 2;
  ctx.strokeStyle = '#0e0e0e';
  ctx.stroke();
  ctx.fillStyle = '#080808';
  ctx.font = `700 ${label.length > 2 ? 8 : 11}px "JetBrains Mono", Consolas, monospace`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(label, cx, cy + 0.6);
  return canvas.toDataURL('image/png');
}
