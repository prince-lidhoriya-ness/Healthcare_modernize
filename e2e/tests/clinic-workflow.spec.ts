import { expect, Page, test } from '@playwright/test'

/**
 * Core demo workflow:
 *   patient signs up -> register doctor -> book visit with them -> clash is rejected -> dashboard -> search -> complete
 * This is the regression check that must stay green after the Java 25 / React 19 upgrade.
 * Runs in the admin portal, signed in as the demo admin (see DemoAccountSeeder).
 */

function isoDate(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

/** A clinic day (Mon-Sat) between 2 and 5 days from today, so it shows in "Next 7 days". */
function upcomingClinicDay(): string {
  const d = new Date()
  d.setDate(d.getDate() + 2 + Math.floor(Math.random() * 4))
  if (d.getDay() === 0) d.setDate(d.getDate() + 1)
  return isoDate(d)
}

function randomSlot(): string {
  const hour = 8 + Math.floor(Math.random() * 10) // 08..17
  const minute = [0, 15, 30, 45][Math.floor(Math.random() * 4)]
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}

const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'admin'
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'Admin@123'

test.beforeEach(async ({ page }) => {
  await page.goto('/admin/login')
  await page.getByLabel('Username').fill(ADMIN_USERNAME)
  await page.getByLabel('Password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
})

async function bookVisit(page: Page, date: string, clinician: string): Promise<string> {
  await page.getByLabel('Visit type').selectOption('BLOOD_TEST')
  await page.getByLabel('Clinician').selectOption(clinician)
  await page.getByLabel('Date').fill(date)
  // Retry with another slot if a previous test run already took this one
  for (let attempt = 0; attempt < 8; attempt++) {
    const slot = randomSlot()
    await page.getByLabel('Start time').selectOption(slot)
    await page.getByRole('button', { name: 'Book visit' }).click()
    const booked = await page
      .getByTestId('patient-name')
      .waitFor({ timeout: 3000 })
      .then(() => true)
      .catch(() => false)
    if (booked) return slot
  }
  throw new Error('Could not find a free slot')
}

test('coordinator registers a doctor, books a visit with them and sees it on the dashboard', async ({ page }) => {
  const suffix = Date.now().toString().slice(-6)
  const firstName = 'Testpatient'
  const lastName = `E2e${suffix}`
  const email = `e2e.${suffix}@example.com`
  const clinician = `Dr. Test Doctor${suffix}`

  // 1. A synthetic patient signs up in the customer portal (patients register themselves)
  const signup = await page.request.post('/api/auth/register', {
    data: {
      firstName,
      lastName,
      dateOfBirth: '1990-06-15',
      email,
      treatmentInterest: 'EGG_FREEZING',
      password: 'Secret#2024',
    },
  })
  expect(signup.status()).toBe(201)
  const patientId = (await signup.json()).user.patientId

  // 2. The coordinator registers a new doctor
  await page.getByRole('link', { name: 'Register doctor' }).click()
  await page.getByLabel('First name').fill('Test')
  await page.getByLabel('Last name').fill(`Doctor${suffix}`)
  await page.getByLabel('Specialty').selectOption('EMBRYOLOGY')
  await page.getByLabel('Email').fill(`doctor.${suffix}@clinic.example.com`)
  await page.getByRole('button', { name: 'Register doctor' }).click()
  await expect(page.getByRole('heading', { name: 'Doctors' })).toBeVisible()
  await expect(page.locator('tbody tr').filter({ hasText: clinician })).toContainText('Embryology')

  // 3. Open the patient
  await page.goto(`/admin/patients/${patientId}`)
  await expect(page.getByTestId('patient-name')).toHaveText(`${firstName} ${lastName}`)
  await expect(page.getByText('Egg freezing')).toBeVisible()
  const patientUrl = page.url()

  // 4. Book a visit for that patient with the new doctor
  await page.getByRole('link', { name: 'Book visit' }).click()
  await expect(page.getByLabel('Patient')).not.toHaveValue('')
  const date = upcomingClinicDay()
  const slot = await bookVisit(page, date, clinician)

  const row = page.locator('tbody tr').filter({ hasText: 'Blood test' })
  await expect(row).toHaveCount(1)
  await expect(row).toContainText(clinician)
  await expect(row).toContainText('Scheduled')

  // 5. A second booking for the same patient at the same time is rejected
  await page.getByRole('link', { name: 'Book visit' }).click()
  await page.getByLabel('Visit type').selectOption('FOLLOW_UP')
  await page.getByLabel('Clinician').selectOption('Dr. Sarah Collins')
  await page.getByLabel('Date').fill(date)
  await page.getByLabel('Start time').selectOption(slot)
  await page.getByRole('button', { name: 'Book visit' }).click()
  await expect(page.getByRole('alert')).toContainText('already has an appointment')

  // 6. The dashboard shows the patient and the visit
  await page.getByRole('link', { name: 'Dashboard' }).click()
  await expect(page.getByTestId('stat-total-patients')).not.toHaveText('0')
  await expect(page.getByRole('link', { name: `${firstName} ${lastName}` })).toBeVisible()

  // 7. The patient appears in search
  await page.getByRole('link', { name: 'Patients', exact: true }).click()
  await page.getByLabel('Search patients').fill(lastName)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.locator('tbody tr')).toHaveCount(1)

  // 8. Mark the visit completed
  await page.goto(patientUrl)
  await page.getByRole('button', { name: 'Complete' }).click()
  await expect(page.locator('tbody tr').filter({ hasText: 'Blood test' })).toContainText('Completed')
})

test('booking in the past is rejected', async ({ page }) => {
  await page.goto('/admin/appointments/new')
  await page.getByLabel('Patient').selectOption({ index: 1 })
  await page.getByLabel('Visit type').selectOption('INITIAL_CONSULTATION')
  await page.getByLabel('Clinician').selectOption('Dr. Elena Ruiz')
  await page.getByLabel('Date').fill('2020-01-07')
  await page.getByLabel('Start time').selectOption('10:00')
  await page.getByRole('button', { name: 'Book visit' }).click()
  await expect(page.getByRole('alert')).toContainText('past')
})
