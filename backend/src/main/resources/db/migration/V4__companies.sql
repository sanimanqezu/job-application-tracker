-- Persistent company universe. Seeded from companies.json at startup and
-- scanned continuously by the harvester (~10/min).
CREATE TABLE IF NOT EXISTS companies (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL UNIQUE,
    apply_url       VARCHAR(600),
    segment         VARCHAR(80),
    stack           VARCHAR(200),
    city            VARCHAR(120),
    fit             VARCHAR(20),
    note            VARCHAR(500),
    last_scanned_at TIMESTAMP,
    jobs_found      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_companies_last_scanned ON companies(last_scanned_at);
