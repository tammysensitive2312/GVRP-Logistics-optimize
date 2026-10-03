/** Local workflow ledger. Evidence is auditable, not cryptographically authenticated. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
export const graphPath = path.join(repository, 'docs/workflows/business-development/graph.json');
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const read = file => JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
function requireValue(condition, message) { if (!condition) throw new Error(message); }
function localFile(root, relative) {
  requireValue(typeof relative === 'string' && relative.length > 0 && !path.isAbsolute(relative), 'Use a relative path');
  const resolved = fs.realpathSync(path.resolve(root, relative));
  const rel = path.relative(fs.realpathSync(root), resolved);
  requireValue(rel !== '..' && !rel.startsWith(`..${path.sep}`) && !path.isAbsolute(rel), 'Path escapes run root');
  return resolved;
}
function manifest(root, scope) {
  requireValue(Array.isArray(scope) && scope.length > 0, 'Scope must include code, tests and design paths');
  const files = {};
  function visit(file) {
    requireValue(!fs.lstatSync(file).isSymbolicLink(), 'Symlinks inside scope are not supported');
    if (fs.statSync(file).isDirectory()) {
      for (const child of fs.readdirSync(file).sort()) visit(path.join(file, child));
    } else {
      files[path.relative(root, file).split(path.sep).join('/')] = hash(fs.readFileSync(file));
    }
  }
  for (const item of scope) visit(localFile(root, item));
  requireValue(Object.keys(files).length > 0, 'Scope contains no files');
  return files;
}
export function snapshot(root, scope) {
  return hash(JSON.stringify(Object.entries(manifest(root, scope)).sort(([a], [b]) => a.localeCompare(b))));
}
export function checkpoint(run, input, graph) {
  requireValue(run.graphHash === hash(JSON.stringify(graph)), 'Graph changed');
  requireValue(input.revision === run.revision, 'Stale checkpoint revision');
  requireValue(!['done', 'cancelled'].includes(run.state), 'Run is terminal');
  for (const key of ['completed', 'remaining', 'read_next', 'blockers', 'decisions', 'processes']) {
    requireValue(Array.isArray(input[key]) && input[key].every(v => typeof v === 'string'), `Expected string array: ${key}`);
  }
  requireValue(typeof input.next_action === 'string' && input.next_action.trim(), 'next_action required');
  requireValue(Array.isArray(input.evidence) && input.evidence.every(v => typeof v === 'string'), 'evidence paths required');
  const evidence = input.evidence.map(relative => ({path: relative,
    sha256: hash(fs.readFileSync(localFile(run.root, relative)))}));
  for (const relative of input.read_next) localFile(run.root, relative);
  const next = structuredClone(run);
  next.revision++;
  next.checkpoint = {state: run.state, revision: next.revision, time: new Date().toISOString(),
    completed: input.completed, remaining: input.remaining, read_next: input.read_next,
    blockers: input.blockers, decisions: input.decisions, processes: input.processes,
    next_action: input.next_action, evidence, files: manifest(run.root, run.scope)};
  next.history.push({revision: next.revision, event: 'checkpoint', from: run.state,
    to: run.state, time: next.checkpoint.time, checkpoint: structuredClone(next.checkpoint)});
  return next;
}
export function recover(run, graph) {
  const cp = run.checkpoint;
  const issues = [];
  if (run.graphHash !== hash(JSON.stringify(graph))) issues.push('Graph changed; review before continuing');
  if (!cp) issues.push('No checkpoint: reconstruct progress from transition evidence');
  if (cp && (cp.state !== run.state || cp.revision !== run.revision)) issues.push('Checkpoint predates current state/revision');
  let changes = [];
  try {
    const current = manifest(run.root, run.scope);
    if (cp) changes = [...new Set([...Object.keys(cp.files), ...Object.keys(current)])]
      .filter(file => cp.files[file] !== current[file])
      .map(file => ({path: file, change: !(file in current) ? 'deleted' : !(file in cp.files) ? 'added' : 'modified'}));
    if (changes.length) issues.push('Scoped files changed; inspect and reverify affected work');
  } catch (error) { issues.push(`Scope cannot be verified: ${error.message}`); }
  const references = [...(cp?.evidence ?? []),
    ...run.history.flatMap(entry => Object.values(entry.evidence ?? {}))];
  const staleEvidence = references.filter(ref => {
    try { return hash(fs.readFileSync(localFile(run.root, ref.path))) !== ref.sha256; }
    catch { return true; }
  });
  if (staleEvidence.length) issues.push('Some historical evidence is changed or unavailable');
  const waiting = ['await_user_test', 'await_decision'].includes(run.state);
  return {id: run.id, state: run.state, revision: run.revision, returnTo: run.returnTo,
    waiting_for_user: waiting, terminal: ['done', 'cancelled'].includes(run.state),
    issues, changes, stale_evidence: staleEvidence, checkpoint: cp ? {...cp, files: undefined} : null,
    instruction: waiting ? 'Wait for actual user input; recovery grants no approval.' :
      'Read AGENTS.md, node contract and relevant evidence; inspect changes before continuing.',
    process_note: 'Recorded process IDs are historical hints; verify identity and status before interacting. Never rerun blindly.'};
}
export function createRun(root, id, scope, graph) {
  requireValue(typeof id === 'string' && id.trim(), 'Run ID is required');
  root = fs.realpathSync(root);
  snapshot(root, scope);
  return { id, root, scope, graphHash: hash(JSON.stringify(graph)), state: graph.initial,
    revision: 0, history: [], handoff: null, returnTo: null };
}
export function transition(run, event, proof, graph) {
  requireValue(run.graphHash === hash(JSON.stringify(graph)), 'Graph changed; review and migrate explicitly');
  requireValue(proof.revision === run.revision, 'Stale proof revision');
  requireValue(graph.nodes[run.state], 'Unknown current state');
  requireValue(!['done', 'cancelled'].includes(run.state), 'Run is terminal');
  let edge = graph.nodes[run.state][event];
  if (event === 'ask' && run.state !== 'await_decision') edge = graph.controls.ask;
  if (event === 'resume' && run.state === 'await_decision') edge = {...graph.controls.resume, to: run.returnTo};
  if (event === 'cancel') edge = graph.controls.cancel;
  requireValue(edge && graph.nodes[edge.to], `Illegal transition: ${run.state} / ${event}`);
  const evidence = {};
  for (const key of edge.requires) {
    const relative = proof.artifacts?.[key];
    const file = localFile(run.root, relative);
    requireValue(fs.statSync(file).isFile() && fs.statSync(file).size > 0, `Empty evidence: ${key}`);
    evidence[key] = {path: relative, sha256: hash(fs.readFileSync(file))};
  }
  const current = snapshot(run.root, run.scope);
  if (edge.fresh) requireValue(run.handoff === current, 'Handoff changed; use changed event and reverify');
  if (event === 'accepted') {
    const acceptance = read(localFile(run.root, proof.artifacts.user_acceptance));
    requireValue(acceptance.decision === 'accepted' && acceptance.run_id === run.id &&
      acceptance.handoff === run.handoff && typeof acceptance.source === 'string' && acceptance.source.trim(),
      'Acceptance must reference this run, exact handoff and actual user message');
  }
  const next = structuredClone(run);
  if (event === 'ask') next.returnTo = run.state;
  if (event === 'resume') next.returnTo = null;
  if (edge.to === 'await_user_test') next.handoff = current;
  if (['implement', 'design'].includes(edge.to)) next.handoff = null;
  next.history.push({revision: run.revision + 1, from: run.state, event, to: edge.to,
    evidence, snapshot: current, time: new Date().toISOString()});
  next.state = edge.to;
  next.revision++;
  return next;
}
function save(file, value) {
  const temp = `${file}.${process.pid}.tmp`;
  try { fs.writeFileSync(temp, JSON.stringify(value, null, 2) + '\n', {flag: 'wx'}); fs.renameSync(temp, file); }
  finally { if (fs.existsSync(temp)) fs.unlinkSync(temp); }
}
function main(args) {
  const [command, file, ...rest] = args;
  const graph = read(graphPath);
  if (command === 'diagram') {
    console.log('flowchart TD');
    for (const [from, edges] of Object.entries(graph.nodes)) {
      console.log(`  ${from}`);
      for (const [event, edge] of Object.entries(edges)) console.log(`  ${from} -->|${event}| ${edge.to}`);
    }
    console.log('  %% ask: active node -> await_decision; resume: return to saved node; cancel: active node -> cancelled');
    return;
  }
  requireValue(file, 'Specify a run file');
  if (command === 'status') { console.log(JSON.stringify(read(file), null, 2)); return; }
  if (command === 'recover') { console.log(JSON.stringify(recover(read(file), graph), null, 2)); return; }
  requireValue(['init', 'step', 'checkpoint'].includes(command), 'Commands: init, status, step, diagram, checkpoint, recover');
  const lock = `${file}.lock`;
  const fd = fs.openSync(lock, 'wx');
  try {
    if (command === 'init') {
      requireValue(!fs.existsSync(file), 'Run already exists');
      const [root, id, ...scope] = rest;
      save(file, createRun(root, id, scope, graph));
    } else if (command === 'checkpoint') {
      save(file, checkpoint(read(file), read(rest[0]), graph));
    } else {
      const [event, proofFile] = rest;
      save(file, transition(read(file), event, read(proofFile), graph));
    }
    console.log(JSON.stringify({state: read(file).state, revision: read(file).revision}));
  } finally { fs.closeSync(fd); fs.unlinkSync(lock); }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(process.argv.slice(2)); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
