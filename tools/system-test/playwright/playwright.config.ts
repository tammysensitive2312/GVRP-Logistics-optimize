import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  expect: {
    timeout: 15_000,
  },
  use: {
    baseURL: 'http://localhost:4200',
    viewport: { width: 1440, height: 1100 },
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },
  reporter: [
    ['list'],
    ['json', { outputFile: '../reports/playwright-report.json' }],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
  ],
});
