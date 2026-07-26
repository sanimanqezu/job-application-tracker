-- Live-scanned job openings pulled from company ATS APIs.
-- Global catalog (not per-user); "new for me" is derived by excluding URLs the
-- user already has in job_applications.
CREATE TABLE IF NOT EXISTS scanned_jobs (
    id            BIGSERIAL PRIMARY KEY,
    source        VARCHAR(40)  NOT NULL,
    company       VARCHAR(150) NOT NULL,
    title         VARCHAR(300) NOT NULL,
    location      VARCHAR(200),
    url           VARCHAR(600) NOT NULL UNIQUE,
    stack         VARCHAR(200),
    segment       VARCHAR(80),
    city          VARCHAR(120),
    junior        BOOLEAN      NOT NULL DEFAULT FALSE,
    posted_at     TIMESTAMP,
    first_seen_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_seen_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scanned_jobs_company ON scanned_jobs(company);
CREATE INDEX IF NOT EXISTS idx_scanned_jobs_junior  ON scanned_jobs(junior);
CREATE INDEX IF NOT EXISTS idx_scanned_jobs_seen    ON scanned_jobs(first_seen_at);
