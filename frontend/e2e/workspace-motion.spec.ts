import { test, expect } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('tarime.staff.inventory-theme', 'dark'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) { await route.continue(); return }
    const body = path === '/api/staff/me' ? { id: 'motion-preview', username: 'preview', name: 'Workspace review', role: 'OWNER', passwordChangeRequired: false }
      : path === '/api/staff/orders' ? { pendingCount: 12, total: 0, items: [], page: 0, size: 5 }
        : path === '/api/staff/inventory' ? { items: [{ variantId: 'test', productName: 'Portland Cement', variantLabel: '50 kg', sku: 'QA-001', category: 'Building Materials', image: '/images/cement-stack.jpg', unit: 'bag', fractional: false, onHand: 120, reserved: 20, available: 100, lowStock: false }], total: 1, lowStockCount: 0, page: 0, size: 12 } : []
    await route.fulfill({ status: path === '/api/auth/me' ? 401 : 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
})

test('live depth, page entrances, circular theme reveal and persistent theme', async ({ page }, info) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/#/staff/overview')
  await expect(page.locator('.staff-shell')).toHaveAttribute('data-theme', 'dark')
  await expect(page.locator('.workspace-stats')).toBeVisible()
  expect(await page.locator('.workspace-page').evaluate(e => getComputedStyle(e).animationName)).toBe('workspace-page-in')
  const card = page.locator('.workspace-stats article').first()
  await card.hover({ position: { x: 30, y: 25 } })
  await expect.poll(() => card.evaluate(e => e.style.getPropertyValue('--tilt-y'))).not.toBe('')
  expect(await card.evaluate(e => getComputedStyle(e).transform)).toContain('matrix3d')
  await page.mouse.move(900, 10)
  await expect.poll(() => card.evaluate(e => e.style.getPropertyValue('--tilt-y'))).toBe('')
  await page.screenshot({ path: info.outputPath('overview-dark.png'), fullPage: true })
  await page.getByRole('button', { name: 'Switch to light theme' }).click()
  await expect(page.locator('html')).toHaveClass(/staff-theme-reveal/)
  await expect(page.locator('html')).not.toHaveClass(/staff-theme-reveal/)
  await expect(page.locator('.staff-shell')).toHaveAttribute('data-theme', 'light')
  await page.screenshot({ path: info.outputPath('overview-light.png'), fullPage: true })
  await page.getByRole('link', { name: 'Inventory', exact: true }).click()
  await expect(page.locator('.inventory-card')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Inventory', exact: true })).toHaveAttribute('aria-current', 'page')
  expect(await page.locator('.staff-nav-indicator').evaluate(e => getComputedStyle(e).opacity)).toBe('1')
  await page.getByRole('button', { name: 'Switch to dark theme' }).click()
  await expect(page.locator('html')).not.toHaveClass(/staff-theme-reveal/)
  await page.getByRole('button', { name: /Receive stock for/ }).click()
  await expect(page.getByLabel(/Quantity received/)).toBeFocused()
  await page.screenshot({ path: info.outputPath('receive-dark.png') })
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: /Receive stock for/ })).toBeFocused()
  await page.reload()
  await expect(page.locator('.staff-shell')).toHaveAttribute('data-theme', 'dark')
  for (const width of [320, 768, 1024]) {
    await page.setViewportSize({ width, height: 900 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: info.outputPath(`inventory-dark-${width}.png`), fullPage: true })
  }
  expect(errors).toEqual([])
})

test('reduced-motion preference keeps the interface still and theme usable', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/#/staff/overview')
  await expect(page.locator('.workspace-stats')).toBeVisible()
  await page.locator('.workspace-stats article').first().hover()
  expect(await page.locator('.workspace-stats article').first().evaluate(e => e.style.getPropertyValue('--tilt-x'))).toBe('')
  expect(await page.evaluate(() => document.getAnimations().filter(animation => animation.playState === 'running').length)).toBe(0)
  await page.getByRole('button', { name: 'Switch to light theme' }).click()
  await expect(page.locator('.staff-shell')).toHaveAttribute('data-theme', 'light')
  await expect(page.locator('html')).not.toHaveClass(/staff-theme-reveal/)
})
