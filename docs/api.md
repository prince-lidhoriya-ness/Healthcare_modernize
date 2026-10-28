# REST API

Base URL: `http://localhost:8080/api`. In the browser app, calls go to `/api` and nginx forwards them to the backend.
All bodies are JSON. Dates use `YYYY-MM-DD` and date-times use the clinic's local time `YYYY-MM-DDTHH:mm[:ss]`.

## Sign-in and access

Sign in with `POST /auth/login`, then send the returned token on every call: `Authorization: Bearer <token>`.
Tokens last 8 hours and are kept in the backend's memory (a backend restart signs everyone out).

| Paths | Who |
|---|---|
| `POST /auth/login`, `POST /auth/register`, `GET /reference-data` | Anyone |
| `GET /auth/me`, `POST /auth/logout` | Any signed-in user |
| `/customer/**` | Customer accounts (patients), own data only |
| All other `/api/**` paths (patients, appointments, dashboard) | Admin accounts (clinic staff) |

No token or an expired one → `401`. Signed in but the wrong portal → `403`.

### `POST /auth/login`
```json
{ "username": "admin", "password": "Admin@123", "portal": "ADMIN" }
```
`portal` (`ADMIN` or `CUSTOMER`) is optional; when given, an account of the other portal is refused with `401`. Customers sign in with their email.
```json
{ "token": "…", "user": { "id": 1, "username": "admin", "role": "ADMIN", "displayName": "Clinic coordinator", "patientId": null } }
```

### `POST /auth/register` → `201 Created`
Customer sign-up. Same fields as `POST /patients` plus `password` (8–100 characters). Creates the patient and a linked customer account, and returns the same body as login. `409` if the email already has an account or is already registered with the clinic.

### `GET /auth/me`
The signed-in user (the `user` object above).

### `POST /auth/logout` → `204 No Content`
Revokes the token.

## Customer portal

All paths use the signed-in customer's own patient record; there is no patient id in the request.

| Call | Does |
|---|---|
| `GET /customer/profile` | The customer's patient record |
| `GET /customer/appointments` | Their appointments, newest first |
| `POST /customer/appointments` → `201` | Book: `{ "visitType": "BLOOD_TEST", "clinician": "Dr. Priya Nair", "startTime": "2026-11-03T09:30" }`. Same rules as `POST /appointments` |
| `PATCH /customer/appointments/{id}/cancel` | Cancel one of their own scheduled visits. Someone else's appointment → `404` |

## Reference data

### `GET /reference-data`
Lists for the dropdowns.

