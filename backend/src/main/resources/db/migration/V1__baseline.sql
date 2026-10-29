-- V1 baseline: the schema exactly as the legacy build (Spring Boot 3.2.0 / Hibernate 6.3.1.Final, ddl-auto=update)
-- creates it on PostgreSQL 13.23. STORY-003 / TASK-006 (R1 of forge/db/strategy.md).
--
-- Source: pg_dump 13.23 --schema-only --no-owner --no-privileges of a disposable postgres:13.23-trixie
-- (sha256:4689940c...8068) created and seeded by the legacy app, after the test suite's second ddl-auto=update pass
-- (archived in forge/db/evidence/r1-legacy/schema-pg13client.sql). NOT derived from docs/schema.sql, which differs:
--   - ids are serial-style: CREATE SEQUENCE <t>_id_seq ... OWNED BY + DEFAULT nextval (Hibernate 6.3 emits bigserial),
--   - timestamp(6), five enum CHECK constraints named <table>_<column>_check,
--   - Hibernate-generated unique (uk_<hash>) and foreign key (fk<hash>) names. Later migrations MUST use these names.
-- Statements are pg_dump's, verbatim and in pg_dump order; only the pg_dump session preamble, comments and
-- \restrict lines were removed. Ownership and privileges are deliberately absent (roles are STORY-009 / R2).
-- One exception: the five CHECK expressions are written as Hibernate wrote them, `col IN ('A', 'B', ...)`.
-- pg_dump's deparsed text `(col)::text = ANY ((ARRAY['A'::character varying, ...])::text[])` does not round-trip
-- (PostgreSQL re-parses it into a different expression tree, so the schema diff was not empty); the IN form
-- reproduces Hibernate's stored expression exactly.
--
-- No IF NOT EXISTS, on purpose: on a non-empty schema V1 must fail loudly, never skip a drifted table. A database
-- created by the legacy build is baselined instead (FLYWAY_BASELINE_ON_MIGRATE=true once, strategy 3.3).
-- Proof that this file == the legacy schema: forge/db/evidence/r1-v1/ (TASK-033, empty pg_dump diff on 13.23 and 18.6).
-- Undo (scratch databases only): backend/db/undo/U1__baseline.sql. On a live database the rollback of V1 is
-- "keep the schema, revert the app"; never U1.
-- FROZEN once merged: never edit this file; fix forward with V2+ (and a matching U2+).
--
-- squawk (2.70.0, --assume-in-transaction: Flyway runs each PostgreSQL migration in one transaction). V1 must
-- reproduce the legacy types exactly, so three type-preference rules are ignored for THIS FILE ONLY
-- (forge/db/strategy.md 3.5); V2+ get no exclusions:
--   prefer-text-field       varchar(n) is the legacy type; Hibernate validate and the column lengths are the contract
--   prefer-timestamp-tz     the columns are wall-clock timestamp(6) by design (inventory section 1); out of scope
--   prefer-bigint-over-int  duration_minutes integer is the legacy type
-- squawk-ignore-file prefer-text-field, prefer-timestamp-tz, prefer-bigint-over-int

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE public.appointment (
    id bigint NOT NULL,
    clinician character varying(100) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    duration_minutes integer NOT NULL,
    start_time timestamp(6) without time zone NOT NULL,
    status character varying(20) NOT NULL,
    visit_type character varying(40) NOT NULL,
    patient_id bigint NOT NULL,
    CONSTRAINT appointment_status_check CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT appointment_visit_type_check CHECK (visit_type IN ('INITIAL_CONSULTATION', 'ULTRASOUND_MONITORING', 'BLOOD_TEST', 'EGG_RETRIEVAL', 'EMBRYO_TRANSFER', 'FOLLOW_UP'))
);

CREATE SEQUENCE public.appointment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.appointment_id_seq OWNED BY public.appointment.id;

CREATE TABLE public.doctor (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    email character varying(150) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    name character varying(100) NOT NULL,
    phone character varying(30),
    specialty character varying(40) NOT NULL,
    CONSTRAINT doctor_specialty_check CHECK (specialty IN ('REPRODUCTIVE_MEDICINE', 'GYNAECOLOGY', 'EMBRYOLOGY', 'ANDROLOGY', 'REPRODUCTIVE_IMAGING'))
);

CREATE SEQUENCE public.doctor_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.doctor_id_seq OWNED BY public.doctor.id;

CREATE TABLE public.patient (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    date_of_birth date NOT NULL,
    email character varying(150) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    phone character varying(30),
    treatment_interest character varying(30) NOT NULL,
    CONSTRAINT patient_treatment_interest_check CHECK (treatment_interest IN ('FERTILITY_ASSESSMENT', 'IVF', 'IUI', 'EGG_FREEZING', 'EGG_DONATION'))
);

CREATE SEQUENCE public.patient_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.patient_id_seq OWNED BY public.patient.id;

CREATE TABLE public.user_account (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    display_name character varying(200) NOT NULL,
    password_hash character varying(100) NOT NULL,
    role character varying(20) NOT NULL,
    username character varying(150) NOT NULL,
    patient_id bigint,
    CONSTRAINT user_account_role_check CHECK (role IN ('ADMIN', 'CUSTOMER'))
);

CREATE SEQUENCE public.user_account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.user_account_id_seq OWNED BY public.user_account.id;

ALTER TABLE ONLY public.appointment ALTER COLUMN id SET DEFAULT nextval('public.appointment_id_seq'::regclass);

ALTER TABLE ONLY public.doctor ALTER COLUMN id SET DEFAULT nextval('public.doctor_id_seq'::regclass);

ALTER TABLE ONLY public.patient ALTER COLUMN id SET DEFAULT nextval('public.patient_id_seq'::regclass);

ALTER TABLE ONLY public.user_account ALTER COLUMN id SET DEFAULT nextval('public.user_account_id_seq'::regclass);

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT appointment_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.doctor
    ADD CONSTRAINT doctor_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT patient_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.doctor
    ADD CONSTRAINT uk_2i9eqm6hy3ephpt9ep0xfjsfq UNIQUE (name);

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT uk_bawli8xm92f30ei6x9p3h8eju UNIQUE (email);

ALTER TABLE ONLY public.user_account
    ADD CONSTRAINT uk_castjbvpeeus0r8lbpehiu0e4 UNIQUE (username);

ALTER TABLE ONLY public.user_account
    ADD CONSTRAINT uk_ch51ya1vd1vc1l0ev7wy5aqo9 UNIQUE (patient_id);

ALTER TABLE ONLY public.doctor
    ADD CONSTRAINT uk_jdtgexk368pq6d2yb3neec59d UNIQUE (email);

ALTER TABLE ONLY public.user_account
    ADD CONSTRAINT user_account_pkey PRIMARY KEY (id);

CREATE INDEX idx_appointment_clinician_start ON public.appointment USING btree (clinician, start_time);

CREATE INDEX idx_appointment_patient ON public.appointment USING btree (patient_id);

ALTER TABLE ONLY public.appointment
    ADD CONSTRAINT fk4apif2ewfyf14077ichee8g06 FOREIGN KEY (patient_id) REFERENCES public.patient(id);

ALTER TABLE ONLY public.user_account
    ADD CONSTRAINT fksqtkyj6gpcpvqcwel18jt04el FOREIGN KEY (patient_id) REFERENCES public.patient(id);
