-- Run with psql -v ON_ERROR_STOP=1 while StudHub is stopped, after a backup.
BEGIN;
ALTER TABLE assignments ALTER COLUMN course_id DROP NOT NULL;
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS status varchar(20);
UPDATE assignments SET status = CASE WHEN completed THEN 'DONE' ELSE 'TODO' END WHERE status IS NULL;
ALTER TABLE project_tasks ADD COLUMN IF NOT EXISTS priority varchar(10);
UPDATE project_tasks SET priority = 'MEDIUM' WHERE priority IS NULL;
COMMIT;