```json
{
  "visitTypes": [{ "code": "BLOOD_TEST", "label": "Blood test", "durationMinutes": 15 }, "..."],
  "treatmentInterests": [{ "code": "EGG_FREEZING", "label": "Egg freezing" }, "..."],
  "appointmentStatuses": ["SCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"],
  "clinicians": ["Dr. Marco Bianchi", "Dr. Sarah Collins", "Dr. Priya Nair", "Dr. Elena Ruiz"],
  "specialties": [{ "code": "EMBRYOLOGY", "label": "Embryology" }, "..."]
}
```
`clinicians` are the names of all registered doctors (see [Doctors](#doctors)), sorted by last name.

| Visit type | Code | Length |
|---|---|---|
| Initial consultation | `INITIAL_CONSULTATION` | 45 min |
| Ultrasound monitoring | `ULTRASOUND_MONITORING` | 20 min |
| Blood test | `BLOOD_TEST` | 15 min |
| Egg retrieval | `EGG_RETRIEVAL` | 60 min |
| Embryo transfer | `EMBRYO_TRANSFER` | 30 min |
| Follow-up | `FOLLOW_UP` | 30 min |

Treatment interests: `FERTILITY_ASSESSMENT`, `IVF`, `IUI`, `EGG_FREEZING`, `EGG_DONATION`.

## Doctors

The four demo doctors are loaded on first start; admins can register more. A doctor's `name` ("Dr. First Last") is what appointments store in `clinician`, so it is unique.

### `GET /doctors`
All doctors, sorted by last name.

### `POST /doctors` → `201 Created`
```json
{ "firstName": "Ana", "lastName": "Lopez", "specialty": "EMBRYOLOGY", "email": "ana.lopez@clinic.example.com", "phone": "+34 600 000 100" }
```
`phone` is optional. Specialties: `REPRODUCTIVE_MEDICINE`, `GYNAECOLOGY`, `EMBRYOLOGY`, `ANDROLOGY`, `REPRODUCTIVE_IMAGING`.
Returns the doctor with `id`, `name` and `createdAt`. `409` if the name or email is already registered.

## Patients

Patients create their own accounts through `POST /auth/register` (customer portal). `POST /patients` is still available to admin accounts but the admin portal no longer uses it.

### `GET /patients?search={text}`
Lists all patients, sorted by last name. The optional `search` filters by first name, last name or email.

### `POST /patients` → `201 Created`
```json
{
  "firstName": "Testpatient",
  "lastName": "Example",
  "dateOfBirth": "1990-06-15",
  "email": "test.example@example.com",
  "phone": "+34 600 000 999",
  "treatmentInterest": "EGG_FREEZING"
}
```
Rules: names, date of birth, email and treatment interest are required. The patient must be at least 18. The email must be unique (`409` otherwise).

Response:
```json
{
  "id": 9, "firstName": "Testpatient", "lastName": "Example", "fullName": "Testpatient Example",
  "dateOfBirth": "1990-06-15", "email": "test.example@example.com", "phone": "+34 600 000 999",
  "treatmentInterest": "EGG_FREEZING", "createdAt": "2026-10-28T13:05:12.123"
}
```

### `GET /patients/{id}`
One patient, or `404`.

### `PUT /patients/{id}`
Updates a patient. Same body as `POST`.

### `GET /patients/{id}/appointments`
All appointments for the patient, newest first.

## Appointments

### `POST /appointments` → `201 Created`
```json
{
  "patientId": 9,
  "visitType": "BLOOD_TEST",
  "clinician": "Dr. Priya Nair",
  "startTime": "2026-11-02T09:30"
}
```
Rules:
- All four fields are required, and the clinician must be one from the reference data.
- The visit cannot be in the past (`400`).
- The clinic is open Monday–Saturday, 08:00–19:00, and the whole visit must fit in that window (`400`).
- The length comes from the visit type. The visit cannot overlap another non-cancelled visit for the **same clinician** or the **same patient** (`409`).

Response:
```json
{
  "id": 21, "patientId": 9, "patientName": "Testpatient Example",
  "visitType": "BLOOD_TEST", "visitTypeLabel": "Blood test",
  "clinician": "Dr. Priya Nair",
  "startTime": "2026-11-02T09:30:00", "endTime": "2026-11-02T09:45:00",
  "durationMinutes": 15, "status": "SCHEDULED"
}
```

### `GET /appointments?upcoming=true`
Scheduled visits from now on, soonest first. `upcoming=false` returns all visits, newest first.

### `PATCH /appointments/{id}/status`
```json
{ "status": "COMPLETED" }
```
Allowed targets: `COMPLETED`, `CANCELLED`, `NO_SHOW`. Only a `SCHEDULED` visit can change (`409` otherwise).

## Dashboard

### `GET /dashboard/summary`
```json
{
  "totalPatients": 9,
  "newPatientsThisWeek": 3,
  "appointmentsToday": 0,
  "upcomingNext7Days": 10,
  "appointmentsByStatus": { "SCHEDULED": 10, "COMPLETED": 6, "CANCELLED": 1, "NO_SHOW": 1 },
  "appointmentsByVisitType": { "Initial consultation": 7, "Blood test": 3, "...": 0 },
  "nextAppointments": [ { "...": "same shape as an appointment" } ],
  "generatedAt": "28/10/2026 13:05"
}
```

## Errors

```json
{ "status": 409, "error": "Conflict", "message": "Dr. Priya Nair already has an appointment from 09:30 to 09:45", "timestamp": "..." }
```

| Code | When |
|---|---|
| 400 | Missing or invalid field, past time, outside clinic hours |
| 401 | Not signed in, token expired, or wrong username/password |
| 403 | Signed in, but the account belongs to the other portal |
| 404 | Patient or appointment not found |
| 409 | Duplicate email, double booking, status already final |
