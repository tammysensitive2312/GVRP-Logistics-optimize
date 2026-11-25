import { test, expect } from '@playwright/test';
import { reseedDatabase } from '../helpers/db';
import { getTestEnv } from '../helpers/env';
import { loginAs, setOrdersPageSize } from '../helpers/auth';

test.describe('Resilience, Conflicts & Defect Probes (ST-RES-006, ST-RES-012, ST-RES-002)', () => {
  test.beforeEach(() => {
    reseedDatabase();
  });

  test('ST-RES-006: Optimistic concurrency on manual revision save rejects stale base with 409 and UI reload prompt', async ({
    browser,
  }) => {
    const env = getTestEnv();

    // First, create a clean 2-order solution S_root in context1
    const context1 = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
    const context2 = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
    const page1 = await context1.newPage();
    const page2 = await context2.newPage();

    try {
      await loginAs(page1, env.branchA, env.plannerA);
      await setOrdersPageSize(page1, '50');

      await page1.locator('input[aria-label="Select order ST-ORD-A1"]').check();
      await page1.locator('input[aria-label="Select order ST-ORD-DUP-01"]').check();
      await page1
        .locator('#vehicles-list .vehicle-item-compact', { hasText: 'ST-29A-1002' })
        .locator('input[type="checkbox"]')
        .check();

      await page1.locator('button.btn-plan-routes').click();
      const planDialog = page1.locator('app-route-planning-dialog');
      await planDialog
        .locator('mat-form-field', { hasText: 'Optimization Speed' })
        .locator('mat-select')
        .click();
      await page1.locator('mat-option:has-text("Fast")').click();

      const [planRes] = await Promise.all([
        page1.waitForResponse(
          (r) => r.url().includes('/api/v1/jobs/plan') && r.request().method() === 'POST',
        ),
        planDialog.locator('button:has-text("Start Optimization")').click(),
      ]);
      expect(planRes.status()).toBe(202);

      const monitor = page1.locator('app-job-monitoring-dialog');
      const viewResultBtn = monitor.locator('button:has-text("View Result")');
      await expect(viewResultBtn).toBeVisible({ timeout: 60_000 });
      await viewResultBtn.click({ force: true });

      // Read S_root id from Route tab revision chain or API
      const [evalP1] = await Promise.all([
        page1.waitForResponse(
          (r) =>
            /\/api\/v1\/solutions\/\d+\/evaluate$/.test(r.url()) && r.request().method() === 'POST',
        ),
        page1.locator('button[aria-label="Customize routes"]').click(),
      ]);
      expect(evalP1.status()).toBe(200);
      const sRootMatch = evalP1.url().match(/\/solutions\/(\d+)\/evaluate/);
      expect(sRootMatch).not.toBeNull();
      const sRootId = Number(sRootMatch![1]);

      // Log in page2 and open the same S_root via deep link /?solution=<sRootId>
      await loginAs(page2, env.branchA, env.plannerA);
      await page2.goto(`/?solution=${sRootId}`);
      await expect(page2).toHaveURL(/\/main/);
      await page2.locator('[role="tablist"] [role="tab"]:has-text("Route")').click();
      await expect(page2.locator('app-route-view')).toBeVisible();

      const [evalP2] = await Promise.all([
        page2.waitForResponse(
          (r) =>
            /\/api\/v1\/solutions\/\d+\/evaluate$/.test(r.url()) && r.request().method() === 'POST',
        ),
        page2.locator('button[aria-label="Customize routes"]').click(),
      ]);
      expect(evalP2.status()).toBe(200);

      // Tab 1 saves Revision R1 (succeeds 200)
      const saveBtn1 = page1.locator('section.edit-session button:has-text("Save revision")');
      await expect(saveBtn1).toBeEnabled();
      const [rev1Res] = await Promise.all([
        page1.waitForResponse(
          (r) =>
            /\/api\/v1\/solutions\/\d+\/revisions$/.test(r.url()) &&
            r.request().method() === 'POST',
        ),
        saveBtn1.click(),
      ]);
      expect(rev1Res.status()).toBe(200);
      const rev1Body = await rev1Res.json();

      // Tab 2 saves Revision R2 anchored on stale S_root -> 409 Conflict + Load latest prompt
      const saveBtn2 = page2.locator('section.edit-session button:has-text("Save revision")');
      await expect(saveBtn2).toBeEnabled();
      const [rev2Res] = await Promise.all([
        page2.waitForResponse(
          (r) =>
            /\/api\/v1\/solutions\/\d+\/revisions$/.test(r.url()) &&
            r.request().method() === 'POST',
        ),
        saveBtn2.click(),
      ]);
      expect(rev2Res.status()).toBe(409);

      const conflictAlert = page2.locator('section.edit-session p.edit-error[role="alert"]');
      await expect(conflictAlert).toBeVisible();
      await expect(conflictAlert).toContainText(String(rev1Body.id));
      const loadLatestBtn = conflictAlert.locator('button:has-text("Load latest")');
      await expect(loadLatestBtn).toBeVisible();

      // Click Load latest -> fetches R1 and exits edit mode
      const [loadLatestRes] = await Promise.all([
        page2.waitForResponse(
          (r) =>
            new RegExp(`/api/v1/solutions/${rev1Body.id}$`).test(r.url()) &&
            r.request().method() === 'GET',
        ),
        loadLatestBtn.click(),
      ]);
      expect(loadLatestRes.status()).toBe(200);
      await expect(page2.locator('section.edit-session')).toBeHidden();
    } finally {
      await context1.close();
      await context2.close();
    }
  });

  test('ST-RES-012: Pareto candidate apply 404 0040402 through real errorInterceptor (defect probe)', async ({
    page,
  }) => {
    // Active defect probe: pareto-view.component.ts:490 reads error.error.errors[0],
    // while errorInterceptor wraps HttpErrorResponse into { message, status, originalError }.
    test.fail(
      true,
      'Confirmed bug ST-RES-012: errorInterceptor hides error.error.errors[0] from pareto-view.component.ts:490, showing generic "Could not apply the plan." instead of "Plan not found".',
    );

    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);

    // Load seeded solution 101 which has FRONTIER candidate 102 ("Balanced", engine_selected=0)
    await page.goto('/?solution=101');
    await expect(page).toHaveURL(/\/main/);
    await page.locator('[role="tablist"] [role="tab"]:has-text("Route")').click();
    await expect(page.locator('app-route-view')).toBeVisible();

    const paretoTab = page.locator('[role="tablist"] [role="tab"]:has-text("Pareto")');
    await expect(paretoTab).toBeVisible();
    await paretoTab.click();

    const paretoView = page.locator('app-pareto-view');
    await expect(paretoView).toBeVisible();

    const candidateRow = paretoView.locator('.candidate-row[data-candidate-id="102"]');
    await expect(candidateRow).toBeVisible();
    await candidateRow.click();

    const applyBtn = paretoView.locator('button.btn-apply');
    await expect(applyBtn).toBeEnabled();

    // Intercept apply endpoint with Entry API's 404 0040402 ErrorResponse shape
    await page.route('**/api/v1/solutions/*/pareto/*/apply', async (route) => {
      await route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Resource not found.',
          errors: [{ code: '0040402', message: 'Candidate not found' }],
        }),
      });
    });

    await applyBtn.click();
    const confirmDialog = page.locator('app-confirm-dialog');
    await expect(confirmDialog).toBeVisible();
    await confirmDialog.locator('button:has-text("Apply")').click();

    // Expected per design: "Plan not found" toast (fails today due to errorInterceptor wrapper!)
    await expect(page.locator('.mat-mdc-snack-bar-container.toast-error')).toContainText(
      'Plan not found',
    );
  });

  test('ST-RES-002: Engine unreachable degradation on progress 204, evaluate (502), and send (502 fail-closed)', async ({
    page,
  }) => {
    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);

    // Part A: Progress poll 204 resolves final job state and resumes polling if still PROCESSING
    let progressCalls = 0;
    let jobByIdCalls = 0;
    await page.route('**/api/v1/jobs/plan', async (route) => {
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 9991,
          branch_id: 1,
          status: 'PROCESSING',
          progress: 20,
          created_at: new Date().toISOString(),
        }),
      });
    });
    await page.route('**/api/v1/jobs/9991', async (route) => {
      jobByIdCalls++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 9991,
          branch_id: 1,
          status: 'PROCESSING',
          progress: 20,
          created_at: new Date().toISOString(),
        }),
      });
    });
    await page.route('**/api/v1/jobs/9991/progress', async (route) => {
      progressCalls++;
      await route.fulfill({ status: 204, body: '' });
    });

    await setOrdersPageSize(page, '50');
    await page.locator('input[aria-label="Select order ST-ORD-A1"]').check();
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
    await expect(monitor).toBeVisible();

    await expect.poll(() => progressCalls, { timeout: 10_000 }).toBeGreaterThanOrEqual(1);
    await expect.poll(() => jobByIdCalls, { timeout: 10_000 }).toBeGreaterThanOrEqual(1);

    await monitor.locator('button:has-text("Close")').click();
    await page.unroute('**/api/v1/jobs/plan');
    await page.unroute('**/api/v1/jobs/9991');
    await page.unroute('**/api/v1/jobs/9991/progress');

    // Part B & C: Load solution 101 -> /evaluate 502 keeps edits in UI; /send 502 fail-closed toast
    await page.goto('/?solution=101');
    await expect(page).toHaveURL(/\/main/);
    await page.locator('[role="tablist"] [role="tab"]:has-text("Route")').click();
    await expect(page.locator('app-route-view')).toBeVisible();

    await page.route('**/api/v1/solutions/101/evaluate', async (route) => {
      await route.fulfill({
        status: 502,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Bad Gateway',
          errors: [{ code: '0050002', message: 'Evaluation service unreachable.' }],
        }),
      });
    });

    await page.locator('button[aria-label="Customize routes"]').click();
    const editError = page.locator('section.edit-session p.edit-error[role="alert"]');
    await expect(editError).toBeVisible();
    await expect(editError).toContainText('Evaluation service is unreachable');
    await expect(editError).toContainText('Your edits are kept');

    await page.locator('section.edit-session button:has-text("Discard")').click();
    await expect(page.locator('section.edit-session')).toBeHidden();

    // Part C: Recheck compliance on solution 101 so Send is enabled on compliant EV route 101,
    // then POST /routes/*/send returns 502 fail-closed -> error toast, 0 sends persisted
    await Promise.all([
      page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/solutions/101/compliance/recheck') &&
          r.request().method() === 'POST',
      ),
      page.locator('section.compliance-panel button.btn-recheck').click(),
    ]);

    await page.route('**/api/v1/routes/*/send', async (route) => {
      await route.fulfill({
        status: 502,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Bad Gateway',
          errors: [{ code: '0050002', message: 'Routing service is unavailable.' }],
        }),
      });
    });

    const sendBtn = page.locator('button[aria-label^="Send "]').first();
    await expect(sendBtn).toBeEnabled();
    await sendBtn.click();
    const sendDialog = page.locator('app-send-route-dialog');
    await expect(sendDialog).toBeVisible();
    await sendDialog.locator('mat-select[aria-label="Driver"]').click();
    await page.locator('mat-option:has-text("Driver One Branch A")').click();
    await sendDialog.locator('button:has-text("Send")').click();

    await expect(page.locator('.mat-mdc-snack-bar-container.toast-error')).toContainText(
      'Routing service is unavailable',
    );
  });

  test('ST-RES-002 (5xx poll probe): Progress 502 triggers pollingDegraded after >=2 failures and stops after 5', async ({
    page,
  }) => {
    // Active defect probe: errorInterceptor uses retry({ count: 1, resetOnSuccess: true })
    // on HttpEvent streams; HttpEventType.Sent counts as a "success" and resets the retry
    // counter before the 502 response arrives, causing an infinite 1s retry loop on GET 5xx
    // so JobPollingService.catchError / pollingDegraded / stop-after-5 never fire.
    test.fail(
      true,
      'Confirmed bug ST-RES-002 (5xx poll): error.interceptor.ts:8-17 retry({ count: 1, resetOnSuccess: true }) resets on HttpEventType.Sent, causing infinite 1s retry on GET 502 so pollingDegraded never appears.',
    );

    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);

    await page.route('**/api/v1/jobs/plan', async (route) => {
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 9992,
          branch_id: 1,
          status: 'PROCESSING',
          progress: 20,
          created_at: new Date().toISOString(),
        }),
      });
    });
    await page.route('**/api/v1/jobs/9992/progress', async (route) => {
      await route.fulfill({
        status: 502,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Bad Gateway',
          errors: [{ code: '0050002', message: 'Optimization engine is unavailable.' }],
        }),
      });
    });

    await setOrdersPageSize(page, '50');
    await page.locator('input[aria-label="Select order ST-ORD-A1"]').check();
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
    await expect(monitor).toBeVisible();

    // Expected per design: pollingDegraded banner appears after >= 2 failures
    await expect(monitor.locator('.polling-warning')).toBeVisible({ timeout: 15_000 });
  });
});
