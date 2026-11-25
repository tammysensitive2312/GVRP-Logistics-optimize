/**
 * Generates Postman Collection v2.1 (`tools/system-test/postman/GVRP-System-Test.postman_collection.json`)
 * covering the 37 API_AUTO_TARGET cases in `GVRP_Document/04-test-design/system-test-cases.csv`.
 *
 * Usage:
 *   node tools/system-test/scripts/generate-postman-collection.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_FILE = path.resolve(__dirname, "../postman/GVRP-System-Test.postman_collection.json");

function makeUrl(baseVar, rawPath, queryPairs = []) {
  const clean = rawPath.startsWith("/") ? rawPath.slice(1) : rawPath;
  const pathParts = clean ? clean.split("/") : [];
  const qStr = queryPairs.length
    ? "?" + queryPairs.map(([k, v]) => `${k}=${v}`).join("&")
    : "";
  return {
    raw: `{{${baseVar}}}/${clean}${qStr}`,
    host: [`{{${baseVar}}}`],
    path: pathParts,
    ...(queryPairs.length
      ? { query: queryPairs.map(([key, value]) => ({ key, value })) }
      : {}),
  };
}

function testEvent(lines) {
  return {
    listen: "test",
    script: { type: "text/javascript", exec: lines },
  };
}

function prereqEvent(lines) {
  return {
    listen: "prerequest",
    script: { type: "text/javascript", exec: lines },
  };
}

function req({
  name,
  method,
  base = "entryBaseUrl",
  path: urlPath,
  query = [],
  headers = [],
  bearerTokenVar = null,
  body = null,
  rawBody = null,
  formdata = null,
  prereq = null,
  tests = [],
}) {
  const hdrs = [...headers];
  if (bearerTokenVar) {
    hdrs.push({ key: "Authorization", value: `Bearer {{${bearerTokenVar}}}` });
  }
  const hasContentType = hdrs.some((h) => h.key.toLowerCase() === "content-type");
  let bodyObj;
  if (formdata) {
    bodyObj = { mode: "formdata", formdata };
  } else if (rawBody !== null) {
    if (!hasContentType) {
      hdrs.push({ key: "Content-Type", value: "application/json" });
    }
    bodyObj = { mode: "raw", raw: rawBody };
  } else if (body !== null) {
    if (!hasContentType) {
      hdrs.push({ key: "Content-Type", value: "application/json" });
    }
    bodyObj = {
      mode: "raw",
      raw: JSON.stringify(body, null, 2),
      options: { raw: { language: "json" } },
    };
  }

  const events = [];
  if (prereq) events.push(prereqEvent(prereq));
  if (tests.length) events.push(testEvent(tests));

  return {
    name,
    ...(events.length ? { event: events } : {}),
    request: {
      method,
      header: hdrs,
      ...(bodyObj ? { body: bodyObj } : {}),
      url: makeUrl(base, urlPath, query),
    },
  };
}

function assertError(status, code, resource = null, field = null) {
  const lines = [
    `pm.test("HTTP ${status}", () => pm.response.to.have.status(${status}));`,
    `pm.test("Error envelope code=${code}", () => {`,
    `  const b = pm.response.json();`,
    `  pm.expect(b.errors).to.be.an("array").that.is.not.empty;`,
    `  pm.expect(b.errors[0].code).to.eql("${code}");`,
  ];
  if (resource !== null) {
    lines.push(`  pm.expect(b.errors[0].resource).to.eql("${resource}");`);
  }
  if (field !== null) {
    lines.push(`  pm.expect(b.errors[0].field).to.eql("${field}");`);
  }
  lines.push(`});`);
  return lines;
}

function loginSetupReq(label, branchVar, username, tokenOutVar) {
  return req({
    name: `Login ${label} (${username})`,
    method: "POST",
    path: "/api/v1/auth/login",
    body: {
      branch_name: `{{${branchVar}}}`,
      username,
      password: "{{testPassword}}",
    },
    tests: [
      `pm.test("Login ${username} returns 200 and saves JWT", () => {`,
      `  pm.response.to.have.status(200);`,
      `  const j = pm.response.json();`,
      `  pm.expect(j.access_token).to.be.a("string").and.not.empty;`,
      `  pm.environment.set("${tokenOutVar}", j.access_token);`,
      `});`,
    ],
  });
}

const completeCallbackPayload = (jobExpr, orderId, vehicleId) => ({
  job_id: jobExpr,
  processing_time_seconds: 1,
  solution: {
    total_distance: 5.2,
    total_cost: 250000,
    total_co2: 0.0,
    total_time: 20,
    total_service_time: 10,
    total_vehicles_used: 1,
    served_orders: 1,
    unserved_orders: 0,
    unassigned_orders: [],
    routes: [
      {
        vehicle_id: vehicleId,
        route_order: 1,
        distance: 5.2,
        duration: 20,
        co2_emission: 0.0,
        order_count: 1,
        load_utilization: 5.0,
        stops: [
          {
            sequence_number: 0,
            type: "DEPOT",
            location_id: "DEPOT-101",
            location_name: "ST-Depot-A1",
            latitude: 21.0285,
            longitude: 105.8542,
            arrival_time: "08:00:00",
            departure_time: "08:00:00",
            service_time: 0,
            wait_time: 0,
            demand: 0,
            load_after: 50,
            distance_to_next: 2.6,
            time_to_next: 10,
          },
          {
            sequence_number: 1,
            type: "ORDER",
            order_id: orderId,
            order_code: "ST-ORD-COMP-01",
            location_id: String(orderId),
            location_name: "Order Stop",
            latitude: 21.025,
            longitude: 105.855,
            arrival_time: "08:10:00",
            departure_time: "08:20:00",
            service_time: 10,
            wait_time: 0,
            demand: 50,
            load_after: 0,
            distance_to_next: 2.6,
            time_to_next: 10,
          },
          {
            sequence_number: 2,
            type: "DEPOT",
            location_id: "DEPOT-101",
            location_name: "ST-Depot-A1",
            latitude: 21.0285,
            longitude: 105.8542,
            arrival_time: "08:30:00",
            departure_time: "08:30:00",
            service_time: 0,
            wait_time: 0,
            demand: 0,
            load_after: 0,
            distance_to_next: 0,
            time_to_next: 0,
          },
        ],
      },
    ],
  },
});

const collection = {
  info: {
    _postman_id: "7f21346f-gvrp-system-test-v1",
    name: "GVRP System Test Suite (STP-GVRP-01)",
    description:
      "Importable Postman v2.1 collection covering API_AUTO_TARGET cases in system-test-cases.csv against isolated gvrp_test_db (port 3307). Zero hardcoded secrets.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
  },
  event: [
    prereqEvent([
      `const d = new Date();`,
      `const pad = (n) => String(n).padStart(2, "0");`,
      `const iso = d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());`,
      `pm.environment.set("todayDate", iso);`,
    ]),
  ],
  item: [
    // =========================================================================
    // 00 - Auth & Token Setup
    // =========================================================================
    {
      name: "00 - Auth & Token Setup",
      item: [
        loginSetupReq("Planner A", "branchAName", "planner.a", "plannerAToken"),
        loginSetupReq("Planner B", "branchBName", "planner.b", "plannerBToken"),
        loginSetupReq("Driver A1", "branchAName", "driver.a1", "driverA1Token"),
        loginSetupReq("Driver A2", "branchAName", "driver.a2", "driverA2Token"),
        loginSetupReq("Customer A", "branchAName", "customer.a", "customerAToken"),
      ],
    },

    // =========================================================================
    // 01 - SEC (ST-SEC-001 .. ST-SEC-014)
    // =========================================================================
    {
      name: "01 - SEC (Security, RBAC & Multi-Tenant Isolation)",
      item: [
        // ST-SEC-001
        req({
          name: "ST-SEC-001a Login unknown branch -> 404 0040402 neutral",
          method: "POST",
          path: "/api/v1/auth/login",
          body: { branch_name: "NO-SUCH-BRANCH", username: "planner.a", password: "{{testPassword}}" },
          tests: [
            ...assertError(404, "0040402", "branches"),
            `pm.test("Neutral message", () => pm.expect(pm.response.json().errors[0].message).to.eql("Resource not found."));`,
          ],
        }),
        req({
          name: "ST-SEC-001b Login wrong password -> 401 0040101 (never 500)",
          method: "POST",
          path: "/api/v1/auth/login",
          body: { branch_name: "{{branchAName}}", username: "planner.a", password: "wrong-password" },
          tests: assertError(401, "0040101"),
        }),
        req({
          name: "ST-SEC-001c Login disabled user -> 401 0040101",
          method: "POST",
          path: "/api/v1/auth/login",
          body: { branch_name: "{{branchAName}}", username: "disabled.a", password: "{{testPassword}}" },
          tests: assertError(401, "0040101"),
        }),
        // ST-SEC-002
        req({
          name: "ST-SEC-002a Register disabled (existing username) -> 403 0040301",
          method: "POST",
          path: "/api/v1/auth/register",
          body: { username: "planner.a", password: "x", email: "x@example.test", full_name: "X", branch_id: 1 },
          tests: [
            ...assertError(403, "0040301"),
            `pm.environment.set("regBodyA", pm.response.text());`,
          ],
        }),
        req({
          name: "ST-SEC-002b Register disabled (unused username) identical -> 403 0040301",
          method: "POST",
          path: "/api/v1/auth/register",
          body: { username: "brand.new.user.999", password: "x", email: "y@example.test", full_name: "Y", branch_id: 999 },
          tests: [
            ...assertError(403, "0040301"),
            `pm.test("Identical response", () => pm.expect(pm.response.text()).to.eql(pm.environment.get("regBodyA")));`,
          ],
        }),
        // ST-SEC-003
        req({
          name: "ST-SEC-003a Tampered JWT signature -> 401 0040102",
          method: "GET",
          path: "/api/v1/orders",
          headers: [{ key: "Authorization", value: "Bearer {{plannerAToken}}tampered" }],
          tests: assertError(401, "0040102"),
        }),
        req({
          name: "ST-SEC-003b Malformed JWT -> 401 0040102",
          method: "GET",
          path: "/api/v1/orders",
          headers: [{ key: "Authorization", value: "Bearer not-a-jwt" }],
          tests: assertError(401, "0040102"),
        }),
        // ST-SEC-005
        req({
          name: "ST-SEC-005a Anonymous GET /orders -> 401 0040102",
          method: "GET",
          path: "/api/v1/orders",
          tests: assertError(401, "0040102"),
        }),
        req({
          name: "ST-SEC-005b DRIVER GET /orders -> 403 0040301",
          method: "GET",
          path: "/api/v1/orders",
          bearerTokenVar: "driverA1Token",
          tests: assertError(403, "0040301"),
        }),
        req({
          name: "ST-SEC-005c DRIVER GET /zone-templates -> 403 0040301",
          method: "GET",
          path: "/api/v1/zone-templates",
          bearerTokenVar: "driverA1Token",
          tests: assertError(403, "0040301"),
        }),
        req({
          name: "ST-SEC-005d DRIVER GET /drivers -> 403 0040301",
          method: "GET",
          path: "/api/v1/drivers",
          bearerTokenVar: "driverA1Token",
          tests: assertError(403, "0040301"),
        }),
        req({
          name: "ST-SEC-005e PLANNER GET /driver/routes -> 403 0040301",
          method: "GET",
          path: "/api/v1/driver/routes",
          bearerTokenVar: "plannerAToken",
          tests: assertError(403, "0040301"),
        }),
        req({
          name: "ST-SEC-005f CUSTOMER GET /orders -> 403 0040301",
          method: "GET",
          path: "/api/v1/orders",
          bearerTokenVar: "customerAToken",
          tests: assertError(403, "0040301"),
        }),
        req({
          name: "ST-SEC-005g CUSTOMER GET /driver/routes -> 403 0040301",
          method: "GET",
          path: "/api/v1/driver/routes",
          bearerTokenVar: "customerAToken",
          tests: assertError(403, "0040301"),
        }),
        // ST-SEC-006
        req({
          name: "ST-SEC-006a GET /depots scoped to Branch A only",
          method: "GET",
          path: "/api/v1/depots",
          bearerTokenVar: "plannerAToken",
          tests: [
            `pm.test("Only ST-BRANCH-A depots returned", () => {`,
            `  pm.response.to.have.status(200);`,
            `  const names = pm.response.json().map(d => d.name);`,
            `  pm.expect(names).to.include("ST-Depot-A1");`,
            `  pm.expect(names).to.not.include("ST-Depot-B1");`,
            `});`,
          ],
        }),
        req({
          name: "ST-SEC-006b PUT /vehicle-types/{B} -> 404 0040402",
          method: "PUT",
          path: "/api/v1/vehicle-types/{{vehicleTypeBId}}",
          bearerTokenVar: "plannerAToken",
          body: {
            type_name: "Hack",
            capacity: 1000,
            fixed_cost: 1000,
            cost_per_km: 100,
            cost_per_hour: 100,
            vehicle_features: { category: "VAN", emission_factor: 100.0, skills: [] },
          },
          tests: assertError(404, "0040402", "types"),
        }),
        req({
          name: "ST-SEC-006c GET /fleets/{B} -> 404 0040402",
          method: "GET",
          path: "/api/v1/fleets/{{fleetBId}}",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "fleets"),
        }),
        req({
          name: "ST-SEC-006d PUT /vehicles/{B} -> 404 0040402",
          method: "PUT",
          path: "/api/v1/vehicles/{{vehicleBId}}",
          bearerTokenVar: "plannerAToken",
          body: {
            vehicle_license_plate: "ST-29A-NEW",
            vehicle_type_id: 101,
            start_depot_id: 101,
            end_depot_id: 101,
          },
          tests: assertError(404, "0040402", "vehicles"),
        }),
        req({
          name: "ST-SEC-006e POST /vehicles/{V_A}/permits foreign branch_zone_id=Z_B -> 404 0040402",
          method: "POST",
          path: "/api/v1/vehicles/{{vehicleA1Id}}/permits",
          bearerTokenVar: "plannerAToken",
          body: {
            branch_zone_id: 201,
            permit_no: "ST-FOREIGN-ZONE",
            valid_from: "2026-01-01",
            valid_to: "2027-01-01",
          },
          tests: assertError(404, "0040402", "zones"),
        }),
        req({
          name: "ST-SEC-006f GET /vehicles/{V_B}/permits -> 404 0040402",
          method: "GET",
          path: "/api/v1/vehicles/{{vehicleBId}}/permits",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "vehicles"),
        }),
        // ST-SEC-007
        req({
          name: "ST-SEC-007a GET /orders/{O_B} vs unknown -> 404 0040402",
          method: "GET",
          path: "/api/v1/orders/{{orderBId}}",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "orders"),
        }),
        req({
          name: "ST-SEC-007b PUT /orders/{O_B} (no coords) -> 404 0040402",
          method: "PUT",
          path: "/api/v1/orders/{{orderBId}}",
          bearerTokenVar: "plannerAToken",
          body: {
            order_code: "NOPE",
            customer_name: "Nope",
            address: "1 Example St, Ha Noi",
            demand: 10,
            delivery_date: "{{todayDate}}",
          },
          tests: assertError(404, "0040402", "orders"),
        }),
        req({
          name: "ST-SEC-007c PATCH /orders/status [O_B, unknown] -> indistinguishable failed[]",
          method: "PATCH",
          path: "/api/v1/orders/status",
          bearerTokenVar: "plannerAToken",
          body: { ids: [201, 999999999], status: "REJECTED" },
          tests: [
            `pm.test("Foreign and unknown indistinguishable in failed[]", () => {`,
            `  pm.response.to.have.status(200);`,
            `  const b = pm.response.json();`,
            `  pm.expect(b.failed.length).to.eql(2);`,
            `  pm.expect(b.failed[0].order_code).to.be.null;`,
            `  pm.expect(b.failed[1].order_code).to.be.null;`,
            `  pm.expect(b.failed[0].reason).to.eql(b.failed[1].reason);`,
            `});`,
          ],
        }),
        req({
          name: "ST-SEC-007d GET /lez/order-zones omits foreign order 201",
          method: "GET",
          path: "/api/v1/lez/order-zones",
          query: [["orderIds", "{{orderA2LezId}},{{orderBId}}"]],
          bearerTokenVar: "plannerAToken",
          tests: [
            `pm.test("Omits Branch B order", () => {`,
            `  pm.response.to.have.status(200);`,
            `  const ids = pm.response.json().map(x => x.order_id);`,
            `  pm.expect(ids).to.include(Number(pm.environment.get("orderA2LezId")));`,
            `  pm.expect(ids).to.not.include(Number(pm.environment.get("orderBId")));`,
            `});`,
          ],
        }),
        // ST-SEC-008
        req({
          name: "ST-SEC-008a POST /jobs/plan missing order 999999999 (baseline)",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerAToken",
          body: {
            order_ids: [999999999],
            vehicle_ids: [102],
            preferences: { goal: "MINIMIZE_COST", speed: "FAST", allow_unassigned_orders: true, time_window_mode: "STRICT" },
          },
          tests: [
            ...assertError(404, "0040402", "orders"),
            `pm.environment.set("missingOrderJobResp", pm.response.text());`,
          ],
        }),
        req({
          name: "ST-SEC-008b POST /jobs/plan foreign order 201 -> byte-identical 404 (SEC-017)",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerAToken",
          body: {
            order_ids: [201],
            vehicle_ids: [102],
            preferences: { goal: "MINIMIZE_COST", speed: "FAST", allow_unassigned_orders: true, time_window_mode: "STRICT" },
          },
          tests: [
            ...assertError(404, "0040402", "orders"),
            `pm.test("Byte-identical to missing order & no SECRET-BRANCH-B-ORD leak", () => {`,
            `  pm.expect(pm.response.text()).to.eql(pm.environment.get("missingOrderJobResp"));`,
            `  pm.expect(pm.response.text()).to.not.include("SECRET-BRANCH-B-ORD");`,
            `});`,
          ],
        }),
        req({
          name: "ST-SEC-008c POST /jobs/plan missing vehicle 999999999 (baseline)",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerAToken",
          body: {
            order_ids: [101],
            vehicle_ids: [999999999],
            preferences: { goal: "MINIMIZE_COST", speed: "FAST", allow_unassigned_orders: true, time_window_mode: "STRICT" },
          },
          tests: [
            ...assertError(404, "0040402", "vehicles"),
            `pm.environment.set("missingVehJobResp", pm.response.text());`,
          ],
        }),
        req({
          name: "ST-SEC-008d POST /jobs/plan foreign vehicle 201 -> byte-identical 404 (SEC-017)",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerAToken",
          body: {
            order_ids: [101],
            vehicle_ids: [201],
            preferences: { goal: "MINIMIZE_COST", speed: "FAST", allow_unassigned_orders: true, time_window_mode: "STRICT" },
          },
          tests: [
            ...assertError(404, "0040402", "vehicles"),
            `pm.test("Byte-identical to missing vehicle", () => pm.expect(pm.response.text()).to.eql(pm.environment.get("missingVehJobResp")));`,
          ],
        }),
        req({
          name: "ST-SEC-008e GET /jobs/{J_B} -> 404 0040402",
          method: "GET",
          path: "/api/v1/jobs/{{jobBId}}",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "jobs"),
        }),
        req({
          name: "ST-SEC-008f GET /jobs/{J_B}/progress -> 404 0040402",
          method: "GET",
          path: "/api/v1/jobs/{{jobBId}}/progress",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "jobs"),
        }),
        req({
          name: "ST-SEC-008g PUT /jobs [own, J_B] -> 404 0040402",
          method: "PUT",
          path: "/api/v1/jobs",
          bearerTokenVar: "plannerAToken",
          body: { ids: [102, 201] },
          tests: assertError(404, "0040402", "jobs"),
        }),
        // ST-SEC-009
        req({
          name: "ST-SEC-009a GET /solutions/{S_B} -> 404 0040402",
          method: "GET",
          path: "/api/v1/solutions/{{solutionBId}}",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "solutions"),
        }),
        req({
          name: "ST-SEC-009b GET /solutions/{S_B}/pareto -> 404 0040402",
          method: "GET",
          path: "/api/v1/solutions/{{solutionBId}}/pareto",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "solutions"),
        }),
        req({
          name: "ST-SEC-009c POST /solutions/{S_B}/pareto/{C_B}/apply -> 404 0040402",
          method: "POST",
          path: "/api/v1/solutions/{{solutionBId}}/pareto/{{paretoCandBId}}/apply",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402"),
        }),
        req({
          name: "ST-SEC-009d GET /solutions/{S_B}/compliance -> 404 0040402",
          method: "GET",
          path: "/api/v1/solutions/{{solutionBId}}/compliance",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "solutions"),
        }),
        req({
          name: "ST-SEC-009e GET /routes/{R_B}/geometry -> 404 0040402",
          method: "GET",
          path: "/api/v1/routes/{{routeBId}}/geometry",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "routes"),
        }),
        // ST-SEC-010
        req({
          name: "ST-SEC-010a Send Route A with foreign solution S_B -> 404 0040402",
          method: "POST",
          path: "/api/v1/routes/{{routeACompliantId}}/send",
          bearerTokenVar: "plannerAToken",
          body: { driver_user_id: 12, solution_id: 201 },
          tests: assertError(404, "0040402", "solutions"),
        }),
        req({
          name: "ST-SEC-010b Send own S_A with missing driver 999999999 (baseline)",
          method: "POST",
          path: "/api/v1/routes/{{routeACompliantId}}/send",
          bearerTokenVar: "plannerAToken",
          body: { driver_user_id: 999999999, solution_id: 101 },
          tests: [
            ...assertError(404, "0040402", "Driver"),
            `pm.environment.set("missingDriverResp", pm.response.text());`,
          ],
        }),
        req({
          name: "ST-SEC-010c Send own S_A with foreign driver 22 -> byte-identical 404 (SEC-018)",
          method: "POST",
          path: "/api/v1/routes/{{routeACompliantId}}/send",
          bearerTokenVar: "plannerAToken",
          body: { driver_user_id: 22, solution_id: 101 },
          tests: [
            ...assertError(404, "0040402", "Driver"),
            `pm.test("Byte-identical to missing driver", () => pm.expect(pm.response.text()).to.eql(pm.environment.get("missingDriverResp")));`,
          ],
        }),
        req({
          name: "ST-SEC-010d GET /routes/{R_B}/sends -> 404 0040402",
          method: "GET",
          path: "/api/v1/routes/{{routeBId}}/sends",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "routes"),
        }),
        req({
          name: "ST-SEC-010e Driver A1 reading Driver A2 sendId -> 403 0040301",
          method: "GET",
          path: "/api/v1/driver/routes/{{sendA2Id}}",
          bearerTokenVar: "driverA1Token",
          tests: assertError(403, "0040301"),
        }),
        // ST-SEC-011
        req({
          name: "ST-SEC-011a GET /zones excludes Branch B zone",
          method: "GET",
          path: "/api/v1/zones",
          bearerTokenVar: "plannerAToken",
          tests: [
            `pm.test("Branch B zone absent", () => {`,
            `  pm.response.to.have.status(200);`,
            `  const ids = pm.response.json().map(z => z.id);`,
            `  pm.expect(ids).to.not.include(Number(pm.environment.get("zoneBId")));`,
            `});`,
          ],
        }),
        req({
          name: "ST-SEC-011b GET /zones/{Z_B} -> 404 0040402",
          method: "GET",
          path: "/api/v1/zones/{{zoneBId}}",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "zones"),
        }),
        req({
          name: "ST-SEC-011c DELETE /zones/{Z_B} -> 404 0040402",
          method: "DELETE",
          path: "/api/v1/zones/{{zoneBId}}",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "zones"),
        }),
        req({
          name: "ST-SEC-011d GET /zones/{Z_A}/verdict?vehicleId={V_B} -> 404 0040402",
          method: "GET",
          path: "/api/v1/zones/{{zoneACopyId}}/verdict",
          query: [["vehicleId", "{{vehicleBId}}"], ["date", "2027-03-03"]],
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402", "vehicles"),
        }),
        // ST-SEC-012
        req({
          name: "ST-SEC-012a Callback /failed missing X-API-Key -> 401 0040102",
          method: "POST",
          path: "/api/v1/solutions/callbacks/failed",
          body: { job_id: 102, error_message: "test" },
          tests: assertError(401, "0040102"),
        }),
        req({
          name: "ST-SEC-012b Callback /failed wrong X-API-Key -> 401 filter JSON",
          method: "POST",
          path: "/api/v1/solutions/callbacks/failed",
          headers: [{ key: "X-API-Key", value: "wrong-key" }],
          body: { job_id: 102, error_message: "test" },
          tests: [
            `pm.test("401 filter JSON", () => {`,
            `  pm.response.to.have.status(401);`,
            `  pm.expect(pm.response.json().message).to.eql("Invalid API key");`,
            `});`,
          ],
        }),
        req({
          name: "ST-SEC-012c Callback /failed with Planner JWT (no X-API-Key) -> 403",
          method: "POST",
          path: "/api/v1/solutions/callbacks/failed",
          bearerTokenVar: "plannerAToken",
          body: { job_id: 102, error_message: "test" },
          tests: assertError(403, "0040301"),
        }),
        // ST-SEC-013
        req({
          name: "ST-SEC-013 Callback payload with foreign Branch B order/vehicle -> 422 0040001",
          method: "POST",
          path: "/api/v1/solutions/callbacks/complete",
          headers: [{ key: "X-API-Key", value: "{{engineCallbackKey}}" }],
          body: completeCallbackPayload(102, 201, 201),
          tests: assertError(422, "0040001"),
        }),
        // ST-SEC-014
        req({
          name: "ST-SEC-014a Engine GET /health without X-Engine-Key -> 200 UP",
          method: "GET",
          base: "engineBaseUrl",
          path: "/api/v1/optimization/health",
          tests: [
            `pm.test("Health 200 UP", () => {`,
            `  pm.response.to.have.status(200);`,
            `  pm.expect(pm.response.json().status).to.eql("UP");`,
            `});`,
          ],
        }),
        req({
          name: "ST-SEC-014b Engine POST /optimization without key -> 401 UNAUTHORIZED",
          method: "POST",
          base: "engineBaseUrl",
          path: "/api/v1/optimization",
          body: {},
          tests: [
            `pm.test("Engine 401", () => {`,
            `  pm.response.to.have.status(401);`,
            `  pm.expect(pm.response.json().status).to.eql("UNAUTHORIZED");`,
            `});`,
          ],
        }),
        req({
          name: "ST-SEC-014c Engine POST /legs wrong key -> 401 UNAUTHORIZED",
          method: "POST",
          base: "engineBaseUrl",
          path: "/api/v1/optimization/legs",
          headers: [{ key: "X-Engine-Key", value: "wrong-engine-key" }],
          body: { points: [{ latitude: 21.02, longitude: 105.85 }] },
          tests: [
            `pm.test("Engine 401", () => {`,
            `  pm.response.to.have.status(401);`,
            `  pm.expect(pm.response.json().status).to.eql("UNAUTHORIZED");`,
            `});`,
          ],
        }),
      ],
    },

    // =========================================================================
    // 02 - RES (Resilience, Concurrency & Error Contracts)
    // =========================================================================
    {
      name: "02 - RES (Resilience, Concurrency & Error Contracts)",
      item: [
        // ST-RES-009
        req({
          name: "ST-RES-009a Plan with 12 ON_ROUTE orders -> 409 0040901 (10 codes + (+2 more))",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerAToken",
          body: {
            order_ids: [111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122],
            vehicle_ids: [102],
            preferences: { goal: "MINIMIZE_COST", speed: "FAST" },
          },
          tests: [
            ...assertError(409, "0040901", "orders"),
            `pm.test("Truncated message with (+2 more)", () => {`,
            `  const e = pm.response.json().errors[0];`,
            `  pm.expect(e.message).to.include("(+2 more)");`,
            `  pm.expect(e.field).to.include("ST-ORD-ONR-12");`,
            `});`,
          ],
        }),
        req({
          name: "ST-RES-009b Plan with MAINTENANCE vehicle 103 -> 409 0040901",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerAToken",
          body: { order_ids: [101], vehicle_ids: [103], preferences: { goal: "MINIMIZE_COST", speed: "FAST" } },
          tests: assertError(409, "0040901", "vehicles"),
        }),
        req({
          name: "ST-RES-009c Replace rules on LINKED zone 101 -> 409 0040901",
          method: "PUT",
          path: "/api/v1/zones/{{zoneALinkedId}}/rules",
          bearerTokenVar: "plannerAToken",
          body: [
            {
              selector: { energy_types: ["DIESEL"] },
              schedule: { type: "ALWAYS_FORBIDDEN" },
              effective_from: "2026-01-01",
            },
          ],
          tests: assertError(409, "0040901"),
        }),
        req({
          name: "ST-RES-009d Cancel COMPLETED job 101 -> 409 0040901",
          method: "PUT",
          path: "/api/v1/jobs",
          bearerTokenVar: "plannerAToken",
          body: { ids: [101] },
          tests: assertError(409, "0040901", "jobs"),
        }),
        req({
          name: "ST-RES-009e Cancel PENDING job 104 -> 409 0040901",
          method: "PUT",
          path: "/api/v1/jobs",
          bearerTokenVar: "plannerAToken",
          body: { ids: [104] },
          tests: assertError(409, "0040901", "jobs"),
        }),
        req({
          name: "ST-RES-009f Link already LINKED template 101 -> 409 0040901",
          method: "POST",
          path: "/api/v1/zones",
          bearerTokenVar: "plannerAToken",
          body: { template_id: 101, mode: "LINKED" },
          tests: assertError(409, "0040901"),
        }),
        req({
          name: "ST-RES-009g Rename LINKED zone 101 -> 409 0040901",
          method: "PATCH",
          path: "/api/v1/zones/{{zoneALinkedId}}",
          bearerTokenVar: "plannerAToken",
          body: { name: "Cannot Rename Linked" },
          tests: assertError(409, "0040901"),
        }),
        req({
          name: "ST-RES-009h Delete permit-referenced zone 103 -> 409 0040901",
          method: "DELETE",
          path: "/api/v1/zones/{{zoneAPermitRefId}}",
          bearerTokenVar: "plannerAToken",
          tests: assertError(409, "0040901"),
        }),
        // ST-RES-010
        req({
          name: "ST-RES-010a Malformed JSON -> 400 0040001 sanitized (SEC-019)",
          method: "POST",
          path: "/api/v1/orders",
          bearerTokenVar: "plannerAToken",
          rawBody: "{bad-json",
          tests: [
            ...assertError(400, "0040001"),
            `pm.test("No Jackson internals leaked", () => {`,
            `  const txt = pm.response.text();`,
            `  pm.expect(pm.response.json().errors[0].message).to.eql("Malformed request body.");`,
            `  pm.expect(txt.toLowerCase()).to.not.include("jackson");`,
            `});`,
          ],
        }),
        req({
          name: "ST-RES-010b Wrong path type /orders/abc-secret -> 400 0040005 without echo",
          method: "GET",
          path: "/api/v1/orders/abc-secret",
          bearerTokenVar: "plannerAToken",
          tests: [
            ...assertError(400, "0040005"),
            `pm.test("Rejected value never echoed", () => pm.expect(pm.response.text()).to.not.include("abc-secret"));`,
          ],
        }),
        req({
          name: "ST-RES-010c DELETE /vehicle-types/101 -> 405 0040501",
          method: "DELETE",
          path: "/api/v1/vehicle-types/{{vehicleTypeAId}}",
          bearerTokenVar: "plannerAToken",
          tests: assertError(405, "0040501"),
        }),
        req({
          name: "ST-RES-010d Unsupported Content-Type text/plain -> 415 0041501",
          method: "POST",
          path: "/api/v1/orders",
          headers: [{ key: "Content-Type", value: "text/plain" }],
          bearerTokenVar: "plannerAToken",
          rawBody: "hello",
          tests: assertError(415, "0041501"),
        }),
        req({
          name: "ST-RES-010e Unknown route /api/v1/does-not-exist -> 404 0040402",
          method: "GET",
          path: "/api/v1/does-not-exist",
          bearerTokenVar: "plannerAToken",
          tests: assertError(404, "0040402"),
        }),
        req({
          name: "ST-RES-010f Accept: text/plain -> 406 empty body",
          method: "GET",
          path: "/api/v1/orders",
          headers: [{ key: "Accept", value: "text/plain" }],
          bearerTokenVar: "plannerAToken",
          tests: [
            `pm.test("406 empty body", () => {`,
            `  pm.response.to.have.status(406);`,
            `  pm.expect(pm.response.text()).to.eql("");`,
            `});`,
          ],
        }),
        req({
          name: "ST-RES-010g Null element in ids[] -> 400 0040001 Bean Validation",
          method: "PATCH",
          path: "/api/v1/orders/status",
          bearerTokenVar: "plannerAToken",
          body: { ids: [null], status: "REJECTED" },
          tests: assertError(400, "0040001", null, "ids[0]"),
        }),
        // ST-RES-011
        req({
          name: "ST-RES-011a Batch cancel [102 PROCESSING, 101 COMPLETED] -> 409 rollback",
          method: "PUT",
          path: "/api/v1/jobs",
          bearerTokenVar: "plannerAToken",
          body: { ids: [102, 101] },
          tests: assertError(409, "0040901", "jobs"),
        }),
        req({
          name: "ST-RES-011b Verify job 102 stayed PROCESSING after rollback",
          method: "GET",
          path: "/api/v1/jobs/{{jobAProcessingId}}",
          bearerTokenVar: "plannerAToken",
          tests: [
            `pm.test("Job 102 still PROCESSING", () => {`,
            `  pm.response.to.have.status(200);`,
            `  pm.expect(pm.response.json().status).to.eql("PROCESSING");`,
            `});`,
          ],
        }),
        // ST-RES-013
        req({
          name: "ST-RES-013a Callback /progress (no-op stub) -> 200",
          method: "POST",
          path: "/api/v1/solutions/callbacks/progress",
          headers: [{ key: "X-API-Key", value: "{{engineCallbackKey}}" }],
          body: { job_id: 102, progress: 50, current_phase: "SOLVING" },
          tests: [`pm.test("200", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "ST-RES-013b Duplicate /complete on COMPLETED job 101 -> 200 idempotent",
          method: "POST",
          path: "/api/v1/solutions/callbacks/complete",
          headers: [{ key: "X-API-Key", value: "{{engineCallbackKey}}" }],
          body: completeCallbackPayload(101, 101, 102),
          tests: [`pm.test("200 idempotent", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "ST-RES-013c /complete on PENDING job 104 -> 503 0050001",
          method: "POST",
          path: "/api/v1/solutions/callbacks/complete",
          headers: [{ key: "X-API-Key", value: "{{engineCallbackKey}}" }],
          body: completeCallbackPayload(104, 101, 102),
          tests: assertError(503, "0050001"),
        }),
        req({
          name: "ST-RES-013d /complete on CANCELLED job 107 -> 200 ignored",
          method: "POST",
          path: "/api/v1/solutions/callbacks/complete",
          headers: [{ key: "X-API-Key", value: "{{engineCallbackKey}}" }],
          body: completeCallbackPayload(107, 101, 102),
          tests: [`pm.test("200 ignored for cancelled", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "ST-RES-013e /complete transitioning COMPLETED order 123 -> 400 0040902",
          method: "POST",
          path: "/api/v1/solutions/callbacks/complete",
          headers: [{ key: "X-API-Key", value: "{{engineCallbackKey}}" }],
          body: completeCallbackPayload(102, 123, 102),
          tests: assertError(400, "0040902", "orders", "status"),
        }),
        req({
          name: "ST-RES-013f /failed on unknown job 999999999 -> 404 0040402",
          method: "POST",
          path: "/api/v1/solutions/callbacks/failed",
          headers: [{ key: "X-API-Key", value: "{{engineCallbackKey}}" }],
          body: { job_id: 999999999, error_message: "unknown" },
          tests: assertError(404, "0040402", "jobs"),
        }),
        req({
          name: "ST-RES-013g Engine GET /999999999/progress -> 404",
          method: "GET",
          base: "engineBaseUrl",
          path: "/api/v1/optimization/{{unknownId}}/progress",
          headers: [{ key: "X-Engine-Key", value: "{{engineInboundKey}}" }],
          tests: [`pm.test("Engine 404", () => pm.response.to.have.status(404));`],
        }),
        // ST-RES-014
        req({
          name: "ST-RES-014a Null emissionFactor in VehicleType -> 400 0040001 features.emissionFactor",
          method: "POST",
          path: "/api/v1/vehicle-types",
          bearerTokenVar: "plannerAToken",
          body: {
            type_name: "ST-Bad-Null-EF",
            capacity: 1000,
            vehicle_features: { skills: [] },
          },
          tests: assertError(400, "0040001", null, "features.emissionFactor"),
        }),
        req({
          name: "ST-RES-014b Half time window on Order create -> 400 0040001",
          method: "POST",
          path: "/api/v1/orders",
          bearerTokenVar: "plannerAToken",
          body: {
            order_code: "ST-BAD-TW",
            customer_name: "Test",
            address: "1 Example St",
            latitude: 21.03,
            longitude: 105.85,
            demand: 10,
            time_window_start: "08:00:00",
            delivery_date: "{{todayDate}}",
          },
          tests: assertError(400, "0040001", "orders"),
        }),
        req({
          name: "ST-RES-014c Multi-date plan -> 400 0040001 field=deliveryDate",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerAToken",
          body: {
            order_ids: [101, 103],
            vehicle_ids: [102],
            preferences: { goal: "MINIMIZE_COST", speed: "FAST" },
          },
          tests: assertError(400, "0040001", "orders", "deliveryDate"),
        }),
        req({
          name: "ST-RES-014d Apply DOMINATED Pareto candidate 103 -> 422 0042201",
          method: "POST",
          path: "/api/v1/solutions/{{solutionAId}}/pareto/{{paretoCandADomId}}/apply",
          bearerTokenVar: "plannerAToken",
          tests: assertError(422, "0042201"),
        }),
        req({
          name: "ST-RES-014e Send empty route 102 -> 400 0040001",
          method: "POST",
          path: "/api/v1/routes/{{routeAEmptyId}}/send",
          bearerTokenVar: "plannerAToken",
          body: { driver_user_id: 12, solution_id: 101 },
          tests: assertError(400, "0040001"),
        }),
        req({
          name: "ST-RES-014f Driver GET /driver/routes/999999999 -> 404 0040402",
          method: "GET",
          path: "/api/v1/driver/routes/{{unknownId}}",
          bearerTokenVar: "driverA1Token",
          tests: assertError(404, "0040402"),
        }),
        req({
          name: "ST-RES-014g Invalid GeoJSON Point on PUT /zones/102/geometry -> 400 0040001",
          method: "PUT",
          path: "/api/v1/zones/{{zoneACopyId}}/geometry",
          bearerTokenVar: "plannerAToken",
          body: { type: "Point", coordinates: [105.85, 21.03] },
          tests: assertError(400, "0040001"),
        }),
        // ST-RES-005 (NOTE: ST-RES-013e moved job 102 from PROCESSING -> FAILED, leaving only 104 active.
        // Submit 2 active jobs to reach cap 3, then verify 4th returns 400 0040903 while Branch B still gets 202!)
        req({
          name: "ST-RES-005a Submit 2nd active job in Branch A -> 202",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerAToken",
          body: {
            order_ids: [101, 102],
            vehicle_ids: [102],
            preferences: { goal: "MINIMIZE_COST", speed: "HIGH_QUALITY", enable_pareto_analysis: true },
          },
          tests: [
            `pm.test("202 ACCEPTED", () => {`,
            `  pm.response.to.have.status(202);`,
            `  pm.environment.set("stRes005Job1", pm.response.json().id);`,
            `});`,
          ],
        }),
        req({
          name: "ST-RES-005b Submit 3rd active job in Branch A -> 202",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerAToken",
          body: {
            order_ids: [101, 102],
            vehicle_ids: [102],
            preferences: { goal: "MINIMIZE_COST", speed: "HIGH_QUALITY", enable_pareto_analysis: true },
          },
          tests: [
            `pm.test("202 ACCEPTED", () => {`,
            `  pm.response.to.have.status(202);`,
            `  pm.environment.set("stRes005Job2", pm.response.json().id);`,
            `});`,
          ],
        }),
        req({
          name: "ST-RES-005c Submit 4th active job in Branch A -> 400 0040903 JOB_LIMIT_EXCEEDED",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerAToken",
          body: { order_ids: [101], vehicle_ids: [102], preferences: { goal: "MINIMIZE_COST", speed: "FAST" } },
          tests: assertError(400, "0040903", "jobs"),
        }),
        req({
          name: "ST-RES-005d Submit 1st active job in Branch B (isolated cap) -> 202",
          method: "POST",
          path: "/api/v1/jobs/plan",
          bearerTokenVar: "plannerBToken",
          body: { order_ids: [201], vehicle_ids: [201], preferences: { goal: "MINIMIZE_COST", speed: "FAST" } },
          tests: [`pm.test("202 ACCEPTED for Branch B", () => pm.response.to.have.status(202));`],
        }),
      ],
    },

    // =========================================================================
    // 03 - LIM (Boundaries, Pagination & Ceilings)
    // =========================================================================
    {
      name: "03 - LIM (Pagination, Input Bounds & Ceilings)",
      item: [
        req({
          name: "ST-LIM-001a GET /vehicles page=-1 -> 400 0040002",
          method: "GET",
          path: "/api/v1/vehicles",
          query: [["page", "-1"], ["size", "10"]],
          bearerTokenVar: "plannerAToken",
          tests: assertError(400, "0040002"),
        }),
        req({
          name: "ST-LIM-001b GET /vehicles size=1001 -> 400 0040002",
          method: "GET",
          path: "/api/v1/vehicles",
          query: [["page", "0"], ["size", "1001"]],
          bearerTokenVar: "plannerAToken",
          tests: assertError(400, "0040002"),
        }),
        req({
          name: "ST-LIM-001c GET /vehicles size=1000 (max ceiling) -> 200",
          method: "GET",
          path: "/api/v1/vehicles",
          query: [["page", "0"], ["size", "1000"]],
          bearerTokenVar: "plannerAToken",
          tests: [`pm.test("200 at max size 1000", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "ST-LIM-002a GET /orders size=10001 -> 400 0040002",
          method: "GET",
          path: "/api/v1/orders",
          query: [["page", "0"], ["size", "10001"]],
          bearerTokenVar: "plannerAToken",
          tests: assertError(400, "0040002"),
        }),
        req({
          name: "ST-LIM-002b GET /orders size=10000 (max ceiling) -> 200",
          method: "GET",
          path: "/api/v1/orders",
          query: [["page", "0"], ["size", "10000"]],
          bearerTokenVar: "plannerAToken",
          tests: [`pm.test("200 at max size 10000", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "ST-LIM-003a GET /jobs limit=0 -> 400 0040002",
          method: "GET",
          path: "/api/v1/jobs",
          query: [["limit", "0"]],
          bearerTokenVar: "plannerAToken",
          tests: assertError(400, "0040002"),
        }),
        req({
          name: "ST-LIM-003b GET /jobs limit=201 -> 400 0040002",
          method: "GET",
          path: "/api/v1/jobs",
          query: [["limit", "201"]],
          bearerTokenVar: "plannerAToken",
          tests: assertError(400, "0040002"),
        }),
        req({
          name: "ST-LIM-003c GET /jobs limit=200 -> 200",
          method: "GET",
          path: "/api/v1/jobs",
          query: [["limit", "200"]],
          bearerTokenVar: "plannerAToken",
          tests: [`pm.test("200 at max limit 200", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "ST-LIM-004 Import with neither file nor textData -> 400 0040002",
          method: "POST",
          path: "/api/v1/orders/import",
          bearerTokenVar: "plannerAToken",
          formdata: [{ key: "deliveryDate", value: "{{todayDate}}", type: "text" }],
          tests: assertError(400, "0040002", null, "file"),
        }),
        req({
          name: "ST-LIM-005a Zone rename > 200 chars -> 400 0040001",
          method: "PATCH",
          path: "/api/v1/zones/{{zoneACopyId}}",
          bearerTokenVar: "plannerAToken",
          body: { name: "Z".repeat(201) },
          tests: assertError(400, "0040001"),
        }),
        req({
          name: "ST-LIM-005b GET /lez/order-zones with 201 IDs -> 400 0040001",
          method: "GET",
          path: "/api/v1/lez/order-zones",
          query: [["orderIds", Array.from({ length: 201 }, (_, i) => i + 1).join(",")]],
          bearerTokenVar: "plannerAToken",
          tests: assertError(400, "0040001"),
        }),
        req({
          name: "ST-LIM-006 Engine POST /optimization max_iterations=20001 -> 400 REJECTED",
          method: "POST",
          base: "engineBaseUrl",
          path: "/api/v1/optimization",
          headers: [{ key: "X-Engine-Key", value: "{{engineInboundKey}}" }],
          body: {
            job_id: 99001,
            depots: [{ id: 1, name: "D1", latitude: 21.0285, longitude: 105.8542 }],
            orders: [{ id: 1, order_code: "O1", latitude: 21.025, longitude: 105.855, demand: 10 }],
            vehicle_types: [{ id: 1, type_name: "V1", capacity: 100 }],
            vehicles: [{ id: 1, vehicle_license_plate: "P1", vehicle_type_id: 1, start_depot_id: 1, end_depot_id: 1 }],
            config: { max_iterations: 20001, num_threads: 1 },
          },
          tests: [
            `pm.test("400 REJECTED", () => {`,
            `  pm.response.to.have.status(400);`,
            `  pm.expect(pm.response.json().status).to.eql("REJECTED");`,
            `});`,
          ],
        }),
        req({
          name: "ST-LIM-007 Engine POST /legs lat=95 -> 400 REJECTED",
          method: "POST",
          base: "engineBaseUrl",
          path: "/api/v1/optimization/legs",
          headers: [{ key: "X-Engine-Key", value: "{{engineInboundKey}}" }],
          body: {
            points: [
              { latitude: 95.0, longitude: 105.85 },
              { latitude: 21.03, longitude: 105.86 },
            ],
          },
          tests: [
            `pm.test("400 REJECTED", () => {`,
            `  pm.response.to.have.status(400);`,
            `  pm.expect(pm.response.json().status).to.eql("REJECTED");`,
            `});`,
          ],
        }),
        req({
          name: "ST-LIM-009 Blank order_code with phone -> 400 without echoing phone",
          method: "POST",
          path: "/api/v1/orders",
          bearerTokenVar: "plannerAToken",
          body: {
            order_code: "",
            customer_name: "Test",
            customer_phone: "0912345678",
            address: "1 Example St",
            latitude: 21.03,
            longitude: 105.85,
            demand: 10,
            delivery_date: "{{todayDate}}",
          },
          tests: [
            ...assertError(400, "0040001", null, "orderCode"),
            `pm.test("Customer phone PII never echoed", () => pm.expect(pm.response.text()).to.not.include("0912345678"));`,
          ],
        }),
        req({
          name: "ST-LIM-010a Create ST-ORD-DUP-01 in Branch B succeeds (branch-scoped unique) -> 200",
          method: "POST",
          path: "/api/v1/orders",
          bearerTokenVar: "plannerBToken",
          body: {
            order_code: "ST-ORD-DUP-01",
            customer_name: "Branch B Copy",
            address: "10 Example St",
            latitude: 21.04,
            longitude: 105.83,
            demand: 10,
            delivery_date: "{{todayDate}}",
          },
          tests: [`pm.test("200 in Branch B", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "ST-LIM-010b Import existing ST-ORD-DUP-01 in Branch A (overwrite=false) -> skipped_count=1",
          method: "POST",
          path: "/api/v1/orders/import",
          bearerTokenVar: "plannerAToken",
          formdata: [
            {
              key: "file",
              type: "file",
              src: "tools/system-test/postman/dup-order-sample.csv",
            },
            { key: "deliveryDate", value: "{{todayDate}}", type: "text" },
            { key: "overwriteExisting", value: "false", type: "text" },
          ],
          tests: [
            `pm.test("200 skipped_count=1, errors=[]", () => {`,
            `  pm.response.to.have.status(200);`,
            `  const b = pm.response.json();`,
            `  pm.expect(b.skipped_count).to.eql(1);`,
            `  pm.expect(b.imported_count).to.eql(0);`,
            `});`,
          ],
        }),
        req({
          name: "ST-LIM-010c Single POST /orders duplicate ST-ORD-DUP-01 -> documents known defect 500",
          method: "POST",
          path: "/api/v1/orders",
          bearerTokenVar: "plannerAToken",
          body: {
            order_code: "ST-ORD-DUP-01",
            customer_name: "Dup A",
            address: "10 Example St",
            latitude: 21.025,
            longitude: 105.855,
            demand: 10,
            delivery_date: "{{todayDate}}",
          },
          tests: [
            `pm.test("Known defect TC-D03-008: currently 500 0050001 (or 409 if fixed)", () => {`,
            `  pm.expect([500, 409]).to.include(pm.response.code);`,
            `});`,
          ],
        }),
      ],
    },

    // =========================================================================
    // 04 - E2E API (Positive DS-V4 Checks, LEZ Send Gate, Supersession)
    // =========================================================================
    {
      name: "04 - E2E API (Main Paths, LEZ Send Gate & Supersession)",
      item: [
        req({
          name: "DS-V4: GET /api/v1/fleets/{fleetAId} -> 200 FleetDTO",
          method: "GET",
          path: "/api/v1/fleets/{{fleetAId}}",
          bearerTokenVar: "plannerAToken",
          tests: [`pm.test("200 FleetDTO", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "DS-V4: GET /api/v1/orders/{orderA1Id} -> 200 OrderDTO",
          method: "GET",
          path: "/api/v1/orders/{{orderA1Id}}",
          bearerTokenVar: "plannerAToken",
          tests: [`pm.test("200 OrderDTO", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "DS-V4: GET /api/v1/zone-templates/{zoneTemplate1Id} -> 200",
          method: "GET",
          path: "/api/v1/zone-templates/{{zoneTemplate1Id}}",
          bearerTokenVar: "plannerAToken",
          tests: [`pm.test("200 ZoneTemplateDTO", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "DS-V4: GET /api/v1/routes/{routeACompliantId}/geometry -> 200",
          method: "GET",
          path: "/api/v1/routes/{{routeACompliantId}}/geometry",
          bearerTokenVar: "plannerAToken",
          tests: [`pm.test("200 RouteGeometryDTO", () => pm.response.to.have.status(200));`],
        }),
        req({
          name: "DS-V4: GET /api/v1/zones/{zoneACopyId}/verdict -> 200",
          method: "GET",
          path: "/api/v1/zones/{{zoneACopyId}}/verdict",
          query: [["vehicleId", "{{vehicleA1Id}}"], ["date", "2027-03-03"]],
          bearerTokenVar: "plannerAToken",
          tests: [`pm.test("200 ZoneVerdict", () => pm.response.to.have.status(200));`],
        }),
        // ST-E2E-009
        req({
          name: "ST-E2E-009 Send LEZ-violating route 103 (diesel inside Zone 102) -> 422 0042201",
          method: "POST",
          path: "/api/v1/routes/{{routeALezViolId}}/send",
          bearerTokenVar: "plannerAToken",
          body: { driver_user_id: 12, solution_id: 101 },
          tests: assertError(422, "0042201"),
        }),
        // ST-E2E-010
        req({
          name: "ST-E2E-010a Send compliant route 101 to Driver A1 -> 200 CURRENT",
          method: "POST",
          path: "/api/v1/routes/{{routeACompliantId}}/send",
          bearerTokenVar: "plannerAToken",
          body: { driver_user_id: 12, solution_id: 101 },
          tests: [
            `pm.test("200 CURRENT", () => {`,
            `  pm.response.to.have.status(200);`,
            `  pm.expect(pm.response.json().status).to.eql("CURRENT");`,
            `});`,
          ],
        }),
        req({
          name: "ST-E2E-010b Re-send compliant route 101 to Driver A2 -> 200 CURRENT",
          method: "POST",
          path: "/api/v1/routes/{{routeACompliantId}}/send",
          bearerTokenVar: "plannerAToken",
          body: { driver_user_id: 13, solution_id: 101 },
          tests: [
            `pm.test("200 CURRENT", () => {`,
            `  pm.response.to.have.status(200);`,
            `  pm.expect(pm.response.json().status).to.eql("CURRENT");`,
            `});`,
          ],
        }),
        req({
          name: "ST-E2E-010c GET /routes/{routeACompliantId}/sends -> newest CURRENT, prior SUPERSEDED",
          method: "GET",
          path: "/api/v1/routes/{{routeACompliantId}}/sends",
          bearerTokenVar: "plannerAToken",
          tests: [
            `pm.test("Newest send is CURRENT, prior is SUPERSEDED", () => {`,
            `  pm.response.to.have.status(200);`,
            `  const list = pm.response.json();`,
            `  pm.expect(list.length).to.be.at.least(2);`,
            `  pm.expect(list[0].status).to.eql("CURRENT");`,
            `  pm.expect(list[1].status).to.eql("SUPERSEDED");`,
            `});`,
          ],
        }),
      ],
    },
  ],
};

fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
fs.writeFileSync(OUT_FILE, JSON.stringify(collection, null, 2) + "\n", "utf8");
const totalRequests = collection.item.reduce((sum, f) => sum + f.item.length, 0);
console.log(`Generated ${OUT_FILE} (${collection.item.length} folders, ${totalRequests} requests).`);
