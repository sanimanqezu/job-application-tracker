-- Remember the uploaded CV's original file name, so the profile can always show
-- which document is currently on file.
ALTER TABLE users ADD COLUMN IF NOT EXISTS cv_file_name VARCHAR(255);
