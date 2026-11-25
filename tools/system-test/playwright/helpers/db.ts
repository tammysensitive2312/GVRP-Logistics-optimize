import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { getRepoRoot } from './env';

/**
 * Resets `gvrp_test_db` on port 3307 back to the clean ST-* baseline seed
 * while leaving Entry API (:8080), Engine API (:8081), and Client (:4200) running.
 */
export function reseedDatabase(): void {
  const scriptPath = path.join(
    getRepoRoot(),
    'tools',
    'system-test',
    'db',
    'reseed-test-db.ps1',
  );
  execFileSync(
    'powershell',
    ['-ExecutionPolicy', 'Bypass', '-File', scriptPath],
    {
      cwd: getRepoRoot(),
      stdio: 'pipe',
      encoding: 'utf8',
    },
  );
}

/**
 * Executes a single SQL statement against `gvrp_test_db` inside `gvrp-system-test-db`
 * without storing any credentials in the test codebase.
 */
export function execTestSql(sql: string): string {
  return execFileSync(
    'docker',
    [
      'exec',
      '-i',
      'gvrp-system-test-db',
      'sh',
      '-c',
      'mysql -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B gvrp_test_db 2>/dev/null',
    ],
    {
      input: sql,
      cwd: getRepoRoot(),
      encoding: 'utf8',
      stdio: ['pipe', 'pipe', 'pipe'],
    },
  );
}
