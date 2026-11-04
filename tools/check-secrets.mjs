#!/usr/bin/env node
// Fails when a git-tracked configuration file holds a literal value for a secret-like key
// (SEC-001). Allowed values: empty, a `${ENV_VAR:...}` placeholder without a literal default,
// or `<...>` in *.example files. Prints file, line and key only — never the value.
import {execFileSync} from 'node:child_process';
import fs from 'node:fs';

export const SECRET_KEY = /(password|passwd|secret|api[-_.]?key|access[-_.]?key|private[-_.]?key|token|credential)/i;
const CONFIG_FILE = /(^|\/)(application[^/]*\.(properties|ya?ml)(\.example)?|\.env[^/]*|[^/]*\.toml)$/i;

/** Problems in one file's text: [{line, key}] for secret-like keys with a literal value. */
export function findLiterals(text, {example = false} = {}) {
  const problems = [];
  text.split(/\r?\n/).forEach((raw, i) => {
    const line = raw.trim();
    if (!line || line.startsWith('#') || line.startsWith('!')) return;
    const m = line.match(/^([A-Za-z0-9_.\-\[\]"]+)\s*[=:]\s*(.*)$/);
    if (!m || !SECRET_KEY.test(m[1])) return;
    const value = m[2].trim().replace(/^["']|["']$/g, '');
    if (value === '') return;
    const placeholder = value.match(/^\$\{[A-Za-z0-9_.\-]+(:([^}]*))?\}$/);
    if (placeholder && !placeholder[2]) return;
    if (example && /^<[^>]*>$/.test(value)) return;
    if (/^\$\{?env:|^env\(/i.test(value)) return; // e.g. TOML `{ env = "VAR" }`-style references
    problems.push({line: i + 1, key: m[1]});
  });
  return problems;
}

function main() {
  const files = execFileSync('git', ['ls-files'], {encoding: 'utf8'}).split('\n')
    .filter(f => CONFIG_FILE.test(f) && !f.includes('node_modules/'));
  let failures = 0;
  for (const file of files) {
    if (!fs.existsSync(file)) continue;
    for (const p of findLiterals(fs.readFileSync(file, 'utf8'), {example: file.endsWith('.example')})) {
      console.error(`${file}:${p.line}: literal value for secret-like key "${p.key}" (move it to an environment variable or application-local.properties)`);
      failures++;
    }
  }
  if (failures) {
    console.error(`check-secrets: ${failures} problem(s) in ${files.length} tracked config file(s).`);
    process.exit(1);
  }
  console.log(`check-secrets: OK (${files.length} tracked config file(s)).`);
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].replace(/\\/g, '/').split('/').pop())) main();
