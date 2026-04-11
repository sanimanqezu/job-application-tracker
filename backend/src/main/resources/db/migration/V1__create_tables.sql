-- Users table
CREATE TABLE IF NOT EXISTS users (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    email        VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Job Applications table
CREATE TABLE IF NOT EXISTS job_applications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company      VARCHAR(150) NOT NULL,
    role         VARCHAR(150) NOT NULL,
    location     VARCHAR(150),
    job_url      TEXT,
    status       VARCHAR(30)  NOT NULL,
    applied_date DATE         NOT NULL,
    salary_range VARCHAR(100),
    notes        TEXT,
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_job_applications_user_id ON job_applications(user_id);
CREATE INDEX IF NOT EXISTS idx_job_applications_status  ON job_applications(status);

-- Interviews table
CREATE TABLE IF NOT EXISTS interviews (
    id               BIGSERIAL PRIMARY KEY,
    application_id   BIGINT    NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    interview_date   TIMESTAMP NOT NULL,
    type             VARCHAR(20) NOT NULL,
    notes            TEXT,
    completed        BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_interviews_application_id ON interviews(application_id);
