import { expect, test } from '@playwright/test'
const phone = () => '+2557' + Math.floor(Math.random() * 100000000).toString().padStart(8, '0')
const password = 'Our sample account passphrase 2026!'

test('customer can register, reload a private session, logout and sign in again', async ({ page }, info) => {
  const number = phone()
  await page.goto('/#/register')
  await page.getByLabel('Full name', { exact: true }).fill('Juma Builder')
  await page.getByLabel('Phone number', { exact: true }).fill(number)
  await page.getByLabel('Password', { exact: true }).fill(password)
  const [registration] = await Promise.all([
    page.waitForResponse(response => response.url().endsWith('/api/auth/register') && response.request().method() === 'POST'),
    page.getByRole('button', { name: 'Create account', exact: true }).click(),
  ])
  expect(registration.status()).toBe(201)
  await expect(page.getByRole('heading', { name: 'Welcome, Juma Builder' })).toBeVisible()
  await expect(page.getByText('Not verified', { exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Welcome, Juma Builder' })).toBeVisible()
  expect(await page.evaluate(() => ({ ...localStorage }))).toEqual({})
  expect((await page.context().cookies()).find(c => c.name === 'JSESSIONID')?.httpOnly).toBe(true)
  expect((await page.request.get('/api/admin/staff')).status()).toBe(403)
  await page.screenshot({ path: info.outputPath('account-desktop.png'), fullPage: true })
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
  expect((await page.request.get('/api/auth/me')).status()).toBe(401)
  await page.getByLabel('Phone number', { exact: true }).fill(number)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Welcome, Juma Builder' })).toBeVisible()
})

test('anonymous account page and invalid credentials never reveal a private profile', async ({ page }) => {
  await page.goto('/#/account')
  await expect(page.getByRole('heading', { name: 'Sign in to continue' })).toBeVisible()
  await page.getByRole('link', { name: 'Sign in', exact: true }).last().click()
  await page.getByLabel('Phone number', { exact: true }).fill(phone())
  await page.getByLabel('Password', { exact: true }).fill('wrong-passphrase')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('Phone number or password is incorrect.')
  await expect(page.getByRole('heading', { name: /^Welcome,/ })).toHaveCount(0)
})

test('phone registration form supports password visibility and has no overflow', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/#/register')
  await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible()
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Show password' }).click()
  await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: 'Hide password' }).click()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('registration-mobile.png'), fullPage: true })
})

test('registration network failure can be retried without claiming account creation', async ({ page }) => {
  await page.route('**/api/auth/register', route => route.abort())
  await page.goto('/#/register')
  await page.getByLabel('Full name', { exact: true }).fill('Asha Builder')
  await page.getByLabel('Phone number', { exact: true }).fill(phone())
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('could not reach your account')
  await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible()
  await page.unroute('**/api/auth/register')
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Welcome, Asha Builder' })).toBeVisible()
})
