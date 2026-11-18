BEGIN;
CREATE TABLE IF NOT EXISTS chat_attachments (
    message_id BIGINT PRIMARY KEY REFERENCES chat_messages(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    size INTEGER NOT NULL CHECK (size BETWEEN 1 AND 8388608),
    data BYTEA NOT NULL CHECK (octet_length(data) = size)
);
COMMIT;
