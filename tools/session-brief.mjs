/**
 * Session brief: a read-only snapshot of where the project stands, computed
 * from the repository at the moment it runs (never from a stored summary, so
 * it cannot go stale). Run at the start of every agent session:
 *
 *   node tools/session-brief.mjs            # markdown for humans and agents
 *   node tools/session-brief.mjs --json     # machine-readable
 *
 * Sources: work-items/<ID>/run.json (via workflow.mjs recover),
 * docs/security/SECURITY_CHECKLIST.md, docs/engineering/LESSONS.md,
 * last-change dates of durable knowledge files, git branch/status.
 * Writes nothing.
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { recover, graphPath } from './feature-workflow/workflow.mjs';

const DAY_MS = 24 * 60 * 60 * 1000;
export const STALE_RUN_DAYS = 7;
const TERMINAL = new Set(['done', 'cancelled']);

export const KNOWLEDGE_FILES = [
  'AGENTS.md',
  'CLAUDE.md',
  'docs/research/PROJECT_STATE.md',
  'docs/research/KNOWN_ISSUES.md',
  'docs/research/RESEARCH_LOG.md',
  'docs/security/SECURITY_CHECKLIST.md',
  'docs/engineering/LESSONS.md'
];

const readText = file => fs.readFileSync(file, 'utf8').replace(/^﻿/, '');

function lastActivity(run) {
  const times = run.history.map(entry => Date.parse(entry.time)).filter(Number.isFinite);
  if (run.checkpoint?.time) times.push(Date.parse(run.checkpoint.time));
  return times.length ? Math.max(...times) : null;
}

/** Open (non-terminal) work items, oldest activity first. */
export function openWorkItems(root, now = Date.now()) {
  const dir = path.join(root, 'work-items');
  if (!fs.existsSync(dir)) return [];
  let graph = null;
  try { graph = JSON.parse(readText(graphPath)); } catch { graph = null; }
  const items = [];
  for (const id of fs.readdirSync(dir).sort()) {
    const file = path.join(dir, id, 'run.json');
    if (!fs.existsSync(file)) continue;
    let run;
    try { run = JSON.parse(readText(file)); } catch (error) {
      items.push({ id, state: 'UNREADABLE', issues: [`run.json unreadable: ${error.message}`] });
      continue;
    }
    if (TERMINAL.has(run.state)) continue;
    const last = lastActivity(run);
    const ageDays = last === null ? null : Math.floor((now - last) / DAY_MS);
    let report = null;
    if (graph) {
      try { report = recover(run, graph); } catch (error) { report = { issues: [`recover failed: ${error.message}`] }; }
    }
    items.push({
      id,
      state: run.state,
      revision: run.revision,
      lastActivity: last === null ? null : new Date(last).toISOString(),
      ageDays,
      stale: ageDays !== null && ageDays >= STALE_RUN_DAYS,
      waitingForUser: ['await_user_test', 'await_decision'].includes(run.state),
      nextAction: run.checkpoint?.next_action ?? null,
      issues: report?.issues ?? ['graph.json unavailable: recover skipped'],
      changedFiles: report?.changes?.length ?? 0
    });
  }
  return items.sort((a, b) => (b.ageDays ?? -1) - (a.ageDays ?? -1));
}

/** Rows of the findings table whose status does not start with FIXED. */
export function openFindings(checklistText) {
  const rows = [];
  for (const line of checklistText.split(/\r?\n/)) {
    const match = line.match(/^\|\s*(SEC-\d+)\s*\|\s*([^|]+?)\s*\|/);
    if (!match) continue;
    const cells = line.split('|').map(cell => cell.trim());
    const status = cells[cells.length - 2] ?? '';
    if (/^FIXED/i.test(status)) continue;
    rows.push({ id: match[1], severity: match[2], status });
  }
  return rows;
}

