# REST API

Base URL: `http://localhost:8080/api`. In the browser app, calls go to `/api` and nginx forwards them to the backend.
All bodies are JSON. Dates use `YYYY-MM-DD` and date-times use the clinic's local time `YYYY-MM-DDTHH:mm[:ss]`.

## Reference data

### `GET /reference-data`
Lists for the dropdowns.

```json
{
  "visitTypes": [{ "code": "BLOOD_TEST", "label": "Blood test", "durationMinutes": 15 }, "..."],
  "treatmentInterests": [{ "code": "EGG_FREEZING", "label": "Egg freezing" }, "..."],
  "appointmentStatuses": ["SCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"],
  "clinicians": ["Dr. Elena Ruiz", "Dr. Marco Bianchi", "Dr. Sarah Collins", "Dr. Priya Nair"]
}
```

| Visit type | Code | Length |
|---|---|---|
| Initial consultation | `INITIAL_CONSULTATION` | 45 min |
| Ultrasound monitoring | `ULTRASOUND_MONITORING` | 20 min |
| Blood test | `BLOOD_TEST` | 15 min |
| Egg retrieval | `EGG_RETRIEVAL` | 60 min |
| Embryo transfer | `EMBRYO_TRANSFER` | 30 min |
| Follow-up | `FOLLOW_UP` | 30 min |

Treatment interests: `FERTILITY_ASSESSMENT`, `IVF`, `IUI`, `EGG_FREEZING`, `EGG_DONATION`.

## Patients

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
| 404 | Patient or appointment not found |
| 409 | Duplicate email, double booking, status already final |
