-- Reference only: Hibernate creates these tables automatically on startup
-- (spring.jpa.hibernate.ddl-auto=update). Shown here so the schema is easy to review.

CREATE TABLE patient (
    id                 BIGSERIAL PRIMARY KEY,
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    date_of_birth      DATE         NOT NULL,
    email              VARCHAR(150) NOT NULL UNIQUE,
    phone              VARCHAR(30),
    treatment_interest VARCHAR(30)  NOT NULL,  -- FERTILITY_ASSESSMENT | IVF | IUI | EGG_FREEZING | EGG_DONATION
    created_at         TIMESTAMP    NOT NULL
);

CREATE TABLE appointment (
    id               BIGSERIAL PRIMARY KEY,
    patient_id       BIGINT       NOT NULL REFERENCES patient (id),
    visit_type       VARCHAR(40)  NOT NULL,  -- INITIAL_CONSULTATION | ULTRASOUND_MONITORING | BLOOD_TEST | EGG_RETRIEVAL | EMBRYO_TRANSFER | FOLLOW_UP
    clinician        VARCHAR(100) NOT NULL,
    start_time       TIMESTAMP    NOT NULL,
    duration_minutes INTEGER      NOT NULL,
    status           VARCHAR(20)  NOT NULL,  -- SCHEDULED | COMPLETED | CANCELLED | NO_SHOW
    created_at       TIMESTAMP    NOT NULL
);

CREATE INDEX idx_appointment_clinician_start ON appointment (clinician, start_time);
CREATE INDEX idx_appointment_patient ON appointment (patient_id);

-- Portal logins. Customer accounts link to their patient; admin accounts have no patient.
CREATE TABLE user_account (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(150) NOT NULL UNIQUE,  -- admin username, or the patient's email
    password_hash VARCHAR(100) NOT NULL,          -- BCrypt
    role          VARCHAR(20)  NOT NULL,          -- ADMIN | CUSTOMER
    display_name  VARCHAR(200) NOT NULL,
    patient_id    BIGINT UNIQUE REFERENCES patient (id),
    created_at    TIMESTAMP    NOT NULL
);

-- Doctors patients can be booked with. appointment.clinician holds doctor.name.
CREATE TABLE doctor (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,  -- "Dr. First Last"
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    specialty  VARCHAR(40)  NOT NULL,         -- REPRODUCTIVE_MEDICINE | GYNAECOLOGY | EMBRYOLOGY | ANDROLOGY | REPRODUCTIVE_IMAGING
    email      VARCHAR(150) NOT NULL UNIQUE,
    phone      VARCHAR(30),
    created_at TIMESTAMP    NOT NULL
);
