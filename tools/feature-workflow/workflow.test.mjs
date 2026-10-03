import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createRun, transition, checkpoint, recover, graphPath} from './workflow.mjs';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const graph = JSON.parse(fs.readFileSync(graphPath, 'utf8'));
const checkpointInput = revision => ({revision, completed: ['Read source'], remaining: ['Add test'],
  read_next: ['src/code.txt'], evidence: ['evidence.md'], blockers: [], decisions: [],
  processes: [], next_action: 'Add failure test'});

test('checkpoint persists across CLI processes and recovery is read only', t => {
  const f = fixture(t);
  const runFile = path.join(f.root, 'run.json');
  const inputFile = path.join(f.root, 'input.json');
  fs.writeFileSync(runFile, JSON.stringify(f.run));
  fs.writeFileSync(inputFile, JSON.stringify(checkpointInput(0)));
  const cli = fileURLToPath(new URL('./workflow.mjs', import.meta.url));
  execFileSync(process.execPath, [cli, 'checkpoint', runFile, inputFile]);
  const before = fs.readFileSync(runFile, 'utf8');
  const result = JSON.parse(execFileSync(process.execPath, [cli, 'recover', runFile], {encoding: 'utf8'}));
  assert.equal(result.checkpoint.next_action, 'Add failure test');
  assert.equal(result.revision, 1);
  assert.deepEqual(result.issues, []);
  assert.equal(fs.readFileSync(runFile, 'utf8'), before);
});
test('recovery reports file changes and stale evidence', t => {
  const f = fixture(t);
  const run = checkpoint(f.run, checkpointInput(0), graph);
  fs.writeFileSync(path.join(f.root, 'src/new.txt'), 'new');
  fs.unlinkSync(path.join(f.root, 'src/code.txt'));
  fs.writeFileSync(path.join(f.root, 'evidence.md'), 'changed');
  const result = recover(run, graph);
  assert.equal(result.changes.length, 2);
  assert.equal(result.stale_evidence.length, 1);
  assert.equal(result.state, 'intake');
});
test('recovery preserves user gates and flags checkpoints from earlier revisions', t => {
  const f = fixture(t); f.handoff();
  const run = checkpoint(f.run, checkpointInput(f.run.revision), graph);
  assert.equal(recover(run, graph).waiting_for_user, true);
  assert.throws(() => transition(run, 'optimize', {revision: run.revision}, graph), /Illegal/);
  const waiting = transition(run, 'ask', {revision: run.revision, artifacts: {question: 'evidence.md'}}, graph);
  const result = recover(waiting, graph);
  assert.equal(result.waiting_for_user, true);
  assert.equal(result.returnTo, 'await_user_test');
  assert.ok(result.issues.some(v => v.includes('predates')));
});
test('legacy runs recover; malformed/stale checkpoints rejected; missing scope reported', t => {
  const f = fixture(t);
  assert.ok(recover(f.run, graph).issues[0].includes('No checkpoint'));
  assert.throws(() => checkpoint(f.run, checkpointInput(9), graph), /Stale/);
  assert.throws(() => checkpoint(f.run, {...checkpointInput(0), completed: 'bad'}, graph), /array/);
  const run = checkpoint(f.run, checkpointInput(0), graph);
  fs.renameSync(path.join(f.root, 'src'), path.join(f.root, 'moved'));
  assert.ok(recover(run, graph).issues.some(v => v.includes('cannot be verified')));
  assert.ok(recover(run, {...graph, version: 99}).issues.some(v => v.includes('Graph changed')));
});
function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'feature-workflow-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  fs.mkdirSync(path.join(root, 'src'));
  fs.writeFileSync(path.join(root, 'src/code.txt'), 'version one');
  fs.writeFileSync(path.join(root, 'evidence.md'), 'Synthetic test evidence, not a real validation.');
  let run = createRun(root, 'TEST-001', ['src'], graph);
  function step(event, extra = {}) {
    const artifacts = Object.fromEntries(['request', 'context', 'design', 'traceability',
      'implementation', 'verification', 'handoff', 'findings', 'user_feedback',
      'closure', 'optimization_plan', 'baseline', 'comparison', 'question',
      'user_decision', 'cancellation'].map(key => [key, 'evidence.md']));
    run = transition(run, event, {revision: run.revision, artifacts: {...artifacts, ...extra}}, graph);
    return run;
  }
  function handoff() { for (const event of ['ready', 'ready', 'ready', 'ready', 'pass']) step(event); }
  function accept() {
    fs.writeFileSync(path.join(root, 'acceptance.json'), JSON.stringify({decision: 'accepted',
      run_id: run.id, handoff: run.handoff, source: 'Synthetic user message for test only'}));
    return step('accepted', {user_acceptance: 'acceptance.json'});
  }
  return {root, step, handoff, accept, get run() { return run; }};
}
test('cannot skip directly to optimization or pass without evidence', t => {
  const f = fixture(t);
  assert.throws(() => f.step('optimize'), /Illegal transition/);
  assert.throws(() => transition(f.run, 'ready', {revision: 0, artifacts: {}}, graph));
  assert.equal(f.run.state, 'intake');
});
test('user gate, optimization, second user gate and completion', t => {
  const f = fixture(t); f.handoff();
  assert.throws(() => f.step('optimize'), /Illegal transition/);
  f.accept(); f.step('optimize');
  fs.writeFileSync(path.join(f.root, 'src/code.txt'), 'optimized');
  f.step('ready'); f.step('pass');
  assert.throws(() => f.step('finish'), /Illegal transition/);
  f.accept(); f.step('finish');
  assert.equal(f.run.state, 'done');
  assert.throws(() => f.step('ready'), /terminal/);
});
test('changed handoff invalidates acceptance and optimization', t => {
  const f = fixture(t); f.handoff();
  fs.writeFileSync(path.join(f.root, 'src/new.txt'), 'new file');
  assert.throws(() => f.accept(), /Handoff changed/);
  f.step('changed'); f.step('ready'); f.step('pass'); f.accept();
  fs.unlinkSync(path.join(f.root, 'src/new.txt'));
  assert.throws(() => f.step('optimize'), /Handoff changed/);
  assert.throws(() => f.step('finish'), /Handoff changed/);
});
test('reject stale revisions, changed graph, wrong acceptance and outside evidence', t => {
  const f = fixture(t);
  assert.throws(() => transition(f.run, 'ready', {revision: 10}, graph), /Stale/);
  assert.throws(() => transition(f.run, 'ready', {revision: 0}, {...graph, version: 2}), /Graph changed/);
  assert.throws(() => f.step('ready', {request: '../'}), /escapes/);
  f.handoff();
  fs.writeFileSync(path.join(f.root, 'wrong.json'), JSON.stringify({decision: 'accepted', run_id: 'OTHER'}));
  assert.throws(() => f.step('accepted', {user_acceptance: 'wrong.json'}), /Acceptance/);
});
test('decision pause survives serialization and returns to original node', t => {
  const f = fixture(t); f.step('ready'); f.step('ask');
  const resumed = transition(JSON.parse(JSON.stringify(f.run)), 'resume', {
    revision: f.run.revision, artifacts: {user_decision: 'evidence.md'}}, graph);
  assert.equal(resumed.state, 'discover');
  assert.equal(resumed.returnTo, null);
});
test('verification failure, rejection and cancellation have explicit paths', t => {
  const f = fixture(t); f.handoff(); f.step('rejected');
  f.step('ready'); f.step('design_changed');
  assert.equal(f.run.state, 'design');
  f.step('cancel');
  assert.equal(f.run.state, 'cancelled');
});
