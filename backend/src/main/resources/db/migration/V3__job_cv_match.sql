-- CV-aware matching: store each job's description and a match score against the
-- candidate's stack, plus the skills that matched.
ALTER TABLE scanned_jobs ADD COLUMN IF NOT EXISTS description    TEXT;
ALTER TABLE scanned_jobs ADD COLUMN IF NOT EXISTS match_score    INT NOT NULL DEFAULT 0;
ALTER TABLE scanned_jobs ADD COLUMN IF NOT EXISTS matched_skills VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_scanned_jobs_match ON scanned_jobs(match_score);
