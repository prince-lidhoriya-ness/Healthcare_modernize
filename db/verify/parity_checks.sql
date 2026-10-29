-- Supplementary parity checks C1-C9 (forge/db/strategy.md section 7.2). C10 is the pg_dump --schema-only diff.
--
-- Read-only. Prints counts, ids and hashes only, never row contents (PII rule R5).
-- Run the same file on both sides and diff the outputs:
--   psql -X -At -v ON_ERROR_STOP=1 -f db/verify/parity_checks.sql > checks-<side>.txt
-- Lines in the EXPECTED-DIFFERENT section (last) may differ between PG13 and PG18; everything above must not.
-- Works unchanged on PostgreSQL 13 and 18 (version-specific catalog columns are read through to_jsonb()).

\set QUIET on
\pset footer off
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

-- C1 per-table count / min(id) / max(id)
SELECT 'C1|' || t || '|' || n || '|' || coalesce(mi::text, '-') || '|' || coalesce(ma::text, '-')
FROM (
    SELECT 'appointment' AS t, count(*) AS n, min(id) AS mi, max(id) AS ma FROM appointment
    UNION ALL SELECT 'doctor', count(*), min(id), max(id) FROM doctor
    UNION ALL SELECT 'patient', count(*), min(id), max(id) FROM patient
    UNION ALL SELECT 'user_account', count(*), min(id), max(id) FROM user_account
) s ORDER BY t;

-- C2 sequence positions (R8): last_value, is_called, and last_value >= max(id)
SELECT 'C2|appointment|' || pg_get_serial_sequence('appointment', 'id') || '|' || s.last_value || '|' || s.is_called
       || '|ge_max=' || (s.last_value >= coalesce((SELECT max(id) FROM appointment), 0))
FROM public.appointment_id_seq s;
SELECT 'C2|doctor|' || pg_get_serial_sequence('doctor', 'id') || '|' || s.last_value || '|' || s.is_called
       || '|ge_max=' || (s.last_value >= coalesce((SELECT max(id) FROM doctor), 0))
FROM public.doctor_id_seq s;
SELECT 'C2|patient|' || pg_get_serial_sequence('patient', 'id') || '|' || s.last_value || '|' || s.is_called
       || '|ge_max=' || (s.last_value >= coalesce((SELECT max(id) FROM patient), 0))
FROM public.patient_id_seq s;
SELECT 'C2|user_account|' || pg_get_serial_sequence('user_account', 'id') || '|' || s.last_value || '|' || s.is_called
       || '|ge_max=' || (s.last_value >= coalesce((SELECT max(id) FROM user_account), 0))
FROM public.user_account_id_seq s;

-- C3 invariants I1-I4: violation counts, all must be 0
SELECT 'C3|I1_clinician_not_a_doctor|' || count(*)
FROM appointment a WHERE NOT EXISTS (SELECT 1 FROM doctor d WHERE d.name = a.clinician);
SELECT 'C3|I2_duration_not_visit_type|' || count(*)
FROM appointment a
WHERE a.duration_minutes IS DISTINCT FROM CASE a.visit_type
        WHEN 'INITIAL_CONSULTATION' THEN 45 WHEN 'ULTRASOUND_MONITORING' THEN 20 WHEN 'BLOOD_TEST' THEN 15
        WHEN 'EGG_RETRIEVAL' THEN 60 WHEN 'EMBRYO_TRANSFER' THEN 30 WHEN 'FOLLOW_UP' THEN 30 END;
SELECT 'C3|I3_admin_iff_no_patient|' || count(*)
FROM user_account WHERE (role = 'ADMIN') <> (patient_id IS NULL);
SELECT 'C3|I4_not_lower_case|' || (
    (SELECT count(*) FROM user_account WHERE username <> lower(username))
  + (SELECT count(*) FROM patient WHERE email <> lower(email))
  + (SELECT count(*) FROM doctor WHERE email <> lower(email)));

-- C4 ordering of the data in the app's orders (collation-sensitive), id tie-break
SELECT 'C4|patient_last_first|' || md5(string_agg(id::text, ',' ORDER BY last_name, first_name, id)) FROM patient;
SELECT 'C4|appointment_start_desc|' || md5(string_agg(id::text, ',' ORDER BY start_time DESC, id)) FROM appointment;
SELECT 'C4|doctor_last_first|' || md5(string_agg(id::text, ',' ORDER BY last_name, first_name, id)) FROM doctor;

