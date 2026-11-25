import { test, expect } from '@playwright/test';
import { reseedDatabase } from '../helpers/db';
import { getTestEnv } from '../helpers/env';
import { loginAs, setOrdersPageSize } from '../helpers/auth';

test.describe('Admin Master Data & LEZ Lifecycle (ST-E2E-013, ST-E2E-016, ST-E2E-008)', () => {
  test.beforeEach(() => {
    reseedDatabase();
  });

  test('ST-E2E-013: Admin depots, vehicle types LEZ attrs + Use suggestion, vehicles & permits CRUD', async ({
    page,
  }) => {
    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);

    // 1. /admin/depots — verify cards and section note
    await page.goto('/admin/depots');
    await expect(page.locator('.items-grid > article.item-card')).toContainText('ST-Depot-A1');
    await expect(page.locator('.section-note')).toBeVisible();

    // 2. /admin/vehicle-types — search, edit LEZ attrs, Use suggestion, save
    await page.goto('/admin/vehicle-types');
    await page.locator('#search-vehicle-types').fill('ST-Van-A1');
    await expect(page.locator('.items-grid > article.item-card')).toHaveCount(1);

    await page.locator('button[aria-label="Edit ST-Van-A1"]').click();
    const vtypeDialog = page.locator('app-vehicle-type-dialog');
    await expect(vtypeDialog).toBeVisible();

    const suggestionBtn = vtypeDialog.locator('button.link-button:has-text("Use suggestion")');
    if (await suggestionBtn.isVisible()) {
      await suggestionBtn.click();
    } else {
      await vtypeDialog.locator('#admin-vtype-legal-class').selectOption({ label: 'TRUCK' });
    }

    await vtypeDialog.locator('#admin-vtype-gross-weight').fill('7500');
    await vtypeDialog.locator('#admin-vtype-energy-type').selectOption({ label: 'DIESEL' });
    await vtypeDialog.locator('#admin-vtype-emission-standard').fill('5');

    const [putTypeRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/vehicle-types\/\d+$/.test(r.url()) && r.request().method() === 'PUT',
      ),
      vtypeDialog.locator('button:has-text("Save")').click(),
    ]);
    expect(putTypeRes.status()).toBe(200);
    await expect(vtypeDialog).toBeHidden();

    // Verify GET /api/v1/fleets/101 and PUT /api/v1/vehicles/103 via authenticated request
    const token = await page.evaluate(() => {
      const raw = localStorage.getItem('gvrp_auth_session') || localStorage.getItem('auth_session');
      if (raw) {
        try {
          return JSON.parse(raw).token as string;
        } catch {
          return null;
        }
      }
      return null;
    });

    if (token) {
      const fleetRes = await page.request.get(`${env.entryBaseUrl}/api/v1/fleets/101`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(fleetRes.status()).toBe(200);

      const vehPutRes = await page.request.put(`${env.entryBaseUrl}/api/v1/vehicles/103`, {
        headers: { Authorization: `Bearer ${token}` },
        data: {
          vehicle_license_plate: 'ST-29A-1003',
          vehicle_type_id: 101,
          start_depot_id: 101,
          end_depot_id: 101,
          status: 'MAINTENANCE',
        },
      });
      expect(vehPutRes.status()).toBe(200);
    }

    // 3. /admin/vehicles — search plate, open VehiclePermitsDialog, CRUD permit P-ST-01
    await page.goto('/admin/vehicles');
    await page.locator('#search-vehicles').fill('ST-29A-1001');
    await expect(page.locator('table.data-table tbody tr')).toHaveCount(1);

    await page.locator('button[aria-label="Permits for ST-29A-1001"]').click();
    const permitsDialog = page.locator('app-vehicle-permits-dialog');
    await expect(permitsDialog).toBeVisible();
    await expect(permitsDialog.locator('table.data-table')).toContainText('ST-PRM-101');

    // Create permit P-ST-01
    await permitsDialog.locator('button:has-text("+ Add permit")').click();
    await permitsDialog.locator('#permit-zone').selectOption({ label: 'ST-Zone-A-Copy' });
    await permitsDialog.locator('#permit-no').fill('P-ST-01');
    await permitsDialog.locator('#permit-from').fill('2026-01-01');
    await permitsDialog.locator('#permit-to').fill('2028-12-31');
    await permitsDialog.locator('#permit-note').fill('Admin lifecycle test permit');

    const [createPermitRes] = await Promise.all([
      page.waitForResponse(
        (r) =>
          /\/api\/v1\/vehicles\/\d+\/permits$/.test(r.url()) && r.request().method() === 'POST',
      ),
      permitsDialog.locator('button:has-text("Save permit")').click(),
    ]);
    expect(createPermitRes.status()).toBe(201);
    await expect(permitsDialog.locator('table.data-table')).toContainText('P-ST-01');

    // Edit P-ST-01 schedule to 08:00-12:00
    const pRow = permitsDialog.locator('table.data-table tbody tr', { hasText: 'P-ST-01' });
    await pRow.locator('button:has-text("Edit")').click();
    await permitsDialog.locator('label.checkbox > input[type="checkbox"]').uncheck();
    if ((await permitsDialog.locator('input[aria-label="Interval 1 start"]').count()) === 0) {
      await permitsDialog.locator('button:has-text("+ Add interval")').click();
    }
    await permitsDialog.locator('input[aria-label="Interval 1 start"]').fill('08:00');
    await permitsDialog.locator('input[aria-label="Interval 1 end"]').fill('12:00');

    const [updatePermitRes] = await Promise.all([
      page.waitForResponse(
        (r) =>
          /\/api\/v1\/vehicles\/\d+\/permits\/\d+$/.test(r.url()) && r.request().method() === 'PUT',
      ),
      permitsDialog.locator('button:has-text("Save permit")').click(),
    ]);
    expect(updatePermitRes.status()).toBe(200);
    await expect(permitsDialog.locator('table.data-table')).toContainText('08:00');

    // Delete P-ST-01
    await permitsDialog.locator('button[aria-label="Delete permit P-ST-01"]').click();
    const confirmDialog = page.locator('app-confirm-dialog');
    await expect(confirmDialog).toBeVisible();

    const [delPermitRes] = await Promise.all([
      page.waitForResponse(
        (r) =>
          /\/api\/v1\/vehicles\/\d+\/permits\/\d+$/.test(r.url()) &&
          r.request().method() === 'DELETE',
      ),
      confirmDialog.locator('button:has-text("Delete")').click(),
    ]);
    expect(delPermitRes.status()).toBe(204);
    await expect(permitsDialog.locator('table.data-table')).not.toContainText('P-ST-01');
  });

  test('ST-E2E-016: LEZ template preview, enable/disable toggle + DEPOT_IN_ZONE warning, rename, acknowledge, delete', async ({
    page,
  }) => {
    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);

    await page.goto('/admin/zones');
    await expect(page.locator('h1:has-text("Low-emission zones")')).toBeVisible();

    // 1. Preview system template ST-TPL-HN-INNER
    const tplRow = page.locator('section.zone-section', { hasText: 'Zone templates' }).locator('tbody tr', {
      hasText: 'ST-TPL-HN-INNER',
    });
    const [tplPreviewRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/zone-templates\/\d+$/.test(r.url()) && r.request().method() === 'GET',
      ),
      tplRow.locator('button:has-text("Preview")').click(),
    ]);
    expect(tplPreviewRes.status()).toBe(200);
    await expect(page.locator('.preview-map .section-title')).toContainText('ST Hanoi Inner Zone');

    // 2. Copy ST-TPL-HN-RING to create an unreferenced zone we can delete later
    const ringRow = page
      .locator('section.zone-section', { hasText: 'Zone templates' })
      .locator('tbody tr', { hasText: 'ST-TPL-HN-RING' });
    const [copyZoneRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/zones') && r.request().method() === 'POST',
      ),
      ringRow.locator('button:has-text("Copy")').click(),
    ]);
    expect(copyZoneRes.status()).toBe(201);

    // 3. Edit ST-Zone-A-Copy: rename to "Hanoi Inner Copy" & set polygon covering ST-Depot-A1 (105.8542, 21.0285)
    const copyRow = page
      .locator('section.zone-section', { hasText: 'My zones' })
      .locator('tbody tr', { hasText: 'ST-Zone-A-Copy' });
    await copyRow.locator('button:has-text("Edit")').click();

    const editor = page.locator('app-zone-editor-dialog');
    await expect(editor).toBeVisible();

    await editor.locator('input.name-input[aria-label="Zone name"]').fill('Hanoi Inner Copy');

    const depotCoveringPolygon = {
      type: 'Polygon',
      coordinates: [
        [
          [105.84, 21.02],
          [105.86, 21.02],
          [105.86, 21.04],
          [105.84, 21.04],
          [105.84, 21.02],
        ],
      ],
    };
    await editor
      .locator('textarea.geometry-text[aria-label="GeoJSON geometry"]')
      .fill(JSON.stringify(depotCoveringPolygon));
    await editor.locator('button:has-text("Preview geometry")').click();

    const [saveRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/zones\/\d+\/rules$/.test(r.url()) && r.request().method() === 'PUT',
      ),
      editor.locator('button:has-text("Save zone")').click(),
    ]);
    expect(saveRes.status()).toBe(200);
    await expect(editor).toBeHidden();

    // 4. Toggle enabled=false then true -> verify DEPOT_IN_ZONE warning and /lez/order-zones exclusion
    const renamedRow = page
      .locator('section.zone-section', { hasText: 'My zones' })
      .locator('tbody tr', { hasText: 'Hanoi Inner Copy' });
    await expect(renamedRow).toBeVisible();

    const toggleCheckbox = renamedRow.locator('input[aria-label="Enable Hanoi Inner Copy"]');
    const [disableRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/zones\/\d+$/.test(r.url()) && r.request().method() === 'PATCH',
      ),
      toggleCheckbox.uncheck(),
    ]);
    expect(disableRes.status()).toBe(200);

    // Re-enable -> backend returns warnings: [DEPOT_IN_ZONE]
    const [enableRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/zones\/\d+$/.test(r.url()) && r.request().method() === 'PATCH',
      ),
      toggleCheckbox.check(),
    ]);
    expect(enableRes.status()).toBe(200);
    const enableBody = await enableRes.json();
    expect(enableBody.warnings?.some((w: { code?: string }) => w.code === 'DEPOT_IN_ZONE')).toBe(
      true,
    );

    // Preview the zone to see the depot-in-zone warning banner in UI
    await renamedRow.locator('button:has-text("Preview")').click();
    await expect(page.locator('.warnings-banner')).toContainText('ST-Depot-A1');

    // 5. Acknowledge template update on Hanoi Inner Copy (v1 -> v2)
    const ackBtn = renamedRow.locator('button.link-button:has-text("Template updated — acknowledge")');
    await expect(ackBtn).toBeVisible();
    await ackBtn.click();

    const confirmDialog = page.locator('app-confirm-dialog');
    await expect(confirmDialog).toBeVisible();
    const [ackRes] = await Promise.all([
      page.waitForResponse(
        (r) =>
          /\/api\/v1\/zones\/\d+\/acknowledge$/.test(r.url()) && r.request().method() === 'POST',
      ),
      confirmDialog.locator('button:has-text("Acknowledge")').click(),
    ]);
    expect(ackRes.status()).toBe(200);
    await expect(renamedRow).toContainText('Up to date');

    // 6. Delete the unreferenced copied zone (ST Hanoi Ring Zone)
    const delRow = page
      .locator('section.zone-section', { hasText: 'My zones' })
      .locator('tbody tr', { hasText: 'ST Hanoi Ring Zone' });
    await expect(delRow).toBeVisible();
    await delRow.locator('button[aria-label^="Delete "]').click();
    await expect(confirmDialog).toBeVisible();

    const [delZoneRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/zones\/\d+$/.test(r.url()) && r.request().method() === 'DELETE',
      ),
      confirmDialog.locator('button:has-text("Delete")').click(),
    ]);
    expect(delZoneRes.status()).toBe(204);
    await expect(delRow).toHaveCount(0);

    // Verify /routes/101/geometry returns 200
    const token = await page.evaluate(() => {
      const raw = localStorage.getItem('gvrp_auth_session') || localStorage.getItem('auth_session');
      return raw ? (JSON.parse(raw).token as string) : null;
    });
    if (token) {
      const geomRes = await page.request.get(`${env.entryBaseUrl}/api/v1/routes/101/geometry`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(geomRes.status()).toBe(200);
    }
  });

  test('ST-E2E-008: LEZ templates -> LINKED/COPIED zone -> polygon & rules -> verdict -> order badges -> job & CompliancePanel recheck', async ({
    page,
  }) => {
    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);

    // 1. In /admin/zones, link ST-TPL-HN-RING (not yet linked) and edit ST-Zone-A-Copy
    await page.goto('/admin/zones');
    const ringRow = page
      .locator('section.zone-section', { hasText: 'Zone templates' })
      .locator('tbody tr', { hasText: 'ST-TPL-HN-RING' });
    const [linkRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/zones') && r.request().method() === 'POST',
      ),
      ringRow.locator('button:has-text("Enable")').click(),
    ]);
    expect(linkRes.status()).toBe(201);

    // Edit ST-Zone-A-Copy with polygon covering ST-ORD-A2-INLEZ (105.8450, 21.0350) + DIESEL Euro < 5 rule (06:00-22:00)
    const copyRow = page
      .locator('section.zone-section', { hasText: 'My zones' })
      .locator('tbody tr', { hasText: 'ST-Zone-A-Copy' });
    await copyRow.locator('button:has-text("Edit")').click();

    const editor = page.locator('app-zone-editor-dialog');
    await expect(editor).toBeVisible();

    const lezPolygon = {
      type: 'Polygon',
      coordinates: [
        [
          [105.84, 21.03],
          [105.85, 21.03],
          [105.85, 21.04],
          [105.84, 21.04],
          [105.84, 21.03],
        ],
      ],
    };
    await editor
      .locator('textarea.geometry-text[aria-label="GeoJSON geometry"]')
      .fill(JSON.stringify(lezPolygon));
    await editor.locator('button:has-text("Preview geometry")').click();

    // Update rule 1 schedule to FORBIDDEN_DURING 06:00-22:00
    await editor.locator('#rule-schedule-0').selectOption({ label: 'FORBIDDEN_DURING' });
    const startInput = editor.locator('input[aria-label="Interval 1 start"]');
    if ((await startInput.count()) === 0) {
      await editor.locator('button:has-text("+ Add interval")').click();
    }
    await editor.locator('input[aria-label="Interval 1 start"]').fill('06:00');
    await editor.locator('input[aria-label="Interval 1 end"]').fill('22:00');

    const [putRulesRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/zones\/\d+\/rules$/.test(r.url()) && r.request().method() === 'PUT',
      ),
      editor.locator('button:has-text("Save zone")').click(),
    ]);
    expect(putRulesRes.status()).toBe(200);
    await expect(editor).toBeHidden();

    // Check Verdict for ST-29A-1001 (DIESEL Euro 4) on ST-Zone-A-Copy
    await page.locator('#verdict-zone').selectOption({ label: 'ST-Zone-A-Copy' });
    await page
      .locator('#verdict-vehicle')
      .selectOption({ label: 'ST-29A-1001 (ST-Van-A1)' });
    await page.locator('#verdict-date').fill(env.todayDate);

    const [verdictRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/zones\/\d+\/verdict/.test(r.url()) && r.request().method() === 'GET',
      ),
      page.locator('button:has-text("Check verdict")').click(),
    ]);
    expect(verdictRes.status()).toBe(200);
    await expect(page.locator('.verdict-result')).toContainText('06:00');

    // 2. Open /main Orders tab -> verify LEZ zone badge on ST-ORD-A2-INLEZ
    const [orderZonesRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/lez/order-zones') && r.request().method() === 'GET',
      ),
      page.goto('/main'),
    ]);
    expect(orderZonesRes.status()).toBe(200);
    await setOrdersPageSize(page, '50');

    const lezRow = page.locator('table.orders-table tr', { hasText: 'ST-ORD-A2-INLEZ' });
    await expect(lezRow.locator('.zone-badges .zone-chip').first()).toBeVisible();
    await expect(lezRow.locator('.zone-badges')).toContainText('ST-Zone-A-Copy');

    // 3. Submit a FAST job with ST-ORD-A1, ST-ORD-A2-INLEZ and ST-29A-1001, ST-29A-1002
    await page.locator('input[aria-label="Select order ST-ORD-A1"]').check();
    await page.locator('input[aria-label="Select order ST-ORD-A2-INLEZ"]').check();
    await page
      .locator('#vehicles-list .vehicle-item-compact', { hasText: 'ST-29A-1001' })
      .locator('input[type="checkbox"]')
      .check();
    await page
      .locator('#vehicles-list .vehicle-item-compact', { hasText: 'ST-29A-1002' })
      .locator('input[type="checkbox"]')
      .check();

    await page.locator('button.btn-plan-routes').click();
    const planDialog = page.locator('app-route-planning-dialog');
    await expect(planDialog).toBeVisible();
    await planDialog
      .locator('mat-form-field', { hasText: 'Optimization Speed' })
      .locator('mat-select')
      .click();
    await page.locator('mat-option:has-text("Fast")').click();

    const [planJobRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/jobs/plan') && r.request().method() === 'POST',
      ),
      planDialog.locator('button:has-text("Start Optimization")').click(),
    ]);
    expect(planJobRes.status()).toBe(202);

    const monitor = page.locator('app-job-monitoring-dialog');
    await expect(monitor).toBeVisible();
    const viewResultBtn = monitor.locator('button:has-text("View Result")');
    await expect(viewResultBtn).toBeVisible({ timeout: 60_000 });
    await viewResultBtn.click({ force: true });

    // 4. Inspect CompliancePanel on the completed solution & verify Recheck now
    const compliancePanel = page.locator('section.compliance-panel');
    await expect(compliancePanel).toBeVisible();
    const [recheckRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/compliance/recheck') && r.request().method() === 'POST',
      ),
      compliancePanel.locator('button.btn-recheck').click(),
    ]);
    expect(recheckRes.status()).toBe(200);

    // 5. Open seeded solution 101 (route 103 has diesel ST-29A-1001 entering ST-ORD-A2-INLEZ)
    // and verify Recheck now surfaces violation with human-readable zone name + clickable leg focus
    await page.goto('/?solution=101');
    await page.locator('[role="tablist"] [role="tab"]:has-text("Route")').click();
    await expect(compliancePanel).toBeVisible();
    const [recheck101] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/solutions/101/compliance/recheck') && r.request().method() === 'POST',
      ),
      compliancePanel.locator('button.btn-recheck').click(),
    ]);
    expect(recheck101.status()).toBe(200);

    const violationBtn = compliancePanel.locator('button.violation-link').first();
    await expect(violationBtn).toBeVisible();
    const violationText = await violationBtn.innerText();
    expect(violationText).not.toContain('BRANCH_ZONE:');
    await violationBtn.click();
  });
});
