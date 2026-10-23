import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { openFindings, lessons, openWorkItems, buildBrief, renderMarkdown, STALE_RUN_DAYS } from './session-brief.mjs';

const CHECKLIST = `
| ID | Severity | Finding | Evidence | Status |
|---|---|---|---|---|
| SEC-001 | Critical | secrets | git | OPEN — rotation deferred |
| SEC-002 | High | idor | static | FIXED on branch x |
| SEC-009 | Medium | disabled users | static | IN PROGRESS: \`work-items/SEC-SESSION-001\` |
`;

test('openFindings keeps every status except FIXED', () => {
  const rows = openFindings(CHECKLIST);
  assert.deepEqual(rows.map(row => row.id), ['SEC-001', 'SEC-009']);
  assert.equal(rows[0].severity, 'Critical');
  assert.match(rows[1].status, /^IN PROGRESS/);
});

test('lessons parses id, title, date and status', () => {
  const text = [
    '# Lessons', '',
    '## L-2026-001 — First lesson', 'Date: 2026-10-16', 'Status: ACTIVE', '',
    '## L-2026-002 - Second lesson', 'Date: 2026-10-17', 'Status: SUPERSEDED by L-2026-003'
  ].join('\n');
  const entries = lessons(text);
  assert.equal(entries.length, 2);
  assert.deepEqual(entries[0], { id: 'L-2026-001', title: 'First lesson', date: '2026-10-16', status: 'ACTIVE' });
  assert.match(entries[1].status, /^SUPERSEDED/);
});

function fixtureRepo() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'brief-'));
  fs.mkdirSync(path.join(root, 'src'));
  fs.writeFileSync(path.join(root, 'src', 'a.txt'), 'a');
  const write = (id, run) => {
    fs.mkdirSync(path.join(root, 'work-items', id), { recursive: true });
    fs.writeFileSync(path.join(root, 'work-items', id, 'run.json'), JSON.stringify(run));
  };
  const base = { root, scope: ['src'], graphHash: 'x', revision: 3, handoff: null, returnTo: null };
  write('DONE-1', { ...base, id: 'DONE-1', state: 'done',
    history: [{ event: 'finish', time: '2026-10-20T00:00:00.000Z' }] });
  write('OLD-1', { ...base, id: 'OLD-1', state: 'await_user_test',
    history: [{ event: 'pass', time: '2026-10-01T00:00:00.000Z' }] });
  write('NEW-1', { ...base, id: 'NEW-1', state: 'implement',
    history: [{ event: 'ready', time: '2026-10-22T00:00:00.000Z' }],
    checkpoint: { time: '2026-10-22T12:00:00.000Z', next_action: 'write tests' } });
  return root;
}

test('openWorkItems skips terminal runs and flags stale ones', () => {
  const root = fixtureRepo();
  const now = Date.parse('2026-10-23T00:00:00.000Z');
  const items = openWorkItems(root, now);
  assert.deepEqual(items.map(item => item.id), ['OLD-1', 'NEW-1']);
  assert.equal(items[0].stale, true);
  assert.ok(items[0].ageDays >= STALE_RUN_DAYS);
  assert.equal(items[0].waitingForUser, true);
  assert.equal(items[1].stale, false);
  assert.equal(items[1].nextAction, 'write tests');
  assert.ok(items.every(item => Array.isArray(item.issues)));
});

test('buildBrief and renderMarkdown work without checklist, lessons or git', () => {
  const root = fixtureRepo();
  const brief = buildBrief(root, Date.parse('2026-10-23T00:00:00.000Z'));
  assert.equal(brief.openFindings, null);
  assert.equal(brief.lessons, null);
  const text = renderMarkdown(brief);
  assert.match(text, /OLD-1/);
  assert.match(text, /STALE/);
  assert.doesNotMatch(text, /DONE-1\*\*/);
});

test('the brief writes nothing', () => {
  const root = fixtureRepo();
  const list = dir => fs.readdirSync(dir, { recursive: true }).sort();
  const before = list(root);
  buildBrief(root);
  assert.deepEqual(list(root), before);
});
