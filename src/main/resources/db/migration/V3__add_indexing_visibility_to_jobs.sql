ALTER TABLE jobs ADD COLUMN user_id UUID;

-- Backfill user_id from items for any existing jobs
UPDATE jobs j
SET user_id = i.user_id
FROM items i
WHERE j.target_id = i.id;

-- Fallback for any orphaned jobs just in case
UPDATE jobs
SET user_id = '00000000-0000-0000-0000-000000000001'
WHERE user_id IS NULL;

ALTER TABLE jobs ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE jobs ADD COLUMN started_at TIMESTAMPTZ;
ALTER TABLE jobs ADD COLUMN finished_at TIMESTAMPTZ;

CREATE INDEX idx_jobs_user_id ON jobs (user_id);
CREATE INDEX idx_jobs_user_id_status ON jobs (user_id, status);
CREATE INDEX idx_jobs_user_id_created_at ON jobs (user_id, created_at DESC);
