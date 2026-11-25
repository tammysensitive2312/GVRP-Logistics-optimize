/**
 * Parallel HTTP concurrency race runner for ST-RES-007 (route send race) and
 * ST-RES-008 (duplicate vehicle license plate race).
 *
 * Usage:
 *   node tools/system-test/scripts/run-concurrency-races.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const LOCAL_ENV_PATH = path.resolve(
  __dirname,
  "../postman/GVRP-Local-Test.postman_environment.local.json"
);

if (!fs.existsSync(LOCAL_ENV_PATH)) {
  console.error(`Missing ${LOCAL_ENV_PATH}. Run setup-test-db.ps1 first.`);
  process.exit(1);
}

const rawEnv = JSON.parse(fs.readFileSync(LOCAL_ENV_PATH, "utf8").replace(/^\uFEFF/, ""));
const env = Object.fromEntries(rawEnv.values.map((v) => [v.key, v.value]));

async function jsonFetch(url, opts = {}) {
  const res = await fetch(url, opts);
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  return { status: res.status, text, json };
}

async function login(branchName, username) {
  const res = await jsonFetch(`${env.entryBaseUrl}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      branch_name: branchName,
      username,
      password: env.testPassword,
    }),
  });
  if (res.status !== 200 || !res.json?.access_token) {
    throw new Error(`Login failed for ${username}: HTTP ${res.status}`);
  }
  return res.json.access_token;
}

async function main() {
  const tokenA = await login(env.branchAName, "planner.a");
  const tokenB = await login(env.branchBName, "planner.b");

  // ST-RES-008: Concurrent duplicate license plate race across Branch A & Branch B
  const plate = `ST-RC-${Date.now().toString().slice(-6)}`;
  console.log(`[ST-RES-008] Firing parallel vehicle creates with plate ${plate}...`);
  const [vehA, vehB] = await Promise.all([
    jsonFetch(`${env.entryBaseUrl}/api/v1/vehicles?fleetId=${env.fleetAId}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${tokenA}`,
      },
      body: JSON.stringify({
        vehicle_license_plate: plate,
        vehicle_type_id: Number(env.vehicleTypeAId),
        start_depot_id: Number(env.depotAId),
        end_depot_id: Number(env.depotAId),
      }),
    }),
    jsonFetch(`${env.entryBaseUrl}/api/v1/vehicles?fleetId=${env.fleetBId}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${tokenB}`,
      },
      body: JSON.stringify({
        vehicle_license_plate: plate,
        vehicle_type_id: Number(env.vehicleTypeBId),
        start_depot_id: Number(env.depotBId),
        end_depot_id: Number(env.depotBId),
      }),
    }),
  ]);
  const vehStatuses = [vehA.status, vehB.status].sort((a, b) => a - b);
  console.log(`[ST-RES-008] Statuses=${JSON.stringify(vehStatuses)}`);
  const vehPass =
    vehStatuses[0] === 201 &&
    vehStatuses[1] === 409 &&
    [vehA, vehB].some((r) => r.json?.errors?.[0]?.code === "0040901");
  console.log(`[ST-RES-008] Result: ${vehPass ? "PASS" : "FAIL"}`);

  // ST-RES-007: Concurrent route sends under route lock
  console.log(`[ST-RES-007] Firing parallel route sends on route ${env.routeARaceId}...`);
  const [send1, send2] = await Promise.all([
    jsonFetch(`${env.entryBaseUrl}/api/v1/routes/${env.routeARaceId}/send`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${tokenA}`,
      },
      body: JSON.stringify({
        driver_user_id: Number(env.driverA1UserId),
        solution_id: Number(env.solutionAId),
      }),
    }),
    jsonFetch(`${env.entryBaseUrl}/api/v1/routes/${env.routeARaceId}/send`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${tokenA}`,
      },
      body: JSON.stringify({
        driver_user_id: Number(env.driverA2UserId),
        solution_id: Number(env.solutionAId),
      }),
    }),
  ]);
  const sendsAfter = await jsonFetch(
    `${env.entryBaseUrl}/api/v1/routes/${env.routeARaceId}/sends`,
    { headers: { Authorization: `Bearer ${tokenA}` } }
  );
  const currentCount = Array.isArray(sendsAfter.json)
    ? sendsAfter.json.filter((s) => s.status === "CURRENT").length
    : -1;
  console.log(
    `[ST-RES-007] Send statuses=[${send1.status}, ${send2.status}], CURRENT rows=${currentCount}`
  );
  const sendPass = currentCount === 1 && send1.status < 500 && send2.status < 500;
  console.log(`[ST-RES-007] Result: ${sendPass ? "PASS" : "FAIL"}`);

  if (!vehPass || !sendPass) {
    process.exit(1);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
