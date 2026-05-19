import { defineConfig, devices } from '@playwright/test'

const usePreview = process.env.E2E_USE_PREVIEW === '1'
const previewPort = 4173
const devPort = 5173
const baseURL = process.env.E2E_BASE_URL || (usePreview ? `http://localhost:${previewPort}` : `http://localhost:${devPort}`)

export default defineConfig({
  testDir: '.',
  testMatch: ['e2e/**/*.spec.ts', 'tests/e2e/**/*.spec.ts'],
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html']] : [['list'], ['html']],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    viewport: { width: 1440, height: 900 },
  },
  webServer: usePreview
    ? {
        command: 'npm run preview -- --host 127.0.0.1 --port 4173',
        port: previewPort,
        timeout: 120_000,
        reuseExistingServer: !process.env.CI,
      }
    : {
        command: 'npm run dev -- --host 127.0.0.1 --port 5173',
        port: devPort,
        timeout: 120_000,
        reuseExistingServer: !process.env.CI,
      },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'mobile-smoke',
      testMatch: ['tests/e2e/**/*.spec.ts'],
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 390, height: 844 },
      },
    },
  ],
})
