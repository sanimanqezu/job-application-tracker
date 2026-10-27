-- Per-user career profile: drives job matching (their CV, target role, experience,
-- and the estimated occupation category). Nullable so pre-existing accounts still validate.
ALTER TABLE users ADD COLUMN IF NOT EXISTS cv               TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS desired_job      VARCHAR(150);
ALTER TABLE users ADD COLUMN IF NOT EXISTS years_experience DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN IF NOT EXISTS job_category     VARCHAR(60);
