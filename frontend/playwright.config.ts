import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  use: { baseURL: `http://127.0.0.1:${process.env.FPA_FRONTEND_PORT || '5173'}`, trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: { command: `npm run dev -- --port ${process.env.FPA_FRONTEND_PORT || '5173'}`, url: `http://127.0.0.1:${process.env.FPA_FRONTEND_PORT || '5173'}`, reuseExistingServer: !process.env.CI },
})
