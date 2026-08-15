export const KIND_ORDER = [
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

export function kindKey(kind: string): string {
  const value = (kind || 'OTHER').toUpperCase();
  return KIND_ORDER.includes(value as (typeof KIND_ORDER)[number]) ? value : 'OTHER';
}

export function kindLabel(kind: string): string {
  switch (kindKey(kind)) {
    case 'OCC':
      return 'OCC';
    case 'CRONJOB':
      return 'CronJob';
    case 'IMPEX':
      return 'ImpEx';
    case 'FLEXIBLE_SEARCH':
      return 'FlexibleSearch';
    case 'SOLR':
      return 'Solr';
    case 'INTERCEPTOR':
      return 'Interceptor';
    case 'MODEL_SAVE':
      return 'Model save';
    case 'INITIALIZE':
      return 'Initialize';
    case 'UPDATE':
      return 'Update';
    case 'ANT':
      return 'Ant';
    case 'TOMCAT':
      return 'Tomcat';
    default:
      return (kind || 'OTHER').replaceAll('_', ' ');
  }
}

export function logKindLabel(kind: string | null | undefined): string {
  switch ((kind || '').toUpperCase()) {
    case 'ALL':
      return 'All';
    case 'CONSOLE':
      return 'console';
    case 'CATALINA':
      return 'catalina';
    case 'WRAPPER':
      return 'wrapper.log';
    case 'ANT':
      return 'ant.log';
    case 'LOCALHOST':
      return 'localhost';
    default:
      return kind ? kind.replaceAll('_', ' ') : 'Log';
  }
}

/** Drop a repeated kind word: "OCC OCCConsentLayerFilter…" → "OCCConsentLayerFilter…". */
export function displayTitle(title: string, kind: string): string {
  if (!title) {
    return title;
  }
  const tokens = [kindKey(kind), kindLabel(kind)].filter((token, index, all) => token && all.indexOf(token) === index);
  let result = title;
  for (const token of tokens) {
    const escaped = token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    result = result.replace(new RegExp(`^(${escaped})\\s+(${escaped})(?=[A-Z._\\s-])`, 'i'), '$2');
  }
  return result;
}
