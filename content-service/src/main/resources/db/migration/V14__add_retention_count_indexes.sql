CREATE INDEX IF NOT EXISTS idx_note_versions_created_at ON note_versions (created_at);
CREATE INDEX IF NOT EXISTS idx_comments_created_at ON comments (created_at);
