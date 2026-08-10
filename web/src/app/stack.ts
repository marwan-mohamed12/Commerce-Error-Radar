export interface StackLine {
  text: string;
  kind: 'at' | 'cause' | 'more' | 'type' | 'other' | 'header';
  className: string;
  method: string;
  file: string;
  lineNo: string;
  packageName: string;
  simpleClass: string;
  exception: string;
  detail: string;
  level: string;
  lead: string;
  mine: boolean;
  hybris: boolean;
  framework: boolean;
}

export interface StackBlock {
  type: 'line' | 'collapsed';
  line?: StackLine;
  hidden?: StackLine[];
}

const FRAME = /^\s*at\s+(?:([\w.]+)\/)?([\w.$]+)\.([\w$<>-]+)\(([^:)]+)?(?::(\d+))?\)\s*$/;
const CAUSE = /^(Caused by|Suppressed):\s+([\w.$]+)(?::\s*(.*))?$/;
const TYPE = /^([\w.$]+(?:Exception|Error|Throwable))(?::\s*(.*))?$/;
const HEADER = /^(ERROR|WARN|FATAL|INFO|DEBUG)\s+(.*)$/;

export function parseStack(raw: string, customPrefix: string): StackLine[] {
  if (!raw) {
    return [];
  }
  return raw.split(/\r?\n/).map((text) => classify(text, customPrefix));
}

export function collapseFramework(lines: StackLine[]): StackBlock[] {
  const blocks: StackBlock[] = [];
  let buffer: StackLine[] = [];
  const flush = () => {
    if (buffer.length === 0) {
      return;
    }
    if (buffer.length === 1) {
      blocks.push({ type: 'line', line: buffer[0] });
    } else {
      blocks.push({ type: 'collapsed', hidden: buffer });
    }
    buffer = [];
  };
  for (const line of lines) {
    const hide = (line.hybris || line.framework) && line.kind === 'at';
    if (hide) {
      buffer.push(line);
    } else {
      flush();
      blocks.push({ type: 'line', line });
    }
  }
  flush();
  return blocks;
}

function classify(text: string, customPrefix: string): StackLine {
  const trimmed = text.trim();
  const cause = CAUSE.exec(trimmed);
  if (cause) {
    return {
      ...base(text, 'cause'),
      lead: cause[1],
      exception: cause[2],
      detail: cause[3] ?? '',
    };
  }
  if (trimmed.startsWith('...')) {
    return base(text, 'more');
  }
  const frame = FRAME.exec(text);
  if (frame) {
    const className = frame[2];
    const split = splitClass(className);
    return {
      ...base(text, 'at'),
      className,
      method: frame[3] ?? '',
      file: frame[4] ?? '',
      lineNo: frame[5] ?? '',
      packageName: split.packageName,
      simpleClass: split.simpleClass,
      mine: !!customPrefix && className.startsWith(customPrefix),
      hybris: className.startsWith('de.hybris.'),
      framework: isFramework(className),
    };
  }
  const typed = TYPE.exec(trimmed);
  if (typed) {
    return {
      ...base(text, 'type'),
      exception: typed[1],
      detail: typed[2] ?? '',
    };
  }
  const header = HEADER.exec(trimmed);
  if (header) {
    return {
      ...base(text, 'header'),
      level: header[1],
      detail: header[2],
    };
  }
  return base(text, 'other');
}

function splitClass(className: string): { packageName: string; simpleClass: string } {
  const dot = className.lastIndexOf('.');
  if (dot < 0) {
    return { packageName: '', simpleClass: className };
  }
  return { packageName: className.slice(0, dot), simpleClass: className.slice(dot + 1) };
}

export function shortPackage(pkg: string, wide = false): string {
  if (!pkg || wide) {
    return pkg;
  }
  const parts = pkg.split('.');
  if (parts.length <= 4) {
    return pkg;
  }
  return `${parts.slice(0, 2).join('.')}.…${parts[parts.length - 1]}`;
}

function base(text: string, kind: StackLine['kind']): StackLine {
  return {
    text,
    kind,
    className: '',
    method: '',
    file: '',
    lineNo: '',
    packageName: '',
    simpleClass: '',
    exception: '',
    detail: '',
    level: '',
    lead: '',
    mine: false,
    hybris: false,
    framework: false,
  };
}

function isFramework(className: string): boolean {
  return [
    'org.springframework.',
    'org.apache.catalina.',
    'org.apache.tomcat.',
    'org.apache.coyote.',
    'java.',
    'javax.',
    'jdk.',
    'sun.',
    'jakarta.servlet.',
  ].some((p) => className.startsWith(p));
}
