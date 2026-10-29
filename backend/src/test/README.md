# Backend tests (STORY-001 characterization suite)

These tests pin what the **legacy** backend (Spring Boot 3.2 / Java 21, commit `81bff2a`) does today,
so the uplift to Java 25 / Spring Boot 4.1.2 (ADR-001) can be shown to behave the same way.
They describe what the legacy code actually does, quirks included, not what it ought to do.
If a test and `analysis/ivirma-clinic/BUSINESS_RULES.md` disagree, the test follows the legacy code
and the discrepancy is reported separately.

## Running

There is no local Maven; use the Maven image (Git Bash: prefix with `MSYS_NO_PATHCONV=1`):

```sh
# unit + web-slice + golden tests (DB-backed classes are skipped without TEST_DB_URL)
docker run --rm -v "<abs path>/backend:/app" -v ivirma-m2:/root/.m2 -w /app \
  maven:3.9.5-eclipse-temurin-21 mvn -B test
```

If Maven Central fails with certificate errors, import the corporate CA into the container JDK first,
e.g. `keytool -importcert -noprompt -alias corp -file /ca/corporate-ca.pem -cacerts -storepass changeit && mvn -B test`
with the PEM mounted at `/ca`.

DB-backed tests (`PersistenceIntegrationTest`, `FullStackIntegrationTest`, `SchemaMigrationIntegrationTest`) run only when `TEST_DB_URL` is set:

```sh
docker compose -p ivirma-char up -d db
export TEST_DB_PASSWORD="$(docker exec ivirma-db printenv POSTGRES_PASSWORD)"   # never echo it
docker run --rm --network ivirma-char_default \
  -e TEST_DB_URL=jdbc:postgresql://db:5432/ivirma_clinic -e TEST_DB_USER=ivirma_app -e TEST_DB_PASSWORD \
  -v "<abs path>/backend:/app" -v ivirma-m2:/root/.m2 -w /app maven:3.9.5-eclipse-temurin-21 mvn -B verify
docker compose -p ivirma-char down -v
```

A run without the DB reports 11 skipped tests (3 + 4 + 4 in the three DB-backed classes). In that case
the DB paths (DELTA-017, the actuator health check and the STORY-003 schema checks) have **not** been proven.
With the DB, `SchemaMigrationIntegrationTest` reports 8 tests (its enum/CHECK factory yields one case per enum column).

Bean Validation messages are the English defaults (`must be a past date`, ...). Run the tests with an
English JVM locale, as the Maven container does.

## Layout and naming

| Class | Pins |
|---|---|
| `appointment/AppointmentServiceCharacterizationTest` | RULE-001, 002, 003, 016, 017, 018, 019, 020, 021, 033, 034 (clock frozen at Thu 2026-10-29 10:00:30) |
| `appointment/AppointmentControllerCharacterizationTest` | RULE-033 controller check |
| `patient/PatientServiceCharacterizationTest` | RULE-024, RULE-025, DELTA-019 (`capitalizeFully`) |
| `patient/PatientControllerCharacterizationTest` | RULE-022, RULE-024 (HTTP), RULE-025 (PUT not validated) |
| `auth/AuthServiceCharacterizationTest` | RULE-026, 027, 035, 045 |
| `auth/TokenServiceCharacterizationTest` | RULE-040, RULE-041 (`Instant.now()` frozen) |
| `auth/AuthHttpCharacterizationTest` | RULE-028, DELTA-013 (Q16), RULE-035/027/040/041/045 over HTTP with the real BCrypt |
| `auth/SecurityCharacterizationTest` | RULE-044 matrix and exact 401/403 bodies, RULE-047, DELTA-014 edges |
| `config/DemoAccountSeederCharacterizationTest` | RULE-039 |
| `characterization/JsonWireFormatCharacterizationTest` | DELTA-009 (field order), DELTA-010 (timestamp `+00:00`), DELTA-011 (trailing tokens) |
| `characterization/GoldenApiResponsesTest` + `resources/golden/*.txt` | TASK-001: status, content type and normalised body for every endpoint in `docs/api.md` |
| `PersistenceIntegrationTest` (DB) | original persistence test + DELTA-017 (dashboard aggregates, native search) |
| `FullStackIntegrationTest` (DB) | DELTA-014 on the full context: `/actuator/health` open, trailing slash, `/api/customerX` |
| `SchemaMigrationIntegrationTest` (DB) | STORY-003: TASK-034 every `@Enumerated` Java enum value is allowed by its column's CHECK (fails naming the enum and the constraint); TASK-035 Hibernate runs `ddl-auto=validate` and V1 is in `flyway_schema_history` |

Every characterization test names the rule or delta it pins, together with `STORY001`, in its method
name (`RULE_019_STORY001_visitMustEndBy1900`) and in its display name (`RULE-019 STORY-001 ...`).
`modernize-verify` finds tests by those ids.

## Golden files (TASK-001)

`GoldenApiResponsesTest` drives every endpoint through MockMvc with the real controllers, security,
exception handler, Jackson setup and services. Only the five repositories are replaced by literal
fixtures, and `LocalDateTime.now()` is frozen at 2026-10-29T10:00. Before comparison it normalises
`token`, error `timestamp` and dashboard `generatedAt` to placeholders, after first checking that each
value has the legacy format. Lines starting with `#` are comments and are not compared.

- A missing golden file is a **failure**, not a skip.
- The run prints `TASK-001 golden: equivalence cases executed: N`. If N is 0, nothing has been proven.
- Re-record only on the **legacy** stack, and review the diff before committing:
  `mvn -B test -Dtest=GoldenApiResponsesTest -Dgolden.record=true`.
  Never re-record on the target to make a failing comparison pass.

## Adding a case

1. Find the rule in `BUSINESS_RULES.md` and read the legacy branch it describes.
2. Add a test to the class for that module. Use literal inputs and literal expected outputs, and name
   it `RULE_NNN_STORY001_<behaviour>` with `@DisplayName("RULE-NNN STORY-001 ...")`.
3. For a new endpoint or response shape, add a `c("<id>", "RULE-NNN ...", method, path, who, body)`
   entry to `GoldenApiResponsesTest.cases()` and record it on the legacy stack.
4. Run the test against the legacy code. It must pass there. If it fails, the test has misunderstood
   the legacy behaviour: fix the test, never the production code.
5. Show that the test can fail: make a small change to the code it pins (a boundary, a comparison, a
   constant) in a scratch copy and check that the test goes red.

Secrets: never copy credentials from the legacy code into tests. Fixture passwords are obviously fake
(`fixture-admin-pass`, `fake-pass-123`), and the DB password is read from `TEST_DB_PASSWORD`.
