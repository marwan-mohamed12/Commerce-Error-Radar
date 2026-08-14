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

/**
 * Compact markdown that pastes cleanly into Microsoft Teams
 * (bold, inline code, fenced stack — no tables or ATX headers).
 */
export function issueTeams(detail: IssueDetail): string {
  const issue = detail.issue;
  const event = detail.events[0];
  const bits: string[] = [
    `**${issue.level}** · ${kindLabel(issue.kind)} · ×${issue.count}`,
    headline(detail),
  ];
  const ids = idsOf(detail);
  if (ids.length) {
    bits.push(ids.map(([key, value]) => `**${bizLabel(key)}** \`${value}\``).join(' · '));
  }
  const meta: string[] = [];
  if (issue.lastSeen) {
    meta.push(`Last seen ${clockTime(issue.lastSeen) || issue.lastSeen}`);
  }
  if (event?.logger) {
    meta.push(`\`${event.logger}\``);
  }
  if (meta.length) {
    bits.push(meta.join(' · '));
  }
  const stack = event?.rawText?.trim();
  if (stack) {
    bits.push('', '```', stack, '```');
  }
  return bits.join('\n');
}
