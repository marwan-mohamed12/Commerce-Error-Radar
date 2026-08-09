export interface StackLine {
  text: string;
  kind: 'at' | 'cause' | 'more' | 'type' | 'other';
  className: string;
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
  if (trimmed.startsWith('Caused by:') || trimmed.startsWith('Suppressed:')) {
    return base(text, 'cause');
  }
  if (trimmed.startsWith('...')) {
    return base(text, 'more');
  }
  const frame = FRAME.exec(text);
  if (frame) {
    const className = frame[2];
    return {
      text,
      kind: 'at',
      className,
      mine: !!customPrefix && className.startsWith(customPrefix),
      hybris: className.startsWith('de.hybris.'),
      framework: isFramework(className),
    };
  }
  if (/^[\w.$]+(?:Exception|Error|Throwable)(?::.*)?$/.test(trimmed)) {
    return base(text, 'type');
  }
  return base(text, 'other');
}

function base(text: string, kind: StackLine['kind']): StackLine {
  return { text, kind, className: '', mine: false, hybris: false, framework: false };
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
