import { expect, test } from '@playwright/test'

/**
 * Customer portal workflow:
 *   sign up -> book a visit -> see it in "My visits" -> cancel it -> cannot open the admin portal
 */

function upcomingClinicDay(): string {
  const d = new Date()
  d.setDate(d.getDate() + 2 + Math.floor(Math.random() * 4))
  if (d.getDay() === 0) d.setDate(d.getDate() + 1)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function randomSlot(): string {
  const hour = 8 + Math.floor(Math.random() * 10) // 08..17
  const minute = [0, 15, 30, 45][Math.floor(Math.random() * 4)]
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}

test('customer signs up, books a visit and cancels it', async ({ page }) => {
  const suffix = Date.now().toString().slice(-6)
  const lastName = `Cust${suffix}`
  const email = `customer.${suffix}@example.com`

  // 1. Sign up
  await page.goto('/customer/register')
  await page.getByLabel('First name').fill('Testcustomer')
  await page.getByLabel('Last name').fill(lastName)
  await page.getByLabel('Date of birth').fill('1991-03-20')
  await page.getByLabel('Treatment interest').selectOption('IVF')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill('Secret#2024')
  await page.getByLabel('Confirm password').fill('Secret#2024')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByTestId('customer-name')).toHaveText('Hello, Testcustomer')

  // 2. Book a visit (retry with another slot if the clinician is already busy)
  await page.goto('/customer/book')
  await page.getByLabel('Visit type').selectOption('BLOOD_TEST')
  await page.getByLabel('Clinician').selectOption('Dr. Marco Bianchi')
  await page.getByLabel('Date').fill(upcomingClinicDay())
  let booked = false
  for (let attempt = 0; attempt < 8 && !booked; attempt++) {
    await page.getByLabel('Start time').selectOption(randomSlot())
    await page.getByRole('button', { name: 'Book visit' }).click()
    booked = await page
      .getByTestId('customer-name')
      .waitFor({ timeout: 3000 })
      .then(() => true)
      .catch(() => false)
  }
  expect(booked).toBe(true)

  const row = page.locator('tbody tr').filter({ hasText: 'Blood test' })
  await expect(row).toContainText('Scheduled')

  // 3. Cancel it
  page.once('dialog', (dialog) => dialog.accept())
  await row.getByRole('button', { name: 'Cancel' }).click()
  await expect(row).toContainText('Cancelled')

  // 4. A customer cannot open the admin portal
  await page.goto('/admin')
  await expect(page).toHaveURL(/\/admin\/login$/)
})

test('seeded customer can sign in and is refused by the admin portal', async ({ page }) => {
  await page.goto('/admin/login')
  await page.getByLabel('Username').fill('laura.gomez@example.com')
  await page.getByLabel('Password').fill(process.env.CUSTOMER_PASSWORD || 'Patient@123')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toContainText('customer portal')

  await page.goto('/customer/login')
  await page.getByLabel('Email').fill('laura.gomez@example.com')
  await page.getByLabel('Password').fill(process.env.CUSTOMER_PASSWORD || 'Patient@123')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByTestId('customer-name')).toHaveText('Hello, Laura')

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/customer\/login$/)
})

test('wrong password is rejected', async ({ page }) => {
  await page.goto('/admin/login')
  await page.getByLabel('Username').fill('admin')
  await page.getByLabel('Password').fill('not-the-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toContainText('Invalid')
})
