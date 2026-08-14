export interface BusinessFilter {
  key: string;
  value: string;
}

const LABELS: Record<string, string> = {
  order: 'Order',
  product: 'Product',
  user: 'User',
  cronjob: 'CronJob',
  impex: 'ImpEx',
  catalogVersion: 'Catalog',
};

/** Kind token so chips reuse the existing palette. */
const TONES: Record<string, string> = {
  order: 'OCC',
  product: 'MODEL_SAVE',
  user: 'OTHER',
  cronjob: 'CRONJOB',
  impex: 'IMPEX',
  catalogVersion: 'FLEXIBLE_SEARCH',
};

export function bizLabel(key: string): string {
  if (!key) {
    return '';
  }
  return LABELS[key] ?? key.replaceAll('_', ' ');
}

export function bizTone(key: string): string {
  return TONES[key] ?? 'OTHER';
}

export function bizEntries(ids: Record<string, string> | null | undefined): [string, string][] {
  if (!ids) {
    return [];
  }
  return Object.entries(ids).filter(([, value]) => !!value);
}

export function bizFilterTitle(key: string, value: string): string {
  const label = bizLabel(key);
  if (key === 'impex') {
    return `Everything for this ImpEx file (${value})`;
  }
  if (key === 'order') {
    return `Everything for this order (${value})`;
  }
  return `Everything for this ${label.toLowerCase()} (${value})`;
}
