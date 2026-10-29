-- U1: undo of V1__baseline.sql. DESTRUCTIVE: drops every application table, its rows and its sequence.
--
-- SCRATCH / DISPOSABLE DATABASES ONLY (reversibility proofs, TASK-008). On a live database the rollback of V1 is
-- "keep the schema, revert the app to the legacy build" (forge/db/strategy.md section 9, R1), never this script.
--
-- Deliberately outside the classpath (backend/db/undo, not src/main/resources/db/migration): Flyway OSS does not run
-- U scripts, so it must never see this file. Run it with psql and an explicit opt-in:
--   PGOPTIONS='-c forge.allow_destructive_undo=on' psql -v ON_ERROR_STOP=1 -f backend/db/undo/U1__baseline.sql
--
-- Result: the database is back to its pre-V1 state (no application tables, sequences or flyway_schema_history).

BEGIN;

DO $$
BEGIN
    IF current_setting('forge.allow_destructive_undo', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'U1 refused: scratch-only undo (set PGOPTIONS=''-c forge.allow_destructive_undo=on'')';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

-- FK order: the referencing tables first. The *_id_seq sequences are OWNED BY their id columns and go with them.
DROP TABLE public.user_account;
DROP TABLE public.appointment;
DROP TABLE public.doctor;
DROP TABLE public.patient;

-- Absent when V1 was applied outside Flyway; present after "flyway migrate" or an app start.
DROP TABLE IF EXISTS public.flyway_schema_history;

COMMIT;
