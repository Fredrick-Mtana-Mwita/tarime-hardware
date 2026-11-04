import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { readFileSync, existsSync } from 'node:fs'
import { createHmac, randomUUID } from 'node:crypto'

const file = process.env.FPA_STAFF_TEST_FIXTURES || ''
const enabled = !!file && existsSync(file) && process.env.FPA_BACKEND_PORT === '18080' && process.env.FPA_FRONTEND_PORT === '15173'
const fixtures = enabled ? JSON.parse(readFileSync(file, 'utf8')) as { owner: string; ownerSecret: string; sales: string; inventory: string; delivery: string; password: string; orderId: string; contactName: string } : null
test.skip(!enabled, 'Staff journeys require explicitly configured isolated test fixtures; never use real staff credentials.')
const privatePassword = 'My private browser staff passphrase 2026!'
function totp(base32: string) {
  let bits = 0, buffer = 0; const bytes: number[] = []
  for (const c of base32) { buffer = (buffer << 5) | 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf(c); bits += 5; if (bits >= 8) { bits -= 8; bytes.push((buffer >> bits) & 255) } }
  const counter = Buffer.alloc(8); counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30000)))
  const hash = createHmac('sha1', Buffer.from(bytes)).update(counter).digest(), offset = hash[19] & 15
  return String((hash.readUInt32BE(offset) & 0x7fffffff) % 1000000).padStart(6, '0')
}
async function login(page: Page, username: string, password: string, code = '') {
  await page.goto('/#/staff/login')
  await page.getByLabel('Staff username', { exact: true }).fill(username)
  await page.getByLabel('Password', { exact: true }).fill(password)
  if (code) await page.getByLabel('Authenticator or recovery code', { exact: false }).fill(code)
  const response = page.waitForResponse(r => r.url().endsWith('/api/staff/auth/login') && r.request().method() === 'POST')
  await page.getByRole('button', { name: 'Sign in to staff workspace' }).click()
  expect((await response).status()).toBe(200)
}
async function changePassword(page: Page, current: string) {
  await expect(page.getByRole('heading', { name: 'Choose your private password' })).toBeVisible()
  await page.getByLabel('Current password', { exact: true }).fill(current)
  await page.getByLabel('New password', { exact: true }).fill(privatePassword)
  await page.getByLabel('Repeat new password', { exact: true }).fill(privatePassword)
  const response = page.waitForResponse(r => r.url().endsWith('/api/staff/auth/password') && r.request().method() === 'POST')
  await page.getByRole('button', { name: 'Save private password', exact: true }).click()
  expect((await response).status()).toBe(200)
  await expect(page.getByRole('heading', { name: 'Ready for the next build.', exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Pending requests', exact: true }).click()
}

test('Owner reviews requests, creates named staff and revokes a signed-in role without exposing stock actions', async ({ page, browser }, info) => {
  test.setTimeout(90000)
  const f = fixtures!
  await login(page, f.owner, f.password, totp(f.ownerSecret))
  await expect(page.getByRole('heading', { name: 'Ready for the next build.' })).toBeVisible()
  await expect(page.locator('.workspace-stats')).toBeVisible()
  for (const width of [1440, 320]) {
    await page.setViewportSize({ width, height: 900 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: info.outputPath(`overview-${width}.png`), fullPage: true })
  }
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByRole('link', { name: 'Pending requests', exact: true }).click()
  await page.getByLabel('Find a request', { exact: true }).fill(f.contactName)
  await page.getByRole('button', { name: 'Apply filters' }).click()
  await expect(page.locator('.staff-request-list article')).toHaveCount(1)
  await page.screenshot({ path: info.outputPath('staff-pending-desktop.png'), fullPage: true })
  await page.getByRole('link', { name: 'Review request', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Request details', exact: true })).toBeVisible()
  await expect(page.locator('.contact-details')).toContainText('Private test address')
  await expect(page.getByRole('button', { name: /dispatch/i })).toHaveCount(0)
  await page.screenshot({ path: info.outputPath('staff-review-desktop.png'), fullPage: true })
  await page.getByRole('link', { name: 'Staff accounts', exact: true }).click()
  const username = 'browser-' + randomUUID()
  await page.getByLabel('Full name', { exact: true }).fill('Browser Named Sales')
  await page.getByLabel('Username', { exact: true }).fill(username)
  await page.getByLabel('Temporary password', { exact: true }).fill(f.password)
  const created = page.waitForResponse(r => r.url().endsWith('/api/staff/accounts') && r.request().method() === 'POST')
  await page.getByRole('button', { name: 'Create staff account', exact: true }).click()
  expect((await created).status()).toBe(201)
  await expect(page.locator('.staff-success')).toContainText('Staff account created')
  await page.getByLabel('Find staff by name or username').fill(username)
  const account = page.locator('.staff-account').filter({ hasText: username })
  await expect(account).toHaveCount(1)
  const context = await browser.newContext(), other = await context.newPage()
  await login(other, username, f.password)
  await changePassword(other, f.password)
  // Password changes alter the revision; refresh the Owner's current account projection first.
  await page.reload()
  await page.getByLabel('Find staff by name or username').fill(username)
  await account.getByRole('combobox', { name: 'Role', exact: true }).selectOption('DELIVERY')
  const changed = page.waitForResponse(r => r.url().includes('/api/staff/accounts/') && r.request().method() === 'PUT')
  await account.getByRole('button', { name: 'Save access', exact: true }).click()
  expect((await changed).status()).toBe(200)
  expect((await other.request.get('/api/staff/orders')).status()).toBe(401)
  await login(other, username, privatePassword)
  await expect(other.getByRole('heading', { name: 'Ready for the next build.' })).toBeVisible()
  expect((await other.request.get('/api/staff/orders')).status()).toBe(403)
  await account.getByLabel('Active account', { exact: true }).uncheck()
  const disabled = page.waitForResponse(r => r.url().includes('/api/staff/accounts/') && r.request().method() === 'PUT')
  await account.getByRole('button', { name: 'Save access', exact: true }).click()
  expect((await disabled).status()).toBe(200)
  expect((await other.request.get('/api/staff/me')).status()).toBe(401)
  await context.close()
})

test('Sales changes a temporary password and reviews only pending requests on a phone', async ({ page }, info) => {
  const f = fixtures!
  await page.setViewportSize({ width: 390, height: 844 })
  await login(page, f.sales, f.password)
  await changePassword(page, f.password)
  await expect(page.getByRole('link', { name: 'Staff accounts', exact: true })).toHaveCount(0)
  expect((await page.request.get('/api/staff/accounts')).status()).toBe(403)
  await page.getByLabel('Find a request', { exact: true }).fill(f.contactName)
  await page.getByRole('button', { name: 'Apply filters' }).click()
  await expect(page.locator('.staff-request-list article')).toHaveCount(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: info.outputPath('staff-pending-mobile.png'), fullPage: true })
  await page.getByRole('link', { name: 'Review request', exact: true }).click()
  await expect(page.locator('.contact-details')).toContainText('Private test address')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('Inventory and Delivery do not request or reveal customer order data', async ({ page }) => {
  const f = fixtures!
  for (const username of [f.inventory, f.delivery]) {
    await login(page, username, f.password)
    await expect(page.getByRole('heading', { name: 'Ready for the next build.' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Pending requests', exact: true })).toHaveCount(0)
    expect((await page.request.get('/api/staff/orders/' + f.orderId)).status()).toBe(403)
    expect((await page.request.get('/api/staff/notifications')).status()).toBe(403)
    await page.goto('/#/staff/orders/' + f.orderId)
    await expect(page.getByRole('alert')).toContainText('Your role cannot access')
    await expect(page.getByText('Private test address', { exact: true })).toHaveCount(0)
    await page.getByRole('button', { name: 'Sign out', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Staff sign-in', exact: true })).toBeVisible()
  }
})

test('staff login network failures retain a retryable form without a fake session', async ({ page }) => {
  await page.goto('/#/staff/login')
  await page.route('**/api/staff/auth/login', route => route.abort())
  await page.getByLabel('Staff username', { exact: true }).fill('unknown-staff')
  await page.getByLabel('Password', { exact: true }).fill('A sample invalid password')
  await page.getByRole('button', { name: 'Sign in to staff workspace' }).click()
  await expect(page.getByRole('alert')).toContainText('could not reach the staff service')
  await expect(page.getByRole('heading', { name: 'Staff sign-in', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Sign in to staff workspace' })).toBeEnabled()
  expect(await page.evaluate(() => ({ ...localStorage }))).toEqual({})
})
