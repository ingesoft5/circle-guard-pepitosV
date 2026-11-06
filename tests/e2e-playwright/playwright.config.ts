import { defineConfig, devices } from '@playwright/test';

// The environment under test, e.g. http://<dev-host>. Required so a run never hits a
// default environment by mistake.
const baseURL = process.env.E2E_BASE_URL;
if (!baseURL) {
  throw new Error('Set E2E_BASE_URL to the web client of the environment under test');
}

export default defineConfig({
  testDir: './tests',
  outputDir: './results/artifacts',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  forbidOnly: !!process.env.CI,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'results/html', open: 'never' }],
    ['junit', { outputFile: 'results/junit.xml' }],
  ],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
