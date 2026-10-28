import { expect, Page, test } from '@playwright/test'

/**
 * Core demo workflow:
 *   register patient -> book visit -> see it on the dashboard -> clash is rejected -> mark completed
 * This is the regression check that must stay green after the Java 25 / React 19 upgrade.
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

test('coordinator registers a patient, books a visit and sees it on the dashboard', async ({ page }) => {
  const suffix = Date.now().toString().slice(-6)
  const firstName = 'Testpatient'
  const lastName = `E2e${suffix}`
  const email = `e2e.${suffix}@example.com`
  const clinician = 'Dr. Priya Nair'

  // 1. Register a synthetic patient
  await page.goto('/patients/new')
  await page.getByLabel('First name').fill(firstName)
  await page.getByLabel('Last name').fill(lastName)
  await page.getByLabel('Date of birth').fill('1990-06-15')
  await page.getByLabel('Treatment interest').selectOption('EGG_FREEZING')
  await page.getByLabel('Email').fill(email)
  await page.getByRole('button', { name: 'Register patient' }).click()

  await expect(page.getByTestId('patient-name')).toHaveText(`${firstName} ${lastName}`)
  await expect(page.getByText('Egg freezing')).toBeVisible()
  const patientUrl = page.url()

  // 2. Book a visit for that patient
  await page.getByRole('link', { name: 'Book visit' }).click()
  await expect(page.getByLabel('Patient')).not.toHaveValue('')
  const date = upcomingClinicDay()
  const slot = await bookVisit(page, date, clinician)

  const row = page.locator('tbody tr').filter({ hasText: 'Blood test' })
  await expect(row).toHaveCount(1)
  await expect(row).toContainText(clinician)
  await expect(row).toContainText('Scheduled')

  // 3. A second booking for the same patient at the same time is rejected
  await page.getByRole('link', { name: 'Book visit' }).click()
  await page.getByLabel('Visit type').selectOption('FOLLOW_UP')
  await page.getByLabel('Clinician').selectOption('Dr. Sarah Collins')
  await page.getByLabel('Date').fill(date)
  await page.getByLabel('Start time').selectOption(slot)
  await page.getByRole('button', { name: 'Book visit' }).click()
  await expect(page.getByRole('alert')).toContainText('already has an appointment')

  // 4. The dashboard shows the patient and the visit
  await page.getByRole('link', { name: 'Dashboard' }).click()
  await expect(page.getByTestId('stat-total-patients')).not.toHaveText('0')
  await expect(page.getByRole('link', { name: `${firstName} ${lastName}` })).toBeVisible()

  // 5. The patient appears in search
  await page.getByRole('link', { name: 'Patients', exact: true }).click()
  await page.getByLabel('Search patients').fill(lastName)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.locator('tbody tr')).toHaveCount(1)

  // 6. Mark the visit completed
  await page.goto(patientUrl)
  await page.getByRole('button', { name: 'Complete' }).click()
  await expect(page.locator('tbody tr').filter({ hasText: 'Blood test' })).toContainText('Completed')
})

test('booking in the past is rejected', async ({ page }) => {
  await page.goto('/appointments/new')
  await page.getByLabel('Patient').selectOption({ index: 1 })
  await page.getByLabel('Visit type').selectOption('INITIAL_CONSULTATION')
  await page.getByLabel('Clinician').selectOption('Dr. Elena Ruiz')
  await page.getByLabel('Date').fill('2020-01-07')
  await page.getByLabel('Start time').selectOption('10:00')
  await page.getByRole('button', { name: 'Book visit' }).click()
  await expect(page.getByRole('alert')).toContainText('past')
})
