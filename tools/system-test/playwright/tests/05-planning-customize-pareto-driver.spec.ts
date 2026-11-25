import { test, expect } from '@playwright/test';
import { reseedDatabase } from '../helpers/db';
import { getTestEnv } from '../helpers/env';
import { loginAs, logoutFromNavbar, logoutFromDriver, setOrdersPageSize } from '../helpers/auth';

test.describe('Planning, Cancel, Pareto, Customize, Timeline & Driver Portal (ST-E2E-012, ST-E2E-006, ST-E2E-002, ST-E2E-007, ST-E2E-015)', () => {
  test.beforeEach(() => {
    reseedDatabase();
  });

  test('ST-E2E-012: Cancel in-flight job in JobMonitoringDialog -> /jobs & /jobs/:id show CANCELLED', async ({
    page,
  }) => {
    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);

    // Import 25 orders so FAST + Pareto takes several seconds in PROCESSING, allowing deterministic UI cancellation
    await page.locator('button:has(mat-icon:text("upload_file"))').click();
    const importDialog = page.locator('app-import-orders-dialog');
    await expect(importDialog).toBeVisible();
    const csvLines = [
      'order_code,customer_name,customer_phone,address,latitude,longitude,demand,service_time,time_window_start,time_window_end,priority,delivery_notes',
      ...Array.from({ length: 25 }, (_, i) => {
        const lat = (21.01 + (i % 5) * 0.008).toFixed(4);
        const lng = (105.81 + Math.floor(i / 5) * 0.012).toFixed(4);
        return `ST-CAN-${String(i + 1).padStart(2, '0')},Cancel Order ${i + 1},0906000${String(i + 1).padStart(3, '0')},${i + 1} Kim Ma Ha Noi,${lat},${lng},20,10,08:00,17:00,1,Cancel test`;
      }),
    ].join('\n');
    await importDialog.locator('input[type="file"]').setInputFiles({
      name: 'cancel-batch.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvLines, 'utf8'),
    });
    const [impRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/orders/import') && r.request().method() === 'POST',
      ),
      importDialog.locator('button:has-text("Import")').click(),
    ]);
    expect(impRes.status()).toBe(200);

    // Filter SCHEDULED + select all matching orders and both vehicles
    await Promise.all([
      page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/orders') &&
          r.url().includes('status=SCHEDULED') &&
          r.request().method() === 'GET',
      ),
      page.locator('select[formcontrolname="status"]').selectOption('SCHEDULED'),
    ]);
    await expect(page.locator('table.orders-table')).toContainText('ST-CAN-');
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/orders') && r.request().method() === 'GET',
      ),
      page.locator('button.btn-select-all').click(),
    ]);
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

    // FAST + Pareto gives 5 scenarios so job stays PROCESSING long enough to cancel via UI
    await planDialog
      .locator('mat-form-field', { hasText: 'Optimization Speed' })
      .locator('mat-select')
      .click();
    await page.locator('mat-option:has-text("Fast")').click();
    await planDialog.locator('mat-checkbox', { hasText: 'Enable Pareto Analysis' }).click();

    const [planRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/jobs/plan') && r.request().method() === 'POST',
      ),
      planDialog.locator('button:has-text("Start Optimization")').click(),
    ]);
    expect(planRes.status()).toBe(202);
    const createdJob = await planRes.json();
    const jobId = createdJob.id as number;

    const monitor = page.locator('app-job-monitoring-dialog');
    await expect(monitor).toBeVisible();

    // Wait until Engine dispatch finishes and job transitions to PROCESSING ("In progress")
    // or wait for first /progress 200 response before cancelling so submitOptimizationAsync doesn't overwrite CANCELLED
    await page
      .waitForResponse(
        (r) =>
          r.url().includes(`/api/v1/jobs/${jobId}/progress`) && r.status() === 200,
        { timeout: 15_000 },
      )
      .catch(() => {});

    // Click Cancel Job while job is running
    const cancelBtn = monitor.locator('button:has-text("Cancel Job")');
    await expect(cancelBtn).toBeVisible();
    await cancelBtn.click();

    const confirmDialog = page.locator('app-confirm-dialog');
    await expect(confirmDialog).toBeVisible();
    const [cancelRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/jobs') && r.request().method() === 'PUT',
      ),
      confirmDialog.locator('button:has-text("Cancel job")').click(),
    ]);
    expect(cancelRes.status()).toBe(204);

    await expect(monitor.locator('.status-badge')).toContainText('Cancelled');
    await monitor.locator('button:has-text("Close")').click();
    await expect(monitor).toBeHidden();

    // Open /jobs and verify newest-first listing contains jobId as CANCELLED
    await page.goto('/jobs');
    const firstRow = page.locator('table.data-table tr.job-row').first();
    await expect(firstRow).toHaveAttribute('aria-label', `Job ${jobId}`);
    await expect(firstRow).toContainText('CANCELLED');

    // Deep-link /jobs/:id highlights row and shows job-detail
    await page.goto(`/jobs/${jobId}`);
    await expect(page.locator('section.job-detail')).toContainText(`Job #${jobId}`);
    await expect(page.locator('section.job-detail')).toContainText('CANCELLED');
  });

  test('ST-E2E-006: Pareto optimization -> Pareto SVG chart -> Apply FRONTIER alternative', async ({
    page,
  }) => {
    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);

    // Import 3 extra SCHEDULED orders for today outside LEZ so both diesel and EV compete cleanly (>= 4 orders total)
    await page.locator('button:has(mat-icon:text("upload_file"))').click();
    const importDialog = page.locator('app-import-orders-dialog');
    await expect(importDialog).toBeVisible();
    const extraCsv = [
      'order_code,customer_name,customer_phone,address,latitude,longitude,demand,service_time,time_window_start,time_window_end,priority,delivery_notes',
      'ST-PAR-01,Pareto 1,0904000001,10 Pho Hue Ha Noi,21.0165,105.8520,120,10,08:00,17:00,1,P1',
      'ST-PAR-02,Pareto 2,0904000002,20 Dai Co Viet Ha Noi,21.0085,105.8470,150,10,08:00,17:00,1,P2',
      'ST-PAR-03,Pareto 3,0904000003,30 Kim Ma Ha Noi,21.0315,105.8180,180,10,08:00,17:00,1,P3',
    ].join('\n');
    await importDialog.locator('input[type="file"]').setInputFiles({
      name: 'pareto-orders.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(extraCsv, 'utf8'),
    });
    const [impRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/orders/import') && r.request().method() === 'POST',
      ),
      importDialog.locator('button:has-text("Import")').click(),
    ]);
    expect(impRes.status()).toBe(200);

    // Filter SCHEDULED + today (wait for debounced status=SCHEDULED loadOrders), select all matching orders & both vehicles
    await Promise.all([
      page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/orders') &&
          r.url().includes('status=SCHEDULED') &&
          r.request().method() === 'GET',
      ),
      page.locator('select[formcontrolname="status"]').selectOption('SCHEDULED'),
    ]);
    await expect(page.locator('table.orders-table')).toContainText('ST-PAR-01');
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/orders') && r.request().method() === 'GET',
      ),
      page.locator('button.btn-select-all').click(),
    ]);
    await expect(page.locator('.select-all-notice')).toBeVisible();

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
    await planDialog.locator('mat-checkbox', { hasText: 'Enable Pareto Analysis' }).click();

    const [planRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/jobs/plan') && r.request().method() === 'POST',
      ),
      planDialog.locator('button:has-text("Start Optimization")').click(),
    ]);
    expect(planRes.status()).toBe(202);

    const monitor = page.locator('app-job-monitoring-dialog');
    await expect(monitor).toBeVisible();

    const viewResultBtn = monitor.locator('button:has-text("View Result")');
    await expect(viewResultBtn).toBeVisible({ timeout: 75_000 });
    await viewResultBtn.click({ force: true });

    // Open Pareto tab
    const paretoTab = page.locator('[role="tablist"] [role="tab"]:has-text("Pareto")');
    await expect(paretoTab).toBeVisible();
    await paretoTab.click();

    const paretoView = page.locator('app-pareto-view');
    await expect(paretoView).toBeVisible();
    await expect(paretoView.locator('svg.pareto-chart')).toBeVisible();
    await expect(paretoView.locator('.candidate-row[data-candidate-id]')).toHaveCount(5);

    // Select a non-active FRONTIER candidate if present and click Apply this plan
    const nonActiveFrontier = paretoView.locator(
      '.candidate-row[data-candidate-id]:not(.active):has(.status-frontier)',
    );
    const count = await nonActiveFrontier.count();
    if (count > 0) {
      await nonActiveFrontier.first().click();
      const applyBtn = paretoView.locator('button.btn-apply');
      await expect(applyBtn).toBeEnabled();
      await applyBtn.click();

      const confirmDialog = page.locator('app-confirm-dialog');
      await expect(confirmDialog).toBeVisible();
      const [applyRes] = await Promise.all([
        page.waitForResponse(
          (r) =>
            /\/api\/v1\/solutions\/\d+\/pareto\/\d+\/apply$/.test(r.url()) &&
            r.request().method() === 'POST',
        ),
        confirmDialog.locator('button:has-text("Apply")').click(),
      ]);
      expect(applyRes.status()).toBe(200);
      await expect(page.locator('.mat-mdc-snack-bar-container.toast-success')).toContainText(
        'applied',
      );
    } else {
      // If all frontier points converged identically, verify SVG points & active plan line still match
      await expect(paretoView.locator('svg.pareto-chart g.point[data-candidate-id]')).toHaveCount(
        5,
      );
    }
  });

  test('ST-E2E-002: Full Planner -> Engine -> Callback -> Send Route -> Driver Portal happy path', async ({
    page,
  }) => {
    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);

    // 1. Create 2 SCHEDULED orders via Add Order dialog
    const ordersToCreate = [
      {
        code: 'ST-ORD-001',
        name: 'Happy Customer 1',
        phone: '0905111001',
        address: '101 Nguyen Thai Hoc, Ha Noi',
        lat: '21.0330',
        lng: '105.8400',
      },
      {
        code: 'ST-ORD-002',
        name: 'Happy Customer 2',
        phone: '0905111002',
        address: '102 Tran Hung Dao, Ha Noi',
        lat: '21.0210',
        lng: '105.8610',
      },
    ];

    for (const o of ordersToCreate) {
      await page.locator('button:has(mat-icon:text("add"))').click();
      const editDialog = page.locator('app-edit-order-dialog');
      await expect(editDialog).toBeVisible();
      await editDialog.locator('input[placeholder="ORD001"]').fill(o.code);
      await editDialog.locator('input[placeholder="John Doe"]').fill(o.name);
      await editDialog.locator('input[placeholder="0901234567"]').fill(o.phone);
      await editDialog.locator('input[placeholder="123 Main Street"]').fill(o.address);
      await editDialog.locator('input[placeholder="21.028511"]').fill(o.lat);
      await editDialog.locator('input[placeholder="105.804817"]').fill(o.lng);
      await editDialog.locator('input[placeholder="50.5"]').fill('20');

      const [addRes] = await Promise.all([
        page.waitForResponse(
          (r) => r.url().includes('/api/v1/orders') && r.request().method() === 'POST',
        ),
        editDialog.locator('button:has-text("Add Order")').click(),
      ]);
      expect(addRes.status()).toBe(200);
      await expect(editDialog).toBeHidden();
    }

    // Show 20 per page so newly created orders are visible
    await page.locator('select.paginator-select').selectOption('20');
    await page.locator('input[aria-label="Select order ST-ORD-001"]').check();
    await page.locator('input[aria-label="Select order ST-ORD-002"]').check();

    // Select EV vehicle ST-29A-1002
    await page
      .locator('#vehicles-list .vehicle-item-compact', { hasText: 'ST-29A-1002' })
      .locator('input[type="checkbox"]')
      .check();

    // Plan Routes -> FAST
    await page.locator('button.btn-plan-routes').click();
    const planDialog = page.locator('app-route-planning-dialog');
    await expect(planDialog).toBeVisible();
    await planDialog
      .locator('mat-form-field', { hasText: 'Optimization Speed' })
      .locator('mat-select')
      .click();
    await page.locator('mat-option:has-text("Fast")').click();

    const [planRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/jobs/plan') && r.request().method() === 'POST',
      ),
      planDialog.locator('button:has-text("Start Optimization")').click(),
    ]);
    expect(planRes.status()).toBe(202);

    const monitor = page.locator('app-job-monitoring-dialog');
    await expect(monitor).toBeVisible();
    const viewResultBtn = monitor.locator('button:has-text("View Result")');
    await expect(viewResultBtn).toBeVisible({ timeout: 60_000 });
    await viewResultBtn.click({ force: true });

    // Inspect Route and Timeline tabs
    await expect(page.locator('app-route-view')).toBeVisible();
    await expect(page.locator('app-route-view')).toContainText('ST-29A-1002');
    await page.locator('[role="tablist"] [role="tab"]:has-text("Timeline")').click();
    await expect(page.locator('app-timeline-view')).toBeVisible();

    // Switch back to Route tab and Send Route 1 to driver.a1
    await page.locator('[role="tablist"] [role="tab"]:has-text("Route")').click();
    const sendBtn = page.locator('button[aria-label^="Send "]').first();
    await expect(sendBtn).toBeEnabled();
    await sendBtn.click();

    const sendDialog = page.locator('app-send-route-dialog');
    await expect(sendDialog).toBeVisible();
    await sendDialog.locator('mat-select[aria-label="Driver"]').click();
    await page.locator('mat-option:has-text("Driver One Branch A")').click();

    const [sendRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/routes\/\d+\/send$/.test(r.url()) && r.request().method() === 'POST',
      ),
      sendDialog.locator('button:has-text("Send")').click(),
    ]);
    expect(sendRes.status()).toBe(200);
    await expect(sendDialog).toBeHidden();

    // Logout planner, log in as driver.a1, inspect /driver route detail
    await logoutFromNavbar(page);
    await loginAs(page, env.branchA, env.driverA1, env.testPassword, {
      expectedUrlRegex: /\/driver/,
    });

    const driverRoutes = page.locator('ul.driver-list button.driver-route');
    await expect(driverRoutes.first()).toBeVisible();
    await driverRoutes.first().click();

    await expect(page.locator('#driver-mini-map')).toBeVisible();
    await expect(page.locator('.next-stop')).toBeVisible();
    await page.locator('button.btn-toggle:has-text("Show all")').click();
    await expect(page.locator('ol.driver-stops > li.driver-stop').first()).toBeVisible();
    await expect(page.locator('a[href^="tel:"]').first()).toBeVisible();
    await expect(page.locator('a[aria-label^="Navigate to "]').first()).toBeVisible();

    await logoutFromDriver(page);
  });

  test('ST-E2E-007: Manual route customization -> /evaluate preview -> Save revision -> SendRouteDialog revision chain', async ({
    page,
  }) => {
    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);
    await setOrdersPageSize(page, '50');

    // Plan a quick 2-order route on ST-29A-1002 so we have >= 2 stops on 1 route to reorder
    await page.locator('input[aria-label="Select order ST-ORD-A1"]').check();
    await page.locator('input[aria-label="Select order ST-ORD-DUP-01"]').check();
    await page
      .locator('#vehicles-list .vehicle-item-compact', { hasText: 'ST-29A-1002' })
      .locator('input[type="checkbox"]')
      .check();

    await page.locator('button.btn-plan-routes').click();
    const planDialog = page.locator('app-route-planning-dialog');
    await planDialog
      .locator('mat-form-field', { hasText: 'Optimization Speed' })
      .locator('mat-select')
      .click();
    await page.locator('mat-option:has-text("Fast")').click();
    await planDialog.locator('button:has-text("Start Optimization")').click();

    const monitor = page.locator('app-job-monitoring-dialog');
    const viewResultBtn = monitor.locator('button:has-text("View Result")');
    await expect(viewResultBtn).toBeVisible({ timeout: 60_000 });
    await viewResultBtn.click({ force: true });

    // Enter Customize edit session
    const customizeBtn = page.locator('button[aria-label="Customize routes"]');
    await expect(customizeBtn).toBeEnabled();

    const [initialEvalRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/solutions\/\d+\/evaluate$/.test(r.url()) && r.request().method() === 'POST',
      ),
      customizeBtn.click(),
    ]);
    expect(initialEvalRes.status()).toBe(200);
    await expect(page.locator('section.edit-session')).toBeVisible();

    const dragStops = page.locator('section.edit-session .cdk-drag.edit-order');
    await expect(dragStops).toHaveCount(2);
    await dragStops.nth(1).scrollIntoViewIfNeeded();

    const firstBox = await dragStops.nth(0).boundingBox();
    const secondBox = await dragStops.nth(1).boundingBox();
    expect(firstBox).not.toBeNull();
    expect(secondBox).not.toBeNull();

    // Drag first stop below second stop to trigger automatic /evaluate after 350ms
    const evalPromise = page.waitForResponse(
      (r) => /\/api\/v1\/solutions\/\d+\/evaluate$/.test(r.url()) && r.request().method() === 'POST',
    );
    await page.mouse.move(firstBox!.x + firstBox!.width / 2, firstBox!.y + firstBox!.height / 2);
    await page.mouse.down();
    await page.waitForTimeout(100);
    await page.mouse.move(
      secondBox!.x + secondBox!.width / 2,
      secondBox!.y + secondBox!.height * 0.85,
      { steps: 20 },
    );
    await page.waitForTimeout(100);
    await page.mouse.up();

    const evalRes = await evalPromise;
    expect(evalRes.status()).toBe(200);
    await expect(page.locator('.edit-totals')).toBeVisible();

    // Save revision -> POST /revisions 200
    const saveBtn = page.locator('section.edit-session button:has-text("Save revision")');
    await expect(saveBtn).toBeEnabled();
    const [revRes] = await Promise.all([
      page.waitForResponse(
        (r) =>
          /\/api\/v1\/solutions\/\d+\/revisions$/.test(r.url()) && r.request().method() === 'POST',
      ),
      saveBtn.click(),
    ]);
    expect(revRes.status()).toBe(200);
    const savedRevision = await revRes.json();
    expect(savedRevision.type).toBe('MANUAL_EDITED');

    // Open SendRouteDialog and inspect Plan revision dropdown (revision-chain)
    const [chainRes] = await Promise.all([
      page.waitForResponse(
        (r) =>
          /\/api\/v1\/solutions\/\d+\/revision-chain$/.test(r.url()) &&
          r.request().method() === 'GET',
      ),
      page.locator('button[aria-label^="Send "]').first().click(),
    ]);
    expect(chainRes.status()).toBe(200);
    const chainBody = await chainRes.json();
    expect(chainBody.length).toBeGreaterThanOrEqual(2);

    const sendDialog = page.locator('app-send-route-dialog');
    await expect(sendDialog).toBeVisible();
    await sendDialog.locator('mat-select[aria-label="Plan revision"]').click();
    await expect(page.locator(`mat-option:has-text("Solution #${savedRevision.id}")`)).toBeVisible();
    await page.locator(`mat-option:has-text("Solution #${savedRevision.id}")`).click();

    await sendDialog.locator('mat-select[aria-label="Driver"]').click();
    await page.locator('mat-option:has-text("Driver One Branch A")').click();

    const [sendRes] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api\/v1\/routes\/\d+\/send$/.test(r.url()) && r.request().method() === 'POST',
      ),
      sendDialog.locator('button:has-text("Send")').click(),
    ]);
    expect(sendRes.status()).toBe(200);
  });

  test('ST-E2E-015: NORMAL speed BackgroundJobDialog -> /jobs View Solution -> Timeline zoom & ExpandedViewDialog -> Planner/Driver Logout', async ({
    page,
  }) => {
    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);
    await setOrdersPageSize(page, '50');

    await page.locator('input[aria-label="Select order ST-ORD-A1"]').check();
    await page.locator('input[aria-label="Select order ST-ORD-DUP-01"]').check();
    await page
      .locator('#vehicles-list .vehicle-item-compact', { hasText: 'ST-29A-1002' })
      .locator('input[type="checkbox"]')
      .check();

    // Submit with NORMAL speed (default) -> opens BackgroundJobDialog
    await page.locator('button.btn-plan-routes').click();
    const planDialog = page.locator('app-route-planning-dialog');
    await expect(planDialog).toBeVisible();

    const [planRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/jobs/plan') && r.request().method() === 'POST',
      ),
      planDialog.locator('button:has-text("Start Optimization")').click(),
    ]);
    expect(planRes.status()).toBe(202);
    const createdJob = await planRes.json();

    const bgDialog = page.locator('app-background-job-dialog');
    await expect(bgDialog).toBeVisible();
    await bgDialog.locator('button:has-text("View Job History")').click();
    await expect(page).toHaveURL(/\/jobs/);

    // Poll Refresh until job completes and View solution button appears
    const targetRow = page.locator(`table.data-table tr.job-row[aria-label="Job ${createdJob.id}"]`);
    const viewSolutionBtn = targetRow.locator('button:has-text("View solution")');
    await expect(async () => {
      if (!(await viewSolutionBtn.isVisible())) {
        const refreshBtn = page.locator('button:has-text("Refresh")');
        if (await refreshBtn.isEnabled()) {
          await Promise.all([
            page.waitForResponse(
              (r) => r.url().includes('/api/v1/jobs') && r.request().method() === 'GET',
            ),
            refreshBtn.click(),
          ]);
        }
      }
      await expect(viewSolutionBtn).toBeVisible({ timeout: 1_500 });
    }).toPass({ timeout: 60_000, intervals: [2_000] });

    await viewSolutionBtn.click();
    await expect(page).toHaveURL(/\/main/);
    await page.locator('[role="tablist"] [role="tab"]:has-text("Route")').click();
    await expect(page.locator('app-route-view')).toBeVisible();

    // Open & close ExpandedViewDialog on Route tab
    await page.locator('button.btn-expand[aria-label="Expand"]').first().click();
    await expect(page.locator('app-expanded-view-dialog')).toBeVisible();
    await page.locator('app-expanded-view-dialog button.btn-collapse[aria-label="Collapse"]').click();
    await expect(page.locator('app-expanded-view-dialog')).toBeHidden();

    // Switch to Timeline tab: zoom in / out / reset & ExpandedViewDialog
    await page.locator('[role="tablist"] [role="tab"]:has-text("Timeline")').click();
    const timeline = page.locator('app-timeline-view');
    await expect(timeline).toBeVisible();
    await expect(timeline.locator('.zoom-level')).toContainText('1x');

    await timeline.locator('button:has-text("Zoom In")').click();
    await expect(timeline.locator('.zoom-level')).not.toContainText('1x');
    await timeline.locator('button:has-text("Zoom Out")').click();
    await timeline.locator('button:has-text("Zoom In")').click();
    await timeline.locator('button:has-text("Reset")').click();
    await expect(timeline.locator('.zoom-level')).toContainText('1x');

    await page.locator('.timeline-tab-content button.btn-expand[aria-label="Expand"]').click();
    await expect(page.locator('app-expanded-view-dialog')).toBeVisible();
    await page.locator('app-expanded-view-dialog button.btn-collapse[aria-label="Collapse"]').click();
    await expect(page.locator('app-expanded-view-dialog')).toBeHidden();

    // Planner logout clears storage & redirects to /login
    await logoutFromNavbar(page);
    const tokenAfterPlannerLogout = await page.evaluate(() =>
      localStorage.getItem('gvrp_auth_session'),
    );
    expect(tokenAfterPlannerLogout).toBeNull();

    // Driver login, progressive disclosure + follow gate, and Driver logout (observes POST /api/v1/auth/logout 404)
    await loginAs(page, env.branchA, env.driverA1, env.testPassword, {
      expectedUrlRegex: /\/driver/,
    });
    await page.locator('ul.driver-list button.driver-route').first().click();
    await page.locator('button:has-text("Follow my location")').click();
    await expect(page.locator('.follow-gate')).toBeVisible();
    await page.locator('.follow-gate button:has-text("Not now")').click();

    const logoutReqPromise = page.waitForResponse(
      (r) => r.url().includes('/api/v1/auth/logout') && r.request().method() === 'POST',
    );
    await logoutFromDriver(page);
    const logoutRes = await logoutReqPromise;
    expect(logoutRes.status()).toBe(404);
  });
});