-- C5 data-independent collation probe
SELECT 'C5|probe_order|' || md5(string_agg(x, '|' ORDER BY x))
FROM (VALUES ('a'), ('B'), ('Á'), ('ä'), ('o''brien'), ('O''Brien'), ('de la Fuente'), ('Del Río'), ('Zoë'), ('zoe'),
             (' x'), ('100'), ('20'), ('ß'), ('ss'), ('Ñandú'), ('nandu')) v(x);
SELECT 'C5|upper_strasse|' || upper('straße');
SELECT 'C5|lower_AEI|' || lower('ÁÉÍ');
SELECT 'C5|a_lt_B|' || ('a' < 'B');

-- C6 collation facts
SELECT 'C6|db|' || pg_encoding_to_char(encoding) || '|' || datcollate || '|' || datctype
FROM pg_database WHERE datname = current_database();
SELECT 'C6|en_US.utf8_actual_version|' || coalesce(pg_collation_actual_version(oid), '-')
FROM pg_collation WHERE collname = 'en_US.utf8' AND collencoding = pg_char_to_encoding('UTF8');

-- C7 case-insensitive lookups (H3) and the search semantics (lower(col) LIKE '%term%')
SELECT 'C7|upper_email_eq|' || count(*) FROM patient WHERE upper(email) = upper('SYN-000042@EXAMPLE.TEST');
SELECT 'C7|upper_username_eq|' || count(*) FROM user_account WHERE upper(username) = upper('Admin');
SELECT 'C7|doctor_name_ignorecase|' || count(*) FROM doctor WHERE upper(name) = upper('dr. ana álvarez');
SELECT 'C7|search_alvarez|' || count(*) FROM patient
WHERE lower(first_name) LIKE '%álvarez%' OR lower(last_name) LIKE '%álvarez%' OR lower(email) LIKE '%álvarez%';
SELECT 'C7|search_obrien|' || count(*) FROM patient
WHERE lower(first_name) LIKE '%o''brien%' OR lower(last_name) LIKE '%o''brien%' OR lower(email) LIKE '%o''brien%';
SELECT 'C7|search_de_la|' || count(*) FROM patient
WHERE lower(first_name) LIKE '%de la%' OR lower(last_name) LIKE '%de la%' OR lower(email) LIKE '%de la%';
SELECT 'C7|search_strasse|' || count(*) FROM patient WHERE lower(last_name) LIKE '%straße%';

-- C8 dashboard aggregates (H5)
SELECT 'C8|status|' || status || '|' || count(*) FROM appointment GROUP BY status ORDER BY status;
SELECT 'C8|visit_type|' || visit_type || '|' || count(*) FROM appointment GROUP BY visit_type ORDER BY visit_type;
SELECT 'C8|treatment_interest|' || treatment_interest || '|' || count(*) FROM patient GROUP BY treatment_interest ORDER BY treatment_interest;
SELECT 'C8|specialty|' || specialty || '|' || count(*) FROM doctor GROUP BY specialty ORDER BY specialty;
SELECT 'C8|role|' || role || '|' || count(*) FROM user_account GROUP BY role ORDER BY role;

-- C9 time zone settings (timestamp values themselves are covered by db_verify compare)
SELECT 'C9|TimeZone|' || current_setting('TimeZone');
SELECT 'C9|DateStyle|' || current_setting('DateStyle');

-- EXPECTED-DIFFERENT (PG13 vs PG18): server_version, data_checksums, password_encryption, locale provider,
-- datcollversion. Recorded, not required to match.
SELECT 'XD|server_version|' || current_setting('server_version');
SELECT 'XD|data_checksums|' || current_setting('data_checksums');
SELECT 'XD|password_encryption|' || current_setting('password_encryption');
SELECT 'XD|datlocprovider|' || coalesce(to_jsonb(d) ->> 'datlocprovider', '-')
       || '|datcollversion|' || coalesce(to_jsonb(d) ->> 'datcollversion', '-')
FROM pg_database d WHERE datname = current_database();

COMMIT;
