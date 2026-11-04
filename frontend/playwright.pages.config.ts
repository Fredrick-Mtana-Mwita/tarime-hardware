import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './e2e-pages', testMatch: 'client-preview.spec.ts',
  use: { baseURL: process.env.PAGES_PREVIEW_URL || 'http://127.0.0.1:15174/tarime-hardware/', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
