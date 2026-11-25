import { test, expect } from '@playwright/test';
import { reseedDatabase, execTestSql } from '../helpers/db';
import { getTestEnv } from '../helpers/env';
import { loginAs, logoutFromNavbar } from '../helpers/auth';

test.describe('ST-E2E-001: New-branch onboarding wizard via setupGuard', () => {
  test.beforeAll(() => {
    reseedDatabase();
  });

  test('redirects fresh branch planner through depot -> vehicle-types -> fleet -> /main', async ({
    page,
  }) => {
    const env = getTestEnv();

    // Log in as planner.new on ST-BRANCH-NEW (0 depots, 0 vehicle types, 0 fleets)
    await loginAs(page, env.branchNew, env.plannerNew, env.testPassword, {
      expectedUrlRegex: /\/setup\/depot/,
    });

    // Explicitly try navigating to /main and verify setupGuard pushes back to /setup/depot
    await page.goto('/main');
    await expect(page).toHaveURL(/\/setup\/depot/);

    // Step 1: /setup/depot — fill name, click map picker, ensure address, submit
    await page.locator('#depot-name').fill('ST-Depot-1');
    const mapPicker = page.locator('app-location-picker .location-picker');
    await mapPicker.waitFor({ state: 'visible' });
    const box = await mapPicker.boundingBox();
    expect(box).not.toBeNull();
    await page.mouse.click(box!.x + box!.width / 2, box!.y + box!.height / 2);

    // Wait for lat/lng inputs to become populated from map pick
    await expect(page.locator('#depot-lat')).not.toHaveValue('');
    await expect(page.locator('#depot-lng')).not.toHaveValue('');

    const addrInput = page.locator('#depot-address');
    if ((await addrInput.inputValue()).trim() === '') {
      await addrInput.fill('1 Example Street, Ha Noi');
    }

    const [depotRes] = await Promise.all([
      page.waitForResponse(
        (res) => res.url().includes('/api/v1/depots') && res.request().method() === 'POST',
      ),
      page.locator('button.btn-primary[type="submit"]').click(),
    ]);
    expect(depotRes.status()).toBe(201);

    // Setup guard / component routes to /setup/vehicle-types
    await expect(page).toHaveURL(/\/setup\/vehicle-types/);

    // Verify setupGuard stops at /setup/vehicle-types when navigating to /main
    await page.goto('/main');
    await expect(page).toHaveURL(/\/setup\/vehicle-types/);

    // Step 2: /setup/vehicle-types — add ST-Van-1t
    await page.locator('#vtype-name').fill('ST-Van-1t');
    await page.locator('#vtype-capacity').fill('1000');
    await page.locator('#vtype-fixed-cost').fill('200000');
    await page.locator('#vtype-cost-per-km').fill('5000');
    await page.locator('#vtype-cost-per-hour').fill('50000');
    await page.locator('#vtype-emission-factor').fill('123.456');

    const [vtypeRes] = await Promise.all([
      page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/vehicle-types') && res.request().method() === 'POST',
      ),
      page.locator('button.btn-primary[type="submit"]').click(),
    ]);
    expect(vtypeRes.status()).toBe(201);

    // Card renders and Continue button enables
    await expect(page.locator('.vehicle-type-card')).toContainText('ST-Van-1t');
    const continueBtn = page.locator('button.btn-success');
    await expect(continueBtn).toBeEnabled();
    await continueBtn.click();

    await expect(page).toHaveURL(/\/setup\/fleet/);

    // Step 3: /setup/fleet — fill fleet & vehicle (plate unique across branches)
    await page.locator('#fleet-name').fill('ST-Fleet-1');
    await page.locator('#vehicle-plate-0').fill('ST-29N-1001');

    const typeSelect = page.locator('#vehicle-type-0');
    await expect(typeSelect.locator('option')).toHaveCount(2);
    await typeSelect.selectOption({ index: 1 });

    const startSelect = page.locator('#vehicle-start-0');
    await expect(startSelect.locator('option')).toHaveCount(2);
    await startSelect.selectOption({ index: 1 });

    const endSelect = page.locator('#vehicle-end-0');
    await expect(endSelect.locator('option')).toHaveCount(2);
    await endSelect.selectOption({ index: 1 });

    const [fleetRes] = await Promise.all([
      page.waitForResponse(
        (res) => res.url().includes('/api/v1/fleets') && res.request().method() === 'POST',
      ),
      page.locator('button.btn-primary[type="submit"]').click(),
    ]);
    expect(fleetRes.status()).toBe(201);

    // Setup complete -> dashboard /main renders with the new depot and vehicle
    await expect(page).toHaveURL(/\/main/);
    await expect(page.locator('#depots-list')).toContainText('ST-Depot-1');
    await expect(page.locator('#vehicles-list')).toContainText('ST-29N-1001');

    // Verify DB state under ST-BRANCH-NEW (branch_id = 3)
    const dbCounts = execTestSql(
      "SELECT (SELECT COUNT(*) FROM depots WHERE branch_id=3), (SELECT COUNT(*) FROM vehicle_types WHERE branch_id=3), (SELECT COUNT(*) FROM fleets WHERE branch_id=3), (SELECT COUNT(*) FROM vehicles v JOIN fleets f ON v.fleet_id=f.id WHERE f.branch_id=3);",
    ).trim();
    expect(dbCounts).toBe('1\t1\t1\t1');

    await logoutFromNavbar(page);
  });
});
