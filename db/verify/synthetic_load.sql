-- Production-shaped SYNTHETIC load for the DB-track rehearsals (TASK-003 fingerprint, TASK-008, TASK-010/029).
--
-- DISPOSABLE / SCRATCH DATABASES ONLY. Run it after the app has created and seeded the schema:
--   psql -v ON_ERROR_STOP=1 --single-transaction -f db/verify/synthetic_load.sql
-- Guarded like U1: the caller must pass PGOPTIONS='-c forge.allow_synthetic_load=on'.
--
-- Deterministic (no random()): every value is a function of the row number, so two loads into the same
-- seeded schema produce the same rows (ids depend on the sequence position at load time).
-- All data is fake; emails use the reserved example.org domain (allowlisted by the gate-2 PII scan).
-- Honours the integrity invariants from forge/db/inventory.md section 4:
--   I1 appointment.clinician is always an existing doctor.name
--   I2 duration_minutes equals the VisitType duration for its visit_type
--   I3 role = 'ADMIN'  <=>  patient_id IS NULL
--   I4 user_account.username, patient.email and doctor.email are lower case
-- Covers: every enum value of all five enum columns, every appointment status, NULL phones, microsecond
-- timestamps, accented / mixed-case / particle surnames (Álvarez, de la Fuente, O'Brien, Zúñiga, álvarez),
-- and trailing-space values (db_verify normalisation blind spot, strategy 7.4).

DO $$
BEGIN
    IF current_setting('forge.allow_synthetic_load', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'synthetic_load refused: scratch-only (set PGOPTIONS=''-c forge.allow_synthetic_load=on'')';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM doctor) THEN
        RAISE EXCEPTION 'synthetic_load refused: seed the schema with the app first (doctor is empty)';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

-- 1. Doctors: one per specialty, with accented / particle names (appointment.clinician points at name).
INSERT INTO doctor (created_at, email, first_name, last_name, name, phone, specialty)
VALUES
    (TIMESTAMP '2024-02-01 08:00:00.000001', 'syn.doctor1@example.org', 'Ana',   'Álvarez',      'Dr. Ana Álvarez',          '+34 600 000 001', 'REPRODUCTIVE_MEDICINE'),
    (TIMESTAMP '2024-02-02 08:00:00.123456', 'syn.doctor2@example.org', 'Luis',  'de la Fuente', 'Dr. Luis de la Fuente',    NULL,              'GYNAECOLOGY'),
    (TIMESTAMP '2024-02-03 08:00:00.999999', 'syn.doctor3@example.org', 'Sean',  'O''Brien',     'Dr. Sean O''Brien',        '+353 1 000 0003', 'EMBRYOLOGY'),
    (TIMESTAMP '2024-02-04 08:00:00.500000', 'syn.doctor4@example.org', 'Íñigo', 'Zúñiga',       'Dr. Íñigo Zúñiga',         NULL,              'ANDROLOGY'),
    (TIMESTAMP '2024-02-05 08:00:00.000010', 'syn.doctor5@example.org', 'Zoë',   'Müller ',      'Dr. Zoë Müller',           '+49 30 0000005 ', 'REPRODUCTIVE_IMAGING');

-- 2. Patients: 10 000 rows.
INSERT INTO patient (created_at, date_of_birth, email, first_name, last_name, phone, treatment_interest)
SELECT TIMESTAMP '2024-01-01 00:00:00' + i * INTERVAL '37 minutes 13.123457 seconds',
       DATE '1975-01-01' + (i * 7) % 9000,
       format('syn-%s@example.org', lpad(i::text, 6, '0')),
       (ARRAY['María','José','Zoë','Ñandú','ana','Beatriz','Chloé','Daniel','Émilie','Fátima','Grace','Hélène'])[1 + i % 12],
       (ARRAY['Álvarez','de la Fuente','O''Brien','Zúñiga','álvarez','Del Río','Smith','García ','MacDonald',
              'van der Berg','Núñez','Østergaard','Ruiz','d''Arcy','LÓPEZ','ß-Straße','Ünal'])[1 + (i * 7) % 17]
           || CASE WHEN i % 97 = 0 THEN ' ' ELSE '' END,
       CASE WHEN i % 5 = 0 THEN NULL ELSE format('+34 6%s', lpad((i * 7919 % 100000000)::text, 8, '0')) END,
       (ARRAY['FERTILITY_ASSESSMENT','IVF','IUI','EGG_FREEZING','EGG_DONATION'])[1 + i % 5]
FROM generate_series(1, 10000) AS g(i);

-- 3. Customer accounts for 2 000 of them (I3: CUSTOMER with patient_id; I4: username = lower-case email).
INSERT INTO user_account (created_at, display_name, password_hash, role, username, patient_id)
SELECT p.created_at + INTERVAL '1 second',
       p.first_name || ' ' || p.last_name,
       'SYNTHETIC-NOT-A-PASSWORD-HASH-' || lpad(p.id::text, 26, '0'),   -- these accounts cannot log in
       'CUSTOMER',
       p.email,
       p.id
FROM patient p
WHERE p.email LIKE 'syn-%@example.org' AND p.id % 5 = 0;

-- 4. Appointments: 50 000 rows over the synthetic patients and ALL doctors (I1), durations from VisitType (I2).
WITH pts AS (
    SELECT id, row_number() OVER (ORDER BY id) AS rn, count(*) OVER () AS n
    FROM patient WHERE email LIKE 'syn-%@example.org'
), docs AS (
    SELECT name, row_number() OVER (ORDER BY id) AS rn, count(*) OVER () AS n FROM doctor
), vt(rn, visit_type, minutes) AS (
    VALUES (1, 'INITIAL_CONSULTATION', 45), (2, 'ULTRASOUND_MONITORING', 20), (3, 'BLOOD_TEST', 15),
           (4, 'EGG_RETRIEVAL', 60), (5, 'EMBRYO_TRANSFER', 30), (6, 'FOLLOW_UP', 30)
), g AS (
    SELECT i FROM generate_series(1, 50000) AS s(i)
)
INSERT INTO appointment (clinician, created_at, duration_minutes, start_time, status, visit_type, patient_id)
SELECT d.name,
       TIMESTAMP '2024-03-01 00:00:00' + g.i * INTERVAL '11 minutes 7.000123 seconds',
       vt.minutes,
       TIMESTAMP '2025-01-06 08:00:00' + ((g.i * 13) % 900) * INTERVAL '1 day' + ((g.i * 17) % 44) * INTERVAL '15 minutes',
       (ARRAY['SCHEDULED','COMPLETED','CANCELLED','NO_SHOW'])[1 + (g.i * 3) % 4],
       vt.visit_type,
       p.id
FROM g
JOIN pts p ON p.rn = 1 + (g.i * 31) % p.n
JOIN docs d ON d.rn = 1 + g.i % d.n
JOIN vt ON vt.rn = 1 + g.i % 6;
