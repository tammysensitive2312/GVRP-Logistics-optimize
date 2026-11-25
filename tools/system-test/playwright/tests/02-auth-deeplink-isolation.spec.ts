import { test, expect } from '@playwright/test';
import { reseedDatabase, execTestSql } from '../helpers/db';
import { getTestEnv } from '../helpers/env';
import { loginAs, logoutFromNavbar, setOrdersPageSize } from '../helpers/auth';

test.describe('Auth, Deep-Link & Session Isolation (ST-E2E-011, ST-SEC-004, ST-SEC-009)', () => {
  test.beforeEach(() => {
    reseedDatabase();
  });

  test('ST-E2E-011: anonymous /?solution=101 redirects through /login and opens solution on /main', async ({
    page,
  }) => {
    const env = getTestEnv();

    // 1. Visit deep link while logged out -> authGuard stores returnUrl & redirects to /login
    await page.goto('/?solution=101');
    await expect(page).toHaveURL(/\/login/);

    // 2. Log in as planner.a -> returns to /?solution=101 -> loads solution 101 -> /main
    await page.locator('input[formcontrolname="branch_name"]').fill(env.branchA);
    await page.locator('input[formcontrolname="username"]').fill(env.plannerA);
    await page.locator('input[formcontrolname="password"]').fill(env.testPassword);

    const [loginRes, solRes] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/auth/login') && r.request().method() === 'POST',
      ),
      page.waitForResponse(
        (r) => r.url().includes('/api/v1/solutions/101') && r.request().method() === 'GET',
      ),
      page.locator('button.btn-login[type="submit"]').click(),
    ]);

    expect(loginRes.status()).toBe(200);
    expect(solRes.status()).toBe(200);

    await expect(page).toHaveURL(/\/main/);
    await expect(page.locator('.mat-mdc-snack-bar-container.toast-success')).toBeVisible();

    // SolutionLinkComponent loads SolutionStore and enables Route tab
    const routeTab = page.locator('[role="tablist"] [role="tab"]:has-text("Route")');
    await expect(routeTab).not.toHaveAttribute('aria-disabled', 'true');
    await routeTab.click();
    await expect(page.locator('app-route-view')).toBeVisible();
    await expect(page.locator('app-route-view')).toContainText('ST-29A-1002');
  });

  test('ST-SEC-009 (UI): cross-branch deep link /?solution=201 returns 404 toast with zero Branch B data', async ({
    page,
  }) => {
    const env = getTestEnv();
    await loginAs(page, env.branchA, env.plannerA);

    const solPromise = page.waitForResponse(
      (r) => r.url().includes('/api/v1/solutions/201') && r.request().method() === 'GET',
    );
    await page.goto('/?solution=201');
    const solRes = await solPromise;
    expect(solRes.status()).toBe(404);

    await expect(page).toHaveURL(/\/main/);
    await expect(page.locator('.mat-mdc-snack-bar-container.toast-error')).toBeVisible();

    const bodyText = await page.locator('body').innerText();
    expect(bodyText).not.toContain('SECRET-BRANCH-B-ORD');
    expect(bodyText).not.toContain('ST-29B-2001');
    expect(bodyText).not.toContain('ST-Depot-B1');
  });

  test('ST-SEC-004: 401 token revocation auto-logout + SessionReset isolation across branch logins', async ({
    page,
  }) => {
    const env = getTestEnv();

    // 1. Log in as planner.a and load Branch A solution 101 so stores hold Branch A data
    await loginAs(page, env.branchA, env.plannerA);
    await expect(page.locator('#depots-list')).toContainText('ST-Depot-A1');
    await setOrdersPageSize(page, '50');
    await expect(page.locator('table.orders-table')).toContainText('ST-ORD-A1');

    // Load solution 101 via direct deep link while authenticated
    await page.goto('/?solution=101');
    await expect(page).toHaveURL(/\/main/);
    await page.locator('[role="tablist"] [role="tab"]:has-text("Route")').click();
    await expect(page.locator('app-route-view')).toContainText('ST-29A-1001');
    await page.waitForLoadState('networkidle');

    // 2. Disable planner.a in DB so the next API request gets 401 UNAUTHORIZED
    execTestSql('UPDATE users SET enabled = 0 WHERE id = 11;');

    // Switch back to Orders tab and change status filter -> 401 -> auto logout
    const res401Promise = page.waitForResponse(
      (r) => r.url().includes('/api/v1/') && r.status() === 401,
    );
    await page.locator('[role="tablist"] [role="tab"]:has-text("Orders")').click().catch(() => {});
    await page
      .locator('select[formcontrolname="status"]')
      .selectOption('SCHEDULED')
      .catch(() => {});
    const orders401 = await res401Promise;
    expect(orders401.status()).toBe(401);

    await expect(page).toHaveURL(/\/login/);

    // 3. Log in as planner.b (ST-BRANCH-B) in the same SPA session
    await loginAs(page, env.branchB, env.plannerB);
    await expect(page).toHaveURL(/\/main/);

    // Verify Branch B data is shown and zero cached Branch A depots/orders/solutions leak
    await expect(page.locator('#depots-list')).toContainText('ST-Depot-B1');
    await expect(page.locator('#vehicles-list')).toContainText('ST-29B-2001');
    await expect(page.locator('table.orders-table')).toContainText('SECRET-BRANCH-B-ORD');

    const bodyText = await page.locator('body').innerText();
    expect(bodyText).not.toContain('ST-Depot-A1');
    expect(bodyText).not.toContain('ST-29A-1001');
    expect(bodyText).not.toContain('ST-ORD-A1');

    // Route tab should be locked (solution cleared by SessionResetService)
    const routeTab = page.locator('[role="tablist"] [role="tab"]:has-text("Route")');
    await expect(routeTab).toHaveAttribute('aria-disabled', 'true');

    await logoutFromNavbar(page);
  });
});
