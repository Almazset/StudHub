BEGIN;

-- NULL means a publication from before the draft flag was introduced.
ALTER TABLE news_posts
    ADD COLUMN IF NOT EXISTS published BOOLEAN,
    ADD COLUMN IF NOT EXISTS source_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS source_date DATE,
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'RELEASED')),
    ALTER COLUMN published_at DROP NOT NULL;

COMMIT;
