import { expect, test } from '@playwright/test'
import { existsSync, readFileSync } from 'node:fs'
const file = process.env.FPA_STAFF_TEST_FIXTURES || ''
const enabled = !!file && existsSync(file) && process.env.FPA_BACKEND_PORT === '18080' && process.env.FPA_FRONTEND_PORT === '15173'
const fixture = enabled ? JSON.parse(readFileSync(file, 'utf8')) as { inventory: string; password: string } : null
test.skip(!enabled, 'Requires isolated test-only staff fixtures and server ports.')

test('Inventory receives at Mwanza and safely retries a committed receipt after a lost response', async ({ page }, info) => {
  test.setTimeout(90000)
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/#/staff/login')
  await page.getByLabel('Staff username', { exact: true }).fill(fixture!.inventory)
  await page.getByLabel('Password', { exact: true }).fill(fixture!.password)
  const [login] = await Promise.all([page.waitForResponse(r => r.url().endsWith('/api/staff/auth/login')), page.getByRole('button', { name: 'Sign in to staff workspace' }).click()])
  expect(login.status()).toBe(200)
  await expect(page.getByRole('heading', { name: 'Ready for the next build.' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Pending requests', exact: true })).toHaveCount(0)
  await page.getByRole('link', { name: 'Inventory', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Inventory', exact: true })).toBeVisible()
  const result = await page.request.get('/api/staff/inventory?branch=TARIME&q=Receipt%20material&size=1')
  expect(result.status()).toBe(200)
  const original = (await result.json()).items[0] as { sku: string; variantId: string; onHand: number; reserved: number }
  expect(original).toBeTruthy()
  await page.getByRole('combobox', { name: 'Branch', exact: true }).selectOption('MWANZA')
  await page.getByLabel('Find materials').fill(original.sku)
  await page.getByRole('button', { name: 'Search inventory' }).click()
  await expect(page.locator('.inventory-card')).toHaveCount(1)
  const before = (await (await page.request.get('/api/staff/inventory?branch=MWANZA&q=' + original.sku)).json()).items[0]
  await page.getByRole('button', { name: /Receive stock for/ }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(page.getByLabel(/Quantity received/)).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByLabel('Supplier / delivery reference')).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: /Receive stock for/ })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(dialog).toBeVisible()
  await page.setViewportSize({ width: 320, height: 900 })
  expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('receive-dialog-320.png') })
  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(dialog.getByText('Mwanza', { exact: true })).toBeVisible()
  await page.getByLabel(/Quantity received/).fill('2')
  const reference = 'Browser delivery ' + Date.now()
  await page.getByLabel('Supplier / delivery reference').fill(reference)
  await page.getByLabel('Receipt reason', { exact: true }).fill('Materials inspected and accepted at Mwanza')
  let lost = false
  await page.route('**/api/staff/inventory/receipts', async route => {
    if (route.request().method() === 'POST' && !lost) { lost = true; const committed = await route.fetch(); expect(committed.status()).toBe(200); await route.abort('failed') }
    else await route.continue()
  })
  await page.getByRole('button', { name: 'Record stock receipt' }).click()
  await expect(dialog.getByRole('alert')).toContainText('could not reach')
  await expect(page.getByLabel(/Quantity received/)).toBeDisabled()
  await page.getByRole('button', { name: 'Retry this receipt' }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page.locator('.staff-success')).toContainText('Received 2')
  const after = (await (await page.request.get('/api/staff/inventory?branch=MWANZA&q=' + original.sku)).json()).items[0]
  expect(Number(after.onHand)).toBe(Number(before.onHand) + 2)
  expect(Number(after.reserved)).toBe(Number(before.reserved))
  const tarime = (await (await page.request.get('/api/staff/inventory?branch=TARIME&q=' + original.sku)).json()).items[0]
  expect(Number(tarime.onHand)).toBe(Number(original.onHand))
  await page.getByRole('link', { name: 'View receipt history', exact: true }).first().click()
  await expect(page.locator('.receipt-list')).toContainText(reference)
  await expect(page.locator('.receipt-list article').filter({ hasText: reference })).toHaveCount(1)
  for (const width of [1440, 1024, 768, 320]) {
    await page.setViewportSize({ width, height: 900 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: info.outputPath(`receipts-${width}.png`), fullPage: true })
  }
  await page.getByRole('link', { name: 'Inventory', exact: true }).click()
  await expect(page.locator('.inventory-card').first()).toBeVisible()
  for (const width of [1440, 1024, 768, 320]) {
    await page.setViewportSize({ width, height: 900 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: info.outputPath(`inventory-${width}.png`), fullPage: true })
  }
  expect(errors).toEqual([])
})