/** Lesson headings `## L-YYYY-NNN — title` with Date/Status fields. */
export function lessons(lessonsText) {
  const entries = [];
  let current = null;
  for (const line of lessonsText.split(/\r?\n/)) {
    const heading = line.match(/^##\s+(L-\d{4}-\d{3})\s+[—-]\s+(.+)$/);
    if (heading) {
      current = { id: heading[1], title: heading[2].trim(), date: null, status: 'ACTIVE' };
      entries.push(current);
      continue;
    }
    if (!current) continue;
    const date = line.match(/^Date:\s*(\S+)/);
    if (date) current.date = date[1];
    const status = line.match(/^Status:\s*(.+)$/);
    if (status) current.status = status[1].trim();
  }
  return entries;
}

function git(root, args) {
  try {
    return execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch {
    return null;
  }
}

/** Last commit date per knowledge file (falls back to file mtime). */
export function knowledgeFreshness(root) {
  return KNOWLEDGE_FILES.map(relative => {
    const absolute = path.join(root, relative);
    if (!fs.existsSync(absolute)) return { file: relative, lastChanged: null, source: 'missing' };
    const committed = git(root, ['log', '-1', '--format=%cs', '--', relative]);
    const dirty = git(root, ['status', '--porcelain', '--', relative]);
    if (committed && !dirty) return { file: relative, lastChanged: committed, source: 'git' };
    const mtime = fs.statSync(absolute).mtime.toISOString().slice(0, 10);
    return { file: relative, lastChanged: mtime, source: dirty ? 'uncommitted' : 'mtime' };
  });
}

/** Work items finished after a given ISO date (yyyy-mm-dd). */
function finishedSince(root, isoDate) {
  const dir = path.join(root, 'work-items');
  if (!isoDate || !fs.existsSync(dir)) return [];
  const since = Date.parse(isoDate);
  const result = [];
  for (const id of fs.readdirSync(dir)) {
    const file = path.join(dir, id, 'run.json');
    if (!fs.existsSync(file)) continue;
    try {
      const run = JSON.parse(readText(file));
      const finish = run.history.find(entry => entry.event === 'finish');
      if (finish && Date.parse(finish.time) > since + DAY_MS) result.push(id);
    } catch { /* unreadable runs are reported by openWorkItems */ }
  }
  return result.sort();
}

export function buildBrief(root, now = Date.now()) {
  const checklist = path.join(root, 'docs/security/SECURITY_CHECKLIST.md');
  const lessonsFile = path.join(root, 'docs/engineering/LESSONS.md');
  const freshness = knowledgeFreshness(root).map(entry => ({
    ...entry,
    workItemsFinishedSince: ['docs/research/PROJECT_STATE.md', 'docs/security/SECURITY_CHECKLIST.md',
      'docs/engineering/LESSONS.md'].includes(entry.file) ? finishedSince(root, entry.lastChanged) : []
  }));
  return {
    generatedAt: new Date(now).toISOString(),
    git: {
      branch: git(root, ['rev-parse', '--abbrev-ref', 'HEAD']),
      head: git(root, ['rev-parse', '--short', 'HEAD']),
      changedFiles: (git(root, ['status', '--porcelain']) ?? '').split('\n').filter(Boolean).length
    },
    openWorkItems: openWorkItems(root, now),
    openFindings: fs.existsSync(checklist) ? openFindings(readText(checklist)) : null,
    lessons: fs.existsSync(lessonsFile) ? lessons(readText(lessonsFile)) : null,
    knowledge: freshness
  };
}

export function renderMarkdown(brief) {
  const out = [];
  out.push(`# Session brief (${brief.generatedAt})`, '');
  out.push('Computed from repository files just now. Re-read a source file before relying on a detail.', '');
  out.push(`Git: branch \`${brief.git.branch ?? '?'}\` @ ${brief.git.head ?? '?'}, ${brief.git.changedFiles} changed/untracked path(s).`, '');

  out.push('## Open work items', '');
  if (!brief.openWorkItems.length) out.push('None.');
  for (const item of brief.openWorkItems) {
    const flags = [item.waitingForUser ? 'waiting for user' : null, item.stale ? `STALE ${item.ageDays} d` : null]
      .filter(Boolean).join(', ');
    out.push(`- **${item.id}**: \`${item.state}\` rev ${item.revision ?? '?'}, last activity ${item.lastActivity ?? 'unknown'}${flags ? ` (${flags})` : ''}`);
    if (item.nextAction) out.push(`  - next action: ${item.nextAction}`);
    for (const issue of item.issues ?? []) out.push(`  - issue: ${issue}`);
  }
  out.push('', `Resume one with: \`node tools/feature-workflow/workflow.mjs recover work-items/<ID>/run.json\``, '');

  out.push('## Security findings not FIXED', '');
  if (brief.openFindings === null) out.push('Checklist not found.');
  else if (!brief.openFindings.length) out.push('None.');
  else for (const row of brief.openFindings) out.push(`- ${row.id} (${row.severity}): ${row.status}`);
  out.push('');

  out.push('## Engineering lessons (docs/engineering/LESSONS.md)', '');
  if (brief.lessons === null) out.push('File not found.');
  else {
    const active = brief.lessons.filter(entry => !/^(SUPERSEDED|OBSOLETE)/i.test(entry.status));
    out.push(`${active.length} active of ${brief.lessons.length}. Read the entry before work in an area its title names.`);
    for (const entry of active) out.push(`- ${entry.id} (${entry.date ?? 'undated'}): ${entry.title}`);
  }
  out.push('');

  out.push('## Durable knowledge freshness', '');
  for (const entry of brief.knowledge) {
    const since = entry.workItemsFinishedSince.length
      ? ` — ${entry.workItemsFinishedSince.length} work item(s) finished since: ${entry.workItemsFinishedSince.join(', ')}; check for stale statements`
      : '';
    out.push(`- \`${entry.file}\`: ${entry.lastChanged ?? 'missing'} (${entry.source})${since}`);
  }
  out.push('');
  return out.join('\n');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const brief = buildBrief(root);
  console.log(process.argv.includes('--json') ? JSON.stringify(brief, null, 2) : renderMarkdown(brief));
}
