# Fertility Clinic Demo App (IVIRMA)

A small three-tier demo application for a fertility clinic, with two portals behind a sign-in:

- **Admin portal** (`/admin`) for the clinic's **patient coordinator**: register doctors, book fertility visits for patients, and see the clinic's schedule on a dashboard.
- **Customer portal** (`/customer`) for **patients**: create an account, see their own visits, book a visit, and cancel one.

> **Synthetic data only.** All patients, clinicians and contact details are fictional.
> The app does not store clinical data and does not make diagnosis or treatment recommendations.

| Tier | Technology |
|---|---|
| Frontend | React 18 · TypeScript · Vite 4, served by nginx |
| Backend | Java 21 · Spring Boot 3.2 (Web, Data JPA, Validation, Security) · Maven |
| Database | PostgreSQL 13 |
| Runtime | Docker Compose on Rancher Desktop |

---

## 1. Run it locally

**Prerequisites:** [Rancher Desktop](https://rancherdesktop.io/), with *Preferences → Container Engine* set to **dockerd (moby)** so the `docker compose` command works.

From this folder (PowerShell or any terminal):

```powershell
docker compose up --build
```

The first build downloads the Maven and npm dependencies and takes a few minutes. When the backend log shows `Started ClinicApplication`, open:

| What | URL |
|---|---|
| App (choose a portal) | http://localhost:3000 |
| Admin portal | http://localhost:3000/admin |
| Customer portal | http://localhost:3000/customer |
| Backend API (needs sign-in, see [`docs/api.md`](docs/api.md)) | http://localhost:8080/api/dashboard/summary |
| Backend health | http://localhost:8080/actuator/health |

Stop with `Ctrl+C` (or `docker compose down`). To also **delete the database** and start fresh: `docker compose down -v`.

### Demo logins

Created on first start when there are no accounts yet (`DemoAccountSeeder`):

| Portal | Username | Password |
|---|---|---|
| Admin | `admin` | `Admin@123` |
| Customer | any patient already in the database, e.g. `laura.gomez@example.com` | `Patient@123` |

New patients can create their own customer account at http://localhost:3000/customer/register.
Override the demo passwords with the `DEMO_ADMIN_PASSWORD` and `DEMO_CUSTOMER_PASSWORD` environment variables (they only apply when the accounts are first created). Sessions are kept in the backend's memory, so restarting the backend signs everyone out.

## 2. Demo walkthrough

**Admin portal** (sign in as `admin`):

1. **Dashboard:** shows patient counts, visits in the next 7 days, and appointments by status and by visit type.
2. **Register doctor:** fill in name, specialty (e.g. *Embryology*) and email. The doctor appears on the **Doctors** page and can be picked when booking straight away, in both portals. (Patients are not registered here: they create their own account in the customer portal.)
3. **Book visit:** pick a visit type (e.g. *Blood test, 15 min*), a clinician, a date and a time.
4. Try booking the same clinician or the same patient at an overlapping time. The app refuses it with a clear message.
5. Back on the **Dashboard**, the new patient count and the new visit appear.
6. Change a visit's status (Complete / No-show / Cancel) from the patient page or the Appointments page.

**Customer portal:**

1. **Create an account** (or sign in as a demo customer). You land on **My visits**.
2. **Book a visit:** pick a visit type, clinician, date and time. The same clinic-hours and double-booking rules apply.
3. **Cancel** an upcoming visit from My visits.
4. A customer account cannot sign in to the admin portal, and an admin account cannot sign in to the customer portal.

## 3. Check the data is saved in PostgreSQL

```powershell
docker exec -it ivirma-db psql -U ivirma_app -d ivirma_clinic
```

Then run:

```sql
SELECT id, first_name, last_name, treatment_interest, created_at FROM patient ORDER BY id DESC LIMIT 5;

SELECT a.id, p.first_name, p.last_name, a.visit_type, a.clinician, a.start_time, a.status
FROM appointment a JOIN patient p ON p.id = a.patient_id
ORDER BY a.id DESC LIMIT 5;
```

Type `\q` to exit. You can also connect a database tool (DBeaver, pgAdmin) to `localhost:5432`, database `ivirma_clinic`, user `ivirma_app`.

## 4. Database initialization

- **Tables:** Hibernate creates or updates the `patient` and `appointment` tables automatically when the backend starts (`spring.jpa.hibernate.ddl-auto=update`). No migration tool is used. See [`docs/schema.sql`](docs/schema.sql) for the resulting schema.
  *Caution:* `update` adds new columns and tables but never drops or renames them. After changing an entity in an incompatible way, reset with `docker compose down -v`.
- **Sample data:** when the database is empty, the backend loads 8 synthetic patients and 18 appointments relative to today (see `DemoDataSeeder`). Turn this off with `clinic.seed-demo-data=false`.

## 5. Tests (no local Java, Maven or Node needed)

Every test runs inside a Docker container, so you only need Rancher Desktop. Run the commands in **PowerShell** from the `ivirma-clinic-app` folder. Each command is one line.

| Step | What it tests | Needs the app running? |
|---|---|---|
| 5.2 | Backend unit + REST API tests (JUnit 5, Mockito, MockMvc) | No |
| 5.3 | Backend tests + PostgreSQL persistence test + coverage report | Yes (uses the `db` container) |
| 5.4 | End-to-end workflow in a real browser (Playwright), which covers the frontend | Yes |

### 5.1 Start the app in the background

```powershell
docker compose up --build -d
docker compose logs -f backend
```
Wait for `Started ClinicApplication`, then press `Ctrl+C` (this stops the log view, not the app).
Check the Compose network name; the commands below use it:
```powershell
docker network ls
```
You should see `ivirma-clinic-app_default`. If your folder has another name, the network is called `<folder-name>_default`. Use that name in 5.3 and 5.4.

### 5.2 Backend unit + API tests

```powershell
docker run --rm -v "${PWD}/backend:/app" -v ivirma-m2:/root/.m2 -w /app maven:3.9.5-eclipse-temurin-21 mvn test
```
The first run downloads dependencies (a few minutes). The `ivirma-m2` volume caches them for later runs.
Expected at the end: `Tests run: 43, Failures: 0, Errors: 0, Skipped: 1` and `BUILD SUCCESS`. The skipped test is the database test, which runs in 5.3.

### 5.3 Backend tests including the PostgreSQL persistence test (+ coverage)

```powershell
docker run --rm --network ivirma-clinic-app_default -e TEST_DB_URL=jdbc:postgresql://db:5432/ivirma_clinic -e TEST_DB_USER=ivirma_app -e TEST_DB_PASSWORD='Ivirma@2024!' -v "${PWD}/backend:/app" -v ivirma-m2:/root/.m2 -w /app maven:3.9.5-eclipse-temurin-21 mvn verify
```
Expected: `Tests run: 43, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
The persistence test saves a patient and an appointment through the API and reads them back from PostgreSQL with SQL.
Coverage report: open `backend\target\site\jacoco\index.html` in a browser.

### 5.4 End-to-end workflow test (Playwright)

```powershell
docker run --rm --ipc=host --network ivirma-clinic-app_default -e BASE_URL=http://frontend -v "${PWD}/e2e:/e2e" -v /e2e/node_modules -w /e2e mcr.microsoft.com/playwright:v1.63.0-noble sh -c "npm ci && npx playwright test"
```
Expected: `5 passed`.
The admin tests (`clinic-workflow.spec.ts`) sign in as the demo admin, sign up a patient through the customer API, register a new doctor, book a visit with that doctor, check that a clashing booking is rejected, find the visit on the dashboard and in search, mark it completed, and check that a booking in the past is rejected. The customer tests (`customer-portal.spec.ts`) sign up, book and cancel a visit, and check that each portal refuses the other portal's accounts. If you changed the demo passwords, pass them with `-e ADMIN_PASSWORD=... -e CUSTOMER_PASSWORD=...`.
Report: open `e2e\playwright-report\index.html`. Failed runs also save screenshots and traces in `e2e\test-results`.

**This end-to-end test is the regression check that must keep passing after any upgrade.**

### 5.5 Stop

```powershell
docker compose down
```
Add `-v` to also delete the database data.

### Troubleshooting tests

- **`network ivirma-clinic-app_default not found`**: the app is not running (do 5.1), or the network has a different name (check with `docker network ls`).
- **Maven or npm download errors**: on the corporate network, configure the proxy in Rancher Desktop (Preferences → Proxy).
- **Persistence test fails with a password error**: the database volume was created with a different password. Run `docker compose down -v`, then 5.1 again.

## 6. Project structure

```
ivirma-clinic-app/
├── docker-compose.yml        db + backend + frontend
├── backend/                  Spring Boot REST API
│   └── src/main/java/com/ivirma/clinic/
│       ├── auth/             Accounts, sign-in, bearer tokens, access rules (SecurityConfig)
│       ├── customer/         Customer portal API (/api/customer)
│       ├── patient/          Patient entity, repository, service, controller
│       ├── doctor/           Doctor entity, repository, service, controller
│       ├── appointment/      Appointment entity, booking rules, controller
│       ├── dashboard/        Summary statistics
│       ├── common/           Enums (visit types, statuses...), errors, reference data
│       └── config/           CORS, doctor, demo data and demo account seeders
├── frontend/                 React + TypeScript (Vite), nginx config
│   └── src/{api,auth,layouts,pages/admin,pages/customer,components,types}
├── e2e/                      Playwright workflow tests
└── docs/                     API reference, schema
```

See [`docs/api.md`](docs/api.md) for the REST API.

## 7. Troubleshooting

- **Port already in use (5432, 8080 or 3000):** stop the other service, or change the left-hand port in `docker-compose.yml`.
- **Build fails downloading Maven/npm packages:** on a corporate network, Docker builds may need your proxy settings (Rancher Desktop → Preferences → proxy, or `HTTP_PROXY`/`HTTPS_PROXY` build args).
- **Backend starts before the database is ready:** Compose waits for the database health check. If it still fails, run `docker compose restart backend`.
