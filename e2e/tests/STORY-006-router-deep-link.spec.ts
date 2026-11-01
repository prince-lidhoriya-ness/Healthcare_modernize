import { expect, test } from '@playwright/test'

/**
 * STORY-006 / DELTA-024: react-router 8 replaces react-router-dom and wraps navigation in startTransition by default.
 * A signed-out deep link must still land on the portal's login, and signing in must return to the page that was
 * asked for: RequireRole keeps it in location.state.from and LoginPage navigates back to it with replace.
 * Legacy (react-router-dom 6.20) behaves the same way, so this passes before and after the upgrade.
 * The account is a fresh synthetic customer whose password is generated for this run (no credential literal).
 */
test('STORY-006 DELTA-024: signed-out deep link goes to login and returns to the page after sign-in', async ({ page }) => {
  const suffix = Date.now().toString().slice(-6)
  const email = `deeplink.${suffix}@example.com`
  const secret = crypto.randomUUID() // 36 characters; sign-up accepts 8 to 100

  const signup = await page.request.post('/api/auth/register', {
    data: {
      firstName: 'Deeplink',
      lastName: `Story${suffix}`,
      dateOfBirth: '1990-06-15',
      email,
      treatmentInterest: 'IVF',
      password: secret,
    },
  })
  expect(signup.status()).toBe(201)

  // Signed out, both portals send a deep link to their own login page
  await page.goto('/admin/patients')
  await expect(page).toHaveURL(/\/admin\/login$/)
  await page.goto('/customer/book')
  await expect(page).toHaveURL(/\/customer\/login$/)

  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(secret)
  await page.getByRole('button', { name: 'Sign in' }).click()

  // Back on the page that was asked for, not the portal home
  await expect(page).toHaveURL(/\/customer\/book$/)
  await expect(page.getByRole('heading', { name: 'Book a visit' })).toBeVisible()
})
