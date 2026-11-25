import fs from 'node:fs';
import path from 'node:path';

export interface TestEnv {
  entryBaseUrl: string;
  engineBaseUrl: string;
  engineInboundKey: string;
  engineCallbackKey: string;
  testPassword: string;
  branchA: string;
  branchB: string;
  branchNew: string;
  plannerA: string;
  plannerB: string;
  plannerNew: string;
  driverA1: string;
  driverA2: string;
  driverB1: string;
  customerA: string;
  disabledA: string;
  todayDate: string;
}

const REPO_ROOT = path.resolve(__dirname, '..', '..', '..', '..');
const LOCAL_ENV_PATH = path.join(
  REPO_ROOT,
  'tools',
  'system-test',
  'postman',
  'GVRP-Local-Test.postman_environment.local.json',
);

let cachedEnv: TestEnv | null = null;

function localIsoDate(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

export function getRepoRoot(): string {
  return REPO_ROOT;
}

export function getTestEnv(): TestEnv {
  if (cachedEnv) return cachedEnv;

  const vars: Record<string, string> = {};
  if (fs.existsSync(LOCAL_ENV_PATH)) {
    let raw = fs.readFileSync(LOCAL_ENV_PATH, 'utf8');
    // Strip UTF-8 BOM if present
    if (raw.charCodeAt(0) === 0xfeff) {
      raw = raw.slice(1);
    }
    const parsed = JSON.parse(raw) as {
      values?: Array<{ key: string; value: string }>;
    };
    for (const item of parsed.values ?? []) {
      vars[item.key] = String(item.value ?? '');
    }
  }

  const testPassword =
    process.env.GVRP_TEST_PASSWORD ||
    process.env.GVRP_TEST_USER_PASSWORD ||
    vars['testPassword'] ||
    '';

  if (!testPassword) {
    throw new Error(
      `Missing testPassword in ${LOCAL_ENV_PATH} and GVRP_TEST_PASSWORD env var.`,
    );
  }

  cachedEnv = {
    entryBaseUrl: vars['entryBaseUrl'] || 'http://localhost:8080',
    engineBaseUrl: vars['engineBaseUrl'] || 'http://localhost:8081',
    engineInboundKey: vars['engineInboundKey'] || '',
    engineCallbackKey: vars['engineCallbackKey'] || '',
    testPassword,
    branchA: vars['branchAName'] || 'ST-BRANCH-A',
    branchB: vars['branchBName'] || 'ST-BRANCH-B',
    branchNew: 'ST-BRANCH-NEW',
    plannerA: 'planner.a',
    plannerB: 'planner.b',
    plannerNew: 'planner.new',
    driverA1: 'driver.a1',
    driverA2: 'driver.a2',
    driverB1: 'driver.b1',
    customerA: 'customer.a',
    disabledA: 'disabled.a',
    todayDate: localIsoDate(),
  };

  return cachedEnv;
}
