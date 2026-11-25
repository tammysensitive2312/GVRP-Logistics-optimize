import { expect, Page } from '@playwright/test';
import { getTestEnv } from './env';

export async function loginAs(
  page: Page,
  branch: string,
  username: string,
  password?: string,
  options?: { expectedUrlRegex?: RegExp },
): Promise<void> {
  const env = getTestEnv();
  const pw = password ?? env.testPassword;

  await page.goto('/login');
  await page.locator('input[formcontrolname="branch_name"]').fill(branch);
  await page.locator('input[formcontrolname="username"]').fill(username);
  await page.locator('input[formcontrolname="password"]').fill(pw);

  await Promise.all([
    page.waitForResponse(
      (res) => res.url().includes('/api/v1/auth/login') && res.request().method() === 'POST',
    ),
    page.locator('button.btn-login[type="submit"]').click(),
  ]);

  const expected = options?.expectedUrlRegex ?? /\/(main|setup\/|driver)/;
  await expect(page).toHaveURL(expected);
}

export async function logoutFromNavbar(page: Page): Promise<void> {
  const menuBtn = page.locator('button[aria-label="Open user menu"]');
  const logoutItem = page.locator('button[role="menuitem"]:has-text("Logout")');
  await menuBtn.waitFor({ state: 'visible' });

  await expect(async () => {
    if (!(await logoutItem.isVisible())) {
      await menuBtn.click();
    }
    await expect(logoutItem).toBeVisible({ timeout: 2_000 });
  }).toPass({ timeout: 15_000 });

  await logoutItem.click();
  await expect(page).toHaveURL(/\/login/);
}

export async function logoutFromDriver(page: Page): Promise<void> {
  const logoutBtn = page.locator('header.driver-header button.btn-logout');
  await logoutBtn.waitFor({ state: 'visible' });
  await logoutBtn.click();
  await expect(page).toHaveURL(/\/login/);
}

export async function setOrdersPageSize(page: Page, size: string = '50'): Promise<void> {
  const select = page.locator('select.paginator-select');
  await select.waitFor({ state: 'visible' });
  await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/v1/orders') && r.request().method() === 'GET',
    ),
    select.selectOption(size),
  ]);
}
