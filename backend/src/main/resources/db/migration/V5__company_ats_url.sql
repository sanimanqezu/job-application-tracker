-- Cache the ATS board URL discovered for a company by the deep probe, so later
-- scans can use the fast (detect-from-URL) path instead of re-probing.
ALTER TABLE companies ADD COLUMN IF NOT EXISTS ats_url VARCHAR(600);
