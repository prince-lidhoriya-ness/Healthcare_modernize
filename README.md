# Fertility Clinic Demo App (IVIRMA)

A small three-tier demo application for a fertility clinic's **patient coordinator**:
register patients, book fertility visits, and see the clinic's schedule on a dashboard.

> **Synthetic data only.** All patients, clinicians and contact details are fictional.
> The app does not store clinical data and does not make diagnosis or treatment recommendations.

| Tier | Technology |
|---|---|
| Frontend | React 18 · TypeScript · Vite 4, served by nginx |
| Backend | Java 21 · Spring Boot 3.2 (Web, Data JPA, Validation) · Maven |
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
| App | http://localhost:3000 |
| Backend API | http://localhost:8080/api/dashboard/summary |
| Backend health | http://localhost:8080/actuator/health |

Stop with `Ctrl+C` (or `docker compose down`). To also **delete the database** and start fresh: `docker compose down -v`.

## 2. Demo walkthrough

1. **Dashboard:** shows patient counts, visits in the next 7 days, and appointments by status and by visit type.
2. **Register patient:** fill in name, date of birth, email and treatment interest (e.g. *Egg freezing*). You land on the patient's page.
3. **Book visit:** pick a visit type (e.g. *Blood test, 15 min*), a clinician, a date and a time.
4. Try booking the same clinician or the same patient at an overlapping time. The app refuses it with a clear message.
5. Back on the **Dashboard**, the new patient count and the new visit appear.
6. Change a visit's status (Complete / No-show / Cancel) from the patient page or the Appointments page.

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

## 5. Tests

| Suite | Location | How to run |
|---|---|---|
| Backend unit + API tests (JUnit 5, Mockito, MockMvc) | `backend/src/test` | `mvn test` in `backend/` |
| PostgreSQL persistence test | `PersistenceIntegrationTest` | Runs only when `TEST_DB_URL` is set (see below) |
| End-to-end workflow (Playwright) | `e2e/` | Against the running app (see below) |

No local Java or Maven? Run the backend tests in a container:

```powershell
docker run --rm -v "${PWD}/backend:/app" -w /app maven:3.9.5-eclipse-temurin-21 mvn test
```

Persistence test against the Compose database (with `docker compose up` running):

```powershell
cd backend
$env:TEST_DB_URL="jdbc:postgresql://localhost:5432/ivirma_clinic"; $env:TEST_DB_USER="ivirma_app"; $env:TEST_DB_PASSWORD="<db password>"
mvn test
```

End-to-end test (needs Node 18+, with the app running on port 3000):

```powershell
cd e2e
npm install
npx playwright install chromium
npx playwright test
```

The e2e test registers a patient, books a visit, checks that a clashing booking is rejected, finds the visit on the dashboard and in search, and marks it completed. **This is the regression check that must keep passing after any upgrade.**

## 6. Project structure

```
ivirma-clinic-app/
├── docker-compose.yml        db + backend + frontend
├── backend/                  Spring Boot REST API
│   └── src/main/java/com/ivirma/clinic/
│       ├── patient/          Patient entity, repository, service, controller
│       ├── appointment/      Appointment entity, booking rules, controller
│       ├── dashboard/        Summary statistics
│       ├── common/           Enums (visit types, statuses...), errors, reference data
│       └── config/           CORS, demo data seeder
├── frontend/                 React + TypeScript (Vite), nginx config
│   └── src/{api,pages,components,types}
├── e2e/                      Playwright workflow tests
└── docs/                     API reference, schema
```

See [`docs/api.md`](docs/api.md) for the REST API.

## 7. Troubleshooting

- **Port already in use (5432, 8080 or 3000):** stop the other service, or change the left-hand port in `docker-compose.yml`.
- **Build fails downloading Maven/npm packages:** on a corporate network, Docker builds may need your proxy settings (Rancher Desktop → Preferences → proxy, or `HTTP_PROXY`/`HTTPS_PROXY` build args).
- **Backend starts before the database is ready:** Compose waits for the database health check. If it still fails, run `docker compose restart backend`.
