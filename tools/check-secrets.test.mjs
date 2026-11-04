import test from 'node:test';
import assert from 'node:assert/strict';
import {findLiterals} from './check-secrets.mjs';

test('literal secret values are reported by key and line, never by value', () => {
  const problems = findLiterals('spring.datasource.password=hunter2\nserver.port=8080\nengine.api.key = abc');
  assert.deepEqual(problems, [{line: 1, key: 'spring.datasource.password'}, {line: 3, key: 'engine.api.key'}]);
  assert.ok(!JSON.stringify(problems).includes('hunter2'));
});

test('empty values and environment placeholders without a default pass', () => {
  assert.deepEqual(findLiterals('a.password=\nb.secret-key=${GVRP_JWT_SECRET:}\nc.api-key=${X}\n# d.password=old'), []);
});

test('a placeholder with a literal default is reported', () => {
  assert.deepEqual(findLiterals('spring.mail.password=${MAIL:fallback}'), [{line: 1, key: 'spring.mail.password'}]);
});

test('angle-bracket placeholders are allowed only in example files', () => {
  assert.deepEqual(findLiterals('geocoding.goong.api-key=<set locally>', {example: true}), []);
  assert.equal(findLiterals('geocoding.goong.api-key=<set locally>').length, 1);
});

test('non-secret keys are ignored', () => {
  assert.deepEqual(findLiterals('spring.mail.username=a@b.c\nlogging.level.root=INFO'), []);
});
