import { IssueDetail } from '../models/radar.models';
import { bizLabel } from './biz';
import { displayTitle, kindLabel } from './kind';
import { clockTime } from './time';

function idsOf(detail: IssueDetail): [string, string][] {
  const event = detail.events[0];
  const map = event?.businessIds ?? detail.issue.lastBusinessIds ?? {};
  return Object.entries(map).filter(([, value]) => !!value);
}

function headline(detail: IssueDetail): string {
  const issue = detail.issue;
  return displayTitle(issue.title, issue.kind);
}

/** GitHub / Confluence / notes. */
export function issueMarkdown(detail: IssueDetail): string {
  const issue = detail.issue;
  const event = detail.events[0];
  const lines: string[] = [
    `# ${issue.level} · ${kindLabel(issue.kind)} · ×${issue.count}`,
    '',
    `**${headline(detail)}**`,
    '',
  ];
  const message = event?.message || issue.lastMessage;
  if (message && message !== issue.title) {
    lines.push(message, '');
  }
  lines.push(`- Last seen: ${clockTime(issue.lastSeen) || issue.lastSeen}`);
  if (event?.logger) {
    lines.push(`- Logger: \`${event.logger}\``);
  }
  if (event?.thread) {
    lines.push(`- Thread: \`${event.thread}\``);
  }
  for (const [key, value] of idsOf(detail)) {
    lines.push(`- ${bizLabel(key)}: \`${value}\``);
  }
  const stack = event?.rawText?.trim();
  if (stack) {
    lines.push('', '## Stack', '', '```', stack, '```');
  }
  const context = event?.contextText?.trim();
  if (context) {
    lines.push('', '## Preceding lines', '', '```', context, '```');
  }
  lines.push('');
  return lines.join('\n');
}

export interface TeamsCopy {
  html: string;
  plain: string;
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/**
 * Teams does not render pasted Markdown. It turns HTML on the clipboard
 * into compose-box rich text, which then Send displays correctly.
 */
export function issueTeams(detail: IssueDetail): TeamsCopy {
  const issue = detail.issue;
  const event = detail.events[0];
  const title = headline(detail);
  const ids = idsOf(detail);
  const seen = clockTime(issue.lastSeen) || issue.lastSeen || '';
  const logger = event?.logger ?? '';
  const stack = event?.rawText?.trim() ?? '';

  const headerPlain = `${issue.level} · ${kindLabel(issue.kind)} · ×${issue.count}`;
  const idsPlain = ids.map(([key, value]) => `${bizLabel(key)}: ${value}`).join(' · ');
  const metaPlain = [seen ? `Last seen ${seen}` : '', logger].filter(Boolean).join(' · ');

  const plain = [
    headerPlain,
    title,
    idsPlain,
    metaPlain,
    stack ? `\n${stack}` : '',
  ]
    .filter(Boolean)
    .join('\n');

  const rows: string[] = [
    `<div><b>${escapeHtml(issue.level)}</b> · ${escapeHtml(kindLabel(issue.kind))} · ×${issue.count}</div>`,
    `<div>${escapeHtml(title)}</div>`,
  ];
  if (ids.length) {
    rows.push(
      `<div>${ids
        .map(([key, value]) => `<b>${escapeHtml(bizLabel(key))}</b> ${escapeHtml(value)}`)
        .join(' · ')}</div>`,
    );
  }
  if (metaPlain) {
    rows.push(`<div>${escapeHtml(metaPlain)}</div>`);
  }
  if (stack) {
    rows.push(
      `<pre style="font-family:Consolas,'Courier New',monospace;white-space:pre-wrap;font-size:12px;margin:8px 0 0">${escapeHtml(stack)}</pre>`,
    );
  }

  const html = `<html><body><!--StartFragment--><div>${rows.join('')}</div><!--EndFragment--></body></html>`;
  return { html, plain };
}

/** Write HTML + plain so Teams / Outlook paste as rich text. */
export async function copyHtml(html: string, plain: string): Promise<void> {
  const htmlBlob = new Blob([html], { type: 'text/html' });
  const textBlob = new Blob([plain], { type: 'text/plain' });
  if (typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
    try {
      await navigator.clipboard.write([
        new ClipboardItem({
          'text/html': htmlBlob,
          'text/plain': textBlob,
        }),
      ]);
      return;
    } catch {
      try {
        await navigator.clipboard.write([
          new ClipboardItem({
            'text/html': Promise.resolve(htmlBlob),
            'text/plain': Promise.resolve(textBlob),
          }),
        ]);
        return;
      } catch {
        /* fall through to plain text */
      }
    }
  }
  await navigator.clipboard.writeText(plain);
}
